package org.apache.tomcat.websocket.pojo;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * 将POJO实例转换为WebSocket端点实例的包装器的基本实现（客户机和服务器具有不同的具体实现）.
 */
public abstract class PojoEndpointBase extends Endpoint {

    private static final Log log = LogFactory.getLog(PojoEndpointBase.class);
    private static final StringManager sm = StringManager.getManager(PojoEndpointBase.class);

    private Object pojo;
    private Map<String,String> pathParameters;
    private PojoMethodMapping methodMapping;


    protected final void doOnOpen(Session session, EndpointConfig config) {
        PojoMethodMapping methodMapping = getMethodMapping();
        Object pojo = getPojo();
        Map<String,String> pathParameters = getPathParameters();

        // 在调用onOpen之前添加消息处理程序, 因为这可能触发一个消息, 该消息又可以触发响应或关闭会话
        for (MessageHandler mh : methodMapping.getMessageHandlers(pojo,
                pathParameters, session, config)) {
            session.addMessageHandler(mh);
        }

        if (methodMapping.getOnOpen() != null) {
            try {
                methodMapping.getOnOpen().invoke(pojo,
                        methodMapping.getOnOpenArgs(
                                pathParameters, session, config));

            } catch (IllegalAccessException e) {
                // 反射相关问题
                log.error(sm.getString(
                        "pojoEndpointBase.onOpenFail",
                        pojo.getClass().getName()), e);
                handleOnOpenOrCloseError(session, e);
                return;
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                handleOnOpenOrCloseError(session, cause);
                return;
            } catch (Throwable t) {
                handleOnOpenOrCloseError(session, t);
                return;
            }
        }
    }


    private void handleOnOpenOrCloseError(Session session, Throwable t) {
        // 如果真的致命 - re-throw
        ExceptionUtils.handleThrowable(t);

        // 触发错误处理程序, 并关闭会话
        onError(session, t);
        try {
            session.close();
        } catch (IOException ioe) {
            log.warn(sm.getString("pojoEndpointBase.closeSessionFail"), ioe);
        }
    }

    @Override
    public final void onClose(Session session, CloseReason closeReason) {

        if (methodMapping.getOnClose() != null) {
            try {
                methodMapping.getOnClose().invoke(pojo,
                        methodMapping.getOnCloseArgs(pathParameters, session, closeReason));
            } catch (Throwable t) {
                log.error(sm.getString("pojoEndpointBase.onCloseFail",
                        pojo.getClass().getName()), t);
                handleOnOpenOrCloseError(session, t);
            }
        }

        // 触发任何相关解码器的销毁方法
        Set<MessageHandler> messageHandlers = session.getMessageHandlers();
        for (MessageHandler messageHandler : messageHandlers) {
            if (messageHandler instanceof PojoMessageHandlerWholeBase<?>) {
                ((PojoMessageHandlerWholeBase<?>) messageHandler).onClose();
            }
        }
    }


    @Override
    public final void onError(Session session, Throwable throwable) {

        if (methodMapping.getOnError() == null) {
            log.error(sm.getString("pojoEndpointBase.onError",
                    pojo.getClass().getName()), throwable);
        } else {
            try {
                methodMapping.getOnError().invoke(
                        pojo,
                        methodMapping.getOnErrorArgs(pathParameters, session,
                                throwable));
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString("pojoEndpointBase.onErrorFail",
                        pojo.getClass().getName()), t);
            }
        }
    }

    protected Object getPojo() { return pojo; }
    protected void setPojo(Object pojo) { this.pojo = pojo; }


    protected Map<String,String> getPathParameters() { return pathParameters; }
    protected void setPathParameters(Map<String,String> pathParameters) {
        this.pathParameters = pathParameters;
    }


    protected PojoMethodMapping getMethodMapping() { return methodMapping; }
    protected void setMethodMapping(PojoMethodMapping methodMapping) {
        this.methodMapping = methodMapping;
    }
}
