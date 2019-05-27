package javax.websocket;

public final class SendResult {
    private final Throwable exception;
    private final boolean ok;

    public SendResult(Throwable exception) {
        this.exception = exception;
        this.ok = (exception == null);
    }

    public SendResult() {
        this (null);
    }

    public Throwable getException() {
        return exception;
    }

    public boolean isOK() {
        return ok;
    }
}
