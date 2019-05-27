package org.apache.catalina.authenticator;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Session;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.coyote.ActionCode;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.http.MimeHeaders;

/**
 * <b>Authenticator</b>和<b>Valve</b>的FORM BASED验证实现类, 正如servlet API规范中描述的.
 */
public class FormAuthenticator extends AuthenticatorBase {

    private static final Log log = LogFactory.getLog(FormAuthenticator.class);


    // ----------------------------------------------------- Instance Variables

    /**
     * 用于从请求读取用户名和密码参数的字符编码.
     * 如果未设置, 将使用请求正文的编码.
     */
    protected String characterEncoding = null;

    /**
     * 如果用户试图直接访问登录页面，或者登录时会话超时，则使用登陆页. 如果未设置，则将发送错误响应.
     */
    protected String landingPage = null;


    // ------------------------------------------------------------- Properties

    /**
     * 返回用于读取用户名和密码的字符编码.
     */
    public String getCharacterEncoding() {
        return characterEncoding;
    }


    /**
     * 设置用于读取用户名和密码的字符编码. .
     *
     * @param encoding 要使用的编码的名称
     */
    public void setCharacterEncoding(String encoding) {
        characterEncoding = encoding;
    }


    /**
     * 当FORM验证被误用，返回登录页.
     */
    public String getLandingPage() {
        return landingPage;
    }


    /**
     * 当FORM验证被误用，设置登录页
     *
     * @param landingPage 登陆页面相对于Web应用程序根的路径
     */
    public void setLandingPage(String landingPage) {
        this.landingPage = landingPage;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 根据指定的登录配置，对作出此请求的用户进行身份验证. 
     * 如果满足指定的约束，返回<code>true</code>; 如果已经创建了一个响应，返回<code>false</code>.
     *
     * @param request Request we are processing
     * @param response Response we are creating
     *
     * @exception IOException 如果发生输入/输出错误
     */
    @Override
    protected boolean doAuthenticate(Request request, HttpServletResponse response)
            throws IOException {

        if (checkForCachedAuthentication(request, response, true)) {
            return true;
        }

        // 稍后需要的对象的引用
        Session session = null;
        Principal principal = null;

        // Have we authenticated this user before but have caching disabled?
        if (!cache) {
            session = request.getSessionInternal(true);
            if (log.isDebugEnabled()) {
                log.debug("Checking for reauthenticate in session " + session);
            }
            String username =
                (String) session.getNote(Constants.SESS_USERNAME_NOTE);
            String password =
                (String) session.getNote(Constants.SESS_PASSWORD_NOTE);
            if ((username != null) && (password != null)) {
                if (log.isDebugEnabled()) {
                    log.debug("Reauthenticating username '" + username + "'");
                }
                principal =
                    context.getRealm().authenticate(username, password);
                if (principal != null) {
                    session.setNote(Constants.FORM_PRINCIPAL_NOTE, principal);
                    if (!matchRequest(request)) {
                        register(request, response, principal,
                                HttpServletRequest.FORM_AUTH,
                                username, password);
                        return true;
                    }
                }
                if (log.isDebugEnabled()) {
                    log.debug("Reauthentication failed, proceed normally");
                }
            }
        }

        // 这是认证成功后原始请求URI的重新提交吗?  如果是这样, 转发 *original*请求.
        if (matchRequest(request)) {
            session = request.getSessionInternal(true);
            if (log.isDebugEnabled()) {
                log.debug("Restore request from session '"
                          + session.getIdInternal()
                          + "'");
            }
            principal = (Principal)
                session.getNote(Constants.FORM_PRINCIPAL_NOTE);
            register(request, response, principal, HttpServletRequest.FORM_AUTH,
                     (String) session.getNote(Constants.SESS_USERNAME_NOTE),
                     (String) session.getNote(Constants.SESS_PASSWORD_NOTE));
            // 如果我们正在缓存主体，则会话中不再需要用户名和密码, 所以移除它们
            if (cache) {
                session.removeNote(Constants.SESS_USERNAME_NOTE);
                session.removeNote(Constants.SESS_PASSWORD_NOTE);
            }
            if (restoreRequest(request, session)) {
                if (log.isDebugEnabled()) {
                    log.debug("Proceed to restored request");
                }
                return true;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Restore of original request failed");
                }
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            }
        }

        // 获取对需要评估的对象的引用
        String contextPath = request.getContextPath();
        String requestURI = request.getDecodedRequestURI();

        // 这是登录页面的动作请求吗?
        boolean loginAction =
            requestURI.startsWith(contextPath) &&
            requestURI.endsWith(Constants.FORM_ACTION);

        LoginConfig config = context.getLoginConfig();

        // No -- 保存此请求并重定向到表单登录页
        if (!loginAction) {
            // 如果此请求是没有'/'的上下文的根, 需要重定向以添加它，登录表单的提交可能无法转到正确的Web应用程序
            if (request.getServletPath().length() == 0 && request.getPathInfo() == null) {
                StringBuilder location = new StringBuilder(requestURI);
                location.append('/');
                if (request.getQueryString() != null) {
                    location.append('?');
                    location.append(request.getQueryString());
                }
                response.sendRedirect(response.encodeRedirectURL(location.toString()));
                return false;
            }

            session = request.getSessionInternal(true);
            if (log.isDebugEnabled()) {
                log.debug("Save request in session '" + session.getIdInternal() + "'");
            }
            try {
                saveRequest(request, session);
            } catch (IOException ioe) {
                log.debug("Request body too big to save during authentication");
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        sm.getString("authenticator.requestBodyTooBig"));
                return false;
            }
            forwardToLoginPage(request, response, config);
            return false;
        }

