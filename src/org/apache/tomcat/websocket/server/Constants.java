package org.apache.tomcat.websocket.server;

public class Constants {

    public static final String BINARY_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM =
            "org.apache.tomcat.websocket.binaryBufferSize";
    public static final String TEXT_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM =
            "org.apache.tomcat.websocket.textBufferSize";
    public static final String ENFORCE_NO_ADD_AFTER_HANDSHAKE_CONTEXT_INIT_PARAM =
            "org.apache.tomcat.websocket.noAddAfterHandshake";

    public static final String SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE =
            "javax.websocket.server.ServerContainer";


    private Constants() {
        // Hide default constructor
    }
}
