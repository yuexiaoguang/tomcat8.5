package org.apache.juli.logging;


/**
 * <p>只有当适合的<code>LogFactory</code>或<code>Log</code>实例无法通过相应的工厂方法创建.</p>
 */
public class LogConfigurationException extends RuntimeException {


    private static final long serialVersionUID = 1L;


    public LogConfigurationException() {
        super();
    }


    public LogConfigurationException(String message) {
        super(message);
    }


    public LogConfigurationException(Throwable cause) {
        super(cause);
    }


    public LogConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
