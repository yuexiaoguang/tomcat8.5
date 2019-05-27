package org.apache.jasper.compiler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 包含用于生成SMAP数据的静态实用程序, 根据Jasper的当前版本.
 */
public class SmapUtil {

    //*********************************************************************
    // Constants

    private static final String SMAP_ENCODING = "UTF-8";

    //*********************************************************************
    // Public entry points

    /**
     * 生成一个适当的 SMAP, 表示当前编译上下文.  (JSR-045.)
     *
     * @param ctxt 当前编译环境
     * @param pageNodes 当前JSP 页面
     * @return 页面的SMAP
     * @throws IOException Error writing SMAP
     */
    public static String[] generateSmap(
        JspCompilationContext ctxt,
        Node.Nodes pageNodes)
        throws IOException {

        // 扫描 Jasper的节点生成的内部类
        PreScanVisitor psVisitor = new PreScanVisitor();
        try {
            pageNodes.visit(psVisitor);
        } catch (JasperException ex) {
        }
        HashMap<String, SmapStratum> map = psVisitor.getMap();

        // set up our SMAP generator
        SmapGenerator g = new SmapGenerator();

        /** Disable reading of input SMAP because:
            1. There is a bug here: getRealPath() is null if .jsp is in a jar
               Bugzilla 14660.
            2. Mappings from other sources into .jsp files are not supported.
            TODO: fix 1. if 2. is not true.
        // determine if we have an input SMAP
        String smapPath = inputSmapPath(ctxt.getRealPath(ctxt.getJspFile()));
            File inputSmap = new File(smapPath);
            if (inputSmap.exists()) {
                byte[] embeddedSmap = null;
            byte[] subSmap = SDEInstaller.readWhole(inputSmap);
            String subSmapString = new String(subSmap, SMAP_ENCODING);
            g.addSmap(subSmapString, "JSP");
        }
        **/

        // 现在，收集关于我们自己层级的信息(JSP)使用JspLineMap
        SmapStratum s = new SmapStratum();

        g.setOutputFileName(unqualify(ctxt.getServletJavaFileName()));

        // Map out Node.Nodes
        evaluateNodes(pageNodes, s, map, ctxt.getOptions().getMappedFile());
        s.optimizeLineSection();
        g.setStratum(s);

        if (ctxt.getOptions().isSmapDumped()) {
            File outSmap = new File(ctxt.getClassFileName() + ".smap");
            PrintWriter so =
                new PrintWriter(
                    new OutputStreamWriter(
                        new FileOutputStream(outSmap),
                        SMAP_ENCODING));
            so.print(g.getString());
            so.close();
        }

        String classFileName = ctxt.getClassFileName();
        int innerClassCount = map.size();
        String [] smapInfo = new String[2 + innerClassCount*2];
        smapInfo[0] = classFileName;
        smapInfo[1] = g.getString();

        int count = 2;
        Iterator<Map.Entry<String,SmapStratum>> iter = map.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String,SmapStratum> entry = iter.next();
            String innerClass = entry.getKey();
            s = entry.getValue();
            s.optimizeLineSection();
            g = new SmapGenerator();
            g.setOutputFileName(unqualify(ctxt.getServletJavaFileName()));
            g.setStratum(s);

            String innerClassFileName =
                classFileName.substring(0, classFileName.indexOf(".class")) +
                '$' + innerClass + ".class";
            if (ctxt.getOptions().isSmapDumped()) {
                File outSmap = new File(innerClassFileName + ".smap");
                PrintWriter so =
                    new PrintWriter(
                        new OutputStreamWriter(
                            new FileOutputStream(outSmap),
                            SMAP_ENCODING));
                so.print(g.getString());
                so.close();
            }
            smapInfo[count] = innerClassFileName;
            smapInfo[count+1] = g.getString();
            count += 2;
        }

