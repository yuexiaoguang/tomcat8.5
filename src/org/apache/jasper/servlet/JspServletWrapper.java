package org.apache.jasper.servlet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.tagext.TagInfo;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.ErrorDispatcher;
import org.apache.jasper.compiler.JavacErrorDetail;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.runtime.ExceptionUtils;
import org.apache.jasper.runtime.InstanceManagerFactory;
import org.apache.jasper.runtime.JspSourceDependent;
import org.apache.jasper.util.FastRemovalDequeue;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.Jar;

/**
 * JSP 引擎(a.k.a Jasper).
 *
 * servlet 容器负责提供一个URLClassLoader , 用于Web应用程序上下文Jasper. Jasper 将为它的ServletContext类加载器获取Tomcat ServletContext 属性,
 * 如果失败, 使用父类加载器.
 * 在其他情况下, 它必须是一个 URLClassLoader.
 */
@SuppressWarnings("deprecation") // Have to support SingleThreadModel
public class JspServletWrapper {

    private static final Map<String,Long> ALWAYS_OUTDATED_DEPENDENCIES =
            new HashMap<>();

    static {
        // If this is missing,
        ALWAYS_OUTDATED_DEPENDENCIES.put("/WEB-INF/web.xml", Long.valueOf(-1));
    }

    // Logger
    private final Log log = LogFactory.getLog(JspServletWrapper.class);

    private Servlet theServlet;
    private final String jspUri;
    private Class<?> tagHandlerClass;
    private final JspCompilationContext ctxt;
    private long available = 0L;
    private final ServletConfig config;
    private final Options options;
    private boolean firstTime = true;
    /** 下次访问时，servlet是否需要重新加载 */
    private volatile boolean reload = true;
    private final boolean isTagFile;
    private int tripCount;
    private JasperException compileException;
    /** servlet资源被编辑的最后时间的时间戳 */
    private volatile long servletClassLastModifiedTime;
    private long lastModificationTest = 0L;
    private long lastUsageTime = System.currentTimeMillis();
    private FastRemovalDequeue<JspServletWrapper>.Entry unloadHandle;
    private final boolean unloadAllowed;
    private final boolean unloadByCount;
    private final boolean unloadByIdle;

    /*
     * JspServletWrapper for JSP pages.
     */
    public JspServletWrapper(ServletConfig config, Options options,
            String jspUri, JspRuntimeContext rctxt) {

        this.isTagFile = false;
        this.config = config;
        this.options = options;
        this.jspUri = jspUri;
        unloadByCount = options.getMaxLoadedJsps() > 0 ? true : false;
        unloadByIdle = options.getJspIdleTimeout() > 0 ? true : false;
        unloadAllowed = unloadByCount || unloadByIdle ? true : false;
        ctxt = new JspCompilationContext(jspUri, options,
                                         config.getServletContext(),
                                         this, rctxt);
    }

    /*
     * JspServletWrapper for tag files.
     */
    public JspServletWrapper(ServletContext servletContext,
                             Options options,
                             String tagFilePath,
                             TagInfo tagInfo,
                             JspRuntimeContext rctxt,
                             Jar tagJar) {

        this.isTagFile = true;
        this.config = null;        // not used
        this.options = options;
        this.jspUri = tagFilePath;
        this.tripCount = 0;
        unloadByCount = options.getMaxLoadedJsps() > 0 ? true : false;
        unloadByIdle = options.getJspIdleTimeout() > 0 ? true : false;
        unloadAllowed = unloadByCount || unloadByIdle ? true : false;
        ctxt = new JspCompilationContext(jspUri, tagInfo, options,
                                         servletContext, this, rctxt,
                                         tagJar);
    }

    public JspCompilationContext getJspEngineContext() {
        return ctxt;
    }

    public void setReload(boolean reload) {
        this.reload = reload;
    }

