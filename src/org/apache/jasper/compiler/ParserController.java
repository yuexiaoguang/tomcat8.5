package org.apache.jasper.compiler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Stack;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.tomcat.Jar;
import org.xml.sax.Attributes;

/**
 * 解析JSP 页面的控制器.
 * <p>
 * 相同的 ParserController实例用于 JSP 页面, 并通过它包含任何 JSP片段(通过一个包含指令), 在每个段中可以用标准或XML语法提供.
 * 这个类选择并调用JSP页面及其包含的片段的适当的解析器.
 */
class ParserController implements TagConstants {

    private static final String CHARSET = "charset=";

    private final JspCompilationContext ctxt;
    private final Compiler compiler;
    private final ErrorDispatcher err;

    /*
     * 表示要处理的文件的语法(XML 或标准的)
     */
    private boolean isXml;

    /*
     * 一个Stack, 用于保存涉及相对路径的包含指令的'当前基础目录'跟踪信息
     */
    private final Stack<String> baseDirStack = new Stack<>();

    private boolean isEncodingSpecifiedInProlog;
    private boolean isBomPresent;
    private int skip;

    private String sourceEnc;

    private boolean isDefaultPageEncoding;
    private boolean isTagFile;
    private boolean directiveOnly;

    /*
     * Constructor
     */
    public ParserController(JspCompilationContext ctxt, Compiler compiler) {
        this.ctxt = ctxt;
        this.compiler = compiler;
        this.err = compiler.getErrorDispatcher();
    }

    public JspCompilationContext getJspCompilationContext () {
        return ctxt;
    }

    public Compiler getCompiler () {
        return compiler;
    }

    /**
     * 解析JSP页面或标签文件. 通过编译器执行.
     *
     * @param inFileName 要解析的JSP页面或标签文件的路径.
     *
     * @return The parsed nodes
     *
     * @throws JasperException 如果在解析过程中发生错误
     * @throws IOException 如果发生I/O错误，例如文件未被发现
     */
    public Node.Nodes parse(String inFileName) throws JasperException, IOException {
        // 如果正在解析一个打包的标签文件或者它包含的资源 (使用一个 include 命令), ctxt.getTagFileJar()返回从中读取标签文件或包含资源的JAR文件,
        isTagFile = ctxt.isTagFile();
        directiveOnly = false;
        return doParse(inFileName, null, ctxt.getTagFileJar());
    }

    /**
     * 解析JSP页面或标签文件的指令. 通过编译器执行.
     *
     * @param inFileName 要解析的JSP页面或标签文件的路径.
     *
     * @return The parsed directive nodes
     *
     * @throws JasperException 如果在解析过程中发生错误
     * @throws IOException 如果发生I/O错误，例如文件未被发现
     */
    public Node.Nodes parseDirectives(String inFileName) throws JasperException, IOException {
        // 如果正在解析一个打包的标签文件或者它包含的资源 (使用一个 include 命令), ctxt.getTagFileJar()返回从中读取标签文件或包含资源的JAR文件.
        isTagFile = ctxt.isTagFile();
        directiveOnly = true;
        return doParse(inFileName, null, ctxt.getTagFileJar());
    }


    /**
     * 使用给定路径处理include指令.
     *
     * @param inFileName 要包含的资源的路径.
     * @param parent include指令的父节点.
     * @param jar 从其中读取包含的资源的JAR文件, 或null 包含的资源从文件系统读取
     *
     * @return The parsed nodes
     *
     * @throws JasperException 如果在解析过程中发生错误
     * @throws IOException 如果发生I/O错误，例如文件未被发现
     */
    public Node.Nodes parse(String inFileName, Node parent, Jar jar)
            throws JasperException, IOException {
        // 对于静态包含的文件, isTagfile 和 directiveOnly 不会修改.
        return doParse(inFileName, parent, jar);
    }

    /**
     * 使用给定名称从标签文件中提取标签文件指令信息. 这是由编译器调用的
     *
     * @param inFileName 要解析的标签文件的名称.
     * @param jar 标记文件的位置.
     *
     * @return 解析的标签文件节点
     *
     * @throws JasperException 如果在解析过程中发生错误
     * @throws IOException 如果发生I/O错误，例如文件未被发现
     */
    public Node.Nodes parseTagFileDirectives(String inFileName, Jar jar)
            throws JasperException, IOException {
        boolean isTagFileSave = isTagFile;
        boolean directiveOnlySave = directiveOnly;
        isTagFile = true;
        directiveOnly = true;
        Node.Nodes page = doParse(inFileName, null, jar);
        directiveOnly = directiveOnlySave;
        isTagFile = isTagFileSave;
        return page;
    }