        return smapInfo;
    }

    public static void installSmap(String[] smap)
        throws IOException {
        if (smap == null) {
            return;
        }

        for (int i = 0; i < smap.length; i += 2) {
            File outServlet = new File(smap[i]);
            SDEInstaller.install(outServlet,
                    smap[i+1].getBytes(StandardCharsets.ISO_8859_1));
        }
    }

    //*********************************************************************
    // Private utilities

    /**
     * 返回给定文件路径的不限制的版本.
     */
    private static String unqualify(String path) {
        path = path.replace('\\', '/');
        return path.substring(path.lastIndexOf('/') + 1);
    }

    //*********************************************************************
    // 安装逻辑 (from Robert Field, JSR-045 spec lead)
    private static class SDEInstaller {

        private final Log log = LogFactory.getLog(SDEInstaller.class);

        static final String nameSDE = "SourceDebugExtension";

        byte[] orig;
        byte[] sdeAttr;
        byte[] gen;

        int origPos = 0;
        int genPos = 0;

        int sdeIndex;

        static void install(File classFile, byte[] smap) throws IOException {
            File tmpFile = new File(classFile.getPath() + "tmp");
            SDEInstaller installer = new SDEInstaller(classFile, smap);
            installer.install(tmpFile);
            if (!classFile.delete()) {
                throw new IOException("classFile.delete() failed");
            }
            if (!tmpFile.renameTo(classFile)) {
                throw new IOException("tmpFile.renameTo(classFile) failed");
            }
        }

        SDEInstaller(File inClassFile, byte[] sdeAttr)
            throws IOException {
            if (!inClassFile.exists()) {
                throw new FileNotFoundException("no such file: " + inClassFile);
            }

            this.sdeAttr = sdeAttr;
            // get the bytes
            orig = readWhole(inClassFile);
            gen = new byte[orig.length + sdeAttr.length + 100];
        }

        void install(File outClassFile) throws IOException {
            // do it
            addSDE();

            // write result
            try (FileOutputStream outStream = new FileOutputStream(outClassFile);) {
                outStream.write(gen, 0, genPos);
            }
        }

        static byte[] readWhole(File input) throws IOException {
            int len = (int)input.length();
            byte[] bytes = new byte[len];
            try (FileInputStream inStream = new FileInputStream(input)) {
                if (inStream.read(bytes, 0, len) != len) {
                    throw new IOException("expected size: " + len);
                }
            }
            return bytes;
        }

        void addSDE() throws UnsupportedEncodingException, IOException {
            copy(4 + 2 + 2); // magic min/maj version
            int constantPoolCountPos = genPos;
            int constantPoolCount = readU2();
            if (log.isDebugEnabled())
                log.debug("constant pool count: " + constantPoolCount);
            writeU2(constantPoolCount);

            // 复制旧的常量池, 返回SDE标记的索引
            sdeIndex = copyConstantPool(constantPoolCount);
            if (sdeIndex < 0) {
                // if "SourceDebugExtension" symbol not there add it
                writeUtf8ForSDE();

                // increment the constantPoolCount
                sdeIndex = constantPoolCount;
                ++constantPoolCount;
                randomAccessWriteU2(constantPoolCountPos, constantPoolCount);

                if (log.isDebugEnabled())
                    log.debug("SourceDebugExtension not found, installed at: " + sdeIndex);
            } else {
                if (log.isDebugEnabled())
                    log.debug("SourceDebugExtension found at: " + sdeIndex);
            }
            copy(2 + 2 + 2); // access, this, super
            int interfaceCount = readU2();
            writeU2(interfaceCount);
            if (log.isDebugEnabled())
                log.debug("interfaceCount: " + interfaceCount);
            copy(interfaceCount * 2);
            copyMembers(); // fields
            copyMembers(); // methods
            int attrCountPos = genPos;
            int attrCount = readU2();
            writeU2(attrCount);
            if (log.isDebugEnabled())
                log.debug("class attrCount: " + attrCount);
            // 复制类属性, 返回true, 如果找到SDE 属性(未复制)
            if (!copyAttrs(attrCount)) {
                // 将添加SDE, 而且它还没有计数
                ++attrCount;
                randomAccessWriteU2(attrCountPos, attrCount);
                if (log.isDebugEnabled())
                    log.debug("class attrCount incremented");
            }
            writeAttrForSDE(sdeIndex);
        }

        void copyMembers() {
            int count = readU2();
            writeU2(count);
            if (log.isDebugEnabled())
                log.debug("members count: " + count);
            for (int i = 0; i < count; ++i) {
                copy(6); // access, name, descriptor
                int attrCount = readU2();
                writeU2(attrCount);
                if (log.isDebugEnabled())
                    log.debug("member attr count: " + attrCount);
                copyAttrs(attrCount);
            }
        }

        boolean copyAttrs(int attrCount) {
            boolean sdeFound = false;
            for (int i = 0; i < attrCount; ++i) {
                int nameIndex = readU2();
                // don't write old SDE
                if (nameIndex == sdeIndex) {
                    sdeFound = true;
                    if (log.isDebugEnabled())
                        log.debug("SDE attr found");
                } else {
                    writeU2(nameIndex); // name
                    int len = readU4();
                    writeU4(len);
                    copy(len);
                    if (log.isDebugEnabled())
                        log.debug("attr len: " + len);
                }
            }
            return sdeFound;
        }

        void writeAttrForSDE(int index) {
            writeU2(index);
            writeU4(sdeAttr.length);
            for (int i = 0; i < sdeAttr.length; ++i) {
                writeU1(sdeAttr[i]);
            }
        }

        void randomAccessWriteU2(int pos, int val) {
            int savePos = genPos;
            genPos = pos;
            writeU2(val);
            genPos = savePos;
        }

        int readU1() {
            return orig[origPos++] & 0xFF;
        }

        int readU2() {
            int res = readU1();
            return (res << 8) + readU1();
        }

        int readU4() {
            int res = readU2();
            return (res << 16) + readU2();
        }

        void writeU1(int val) {
            gen[genPos++] = (byte)val;
        }

        void writeU2(int val) {
            writeU1(val >> 8);
            writeU1(val & 0xFF);
        }

        void writeU4(int val) {
            writeU2(val >> 16);
            writeU2(val & 0xFFFF);
        }

        void copy(int count) {
            for (int i = 0; i < count; ++i) {
                gen[genPos++] = orig[origPos++];
            }
        }

        byte[] readBytes(int count) {
            byte[] bytes = new byte[count];
            for (int i = 0; i < count; ++i) {
                bytes[i] = orig[origPos++];
            }
            return bytes;
        }

        void writeBytes(byte[] bytes) {
            for (int i = 0; i < bytes.length; ++i) {
                gen[genPos++] = bytes[i];
            }
        }

        int copyConstantPool(int constantPoolCount)
            throws UnsupportedEncodingException, IOException {
            int sdeIndex = -1;
            // 在类文件中复制常量池索引0
            for (int i = 1; i < constantPoolCount; ++i) {
                int tag = readU1();
                writeU1(tag);
                switch (tag) {
                    case 7 :  // Class
                    case 8 :  // String
                    case 16 : // MethodType
                        if (log.isDebugEnabled())
                            log.debug(i + " copying 2 bytes");
                        copy(2);
                        break;
                    case 15 : // MethodHandle
                        if (log.isDebugEnabled())
                            log.debug(i + " copying 3 bytes");
                        copy(3);
                        break;
                    case 9 :  // Field
                    case 10 : // Method
                    case 11 : // InterfaceMethod
                    case 3 :  // Integer
                    case 4 :  // Float
                    case 12 : // NameAndType
                    case 18 : // InvokeDynamic
                        if (log.isDebugEnabled())
                            log.debug(i + " copying 4 bytes");
                        copy(4);
                        break;
                    case 5 : // Long
                    case 6 : // Double
                        if (log.isDebugEnabled())
                            log.debug(i + " copying 8 bytes");
                        copy(8);
                        i++;
                        break;
                    case 1 : // Utf8
                        int len = readU2();
                        writeU2(len);
                        byte[] utf8 = readBytes(len);
                        String str = new String(utf8, "UTF-8");
                        if (log.isDebugEnabled())
                            log.debug(i + " read class attr -- '" + str + "'");
                        if (str.equals(nameSDE)) {
                            sdeIndex = i;
                        }
                        writeBytes(utf8);
                        break;
                    default :
                        throw new IOException("unexpected tag: " + tag);
                }
            }
            return sdeIndex;
        }

        void writeUtf8ForSDE() {
            int len = nameSDE.length();
            writeU1(1); // Utf8 tag
            writeU2(len);
            for (int i = 0; i < len; ++i) {
                writeU1(nameSDE.charAt(i));
            }
        }
    }

    public static void evaluateNodes(
        Node.Nodes nodes,
        SmapStratum s,
        HashMap<String, SmapStratum> innerClassMap,
        boolean breakAtLF) {
        try {
            nodes.visit(new SmapGenVisitor(s, breakAtLF, innerClassMap));
        } catch (JasperException ex) {
        }
    }

    private static class SmapGenVisitor extends Node.Visitor {

        private SmapStratum smap;
        private final boolean breakAtLF;
        private final HashMap<String, SmapStratum> innerClassMap;

        SmapGenVisitor(SmapStratum s, boolean breakAtLF, HashMap<String, SmapStratum> map) {
            this.smap = s;
            this.breakAtLF = breakAtLF;
            this.innerClassMap = map;
        }

        @Override
        public void visitBody(Node n) throws JasperException {
            SmapStratum smapSave = smap;
            String innerClass = n.getInnerClassName();
            if (innerClass != null) {
                this.smap = innerClassMap.get(innerClass);
            }
            super.visitBody(n);
            smap = smapSave;
        }

        @Override
        public void visit(Node.Declaration n) throws JasperException {
            doSmapText(n);
        }

        @Override
        public void visit(Node.Expression n) throws JasperException {
            doSmapText(n);
        }

        @Override
        public void visit(Node.Scriptlet n) throws JasperException {
            doSmapText(n);
        }

        @Override
        public void visit(Node.IncludeAction n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.ForwardAction n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.GetProperty n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.SetProperty n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.UseBean n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.PlugIn n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.CustomTag n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.UninterpretedTag n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.JspElement n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.JspText n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.NamedAttribute n) throws JasperException {
            visitBody(n);
        }

        @Override
        public void visit(Node.JspBody n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.InvokeAction n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.DoBodyAction n) throws JasperException {
            doSmap(n);
            visitBody(n);
        }

        @Override
        public void visit(Node.ELExpression n) throws JasperException {
            doSmap(n);
        }

        @Override
        public void visit(Node.TemplateText n) throws JasperException {
            Mark mark = n.getStart();
            if (mark == null) {
                return;
            }

            //添加文件信息
            String fileName = mark.getFile();
            smap.addFile(unqualify(fileName), fileName);

            //添加一个 LineInfo, 对应于这个节点的开始
            int iInputStartLine = mark.getLineNumber();
            int iOutputStartLine = n.getBeginJavaLine();
            int iOutputLineIncrement = breakAtLF? 1: 0;
            smap.addLineData(iInputStartLine, fileName, 1, iOutputStartLine,
                             iOutputLineIncrement);

            // Output additional mappings in the text
            java.util.ArrayList<Integer> extraSmap = n.getExtraSmap();

            if (extraSmap != null) {
                for (int i = 0; i < extraSmap.size(); i++) {
                    iOutputStartLine += iOutputLineIncrement;
                    smap.addLineData(
                        iInputStartLine+extraSmap.get(i).intValue(),
                        fileName,
                        1,
                        iOutputStartLine,
                        iOutputLineIncrement);
                }
            }
        }

        private void doSmap(
            Node n,
            int inLineCount,
            int outIncrement,
            int skippedLines) {
            Mark mark = n.getStart();
            if (mark == null) {
                return;
            }

            String unqualifiedName = unqualify(mark.getFile());
            smap.addFile(unqualifiedName, mark.getFile());
            smap.addLineData(
                mark.getLineNumber() + skippedLines,
                mark.getFile(),
                inLineCount - skippedLines,
                n.getBeginJavaLine() + skippedLines,
                outIncrement);
        }

        private void doSmap(Node n) {
            doSmap(n, 1, n.getEndJavaLine() - n.getBeginJavaLine(), 0);
        }

        private void doSmapText(Node n) {
            String text = n.getText();
            int index = 0;
            int next = 0;
            int lineCount = 1;
            int skippedLines = 0;
            boolean slashStarSeen = false;
            boolean beginning = true;

            // 计算文本中的行数, 但在文本开头跳过注释行.
            while ((next = text.indexOf('\n', index)) > -1) {
                if (beginning) {
                    String line = text.substring(index, next).trim();
                    if (!slashStarSeen && line.startsWith("/*")) {
                        slashStarSeen = true;
                    }
                    if (slashStarSeen) {
                        skippedLines++;
                        int endIndex = line.indexOf("*/");
                        if (endIndex >= 0) {
                            // End of /* */ comment
                            slashStarSeen = false;
                            if (endIndex < line.length() - 2) {
                                // Some executable code after comment
                                skippedLines--;
                                beginning = false;
                            }
                        }
                    } else if (line.length() == 0 || line.startsWith("//")) {
                        skippedLines++;
                    } else {
                        beginning = false;
                    }
                }
                lineCount++;
                index = next + 1;
            }

            doSmap(n, lineCount, 1, skippedLines);
        }
    }

    private static class PreScanVisitor extends Node.Visitor {

        HashMap<String, SmapStratum> map = new HashMap<>();

        @Override
        public void doVisit(Node n) {
            String inner = n.getInnerClassName();
            if (inner != null && !map.containsKey(inner)) {
                map.put(inner, new SmapStratum());
            }
        }

        HashMap<String, SmapStratum> getMap() {
            return map;
        }
    }
}
