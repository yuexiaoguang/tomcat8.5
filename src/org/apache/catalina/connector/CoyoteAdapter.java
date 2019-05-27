package org.apache.catalina.connector;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ReadListener;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.SessionTrackingMode;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Authenticator;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Wrapper;
import org.apache.catalina.authenticator.AuthenticatorBase;
import org.apache.catalina.core.AsyncContextImpl;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.SessionConfig;
import org.apache.catalina.util.URLEncoder;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.ServerCookie;
import org.apache.tomcat.util.http.ServerCookies;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.res.StringManager;


/**
 * 一个请求处理器的实现，它将处理过程委托给一个Coyote处理器.
 */
public class CoyoteAdapter implements Adapter {

    private static final Log log = LogFactory.getLog(CoyoteAdapter.class);

    // -------------------------------------------------------------- Constants

    private static final String POWERED_BY = "Servlet/4.0 JSP/2.3 " +
            "(" + ServerInfo.getServerInfo() + " Java/" +
            System.getProperty("java.vm.vendor") + "/" +
            System.getProperty("java.runtime.version") + ")";

    private static final EnumSet<SessionTrackingMode> SSL_ONLY =
        EnumSet.of(SessionTrackingMode.SSL);

    public static final int ADAPTER_NOTES = 1;


    protected static final boolean ALLOW_BACKSLASH =
        Boolean.parseBoolean(System.getProperty("org.apache.catalina.connector.CoyoteAdapter.ALLOW_BACKSLASH", "false"));


    private static final ThreadLocal<String> THREAD_NAME =
            new ThreadLocal<String>() {

                @Override
                protected String initialValue() {
                    return Thread.currentThread().getName();
                }

    };

    // ----------------------------------------------------------- Constructors


    /**
     * @param connector 拥有这个处理器的CoyoteConnector
     */
    public CoyoteAdapter(Connector connector) {
        super();
        this.connector = connector;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 这个处理器关联的CoyoteConnector..
     */
    private final Connector connector;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(CoyoteAdapter.class);


    // -------------------------------------------------------- Adapter Methods

    @Override
    public boolean asyncDispatch(org.apache.coyote.Request req, org.apache.coyote.Response res,
            SocketEvent status) throws Exception {

        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);

        if (request == null) {
            throw new IllegalStateException("Dispatch may only happen on an existing request.");
        }

        boolean success = true;
        AsyncContextImpl asyncConImpl = request.getAsyncContextInternal();

        req.getRequestProcessor().setWorkerThreadName(THREAD_NAME.get());

        try {
            if (!request.isAsync()) {
                // 错误或超时
                // 解除任何挂起以允许响应写入客户端(即如果一个异步请求使用sendError())
                response.setSuspended(false);
            }

            if (status==SocketEvent.TIMEOUT) {
                if (!asyncConImpl.timeout()) {
                    asyncConImpl.setErrorState(null, false);
                }
            } else if (status==SocketEvent.ERROR) {
                // 在非容器线程上发生I/O错误，这意味着需要关闭套接字，因此将成功设置为false以触发关闭
                success = false;
                Throwable t = (Throwable)req.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
                req.getAttributes().remove(RequestDispatcher.ERROR_EXCEPTION);
                ClassLoader oldCL = null;
                try {
                    oldCL = request.getContext().bind(false, null);
                    if (req.getReadListener() != null) {
                        req.getReadListener().onError(t);
                    }
                    if (res.getWriteListener() != null) {
                        res.getWriteListener().onError(t);
                    }
                } finally {
                    request.getContext().unbind(false, oldCL);
                }
                if (t != null) {
                    asyncConImpl.setErrorState(t, true);
                }
            }

            // 检查是否使用非阻塞写或读
            if (!request.isAsyncDispatching() && request.isAsync()) {
                WriteListener writeListener = res.getWriteListener();
                ReadListener readListener = req.getReadListener();
                if (writeListener != null && status == SocketEvent.OPEN_WRITE) {
                    ClassLoader oldCL = null;
                    try {
                        oldCL = request.getContext().bind(false, null);
                        res.onWritePossible();
                        if (request.isFinished() && req.sendAllDataReadEvent() &&
                                readListener != null) {
                            readListener.onAllDataRead();
                        }
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                        writeListener.onError(t);
                        success = false;
                    } finally {
                        request.getContext().unbind(false, oldCL);
                    }
                } else if (readListener != null && status == SocketEvent.OPEN_READ) {
                    ClassLoader oldCL = null;
                    try {
                        oldCL = request.getContext().bind(false, null);
                        // 如果正在非容器线程上读取数据，将使用 OPEN_READ在容器线程上返回执行onAllDataRead()事件.
                        // 因此, 确保onDataAvailable()在这种情况下不会调用.
                        if (!request.isFinished()) {
                            readListener.onDataAvailable();
                        }
                        if (request.isFinished() && req.sendAllDataReadEvent()) {
                            readListener.onAllDataRead();
                        }
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                        readListener.onError(t);
                        success = false;
                    } finally {
                        request.getContext().unbind(false, oldCL);
                    }
                }
            }

            // 在异步处理过程中发生错误，需要由应用程序的错误页面机制来处理(或Tomcat的)?
            if (!request.isAsyncDispatching() && request.isAsync() &&
                    response.isErrorReportRequired()) {
                connector.getService().getContainer().getPipeline().getFirst().invoke(
                        request, response);
            }

            if (request.isAsyncDispatching()) {
                connector.getService().getContainer().getPipeline().getFirst().invoke(
                        request, response);
                Throwable t = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
                if (t != null) {
                    asyncConImpl.setErrorState(t, true);
                }
            }

            if (!request.isAsync()) {
                request.finishRequest();
                response.finishResponse();
            }

            // 检查处理器是否处于错误状态. 如果是, 马上释放.
            AtomicBoolean error = new AtomicBoolean(false);
            res.action(ActionCode.IS_ERROR, error);
            if (error.get()) {
                if (request.isAsyncCompleting()) {
                    // 连接将被强制关闭. 需要触发调用onComplete().
                    res.action(ActionCode.ASYNC_POST_PROCESS,  null);
                }
                success = false;
            }
        } catch (IOException e) {
            success = false;
            // Ignore
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            success = false;
            log.error(sm.getString("coyoteAdapter.asyncDispatch"), t);
        } finally {
            if (!success) {
                res.setStatus(500);
            }

            // 访问日志
            if (!success || !request.isAsync()) {
                long time = 0;
                if (req.getStartTime() != -1) {
                    time = System.currentTimeMillis() - req.getStartTime();
                }
                Context context = request.getContext();
                if (context != null) {
                    context.logAccess(request, response, time, false);
                } else {
                    log(req, res, time);
                }
            }

            req.getRequestProcessor().setWorkerThreadName(null);
            // 回收包装请求和响应
            if (!success || !request.isAsync()) {
                request.recycle();
                response.recycle();
            }
        }
        return success;
    }


