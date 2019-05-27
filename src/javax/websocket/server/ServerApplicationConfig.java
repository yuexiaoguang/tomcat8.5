package javax.websocket.server;

import java.util.Set;

import javax.websocket.Endpoint;

/**
 * 应用程序可以提供此接口的实现, 过滤发现的WebSocket 端点. 这个类的实现可以通过ServletContainerInitializer扫描去发现.
 */
public interface ServerApplicationConfig {

    /**
     * 启用应用来过滤发现的{@link ServerEndpointConfig}实现类.
     *
     * @param scanned   应用中找到的{@link Endpoint}实现类
     * @return  应用程序希望部署的端点的一组配置
     */
    Set<ServerEndpointConfig> getEndpointConfigs(
            Set<Class<? extends Endpoint>> scanned);

    /**
     * 启用应用来过滤发现的{@link ServerEndpoint}注解的类.
     *
     * @param scanned   应用中找到的{@link ServerEndpoint}注解的POJO
     * 
     * @return  应用希望部署的一组POJO
     */
    Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned);
}
