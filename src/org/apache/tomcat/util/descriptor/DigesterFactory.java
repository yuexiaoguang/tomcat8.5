package org.apache.tomcat.util.descriptor;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSet;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.ext.EntityResolver2;

/**
 * 隐藏了Digester初始化细节的Digester包装类.
 */
public class DigesterFactory {

    private static final Log log = LogFactory.getLog(DigesterFactory.class);
    private static final StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);

    private static final Class<ServletContext> CLASS_SERVLET_CONTEXT;
    private static final Class<?> CLASS_JSP_CONTEXT;

    static {
        CLASS_SERVLET_CONTEXT = ServletContext.class;
        Class<?> jspContext = null;
        try {
            jspContext = Class.forName("javax.servlet.jsp.JspContext");
        } catch (ClassNotFoundException e) {
            // Ignore - JSP API不存在.
        }
        CLASS_JSP_CONTEXT = jspContext;
    }


    /**
     * 将Servlet API使用的众所周知的公共ID映射到匹配的本地资源.
     */
    public static final Map<String,String> SERVLET_API_PUBLIC_IDS;

    /**
     * 将Servlet API使用的众所周知的系统ID映射到匹配的本地资源.
     */
    public static final Map<String,String> SERVLET_API_SYSTEM_IDS;

    static {
        Map<String, String> publicIds = new HashMap<>();
        Map<String, String> systemIds = new HashMap<>();

        // W3C
        add(publicIds, XmlIdentifiers.XSD_10_PUBLIC, locationFor("XMLSchema.dtd"));
        add(publicIds, XmlIdentifiers.DATATYPES_PUBLIC, locationFor("datatypes.dtd"));
        add(systemIds, XmlIdentifiers.XML_2001_XSD, locationFor("xml.xsd"));

        // from J2EE 1.2
        add(publicIds, XmlIdentifiers.WEB_22_PUBLIC, locationFor("web-app_2_2.dtd"));
        add(publicIds, XmlIdentifiers.TLD_11_PUBLIC, locationFor("web-jsptaglibrary_1_1.dtd"));

        // from J2EE 1.3
        add(publicIds, XmlIdentifiers.WEB_23_PUBLIC, locationFor("web-app_2_3.dtd"));
        add(publicIds, XmlIdentifiers.TLD_12_PUBLIC, locationFor("web-jsptaglibrary_1_2.dtd"));

        // from J2EE 1.4
        add(systemIds, "http://www.ibm.com/webservices/xsd/j2ee_web_services_1_1.xsd",
                locationFor("j2ee_web_services_1_1.xsd"));
        add(systemIds, "http://www.ibm.com/webservices/xsd/j2ee_web_services_client_1_1.xsd",
                locationFor("j2ee_web_services_client_1_1.xsd"));
        add(systemIds, XmlIdentifiers.WEB_24_XSD, locationFor("web-app_2_4.xsd"));
        add(systemIds, XmlIdentifiers.TLD_20_XSD, locationFor("web-jsptaglibrary_2_0.xsd"));
        addSelf(systemIds, "j2ee_1_4.xsd");
        addSelf(systemIds, "jsp_2_0.xsd");

        // from JavaEE 5
        add(systemIds, XmlIdentifiers.WEB_25_XSD, locationFor("web-app_2_5.xsd"));
        add(systemIds, XmlIdentifiers.TLD_21_XSD, locationFor("web-jsptaglibrary_2_1.xsd"));
        addSelf(systemIds, "javaee_5.xsd");
        addSelf(systemIds, "jsp_2_1.xsd");
        addSelf(systemIds, "javaee_web_services_1_2.xsd");
        addSelf(systemIds, "javaee_web_services_client_1_2.xsd");

        // from JavaEE 6
        add(systemIds, XmlIdentifiers.WEB_30_XSD, locationFor("web-app_3_0.xsd"));
        add(systemIds, XmlIdentifiers.WEB_FRAGMENT_30_XSD, locationFor("web-fragment_3_0.xsd"));
        addSelf(systemIds, "web-common_3_0.xsd");
        addSelf(systemIds, "javaee_6.xsd");
        addSelf(systemIds, "jsp_2_2.xsd");
        addSelf(systemIds, "javaee_web_services_1_3.xsd");
        addSelf(systemIds, "javaee_web_services_client_1_3.xsd");

        // from JavaEE 7
        add(systemIds, XmlIdentifiers.WEB_31_XSD, locationFor("web-app_3_1.xsd"));
        add(systemIds, XmlIdentifiers.WEB_FRAGMENT_31_XSD, locationFor("web-fragment_3_1.xsd"));
        addSelf(systemIds, "web-common_3_1.xsd");
        addSelf(systemIds, "javaee_7.xsd");
        addSelf(systemIds, "jsp_2_3.xsd");
        addSelf(systemIds, "javaee_web_services_1_4.xsd");
        addSelf(systemIds, "javaee_web_services_client_1_4.xsd");

        SERVLET_API_PUBLIC_IDS = Collections.unmodifiableMap(publicIds);
        SERVLET_API_SYSTEM_IDS = Collections.unmodifiableMap(systemIds);
    }

    private static void addSelf(Map<String, String> ids, String id) {
        String location = locationFor(id);
        if (location != null) {
            ids.put(id, location);
            ids.put(location, location);
        }
    }

    private static void add(Map<String,String> ids, String id, String location) {
        if (location != null) {
            ids.put(id, location);
        }
    }

    private static String locationFor(String name) {
        URL location = CLASS_SERVLET_CONTEXT.getResource("resources/" + name);
        if (location == null && CLASS_JSP_CONTEXT != null) {
            location = CLASS_JSP_CONTEXT.getResource("resources/" + name);
        }
        if (location == null) {
            log.warn(sm.getString("digesterFactory.missingSchema", name));
            return null;
        }
        return location.toExternalForm();
    }


    /**
     * @param xmlValidation 打开/关闭xml验证
     * @param xmlNamespaceAware 打开/关闭命名空间验证
     * @param rule 用于解析xml的<code>RuleSet</code>的实例.
     * @param blockExternal 打开/关闭阻塞外部资源
     * 
     * @return a new digester
     */
    public static Digester newDigester(boolean xmlValidation,
                                       boolean xmlNamespaceAware,
                                       RuleSet rule,
                                       boolean blockExternal) {
        Digester digester = new Digester();
        digester.setNamespaceAware(xmlNamespaceAware);
        digester.setValidating(xmlValidation);
        digester.setUseContextClassLoader(true);
        EntityResolver2 resolver = new LocalResolver(SERVLET_API_PUBLIC_IDS,
                SERVLET_API_SYSTEM_IDS, blockExternal);
        digester.setEntityResolver(resolver);
        if (rule != null) {
            digester.addRuleSet(rule);
        }

        return digester;
    }
}
