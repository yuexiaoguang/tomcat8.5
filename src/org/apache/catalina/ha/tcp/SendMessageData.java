package org.apache.catalina.ha.tcp;

import org.apache.catalina.tribes.Member;

public class SendMessageData {

    private Object message ;
    private Member destination ;
    private Exception exception ;


    /**
     * @param message 要发送的消息
     * @param destination Member目的地
     * @param exception 关联的错误
     */
    public SendMessageData(Object message, Member destination,
            Exception exception) {
        super();
        this.message = message;
        this.destination = destination;
        this.exception = exception;
    }

    public Member getDestination() {
        return destination;
    }
    public Exception getException() {
        return exception;
    }
    public Object getMessage() {
        return message;
    }
}
