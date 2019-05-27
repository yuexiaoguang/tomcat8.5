package org.apache.tomcat.websocket.pojo;

import java.lang.reflect.Method;

import javax.websocket.PongMessage;
import javax.websocket.Session;

/**
 * 处理整个消息的PongMessage特定消息的具体实现.
 */
public class PojoMessageHandlerWholePong
        extends PojoMessageHandlerWholeBase<PongMessage> {

    public PojoMessageHandlerWholePong(Object pojo, Method method,
            Session session, Object[] params, int indexPayload, boolean convert,
            int indexSession) {
        super(pojo, method, session, params, indexPayload, convert,
                indexSession, -1);
    }

    @Override
    protected Object decode(PongMessage message) {
        // Never decoded
        return null;
    }


    @Override
    protected void onClose() {
        // NO-OP
    }
}
