package org.apache.tomcat.websocket;

import javax.websocket.MessageHandler;

public interface WrappedMessageHandler {
    long getMaxMessageSize();

    MessageHandler getWrappedHandler();
}
