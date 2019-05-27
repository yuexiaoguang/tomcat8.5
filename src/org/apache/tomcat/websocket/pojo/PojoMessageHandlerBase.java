package org.apache.tomcat.websocket.pojo;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import javax.websocket.EncodeException;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.websocket.WrappedMessageHandler;

/**
 * POJO消息处理程序的通用实现代码.
 *
 * @param <T>   要处理的消息类型
 */
public abstract class PojoMessageHandlerBase<T>
        implements WrappedMessageHandler {

    protected final Object pojo;
    protected final Method method;
    protected final Session session;
    protected final Object[] params;
    protected final int indexPayload;
    protected final boolean convert;
    protected final int indexSession;
    protected final long maxMessageSize;

    public PojoMessageHandlerBase(Object pojo, Method method,
            Session session, Object[] params, int indexPayload, boolean convert,
            int indexSession, long maxMessageSize) {
        this.pojo = pojo;
        this.method = method;
        // TODO: 这里应该已经可以访问该方法，但是在一些尚未完全理解的情况下，以下代码似乎是必要的.
        try {
            this.method.setAccessible(true);
        } catch (Exception e) {
            // 最好确保方法是可访问的, 但是忽略异常并希望最好
        }
        this.session = session;
        this.params = params;
        this.indexPayload = indexPayload;
        this.convert = convert;
        this.indexSession = indexSession;
        this.maxMessageSize = maxMessageSize;
    }


    protected final void processResult(Object result) {
        if (result == null) {
            return;
        }

        RemoteEndpoint.Basic remoteEndpoint = session.getBasicRemote();
        try {
            if (result instanceof String) {
                remoteEndpoint.sendText((String) result);
            } else if (result instanceof ByteBuffer) {
                remoteEndpoint.sendBinary((ByteBuffer) result);
            } else if (result instanceof byte[]) {
                remoteEndpoint.sendBinary(ByteBuffer.wrap((byte[]) result));
            } else {
                remoteEndpoint.sendObject(result);
            }
        } catch (IOException | EncodeException ioe) {
            throw new IllegalStateException(ioe);
        }
    }


    /**
     * 如果POJO是消息处理程序，则公开该POJO，这样，如果原始处理程序已被包装，则会话能够匹配删除处理程序的请求.
     */
    @Override
    public final MessageHandler getWrappedHandler() {
        if (pojo instanceof MessageHandler) {
            return (MessageHandler) pojo;
        } else {
            return null;
        }
    }


    @Override
    public final long getMaxMessageSize() {
        return maxMessageSize;
    }


    protected final void handlePojoMethodException(Throwable t) {
        t = ExceptionUtils.unwrapInvocationTargetException(t);
        ExceptionUtils.handleThrowable(t);
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        } else {
            throw new RuntimeException(t.getMessage(), t);
        }
    }
}
