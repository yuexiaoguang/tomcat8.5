package org.apache.catalina.core;

import java.io.IOException;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class AsyncListenerWrapper {

    private AsyncListener listener = null;
    private ServletRequest servletRequest = null;
    private ServletResponse servletResponse = null;


    public void fireOnStartAsync(AsyncEvent event) throws IOException {
        listener.onStartAsync(customizeEvent(event));
    }


    public void fireOnComplete(AsyncEvent event) throws IOException {
        listener.onComplete(customizeEvent(event));
    }


    public void fireOnTimeout(AsyncEvent event) throws IOException {
        listener.onTimeout(customizeEvent(event));
    }


    public void fireOnError(AsyncEvent event) throws IOException {
        listener.onError(customizeEvent(event));
    }


    public AsyncListener getListener() {
        return listener;
    }


    public void setListener(AsyncListener listener) {
        this.listener = listener;
    }


    public void setServletRequest(ServletRequest servletRequest) {
        this.servletRequest = servletRequest;
    }


    public void setServletResponse(ServletResponse servletResponse) {
        this.servletResponse = servletResponse;
    }


    private AsyncEvent customizeEvent(AsyncEvent event) {
        if (servletRequest != null && servletResponse != null) {
            return new AsyncEvent(event.getAsyncContext(), servletRequest, servletResponse,
                    event.getThrowable());
        } else {
            return event;
        }
    }
}
