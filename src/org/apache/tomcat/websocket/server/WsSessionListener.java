package org.apache.tomcat.websocket.server;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

public class WsSessionListener implements HttpSessionListener{

    private final WsServerContainer wsServerContainer;


    public WsSessionListener(WsServerContainer wsServerContainer) {
        this.wsServerContainer = wsServerContainer;
    }


    @Override
    public void sessionCreated(HttpSessionEvent se) {
        // NO-OP
    }


    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        wsServerContainer.closeAuthenticatedSession(se.getSession().getId());
    }
}
