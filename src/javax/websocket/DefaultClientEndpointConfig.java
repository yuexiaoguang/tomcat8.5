package javax.websocket;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class DefaultClientEndpointConfig implements ClientEndpointConfig {

    private final List<String> preferredSubprotocols;
    private final List<Extension> extensions;
    private final List<Class<? extends Encoder>> encoders;
    private final List<Class<? extends Decoder>> decoders;
    private final Map<String,Object> userProperties = new ConcurrentHashMap<>();
    private final Configurator configurator;


    DefaultClientEndpointConfig(List<String> preferredSubprotocols,
            List<Extension> extensions,
            List<Class<? extends Encoder>> encoders,
            List<Class<? extends Decoder>> decoders,
            Configurator configurator) {
        this.preferredSubprotocols = preferredSubprotocols;
        this.extensions = extensions;
        this.decoders = decoders;
        this.encoders = encoders;
        this.configurator = configurator;
    }


    @Override
    public List<String> getPreferredSubprotocols() {
        return preferredSubprotocols;
    }


    @Override
    public List<Extension> getExtensions() {
        return extensions;
    }


    @Override
    public List<Class<? extends Encoder>> getEncoders() {
        return encoders;
    }


    @Override
    public List<Class<? extends Decoder>> getDecoders() {
        return decoders;
    }


    @Override
    public final Map<String, Object> getUserProperties() {
        return userProperties;
    }


    @Override
    public Configurator getConfigurator() {
        return configurator;
    }
}
