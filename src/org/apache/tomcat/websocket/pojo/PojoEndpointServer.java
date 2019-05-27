package org.apache.tomcat.websocket.pojo;

import java.util.Map;

import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.tomcat.util.res.StringManager;

/**
 * {@link javax.websocket.server.ServerEndpoint}注解的POJO的实例的包装器类, 因此它们出现为标准{@link javax.websocket.Endpoint}实例.
 */
public class PojoEndpointServer extends PojoEndpointBase {

    private static final StringManager sm =
            StringManager.getManager(PojoEndpointServer.class);

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {

        ServerEndpointConfig sec = (ServerEndpointConfig) endpointConfig;

        Object pojo;
        try {
            pojo = sec.getConfigurator().getEndpointInstance(
                    sec.getEndpointClass());
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(sm.getString(
                    "pojoEndpointServer.getPojoInstanceFail",
                    sec.getEndpointClass().getName()), e);
        }
        setPojo(pojo);

        @SuppressWarnings("unchecked")
        Map<String,String> pathParameters =
                (Map<String, String>) sec.getUserProperties().get(
                        Constants.POJO_PATH_PARAM_KEY);
        setPathParameters(pathParameters);

        PojoMethodMapping methodMapping =
                (PojoMethodMapping) sec.getUserProperties().get(
                        Constants.POJO_METHOD_MAPPING_KEY);
        setMethodMapping(methodMapping);

        doOnOpen(session, endpointConfig);
    }
}
