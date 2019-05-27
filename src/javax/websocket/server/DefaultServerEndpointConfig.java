package javax.websocket.server;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Extension;

/**
 * 提供服务端WebSocket的默认配置.
 */
final class DefaultServerEndpointConfig implements ServerEndpointConfig {

    private final Class<?> endpointClass;
    private final String path;
    private final List<String> subprotocols;
    private final List<Extension> extensions;
    private final List<Class<? extends Encoder>> encoders;
    private final List<Class<? extends Decoder>> decoders;
    private final Configurator serverEndpointConfigurator;
    private final Map<String,Object> userProperties = new ConcurrentHashMap<>();

    DefaultServerEndpointConfig(
            Class<?> endpointClass, String path,
            List<String> subprotocols, List<Extension> extensions,
            List<Class<? extends Encoder>> encoders,
            List<Class<? extends Decoder>> decoders,
            Configurator serverEndpointConfigurator) {
        this.endpointClass = endpointClass;
        this.path = path;
        this.subprotocols = subprotocols;
        this.extensions = extensions;
        this.encoders = encoders;
        this.decoders = decoders;
        this.serverEndpointConfigurator = serverEndpointConfigurator;
    }

    @Override
    public Class<?> getEndpointClass() {
        return endpointClass;
    }

    @Override
    public List<Class<? extends Encoder>> getEncoders() {
        return this.encoders;
    }

    @Override
    public List<Class<? extends Decoder>> getDecoders() {
        return this.decoders;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public Configurator getConfigurator() {
        return serverEndpointConfigurator;
    }

    @Override
    public final Map<String, Object> getUserProperties() {
        return userProperties;
    }

    @Override
    public final List<String> getSubprotocols() {
        return subprotocols;
    }

    @Override
    public final List<Extension> getExtensions() {
        return extensions;
    }
}
