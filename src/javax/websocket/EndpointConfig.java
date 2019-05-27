package javax.websocket;

import java.util.List;
import java.util.Map;

public interface EndpointConfig {

    List<Class<? extends Encoder>> getEncoders();

    List<Class<? extends Decoder>> getDecoders();

    Map<String,Object> getUserProperties();
}
