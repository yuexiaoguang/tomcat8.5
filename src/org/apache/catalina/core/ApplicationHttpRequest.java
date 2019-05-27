package org.apache.catalina.core;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.servlet4preview.http.PushBuilder;
import org.apache.catalina.servlet4preview.http.ServletMapping;
import org.apache.catalina.util.ParameterMap;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.Parameters;

/**
 * 包装一个<code>javax.servlet.http.HttpServletRequest</code>
 * 转换一个应用响应对象(这可能是传递给servlet的原始消息, 或者可能基于  2.3
 * <code>javax.servlet.http.HttpServletRequestWrapper</code>)
 * 回到一个内部的<code>org.apache.catalina.HttpRequest</code>.
 * <p>
 * <strong>WARNING</strong>: 
 * 由于java不支持多重继承, <code>ApplicationRequest</code>中所有的逻辑在<code>ApplicationHttpRequest</code>中是重复的. 
 * 确保在进行更改时保持这两个类同步!
 */
class ApplicationHttpRequest
        extends org.apache.catalina.servlet4preview.http.HttpServletRequestWrapper {


    // ------------------------------------------------------- Static Variables


    /**
     * 请求调度程序特殊的属性名称集合.
     */
    protected static final String specials[] =
    { RequestDispatcher.INCLUDE_REQUEST_URI,
      RequestDispatcher.INCLUDE_CONTEXT_PATH,
      RequestDispatcher.INCLUDE_SERVLET_PATH,
      RequestDispatcher.INCLUDE_PATH_INFO,
      RequestDispatcher.INCLUDE_QUERY_STRING,
      ApplicationDispatcher.INCLUDE_MAPPING,
      RequestDispatcher.FORWARD_REQUEST_URI,
      RequestDispatcher.FORWARD_CONTEXT_PATH,
      RequestDispatcher.FORWARD_SERVLET_PATH,
      RequestDispatcher.FORWARD_PATH_INFO,
      RequestDispatcher.FORWARD_QUERY_STRING,
      ApplicationDispatcher.FORWARD_MAPPING};

    private static final int SPECIALS_FIRST_FORWARD_INDEX = 6;


    // ----------------------------------------------------------- Constructors


    /**
     * @param request 被包装的servlet请求
     * @param context 包装请求的目标上下文
     * @param crossContext {@code true}如果包装请求是跨上下文请求, 否则{@code false}
     */
    public ApplicationHttpRequest(HttpServletRequest request, Context context,
                                  boolean crossContext) {

        super(request);
        this.context = context;
        this.crossContext = crossContext;
        setRequest(request);

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 此请求的上下文.
     */
    protected final Context context;


    /**
     * 请求的上下文路径.
     */
    protected String contextPath = null;


    /**
     * 如果这个请求是跨上下文的, 修改会话访问行为.
     */
    protected final boolean crossContext;


    /**
     * 当前调度器类型.
     */
    protected DispatcherType dispatcherType = null;


    /**
     * 请求参数. 
     * 这是从包装请求初始化的，但是允许更新.
     */
    protected Map<String, String[]> parameters = null;


    /**
     * 这个请求的参数已经被解析了吗?
     */
    private boolean parsedParams = false;


    /**
     * 请求的路径信息.
     */
    protected String pathInfo = null;


    /**
     * 当前请求的查询参数.
     */
    private String queryParamString = null;


    /**
     * 此请求的查询字符串.
     */
    protected String queryString = null;


    /**
     * 当前请求调度器路径.
     */
    protected Object requestDispatcherPath = null;


    /**
     * 此请求的请求URI.
     */
    protected String requestURI = null;


    /**
     * 此请求的servlet路径.
     */
    protected String servletPath = null;


    /**
     * 这个请求的映射.
     */
    private ServletMapping mapping = null;


    /**
     * 此请求的当前活动会话.
     */
    protected Session session = null;


    /**
     * 特殊属性.
     */
    protected final Object[] specialAttributes = new Object[specials.length];


    // ------------------------------------------------- ServletRequest Methods

    @Override
    public ServletContext getServletContext() {
        if (context == null) {
            return null;
        }
        return context.getServletContext();
    }


    /**
     * 重写包装请求的<code>getAttribute()</code>方法.
     *
     * @param name 要检索的属性的名称
     */
    @Override
    public Object getAttribute(String name) {

        if (name.equals(Globals.DISPATCHER_TYPE_ATTR)) {
            return dispatcherType;
        } else if (name.equals(Globals.DISPATCHER_REQUEST_PATH_ATTR)) {
            if ( requestDispatcherPath != null ){
                return requestDispatcherPath.toString();
            } else {
                return null;
            }
        }

        int pos = getSpecial(name);
        if (pos == -1) {
            return getRequest().getAttribute(name);
        } else {
            if ((specialAttributes[pos] == null) &&
                    (specialAttributes[SPECIALS_FIRST_FORWARD_INDEX] == null) &&
                    (pos >= SPECIALS_FIRST_FORWARD_INDEX)) {
                // 如果它是一个重定向特殊属性, 和 null, 这意味着这是一个include, 因此，检查包装请求，因为请求可能在包含之前被转发
                return getRequest().getAttribute(name);
            } else {
                return specialAttributes[pos];
            }
        }

    }


    @Override
    public Enumeration<String> getAttributeNames() {
        return (new AttributeNamesEnumerator());
    }


    /**
     * @param name 要删除的属性的名称
     */
    @Override
    public void removeAttribute(String name) {

        if (!removeSpecial(name))
            getRequest().removeAttribute(name);

    }


    /**
     * @param name 属性名
     * @param value 属性值
     */
    @Override
    public void setAttribute(String name, Object value) {

        if (name.equals(Globals.DISPATCHER_TYPE_ATTR)) {
            dispatcherType = (DispatcherType)value;
            return;
        } else if (name.equals(Globals.DISPATCHER_REQUEST_PATH_ATTR)) {
            requestDispatcherPath = value;
            return;
        }

        if (!setSpecial(name, value)) {
            getRequest().setAttribute(name, value);
        }

    }


    /**
     * 返回一个将资源包装在指定的路径上的RequestDispatcher, 它可以被解释为相对于当前请求路径.
     *
     * @param path 要包装的资源的路径
     */
    @Override
    public RequestDispatcher getRequestDispatcher(String path) {

        if (context == null)
            return (null);

        // 如果路径已经是上下文相对的, 传递它
        if (path == null)
            return (null);
        else if (path.startsWith("/"))
            return (context.getServletContext().getRequestDispatcher(path));

        // 将请求相对路径转换为上下文相对路径
        String servletPath =
            (String) getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
        if (servletPath == null)
            servletPath = getServletPath();

        // 添加路径信息
        String pathInfo = getPathInfo();
        String requestPath = null;

        if (pathInfo == null) {
            requestPath = servletPath;
        } else {
            requestPath = servletPath + pathInfo;
        }

        int pos = requestPath.lastIndexOf('/');
        String relative = null;
        if (pos >= 0) {
            relative = requestPath.substring(0, pos + 1) + path;
        } else {
            relative = requestPath + path;
        }

        return (context.getServletContext().getRequestDispatcher(relative));

    }


    @Override
    public DispatcherType getDispatcherType() {
        return dispatcherType;
    }


    // --------------------------------------------- HttpServletRequest Methods


    @Override
    public String getContextPath() {

        return (this.contextPath);

    }


    /**
     * @param name 请求参数的名称
     */
    @Override
    public String getParameter(String name) {

        parseParameters();

        String[] value = parameters.get(name);
        if (value == null) {
            return null;
        }
        return value[0];
    }


    @Override
    public Map<String, String[]> getParameterMap() {
        parseParameters();
        return (parameters);
    }


    @Override
    public Enumeration<String> getParameterNames() {
        parseParameters();
        return Collections.enumeration(parameters.keySet());
    }


    /**
     * @param name 请求参数的名称
     */
    @Override
    public String[] getParameterValues(String name) {

        parseParameters();
        return parameters.get(name);
    }


    @Override
    public String getPathInfo() {
        return (this.pathInfo);
    }


    @Override
    public String getPathTranslated() {
        if (getPathInfo() == null || getServletContext() == null) {
            return null;
        }

        return getServletContext().getRealPath(getPathInfo());
    }


    @Override
    public String getQueryString() {
        return (this.queryString);
    }


    @Override
    public String getRequestURI() {
        return (this.requestURI);
    }


    @Override
    public StringBuffer getRequestURL() {

        StringBuffer url = new StringBuffer();
        String scheme = getScheme();
        int port = getServerPort();
        if (port < 0)
            port = 80; // Work around java.net.URL bug

        url.append(scheme);
        url.append("://");
        url.append(getServerName());
        if ((scheme.equals("http") && (port != 80))
            || (scheme.equals("https") && (port != 443))) {
            url.append(':');
            url.append(port);
        }
        url.append(getRequestURI());

        return (url);
    }


    @Override
    public String getServletPath() {
        return (this.servletPath);
    }


    @Override
    public ServletMapping getServletMapping() {
        return mapping;
    }


    /**
     * 返回与此请求关联的会话, 必要时创建一个.
     */
    @Override
    public HttpSession getSession() {
        return (getSession(true));
    }


    /**
     * 返回与此请求关联的会话, 必要时创建一个.
     *
     * @param create 如果不存在，是否创建新会话
     */
    @Override
    public HttpSession getSession(boolean create) {

        if (crossContext) {

            // 如果还没有分配上下文，就不能有会话
            if (context == null)
                return (null);

            // 返回当前会话，如果它存在并且有效
            if (session != null && session.isValid()) {
                return (session.getSession());
            }

            HttpSession other = super.getSession(false);
            if (create && (other == null)) {
                // 首先在第一个上下文中创建一个会话: 问题是顶层请求是唯一可以安全创建cookie的请求
                other = super.getSession(true);
            }
            if (other != null) {
                Session localSession = null;
                try {
                    localSession =
                        context.getManager().findSession(other.getId());
                    if (localSession != null && !localSession.isValid()) {
                        localSession = null;
                    }
                } catch (IOException e) {
                    // Ignore
                }
                if (localSession == null && create) {
                    localSession =
                        context.getManager().createSession(other.getId());
                }
                if (localSession != null) {
                    localSession.access();
                    session = localSession;
                    return session.getSession();
                }
            }
            return null;

        } else {
            return super.getSession(create);
        }
    }


    /**
     * 返回true，如果请求指定一个JSESSIONID(在这个ApplicationHttpRequest的上下文中是可用的), 否则返回false.
     */
    @Override
    public boolean isRequestedSessionIdValid() {

        if (crossContext) {

            String requestedSessionId = getRequestedSessionId();
            if (requestedSessionId == null)
                return false;
            if (context == null)
                return false;
            Manager manager = context.getManager();
            if (manager == null)
                return false;
            Session session = null;
            try {
                session = manager.findSession(requestedSessionId);
            } catch (IOException e) {
                // Ignore
            }
            if ((session != null) && session.isValid()) {
                return true;
            } else {
                return false;
            }

        } else {
            return super.isRequestedSessionIdValid();
        }
    }


    @Override
    public PushBuilder newPushBuilder() {
        ServletRequest current = getRequest();
        while (current instanceof ServletRequestWrapper) {
            current = ((ServletRequestWrapper) current).getRequest();
        }
        if (current instanceof RequestFacade) {
            return ((RequestFacade) current).newPushBuilder(this);
        } else {
            return null;
        }
    }


    // -------------------------------------------------------- Package Methods

    /**
     * 回收这个请求
     */
    public void recycle() {
        if (session != null) {
            session.endAccess();
        }
    }


    /**
     * 设置此请求的上下文路径.
     *
     * @param contextPath 上下文路径
     */
    void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }


    /**
     * 设置此请求的路径信息.
     *
     * @param pathInfo 路径信息
     */
    void setPathInfo(String pathInfo) {
        this.pathInfo = pathInfo;
    }


    /**
     * 设置这个请求的查询字符串.
     *
     * @param queryString 查询字符串
     */
    void setQueryString(String queryString) {
        this.queryString = queryString;
    }


    /**
     * 设置包装的请求.
     *
     * @param request 包装的请求
     */
    void setRequest(HttpServletRequest request) {

        super.setRequest(request);

        // 初始化此请求的属性
        dispatcherType = (DispatcherType)request.getAttribute(Globals.DISPATCHER_TYPE_ATTR);
        requestDispatcherPath = request.getAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR);

        // 初始化此请求的路径元素
        contextPath = request.getContextPath();
        pathInfo = request.getPathInfo();
        queryString = request.getQueryString();
        requestURI = request.getRequestURI();
        servletPath = request.getServletPath();
        if (request instanceof org.apache.catalina.servlet4preview.http.HttpServletRequest) {
            mapping = ((org.apache.catalina.servlet4preview.http.HttpServletRequest) request).getServletMapping();
        } else {
            mapping = (new ApplicationMapping(null)).getServletMapping();
        }
    }


    /**
     * 设置这个请求的请求URI.
     *
     * @param requestURI The new request URI
     */
    void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }


    /**
     * 设置此请求的servlet路径.
     *
     * @param servletPath The new servlet path
     */
    void setServletPath(String servletPath) {
        this.servletPath = servletPath;
    }


    /**
     * 解析此请求的参数.
     *
     * 如果参数在查询字符串和请求内容中都存在, 合并.
     */
    void parseParameters() {

        if (parsedParams) {
            return;
        }

        parameters = new ParameterMap<>();
        parameters.putAll(getRequest().getParameterMap());
        mergeParameters();
        ((ParameterMap<String,String[]>) parameters).setLocked(true);
        parsedParams = true;
    }


    /**
     * 保存此请求的查询参数.
     *
     * @param queryString 包含此请求参数的查询字符串
     */
    void setQueryParams(String queryString) {
        this.queryParamString = queryString;
    }


    void setMapping(ServletMapping mapping) {
        this.mapping = mapping;
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * 这是一个特殊的属性名称，只加入included servlet中?
     *
     * @param name 属性名
     */
    protected boolean isSpecial(String name) {

        for (int i = 0; i < specials.length; i++) {
            if (specials[i].equals(name))
                return true;
        }
        return false;

    }


    /**
     * 获取特殊属性.
     *
     * @return 特殊属性位置, 或 -1 没有特殊属性
     */
    protected int getSpecial(String name) {
        for (int i = 0; i < specials.length; i++) {
            if (specials[i].equals(name)) {
                return (i);
            }
        }
        return (-1);
    }


    /**
     * 设置特殊属性.
     *
     * @return true 如果属性是一个特殊属性, 否则false
     */
    protected boolean setSpecial(String name, Object value) {
        for (int i = 0; i < specials.length; i++) {
            if (specials[i].equals(name)) {
                specialAttributes[i] = value;
                return true;
            }
        }
        return false;
    }


    /**
     * 移除特殊属性.
     *
     * @return true 如果属性是一个特殊属性, 否则false
     */
    protected boolean removeSpecial(String name) {
        for (int i = 0; i < specials.length; i++) {
            if (specials[i].equals(name)) {
                specialAttributes[i] = null;
                return true;
            }
        }
        return false;
    }


    /**
     * 将两组参数值合并到一个字符串数组中.
     *
     * @param values1 First set of values
     * @param values2 Second set of values
     */
    private String[] mergeValues(String[] values1, String[] values2) {

        ArrayList<Object> results = new ArrayList<>();

        if (values1 == null) {
            // Skip - nothing to merge
        } else {
            for (String value : values1) {
                results.add(value);
            }
        }

        if (values2 == null) {
            // Skip - nothing to merge
        } else {
            for (String value : values2) {
                results.add(value);
            }
        }

        String values[] = new String[results.size()];
        return results.toArray(values);
    }


    // ------------------------------------------------------ Private Methods


    /**
     * 合并保存的查询参数和在此请求中已经存在的参数, 这样，如果有重复的参数名，则查询字符串中的参数值首先出现.
     */
    private void mergeParameters() {

        if ((queryParamString == null) || (queryParamString.length() < 1))
            return;

        // 从调度目标解析查询字符串
        Parameters paramParser = new Parameters();
        MessageBytes queryMB = MessageBytes.newInstance();
        queryMB.setString(queryParamString);

        // TODO
        // - Should only use body encoding if useBodyEncodingForURI is true
        // - Otherwise, should use URIEncoding
        // - The problem is that the connector is not available...

        String encoding = getCharacterEncoding();
        Charset charset = null;
        if (encoding != null) {
            try {
                charset = B2CConverter.getCharset(encoding);
                queryMB.setCharset(charset);
            } catch (UnsupportedEncodingException e) {
                // Fall-back to default (ISO-8859-1)
                charset = StandardCharsets.ISO_8859_1;
            }
        }

        paramParser.setQuery(queryMB);
        paramParser.setQueryStringCharset(charset);
        paramParser.handleQueryParameters();

        // 从调度目标插入附加参数
        Enumeration<String> dispParamNames = paramParser.getParameterNames();
        while (dispParamNames.hasMoreElements()) {
            String dispParamName = dispParamNames.nextElement();
            String[] dispParamValues = paramParser.getParameterValues(dispParamName);
            String[] originalValues = parameters.get(dispParamName);
            if (originalValues == null) {
                parameters.put(dispParamName, dispParamValues);
                continue;
            }
            parameters.put(dispParamName, mergeValues(dispParamValues, originalValues));
        }
    }


    // ----------------------------------- AttributeNamesEnumerator Inner Class


    /**
     * 用于将特殊属性公开为可用的请求属性.
     */
    protected class AttributeNamesEnumerator implements Enumeration<String> {

        protected int pos = -1;
        protected final int last;
        protected final Enumeration<String> parentEnumeration;
        protected String next = null;

        public AttributeNamesEnumerator() {
            int last = -1;
            parentEnumeration = getRequest().getAttributeNames();
            for (int i = specialAttributes.length - 1; i >= 0; i--) {
                if (getAttribute(specials[i]) != null) {
                    last = i;
                    break;
                }
            }
            this.last = last;
        }

        @Override
        public boolean hasMoreElements() {
            return ((pos != last) || (next != null)
                    || ((next = findNext()) != null));
        }

        @Override
        public String nextElement() {
            if (pos != last) {
                for (int i = pos + 1; i <= last; i++) {
                    if (getAttribute(specials[i]) != null) {
                        pos = i;
                        return (specials[i]);
                    }
                }
            }
            String result = next;
            if (next != null) {
                next = findNext();
            } else {
                throw new NoSuchElementException();
            }
            return result;
        }

        protected String findNext() {
            String result = null;
            while ((result == null) && (parentEnumeration.hasMoreElements())) {
                String current = parentEnumeration.nextElement();
                if (!isSpecial(current)) {
                    result = current;
                }
            }
            return result;
        }
    }
}
