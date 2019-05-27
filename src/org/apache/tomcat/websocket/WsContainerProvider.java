package org.apache.tomcat.websocket;

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

public class WsContainerProvider extends ContainerProvider {

    @Override
    protected WebSocketContainer getContainer() {
        return new WsWebSocketContainer();
    }
}
