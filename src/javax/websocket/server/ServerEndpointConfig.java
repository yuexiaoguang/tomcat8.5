package javax.websocket.server;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;

/**
 * 为发布到服务器端的WebSocket提供配置信息. 应用程序可以提供自己的实现 或使用{@link Builder}.
 */
public interface ServerEndpointConfig extends EndpointConfig {

    Class<?> getEndpointClass();

    /**
     * 返回这个WebSocket在服务端注册的的路径. 它可能是一个路径或一个0级别 URI模板.
     */
    String getPath();

    List<String> getSubprotocols();

    List<Extension> getExtensions();

    Configurator getConfigurator();


    public final class Builder {

        public static Builder create(
                Class<?> endpointClass, String path) {
            return new Builder(endpointClass, path);
        }


        private final Class<?> endpointClass;
        private final String path;
        private List<Class<? extends Encoder>> encoders =
                Collections.emptyList();
        private List<Class<? extends Decoder>> decoders =
                Collections.emptyList();
        private List<String> subprotocols = Collections.emptyList();
        private List<Extension> extensions = Collections.emptyList();
        private Configurator configurator =
                Configurator.fetchContainerDefaultConfigurator();


        private Builder(Class<?> endpointClass,
                String path) {
            this.endpointClass = endpointClass;
            this.path = path;
        }

        public ServerEndpointConfig build() {
            return new DefaultServerEndpointConfig(endpointClass, path,
                    subprotocols, extensions, encoders, decoders, configurator);
        }


        public Builder encoders(
                List<Class<? extends Encoder>> encoders) {
            if (encoders == null || encoders.size() == 0) {
                this.encoders = Collections.emptyList();
            } else {
                this.encoders = Collections.unmodifiableList(encoders);
            }
            return this;
        }


        public Builder decoders(
                List<Class<? extends Decoder>> decoders) {
            if (decoders == null || decoders.size() == 0) {
                this.decoders = Collections.emptyList();
            } else {
                this.decoders = Collections.unmodifiableList(decoders);
            }
            return this;
        }


        public Builder subprotocols(
                List<String> subprotocols) {
            if (subprotocols == null || subprotocols.size() == 0) {
                this.subprotocols = Collections.emptyList();
            } else {
                this.subprotocols = Collections.unmodifiableList(subprotocols);
            }
            return this;
        }


        public Builder extensions(
                List<Extension> extensions) {
            if (extensions == null || extensions.size() == 0) {
                this.extensions = Collections.emptyList();
            } else {
                this.extensions = Collections.unmodifiableList(extensions);
            }
            return this;
        }


        public Builder configurator(Configurator serverEndpointConfigurator) {
            if (serverEndpointConfigurator == null) {
                this.configurator = Configurator.fetchContainerDefaultConfigurator();
            } else {
                this.configurator = serverEndpointConfigurator;
            }
            return this;
        }
    }


    public class Configurator {

        private static volatile Configurator defaultImpl = null;
        private static final Object defaultImplLock = new Object();

        private static final String DEFAULT_IMPL_CLASSNAME =
                "org.apache.tomcat.websocket.server.DefaultServerEndpointConfigurator";

        static Configurator fetchContainerDefaultConfigurator() {
            if (defaultImpl == null) {
                synchronized (defaultImplLock) {
                    if (defaultImpl == null) {
                        defaultImpl = loadDefault();
                    }
                }
            }
            return defaultImpl;
        }


        private static Configurator loadDefault() {
            Configurator result = null;

            ServiceLoader<Configurator> serviceLoader =
                    ServiceLoader.load(Configurator.class);

            Iterator<Configurator> iter = serviceLoader.iterator();
            while (result == null && iter.hasNext()) {
                result = iter.next();
            }

            // Fall-back. Also used by unit tests
            if (result == null) {
                try {
                    @SuppressWarnings("unchecked")
                    Class<Configurator> clazz =
                            (Class<Configurator>) Class.forName(
                                    DEFAULT_IMPL_CLASSNAME);
                    result = clazz.getConstructor().newInstance();
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                        IllegalArgumentException | InvocationTargetException |
                        NoSuchMethodException | SecurityException e) {
                    // No options left. Just return null.
                }
            }
            return result;
        }

        public String getNegotiatedSubprotocol(List<String> supported,
                List<String> requested) {
            return fetchContainerDefaultConfigurator().getNegotiatedSubprotocol(supported, requested);
        }

        public List<Extension> getNegotiatedExtensions(List<Extension> installed,
                List<Extension> requested) {
            return fetchContainerDefaultConfigurator().getNegotiatedExtensions(installed, requested);
        }

        public boolean checkOrigin(String originHeaderValue) {
            return fetchContainerDefaultConfigurator().checkOrigin(originHeaderValue);
        }

        public void modifyHandshake(ServerEndpointConfig sec,
                HandshakeRequest request, HandshakeResponse response) {
            fetchContainerDefaultConfigurator().modifyHandshake(sec, request, response);
        }

        public <T extends Object> T getEndpointInstance(Class<T> clazz)
                throws InstantiationException {
            return fetchContainerDefaultConfigurator().getEndpointInstance(
                    clazz);
        }
    }
}
