package org.apache.tomcat.websocket.server;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;
import javax.servlet.http.WebConnection;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;

import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.Transformation;
import org.apache.tomcat.websocket.WsIOException;
import org.apache.tomcat.websocket.WsSession;

/**
 * Servlet 3.1用于WebSocket连接的HTTP升级处理程序.
 */
public class WsHttpUpgradeHandler implements InternalHttpUpgradeHandler {

    private static final Log log = LogFactory.getLog(WsHttpUpgradeHandler.class);
    private static final StringManager sm = StringManager.getManager(WsHttpUpgradeHandler.class);

    private final ClassLoader applicationClassLoader;

    private SocketWrapperBase<?> socketWrapper;

    private Endpoint ep;
    private EndpointConfig endpointConfig;
    private WsServerContainer webSocketContainer;
    private WsHandshakeRequest handshakeRequest;
    private List<Extension> negotiatedExtensions;
    private String subProtocol;
    private Transformation transformation;
    private Map<String,String> pathParameters;
    private boolean secure;
    private WebConnection connection;

    private WsRemoteEndpointImplServer wsRemoteEndpointServer;
    private WsFrameServer wsFrame;
    private WsSession wsSession;


    public WsHttpUpgradeHandler() {
        applicationClassLoader = Thread.currentThread().getContextClassLoader();
    }


    @Override
    public void setSocketWrapper(SocketWrapperBase<?> socketWrapper) {
        this.socketWrapper = socketWrapper;
    }


    public void preInit(Endpoint ep, EndpointConfig endpointConfig,
            WsServerContainer wsc, WsHandshakeRequest handshakeRequest,
            List<Extension> negotiatedExtensionsPhase2, String subProtocol,
            Transformation transformation, Map<String,String> pathParameters,
            boolean secure) {
        this.ep = ep;
        this.endpointConfig = endpointConfig;
        this.webSocketContainer = wsc;
        this.handshakeRequest = handshakeRequest;
        this.negotiatedExtensions = negotiatedExtensionsPhase2;
        this.subProtocol = subProtocol;
        this.transformation = transformation;
        this.pathParameters = pathParameters;
        this.secure = secure;
    }


    @Override
    public void init(WebConnection connection) {
        if (ep == null) {
            throw new IllegalStateException(
                    sm.getString("wsHttpUpgradeHandler.noPreInit"));
        }

        String httpSessionId = null;
        Object session = handshakeRequest.getHttpSession();
        if (session != null ) {
            httpSessionId = ((HttpSession) session).getId();
        }

        // 需要使用Web应用程序的类加载器调用onOpen
        // 使用应用程序的类加载器创建框架，以便它可以从ServerContainerImpl获取特定于应用程序的配置文件
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(applicationClassLoader);
        try {
            wsRemoteEndpointServer = new WsRemoteEndpointImplServer(socketWrapper, webSocketContainer);
            wsSession = new WsSession(ep, wsRemoteEndpointServer,
                    webSocketContainer, handshakeRequest.getRequestURI(),
                    handshakeRequest.getParameterMap(),
                    handshakeRequest.getQueryString(),
                    handshakeRequest.getUserPrincipal(), httpSessionId,
                    negotiatedExtensions, subProtocol, pathParameters, secure,
                    endpointConfig);
            wsFrame = new WsFrameServer(socketWrapper, wsSession, transformation,
                    applicationClassLoader);
            // WsFrame添加必要的最终转换. 将完成的转换链复制到远程端点.
            wsRemoteEndpointServer.setTransformation(wsFrame.getTransformation());
            ep.onOpen(wsSession, endpointConfig);
            webSocketContainer.registerSession(ep, wsSession);
        } catch (DeploymentException e) {
            throw new IllegalArgumentException(e);
        } finally {
            t.setContextClassLoader(cl);
        }
    }


    @Override
    public SocketState upgradeDispatch(SocketEvent status) {
        switch (status) {
            case OPEN_READ:
                try {
                    return wsFrame.notifyDataAvailable();
                } catch (WsIOException ws) {
                    close(ws.getCloseReason());
                } catch (IOException ioe) {
                    onError(ioe);
                    CloseReason cr = new CloseReason(
                            CloseCodes.CLOSED_ABNORMALLY, ioe.getMessage());
                    close(cr);
                    return SocketState.CLOSED;
                }
                break;
            case OPEN_WRITE:
                wsRemoteEndpointServer.onWritePossible(false);
                break;
            case STOP:
                CloseReason cr = new CloseReason(CloseCodes.GOING_AWAY,
                        sm.getString("wsHttpUpgradeHandler.serverStop"));
                try {
                    wsSession.close(cr);
                } catch (IOException ioe) {
                    onError(ioe);
                    cr = new CloseReason(
                            CloseCodes.CLOSED_ABNORMALLY, ioe.getMessage());
                    close(cr);
                    return SocketState.CLOSED;
                }
                break;
            case ERROR:
                String msg = sm.getString("wsHttpUpgradeHandler.closeOnError");
                wsSession.doClose(new CloseReason(CloseCodes.GOING_AWAY, msg),
                        new CloseReason(CloseCodes.CLOSED_ABNORMALLY, msg));
                //$FALL-THROUGH$
            case DISCONNECT:
            case TIMEOUT:
                return SocketState.CLOSED;

        }
        if (wsFrame.isOpen()) {
            return SocketState.UPGRADED;
        } else {
            return SocketState.CLOSED;
        }
    }


    @Override
    public void pause() {
        // NO-OP
    }


    @Override
    public void destroy() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                log.error(sm.getString("wsHttpUpgradeHandler.destroyFailed"), e);
            }
        }
    }


    private void onError(Throwable throwable) {
        // 需要使用Web应用程序的类加载器时调用onError
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(applicationClassLoader);
        try {
            ep.onError(wsSession, throwable);
        } finally {
            t.setContextClassLoader(cl);
        }
    }


    private void close(CloseReason cr) {
        /*
         * 对该方法的任何调用都是客户端读取有问题的结果. 此时，连接的状态未知.
         * 尝试向客户端发送一个关闭的帧，然后立即关闭套接字. 没有必要等待来自客户端的关闭帧，因为无法保证能够从客户端连接的混乱状态中恢复.
         */
        wsSession.onClose(cr);
    }


    @Override
    public void setSslSupport(SSLSupport sslSupport) {
        // NO-OP. WebSocket不需要访问与底层连接相关联的TLS信息.
    }
}