        // Yes -- 确认请求, 如果指定的凭据不正确，则重定向到错误页
        request.getResponse().sendAcknowledgement();
        Realm realm = context.getRealm();
        if (characterEncoding != null) {
            request.setCharacterEncoding(characterEncoding);
        }
        String username = request.getParameter(Constants.FORM_USERNAME);
        String password = request.getParameter(Constants.FORM_PASSWORD);
        if (log.isDebugEnabled()) {
            log.debug("Authenticating username '" + username + "'");
        }
        principal = realm.authenticate(username, password);
        if (principal == null) {
            forwardToErrorPage(request, response, config);
            return false;
        }

        if (log.isDebugEnabled()) {
            log.debug("Authentication of '" + username + "' was successful");
        }

        if (session == null) {
            session = request.getSessionInternal(false);
        }
        if (session == null) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug
                    ("User took so long to log on the session expired");
            }
            if (landingPage == null) {
                response.sendError(HttpServletResponse.SC_REQUEST_TIMEOUT,
                        sm.getString("authenticator.sessionExpired"));
            } else {
                // Make the authenticator think the user originally requested
                // the landing page
                String uri = request.getContextPath() + landingPage;
                SavedRequest saved = new SavedRequest();
                saved.setMethod("GET");
                saved.setRequestURI(uri);
                saved.setDecodedRequestURI(uri);
                request.getSessionInternal(true).setNote(
                        Constants.FORM_REQUEST_NOTE, saved);
                response.sendRedirect(response.encodeRedirectURL(uri));
            }
            return false;
        }

        // Save the authenticated Principal in our session
        session.setNote(Constants.FORM_PRINCIPAL_NOTE, principal);

        // Save the username and password as well
        session.setNote(Constants.SESS_USERNAME_NOTE, username);
        session.setNote(Constants.SESS_PASSWORD_NOTE, password);

        // 将用户重定向到原始请求URI(这将导致原始请求恢复)
        requestURI = savedRequestURL(session);
        if (log.isDebugEnabled()) {
            log.debug("Redirecting to original '" + requestURI + "'");
        }
        if (requestURI == null) {
            if (landingPage == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        sm.getString("authenticator.formlogin"));
            } else {
                // Make the authenticator think the user originally requested
                // the landing page
                String uri = request.getContextPath() + landingPage;
                SavedRequest saved = new SavedRequest();
                saved.setMethod("GET");
                saved.setRequestURI(uri);
                saved.setDecodedRequestURI(uri);
                session.setNote(Constants.FORM_REQUEST_NOTE, saved);
                response.sendRedirect(response.encodeRedirectURL(uri));
            }
        } else {
            // 直到servlet API允许指定重定向的类型才能使用.
            Response internalResponse = request.getResponse();
            String location = response.encodeRedirectURL(requestURI);
            if ("HTTP/1.1".equals(request.getProtocol())) {
                internalResponse.sendRedirect(location,
                        HttpServletResponse.SC_SEE_OTHER);
            } else {
                internalResponse.sendRedirect(location,
                        HttpServletResponse.SC_FOUND);
            }
        }
        return false;

    }


    @Override
    protected boolean isContinuationRequired(Request request) {
        // 基于表单登录的特殊处理，登录表单可能位于安全区域之外
        String contextPath = this.context.getPath();
        String decodedRequestURI = request.getDecodedRequestURI();
        if (decodedRequestURI.startsWith(contextPath) &&
                decodedRequestURI.endsWith(Constants.FORM_ACTION)) {
            return true;
        }

        // 基于表单登录的特殊处理, 对于某些HTTP方法保护资源的情况, 但在向受保护资源重定向时验证后使用的GET不受保护.
        // TODO: 类似于FormAuthenticator.matchRequest() 逻辑
        // 有没有办法消除重复?
        Session session = request.getSessionInternal(false);
        if (session != null) {
            SavedRequest savedRequest = (SavedRequest) session.getNote(Constants.FORM_REQUEST_NOTE);
            if (savedRequest != null &&
                    decodedRequestURI.equals(savedRequest.getDecodedRequestURI())) {
                return true;
            }
        }

        return false;
    }


    @Override
    protected String getAuthMethod() {
        return HttpServletRequest.FORM_AUTH;
    }


    /**
     * 转发到登录页面
     *
     * @param request Request we are processing
     * @param response Response we are populating
     * @param config    描述如何进行身份验证的登录配置
     * 
     * @throws IOException  如果转发到登录页面失败， 调用{@link HttpServletResponse#sendError(int, String)}抛出一个{@link IOException}
     */
    protected void forwardToLoginPage(Request request,
            HttpServletResponse response, LoginConfig config)
            throws IOException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("formAuthenticator.forwardLogin",
                    request.getRequestURI(), request.getMethod(),
                    config.getLoginPage(), context.getName()));
        }

        String loginPage = config.getLoginPage();
        if (loginPage == null || loginPage.length() == 0) {
            String msg = sm.getString("formAuthenticator.noLoginPage",
                    context.getName());
            log.warn(msg);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    msg);
            return;
        }

        if (getChangeSessionIdOnAuthentication()) {
            Session session = request.getSessionInternal(false);
            if (session != null) {
                Manager manager = request.getContext().getManager();
                manager.changeSessionId(session);
                request.changeSessionId(session.getId());
            }
        }

        // 总是使用登录页面的 GET, 不管使用什么方法
        String oldMethod = request.getMethod();
        request.getCoyoteRequest().method().setString("GET");

        RequestDispatcher disp =
            context.getServletContext().getRequestDispatcher(loginPage);
        try {
            if (context.fireRequestInitEvent(request.getRequest())) {
                disp.forward(request.getRequest(), response);
                context.fireRequestDestroyEvent(request.getRequest());
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            String msg = sm.getString("formAuthenticator.forwardLoginFail");
            log.warn(msg, t);
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    msg);
        } finally {
            // 恢复原始方法，以便将其写入访问日志
            request.getCoyoteRequest().method().setString(oldMethod);
        }
    }


    /**
     * 重定向到错误页
     *
     * @param request Request we are processing
     * @param response Response we are populating
     * @param config   描述如何进行身份验证的登录配置
     * @throws IOException  如果转发到登录页面失败， 调用{@link HttpServletResponse#sendError(int, String)}抛出一个{@link IOException}
     */
    protected void forwardToErrorPage(Request request,
            HttpServletResponse response, LoginConfig config)
            throws IOException {

        String errorPage = config.getErrorPage();
        if (errorPage == null || errorPage.length() == 0) {
            String msg = sm.getString("formAuthenticator.noErrorPage",
                    context.getName());
            log.warn(msg);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    msg);
            return;
        }

        RequestDispatcher disp =
                context.getServletContext().getRequestDispatcher(config.getErrorPage());
        try {
            if (context.fireRequestInitEvent(request.getRequest())) {
                disp.forward(request.getRequest(), response);
                context.fireRequestDestroyEvent(request.getRequest());
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            String msg = sm.getString("formAuthenticator.forwardErrorFail");
            log.warn(msg, t);
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    msg);
        }
    }


    /**
     * 此请求是否与保存的请求匹配(因此它必须在成功认证后重定向?
     *
     * @param request 待核实的请求
     * 
     * @return <code>true</code>如果请求与保存的请求匹配
     */
    protected boolean matchRequest(Request request) {
        // 是否已创建会话?
        Session session = request.getSessionInternal(false);
        if (session == null) {
            return false;
        }

        // 是否存在保存的请求?
        SavedRequest sreq =
                (SavedRequest) session.getNote(Constants.FORM_REQUEST_NOTE);
        if (sreq == null) {
            return false;
        }

        // 是否存在保存的主体?
        if (session.getNote(Constants.FORM_PRINCIPAL_NOTE) == null) {
            return false;
        }

        // 请求URI匹配吗?
        String decodedRequestURI = request.getDecodedRequestURI();
        if (decodedRequestURI == null) {
            return false;
        }
        return decodedRequestURI.equals(sreq.getDecodedRequestURI());
    }


    /**
     * 从会话中存储的信息恢复原始请求.
     * 如果原始请求不再存在 (由于会话超时), 返回<code>false</code>; 否则返回<code>true</code>.
     *
     * @param request 要恢复的请求
     * @param session 包含保存的信息的会话
     * 
     * @return <code>true</code>如果请求成功恢复
     * @throws IOException 如果过程中发生了IO错误
     */
    protected boolean restoreRequest(Request request, Session session)
            throws IOException {

        // 从会话中检索和删除 SavedRequest对象
        SavedRequest saved = (SavedRequest)
            session.getNote(Constants.FORM_REQUEST_NOTE);
        session.removeNote(Constants.FORM_REQUEST_NOTE);
        session.removeNote(Constants.FORM_PRINCIPAL_NOTE);
        if (saved == null) {
            return false;
        }

        // 吞下任何请求主体，因为我们将替换它
        // AJP连接器使用内容长度标头来确定请求主体需要读取多少数据，需要在头文件恢复之前执行此操作
        byte[] buffer = new byte[4096];
        InputStream is = request.createInputStream();
        while (is.read(buffer) >= 0) {
            // Ignore request body
        }

        // 修改当前的请求以反映最初的请求
        request.clearCookies();
        Iterator<Cookie> cookies = saved.getCookies();
        while (cookies.hasNext()) {
            request.addCookie(cookies.next());
        }

        String method = saved.getMethod();
        MimeHeaders rmh = request.getCoyoteRequest().getMimeHeaders();
        rmh.recycle();
        boolean cacheable = "GET".equalsIgnoreCase(method) ||
                           "HEAD".equalsIgnoreCase(method);
        Iterator<String> names = saved.getHeaderNames();
        while (names.hasNext()) {
            String name = names.next();
            // 浏览器现在不希望有条件响应.
            // 假设它能悄悄地从意想不到的412中恢复过来.
            // BZ 43687
            if(!("If-Modified-Since".equalsIgnoreCase(name) ||
                 (cacheable && "If-None-Match".equalsIgnoreCase(name)))) {
                Iterator<String> values = saved.getHeaderValues(name);
                while (values.hasNext()) {
                    rmh.addValue(name).setString(values.next());
                }
            }
        }

        request.clearLocales();
        Iterator<Locale> locales = saved.getLocales();
        while (locales.hasNext()) {
            request.addLocale(locales.next());
        }

        request.getCoyoteRequest().getParameters().recycle();

        ByteChunk body = saved.getBody();

        if (body != null) {
            request.getCoyoteRequest().action
                (ActionCode.REQ_SET_BODY_REPLAY, body);

            // Set content type
            MessageBytes contentType = MessageBytes.newInstance();

            // 如果没有指定的内容类型, 对于POST使用默认的
            String savedContentType = saved.getContentType();
            if (savedContentType == null && "POST".equalsIgnoreCase(method)) {
                savedContentType = "application/x-www-form-urlencoded";
            }

            contentType.setString(savedContentType);
            request.getCoyoteRequest().setContentType(contentType);
        }

        request.getCoyoteRequest().method().setString(method);

        return true;
    }


    /**
     * 将原始请求信息保存到会话中.
     *
     * @param request 要保存的请求
     * @param session 包含保存信息的会话
     * @throws IOException 如果过程中发生了IO错误
     */
    protected void saveRequest(Request request, Session session)
        throws IOException {

        // 创建和填充一个 SavedRequest 对象
        SavedRequest saved = new SavedRequest();
        Cookie cookies[] = request.getCookies();
        if (cookies != null) {
            for (int i = 0; i < cookies.length; i++) {
                saved.addCookie(cookies[i]);
            }
        }
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                String value = values.nextElement();
                saved.addHeader(name, value);
            }
        }
        Enumeration<Locale> locales = request.getLocales();
        while (locales.hasMoreElements()) {
            Locale locale = locales.nextElement();
            saved.addLocale(locale);
        }

        // May need to acknowledge a 100-continue expectation
        request.getResponse().sendAcknowledgement();

        ByteChunk body = new ByteChunk();
        body.setLimit(request.getConnector().getMaxSavePostSize());

        byte[] buffer = new byte[4096];
        int bytesRead;
        InputStream is = request.getInputStream();

        while ( (bytesRead = is.read(buffer) ) >= 0) {
            body.append(buffer, 0, bytesRead);
        }

        // 如果需要保存，只保存请求主体
        if (body.getLength() > 0) {
            saved.setContentType(request.getContentType());
            saved.setBody(body);
        }

        saved.setMethod(request.getMethod());
        saved.setQueryString(request.getQueryString());
        saved.setRequestURI(request.getRequestURI());
        saved.setDecodedRequestURI(request.getDecodedRequestURI());

        // Stash the SavedRequest in our session for later use
        session.setNote(Constants.FORM_REQUEST_NOTE, saved);
    }


    /**
     * 返回保存的请求的URI (使用相应的查询字符串)，这样就可以重定向到它.
     *
     * @param session 当前会话
     * @return 原始请求URL
     */
    protected String savedRequestURL(Session session) {

        SavedRequest saved =
            (SavedRequest) session.getNote(Constants.FORM_REQUEST_NOTE);
        if (saved == null) {
            return (null);
        }
        StringBuilder sb = new StringBuilder(saved.getRequestURI());
        if (saved.getQueryString() != null) {
            sb.append('?');
            sb.append(saved.getQueryString());
        }
        return (sb.toString());
    }
}
