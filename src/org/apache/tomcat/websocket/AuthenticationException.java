package org.apache.tomcat.websocket;

/**
 * 连接到远程websocket 端点的身份验证错误引发的异常.
 */
public class AuthenticationException extends Exception {

    private static final long serialVersionUID = 5709887412240096441L;

    public AuthenticationException(String message) {
        super(message);
    }

}
