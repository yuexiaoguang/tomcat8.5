package javax.websocket.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.websocket.Decoder;
import javax.websocket.Encoder;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ServerEndpoint {

    /**
     * 注解的类应该映射到的URI 或 URI-template.
     */
    String value();

    String[] subprotocols() default {};

    Class<? extends Decoder>[] decoders() default {};

    Class<? extends Encoder>[] encoders() default {};

    public Class<? extends ServerEndpointConfig.Configurator> configurator()
            default ServerEndpointConfig.Configurator.class;
}