    public Servlet getServlet() throws ServletException {
        // DCL on 'reload' requires that 'reload' be volatile
        // (这也强制了读取内存的界限, 确保新的servlet总是被读取)
        if (reload) {
            synchronized (this) {
                // 在jsw上同步，启用 simultaneous 加载不同的页面, 但不是同一个页面.
                if (reload) {
                    // 这是为了维持原来的协议.
                    destroy();

                    final Servlet servlet;

                    try {
                        InstanceManager instanceManager = InstanceManagerFactory.getInstanceManager(config);
                        servlet = (Servlet) instanceManager.newInstance(ctxt.getFQCN(), ctxt.getJspLoader());
                    } catch (Exception e) {
                        Throwable t = ExceptionUtils
                                .unwrapInvocationTargetException(e);
                        ExceptionUtils.handleThrowable(t);
                        throw new JasperException(t);
                    }

                    servlet.init(config);

                    if (!firstTime) {
                        ctxt.getRuntimeContext().incrementJspReloadCount();
                    }

                    theServlet = servlet;
                    reload = false;
                    // Volatile 'reload' forces in order write of 'theServlet' and new servlet object
                }
            }
        }
        return theServlet;
    }

    public ServletContext getServletContext() {
        return ctxt.getServletContext();
    }

    /**
     * 设置编译异常.
     *
     * @param je The compilation exception
     */
    public void setCompilationException(JasperException je) {
        this.compileException = je;
    }

    /**
     * 设置servlet类文件的最后修改时间.
     *
     * @param lastModified servlet类文件的最后修改时间
     */
    public void setServletClassLastModifiedTime(long lastModified) {
        // DCL 要求 servletClassLastModifiedTime 是 volatile,
        // 强制读取和写入边界(并获取long的原子写入)
        if (this.servletClassLastModifiedTime < lastModified) {
            synchronized (this) {
                if (this.servletClassLastModifiedTime < lastModified) {
                    this.servletClassLastModifiedTime = lastModified;
                    reload = true;
                    // 真的需要卸载老的类，但不能这样做. 最好扔掉JspLoader, 因此将创建新的加载新类的加载器.
                    // TODO Are there inefficiencies between reload and the isOutDated() check?
                    ctxt.clearJspLoader();
                }
            }
        }
    }

    /**
     * 编译并加载标签文件.
     * 
     * @return 加载的类
     * @throws JasperException 编译或加载标记文件错误
     */
    public Class<?> loadTagFile() throws JasperException {

        try {
            if (ctxt.isRemoved()) {
                throw new FileNotFoundException(jspUri);
            }
            if (options.getDevelopment() || firstTime ) {
                synchronized (this) {
                    firstTime = false;
                    ctxt.compile();
                }
            } else {
                if (compileException != null) {
                    throw compileException;
                }
            }

            if (reload) {
                tagHandlerClass = ctxt.load();
                reload = false;
            }
        } catch (FileNotFoundException ex) {
            throw new JasperException(ex);
        }

        return tagHandlerClass;
    }

    /**
     * 编译并加载标签文件的原型. 当编译带有循环依赖关系的标签文件时，这是必需的. 一个原型（骨架）与其他标签文件的其他无依赖性生成和编译.
     * 
     * @return 加载的类
     * @throws JasperException 编译或加载标记文件错误
     */
    public Class<?> loadTagFilePrototype() throws JasperException {

        ctxt.setPrototypeMode(true);
        try {
            return loadTagFile();
        } finally {
            ctxt.setPrototypeMode(false);
        }
    }

    /**
     * 获取当前页面具有源依赖项的文件列表.
     * 
     * @return the map of dependent resources
     */
    public java.util.Map<String,Long> getDependants() {
        try {
            Object target;
            if (isTagFile) {
                if (reload) {
                    tagHandlerClass = ctxt.load();
                    reload = false;
                }
                target = tagHandlerClass.getConstructor().newInstance();
            } else {
                target = getServlet();
            }
            if (target instanceof JspSourceDependent) {
                return ((JspSourceDependent) target).getDependants();
            }
        } catch (AbstractMethodError ame) {
            // 几乎可以肯定的是，使用旧版本的接口的Tomcat 7.0.17编译的JSP. 强制重新编译.
            return ALWAYS_OUTDATED_DEPENDENCIES;
        } catch (Throwable ex) {
            ExceptionUtils.handleThrowable(ex);
        }
        return null;
    }

