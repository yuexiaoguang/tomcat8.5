package org.apache.coyote;

public enum ErrorState {

    /**
     * 不处于错误状态.
     */
    NONE(false, 0, true, true),

    /**
     * 当前请求/响应处于错误状态，虽然完成当前响应是安全的，但继续使用现有的连接是不安全的，一旦响应完成，必须关闭该连接.
     * 对于多路复用协议, 当前请求/响应完成时必须关闭 channel, 但是连接可能继续.
     */
    CLOSE_CLEAN(true, 1, true, true),

    /**
     * 当前请求/响应处于错误状态，继续使用它们是不安全的.
     * 对于多路复用协议 (例如 HTTP/2), 必须直接关闭 stream/channel, 但是连接可能继续.
     * 对于非多路复用协议 (AJP, HTTP/1.x), 必须关闭当前连接.
     */
    CLOSE_NOW(true, 2, false, true),

    /**
     * 检测到影响底层网络连接的错误. 继续使用必须立即关闭的网络连接是不安全的.
     * 对于多路复用协议 (例如 HTTP/2), 这影响了所有多路复用 channel.
     */
    CLOSE_CONNECTION_NOW(true, 3, false, false);

    private final boolean error;
    private final int severity;
    private final boolean ioAllowed;
    private final boolean connectionIoAllowed;

    private ErrorState(boolean error, int severity, boolean ioAllowed,
            boolean connectionIoAllowed) {
        this.error = error;
        this.severity = severity;
        this.ioAllowed = ioAllowed;
        this.connectionIoAllowed = connectionIoAllowed;
    }

    public boolean isError() {
        return error;
    }

    /**
     * 比较 ErrorState, 返回更严重的.
     *
     * @param input 与此比较的错误状态
     *
     * @return 最严重的错误状态
     */
    public ErrorState getMostSevere(ErrorState input) {
        if (input.severity > this.severity) {
            return input;
        } else {
            return this;
        }
    }

    public boolean isIoAllowed() {
        return ioAllowed;
    }

    public boolean isConnectionIoAllowed() {
        return connectionIoAllowed;
    }
}
