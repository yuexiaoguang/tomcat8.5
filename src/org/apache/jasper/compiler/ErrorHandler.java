package org.apache.jasper.compiler;

import org.apache.jasper.JasperException;

/**
 * 用于处理JSP解析和javac编译错误.
 * 
 * 此接口的实现类，可以和ErrorDispatcher一起注册, 通过设置JSP页面编译器的 XXX 初始化参数, 以及执行Catalina的web.xml文件的servlet来实现的完全限定类名.
 */
public interface ErrorHandler {

    /**
     * 处理给定的JSP解析错误.
     *
     * @param fname 发生解析错误的JSP文件的名称
     * @param line 解析错误行号
     * @param column 解析错误列号
     * @param msg 解析错误消息
     * @param exception 解析异常
     * 
     * @throws JasperException An error occurred
     */
    public void jspError(String fname, int line, int column, String msg,
            Exception exception) throws JasperException;

    /**
     * 处理给定的JSP解析错误.
     *
     * @param msg 解析错误消息
     * @param exception 解析异常
     * 
     * @throws JasperException An error occurred
     */
    public void jspError(String msg, Exception exception)
            throws JasperException;

    /**
     * 处理给定的javac编译错误.
     *
     * @param details JavacErrorDetail实例数组, 对应于编译错误
     * 
     * @throws JasperException An error occurred
     */
    public void javacError(JavacErrorDetail[] details)
            throws JasperException;

    /**
     * 处理给定的javac错误报告和异常.
     *
     * @param errorReport 编译错误报告
     * @param exception 编译异常
     * 
     * @throws JasperException An error occurred
     */
    public void javacError(String errorReport, Exception exception)
            throws JasperException;
}