    public boolean isTagFile() {
        return this.isTagFile;
    }

    public int incTripCount() {
        return tripCount++;
    }

    public int decTripCount() {
        return tripCount--;
    }

    public String getJspUri() {
        return jspUri;
    }

    public FastRemovalDequeue<JspServletWrapper>.Entry getUnloadHandle() {
        return unloadHandle;
    }

    public void service(HttpServletRequest request,
                        HttpServletResponse response,
                        boolean precompile)
            throws ServletException, IOException, FileNotFoundException {

        Servlet servlet;

        try {

            if (ctxt.isRemoved()) {
                throw new FileNotFoundException(jspUri);
            }

            if ((available > 0L) && (available < Long.MAX_VALUE)) {
                if (available > System.currentTimeMillis()) {
                    response.setDateHeader("Retry-After", available);
                    response.sendError
                        (HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                         Localizer.getMessage("jsp.error.unavailable"));
                    return;
                }

                // Wait period has expired. Reset.
                available = 0;
            }

            /*
             * (1) 编译
             */
            if (options.getDevelopment() || firstTime ) {
                synchronized (this) {
                    firstTime = false;

                    // 以下设置reload为 true
                    ctxt.compile();
                }
            } else {
                if (compileException != null) {
                    // Throw cached compilation exception
                    throw compileException;
                }
            }

            /*
             * (2) (Re)加载servlet类文件
             */
            servlet = getServlet();

            // 如果一个页面只被预编译, 返回.
            if (precompile) {
                return;
            }

        } catch (ServletException ex) {
            if (options.getDevelopment()) {
                throw handleJspException(ex);
            }
            throw ex;
        } catch (FileNotFoundException fnfe) {
            // 文件已经被删除. 让调用者处理这个.
            throw fnfe;
        } catch (IOException ex) {
            if (options.getDevelopment()) {
                throw handleJspException(ex);
            }
            throw ex;
        } catch (IllegalStateException ex) {
            if (options.getDevelopment()) {
                throw handleJspException(ex);
            }
            throw ex;
        } catch (Exception ex) {
            if (options.getDevelopment()) {
                throw handleJspException(ex);
            }
            throw new JasperException(ex);
        }

        try {
            /*
             * (3) 处理加载的Jsp的数量限制
             */
            if (unloadAllowed) {
                synchronized(this) {
                    if (unloadByCount) {
                        if (unloadHandle == null) {
                            unloadHandle = ctxt.getRuntimeContext().push(this);
                        } else if (lastUsageTime < ctxt.getRuntimeContext().getLastJspQueueUpdate()) {
                            ctxt.getRuntimeContext().makeYoungest(unloadHandle);
                            lastUsageTime = System.currentTimeMillis();
                        }
                    } else {
                        if (lastUsageTime < ctxt.getRuntimeContext().getLastJspQueueUpdate()) {
                            lastUsageTime = System.currentTimeMillis();
                        }
                    }
                }
            }

            /*
             * (4) Service request
             */
            servlet.service(request, response);
        } catch (UnavailableException ex) {
            String includeRequestUri = (String)
                request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI);
            if (includeRequestUri != null) {
                // 这个文件被included. Throw an exception as
                // a response.sendError() will be ignored by the
                // servlet engine.
                throw ex;
            }

            int unavailableSeconds = ex.getUnavailableSeconds();
            if (unavailableSeconds <= 0) {
                unavailableSeconds = 60;        // Arbitrary default
            }
            available = System.currentTimeMillis() +
                (unavailableSeconds * 1000L);
            response.sendError
                (HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                 ex.getMessage());
        } catch (ServletException ex) {
            if(options.getDevelopment()) {
                throw handleJspException(ex);
            }
            throw ex;
        } catch (IOException ex) {
            if (options.getDevelopment()) {
                throw new IOException(handleJspException(ex).getMessage(), ex);
            }
            throw ex;
        } catch (IllegalStateException ex) {
            if(options.getDevelopment()) {
                throw handleJspException(ex);
            }
            throw ex;
        } catch (Exception ex) {
            if(options.getDevelopment()) {
                throw handleJspException(ex);
            }
            throw new JasperException(ex);
        }
    }

    public void destroy() {
        if (theServlet != null) {
            try {
                theServlet.destroy();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(Localizer.getMessage("jsp.error.servlet.destroy.failed"), t);
            }
            InstanceManager instanceManager = InstanceManagerFactory.getInstanceManager(config);
            try {
                instanceManager.destroyInstance(theServlet);
            } catch (Exception e) {
                Throwable t = ExceptionUtils.unwrapInvocationTargetException(e);
                ExceptionUtils.handleThrowable(t);
                // 记录任何异常, 因为它不能被传递
                log.error(Localizer.getMessage("jsp.error.file.not.found",
                        e.getMessage()), t);
            }
        }
    }

    public long getLastModificationTest() {
        return lastModificationTest;
    }
    
    public void setLastModificationTest(long lastModificationTest) {
        this.lastModificationTest = lastModificationTest;
    }

    public long getLastUsageTime() {
        return lastUsageTime;
    }

    /**
     * <p>试图构建一个包含有用的错误信息的 JasperException.
     * 使用 JSP 编辑器系统翻译来自异常的servlet的行号为JSP中的行号. 然后构造包含该信息的异常, 以及帮助调试的JSP代码段.
     *</p>
     *
     * @param ex the exception that was the cause of the problem.
     * @return a JasperException with more detailed information
     */
    protected JasperException handleJspException(Exception ex) {
        try {
            Throwable realException = ex;
            if (ex instanceof ServletException) {
                realException = ((ServletException) ex).getRootCause();
            }

            // 首先标识表示JSP的跟踪中的堆栈帧
            StackTraceElement[] frames = realException.getStackTrace();
            StackTraceElement jspFrame = null;

            for (int i=0; i<frames.length; ++i) {
                if ( frames[i].getClassName().equals(this.getServlet().getClass().getName()) ) {
                    jspFrame = frames[i];
                    break;
                }
            }


            if (jspFrame == null ||
                    this.ctxt.getCompiler().getPageNodes() == null) {
                // 如果找不到对应于生成的servlet类的堆栈跟踪中的一个帧，或者没有解析的JSP的副本, 什么都不能做
                return new JasperException(ex);
            }

            int javaLineNumber = jspFrame.getLineNumber();
            JavacErrorDetail detail = ErrorDispatcher.createJavacError(
                    jspFrame.getMethodName(),
                    this.ctxt.getCompiler().getPageNodes(),
                    null,
                    javaLineNumber,
                    ctxt);

            // 如果行号小于1，我们就无法找出JSP的错误所在
            int jspLineNumber = detail.getJspBeginLineNumber();
            if (jspLineNumber < 1) {
                throw new JasperException(ex);
            }

            if (options.getDisplaySourceFragment()) {
                return new JasperException(Localizer.getMessage
                        ("jsp.exception", detail.getJspFileName(),
                                "" + jspLineNumber) + System.lineSeparator() +
                                System.lineSeparator() + detail.getJspExtract() +
                                System.lineSeparator() + System.lineSeparator() +
                                "Stacktrace:", ex);

            }

            return new JasperException(Localizer.getMessage
                    ("jsp.exception", detail.getJspFileName(),
                            "" + jspLineNumber), ex);
        } catch (Exception je) {
            // 如果有错误, 恢复原来的行为
            if (ex instanceof JasperException) {
                return (JasperException) ex;
            }
            return new JasperException(ex);
        }
    }
}