    @Override
    public void service(org.apache.coyote.Request req, org.apache.coyote.Response res)
            throws Exception {

        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);

        if (request == null) {
            // Create objects
            request = connector.createRequest();
            request.setCoyoteRequest(req);
            response = connector.createResponse();
            response.setCoyoteResponse(res);

            // Link objects
            request.setResponse(response);
            response.setRequest(request);

            // Set as notes
            req.setNote(ADAPTER_NOTES, request);
            res.setNote(ADAPTER_NOTES, response);

            // Set query string encoding
            req.getParameters().setQueryStringCharset(connector.getURICharset());
        }

        if (connector.getXpoweredBy()) {
            response.addHeader("X-Powered-By", POWERED_BY);
        }

        boolean async = false;
        boolean postParseSuccess = false;

        req.getRequestProcessor().setWorkerThreadName(THREAD_NAME.get());

        try {
            // 解析并设置Catalina 并配置指定的请求参数
            postParseSuccess = postParseRequest(req, request, res, response);
            if (postParseSuccess) {
                // 检查阀门，如果支持异步
                request.setAsyncSupported(
                        connector.getService().getContainer().getPipeline().isAsyncSupported());
                // 调用容器
                connector.getService().getContainer().getPipeline().getFirst().invoke(
                        request, response);
            }
            if (request.isAsync()) {
                async = true;
                ReadListener readListener = req.getReadListener();
                if (readListener != null && request.isFinished()) {
                    // 在service()方法中所有数据可能已被读取，因此需要在这里验证
                    ClassLoader oldCL = null;
                    try {
                        oldCL = request.getContext().bind(false, null);
                        if (req.sendAllDataReadEvent()) {
                            req.getReadListener().onAllDataRead();
                        }
                    } finally {
                        request.getContext().unbind(false, oldCL);
                    }
                }

                Throwable throwable =
                        (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

                // 如果启动异步请求, 一旦容器线程结束并且错误发生，就不会结束, 触发异步错误过程
                if (!request.isAsyncCompleting() && throwable != null) {
                    request.getAsyncContextInternal().setErrorState(throwable, true);
                }
            } else {
                request.finishRequest();
                response.finishResponse();
            }

        } catch (IOException e) {
            // Ignore
        } finally {
            AtomicBoolean error = new AtomicBoolean(false);
            res.action(ActionCode.IS_ERROR, error);

            if (request.isAsyncCompleting() && error.get()) {
                // 连接将被强制关闭. 需要在这里触发调用onComplete().
                res.action(ActionCode.ASYNC_POST_PROCESS,  null);
                async = false;
            }

            // Access log
            if (!async && postParseSuccess) {
                // 仅在调用处理时才记录.
                // 如果postParseRequest()失败, 已经记录了它.
                Context context = request.getContext();
                // 如果上下文是 null, 端点很可能是关闭的, 此连接关闭，请求在不同线程中循环使用.
                //该线程将更新访问日志，因此在此情况下不更新访问日志是可以的.
                if (context != null) {
                    context.logAccess(request, response,
                            System.currentTimeMillis() - req.getStartTime(), false);
                }
            }

            req.getRequestProcessor().setWorkerThreadName(null);

            // 回收包装请求和响应
            if (!async) {
                request.recycle();
                response.recycle();
            }
        }
    }


