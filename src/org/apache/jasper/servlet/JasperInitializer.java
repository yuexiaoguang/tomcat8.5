package org.apache.jasper.servlet;

import java.io.IOException;
import java.util.Set;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.jsp.JspFactory;

import org.apache.jasper.Constants;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.compiler.TldCache;
import org.apache.jasper.runtime.JspFactoryImpl;
import org.apache.jasper.security.SecurityClassLoad;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.xml.sax.SAXException;

/**
 * Jasper JSP Engine的初始化程序.
 */
public class JasperInitializer implements ServletContainerInitializer {

    private static final String MSG = "org.apache.jasper.servlet.JasperInitializer";
    private static final Log log = LogFactory.getLog(JasperInitializer.class);

    /**
     * JSP servlet在运行时需要预加载类，以便不会获得defineClassInPackage安全性异常.
     */
    static {
        JspFactoryImpl factory = new JspFactoryImpl();
        SecurityClassLoad.securityClassLoad(factory.getClass().getClassLoader());
        if( System.getSecurityManager() != null ) {
            String basePackage = "org.apache.jasper.";
            try {
                factory.getClass().getClassLoader().loadClass( basePackage +
                        "runtime.JspFactoryImpl$PrivilegedGetPageContext");
                factory.getClass().getClassLoader().loadClass( basePackage +
                        "runtime.JspFactoryImpl$PrivilegedReleasePageContext");
                factory.getClass().getClassLoader().loadClass( basePackage +
                        "runtime.JspRuntimeLibrary");
                factory.getClass().getClassLoader().loadClass( basePackage +
                        "runtime.ServletResponseWrapperInclude");
                factory.getClass().getClassLoader().loadClass( basePackage +
                        "servlet.JspServletWrapper");
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException(ex);
            }
        }

        if (JspFactory.getDefaultFactory() == null) {
            JspFactory.setDefaultFactory(factory);
        }
    }

    @Override
    public void onStartup(Set<Class<?>> types, ServletContext context) throws ServletException {
        if (log.isDebugEnabled()) {
            log.debug(Localizer.getMessage(MSG + ".onStartup", context.getServletContextName()));
        }

        // 设置一个简单的默认实例管理器
        if (context.getAttribute(InstanceManager.class.getName())==null) {
            context.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());
        }

        boolean validate = Boolean.parseBoolean(
                context.getInitParameter(Constants.XML_VALIDATION_TLD_INIT_PARAM));
        String blockExternalString = context.getInitParameter(
                Constants.XML_BLOCK_EXTERNAL_INIT_PARAM);
        boolean blockExternal;
        if (blockExternalString == null) {
            blockExternal = true;
        } else {
            blockExternal = Boolean.parseBoolean(blockExternalString);
        }

        // 扫描TLD应用
        TldScanner scanner = newTldScanner(context, true, validate, blockExternal);
        try {
            scanner.scan();
        } catch (IOException | SAXException e) {
            throw new ServletException(e);
        }

        // 添加TLD中定义的任何监听器
        for (String listener : scanner.getListeners()) {
            context.addListener(listener);
        }

        context.setAttribute(TldCache.SERVLET_CONTEXT_ATTRIBUTE_NAME,
                new TldCache(context, scanner.getUriTldResourcePathMap(),
                        scanner.getTldResourcePathTaglibXmlMap()));
    }

    protected TldScanner newTldScanner(ServletContext context, boolean namespaceAware,
            boolean validate, boolean blockExternal) {
        return new TldScanner(context, namespaceAware, validate, blockExternal);
    }
}
