package org.apache.jasper.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.runtime.ExceptionUtils;
import org.apache.tomcat.JarScanType;
import org.apache.tomcat.util.descriptor.web.FragmentJarScannerCallback;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.tomcat.util.descriptor.web.WebXmlParser;
import org.apache.tomcat.util.scan.StandardJarScanFilter;
import org.apache.tomcat.util.scan.StandardJarScanner;


/**
 * 简单的<code>ServletContext</code>实现不包括HTTP指定的方法.
 */
public class JspCServletContext implements ServletContext {


    // ----------------------------------------------------- Instance Variables


    /**
     * Servlet 上下文属性.
     */
    private final Map<String,Object> myAttributes;


    /**
     * Servlet 上下文初始化参数.
     */
    private final ConcurrentMap<String,String> myParameters = new ConcurrentHashMap<>();


    /**
     * 要写入日志信息的writer.
     */
    private final PrintWriter myLogWriter;


    /**
     * 这个上下文的基础 URL.
     */
    private final URL myResourceBaseURL;


    /**
     * 合并的 web.xml.
     */
    private WebXml webXml;


    private JspConfigDescriptor jspConfigDescriptor;

    /**
     * Web application class loader.
     */
    private final ClassLoader loader;


    // ----------------------------------------------------------- Constructors

    /**
     * @param aLogWriter <code>log()</code>调用的PrintWriter
     * @param aResourceBaseURL 资源基础 URL
     * @param classLoader   这个{@link ServletContext}的Class loader
     * @param validate      解析web.xml时是否验证?
     * @param blockExternal 解析web.xml时，是否阻塞其他实体?
     * 
     * @throws JasperException An error occurred building the merged web.xml
     */
    public JspCServletContext(PrintWriter aLogWriter, URL aResourceBaseURL,
            ClassLoader classLoader, boolean validate, boolean blockExternal)
            throws JasperException {

        myAttributes = new HashMap<>();
        myParameters.put(Constants.XML_BLOCK_EXTERNAL_INIT_PARAM,
                String.valueOf(blockExternal));
        myLogWriter = aLogWriter;
        myResourceBaseURL = aResourceBaseURL;
        this.loader = classLoader;
        this.webXml = buildMergedWebXml(validate, blockExternal);
        jspConfigDescriptor = webXml.getJspConfigDescriptor();
    }

    private WebXml buildMergedWebXml(boolean validate, boolean blockExternal)
            throws JasperException {
        WebXml webXml = new WebXml();
        WebXmlParser webXmlParser = new WebXmlParser(validate, validate, blockExternal);
        // Use this class's classloader as Ant will have set the TCCL to its own
        webXmlParser.setClassLoader(getClass().getClassLoader());

        try {
            URL url = getResource(
                    org.apache.tomcat.util.descriptor.web.Constants.WEB_XML_LOCATION);
            if (!webXmlParser.parseWebXml(url, webXml, false)) {
                throw new JasperException(Localizer.getMessage("jspc.error.invalidWebXml"));
            }
        } catch (IOException e) {
            throw new JasperException(e);
        }

        // if the application is metadata-complete then we can skip fragment processing
        if (webXml.isMetadataComplete()) {
            return webXml;
        }

        // If an empty absolute ordering element is present, fragment processing
        // may be skipped.
        Set<String> absoluteOrdering = webXml.getAbsoluteOrdering();
        if (absoluteOrdering != null && absoluteOrdering.isEmpty()) {
            return webXml;
        }

        Map<String, WebXml> fragments = scanForFragments(webXmlParser);
        Set<WebXml> orderedFragments = WebXml.orderWebFragments(webXml, fragments, this);

        // JspC不受注解影响，因此跳过该处理, 合并
        webXml.merge(orderedFragments);
        return webXml;
    }