    /**
     * 解析给定路径名的JSP页面或标签文件.
     *
     * @param inFileName 要解析的JSP页面或标签文件的名称.
     * @param parent 父节点(non-null 当解析一个include指令的时候)
     * @param jar  从其中读取包含的资源的JAR文件, 或null 包含的资源从文件系统读取
     */
    private Node.Nodes doParse(String inFileName, Node parent, Jar jar)
            throws FileNotFoundException, JasperException, IOException {

        Node.Nodes parsedPage = null;
        isEncodingSpecifiedInProlog = false;
        isBomPresent = false;
        isDefaultPageEncoding = false;

        String absFileName = resolveFileName(inFileName);
        String jspConfigPageEnc = getJspConfigPageEncoding(absFileName);

        // 计算正在处理的JSP文档类型和编码类型
        determineSyntaxAndEncoding(absFileName, jar, jspConfigPageEnc);

        if (parent != null) {
            // Included resource, add to dependent list
            if (jar == null) {
                compiler.getPageInfo().addDependant(absFileName,
                        ctxt.getLastModified(absFileName));
            } else {
                String entry = absFileName.substring(1);
                compiler.getPageInfo().addDependant(jar.getURL(entry),
                        Long.valueOf(jar.getLastModified(entry)));

            }
        }

        if ((isXml && isEncodingSpecifiedInProlog) || isBomPresent) {
            /*
             * 确保XML中明确指定的编码与JSP配置元素中的编码相匹配, 处理 "UTF-16", "UTF-16BE", "UTF-16LE"相同.
             */
            if (jspConfigPageEnc != null && !jspConfigPageEnc.equals(sourceEnc)
                    && (!jspConfigPageEnc.startsWith("UTF-16")
                            || !sourceEnc.startsWith("UTF-16"))) {
                err.jspError("jsp.error.prolog_config_encoding_mismatch",
                        sourceEnc, jspConfigPageEnc);
            }
        }

        // 分派到适当的解析器
        if (isXml) {
            // JSP 文档 (XML 语法)
            // 创建并在JspDocumentParser中妥善关闭jspx页面的InputStream
            parsedPage = JspDocumentParser.parse(this, absFileName, jar, parent,
                    isTagFile, directiveOnly, sourceEnc, jspConfigPageEnc,
                    isEncodingSpecifiedInProlog, isBomPresent);
        } else {
            // Standard syntax
            try (InputStreamReader inStreamReader = JspUtil.getReader(
                    absFileName, sourceEnc, jar, ctxt, err, skip);) {
                JspReader jspReader = new JspReader(ctxt, absFileName,
                        inStreamReader, err);
                parsedPage = Parser.parse(this, jspReader, parent, isTagFile,
                        directiveOnly, jar, sourceEnc, jspConfigPageEnc,
                        isDefaultPageEncoding, isBomPresent);
            }
        }

        baseDirStack.pop();

        return parsedPage;
    }

    /*
     * 检查给定URI是否与web.xml中的jsp-property-group指定的URL模式匹配, 如果这样的话, 返回<page-encoding>元素的值.
     *
     * @param absFileName 要匹配的URI
     *
     * @return 匹配URL模式的jsp-property-group 的<page-encoding>属性的值
     */
    private String getJspConfigPageEncoding(String absFileName) {

        JspConfig jspConfig = ctxt.getOptions().getJspConfig();
        JspConfig.JspProperty jspProperty
            = jspConfig.findJspProperty(absFileName);
        return jspProperty.getPageEncoding();
    }

