package org.apache.jasper.compiler;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.jasper.JspCompilationContext;
import org.apache.tomcat.Jar;

/**
 * 提供有关javac编译错误信息.
 */
public class JavacErrorDetail {

    private final String javaFileName;
    private final int javaLineNum;
    private String jspFileName;
    private int jspBeginLineNum;
    private final StringBuilder errMsg;
    private String jspExtract = null;

    /**
     * @param javaFileName 发生编译错误的Java 文件的名称
     * @param javaLineNum 编译错误行号
     * @param errMsg 编译错误消息
     */
    public JavacErrorDetail(String javaFileName,
                            int javaLineNum,
                            StringBuilder errMsg) {

        this(javaFileName, javaLineNum, null, -1, errMsg, null);
    }

    /**
     * @param javaFileName 发生编译错误的Java 文件的名称
     * @param javaLineNum 编译错误行号
     * @param jspFileName 生成Java源文件的JSP文件的名称
     * @param jspBeginLineNum 负责编译错误的JSP元素的开始行号
     * @param errMsg 编译错误消息
     * @param ctxt 编译上下文
     */
    public JavacErrorDetail(String javaFileName,
            int javaLineNum,
            String jspFileName,
            int jspBeginLineNum,
            StringBuilder errMsg,
            JspCompilationContext ctxt) {

        this.javaFileName = javaFileName;
        this.javaLineNum = javaLineNum;
        this.errMsg = errMsg;
        this.jspFileName = jspFileName;
        // Note: this.jspBeginLineNum设置在该方法的末尾, 因为在执行该方法期间可以修改

        if (jspBeginLineNum > 0 && ctxt != null) {
            InputStream is = null;
            try {
                Jar tagJar = ctxt.getTagFileJar();
                if (tagJar != null) {
                    // Strip leading '/'
                    String entryName = jspFileName.substring(1);
                    is = tagJar.getInputStream(entryName);
                    this.jspFileName = tagJar.getURL(entryName);
                } else {
                    is = ctxt.getResourceAsStream(jspFileName);
                }
                // 读取这两个文件，这样我们就可以检查它们
                String[] jspLines = readFile(is);

                try (FileInputStream fis = new FileInputStream(ctxt.getServletJavaFileName())) {
                    String[] javaLines = readFile(fis);

                    if (jspLines.length < jspBeginLineNum) {
                        // Avoid ArrayIndexOutOfBoundsException
                        // Probably bug 48498 but could be some other cause
                        jspExtract = Localizer.getMessage("jsp.error.bug48498");
                        return;
                    }

                    // 如果该行包含多行脚本块没有关闭, 那么得到的JSP行号可能有故障. 扫描匹配java行...
                    if (jspLines[jspBeginLineNum-1].lastIndexOf("<%") >
                        jspLines[jspBeginLineNum-1].lastIndexOf("%>")) {
                        String javaLine = javaLines[javaLineNum-1].trim();

                        for (int i=jspBeginLineNum-1; i<jspLines.length; i++) {
                            if (jspLines[i].indexOf(javaLine) != -1) {
                                // Update jsp line number
                                jspBeginLineNum = i+1;
                                break;
                            }
                        }
                    }

                    // 复制一个JSP片段以显示给用户
                    StringBuilder fragment = new StringBuilder(1024);
                    int startIndex = Math.max(0, jspBeginLineNum-1-3);
                    int endIndex = Math.min(
                            jspLines.length-1, jspBeginLineNum-1+3);

                    for (int i=startIndex;i<=endIndex; ++i) {
                        fragment.append(i+1);
                        fragment.append(": ");
                        fragment.append(jspLines[i]);
                        fragment.append(System.lineSeparator());
                    }
                    jspExtract = fragment.toString();
                }
            } catch (IOException ioe) {
                // Can't read files - ignore
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException ignore) {
                        // Ignore
                    }
                }
            }
        }
        this.jspBeginLineNum = jspBeginLineNum;
    }

    /**
     * 获取发生编译错误的Java源文件的名称
     *
     * @return Java源文件的名称
     */
    public String getJavaFileName() {
        return this.javaFileName;
    }

    /**
     * 获取编译错误行号.
     *
     * @return 编译错误行数
     */
    public int getJavaLineNumber() {
        return this.javaLineNum;
    }

    /**
     * 获取生成Java源文件的JSP文件的名称.
     */
    public String getJspFileName() {
        return this.jspFileName;
    }

    /**
     * 获取负责编译错误的JSP元素的开始行号.
     */
    public int getJspBeginLineNumber() {
        return this.jspBeginLineNum;
    }

    /**
     * 获取编译错误消息.
     */
    public String getErrorMessage() {
        return this.errMsg.toString();
    }

    /**
     * 获取对应于此消息的JSP的摘录.
     */
    public String getJspExtract() {
        return this.jspExtract;
    }

    /**
     * 将文本文件转换为 String[]. 生成错误消息时, 用于读取JSP和生成的Java文件.
     */
    private String[] readFile(InputStream s) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(s));
        List<String> lines = new ArrayList<>();
        String line;

        while ( (line = reader.readLine()) != null ) {
            lines.add(line);
        }

        return lines.toArray( new String[lines.size()] );
    }
}