    private Map<String, WebXml> scanForFragments(WebXmlParser webXmlParser) throws JasperException {
        StandardJarScanner scanner = new StandardJarScanner();
        // TODO - enabling this means initializing the classloader first in JspC
        scanner.setScanClassPath(false);
        // TODO - configure filter rules from Ant rather then system properties
        scanner.setJarScanFilter(new StandardJarScanFilter());

        FragmentJarScannerCallback callback =
                new FragmentJarScannerCallback(webXmlParser, false, true);
        scanner.scan(JarScanType.PLUGGABILITY, this, callback);
        if (!callback.isOk()) {
            throw new JasperException(Localizer.getMessage("jspc.error.invalidFragment"));
        }
        return callback.getFragments();
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 返回指定的上下文属性.
     *
     * @param name Name of the requested attribute
     */
    @Override
    public Object getAttribute(String name) {
        return myAttributes.get(name);
    }


    /**
     * 返回上下文属性名称的枚举.
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(myAttributes.keySet());
    }


    /**
     * 返回指定路径的 servlet上下文.
     *
     * @param uripath Server相对路径, 以'/'开头
     */
    @Override
    public ServletContext getContext(String uripath) {
        return null;
    }


    /**
     * 返回上下文路径.
     */
    @Override
    public String getContextPath() {
        return null;
    }


    /**
     * 返回指定的上下文初始化参数.
     *
     * @param name Name of the requested parameter
     */
    @Override
    public String getInitParameter(String name) {
        return myParameters.get(name);
    }


    /**
     * 返回上下文初始化参数名称的枚举.
     */
    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(myParameters.keySet());
    }


    /**
     * 返回 Servlet API 主版本号.
     */
    @Override
    public int getMajorVersion() {
        return 3;
    }


    /**
     * 返回指定文件名的 MIME 类型.
     *
     * @param file 请求的MIME类型的文件名
     */
    @Override
    public String getMimeType(String file) {
        return null;
    }


    /**
     * 返回Servlet API 次要版本号.
     */
    @Override
    public int getMinorVersion() {
        return 1;
    }


    /**
     * 返回指定的servlet名称的请求分派器.
     *
     * @param name 请求的servlet的名称
     */
    @Override
    public RequestDispatcher getNamedDispatcher(String name) {
        return null;
    }


    /**
     * 返回指定上下文相对虚拟路径的实际路径.
     *
     * @param path 要解析的上下文相关虚拟路径
     */
    @Override
    public String getRealPath(String path) {
        if (!myResourceBaseURL.getProtocol().equals("file"))
            return null;
        if (!path.startsWith("/"))
            return null;
        try {
            File f = new File(getResource(path).toURI());
            return f.getAbsolutePath();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            return null;
        }
    }