    /**
     * 确定给定文件的语法(标准的或 XML) 和页面编码属性, 并在'isXml' 和 'sourceEnc'实例变量中保存它们.
     */
    private void determineSyntaxAndEncoding(String absFileName, Jar jar,
            String jspConfigPageEnc)
    throws JasperException, IOException {

        isXml = false;

        /*
         * 'true' 如果文件的语法(XML 或标准的)来自外部信息: 无论是通过JSP 配置元素, ".jspx"后缀, 或封闭文件(包含资源)
         */
        boolean isExternal = false;

        /*
         * 是否需要从临时使用"ISO-8859-1"返回到"UTF-8"
         */
        boolean revert = false;

        JspConfig jspConfig = ctxt.getOptions().getJspConfig();
        JspConfig.JspProperty jspProperty = jspConfig.findJspProperty(
                absFileName);
        if (jspProperty.isXml() != null) {
            // 如果<is-xml>在<jsp-property-group>中指定, 使用它.
            isXml = JspUtil.booleanValue(jspProperty.isXml());
            isExternal = true;
        } else if (absFileName.endsWith(".jspx")
                || absFileName.endsWith(".tagx")) {
            isXml = true;
            isExternal = true;
        }

        if (isExternal && !isXml) {
            // JSP (标准)语法. 使用 jsp-config中指定的编码
            sourceEnc = jspConfigPageEnc;
            if (sourceEnc != null) {
                return;
            }
            // 不知道编码
            sourceEnc = "ISO-8859-1";
        } else {
            // XML syntax or unknown, (auto)detect encoding ...
            EncodingDetector encodingDetector;
            try (InputStream inStream = JspUtil.getInputStream(absFileName, jar, ctxt)) {
                encodingDetector = new EncodingDetector(inStream);
            }

            sourceEnc = encodingDetector.getEncoding();
            isEncodingSpecifiedInProlog = encodingDetector.isEncodingSpecifiedInProlog();
            isBomPresent = (encodingDetector.getSkip() > 0);
            skip = encodingDetector.getSkip();

            if (!isXml && sourceEnc.equals("UTF-8")) {
                /*
                 * 不知道是在处理XML还是标准语法.
				 * 因此, 需要检查一下页面是否包含一个<jsp:root> 元素.
				 *
				 * 需要小心, 因为页面可以编码在ISO-8859-1 (或者完全不同的东西),可能包含字节序列，这将导致 UTF-8 转换器抛出异常. 
				 *
				 * 在这种情况下, 使用 ISO-8859-1源代码编码是安全的, 由于在 ISO-8859-1中没有无效字节序列,
				 * 并且正在寻找的 byte/character序列(即<jsp:root>)在两种编码中都相同(UTF-8和ISO-8859-1都是ASCII的扩展).
                 */
                sourceEnc = "ISO-8859-1";
                revert = true;
            }
        }

        if (isXml) {
            // (这意味着'isExternal' 是 TRUE.)
		    // 正在处理一个JSP文档(通过JSP配置或".jspx" 后缀), 所以完成了.
            return;
        }

        /*
         * 在这个点, 'isExternal'或'isXml'是 FALSE.
		 * 搜索 jsp:root动作, 为了确定我们是在处理XML还是标准语法(除非已经知道正在使用的语法, 即当'isExternal' 是TRUE和'isXml'是 FALSE的时候).
		 * 没有检查XML序言, 因为没有什么可以阻止页面输出XML，并且仍然使用JSP语法(在这种情况下, XML 序言被视为模板文本).
         */
        JspReader jspReader = null;
        try {
            jspReader = new JspReader(ctxt, absFileName, sourceEnc, jar, err);
        } catch (FileNotFoundException ex) {
            throw new JasperException(ex);
        }
        Mark startMark = jspReader.mark();
        if (!isExternal) {
            jspReader.reset(startMark);
            if (hasJspRoot(jspReader)) {
                if (revert) {
                    sourceEnc = "UTF-8";
                }
                isXml = true;
                return;
            } else {
                if (revert && isBomPresent) {
                    sourceEnc = "UTF-8";
                }
                isXml = false;
            }
        }

        /*
         * 在这一点, 我们知道我们在处理JSP语法.
		 * 如果提供了一个 XML 序言, 它被视为模板文本.
		 * 从页面指令中确定页面编码, 除非它通过JSP配置指定.
         */
        if (!isBomPresent) {
            sourceEnc = jspConfigPageEnc;
            if (sourceEnc == null) {
                sourceEnc = getPageEncodingForJspSyntax(jspReader, startMark);
                if (sourceEnc == null) {
                    // Default to "ISO-8859-1" per JSP spec
                    sourceEnc = "ISO-8859-1";
                    isDefaultPageEncoding = true;
                }
            }
        }

    }

