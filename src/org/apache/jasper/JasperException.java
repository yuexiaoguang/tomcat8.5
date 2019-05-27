package org.apache.jasper;

/**
 * JSP 引擎所有异常的基类. 让它在顶级捕捉到这一点是很方便的.
 */
public class JasperException extends javax.servlet.ServletException {

    private static final long serialVersionUID = 1L;

    public JasperException(String reason) {
        super(reason);
    }

    public JasperException(String reason, Throwable exception) {
        super(reason, exception);
    }

    public JasperException(Throwable exception) {
        super(exception);
    }
}
