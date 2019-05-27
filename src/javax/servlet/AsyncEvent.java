package javax.servlet;

public class AsyncEvent {
    private final AsyncContext context;
    private final ServletRequest request;
    private final ServletResponse response;
    private final Throwable throwable;

    public AsyncEvent(AsyncContext context) {
        this.context = context;
        this.request = null;
        this.response = null;
        this.throwable = null;
    }

    public AsyncEvent(AsyncContext context, ServletRequest request,
            ServletResponse response) {
        this.context = context;
        this.request = request;
        this.response = response;
        this.throwable = null;
    }

    public AsyncEvent(AsyncContext context, Throwable throwable) {
        this.context = context;
        this.throwable = throwable;
        this.request = null;
        this.response = null;
    }

    public AsyncEvent(AsyncContext context, ServletRequest request,
            ServletResponse response, Throwable throwable) {
        this.context = context;
        this.request = request;
        this.response = response;
        this.throwable = throwable;
    }

    public AsyncContext getAsyncContext() {
        return context;
    }

    public ServletRequest getSuppliedRequest() {
        return request;
    }

    public ServletResponse getSuppliedResponse() {
        return response;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
