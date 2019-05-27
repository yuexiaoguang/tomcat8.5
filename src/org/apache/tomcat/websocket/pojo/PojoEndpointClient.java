package org.apache.tomcat.websocket.pojo;

import java.util.Collections;
import java.util.List;

import javax.websocket.Decoder;
import javax.websocket.DeploymentException;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;


/**
 * {@link javax.websocket.ClientEndpoint}注解的 POJO实例的包装器类, 因此它们出现为标准{@link javax.websocket.Endpoint}实例.
 */
public class PojoEndpointClient extends PojoEndpointBase {

    public PojoEndpointClient(Object pojo,
            List<Class<? extends Decoder>> decoders) throws DeploymentException {
        setPojo(pojo);
        setMethodMapping(
                new PojoMethodMapping(pojo.getClass(), decoders, null));
        setPathParameters(Collections.<String,String>emptyMap());
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        doOnOpen(session, config);
    }
}
