package org.apache.coyote;

import java.io.IOException;

/**
 * 向Tomcat内部发信号, 发生错误需要关闭连接. 对于多路复用协议, 例如 HTTP/2, 意味着必须关闭 channel, 但是连接可以继续.
 * 对于非多路复用协议, 必须关闭连接. 它对应于 {@link ErrorState#CLOSE_NOW}.
 */
public class CloseNowException extends IOException {

    private static final long serialVersionUID = 1L;


    public CloseNowException() {
        super();
    }


    public CloseNowException(String message, Throwable cause) {
        super(message, cause);
    }


    public CloseNowException(String message) {
        super(message);
    }


    public CloseNowException(Throwable cause) {
        super(cause);
    }
}
