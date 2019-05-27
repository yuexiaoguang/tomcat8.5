package org.apache.jasper.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilePermission;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.cert.Certificate;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletContext;

import org.apache.jasper.Constants;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.apache.jasper.runtime.ExceptionUtils;
import org.apache.jasper.servlet.JspServletWrapper;
import org.apache.jasper.util.FastRemovalDequeue;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * 跟踪 JSP 编译时间文件依赖项, 当&060;%@include file="..."%&062的时候; 使用指令.
 *
 * 后台线程定期检查JSP页面所依赖的文件. 如果一个依赖文件修改包含它的JSP页面会被重新编译.
 *
 * 仅当Web应用程序上下文是目录时才使用.
 */
public final class JspRuntimeContext {

    /**
     * Logger
     */
    private final Log log = LogFactory.getLog(JspRuntimeContext.class);

    /**
     * web应用的JSP重新加载的次数.
     */
    private final AtomicInteger jspReloadCount = new AtomicInteger(0);

    /**
     * web应用的JSP重新加载的次数.
     */
    private final AtomicInteger jspUnloadCount = new AtomicInteger(0);

    // ----------------------------------------------------------- Constructors

    /**
     * 从文件中生成的任何依赖项中的加载.
     *
     * @param context Web应用的ServletContext
     * @param options 主要的Jasper选项
     */
    public JspRuntimeContext(ServletContext context, Options options) {

        this.context = context;
        this.options = options;

        // 获取父级类加载器
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = this.getClass().getClassLoader();
        }

        if (log.isDebugEnabled()) {
            if (loader != null) {
                log.debug(Localizer.getMessage("jsp.message.parent_class_loader_is",
                                               loader.toString()));
            } else {
                log.debug(Localizer.getMessage("jsp.message.parent_class_loader_is",
                                               "<none>"));
            }
        }

        parentClassLoader =  loader;
        classpath = initClassPath();

        if (context instanceof org.apache.jasper.servlet.JspCServletContext) {
            codeSource = null;
            permissionCollection = null;
            return;
        }

        if (Constants.IS_SECURITY_ENABLED) {
            SecurityHolder holder = initSecurity();
            codeSource = holder.cs;
            permissionCollection = holder.pc;
        } else {
            codeSource = null;
            permissionCollection = null;
        }

        // 如果这个Web应用程序上下文从目录中运行, 启动后台编译线程
        String appBase = context.getRealPath("/");
        if (!options.getDevelopment()
                && appBase != null
                && options.getCheckInterval() > 0) {
            lastCompileCheck = System.currentTimeMillis();
        }

        if (options.getMaxLoadedJsps() > 0) {
            jspQueue = new FastRemovalDequeue<>(options.getMaxLoadedJsps());
            if (log.isDebugEnabled()) {
                log.debug(Localizer.getMessage("jsp.message.jsp_queue_created",
                                               "" + options.getMaxLoadedJsps(), context.getContextPath()));
            }
        }

