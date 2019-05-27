package org.apache.tomcat.websocket.pojo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import javax.websocket.DecodeException;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.apache.tomcat.websocket.WsSession;

/**
 * POJO部分消息处理程序的通用实现代码. 所有真正的工作都是在这一类和超类中完成的.
 *
 * @param <T>   要处理的消息类型
 */
public abstract class PojoMessageHandlerPartialBase<T>
        extends PojoMessageHandlerBase<T> implements MessageHandler.Partial<T> {

    private final int indexBoolean;

    public PojoMessageHandlerPartialBase(Object pojo, Method method,
            Session session, Object[] params, int indexPayload,
            boolean convert, int indexBoolean, int indexSession,
            long maxMessageSize) {
        super(pojo, method, session, params, indexPayload, convert,
                indexSession, maxMessageSize);
        this.indexBoolean = indexBoolean;
    }


    @Override
    public final void onMessage(T message, boolean last) {
        if (params.length == 1 && params[0] instanceof DecodeException) {
            ((WsSession) session).getLocal().onError(session,
                    (DecodeException) params[0]);
            return;
        }
        Object[] parameters = params.clone();
        if (indexBoolean != -1) {
            parameters[indexBoolean] = Boolean.valueOf(last);
        }
        if (indexSession != -1) {
            parameters[indexSession] = session;
        }
        if (convert) {
            parameters[indexPayload] = ((ByteBuffer) message).array();
        } else {
            parameters[indexPayload] = message;
        }
        Object result = null;
        try {
            result = method.invoke(pojo, parameters);
        } catch (IllegalAccessException | InvocationTargetException e) {
            handlePojoMethodException(e);
        }
        processResult(result);
    }
}
