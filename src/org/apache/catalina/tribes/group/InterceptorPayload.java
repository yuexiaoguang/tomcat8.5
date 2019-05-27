package org.apache.catalina.tribes.group;

import org.apache.catalina.tribes.ErrorHandler;

public class InterceptorPayload  {
    private ErrorHandler errorHandler;

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }
}