        /* Init parameter is in seconds, locally we use milliseconds */
        jspIdleTimeout = options.getJspIdleTimeout() * 1000;
    }

    // ----------------------------------------------------- Instance Variables

    /**
     * 这个Web应用程序的 ServletContext
     */
    private final ServletContext context;
    private final Options options;
    private final ClassLoader parentClassLoader;
    private final PermissionCollection permissionCollection;
    private final CodeSource codeSource;
    private final String classpath;
    private volatile long lastCompileCheck = -1L;
    private volatile long lastJspQueueUpdate = System.currentTimeMillis();
    /* JSP空闲超时, in milliseconds */
    private long jspIdleTimeout;

    /**
     * JSP 页面映射到它们的 JspServletWrapper
     */
    private final Map<String, JspServletWrapper> jsps = new ConcurrentHashMap<>();

    /**
     * 通过上次访问保持的JSP页面排序.
     */
    private FastRemovalDequeue<JspServletWrapper> jspQueue = null;

    // ------------------------------------------------------ Public Methods

    /**
     * @param jspUri JSP URI
     * @param jsw Servlet wrapper for JSP
     */
    public void addWrapper(String jspUri, JspServletWrapper jsw) {
        jsps.put(jspUri, jsw);
    }

    /**
     * 获取现有的JspServletWrapper.
     *
     * @param jspUri JSP URI
     * @return JspServletWrapper for JSP
     */
    public JspServletWrapper getWrapper(String jspUri) {
        return jsps.get(jspUri);
    }

    /**
     * @param jspUri 要删除的JspServletWrapper的JSP URI
     */
    public void removeWrapper(String jspUri) {
        jsps.remove(jspUri);
    }

    /**
     * 将新创建的JspServletWrapper放入队列, jsp第一个执行的位置. 销毁在队列中被替换的任何JSP.
     *
     * @param jsw Servlet wrapper for jsp.
     * 
     * @return 在后期执行时可以被推到队列前面的unloadHandle.
     * */
    public FastRemovalDequeue<JspServletWrapper>.Entry push(JspServletWrapper jsw) {
        if (log.isTraceEnabled()) {
            log.trace(Localizer.getMessage("jsp.message.jsp_added",
                                           jsw.getJspUri(), context.getContextPath()));
        }
        FastRemovalDequeue<JspServletWrapper>.Entry entry = jspQueue.push(jsw);
        JspServletWrapper replaced = entry.getReplaced();
        if (replaced != null) {
            if (log.isDebugEnabled()) {
                log.debug(Localizer.getMessage("jsp.message.jsp_removed_excess",
                                               replaced.getJspUri(), context.getContextPath()));
            }
            unloadJspServletWrapper(replaced);
            entry.clearReplaced();
        }
        return entry;
    }

    /**
     * 将unloadHandle放入队列的前面.
     *
     * @param unloadHandle the unloadHandle for the jsp.
     * */
    public void makeYoungest(FastRemovalDequeue<JspServletWrapper>.Entry unloadHandle) {
        if (log.isTraceEnabled()) {
            JspServletWrapper jsw = unloadHandle.getContent();
            log.trace(Localizer.getMessage("jsp.message.jsp_queue_update",
                                           jsw.getJspUri(), context.getContextPath()));
        }
        jspQueue.moveFirst(unloadHandle);
    }

    /**
     * 返回存在JspServletWrappers的JSP的数量, 即, 已经被加载进应用的JSP的数量.
     */
    public int getJspCount() {
        return jsps.size();
    }

    /**
     * 获取该应用上下文的 SecurityManager Policy CodeSource.
     */
    public CodeSource getCodeSource() {
        return codeSource;
    }

    /**
     * 获取父级 URLClassLoader.
     *
     * @return ClassLoader parent
     */
    public ClassLoader getParentClassLoader() {
        return parentClassLoader;
    }

    /**
     * 获取该应用上下文的 SecurityManager PermissionCollection.
     *
     * @return PermissionCollection permissions
     */
    public PermissionCollection getPermissionCollection() {
        return permissionCollection;
    }

    /**
     * 处理web应用上下文的"destory"事件.
     */
    public void destroy() {
        Iterator<JspServletWrapper> servlets = jsps.values().iterator();
        while (servlets.hasNext()) {
            servlets.next().destroy();
        }
    }

    /**
     * 递增JSP重新加载计数器.
     */
    public void incrementJspReloadCount() {
        jspReloadCount.incrementAndGet();
    }

    /**
     * 重置JSP重新加载计数器.
     *
     * @param count 重载的值
     */
    public void setJspReloadCount(int count) {
        jspReloadCount.set(count);
    }

    /**
     * 获取JSP重新加载计数器的当前值.
     */
    public int getJspReloadCount() {
        return jspReloadCount.intValue();
    }

    /**
     * 获取JSP队列的大小.
     *
     * @return JSP数量 (这个JspServlet关联的)
     */
    public int getJspQueueLength() {
        if (jspQueue != null) {
            return jspQueue.getSize();
        }
        return -1;
    }

    /**
     * 获取已卸载的JSP的数量.
     *
     * @return JSP数量 (这个JspServlet关联的)
     */
    public int getJspUnloadCount() {
        return jspUnloadCount.intValue();
    }


    /**
     * 后台线程使用该方法检查检查此类注册的JSP依赖项.
     */
    public void checkCompile() {

        if (lastCompileCheck < 0) {
            // Checking was disabled
            return;
        }
        long now = System.currentTimeMillis();
        if (now > (lastCompileCheck + (options.getCheckInterval() * 1000L))) {
            lastCompileCheck = now;
        } else {
            return;
        }

        Object [] wrappers = jsps.values().toArray();
        for (int i = 0; i < wrappers.length; i++ ) {
            JspServletWrapper jsw = (JspServletWrapper)wrappers[i];
            JspCompilationContext ctxt = jsw.getJspEngineContext();
            // JspServletWrapper 也同步, 当它检测到它必须重新加载
            synchronized(jsw) {
                try {
                    ctxt.compile();
                } catch (FileNotFoundException ex) {
                    ctxt.incrementRemoved();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    jsw.getServletContext().log("Background compile failed",
                                                t);
                }
            }
        }

    }

    /**
     * @return 传递给Java编译器的classpath.
     */
    public String getClassPath() {
        return classpath;
    }

    /**
     * @return 后台任务更新的最后时间
     */
    public long getLastJspQueueUpdate() {
        return lastJspQueueUpdate;
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 用于初始化编译的classpath.
     * @return the compilation classpath
     */
    private String initClassPath() {

        StringBuilder cpath = new StringBuilder();

        if (parentClassLoader instanceof URLClassLoader) {
            URL [] urls = ((URLClassLoader)parentClassLoader).getURLs();

            for (int i = 0; i < urls.length; i++) {
                // Tomcat可以使用除了文件URL之外的URL. 但是, 文件以外的协议: 将生成一个坏的文件系统路径,
                // 所以只添加文件: classpath的协议URL.

                if (urls[i].getProtocol().equals("file") ) {
                    try {
                        // 需要解码URL, 主要是将%20序列转换回空格
                        String decoded = URLDecoder.decode(urls[i].getPath(), "UTF-8");
                        cpath.append(decoded + File.pathSeparator);
                    } catch (UnsupportedEncodingException e) {
                        // All JREs are required to support UTF-8
                    }
                }
            }
        }

        cpath.append(options.getScratchDir() + File.pathSeparator);

        String cp = (String) context.getAttribute(Constants.SERVLET_CLASSPATH);
        if (cp == null || cp.equals("")) {
            cp = options.getClassPath();
        }

        String path = cpath.toString() + cp;

        if(log.isDebugEnabled()) {
            log.debug("Compilation classpath initialized: " + path);
        }
        return path;
    }

    /**
     * 允许 initSecurity()返回两个
     */
    private static class SecurityHolder{
        private final CodeSource cs;
        private final PermissionCollection pc;
        private SecurityHolder(CodeSource cs, PermissionCollection pc){
            this.cs = cs;
            this.pc = pc;
        }
    }
    /**
     * 初始化SecurityManager 数据.
     */
    private SecurityHolder initSecurity() {

        // 设置这个Web应用上下文的 PermissionCollection, 基于配置的权限, 为web应用上下文目录的根目录, 然后为该目录添加一个文件读取权限.
        Policy policy = Policy.getPolicy();
        CodeSource source = null;
        PermissionCollection permissions = null;
        if( policy != null ) {
            try {
                // 获取Web应用程序上下文的权限
                String docBase = context.getRealPath("/");
                if( docBase == null ) {
                    docBase = options.getScratchDir().toString();
                }
                String codeBase = docBase;
                if (!codeBase.endsWith(File.separator)){
                    codeBase = codeBase + File.separator;
                }
                File contextDir = new File(codeBase);
                URL url = contextDir.getCanonicalFile().toURI().toURL();
                source = new CodeSource(url,(Certificate[])null);
                permissions = policy.getPermissions(source);

                // 为Web应用程序上下文目录创建一个文件读取权限
                if (!docBase.endsWith(File.separator)){
                    permissions.add
                        (new FilePermission(docBase,"read"));
                    docBase = docBase + File.separator;
                } else {
                    permissions.add
                        (new FilePermission
                            (docBase.substring(0,docBase.length() - 1),"read"));
                }
                docBase = docBase + "-";
                permissions.add(new FilePermission(docBase,"read"));

                // 规范中, 应用应该对它们的临时目录有读取/写入权限.
                // 没有安全敏感文件, 至少任何应用程序都没有完全控制, 这里将被写入.
                String workDir = options.getScratchDir().toString();
                if (!workDir.endsWith(File.separator)){
                    permissions.add
                        (new FilePermission(workDir,"read,write"));
                    workDir = workDir + File.separator;
                }
                workDir = workDir + "-";
                permissions.add(new FilePermission(
                        workDir,"read,write,delete"));

                // 允许JSP 访问 org.apache.jasper.runtime.HttpJspBase
                permissions.add( new RuntimePermission(
                    "accessClassInPackage.org.apache.jasper.runtime") );
            } catch(Exception e) {
                context.log("Security Init for context failed",e);
            }
        }
        return new SecurityHolder(source, permissions);
    }

    private void unloadJspServletWrapper(JspServletWrapper jsw) {
        removeWrapper(jsw.getJspUri());
        synchronized(jsw) {
            jsw.destroy();
        }
        jspUnloadCount.incrementAndGet();
    }


    /**
     * 后台线程使用此方法来检查是否应该卸载任何JSP.
     */
    public void checkUnload() {

        if (log.isTraceEnabled()) {
            int queueLength = -1;
            if (jspQueue != null) {
                queueLength = jspQueue.getSize();
            }
            log.trace(Localizer.getMessage("jsp.message.jsp_unload_check",
                                           context.getContextPath(), "" + jsps.size(), "" + queueLength));
        }
        long now = System.currentTimeMillis();
        if (jspIdleTimeout > 0) {
            long unloadBefore = now - jspIdleTimeout;
            Object [] wrappers = jsps.values().toArray();
            for (int i = 0; i < wrappers.length; i++ ) {
                JspServletWrapper jsw = (JspServletWrapper)wrappers[i];
                synchronized(jsw) {
                    if (jsw.getLastUsageTime() < unloadBefore) {
                        if (log.isDebugEnabled()) {
                            log.debug(Localizer.getMessage("jsp.message.jsp_removed_idle",
                                                           jsw.getJspUri(), context.getContextPath(),
                                                           "" + (now-jsw.getLastUsageTime())));
                        }
                        if (jspQueue != null) {
                            jspQueue.remove(jsw.getUnloadHandle());
                        }
                        unloadJspServletWrapper(jsw);
                    }
                }
            }
        }
        lastJspQueueUpdate = now;
    }
}
