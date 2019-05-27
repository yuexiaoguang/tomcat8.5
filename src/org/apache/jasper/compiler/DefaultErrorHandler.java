package org.apache.jasper.compiler;

import org.apache.jasper.JasperException;

/**
 * ErrorHandler接口的默认实现类.
 */
class DefaultErrorHandler implements ErrorHandler {

    /*
     * 处理指定的JSP 解析错误.
     *
     * @param fname 发生解析错误的JSP文件的名称
     * @param line 解析错误行号
     * @param column 解析错误列号
     * @param errMsg 解析错误消息
     * @param exception 解析异常
     */
    @Override
    public void jspError(String fname, int line, int column, String errMsg,
            Exception ex) throws JasperException {
        throw new JasperException(fname + " (" +
                Localizer.getMessage("jsp.error.location",
                        Integer.toString(line), Integer.toString(column)) +
                ") " + errMsg, ex);
    }

    /*
     * 处理指定的JSP 解析错误.
     *
     * @param errMsg 解析错误消息
     * @param exception 解析异常
     */
    @Override
    public void jspError(String errMsg, Exception ex) throws JasperException {
        throw new JasperException(errMsg, ex);
    }

    /*
     * 处理指定的 javac 编译错误.
     *
     * @param details JavacErrorDetail实例数组, 对应编译错误
     */
    @Override
    public void javacError(JavacErrorDetail[] details) throws JasperException {

        if (details == null) {
            return;
        }

        Object[] args = null;
        StringBuilder buf = new StringBuilder();

        for (int i=0; i < details.length; i++) {
            if (details[i].getJspBeginLineNumber() >= 0) {
                args = new Object[] {
                        Integer.valueOf(details[i].getJspBeginLineNumber()),
                        details[i].getJspFileName() };
                buf.append(System.lineSeparator());
                buf.append(System.lineSeparator());
                buf.append(Localizer.getMessage("jsp.error.single.line.number",
                        args));
                buf.append(System.lineSeparator());
                buf.append(details[i].getErrorMessage());
                buf.append(System.lineSeparator());
                buf.append(details[i].getJspExtract());
            } else {
                args = new Object[] {
                        Integer.valueOf(details[i].getJavaLineNumber()),
                        details[i].getJavaFileName() };
                buf.append(System.lineSeparator());
                buf.append(System.lineSeparator());
                buf.append(Localizer.getMessage("jsp.error.java.line.number",
                        args));
                buf.append(System.lineSeparator());
                buf.append(details[i].getErrorMessage());
            }
        }
        buf.append(System.lineSeparator());
        buf.append(System.lineSeparator());
        buf.append("Stacktrace:");
        throw new JasperException(
                Localizer.getMessage("jsp.error.unable.compile") + ": " + buf);
    }

    /**
     * 处理指定的 javac 错误报告和异常.
     *
     * @param errorReport 编译错误报告
     * @param exception 编译异常
     */
    @Override
    public void javacError(String errorReport, Exception exception)
    throws JasperException {

        throw new JasperException(
                Localizer.getMessage("jsp.error.unable.compile"), exception);
    }

}
