package javax.websocket.server;

import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;

/**
 * 提供以编程方式部署端点的能力.
 */
public interface ServerContainer extends WebSocketContainer {
    public abstract void addEndpoint(Class<?> clazz) throws DeploymentException;

    public abstract void addEndpoint(ServerEndpointConfig sec)
            throws DeploymentException;
}