    /**
     * 返回指定上下文相对路径的请求分配器.
     *
     * @param path 获取调度程序的上下文相对路径
     */
    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return null;
    }


    /**
     * 返回一个资源的 URL 对象, 映射到指定的上下文相对路径.
     *
     * @param path 上下文所需资源的相对路径
     *
     * @exception MalformedURLException 如果资源路径没有正确形成
     */
    @Override
    public URL getResource(String path) throws MalformedURLException {

        if (!path.startsWith("/"))
            throw new MalformedURLException("Path '" + path +
                                            "' does not start with '/'");
        URL url = new URL(myResourceBaseURL, path.substring(1));
        try (InputStream is = url.openStream()) {
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            url = null;
        }
        return url;
    }


    /**
     * 返回一个 InputStream 允许在指定上下文相对路径中访问资源.
     *
     * @param path 上下文所需资源的相对路径
     */
    @Override
    public InputStream getResourceAsStream(String path) {

        try {
            return (getResource(path).openStream());
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            return (null);
        }
    }


    /**
     * 返回指定上下文路径的"directory"的资源路径集.
     *
     * @param path 上下文相关基础路径
     */
    @Override
    public Set<String> getResourcePaths(String path) {

        Set<String> thePaths = new HashSet<>();
        if (!path.endsWith("/"))
            path += "/";
        String basePath = getRealPath(path);
        if (basePath == null)
            return (thePaths);
        File theBaseDir = new File(basePath);
        if (!theBaseDir.exists() || !theBaseDir.isDirectory())
            return (thePaths);
        String theFiles[] = theBaseDir.list();
        if (theFiles == null) {
            return thePaths;
        }
        for (int i = 0; i < theFiles.length; i++) {
            File testFile = new File(basePath + File.separator + theFiles[i]);
            if (testFile.isFile())
                thePaths.add(path + theFiles[i]);
            else if (testFile.isDirectory())
                thePaths.add(path + theFiles[i] + "/");
        }
        return (thePaths);

    }


    /**
     * 返回此服务器的描述性信息.
     */
    @Override
    public String getServerInfo() {
        return ("JspC/ApacheTomcat8");
    }

    /**
     * 返回此servlet上下文的名称.
     */
    @Override
    public String getServletContextName() {
        return (getServerInfo());
    }


    /**
     * 记录指定的消息.
     *
     * @param message The message to be logged
     */
    @Override
    public void log(String message) {
        myLogWriter.println(message);
    }


    /**
     * 记录指定的消息和异常.
     *
     * @param exception 要记录的异常
     * @param message 要记录的消息
     */
    @Override
    public void log(String message, Throwable exception) {
        myLogWriter.println(message);
        exception.printStackTrace(myLogWriter);
    }


    /**
     * 删除指定的上下文属性.
     *
     * @param name 要删除的属性的名称
     */
    @Override
    public void removeAttribute(String name) {
        myAttributes.remove(name);
    }


    /**
     * 设置或替换指定的上下文属性.
     *
     * @param name 要设置的上下文属性的名称
     * @param value 相应的属性值
     */
    @Override
    public void setAttribute(String name, Object value) {
        myAttributes.put(name, value);
    }


    @Override
    public FilterRegistration.Dynamic addFilter(String filterName,
            String className) {
        return null;
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName,
            String className) {
        return null;
    }


    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return EnumSet.noneOf(SessionTrackingMode.class);
    }


    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        return EnumSet.noneOf(SessionTrackingMode.class);
    }


    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return null;
    }


    @Override
    public void setSessionTrackingModes(
            Set<SessionTrackingMode> sessionTrackingModes) {
        // Do nothing
    }


    @Override
    public Dynamic addFilter(String filterName, Filter filter) {
        return null;
    }


    @Override
    public Dynamic addFilter(String filterName,
            Class<? extends Filter> filterClass) {
        return null;
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName,
            Servlet servlet) {
        return null;
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName,
            Class<? extends Servlet> servletClass) {
        return null;
    }


    @Override
    public <T extends Filter> T createFilter(Class<T> c)
            throws ServletException {
        return null;
    }


    @Override
    public <T extends Servlet> T createServlet(Class<T> c)
            throws ServletException {
        return null;
    }


    @Override
    public FilterRegistration getFilterRegistration(String filterName) {
        return null;
    }


    @Override
    public ServletRegistration getServletRegistration(String servletName) {
        return null;
    }


    @Override
    public boolean setInitParameter(String name, String value) {
        return myParameters.putIfAbsent(name, value) == null;
    }


    @Override
    public void addListener(Class<? extends EventListener> listenerClass) {
        // NOOP
    }


    @Override
    public void addListener(String className) {
        // NOOP
    }


    @Override
    public <T extends EventListener> void addListener(T t) {
        // NOOP
    }


    @Override
    public <T extends EventListener> T createListener(Class<T> c)
            throws ServletException {
        return null;
    }


    @Override
    public void declareRoles(String... roleNames) {
        // NOOP
    }


    @Override
    public ClassLoader getClassLoader() {
        return loader;
    }


    @Override
    public int getEffectiveMajorVersion() {
        return webXml.getMajorVersion();
    }


    @Override
    public int getEffectiveMinorVersion() {
        return webXml.getMinorVersion();
    }


    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        return null;
    }


    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return jspConfigDescriptor;
    }


    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        return null;
    }


    @Override
    public String getVirtualServerName() {
        return null;
    }
}