    /*
     * 确定JSP语法中页面或标签文件的页面源编码,
     * 通过读取(在这个顺序)'pageEncoding'页面指令属性的值, 或'contentType'页面指令属性的字符值.
     *
     * @return 页面编码, 或者null
     */
    private String getPageEncodingForJspSyntax(JspReader jspReader,
            Mark startMark)
    throws JasperException {

        String encoding = null;
        String saveEncoding = null;

        jspReader.reset(startMark);

        /*
         * 从表单<%@ page %>, <%@ tag %>, <jsp:directive.page >, <jsp:directive.tag >指令确定页面编码.
         */
        while (true) {
            if (jspReader.skipUntil("<") == null) {
                break;
            }
            // 如果这是一个注释, 跳过
            if (jspReader.matches("%--")) {
                if (jspReader.skipUntil("--%>") == null) {
                    // 错误将在解析器中捕获
                    break;
                }
                continue;
            }
            boolean isDirective = jspReader.matches("%@");
            if (isDirective) {
                jspReader.skipSpaces();
            }
            else {
                isDirective = jspReader.matches("jsp:directive.");
            }
            if (!isDirective) {
                continue;
            }

            // 比较"tag ", 所以不匹配"taglib"
            if (jspReader.matches("tag ") || jspReader.matches("page")) {

                jspReader.skipSpaces();
                Attributes attrs = Parser.parseAttributes(this, jspReader);
                encoding = getPageEncodingFromDirective(attrs, "pageEncoding");
                if (encoding != null) {
                    break;
                }
                encoding = getPageEncodingFromDirective(attrs, "contentType");
                if (encoding != null) {
                    saveEncoding = encoding;
                }
            }
        }

        if (encoding == null) {
            encoding = saveEncoding;
        }

        return encoding;
    }

    /*
     * 扫描给定名称和给定属性的属性, 可能是'pageEncoding'或'contentType', 并返回指定的页面编码.
     *
     * 在'contentType'的情况下, 页面编码从内容类型的'charset'属性中获取.
     *
     * @param attrs 页面指令属性
     * @param attrName 要搜索的属性名称 ('pageEncoding' 或 'contentType')
     *
     * @return 页面编码, 或 null
     */
    private String getPageEncodingFromDirective(Attributes attrs,
            String attrName) {
        String value = attrs.getValue(attrName);
        if (attrName.equals("pageEncoding")) {
            return value;
        }

        // attrName = contentType
        String contentType = value;
        String encoding = null;
        if (contentType != null) {
            int loc = contentType.indexOf(CHARSET);
            if (loc != -1) {
                encoding = contentType.substring(loc + CHARSET.length());
            }
        }

        return encoding;
    }

    /*
     * 解析文件的名称 并更新baseDirStack()跟踪每个包含文件的当前基目录.
     * 'root'文件总是一个'绝对'路径, 所以不需要在baseDirStack中设置初始值.
     */
    private String resolveFileName(String inFileName) {
        String fileName = inFileName.replace('\\', '/');
        boolean isAbsolute = fileName.startsWith("/");
        fileName = isAbsolute ? fileName
                : baseDirStack.peek() + fileName;
        String baseDir =
            fileName.substring(0, fileName.lastIndexOf('/') + 1);
        baseDirStack.push(baseDir);
        return fileName;
    }

    /*
     * 检查给定页面是否包含, 作为第一个元素, 一个前缀绑定到JSP的命名空间的<root>元素, 例如:
     *
     * <wombat:root xmlns:wombat="http://java.sun.com/JSP/Page" version="1.2">
     *   ...
     * </wombat:root>
     *
     * @param reader 这个页面的reader
     *
     * @return true 如果此页面包含一个根元素，其前缀绑定到JSP名称空间, 或者false
     */
    private boolean hasJspRoot(JspReader reader) {

        // <prefix>:root must be the first element
        Mark start = null;
        while ((start = reader.skipUntil("<")) != null) {
            int c = reader.nextChar();
            if (c != '!' && c != '?') break;
        }
        if (start == null) {
            return false;
        }
        Mark stop = reader.skipUntil(":root");
        if (stop == null) {
            return false;
        }
        // call substring to get rid of leading '<'
        String prefix = reader.getText(start, stop).substring(1);

        start = stop;
        stop = reader.skipUntil(">");
        if (stop == null) {
            return false;
        }

        // 确定<root>元素的前缀的命名空间
        String root = reader.getText(start, stop);
        String xmlnsDecl = "xmlns:" + prefix;
        int index = root.indexOf(xmlnsDecl);
        if (index == -1) {
            return false;
        }
        index += xmlnsDecl.length();
        while (index < root.length()
                && Character.isWhitespace(root.charAt(index))) {
            index++;
        }
        if (index < root.length() && root.charAt(index) == '=') {
            index++;
            while (index < root.length()
                    && Character.isWhitespace(root.charAt(index))) {
                index++;
            }
            if (index < root.length()
                && (root.charAt(index) == '"' || root.charAt(index) == '\'')) {
                index++;
                if (root.regionMatches(index, JSP_URI, 0, JSP_URI.length())) {
                    return true;
                }
            }
        }

        return false;
    }
}
