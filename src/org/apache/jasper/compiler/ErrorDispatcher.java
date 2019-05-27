package org.apache.jasper.compiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.util.ArrayList;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.xml.sax.SAXException;

/**
 * 负责调度JSP解析和javac编译错误到配置错误处理程序.
 *
 * 该类还负责定位任何错误代码, 在将它们传递给已配置的错误处理程序之前.
 * 
 * 在一个java编译错误的情况下, 编译器错误信息解析为JavacErrorDetail 实例数组, 传递到已配置的错误处理程序.
 */
public class ErrorDispatcher {

    /**
     * 自定义错误处理程序
     */
    private final ErrorHandler errHandler;

    /**
     * 指示是否采用JspServlet或JspC编译
     */
    private final boolean jspcMode;


    /**
     * @param jspcMode true 如果JspC已开始编译, 否则false
     */
    public ErrorDispatcher(boolean jspcMode) {
        // XXX check web.xml for custom error handler
        errHandler = new DefaultErrorHandler();
        this.jspcMode = jspcMode;
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param errCode 错误码
     * @param args 替换的参数
     * 
     * @throws JasperException An error occurred
     */
    public void jspError(String errCode, String... args) throws JasperException {
        dispatch(null, errCode, args, null);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param where 错误的位置
     * @param errCode 错误码
     * @param args 替换的参数
     * 
     * @throws JasperException An error occurred
     */
    public void jspError(Mark where, String errCode, String... args)
            throws JasperException {
        dispatch(where, errCode, args, null);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param where 错误的位置
     * @param errCode 错误码
     * @param args 替换的参数
     * 
     * @throws JasperException An error occurred
     */
    public void jspError(Node n, String errCode, String... args)
            throws JasperException {
        dispatch(n.getStart(), errCode, args, null);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * @param e 解析错误
     * 
     * @throws JasperException An error occurred
     */
    public void jspError(Exception e) throws JasperException {
        dispatch(null, null, null, e);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param errCode 错误码
     * @param args 替换的参数
     * @param e 解析错误
     * 
     * @throws JasperException An error occurred
     */
    public void jspError(Exception e, String errCode, String... args)
                throws JasperException {
        dispatch(null, errCode, args, e);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param where 错误的位置
     * @param e 解析错误
     * @param errCode 错误码
     * @param args 替换的参数
     * 
     * @throws JasperException An error occurred
     */
    public void jspError(Mark where, Exception e, String errCode, String... args)
                throws JasperException {
        dispatch(where, errCode, args, e);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param n 导致错误的节点
     * @param e 解析错误
     * @param errCode 错误码
     * @param args 替换的参数
     * 
     * @throws JasperException An error occurred
     */
    public void jspError(Node n, Exception e, String errCode, String... args)
                throws JasperException {
        dispatch(n.getStart(), errCode, args, e);
    }

    /**
     * 解析给定的错误信息到javac编译错误信息数组中(每一个javac编译错误的行数).
     *
     * @param errMsg 错误信息
     * @param fname 编译失败的java源文件的名字
     * @param page java源文件生成的JSP页面的Node 表示形式
     *
     * @return javac编译错误数组, 或null 如果给定的错误消息不包含任何编译错误行号
     * 
     * @throws JasperException An error occurred
     * @throws IOException 通常不应该发生的IO错误
     */
    public static JavacErrorDetail[] parseJavacErrors(String errMsg,
                                                      String fname,
                                                      Node.Nodes page)
            throws JasperException, IOException {

        return parseJavacMessage(errMsg, fname, page);
    }

    /**
     * 将给定的javac编译错误分配到配置的错误处理程序.
     *
     * @param javacErrors javac编译错误数组
     * 
     * @throws JasperException An error occurred
     */
    public void javacError(JavacErrorDetail[] javacErrors)
            throws JasperException {

        errHandler.javacError(javacErrors);
    }


    /**
     * 将给定的编译错误报告和异常分派给已配置的错误处理程序.
     *
     * @param errorReport 编译错误报告
     * @param e 编译异常
     * 
     * @throws JasperException An error occurred
     */
    public void javacError(String errorReport, Exception e)
                throws JasperException {

        errHandler.javacError(errorReport, e);
    }


    //*********************************************************************
    // Private utility methods

    /**
     * 将给定的JSP解析错误分派到已配置的错误处理程序.
     *
     * 给定的错误码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param where 错误的位置
     * @param errCode 错误码
     * @param args 参数替换的参数
     * @param e 解析的异常
     * 
     * @throws JasperException An error occurred
     */
    private void dispatch(Mark where, String errCode, Object[] args,
                          Exception e) throws JasperException {
        String file = null;
        String errMsg = null;
        int line = -1;
        int column = -1;
        boolean hasLocation = false;

        // Localize
        if (errCode != null) {
            errMsg = Localizer.getMessage(errCode, args);
        } else if (e != null) {
            // give a hint about what's wrong
            errMsg = e.getMessage();
        }

        // Get error location
        if (where != null) {
            if (jspcMode) {
                // 获取引起错误的资源的完整URL
                try {
                    file = where.getURL().toString();
                } catch (MalformedURLException me) {
                    // 使用上下文相对路径
                    file = where.getFile();
                }
            } else {
                // 获取上下文相对资源路径, 以便不泄露任何本地文件系统细节
                file = where.getFile();
            }
            line = where.getLineNumber();
            column = where.getColumnNumber();
            hasLocation = true;
        }

        // 获取嵌套异常
        Exception nestedEx = e;
        if ((e instanceof SAXException)
                && (((SAXException) e).getException() != null)) {
            nestedEx = ((SAXException) e).getException();
        }

        if (hasLocation) {
            errHandler.jspError(file, line, column, errMsg, nestedEx);
        } else {
            errHandler.jspError(errMsg, nestedEx);
        }
    }

    /**
     * 解析java编译错误信息, 其中可能包含一个或多个编译错误, 返回JavacErrorDetail 实例数组.
     *
     * 每个JavacErrorDetail实例包含一个编译错误的信息.
     *
     * @param errMsg javac编译器生成的编译错误信息
     * @param fname 编译失败的java源文件名称
     * @param page java源文件生成的JSP页面的节点
     *
     * @return JavacErrorDetail实例数组, 对应于编译错误
     * 
     * @throws JasperException An error occurred
     * @throws IOException 通常不应该发生的IO错误
     */
    private static JavacErrorDetail[] parseJavacMessage(
                                String errMsg, String fname, Node.Nodes page)
                throws IOException, JasperException {

        ArrayList<JavacErrorDetail> errors = new ArrayList<>();
        StringBuilder errMsgBuf = null;
        int lineNum = -1;
        JavacErrorDetail javacError = null;

        BufferedReader reader = new BufferedReader(new StringReader(errMsg));

        /*
         * 解析编译错误. 每个编译错误由一个文件路径和错误行号组成, 其次是描述错误的若干行.
         */
        String line = null;
        while ((line = reader.readLine()) != null) {

            /*
             * 错误行号由冒号分隔的集.
             * 在Windows上忽略驱动器后面的冒号 (fromIndex = 2).
             * XXX 处理但是没有行信息
             */
            int beginColon = line.indexOf(':', 2);
            int endColon = line.indexOf(':', beginColon + 1);
            if ((beginColon >= 0) && (endColon >= 0)) {
                if (javacError != null) {
                    // 将以前的错误添加到错误集合
                    errors.add(javacError);
                }

                String lineNumStr = line.substring(beginColon + 1, endColon);
                try {
                    lineNum = Integer.parseInt(lineNumStr);
                } catch (NumberFormatException e) {
                    lineNum = -1;
                }

                errMsgBuf = new StringBuilder();

                javacError = createJavacError(fname, page, errMsgBuf, lineNum);
            }

            // 忽略第一个错误之前的消息
            if (errMsgBuf != null) {
                errMsgBuf.append(line);
                errMsgBuf.append(System.lineSeparator());
            }
        }

        // 将最后一个错误添加到错误集合中
        if (javacError != null) {
            errors.add(javacError);
        }

        reader.close();

        JavacErrorDetail[] errDetails = null;
        if (errors.size() > 0) {
            errDetails = new JavacErrorDetail[errors.size()];
            errors.toArray(errDetails);
        }

        return errDetails;
    }


    /**
     * 创建一个编译错误.
     * 
     * @param fname 文件名
     * @param page 页面节点
     * @param errMsgBuf 错误信息
     * @param lineNum 错误的源行号
     * 
     * @return JavacErrorDetail The error details
     * @throws JasperException An error occurred
     */
    public static JavacErrorDetail createJavacError(String fname,
            Node.Nodes page, StringBuilder errMsgBuf, int lineNum)
    throws JasperException {
        return createJavacError(fname, page, errMsgBuf, lineNum, null);
    }


    /**
     * 创建一个编译错误.
     * 
     * @param fname 文件名
     * @param page 页面节点
     * @param errMsgBuf 错误信息
     * @param lineNum 错误的源行号
     * @param ctxt 编译上下文
     * 
     * @return JavacErrorDetail The error details
     * @throws JasperException An error occurred
     */
    public static JavacErrorDetail createJavacError(String fname,
            Node.Nodes page, StringBuilder errMsgBuf, int lineNum,
            JspCompilationContext ctxt) throws JasperException {
        JavacErrorDetail javacError;
        // 尝试映射javac错误行号到JSP页面行
        ErrorVisitor errVisitor = new ErrorVisitor(lineNum);
        page.visit(errVisitor);
        Node errNode = errVisitor.getJspSourceNode();
        if ((errNode != null) && (errNode.getStart() != null)) {
            // 如果这是一个scriplet节点, 那么在JSP行和Java行之间是一对一映射
            if (errVisitor.getJspSourceNode() instanceof Node.Scriptlet ||
                    errVisitor.getJspSourceNode() instanceof Node.Declaration) {
                javacError = new JavacErrorDetail(
                        fname,
                        lineNum,
                        errNode.getStart().getFile(),
                        errNode.getStart().getLineNumber() + lineNum -
                            errVisitor.getJspSourceNode().getBeginJavaLine(),
                        errMsgBuf,
                        ctxt);
            } else {
                javacError = new JavacErrorDetail(
                        fname,
                        lineNum,
                        errNode.getStart().getFile(),
                        errNode.getStart().getLineNumber(),
                        errMsgBuf,
                        ctxt);
            }
        } else {
            /*
             * javac错误行号不能映射到JSP页面的行数. 例如, 如果一个脚本缺少右括号, 将破坏代码生成器位置的try-catch-finally 块:
             * 结果就是, javac错误行号将超出为脚本生成的java行号的开始和结束范围, 因此不能被映射到在JSP页面中的脚本开始的行数.
             * 错误详情中只包括 javac错误信息.
             */
            javacError = new JavacErrorDetail(
                    fname,
                    lineNum,
                    errMsgBuf);
        }
        return javacError;
    }


    /**
     * 访问者，负责将生成的servlet源代码中的行号映射到相应的JSP节点.
     */
    private static class ErrorVisitor extends Node.Visitor {

        /**
         * 要映射的java的源代码行号
         */
        private final int lineNum;

        /**
         * 在生成的servlet的Java源代码范围包含的Java源行号要映射的JSP 节点
         */
        private Node found;

        /**
         * @param lineNum 生成的servlet代码中的源行号
         */
        public ErrorVisitor(int lineNum) {
            this.lineNum = lineNum;
        }

        @Override
        public void doVisit(Node n) throws JasperException {
            if ((lineNum >= n.getBeginJavaLine())
                    && (lineNum < n.getEndJavaLine())) {
                found = n;
            }
        }

        /**
         * 获取生成的servlet代码中的源行号映射的 JSP 节点.
         *
         * @return 映射生成的servlet代码中的源代码行的JSP节点
         */
        public Node getJspSourceNode() {
            return found;
        }
    }
}
