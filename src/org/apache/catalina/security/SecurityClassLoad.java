package org.apache.catalina.security;

/**
 * 用于预加载Java类，当使用Java SecurityManager的时候.
 * 因此 defineClassInPackage RuntimePermission 不会触发一个 AccessControlException.
 */
public final class SecurityClassLoad {

    public static void securityClassLoad(ClassLoader loader) throws Exception {
        securityClassLoad(loader, true);
    }


    static void securityClassLoad(ClassLoader loader, boolean requireSecurityManager)
            throws Exception {

        if (requireSecurityManager && System.getSecurityManager() == null) {
            return;
        }

        loadCorePackage(loader);
        loadCoyotePackage(loader);
        loadLoaderPackage(loader);
        loadRealmPackage(loader);
        loadServletsPackage(loader);
        loadSessionPackage(loader);
        loadUtilPackage(loader);
        loadValvesPackage(loader);
        loadJavaxPackage(loader);
        loadConnectorPackage(loader);
        loadTomcatPackage(loader);
    }


    private static final void loadCorePackage(ClassLoader loader)
            throws Exception {
        final String basePackage = "org.apache.catalina.core.";
        loader.loadClass
            (basePackage +
             "AccessLogAdapter");
        loader.loadClass
            (basePackage +
             "ApplicationContextFacade$PrivilegedExecuteMethod");
        loader.loadClass
            (basePackage +
             "ApplicationDispatcher$PrivilegedForward");
        loader.loadClass
            (basePackage +
             "ApplicationDispatcher$PrivilegedInclude");
        loader.loadClass
            (basePackage +
             "ApplicationPushBuilder");
        loader.loadClass
            (basePackage +
            "AsyncContextImpl");
        loader.loadClass
            (basePackage +
            "AsyncContextImpl$AsyncRunnable");
        loader.loadClass
            (basePackage +
            "AsyncContextImpl$DebugException");
        loader.loadClass
            (basePackage +
            "AsyncListenerWrapper");
        loader.loadClass
            (basePackage +
             "ContainerBase$PrivilegedAddChild");
        loader.loadClass
            (basePackage +
             "DefaultInstanceManager$1");
        loader.loadClass
            (basePackage +
             "DefaultInstanceManager$2");
        loader.loadClass
            (basePackage +
             "DefaultInstanceManager$3");
        loader.loadClass
            (basePackage +
             "DefaultInstanceManager$AnnotationCacheEntry");
        loader.loadClass
            (basePackage +
             "DefaultInstanceManager$AnnotationCacheEntryType");
        loader.loadClass
            (basePackage +
             "ApplicationHttpRequest$AttributeNamesEnumerator");
    }


    private static final void loadLoaderPackage(ClassLoader loader)
            throws Exception {
        final String basePackage = "org.apache.catalina.loader.";
        loader.loadClass
            (basePackage +
             "WebappClassLoaderBase$PrivilegedFindClassByName");
        loader.loadClass(basePackage + "WebappClassLoaderBase$PrivilegedHasLoggingConfig");
    }


    private static final void loadRealmPackage(ClassLoader loader)
            throws Exception {
        final String basePackage = "org.apache.catalina.realm.";
        loader.loadClass
            (basePackage + "LockOutRealm$LockRecord");
    }


    private static final void loadServletsPackage(ClassLoader loader)
            throws Exception {
        final String basePackage = "org.apache.catalina.servlets.";
        // 避免DefaultServlet中可能的内存泄漏, 在security manager中运行时. DefaultServlet需要加载XML 解析器, 在security manager中运行时.
        // 希望通过容器加载而不是Web应用程序来防止Web应用程序类加载器内存泄漏.
        loader.loadClass(basePackage + "DefaultServlet");
    }


    private static final void loadSessionPackage(ClassLoader loader)
            throws Exception {
        final String basePackage = "org.apache.catalina.session.";
        loader.loadClass
            (basePackage + "StandardSession");
        loader.loadClass
            (basePackage + "StandardSession$1");
        loader.loadClass
            (basePackage + "StandardManager$PrivilegedDoUnload");
    }


    private static final void loadUtilPackage(ClassLoader loader)
            throws Exception {
        final String basePackage = "org.apache.catalina.util.";
        loader.loadClass(basePackage + "ParameterMap");
        loader.loadClass(basePackage + "RequestUtil");
        loader.loadClass(basePackage + "TLSUtil");
    }


    private static final void loadValvesPackage(ClassLoader loader)
            throws Exception {
        final String basePackage = "org.apache.catalina.valves.";
        loader.loadClass(basePackage + "AbstractAccessLogValve$3");
    }