    @Override
    public boolean prepare(org.apache.coyote.Request req, org.apache.coyote.Response res)
            throws IOException, ServletException {
        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);

        return postParseRequest(req, request, res, response);
    }


    @Override
    public void log(org.apache.coyote.Request req,
            org.apache.coyote.Response res, long time) {

        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);

        if (request == null) {
            // Create objects
            request = connector.createRequest();
            request.setCoyoteRequest(req);
            response = connector.createResponse();
            response.setCoyoteResponse(res);

            // Link objects
            request.setResponse(response);
            response.setRequest(request);

            // Set as notes
            req.setNote(ADAPTER_NOTES, request);
            res.setNote(ADAPTER_NOTES, response);

            // Set query string encoding
            req.getParameters().setQueryStringCharset(connector.getURICharset());
        }

        try {
            // 最低等级可用日志. logAccess()将在父级容器中自动调用.
            boolean logged = false;
            Context context = request.mappingData.context;
            Host host = request.mappingData.host;
            if (context != null) {
                logged = true;
                context.logAccess(request, response, time, true);
            } else if (host != null) {
                logged = true;
                host.logAccess(request, response, time, true);
            }
            if (!logged) {
                connector.getService().getContainer().logAccess(request, response, time, true);
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString("coyoteAdapter.accesslogFail"), t);
        } finally {
            request.recycle();
            response.recycle();
        }
    }


    private static class RecycleRequiredException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    @Override
    public void checkRecycled(org.apache.coyote.Request req,
            org.apache.coyote.Response res) {
        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);
        String messageKey = null;
        if (request != null && request.getHost() != null) {
            messageKey = "coyoteAdapter.checkRecycled.request";
        } else if (response != null && response.getContentWritten() != 0) {
            messageKey = "coyoteAdapter.checkRecycled.response";
        }
        if (messageKey != null) {
            // 记录这个请求, 因为它可能跳过了访问日志.
            // log() 方法将关心回收.
            log(req, res, 0L);

            if (connector.getState().isAvailable()) {
                if (log.isInfoEnabled()) {
                    log.info(sm.getString(messageKey),
                            new RecycleRequiredException());
                }
            } else {
                // 可能有一些中止的请求.
                // 当连接器关闭时, 请求和响应将不被重用, 所以这里没有警告的问题.
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString(messageKey),
                            new RecycleRequiredException());
                }
            }
        }
    }


    @Override
    public String getDomain() {
        return connector.getDomain();
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * 在解析HTTP报头之后执行必要的处理，以便将请求/响应传递到容器管道的开始进行处理.
     *
     * @param req      The coyote request object
     * @param request  The catalina request object
     * @param res      The coyote response object
     * @param response The catalina response object
     *
     * @return <code>true</code>如果请求被传递到容器管道的开始, 否则<code>false</code>
     *
     * @throws IOException 如果在处理报头时缓冲区中的空间不足
     * @throws ServletException 如果不能确定目标servlet的支持方法
     */
    protected boolean postParseRequest(org.apache.coyote.Request req, Request request,
            org.apache.coyote.Response res, Response response) throws IOException, ServletException {

        // 如果处理器已经设置了该方案(AJP做这些, 如果启用SSL，HTTP做这些)使用此设置安全标志.
        // 如果处理器没有设置它, 使用连接器的设置
        if (req.scheme().isNull()) {
            // 使用连接器方案和安全配置, (默认为"http" 和 false)
            req.scheme().setString(connector.getScheme());
            request.setSecure(connector.getSecure());
        } else {
            // 使用处理器指定方案确定安全状态
            request.setSecure(req.scheme().equals("https"));
        }

        // 此时Host header已被处理.
        // Override if the proxyPort/proxyHost are set
        String proxyName = connector.getProxyName();
        int proxyPort = connector.getProxyPort();
        if (proxyPort != 0) {
            req.setServerPort(proxyPort);
        } else if (req.getServerPort() == -1) {
            // 未显式设置. 基于该方案使用默认端口
            if (req.scheme().equals("https")) {
                req.setServerPort(443);
            } else {
                req.setServerPort(80);
            }
        }
        if (proxyName != null) {
            req.serverName().setString(proxyName);
        }

        MessageBytes undecodedURI = req.requestURI();

        // Check for ping OPTIONS * request
        if (undecodedURI.equals("*")) {
            if (req.method().equalsIgnoreCase("OPTIONS")) {
                StringBuilder allow = new StringBuilder();
                allow.append("GET, HEAD, POST, PUT, DELETE");
                // Trace if allowed
                if (connector.getAllowTrace()) {
                    allow.append(", TRACE");
                }
                // Always allow options
                allow.append(", OPTIONS");
                res.setHeader("Allow", allow.toString());
            } else {
                res.setStatus(404);
                res.setMessage("Not found");
            }
            connector.getService().getContainer().logAccess(
                    request, response, 0, true);
            return false;
        }

        MessageBytes decodedURI = req.decodedURI();

        if (undecodedURI.getType() == MessageBytes.T_BYTES) {
            // 将原始URI复制到 decodedURI
            decodedURI.duplicate(undecodedURI);

            // 解析路径参数. This will:
            //   - 剔除路径参数
            //   - 覆盖decodedURI 到 bytes
            parsePathParameters(req, request);

            // URI 解码
            // URL的%xx 解码
            try {
                req.getURLDecoder().convert(decodedURI, false);
            } catch (IOException ioe) {
                res.setStatus(400);
                res.setMessage("Invalid URI: " + ioe.getMessage());
                connector.getService().getContainer().logAccess(
                        request, response, 0, true);
                return false;
            }
            // Normalization
            if (!normalize(req.decodedURI())) {
                res.setStatus(400);
                res.setMessage("Invalid URI");
                connector.getService().getContainer().logAccess(
                        request, response, 0, true);
                return false;
            }
            // 字符解码
            convertURI(decodedURI, request);
            // 检查URI是否仍然是规范化
            if (!checkNormalize(req.decodedURI())) {
                res.setStatus(400);
                res.setMessage("Invalid URI character encoding");
                connector.getService().getContainer().logAccess(
                        request, response, 0, true);
                return false;
            }
        } else {
            /* URI 是 char 或 String, 并已使用内存协议处理程序发送. 假设如下:
             * - req.requestURI()已经设置为'original' 未解码,未标准化的URI
             * - req.decodedURI()已经设置为已解码的, 标准化形式的req.requestURI()
             */
            decodedURI.toChars();
            // 删除所有路径参数; 任何需要的路径参数都应该使用请求对象来设置，而不是在URL中传递它
            CharChunk uriCC = decodedURI.getCharChunk();
            int semicolon = uriCC.indexOf(';');
            if (semicolon > 0) {
                decodedURI.setChars
                    (uriCC.getBuffer(), uriCC.getStart(), semicolon);
            }
        }

        // Request mapping.
        MessageBytes serverName;
        if (connector.getUseIPVHosts()) {
            serverName = req.localName();
            if (serverName.isNull()) {
                // well, they did ask for it
                res.action(ActionCode.REQ_LOCAL_NAME_ATTRIBUTE, null);
            }
        } else {
            serverName = req.serverName();
        }

        // 第二映射循环的版本和希望得到那个版本的Context
        String version = null;
        Context versionContext = null;
        boolean mapRequired = true;

        while (mapRequired) {
            // 这将默认映射最新版本
            connector.getService().getMapper().map(serverName, decodedURI,
                    version, request.getMappingData());

            // 如果在这一点上没有上下文, 很可能没有部署根上下文
            if (request.getContext() == null) {
                res.setStatus(404);
                res.setMessage("Not found");
                // No context, so use host
                Host host = request.getHost();
                // 确认是host (可能不是在关机期间)
                if (host != null) {
                    host.logAccess(request, response, 0, true);
                }
                return false;
            }

            // 现在有了上下文，可以从URL解析会话ID. 需要在重定向之前执行此操作，以防在重定向中包含会话ID
            String sessionID;
            if (request.getServletContext().getEffectiveSessionTrackingModes()
                    .contains(SessionTrackingMode.URL)) {

                // 如果有一个会话ID
                sessionID = request.getPathParameter(
                        SessionConfig.getSessionUriParamName(
                                request.getContext()));
                if (sessionID != null) {
                    request.setRequestedSessionId(sessionID);
                    request.setRequestedSessionURL(true);
                }
            }

            // 在Cookie和SSL会话中查找会话ID
            parseSessionCookiesId(request);
            parseSessionSslId(request);

            sessionID = request.getRequestedSessionId();

            mapRequired = false;
            if (version != null && request.getContext() == versionContext) {
                // 得到了要求的版本. That is it.
            } else {
                version = null;
                versionContext = null;

                Context[] contexts = request.getMappingData().contexts;
                // 单个contextVersion意味着不需要重新映射
                // 没有会话ID意味着没有重新映射的可能性
                if (contexts != null && sessionID != null) {
                    // 查找与会话关联的上下文
                    for (int i = (contexts.length); i > 0; i--) {
                        Context ctxt = contexts[i - 1];
                        if (ctxt.getManager().findSession(sessionID) != null) {
                            // 是不是已经映射过的那个?
                            if (!ctxt.equals(request.getMappingData().context)) {
                                // 通过映射正确的上下文来设置第二次版本
                                version = ctxt.getWebappVersion();
                                versionContext = ctxt;
                                // Reset mapping
                                request.getMappingData().recycle();
                                mapRequired = true;
                                // 在使用不同设置配置正确的上下文的情况下，回收cookie和会话信息
                                request.recycleSessionInfo();
                                request.recycleCookieInfo(true);
                            }
                            break;
                        }
                    }
                }
            }

            if (!mapRequired && request.getContext().getPaused()) {
                // 找到匹配的上下文，但它被暂停了. 映射数据将是错误的，因为此时可能无法注册一些Wrapper.
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Should never happen
                }
                // Reset mapping
                request.getMappingData().recycle();
                mapRequired = true;
            }
        }

        // 可能重定向
        MessageBytes redirectPathMB = request.getMappingData().redirectPath;
        if (!redirectPathMB.isNull()) {
            String redirectPath = URLEncoder.DEFAULT.encode(
                    redirectPathMB.toString(), StandardCharsets.UTF_8);
            String query = request.getQueryString();
            if (request.isRequestedSessionIdFromURL()) {
                // 这不是最好的，但这不是很常见，它不重要
                redirectPath = redirectPath + ";" +
                        SessionConfig.getSessionUriParamName(
                            request.getContext()) +
                    "=" + request.getRequestedSessionId();
            }
            if (query != null) {
                // 这不是最好的，但这不是很常见，它不重要
                redirectPath = redirectPath + "?" + query;
            }
            response.sendRedirect(redirectPath);
            request.getContext().logAccess(request, response, 0, true);
            return false;
        }

        // 过滤器跟踪方法
        if (!connector.getAllowTrace()
                && req.method().equalsIgnoreCase("TRACE")) {
            Wrapper wrapper = request.getWrapper();
            String header = null;
            if (wrapper != null) {
                String[] methods = wrapper.getServletMethods();
                if (methods != null) {
                    for (int i=0; i<methods.length; i++) {
                        if ("TRACE".equals(methods[i])) {
                            continue;
                        }
                        if (header == null) {
                            header = methods[i];
                        } else {
                            header += ", " + methods[i];
                        }
                    }
                }
            }
            res.setStatus(405);
            res.addHeader("Allow", header);
            res.setMessage("TRACE method is not allowed");
            request.getContext().logAccess(request, response, 0, true);
            return false;
        }

        doConnectorAuthenticationAuthorization(req, request);

        return true;
    }


    private void doConnectorAuthenticationAuthorization(org.apache.coyote.Request req, Request request) {
        // 设置远程主体
        String username = req.getRemoteUser().toString();
        if (username != null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("coyoteAdapter.authenticate", username));
            }
            if (req.getRemoteUserNeedsAuthorization()) {
                Authenticator authenticator = request.getContext().getAuthenticator();
                if (authenticator == null) {
                    // 没有为应用程序配置安全约束，因此不需要授权用户. 使用CoyotePrincipal 提供经认证的用户.
                    request.setUserPrincipal(new CoyotePrincipal(username));
                } else if (!(authenticator instanceof AuthenticatorBase)) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("coyoteAdapter.authorize", username));
                    }
                    // 可能不触发自定义身份验证程序.
                    // 在这里做认证以确保它完成.
                    request.setUserPrincipal(
                            request.getContext().getRealm().authenticate(username));
                }
                // 如果Authenticator是一个AuthenticatorBase实例，那么它将检查 req.getRemoteUserNeedsAuthorization(),
                // 并触发验证. 它还将缓存结果，防止对该Realm的过度调用.
            } else {
                // 连接器未配置为授权. 使用提供的用户名创建没有任何角色的用户.
                request.setUserPrincipal(new CoyotePrincipal(username));
            }
        }

        // 设置认证类型
        String authtype = req.getAuthType().toString();
        if (authtype != null) {
            request.setAuthType(authtype);
        }
    }


    /**
     * 从请求中提取路径参数.
     * 假设参数的格式为 /path;name=value;name2=value2/ 等. 目前只对会话ID感兴趣，将以这种形式出现. 其他参数可以安全地忽略.
     *
     * @param req Coyote请求对象
     * @param request Servlet请求对象
     */
    protected void parsePathParameters(org.apache.coyote.Request req,
            Request request) {

        // 按字节处理(这是默认格式，所以这通常是NOP
        req.decodedURI().toBytes();

        ByteChunk uriBC = req.decodedURI().getByteChunk();
        int semicolon = uriBC.indexOf(';', 0);
        // 性能优化. 返回时，它是已知的，没有路径参数;
        if (semicolon == -1) {
            return;
        }

        // 使用什么编码? 一些平台，如Z/OS，使用默认的编码，不给出预期的结果，因此要明确
        Charset charset = connector.getURICharset();

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("coyoteAdapter.debug", "uriBC",
                    uriBC.toString()));
            log.debug(sm.getString("coyoteAdapter.debug", "semicolon",
                    String.valueOf(semicolon)));
            log.debug(sm.getString("coyoteAdapter.debug", "enc", charset.name()));
        }

        while (semicolon > -1) {
            // 解析路径参数，并从解码的请求URI中提取它
            int start = uriBC.getStart();
            int end = uriBC.getEnd();

            int pathParamStart = semicolon + 1;
            int pathParamEnd = ByteChunk.findBytes(uriBC.getBuffer(),
                    start + pathParamStart, end,
                    new byte[] {';', '/'});

            String pv = null;

            if (pathParamEnd >= 0) {
                if (charset != null) {
                    pv = new String(uriBC.getBuffer(), start + pathParamStart,
                                pathParamEnd - pathParamStart, charset);
                }
                // 从解码请求URI中提取路径参数
                byte[] buf = uriBC.getBuffer();
                for (int i = 0; i < end - start - pathParamEnd; i++) {
                    buf[start + semicolon + i]
                        = buf[start + i + pathParamEnd];
                }
                uriBC.setBytes(buf, start,
                        end - start - pathParamEnd + semicolon);
            } else {
                if (charset != null) {
                    pv = new String(uriBC.getBuffer(), start + pathParamStart,
                                (end - start) - pathParamStart, charset);
                }
                uriBC.setEnd(start + semicolon);
            }

            if (log.isDebugEnabled()) {
                log.debug(sm.getString("coyoteAdapter.debug", "pathParamStart",
                        String.valueOf(pathParamStart)));
                log.debug(sm.getString("coyoteAdapter.debug", "pathParamEnd",
                        String.valueOf(pathParamEnd)));
                log.debug(sm.getString("coyoteAdapter.debug", "pv", pv));
            }

            if (pv != null) {
                int equals = pv.indexOf('=');
                if (equals > -1) {
                    String name = pv.substring(0, equals);
                    String value = pv.substring(equals + 1);
                    request.addPathParameter(name, value);
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("coyoteAdapter.debug", "equals",
                                String.valueOf(equals)));
                        log.debug(sm.getString("coyoteAdapter.debug", "name",
                                name));
                        log.debug(sm.getString("coyoteAdapter.debug", "value",
                                value));
                    }
                }
            }

            semicolon = uriBC.indexOf(';', semicolon);
        }
    }


    /**
     * 查找SSL会话ID. 仅启用SSL会话ID，如果它是唯一启用的跟踪方法.
     *
     * @param request Servlet请求对象
     */
    protected void parseSessionSslId(Request request) {
        if (request.getRequestedSessionId() == null &&
                SSL_ONLY.equals(request.getServletContext()
                        .getEffectiveSessionTrackingModes()) &&
                        request.connector.secure) {
            String sessionId = (String) request.getAttribute(SSLSupport.SESSION_ID_KEY);
            if (sessionId != null) {
                request.setRequestedSessionId(sessionId);
                request.setRequestedSessionSSL(true);
            }
        }
    }


    /**
     * 解析Cookie中的会话ID.
     *
     * @param request Servlet请求对象
     */
    protected void parseSessionCookiesId(Request request) {

        // 如果在当前上下文中禁用了通过cookie进行会话跟踪, 不要在cookie中寻找会话ID, 因为存在会话ID的父上下文中的cookie可能会覆盖在URL中编码的有效会话ID
        Context context = request.getMappingData().context;
        if (context != null && !context.getServletContext()
                .getEffectiveSessionTrackingModes().contains(
                        SessionTrackingMode.COOKIE)) {
            return;
        }

        // Parse session id from cookies
        ServerCookies serverCookies = request.getServerCookies();
        int count = serverCookies.getCookieCount();
        if (count <= 0) {
            return;
        }

        String sessionCookieName = SessionConfig.getSessionCookieName(context);

        for (int i = 0; i < count; i++) {
            ServerCookie scookie = serverCookies.getCookie(i);
            if (scookie.getName().equals(sessionCookieName)) {
                // 覆盖URL中请求的任何内容
                if (!request.isRequestedSessionIdFromCookie()) {
                    // 只接受第一个会话ID cookie
                    convertMB(scookie.getValue());
                    request.setRequestedSessionId
                        (scookie.getValue().toString());
                    request.setRequestedSessionCookie(true);
                    request.setRequestedSessionURL(false);
                    if (log.isDebugEnabled()) {
                        log.debug(" Requested cookie session id is " +
                            request.getRequestedSessionId());
                    }
                } else {
                    if (!request.isRequestedSessionIdValid()) {
                        // 替换会话ID直到一个有效
                        convertMB(scookie.getValue());
                        request.setRequestedSessionId
                            (scookie.getValue().toString());
                    }
                }
            }
        }

    }


    /**
     * URI的字符转换.
     *
     * @param uri 包含URI的MessageBytes对象
     * @param request Servlet请求对象
     * @throws IOException 如果IO异常发生，则向客户端发送错误
     */
    protected void convertURI(MessageBytes uri, Request request) throws IOException {

        ByteChunk bc = uri.getByteChunk();
        int length = bc.getLength();
        CharChunk cc = uri.getCharChunk();
        cc.allocate(length, -1);

        Charset charset = connector.getURICharset();

        B2CConverter conv = request.getURIConverter();
        if (conv == null) {
            conv = new B2CConverter(charset, true);
            request.setURIConverter(conv);
        } else {
            conv.recycle();
        }

        try {
            conv.convert(bc, cc, true);
            uri.setChars(cc.getBuffer(), cc.getStart(), cc.getLength());
        } catch (IOException ioe) {
            // 永远不会发生，因为 B2CConverter 应该替换问题字符
            request.getResponse().sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }


    /**
     * US-ASCII MessageBytes的字符转换.
     *
     * @param mb 包含应该转换为字符的字节的MessageBytes实例
     */
    protected void convertMB(MessageBytes mb) {

        // 这当然是有意义的字节
        if (mb.getType() != MessageBytes.T_BYTES) {
            return;
        }

        ByteChunk bc = mb.getByteChunk();
        CharChunk cc = mb.getCharChunk();
        int length = bc.getLength();
        cc.allocate(length, -1);

        // 默认编码: 快速转换
        byte[] bbuf = bc.getBuffer();
        char[] cbuf = cc.getBuffer();
        int start = bc.getStart();
        for (int i = 0; i < length; i++) {
            cbuf[i] = (char) (bbuf[i + start] & 0xff);
        }
        mb.setChars(cbuf, 0, length);

    }


    /**
     * 该方法规范化 "\", "//", "/./" 和 "/../".
     *
     * @param uriMB 要规范化的URI
     *
     * @return <code>false</code>如果URI规范化之后超过根路径, 或者URI 包含一个 null 字节,
     *         否则<code>true</code>
     */
    public static boolean normalize(MessageBytes uriMB) {

        ByteChunk uriBC = uriMB.getByteChunk();
        final byte[] b = uriBC.getBytes();
        final int start = uriBC.getStart();
        int end = uriBC.getEnd();

        // 空URL是不可接受的
        if (start == end) {
            return false;
        }

        // URL * 是可以接受的
        if ((end - start == 1) && b[start] == (byte) '*') {
            return true;
        }

        int pos = 0;
        int index = 0;

        // Replace '\' with '/'
        // Check for null byte
        for (pos = start; pos < end; pos++) {
            if (b[pos] == (byte) '\\') {
                if (ALLOW_BACKSLASH) {
                    b[pos] = (byte) '/';
                } else {
                    return false;
                }
            }
            if (b[pos] == (byte) 0) {
                return false;
            }
        }

        // The URL must start with '/'
        if (b[start] != (byte) '/') {
            return false;
        }

        // Replace "//" with "/"
        for (pos = start; pos < (end - 1); pos++) {
            if (b[pos] == (byte) '/') {
                while ((pos + 1 < end) && (b[pos + 1] == (byte) '/')) {
                    copyBytes(b, pos, pos + 1, end - pos - 1);
                    end--;
                }
            }
        }

        // 如果URI 以"/." 或 "/.."结尾, 那么追加一个额外的 "/"
        // Note: 可以将URI扩展1而没有任何副作用，因为下一个字符是不重要的空格.
        if (((end - start) >= 2) && (b[end - 1] == (byte) '.')) {
            if ((b[end - 2] == (byte) '/')
                || ((b[end - 2] == (byte) '.')
                    && (b[end - 3] == (byte) '/'))) {
                b[end] = (byte) '/';
                end++;
            }
        }

        uriBC.setEnd(end);

        index = 0;

        // 解析路径中的"/./"
        while (true) {
            index = uriBC.indexOf("/./", 0, 3, index);
            if (index < 0) {
                break;
            }
            copyBytes(b, start + index, start + index + 2,
                      end - start - index - 2);
            end = end - 2;
            uriBC.setEnd(end);
        }

        index = 0;

        // 解析路径中的"/../"
        while (true) {
            index = uriBC.indexOf("/../", 0, 4, index);
            if (index < 0) {
                break;
            }
            // 防止超出上下文
            if (index == 0) {
                return false;
            }
            int index2 = -1;
            for (pos = start + index - 1; (pos >= 0) && (index2 < 0); pos --) {
                if (b[pos] == (byte) '/') {
                    index2 = pos;
                }
            }
            copyBytes(b, start + index2, start + index + 3,
                      end - start - index - 3);
            end = end + index2 - index - 3;
            uriBC.setEnd(end);
            index = index2;
        }

        return true;

    }


    /**
     * 检查字符解码后的URI是否规范化. 这个方法检查 "\", 0, "//", "/./" 和 "/../".
     *
     * @param uriMB 要检查的URI(should be chars)
     *
     * @return <code>false</code> 如果假定要规范化的序列仍然存在于URI中, 否则<code>true</code>
     */
    public static boolean checkNormalize(MessageBytes uriMB) {

        CharChunk uriCC = uriMB.getCharChunk();
        char[] c = uriCC.getChars();
        int start = uriCC.getStart();
        int end = uriCC.getEnd();

        int pos = 0;

        // 检查 '\' 和 0
        for (pos = start; pos < end; pos++) {
            if (c[pos] == '\\') {
                return false;
            }
            if (c[pos] == 0) {
                return false;
            }
        }

        // 检查 "//"
        for (pos = start; pos < (end - 1); pos++) {
            if (c[pos] == '/') {
                if (c[pos + 1] == '/') {
                    return false;
                }
            }
        }

        // 检查是否以 "/." 或 "/.."结尾
        if (((end - start) >= 2) && (c[end - 1] == '.')) {
            if ((c[end - 2] == '/')
                    || ((c[end - 2] == '.')
                    && (c[end - 3] == '/'))) {
                return false;
            }
        }

        // 检查 "/./"
        if (uriCC.indexOf("/./", 0, 3, 0) >= 0) {
            return false;
        }

        // 检查 "/../"
        if (uriCC.indexOf("/../", 0, 4, 0) >= 0) {
            return false;
        }

        return true;

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 将字节数组复制到不同的位置. 规范化期间使用.
     *
     * @param b 应该复制的字节
     * @param dest 目标偏移量
     * @param src 源偏移量
     * @param len 长度
     */
    protected static void copyBytes(byte[] b, int dest, int src, int len) {
        for (int pos = 0; pos < len; pos++) {
            b[pos + dest] = b[pos + src];
        }
    }
}
