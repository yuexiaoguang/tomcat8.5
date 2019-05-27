package org.apache.catalina.core;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.naming.NamingException;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRegistration.Dynamic;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Service;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.mapper.MappingData;
import org.apache.catalina.servlet4preview.http.ServletMapping;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.URLEncoder;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.http.RequestUtil;
import org.apache.tomcat.util.res.StringManager;

/**
 * <code>ServletContext</code>的标准实现类，表示Web应用程序的执行环境. 
 * 这个类的实例被关联到每个<code>StandardContext</code>实例
 */
@SuppressWarnings("deprecation")
public class ApplicationContext implements org.apache.catalina.servlet4preview.ServletContext {

    protected static final boolean STRICT_SERVLET_COMPLIANCE;

    protected static final boolean GET_RESOURCE_REQUIRE_SLASH;


    static {
        STRICT_SERVLET_COMPLIANCE = Globals.STRICT_SERVLET_COMPLIANCE;

        String requireSlash = System.getProperty(
                "org.apache.catalina.core.ApplicationContext.GET_RESOURCE_REQUIRE_SLASH");
        if (requireSlash == null) {
            GET_RESOURCE_REQUIRE_SLASH = STRICT_SERVLET_COMPLIANCE;
        } else {
            GET_RESOURCE_REQUIRE_SLASH = Boolean.parseBoolean(requireSlash);
        }
    }

    // ----------------------------------------------------------- Constructors