    private static final void loadCoyotePackage(ClassLoader loader)
            throws Exception {
        final String basePackage = "org.apache.coyote.";
        loader.loadClass(basePackage + "http11.Constants");
        // Make sure system property is read at this point
        Class<?> clazz = loader.loadClass(basePackage + "Constants");
        clazz.getConstructor().newInstance();
        loader.loadClass(basePackage + "http2.Stream$1");
    }


    private static final void loadJavaxPackage(ClassLoader loader)
            throws Exception {
        loader.loadClass("javax.servlet.http.Cookie");
    }


    private static final void loadConnectorPackage(ClassLoader loader)
            throws Exception {
        final String basePackage = "org.apache.catalina.connector.";
        loader.loadClass
            (basePackage +
             "RequestFacade$GetAttributePrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetParameterMapPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetRequestDispatcherPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetParameterPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetParameterNamesPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetParameterValuePrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetCharacterEncodingPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetHeadersPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetHeaderNamesPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetCookiesPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetLocalePrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetLocalesPrivilegedAction");
        loader.loadClass
            (basePackage +
             "ResponseFacade$SetContentTypePrivilegedAction");
        loader.loadClass
            (basePackage +
             "ResponseFacade$DateHeaderPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetSessionPrivilegedAction");
        loader.loadClass
            (basePackage +
             "ResponseFacade$1");
        loader.loadClass
            (basePackage +
             "OutputBuffer$1");
        loader.loadClass
            (basePackage +
             "OutputBuffer$2");
        loader.loadClass
            (basePackage +
             "CoyoteInputStream$1");
        loader.loadClass
            (basePackage +
             "CoyoteInputStream$2");
        loader.loadClass
            (basePackage +
             "CoyoteInputStream$3");
        loader.loadClass
            (basePackage +
             "CoyoteInputStream$4");
        loader.loadClass
            (basePackage +
             "CoyoteInputStream$5");
        loader.loadClass
            (basePackage +
             "InputBuffer$1");
        loader.loadClass
            (basePackage +
             "Response$1");
        loader.loadClass
            (basePackage +
             "Response$2");
        loader.loadClass
            (basePackage +
             "Response$3");
    }

    private static final void loadTomcatPackage(ClassLoader loader)
            throws Exception {
        final String basePackage = "org.apache.tomcat.";
        // buf
        loader.loadClass(basePackage + "util.buf.B2CConverter");
        loader.loadClass(basePackage + "util.buf.ByteBufferUtils");
        loader.loadClass(basePackage + "util.buf.C2BConverter");
        loader.loadClass(basePackage + "util.buf.HexUtils");
        loader.loadClass(basePackage + "util.buf.StringCache");
        loader.loadClass(basePackage + "util.buf.StringCache$ByteEntry");
        loader.loadClass(basePackage + "util.buf.StringCache$CharEntry");
        loader.loadClass(basePackage + "util.buf.UriUtil");
        // collections
        Class<?> clazz = loader.loadClass(basePackage + "util.collections.CaseInsensitiveKeyMap");
        // Ensure StringManager is configured
        clazz.getConstructor().newInstance();
        loader.loadClass(basePackage + "util.collections.CaseInsensitiveKeyMap$EntryImpl");
        loader.loadClass(basePackage + "util.collections.CaseInsensitiveKeyMap$EntryIterator");
        loader.loadClass(basePackage + "util.collections.CaseInsensitiveKeyMap$EntrySet");
        loader.loadClass(basePackage + "util.collections.CaseInsensitiveKeyMap$Key");
        // http
        loader.loadClass(basePackage + "util.http.CookieProcessor");
        loader.loadClass(basePackage + "util.http.NamesEnumerator");
        // Make sure system property is read at this point
        clazz = loader.loadClass(basePackage + "util.http.FastHttpDateFormat");
        clazz.getConstructor().newInstance();
        loader.loadClass(basePackage + "util.http.parser.HttpParser");
        loader.loadClass(basePackage + "util.http.parser.MediaType");
        loader.loadClass(basePackage + "util.http.parser.MediaTypeCache");
        loader.loadClass(basePackage + "util.http.parser.SkipResult");
        // net
        loader.loadClass(basePackage + "util.net.Constants");
        loader.loadClass(basePackage + "util.net.DispatchType");
        loader.loadClass(basePackage +
                "util.net.NioBlockingSelector$BlockPoller$1");
        loader.loadClass(basePackage +
                "util.net.NioBlockingSelector$BlockPoller$2");
        loader.loadClass(basePackage +
                "util.net.NioBlockingSelector$BlockPoller$3");
        // security
        loader.loadClass(basePackage + "util.security.PrivilegedGetTccl");
        loader.loadClass(basePackage + "util.security.PrivilegedSetTccl");
    }
}

