package org.apache.catalina.startup;

/**
 * String constants for the startup package.
 */
public final class Constants {

    public static final String Package = "org.apache.catalina.startup";

    public static final String ApplicationContextXml = "META-INF/context.xml";
    public static final String ApplicationWebXml = "/WEB-INF/web.xml";
    public static final String DefaultContextXml = "conf/context.xml";
    public static final String DefaultWebXml = "conf/web.xml";
    public static final String HostContextXml = "context.xml.default";
    public static final String HostWebXml = "web.xml.default";
    public static final String WarTracker = "/META-INF/war-tracker";

    /**
     * 假的值，用于阻止加载默认的 web.xml 文件.
     *
     * <p>
     * 在内嵌的Tomcat中有用, 当默认配置以编程方式完成时, 即通过调用
     * <code>Tomcat.initWebappDefaults(context)</code>.
     */
    public static final String NoDefaultWebXml = "org/apache/catalina/startup/NO_DEFAULT_XML";
}
