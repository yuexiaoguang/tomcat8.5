package org.apache.tomcat.websocket.pojo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.websocket.DecodeException;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.apache.tomcat.websocket.WsSession;

/**
 * POJO全消息处理程序的通用实现代码. 所有真正的工作都是在这一类和超类中完成的.
 *
 * @param <T>   要处理的消息类型
 */
public abstract class PojoMessageHandlerWholeBase<T>
        extends PojoMessageHandlerBase<T> implements MessageHandler.Whole<T> {

    public PojoMessageHandlerWholeBase(Object pojo, Method method,
            Session session, Object[] params, int indexPayload,
            boolean convert, int indexSession, long maxMessageSize) {
        super(pojo, method, session, params, indexPayload, convert,
                indexSession, maxMessageSize);
    }


    @Override
    public final void onMessage(T message) {

        if (params.length == 1 && params[0] instanceof DecodeException) {
            ((WsSession) session).getLocal().onError(session,
                    (DecodeException) params[0]);
            return;
        }

        // 这个消息能被解码吗?
        Object payload;
        try {
            payload = decode(message);
        } catch (DecodeException de) {
            ((WsSession) session).getLocal().onError(session, de);
            return;
        }

        if (payload == null) {
            // 未解码. 转换.
            if (convert) {
                payload = convert(message);
            } else {
                payload = message;
            }
        }

        Object[] parameters = params.clone();
        if (indexSession != -1) {
            parameters[indexSession] = session;
        }
        parameters[indexPayload] = payload;

        Object result = null;
        try {
            result = method.invoke(pojo, parameters);
        } catch (IllegalAccessException | InvocationTargetException e) {
            handlePojoMethodException(e);
        }
        processResult(result);
    }

    protected Object convert(T message) {
        return message;
    }


    protected abstract Object decode(T message) throws DecodeException;
    protected abstract void onClose();
}
