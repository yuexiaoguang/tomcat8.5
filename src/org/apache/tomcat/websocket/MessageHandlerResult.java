package org.apache.tomcat.websocket;

import javax.websocket.MessageHandler;

public class MessageHandlerResult {

    private final MessageHandler handler;
    private final MessageHandlerResultType type;


    public MessageHandlerResult(MessageHandler handler,
            MessageHandlerResultType type) {
        this.handler = handler;
        this.type = type;
    }


    public MessageHandler getHandler() {
        return handler;
    }


    public MessageHandlerResultType getType() {
        return type;
    }
}