    /**
     * @param context 关联的Context实例
     */
    public ApplicationContext(StandardContext context) {
        super();
        this.context = context;
        this.service = ((Engine) context.getParent().getParent()).getService();
        this.sessionCookieConfig = new ApplicationSessionCookieConfig(context);

        // 会话跟踪模式
        populateSessionTrackingModes();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 上下文属性.
     */
    protected Map<String,Object> attributes = new ConcurrentHashMap<>();


    /**
     * 此上下文的只读属性列表.
     */
    private final Map<String,String> readOnlyAttributes = new ConcurrentHashMap<>();


    /**
     * 关联的Context.
     */
    private final StandardContext context;


    /**
     * 关联的Service实例.
     */
    private final Service service;


    /**
     * 空字符串集合.
     */
    private static final List<String> emptyString = Collections.emptyList();


    /**
     * 空Servlet集合.
     */
    private static final List<Servlet> emptyServlet = Collections.emptyList();


    /**
     * 封装的外观模式.
     */
    private final ServletContext facade = new ApplicationContextFacade(this);


    /**
     * 合并的上下文初始化参数.
     */
    private final ConcurrentMap<String,String> parameters = new ConcurrentHashMap<>();


    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 请求调度中使用的线程本地数据.
     */
    private final ThreadLocal<DispatchData> dispatchData = new ThreadLocal<>();


    /**
     * Session Cookie配置
     */
    private SessionCookieConfig sessionCookieConfig;

    /**
     * Session跟踪模式
     */
    private Set<SessionTrackingMode> sessionTrackingModes = null;
    private Set<SessionTrackingMode> defaultSessionTrackingModes = null;
    private Set<SessionTrackingMode> supportedSessionTrackingModes = null;

    /**
     * 是否添加了{@link ServletContextListener}. 一旦第一个{@link ServletContextListener}被调用, 不再添加别的.
     */
    private boolean newServletContextListenerAllowed = true;


    // ------------------------------------------------- ServletContext Methods

    @Override
    public Object getAttribute(String name) {
        return (attributes.get(name));
    }


    @Override
    public Enumeration<String> getAttributeNames() {
        Set<String> names = new HashSet<>();
        names.addAll(attributes.keySet());
        return Collections.enumeration(names);
    }


    @Override
    public ServletContext getContext(String uri) {

        // 验证指定参数的格式
        if (uri == null || !uri.startsWith("/")) {
            return null;
        }

        Context child = null;
        try {
            // 寻找精确的匹配
            Container host = context.getParent();
            child = (Context) host.findChild(uri);

            // 非运行上下文应被忽略.
            if (child != null && !child.getState().isAvailable()) {
                child = null;
            }

            // 删除任何版本信息并使用映射器
            if (child == null) {
                int i = uri.indexOf("##");
                if (i > -1) {
                    uri = uri.substring(0, i);
                }
                // Note: 使用专用Mapper方法可以更高效，但是这样的实现将需要对映射器进行一些重构，以避免现有代码的复制/粘贴.
                MessageBytes hostMB = MessageBytes.newInstance();
                hostMB.setString(host.getName());

                MessageBytes pathMB = MessageBytes.newInstance();
                pathMB.setString(uri);

                MappingData mappingData = new MappingData();
                ((Engine) host.getParent()).getService().getMapper().map(hostMB, pathMB, null, mappingData);
                child = mappingData.context;
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            return null;
        }

        if (child == null) {
            return null;
        }

        if (context.getCrossContext()) {
            // 如果启用crossContext, 总是可以返回上下文
            return child.getServletContext();
        } else if (child == context) {
            // 仍然可以返回当前上下文
            return context.getServletContext();
        } else {
            // Nothing to return
            return null;
        }
    }


    @Override
    public String getContextPath() {
        return context.getPath();
    }


    @Override
    public String getInitParameter(final String name) {
        // 上下文的XML设置的特殊处理必须始终覆盖可能由应用程序设置的任何内容.
        if (Globals.JASPER_XML_VALIDATION_TLD_INIT_PARAM.equals(name) &&
                context.getTldValidation()) {
            return "true";
        }
        if (Globals.JASPER_XML_BLOCK_EXTERNAL_INIT_PARAM.equals(name)) {
            if (!context.getXmlBlockExternal()) {
                // 系统管理员已显式更改了默认值
                return "false";
            }
        }
        return parameters.get(name);
    }


    @Override
    public Enumeration<String> getInitParameterNames() {
        Set<String> names = new HashSet<>();
        names.addAll(parameters.keySet());
        // 这些属性的XML设置的特殊处理将总是有效的，如果它们被设定在上下文中
        if (context.getTldValidation()) {
            names.add(Globals.JASPER_XML_VALIDATION_TLD_INIT_PARAM);
        }
        if (!context.getXmlBlockExternal()) {
            names.add(Globals.JASPER_XML_BLOCK_EXTERNAL_INIT_PARAM);
        }
        return Collections.enumeration(names);
    }


    @Override
    public int getMajorVersion() {
        return Constants.MAJOR_VERSION;
    }


    @Override
    public int getMinorVersion() {
        return Constants.MINOR_VERSION;
    }


    /**
     * 返回指定文件的 MIME类型, 或者<code>null</code>.
     *
     * @param file 用于识别MIME类型的文件名
     */
    @Override
    public String getMimeType(String file) {

        if (file == null)
            return (null);
        int period = file.lastIndexOf('.');
        if (period < 0)
            return (null);
        String extension = file.substring(period + 1);
        if (extension.length() < 1)
            return (null);
        return (context.findMimeMapping(extension));

    }


    /**
     * 返回一个作为命名servlet的封装的<code>RequestDispatcher</code>对象.
     *
     * @param name 请求调度器的servlet的名称
     */
    @Override
    public RequestDispatcher getNamedDispatcher(String name) {

        // 验证名称参数
        if (name == null)
            return (null);

        // 创建并返回相应的请求调度器
        Wrapper wrapper = (Wrapper) context.findChild(name);
        if (wrapper == null)
            return (null);

        return new ApplicationDispatcher(wrapper, null, null, null, null, null, name);

    }


    @Override
    public String getRealPath(String path) {
        String validatedPath = validateResourcePath(path, true);
        return context.getRealPath(validatedPath);
    }


    @Override
    public RequestDispatcher getRequestDispatcher(final String path) {

        // 验证路径参数
        if (path == null) {
            return (null);
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(
                    sm.getString("applicationContext.requestDispatcher.iae", path));
        }

        // 需要分离查询字符串和URI. ApplicationDispatcher构造参数需要这个. 映射还需要没有查询字符串的URI.
        String uri;
        String queryString;
        int pos = path.indexOf('?');
        if (pos >= 0) {
            uri = path.substring(0, pos);
            queryString = path.substring(pos + 1);
        } else {
            uri = path;
            queryString = null;
        }

        String normalizedPath = RequestUtil.normalize(uri);
        if (normalizedPath == null) {
            return (null);
        }

        if (getContext().getDispatchersUseEncodedPaths()) {
            // Decode
            String decodedPath;
            try {
                decodedPath = URLDecoder.decode(normalizedPath, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // Impossible
                return null;
            }

            // 尝试编码 /../
            normalizedPath = RequestUtil.normalize(decodedPath);
            if (!decodedPath.equals(normalizedPath)) {
                getContext().getLogger().warn(
                        sm.getString("applicationContext.illegalDispatchPath", path),
                        new IllegalArgumentException());
                return null;
            }

            // URI需要包含上下文路径
            uri = URLEncoder.DEFAULT.encode(getContextPath(), StandardCharsets.UTF_8) + uri;
        } else {
            // uri 传递到 ApplicationDispatcher的构造方法，最终作为返回编码的值的 getRequestURI()值.
            // 因此，由于路径中传递的值被解码，所以在这里编码URI.
            uri = URLEncoder.DEFAULT.encode(getContextPath() + uri, StandardCharsets.UTF_8);
        }

        pos = normalizedPath.length();

        // 使用线程本地URI和映射数据
        DispatchData dd = dispatchData.get();
        if (dd == null) {
            dd = new DispatchData();
            dispatchData.set(dd);
        }

        MessageBytes uriMB = dd.uriMB;
        uriMB.recycle();

        // 使用线程本地映射数据
        MappingData mappingData = dd.mappingData;

        // Map the URI
        CharChunk uriCC = uriMB.getCharChunk();
        try {
            uriCC.append(context.getPath(), 0, context.getPath().length());
            /*
             * 忽略任何尾随的路径参数(使用 ';'分隔)
             */
            int semicolon = normalizedPath.indexOf(';');
            if (pos >= 0 && semicolon > pos) {
                semicolon = -1;
            }
            uriCC.append(normalizedPath, 0, semicolon > 0 ? semicolon : pos);
            service.getMapper().map(context, uriMB, mappingData);
            if (mappingData.wrapper == null) {
                return (null);
            }
            /*
             * 追加任何尾随的路径参数 (使用 ';'分隔) 为了映射的目的忽略了这一点, 因此，他们反映在RequestDispatcher的requestURI
             */
            if (semicolon > 0) {
                uriCC.append(normalizedPath, semicolon, pos - semicolon);
            }
        } catch (Exception e) {
            // Should never happen
            log(sm.getString("applicationContext.mapping.error"), e);
            return (null);
        }

        Wrapper wrapper = mappingData.wrapper;
        String wrapperPath = mappingData.wrapperPath.toString();
        String pathInfo = mappingData.pathInfo.toString();
        ServletMapping mapping = (new ApplicationMapping(mappingData)).getServletMapping();

        mappingData.recycle();

        // 创建一个RequestDispatcher处理这个请求
        return new ApplicationDispatcher(wrapper, uri, wrapperPath, pathInfo,
                queryString, mapping, null);
    }


    @Override
    public URL getResource(String path) throws MalformedURLException {

        String validatedPath = validateResourcePath(path, false);

        if (validatedPath == null) {
            throw new MalformedURLException(
                    sm.getString("applicationContext.requestDispatcher.iae", path));
        }

        WebResourceRoot resources = context.getResources();
        if (resources != null) {
            return resources.getResource(validatedPath).getURL();
        }

        return null;
    }


    @Override
    public InputStream getResourceAsStream(String path) {

        String validatedPath = validateResourcePath(path, false);

        if (validatedPath == null) {
            return null;
        }

        WebResourceRoot resources = context.getResources();
        if (resources != null) {
            return resources.getResource(validatedPath).getInputStream();
        }

        return null;
    }


    /*
     * 返回null, 如果输入路径无效或路径将是resources.getResource()可接受的 .
     */
    private String validateResourcePath(String path, boolean allowEmptyPath) {
        if (path == null) {
            return null;
        }

        if (path.length() == 0 && allowEmptyPath) {
            return path;
        }

        if (!path.startsWith("/")) {
            if (GET_RESOURCE_REQUIRE_SLASH) {
                return null;
            } else {
                return "/" + path;
            }
        }

        return path;
    }


    @Override
    public Set<String> getResourcePaths(String path) {

        // Validate the path argument
        if (path == null) {
            return null;
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException
                (sm.getString("applicationContext.resourcePaths.iae", path));
        }

        WebResourceRoot resources = context.getResources();
        if (resources != null) {
            return resources.listWebAppPaths(path);
        }

        return null;
    }


    @Override
    public String getServerInfo() {
        return ServerInfo.getServerInfo();
    }



    @Override
    public String getServletContextName() {
        return context.getDisplayName();
    }


    @Override
    public void log(String message) {
        context.getLogger().info(message);
    }

    @Override
    public void log(String message, Throwable throwable) {
        context.getLogger().error(message, throwable);
    }


    @Override
    public void removeAttribute(String name) {

        Object value = null;

        // 删除指定属性
        // 检查只读属性
        if (readOnlyAttributes.containsKey(name)){
            return;
        }
        value = attributes.remove(name);
        if (value == null) {
            return;
        }

        // 通知事件监听器
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0))
            return;
        ServletContextAttributeEvent event =
          new ServletContextAttributeEvent(context.getServletContext(),
                                            name, value);
        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof ServletContextAttributeListener))
                continue;
            ServletContextAttributeListener listener =
                (ServletContextAttributeListener) listeners[i];
            try {
                context.fireContainerEvent("beforeContextAttributeRemoved",
                                           listener);
                listener.attributeRemoved(event);
                context.fireContainerEvent("afterContextAttributeRemoved",
                                           listener);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                context.fireContainerEvent("afterContextAttributeRemoved",
                                           listener);
                // FIXME - should we do anything besides log these?
                log(sm.getString("applicationContext.attributeEvent"), t);
            }
        }
    }


    @Override
    public void setAttribute(String name, Object value) {
        // Name cannot be null
        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("applicationContext.setAttribute.namenull"));

        // Null 等同于 removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        // 添加或替换指定的属性
        // 检查只读属性
        if (readOnlyAttributes.containsKey(name))
            return;

        Object oldValue = attributes.put(name, value);
        boolean replaced = oldValue != null;

        // 通知相关事件监听器
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0))
            return;
        ServletContextAttributeEvent event = null;
        if (replaced)
            event =
                new ServletContextAttributeEvent(context.getServletContext(),
                                                 name, oldValue);
        else
            event =
                new ServletContextAttributeEvent(context.getServletContext(),
                                                 name, value);

        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof ServletContextAttributeListener))
                continue;
            ServletContextAttributeListener listener =
                (ServletContextAttributeListener) listeners[i];
            try {
                if (replaced) {
                    context.fireContainerEvent
                        ("beforeContextAttributeReplaced", listener);
                    listener.attributeReplaced(event);
                    context.fireContainerEvent("afterContextAttributeReplaced",
                                               listener);
                } else {
                    context.fireContainerEvent("beforeContextAttributeAdded",
                                               listener);
                    listener.attributeAdded(event);
                    context.fireContainerEvent("afterContextAttributeAdded",
                                               listener);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                if (replaced)
                    context.fireContainerEvent("afterContextAttributeReplaced",
                                               listener);
                else
                    context.fireContainerEvent("afterContextAttributeAdded",
                                               listener);
                // FIXME - should we do anything besides log these?
                log(sm.getString("applicationContext.attributeEvent"), t);
            }
        }
    }


    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, String className) {
        return addFilter(filterName, className, null);
    }


    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
        return addFilter(filterName, null, filter);
    }


    @Override
    public FilterRegistration.Dynamic addFilter(String filterName,
            Class<? extends Filter> filterClass) {
        return addFilter(filterName, filterClass.getName(), null);
    }


    private FilterRegistration.Dynamic addFilter(String filterName,
            String filterClass, Filter filter) throws IllegalStateException {

        if (filterName == null || filterName.equals("")) {
            throw new IllegalArgumentException(sm.getString(
                    "applicationContext.invalidFilterName", filterName));
        }

        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            //TODO Spec breaking enhancement to ignore this restriction
            throw new IllegalStateException(
                    sm.getString("applicationContext.addFilter.ise",
                            getContextPath()));
        }

        FilterDef filterDef = context.findFilterDef(filterName);

        // 假设'complete' FilterRegistration有class和name
        if (filterDef == null) {
            filterDef = new FilterDef();
            filterDef.setFilterName(filterName);
            context.addFilterDef(filterDef);
        } else {
            if (filterDef.getFilterName() != null &&
                    filterDef.getFilterClass() != null) {
                return null;
            }
        }

        if (filter == null) {
            filterDef.setFilterClass(filterClass);
        } else {
            filterDef.setFilterClass(filter.getClass().getName());
            filterDef.setFilter(filter);
        }

        return new ApplicationFilterRegistration(filterDef, context);
    }


    @Override
    public <T extends Filter> T createFilter(Class<T> c) throws ServletException {
        try {
            @SuppressWarnings("unchecked")
            T filter = (T) context.getInstanceManager().newInstance(c.getName());
            return filter;
        } catch (InvocationTargetException e) {
            ExceptionUtils.handleThrowable(e.getCause());
            throw new ServletException(e);
        } catch (IllegalAccessException | NamingException | InstantiationException |
                ClassNotFoundException | NoSuchMethodException e) {
            throw new ServletException(e);
        }
    }


    @Override
    public FilterRegistration getFilterRegistration(String filterName) {
        FilterDef filterDef = context.findFilterDef(filterName);
        if (filterDef == null) {
            return null;
        }
        return new ApplicationFilterRegistration(filterDef, context);
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, String className) {
        return addServlet(servletName, className, null, null);
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
        return addServlet(servletName, null, servlet, null);
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName,
            Class<? extends Servlet> servletClass) {
        return addServlet(servletName, servletClass.getName(), null, null);
    }


    @Override
    public Dynamic addJspFile(String jspName, String jspFile) {

        // jspName 在 addServlet()中验证
        if (jspFile == null || !jspFile.startsWith("/")) {
            throw new IllegalArgumentException(
                    sm.getString("applicationContext.addJspFile.iae", jspFile));
        }

        String jspServletClassName = null;
        Map<String,String> jspFileInitParams = new HashMap<>();

        Wrapper jspServlet = (Wrapper) context.findChild("jsp");

        if (jspServlet == null) {
            // 当前没有定义JSP servlet.
            // 使用默认的JSP servlet类名
            jspServletClassName = Constants.JSP_SERVLET_CLASS;
        } else {
            // 定义了JSP Servlet.
            // 使用相同的 JSP Servlet类名
            jspServletClassName = jspServlet.getServletClass();
            // 使用相同的初始化参数
            String[] params = jspServlet.findInitParameters();
            for (String param : params) {
                jspFileInitParams.put(param, jspServlet.findInitParameter(param));
            }
        }

        // 添加init参数以指定JSP文件
        jspFileInitParams.put("jspFile", jspFile);

        return addServlet(jspName, jspServletClassName, null, jspFileInitParams);
    }


    private ServletRegistration.Dynamic addServlet(String servletName, String servletClass,
            Servlet servlet, Map<String,String> initParams) throws IllegalStateException {

        if (servletName == null || servletName.equals("")) {
            throw new IllegalArgumentException(sm.getString(
                    "applicationContext.invalidServletName", servletName));
        }

        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            //TODO Spec breaking enhancement to ignore this restriction
            throw new IllegalStateException(
                    sm.getString("applicationContext.addServlet.ise",
                            getContextPath()));
        }

        Wrapper wrapper = (Wrapper) context.findChild(servletName);

        // 假设'complete' ServletRegistration有class和name
        if (wrapper == null) {
            wrapper = context.createWrapper();
            wrapper.setName(servletName);
            context.addChild(wrapper);
        } else {
            if (wrapper.getName() != null &&
                    wrapper.getServletClass() != null) {
                if (wrapper.isOverridable()) {
                    wrapper.setOverridable(false);
                } else {
                    return null;
                }
            }
        }

        if (servlet == null) {
            wrapper.setServletClass(servletClass);
        } else {
            wrapper.setServletClass(servlet.getClass().getName());
            wrapper.setServlet(servlet);
        }

        if (initParams != null) {
            for (Map.Entry<String, String> initParam: initParams.entrySet()) {
                wrapper.addInitParameter(initParam.getKey(), initParam.getValue());
            }
        }

        return context.dynamicServletAdded(wrapper);
    }


    @Override
    public <T extends Servlet> T createServlet(Class<T> c)
    throws ServletException {
        try {
            @SuppressWarnings("unchecked")
            T servlet = (T) context.getInstanceManager().newInstance(c.getName());
            context.dynamicServletCreated(servlet);
            return servlet;
        } catch (InvocationTargetException e) {
            ExceptionUtils.handleThrowable(e.getCause());
            throw new ServletException(e);
        } catch (IllegalAccessException | NamingException | InstantiationException |
                ClassNotFoundException | NoSuchMethodException e) {
            throw new ServletException(e);
        }
    }


    @Override
    public ServletRegistration getServletRegistration(String servletName) {
        Wrapper wrapper = (Wrapper) context.findChild(servletName);
        if (wrapper == null) {
            return null;
        }

        return new ApplicationServletRegistration(wrapper, context);
    }


    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return defaultSessionTrackingModes;
    }


    private void populateSessionTrackingModes() {
        // 默认情况下URL重写总是启用的
        defaultSessionTrackingModes = EnumSet.of(SessionTrackingMode.URL);
        supportedSessionTrackingModes = EnumSet.of(SessionTrackingMode.URL);

        if (context.getCookies()) {
            defaultSessionTrackingModes.add(SessionTrackingMode.COOKIE);
            supportedSessionTrackingModes.add(SessionTrackingMode.COOKIE);
        }

        // SSL在默认情况下不能启用，因为它只能自己使用
        // Context > Host > Engine > Service
        Service s = ((Engine) context.getParent().getParent()).getService();
        Connector[] connectors = s.findConnectors();
        // 需要至少一个启用SSL的连接器使用SSL会话ID.
        for (Connector connector : connectors) {
            if (Boolean.TRUE.equals(connector.getAttribute("SSLEnabled"))) {
                supportedSessionTrackingModes.add(SessionTrackingMode.SSL);
                break;
            }
        }
    }


    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        if (sessionTrackingModes != null) {
            return sessionTrackingModes;
        }
        return defaultSessionTrackingModes;
    }


    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return sessionCookieConfig;
    }


    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {

        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(
                    sm.getString("applicationContext.setSessionTracking.ise",
                            getContextPath()));
        }

        // 检查是否仅支持已请求的跟踪模式
        for (SessionTrackingMode sessionTrackingMode : sessionTrackingModes) {
            if (!supportedSessionTrackingModes.contains(sessionTrackingMode)) {
                throw new IllegalArgumentException(sm.getString(
                        "applicationContext.setSessionTracking.iae.invalid",
                        sessionTrackingMode.toString(), getContextPath()));
            }
        }

        // 检查SSL没有配置任何其他内容
        if (sessionTrackingModes.contains(SessionTrackingMode.SSL)) {
            if (sessionTrackingModes.size() > 1) {
                throw new IllegalArgumentException(sm.getString(
                        "applicationContext.setSessionTracking.iae.ssl",
                        getContextPath()));
            }
        }

        this.sessionTrackingModes = sessionTrackingModes;
    }


    @Override
    public boolean setInitParameter(String name, String value) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(
                    sm.getString("applicationContext.setInitParam.ise",
                            getContextPath()));
        }

        return parameters.putIfAbsent(name, value) == null;
    }


    @Override
    public void addListener(Class<? extends EventListener> listenerClass) {
        EventListener listener;
        try {
            listener = createListener(listenerClass);
        } catch (ServletException e) {
            throw new IllegalArgumentException(sm.getString(
                    "applicationContext.addListener.iae.init",
                    listenerClass.getName()), e);
        }
        addListener(listener);
    }


    @Override
    public void addListener(String className) {

        try {
            if (context.getInstanceManager() != null) {
                Object obj = context.getInstanceManager().newInstance(className);

                if (!(obj instanceof EventListener)) {
                    throw new IllegalArgumentException(sm.getString(
                            "applicationContext.addListener.iae.wrongType",
                            className));
                }

                EventListener listener = (EventListener) obj;
                addListener(listener);
            }
        } catch (InvocationTargetException e) {
            ExceptionUtils.handleThrowable(e.getCause());
            throw new IllegalArgumentException(sm.getString(
                    "applicationContext.addListener.iae.cnfe", className),
                    e);
        } catch (IllegalAccessException | NamingException | InstantiationException |
                ClassNotFoundException | NoSuchMethodException e) {
            throw new IllegalArgumentException(sm.getString(
                    "applicationContext.addListener.iae.cnfe", className),
                    e);
        }

    }


    @Override
    public <T extends EventListener> void addListener(T t) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(
                    sm.getString("applicationContext.addListener.ise",
                            getContextPath()));
        }

        boolean match = false;
        if (t instanceof ServletContextAttributeListener ||
                t instanceof ServletRequestListener ||
                t instanceof ServletRequestAttributeListener ||
                t instanceof HttpSessionIdListener ||
                t instanceof HttpSessionAttributeListener) {
            context.addApplicationEventListener(t);
            match = true;
        }

        if (t instanceof HttpSessionListener
                || (t instanceof ServletContextListener &&
                        newServletContextListenerAllowed)) {
            // 将监听器直接添加到实例列表中，而不是添加到类名称列表中.
            context.addApplicationLifecycleListener(t);
            match = true;
        }

        if (match) return;

        if (t instanceof ServletContextListener) {
            throw new IllegalArgumentException(sm.getString(
                    "applicationContext.addListener.iae.sclNotAllowed",
                    t.getClass().getName()));
        } else {
            throw new IllegalArgumentException(sm.getString(
                    "applicationContext.addListener.iae.wrongType",
                    t.getClass().getName()));
        }
    }


    @Override
    public <T extends EventListener> T createListener(Class<T> c)
            throws ServletException {
        try {
            @SuppressWarnings("unchecked")
            T listener =
                (T) context.getInstanceManager().newInstance(c);
            if (listener instanceof ServletContextListener ||
                    listener instanceof ServletContextAttributeListener ||
                    listener instanceof ServletRequestListener ||
                    listener instanceof ServletRequestAttributeListener ||
                    listener instanceof HttpSessionListener ||
                    listener instanceof HttpSessionIdListener ||
                    listener instanceof HttpSessionAttributeListener) {
                return listener;
            }
            throw new IllegalArgumentException(sm.getString(
                    "applicationContext.addListener.iae.wrongType",
                    listener.getClass().getName()));
        } catch (InvocationTargetException e) {
            ExceptionUtils.handleThrowable(e.getCause());
            throw new ServletException(e);
        } catch (IllegalAccessException | NamingException | InstantiationException |
                NoSuchMethodException e) {
            throw new ServletException(e);
        }
    }


    @Override
    public void declareRoles(String... roleNames) {

        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            //TODO Spec breaking enhancement to ignore this restriction
            throw new IllegalStateException(
                    sm.getString("applicationContext.addRole.ise",
                            getContextPath()));
        }

        if (roleNames == null) {
            throw new IllegalArgumentException(
                    sm.getString("applicationContext.roles.iae",
                            getContextPath()));
        }

        for (String role : roleNames) {
            if (role == null || "".equals(role)) {
                throw new IllegalArgumentException(
                        sm.getString("applicationContext.role.iae",
                                getContextPath()));
            }
            context.addSecurityRole(role);
        }
    }


    @Override
    public ClassLoader getClassLoader() {
        ClassLoader result = context.getLoader().getClassLoader();
        if (Globals.IS_SECURITY_ENABLED) {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            ClassLoader parent = result;
            while (parent != null) {
                if (parent == tccl) {
                    break;
                }
                parent = parent.getParent();
            }
            if (parent == null) {
                System.getSecurityManager().checkPermission(
                        new RuntimePermission("getClassLoader"));
            }
        }

        return result;
    }


    @Override
    public int getEffectiveMajorVersion() {
        return context.getEffectiveMajorVersion();
    }


    @Override
    public int getEffectiveMinorVersion() {
        return context.getEffectiveMinorVersion();
    }


    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        Map<String, ApplicationFilterRegistration> result = new HashMap<>();

        FilterDef[] filterDefs = context.findFilterDefs();
        for (FilterDef filterDef : filterDefs) {
            result.put(filterDef.getFilterName(),
                    new ApplicationFilterRegistration(filterDef, context));
        }

        return result;
    }


    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return context.getJspConfigDescriptor();
    }


    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        Map<String, ApplicationServletRegistration> result = new HashMap<>();

        Container[] wrappers = context.findChildren();
        for (Container wrapper : wrappers) {
            result.put(((Wrapper) wrapper).getName(),
                    new ApplicationServletRegistration(
                            (Wrapper) wrapper, context));
        }

        return result;
    }


    @Override
    public String getVirtualServerName() {
        // Constructor will fail if context or its parent is null
        Container host = context.getParent();
        Container engine = host.getParent();
        return engine.getName() + "/" + host.getName();
    }


    @Override
    public int getSessionTimeout() {
        return context.getSessionTimeout();
    }


    @Override
    public void setSessionTimeout(int sessionTimeout) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(
                    sm.getString("applicationContext.setSessionTimeout.ise",
                            getContextPath()));
        }

        context.setSessionTimeout(sessionTimeout);
    }


    @Override
    public String getRequestCharacterEncoding() {
        return context.getRequestCharacterEncoding();
    }


    @Override
    public void setRequestCharacterEncoding(String encoding) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(
                    sm.getString("applicationContext.setRequestEncoding.ise",
                            getContextPath()));
        }

        context.setRequestCharacterEncoding(encoding);
    }


    @Override
    public String getResponseCharacterEncoding() {
        return context.getResponseCharacterEncoding();
    }


    @Override
    public void setResponseCharacterEncoding(String encoding) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(
                    sm.getString("applicationContext.setResponseEncoding.ise",
                            getContextPath()));
        }

        context.setResponseCharacterEncoding(encoding);
    }


    // -------------------------------------------------------- Package Methods
    protected StandardContext getContext() {
        return this.context;
    }

    /**
     * 清除所有应用程序创建的属性.
     */
    protected void clearAttributes() {

        // 创建要删除的属性列表
        ArrayList<String> list = new ArrayList<>();
        Iterator<String> iter = attributes.keySet().iterator();
        while (iter.hasNext()) {
            list.add(iter.next());
        }

        // 删除应用程序原始属性 (只读属性将保留在适当位置)
        Iterator<String> keys = list.iterator();
        while (keys.hasNext()) {
            String key = keys.next();
            removeAttribute(key);
        }

    }


    /**
     * @return 这个ApplicationContext关联的外观.
     */
    protected ServletContext getFacade() {
        return (this.facade);
    }


    /**
     * 将属性设置为只读.
     */
    void setAttributeReadOnly(String name) {

        if (attributes.containsKey(name))
            readOnlyAttributes.put(name, name);

    }


    protected void setNewServletContextListenerAllowed(boolean allowed) {
        this.newServletContextListenerAllowed = allowed;
    }

    /**
     * 在调度期间执行路径映射时，内部类用作线程本地存储.
     */
    private static final class DispatchData {

        public MessageBytes uriMB;
        public MappingData mappingData;

        public DispatchData() {
            uriMB = MessageBytes.newInstance();
            CharChunk uriCC = uriMB.getCharChunk();
            uriCC.setLimit(-1);
            mappingData = new MappingData();
        }
    }
}
