package org.apache.jasper.servlet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jasper.Constants;
import org.apache.jasper.EmbeddedServletOptions;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.runtime.ExceptionUtils;
import org.apache.jasper.security.SecurityUtil;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.PeriodicEventListener;
import org.apache.tomcat.util.security.Escape;

/**
 * JSP 引擎(a.k.a Jasper).
 *
 * servlet容器负责提供一个URLClassLoader, 对于 web 应用上下文Jasper使用的.
 * Jasper 为它的ServletContext类加载器尝试获取Tomcat ServletContext属性, 如果失败, 它使用父类加载器.
 * 其它情况下, 它必须是一个 URLClassLoader.
 */
public class JspServlet extends HttpServlet implements PeriodicEventListener {

    private static final long serialVersionUID = 1L;

    // Logger
    private final transient Log log = LogFactory.getLog(JspServlet.class);

    private transient ServletContext context;
    private ServletConfig config;
    private transient Options options;
    private transient JspRuntimeContext rctxt;
    // jsp的jspFile配置为一个 servlet, 在将此配置转换为该servlet的init参数的环境中.
    private String jspFile;


    /*
     * 初始化JspServlet.
     */
    @Override
    public void init(ServletConfig config) throws ServletException {

        super.init(config);
        this.config = config;
        this.context = config.getServletContext();

        // 初始化JSP 运行上下文
        // 验证自定义 Options 实现
        String engineOptionsName = config.getInitParameter("engineOptionsClass");
        if (Constants.IS_SECURITY_ENABLED && engineOptionsName != null) {
            log.info(Localizer.getMessage(
                    "jsp.info.ignoreSetting", "engineOptionsClass", engineOptionsName));
            engineOptionsName = null;
        }
        if (engineOptionsName != null) {
            // 初始化声明的 Options 实现
            try {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                Class<?> engineOptionsClass = loader.loadClass(engineOptionsName);
                Class<?>[] ctorSig = { ServletConfig.class, ServletContext.class };
                Constructor<?> ctor = engineOptionsClass.getConstructor(ctorSig);
                Object[] args = { config, context };
                options = (Options) ctor.newInstance(args);
            } catch (Throwable e) {
                e = ExceptionUtils.unwrapInvocationTargetException(e);
                ExceptionUtils.handleThrowable(e);
                // 需要本地化.
                log.warn("Failed to load engineOptionsClass", e);
                // 使用的默认的 Options 实现
                options = new EmbeddedServletOptions(config, context);
            }
        } else {
            // 使用的默认的 Options 实现
            options = new EmbeddedServletOptions(config, context);
        }
        rctxt = new JspRuntimeContext(context, options);
        if (config.getInitParameter("jspFile") != null) {
            jspFile = config.getInitParameter("jspFile");
            try {
                if (null == context.getResource(jspFile)) {
                    return;
                }
            } catch (MalformedURLException e) {
                throw new ServletException("cannot locate jsp file", e);
            }
            try {
                if (SecurityUtil.isPackageProtectionEnabled()){
                   AccessController.doPrivileged(new PrivilegedExceptionAction<Object>(){
                        @Override
                        public Object run() throws IOException, ServletException {
                            serviceJspFile(null, null, jspFile, true);
                            return null;
                        }
                    });
                } else {
                    serviceJspFile(null, null, jspFile, true);
                }
            } catch (IOException e) {
                throw new ServletException("Could not precompile jsp: " + jspFile, e);
            } catch (PrivilegedActionException e) {
                Throwable t = e.getCause();
                if (t instanceof ServletException) throw (ServletException)t;
                throw new ServletException("Could not precompile jsp: " + jspFile, e);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug(Localizer.getMessage("jsp.message.scratch.dir.is",
                    options.getScratchDir().toString()));
            log.debug(Localizer.getMessage("jsp.message.dont.modify.servlets"));
        }
    }


    /**
     * 返回存在JspServletWrapper的JSP的数量, 即, JSP加载进这个JspServlet关联的Web应用的数量.
     *
     * <p>此信息可用于监视目的.
     *
     * @return JSP加载进这个JspServlet关联的Web应用的数量.
     */
    public int getJspCount() {
        return this.rctxt.getJspCount();
    }


    /**
     * 重置 JSP 重新加载计数.
     *
     * @param count 重置JSP重新加载计数器的值
     */
    public void setJspReloadCount(int count) {
        this.rctxt.setJspReloadCount(count);
    }


    /**
     * 获取已加载JSP的数量.
     *
     * <p>此信息可用于监视目的.
     *
     * @return 已加载JSP的数量.
     */
    public int getJspReloadCount() {
        return this.rctxt.getJspReloadCount();
    }


    /**
     * 获取JSP限制队列中的JSP数量
     *
     * <p>此信息可用于监视目的.
     *
     * @return JSP的数量.
     */
    public int getJspQueueLength() {
        return this.rctxt.getJspQueueLength();
    }


    /**
     * 获取已卸载的JSP的数量.
     *
     * <p>此信息可用于监视目的.
     *
     * @return JSP的数量.
     */
    public int getJspUnloadCount() {
        return this.rctxt.getJspUnloadCount();
    }


    /**
     * <p>找一个在JSP 1.2规范的Section 8.4.2描述的<em>precompilation request</em>.
     * <strong>WARNING</strong> - 不能使用<code>request.getParameter()</code>, 因为这将触发解析所有请求参数,
     * 而且不给servlet一个首先调用<code>request.setCharacterEncoding()</code>的机会.</p>
     *
     * @param request 处理的servlet请求
     *
     * @exception ServletException 如果指定名称的<code>jsp_precompile</code>参数值无效
     */
    boolean preCompile(HttpServletRequest request) throws ServletException {

        String queryString = request.getQueryString();
        if (queryString == null) {
            return false;
        }
        int start = queryString.indexOf(Constants.PRECOMPILE);
        if (start < 0) {
            return false;
        }
        queryString =
            queryString.substring(start + Constants.PRECOMPILE.length());
        if (queryString.length() == 0) {
            return true;             // ?jsp_precompile
        }
        if (queryString.startsWith("&")) {
            return true;             // ?jsp_precompile&foo=bar...
        }
        if (!queryString.startsWith("=")) {
            return false;            // part of some other name or value
        }
        int limit = queryString.length();
        int ampersand = queryString.indexOf('&');
        if (ampersand > 0) {
            limit = ampersand;
        }
        String value = queryString.substring(1, limit);
        if (value.equals("true")) {
            return true;             // ?jsp_precompile=true
        } else if (value.equals("false")) {
            // 规范中说明如果jsp_precompile=false, 请求不应传递到JSP页面; 实现这一点最简单的方法是将标志设置为 true, 预编译页面.
		    // 这仍然符合规范, 因为预编译的要求可以忽略.
            return true;             // ?jsp_precompile=false
        } else {
            throw new ServletException("Cannot have request parameter " +
                                       Constants.PRECOMPILE + " set to " +
                                       value);
        }

    }


    @Override
    public void service (HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // jspFile需要作为一个init-param配置
        String jspUri = jspFile;

        if (jspUri == null) {
            /*
             * 检查所请求的JSP是否已成为RequestDispatcher.include()的目标
             */
            jspUri = (String) request.getAttribute(
                    RequestDispatcher.INCLUDE_SERVLET_PATH);
            if (jspUri != null) {
                /*
                 * 请求的JSP 是RequestDispatcher.include()的目标.
                 * 它的路径是从相关的 javax.servlet.include.* 请求属性中收集的
                 */
                String pathInfo = (String) request.getAttribute(
                        RequestDispatcher.INCLUDE_PATH_INFO);
                if (pathInfo != null) {
                    jspUri += pathInfo;
                }
            } else {
                /*
                 * 请求的JSP 不是RequestDispatcher.include()的目标.
                 * 从请求的 getServletPath() 和 getPathInfo()重建路径
                 */
                jspUri = request.getServletPath();
                String pathInfo = request.getPathInfo();
                if (pathInfo != null) {
                    jspUri += pathInfo;
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("JspEngine --> " + jspUri);
            log.debug("\t     ServletPath: " + request.getServletPath());
            log.debug("\t        PathInfo: " + request.getPathInfo());
            log.debug("\t        RealPath: " + context.getRealPath(jspUri));
            log.debug("\t      RequestURI: " + request.getRequestURI());
            log.debug("\t     QueryString: " + request.getQueryString());
        }

        try {
            boolean precompile = preCompile(request);
            serviceJspFile(request, response, jspUri, precompile);
        } catch (RuntimeException e) {
            throw e;
        } catch (ServletException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            throw new ServletException(e);
        }

    }

    @Override
    public void destroy() {
        if (log.isDebugEnabled()) {
            log.debug("JspServlet.destroy()");
        }

        rctxt.destroy();
    }


    @Override
    public void periodicEvent() {
        rctxt.checkUnload();
        rctxt.checkCompile();
    }

    // -------------------------------------------------------- Private Methods

    private void serviceJspFile(HttpServletRequest request,
                                HttpServletResponse response, String jspUri,
                                boolean precompile)
        throws ServletException, IOException {

        JspServletWrapper wrapper = rctxt.getWrapper(jspUri);
        if (wrapper == null) {
            synchronized(this) {
                wrapper = rctxt.getWrapper(jspUri);
                if (wrapper == null) {
                    // 检查请求的JSP 页面是否存在, 避免创建不必要的目录和文件.
                    if (null == context.getResource(jspUri)) {
                        handleMissingResource(request, response, jspUri);
                        return;
                    }
                    wrapper = new JspServletWrapper(config, options, jspUri,
                                                    rctxt);
                    rctxt.addWrapper(jspUri,wrapper);
                }
            }
        }

        try {
            wrapper.service(request, response, precompile);
        } catch (FileNotFoundException fnfe) {
            handleMissingResource(request, response, jspUri);
        }

    }


    private void handleMissingResource(HttpServletRequest request,
            HttpServletResponse response, String jspUri)
            throws ServletException, IOException {

        String includeRequestUri =
            (String)request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI);

        if (includeRequestUri != null) {
            // 这个文件被包括. Throw an exception as a response.sendError() will be ignored
            String msg =
                Localizer.getMessage("jsp.error.file.not.found",jspUri);
            // 严格地, 过滤这个是一个应用程序的责任，但以防万一...
            throw new ServletException(Escape.htmlElementContent(msg));
        } else {
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        request.getRequestURI());
            } catch (IllegalStateException ise) {
                log.error(Localizer.getMessage("jsp.error.file.not.found",
                        jspUri));
            }
        }
        return;
    }
}
