package org.apache.tomcat.websocket;

import java.util.ArrayList;
import java.util.List;

import javax.websocket.Extension;

public class WsExtension implements Extension {

    private final String name;
    private final List<Parameter> parameters = new ArrayList<>();

    WsExtension(String name) {
        this.name = name;
    }

    void addParameter(Parameter parameter) {
        parameters.add(parameter);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Parameter> getParameters() {
        return parameters;
    }
}
