package org.apache.catalina;


/**
 * 与生命周期相关的问题的通用异常. 这种异常通常被认为对包含此组件的应用程序的操作是致命的.
 */
public final class LifecycleException extends Exception {

    private static final long serialVersionUID = 1L;

    //------------------------------------------------------------ Constructors


    public LifecycleException() {
        super();
    }


    public LifecycleException(String message) {
        super(message);
    }


    public LifecycleException(Throwable throwable) {
        super(throwable);
    }


    public LifecycleException(String message, Throwable throwable) {
        super(message, throwable);
    }
}