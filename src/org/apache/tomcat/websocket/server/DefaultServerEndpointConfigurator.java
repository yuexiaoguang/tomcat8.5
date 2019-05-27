package org.apache.tomcat.websocket.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

public class DefaultServerEndpointConfigurator
        extends ServerEndpointConfig.Configurator {

    @Override
    public <T> T getEndpointInstance(Class<T> clazz)
            throws InstantiationException {
        try {
            return clazz.getConstructor().newInstance();
        } catch (InstantiationException e) {
            throw e;
        } catch (ReflectiveOperationException e) {
            InstantiationException ie = new InstantiationException();
            ie.initCause(e);
            throw ie;
        }
    }


    @Override
    public String getNegotiatedSubprotocol(List<String> supported,
            List<String> requested) {

        for (String request : requested) {
            if (supported.contains(request)) {
                return request;
            }
        }
        return "";
    }


    @Override
    public List<Extension> getNegotiatedExtensions(List<Extension> installed,
            List<Extension> requested) {
        Set<String> installedNames = new HashSet<>();
        for (Extension e : installed) {
            installedNames.add(e.getName());
        }
        List<Extension> result = new ArrayList<>();
        for (Extension request : requested) {
            if (installedNames.contains(request.getName())) {
                result.add(request);
            }
        }
        return result;
    }


    @Override
    public boolean checkOrigin(String originHeaderValue) {
        return true;
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig sec,
            HandshakeRequest request, HandshakeResponse response) {
        // NO-OP
    }

}
