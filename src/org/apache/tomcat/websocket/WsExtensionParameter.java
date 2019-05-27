package org.apache.tomcat.websocket;

import javax.websocket.Extension.Parameter;

public class WsExtensionParameter implements Parameter {

    private final String name;
    private final String value;

    WsExtensionParameter(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getValue() {
        return value;
    }
}
