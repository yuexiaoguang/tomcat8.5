package org.apache.jasper.runtime;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.jsp.JspWriter;

/**
 * JSP 'include'操作使用的ServletResponseWrapper.
 *
 * 这个包装响应对象传递给 RequestDispatcher.include(), 这样包含的资源的输出被追加到包含的页面的输出中.
 */
public class ServletResponseWrapperInclude extends HttpServletResponseWrapper {

    /**
     * PrintWriter追加到包含的页面的JspWriter.
     */
    private final PrintWriter printWriter;

    private final JspWriter jspWriter;

    public ServletResponseWrapperInclude(ServletResponse response,
                                         JspWriter jspWriter) {
        super((HttpServletResponse)response);
        this.printWriter = new PrintWriter(jspWriter);
        this.jspWriter = jspWriter;
    }

    /**
     * 返回一个包含的页面的包装器JspWriter.
     */
    @Override
    public PrintWriter getWriter() throws IOException {
        return printWriter;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        throw new IllegalStateException();
    }

    /**
     * 清除JspWriter的输出缓冲区.
     */
    @Override
    public void resetBuffer() {
        try {
            jspWriter.clearBuffer();
        } catch (IOException ioe) {
        }
    }
}
