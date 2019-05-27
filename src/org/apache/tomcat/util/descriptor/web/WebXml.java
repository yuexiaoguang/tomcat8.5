package org.apache.tomcat.util.descriptor.web;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.descriptor.JspPropertyGroupDescriptor;
import javax.servlet.descriptor.TaglibDescriptor;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.descriptor.XmlIdentifiers;
import org.apache.tomcat.util.digester.DocumentProperties;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;

/**
 * 表示web.xml和web-fragment.xml的公共元素.
 * 在合并元素之前为解析的数据提供存储库. 验证在多个类之间传播:
 * digester 检查结构的正确性 (eg 单个 login-config). 
 * 此类检查无效重复项 (eg filter/servlet 名称)
 * StandardContext 将检查值的有效性 (eg URL 格式等)
 */
@SuppressWarnings("deprecation")
public class WebXml extends XmlEncodingBase implements DocumentProperties.Encoding,
        DocumentProperties.Charset {

    protected static final String ORDER_OTHERS =
        "org.apache.catalina.order.others";

    private static final StringManager sm =
        StringManager.getManager(Constants.PACKAGE_NAME);

    private static final Log log = LogFactory.getLog(WebXml.class);

    /**
     * 全局默认值是可覆盖的, 但Servlet和Servlet映射必须是唯一的. 重复通常会触发错误.
     * 此标志指示新添加的Servlet元素是否标记为可覆盖.
     */
    private boolean overridable = false;
    public boolean isOverridable() {
        return overridable;
    }
    public void setOverridable(boolean overridable) {
        this.overridable = overridable;
    }

    /**
     * web.xml 特有的元素
     * 绝对排序
     */
    private Set<String> absoluteOrdering = null;
    public void createAbsoluteOrdering() {
        if (absoluteOrdering == null) {
            absoluteOrdering = new LinkedHashSet<>();
        }
    }
    public void addAbsoluteOrdering(String fragmentName) {
        createAbsoluteOrdering();
        absoluteOrdering.add(fragmentName);
    }
    public void addAbsoluteOrderingOthers() {
        createAbsoluteOrdering();
        absoluteOrdering.add(ORDER_OTHERS);
    }
    public Set<String> getAbsoluteOrdering() {
        return absoluteOrdering;
    }

    /**
     * web-fragment.xml 特有元素
     * 相对排序
     */
    private final Set<String> after = new LinkedHashSet<>();
    public void addAfterOrdering(String fragmentName) {
        after.add(fragmentName);
    }
    public void addAfterOrderingOthers() {
        if (before.contains(ORDER_OTHERS)) {
            throw new IllegalArgumentException(sm.getString(
                    "webXml.multipleOther"));
        }
        after.add(ORDER_OTHERS);
    }
    public Set<String> getAfterOrdering() { return after; }

    private final Set<String> before = new LinkedHashSet<>();
    public void addBeforeOrdering(String fragmentName) {
        before.add(fragmentName);
    }
    public void addBeforeOrderingOthers() {
        if (after.contains(ORDER_OTHERS)) {
            throw new IllegalArgumentException(sm.getString(
                    "webXml.multipleOther"));
        }
        before.add(ORDER_OTHERS);
    }
    public Set<String> getBeforeOrdering() { return before; }

    // 共同的元素和属性
    // web-app元素的必需属性
    public String getVersion() {
        StringBuilder sb = new StringBuilder(3);
        sb.append(majorVersion);
        sb.append('.');
        sb.append(minorVersion);
        return sb.toString();
    }
    /**
     * 设置此web.xml文件的版本
     * 
     * @param version   将忽略<code>null</code>值
     */
    public void setVersion(String version) {
        if (version == null) {
            return;
        }
        switch (version) {
            case "2.4":
                majorVersion = 2;
                minorVersion = 4;
                break;
            case "2.5":
                majorVersion = 2;
                minorVersion = 5;
                break;
            case "3.0":
                majorVersion = 3;
                minorVersion = 0;
                break;
            case "3.1":
                majorVersion = 3;
                minorVersion = 1;
                break;
            default:
                log.warn(sm.getString("webXml.version.unknown", version));
        }
    }



    // 可选的publicId属性
    private String publicId = null;
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) {
        // 更新主要版本和次要版本
        if (publicId == null) {
            return;
        }
        switch (publicId) {
            case XmlIdentifiers.WEB_22_PUBLIC:
                majorVersion = 2;
                minorVersion = 2;
                this.publicId = publicId;
                break;
            case XmlIdentifiers.WEB_23_PUBLIC:
                majorVersion = 2;
                minorVersion = 3;
                this.publicId = publicId;
                break;
            default:
                log.warn(sm.getString("webXml.unrecognisedPublicId", publicId));
                break;
        }
    }

    // Optional metadata-complete attribute
    private boolean metadataComplete = false;
    public boolean isMetadataComplete() { return metadataComplete; }
    public void setMetadataComplete(boolean metadataComplete) {
        this.metadataComplete = metadataComplete; }

    // Optional name element
    private String name = null;
    public String getName() { return name; }
    public void setName(String name) {
        if (ORDER_OTHERS.equalsIgnoreCase(name)) {
            // This is unusual. This name will be ignored. Log the fact.
            log.warn(sm.getString("webXml.reservedName", name));
        } else {
            this.name = name;
        }
    }

    // 派生主要和次要版本属性
    // 默认为 3,1
    private int majorVersion = 3;
    private int minorVersion = 1;
    public int getMajorVersion() { return majorVersion; }
    public int getMinorVersion() { return minorVersion; }

    // web-app elements
    // TODO: Ignored elements:
    // - description
    // - icon

    // display-name - TODO should support multiple with language
    private String displayName = null;
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    // distributable
    private boolean distributable = false;
    public boolean isDistributable() { return distributable; }
    public void setDistributable(boolean distributable) {
        this.distributable = distributable;
    }

    // deny-uncovered-http-methods
    private boolean denyUncoveredHttpMethods = false;
    public boolean getDenyUncoveredHttpMethods() {
        return denyUncoveredHttpMethods;
    }
    public void setDenyUncoveredHttpMethods(boolean denyUncoveredHttpMethods) {
        this.denyUncoveredHttpMethods = denyUncoveredHttpMethods;
    }

    // context-param
    // TODO: description (multiple with language) is ignored
    private final Map<String,String> contextParams = new HashMap<>();
    public void addContextParam(String param, String value) {
        contextParams.put(param, value);
    }
    public Map<String,String> getContextParams() { return contextParams; }

    // filter
    // TODO: Should support multiple description elements with language
    // TODO: Should support multiple display-name elements with language
    // TODO: Should support multiple icon elements
    // TODO: Description for init-param is ignored
    private final Map<String,FilterDef> filters = new LinkedHashMap<>();
    public void addFilter(FilterDef filter) {
        if (filters.containsKey(filter.getFilterName())) {
            // Filter names must be unique within a web(-fragment).xml
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateFilter",
                            filter.getFilterName()));
        }
        filters.put(filter.getFilterName(), filter);
    }
    public Map<String,FilterDef> getFilters() { return filters; }

    // filter-mapping
    private final Set<FilterMap> filterMaps = new LinkedHashSet<>();
    private final Set<String> filterMappingNames = new HashSet<>();
    public void addFilterMapping(FilterMap filterMap) {
        filterMaps.add(filterMap);
        filterMappingNames.add(filterMap.getFilterName());
    }
    public Set<FilterMap> getFilterMappings() { return filterMaps; }

    // listener
    // TODO: description (multiple with language) is ignored
    // TODO: display-name (multiple with language) is ignored
    // TODO: icon (multiple) is ignored
    private final Set<String> listeners = new LinkedHashSet<>();
    public void addListener(String className) {
        listeners.add(className);
    }
    public Set<String> getListeners() { return listeners; }

    // servlet
    // TODO: description (multiple with language) is ignored
    // TODO: display-name (multiple with language) is ignored
    // TODO: icon (multiple) is ignored
    // TODO: init-param/description (multiple with language) is ignored
    // TODO: security-role-ref/description (multiple with language) is ignored
    private final Map<String,ServletDef> servlets = new HashMap<>();
    public void addServlet(ServletDef servletDef) {
        servlets.put(servletDef.getServletName(), servletDef);
        if (overridable) {
            servletDef.setOverridable(overridable);
        }
    }
    public Map<String,ServletDef> getServlets() { return servlets; }

    // servlet-mapping
    // Note: web.xml中的URLPatterns可能是URL编码的
    //       (http://svn.apache.org/r285186)
    private final Map<String,String> servletMappings = new HashMap<>();
    private final Set<String> servletMappingNames = new HashSet<>();
    public void addServletMapping(String urlPattern, String servletName) {
        addServletMappingDecoded(UDecoder.URLDecode(urlPattern, getCharset()), servletName);
    }
    public void addServletMappingDecoded(String urlPattern, String servletName) {
        String oldServletName = servletMappings.put(urlPattern, servletName);
        if (oldServletName != null) {
            // 重复映射. 根据Servlet EG的澄清, 部署应该失败.
            throw new IllegalArgumentException(sm.getString(
                    "webXml.duplicateServletMapping", oldServletName,
                    servletName, urlPattern));
        }
        servletMappingNames.add(servletName);
    }
    public Map<String,String> getServletMappings() { return servletMappings; }

    // session-config
    // Digester将检查其中是否只有一个
    private SessionConfig sessionConfig = new SessionConfig();
    public void setSessionConfig(SessionConfig sessionConfig) {
        this.sessionConfig = sessionConfig;
    }
    public SessionConfig getSessionConfig() { return sessionConfig; }

    // mime-mapping
    private final Map<String,String> mimeMappings = new HashMap<>();
    public void addMimeMapping(String extension, String mimeType) {
        mimeMappings.put(extension, mimeType);
    }
    public Map<String,String> getMimeMappings() { return mimeMappings; }

    // welcome-file-list merge control
    private boolean replaceWelcomeFiles = false;
    private boolean alwaysAddWelcomeFiles = true;
    /**
     * 将web.xml文件合并/解析到此web.xml时, 应完全替换当前集?
     * 
     * @param replaceWelcomeFiles <code>true</code> 替换欢迎文件而不是添加到列表中
     */
    public void setReplaceWelcomeFiles(boolean replaceWelcomeFiles) {
        this.replaceWelcomeFiles = replaceWelcomeFiles;
    }
    /**
     * 从此web.xml合并时, 是否应将欢迎文件添加到目标web.xml, 即使它已包含欢迎文件定义.
     * 
     * @param alwaysAddWelcomeFiles <code>true</code> 添加欢迎文件
     */
    public void setAlwaysAddWelcomeFiles(boolean alwaysAddWelcomeFiles) {
        this.alwaysAddWelcomeFiles = alwaysAddWelcomeFiles;
    }

    // welcome-file-list
    private final Set<String> welcomeFiles = new LinkedHashSet<>();
    public void addWelcomeFile(String welcomeFile) {
        if (replaceWelcomeFiles) {
            welcomeFiles.clear();
            replaceWelcomeFiles = false;
        }
        welcomeFiles.add(welcomeFile);
    }
    public Set<String> getWelcomeFiles() { return welcomeFiles; }

    // error-page
    private final Map<String,ErrorPage> errorPages = new HashMap<>();
    public void addErrorPage(ErrorPage errorPage) {
        errorPages.put(errorPage.getName(), errorPage);
    }
    public Map<String,ErrorPage> getErrorPages() { return errorPages; }

    // Digester将检查是否只有一个jsp-config
    // jsp-config/taglib or taglib (2.3 and earlier)
    private final Map<String,String> taglibs = new HashMap<>();
    public void addTaglib(String uri, String location) {
        if (taglibs.containsKey(uri)) {
            // Taglib URI在 web(-fragment).xml 中必须是惟一的
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateTaglibUri", uri));
        }
        taglibs.put(uri, location);
    }
    public Map<String,String> getTaglibs() { return taglibs; }

    // jsp-config/jsp-property-group
    private final Set<JspPropertyGroup> jspPropertyGroups = new LinkedHashSet<>();
    public void addJspPropertyGroup(JspPropertyGroup propertyGroup) {
        propertyGroup.setCharset(getCharset());
        jspPropertyGroups.add(propertyGroup);
    }
    public Set<JspPropertyGroup> getJspPropertyGroups() {
        return jspPropertyGroups;
    }

    // security-constraint
    // TODO: Should support multiple display-name elements with language
    // TODO: Should support multiple description elements with language
    private final Set<SecurityConstraint> securityConstraints = new HashSet<>();
    public void addSecurityConstraint(SecurityConstraint securityConstraint) {
        securityConstraint.setCharset(getCharset());
        securityConstraints.add(securityConstraint);
    }
    public Set<SecurityConstraint> getSecurityConstraints() {
        return securityConstraints;
    }

    // login-config
    // Digester将检查其中是否只有一个
    private LoginConfig loginConfig = null;
    public void setLoginConfig(LoginConfig loginConfig) {
        this.loginConfig = loginConfig;
    }
    public LoginConfig getLoginConfig() { return loginConfig; }

    // security-role
    // TODO: description (multiple with language) is ignored
    private final Set<String> securityRoles = new HashSet<>();
    public void addSecurityRole(String securityRole) {
        securityRoles.add(securityRole);
    }
    public Set<String> getSecurityRoles() { return securityRoles; }

    // env-entry
    // TODO: Should support multiple description elements with language
    private final Map<String,ContextEnvironment> envEntries = new HashMap<>();
    public void addEnvEntry(ContextEnvironment envEntry) {
        if (envEntries.containsKey(envEntry.getName())) {
            // env-entry 名称在 web(-fragment).xml 中必须是惟一的
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateEnvEntry",
                            envEntry.getName()));
        }
        envEntries.put(envEntry.getName(),envEntry);
    }
    public Map<String,ContextEnvironment> getEnvEntries() { return envEntries; }

    // ejb-ref
    // TODO: Should support multiple description elements with language
    private final Map<String,ContextEjb> ejbRefs = new HashMap<>();
    public void addEjbRef(ContextEjb ejbRef) {
        ejbRefs.put(ejbRef.getName(),ejbRef);
    }
    public Map<String,ContextEjb> getEjbRefs() { return ejbRefs; }

    // ejb-local-ref
    // TODO: Should support multiple description elements with language
    private final Map<String,ContextLocalEjb> ejbLocalRefs = new HashMap<>();
    public void addEjbLocalRef(ContextLocalEjb ejbLocalRef) {
        ejbLocalRefs.put(ejbLocalRef.getName(),ejbLocalRef);
    }
    public Map<String,ContextLocalEjb> getEjbLocalRefs() {
        return ejbLocalRefs;
    }

    // service-ref
    // TODO: Should support multiple description elements with language
    // TODO: Should support multiple display-names elements with language
    // TODO: Should support multiple icon elements ???
    private final Map<String,ContextService> serviceRefs = new HashMap<>();
    public void addServiceRef(ContextService serviceRef) {
        serviceRefs.put(serviceRef.getName(), serviceRef);
    }
    public Map<String,ContextService> getServiceRefs() { return serviceRefs; }

    // resource-ref
    // TODO: Should support multiple description elements with language
    private final Map<String,ContextResource> resourceRefs = new HashMap<>();
    public void addResourceRef(ContextResource resourceRef) {
        if (resourceRefs.containsKey(resourceRef.getName())) {
            // resource-ref 名称必须在 web(-fragment).xml 中唯一
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateResourceRef",
                            resourceRef.getName()));
        }
        resourceRefs.put(resourceRef.getName(), resourceRef);
    }
    public Map<String,ContextResource> getResourceRefs() {
        return resourceRefs;
    }

    // resource-env-ref
    // TODO: Should support multiple description elements with language
    private final Map<String,ContextResourceEnvRef> resourceEnvRefs = new HashMap<>();
    public void addResourceEnvRef(ContextResourceEnvRef resourceEnvRef) {
        if (resourceEnvRefs.containsKey(resourceEnvRef.getName())) {
            // resource-env-ref 名称必须在 web(-fragment).xml 中唯一
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateResourceEnvRef",
                            resourceEnvRef.getName()));
        }
        resourceEnvRefs.put(resourceEnvRef.getName(), resourceEnvRef);
    }
    public Map<String,ContextResourceEnvRef> getResourceEnvRefs() {
        return resourceEnvRefs;
    }

    // message-destination-ref
    // TODO: Should support multiple description elements with language
    private final Map<String,MessageDestinationRef> messageDestinationRefs =
        new HashMap<>();
    public void addMessageDestinationRef(
            MessageDestinationRef messageDestinationRef) {
        if (messageDestinationRefs.containsKey(
                messageDestinationRef.getName())) {
            // message-destination-ref 名称必须在 web(-fragment).xml 中唯一
            throw new IllegalArgumentException(sm.getString(
                    "webXml.duplicateMessageDestinationRef",
                    messageDestinationRef.getName()));
        }
        messageDestinationRefs.put(messageDestinationRef.getName(),
                messageDestinationRef);
    }
    public Map<String,MessageDestinationRef> getMessageDestinationRefs() {
        return messageDestinationRefs;
    }

    // message-destination
    // TODO: Should support multiple description elements with language
    // TODO: Should support multiple display-names elements with language
    // TODO: Should support multiple icon elements ???
    private final Map<String,MessageDestination> messageDestinations =
            new HashMap<>();
    public void addMessageDestination(
            MessageDestination messageDestination) {
        if (messageDestinations.containsKey(
                messageDestination.getName())) {
            // message-destination 名称必须在 web(-fragment).xml 中唯一
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateMessageDestination",
                            messageDestination.getName()));
        }
        messageDestinations.put(messageDestination.getName(),
                messageDestination);
    }
    public Map<String,MessageDestination> getMessageDestinations() {
        return messageDestinations;
    }

    // locale-encoding-mapping-list
    private final Map<String,String> localeEncodingMappings = new HashMap<>();
    public void addLocaleEncodingMapping(String locale, String encoding) {
        localeEncodingMappings.put(locale, encoding);
    }
    public Map<String,String> getLocaleEncodingMappings() {
        return localeEncodingMappings;
    }

    // post-construct
    private Map<String, String> postConstructMethods = new HashMap<>();
    public void addPostConstructMethods(String clazz, String method) {
        if (!postConstructMethods.containsKey(clazz)) {
            postConstructMethods.put(clazz, method);
        }
    }
    public Map<String, String> getPostConstructMethods() {
        return postConstructMethods;
    }

    // pre-destroy elements
    private Map<String, String> preDestroyMethods = new HashMap<>();
    public void addPreDestroyMethods(String clazz, String method) {
        if (!preDestroyMethods.containsKey(clazz)) {
            preDestroyMethods.put(clazz, method);
        }
    }
    public Map<String, String> getPreDestroyMethods() {
        return preDestroyMethods;
    }

    public JspConfigDescriptor getJspConfigDescriptor() {
        if (jspPropertyGroups.isEmpty() && taglibs.isEmpty()) {
            return null;
        }

        Collection<JspPropertyGroupDescriptor> descriptors =
                new ArrayList<>(jspPropertyGroups.size());
        for (JspPropertyGroup jspPropertyGroup : jspPropertyGroups) {
            JspPropertyGroupDescriptor descriptor =
                    new JspPropertyGroupDescriptorImpl(jspPropertyGroup);
            descriptors.add(descriptor);

        }

        Collection<TaglibDescriptor> tlds = new HashSet<>(taglibs.size());
        for (Entry<String, String> entry : taglibs.entrySet()) {
            TaglibDescriptor descriptor = new TaglibDescriptorImpl(
                    entry.getValue(), entry.getKey());
            tlds.add(descriptor);
        }
        return new JspConfigDescriptorImpl(descriptors, tlds);
    }

    // 未在web.xml或web-fragment.xml中定义的属性

    // URL of JAR / exploded JAR for this web-fragment
    private URL uRL = null;
    public void setURL(URL url) { this.uRL = url; }
    public URL getURL() { return uRL; }

    // Name of jar file
    private String jarName = null;
    public void setJarName(String jarName) { this.jarName = jarName; }
    public String getJarName() { return jarName; }

    // 这是应用程序的JAR部分还是容器JAR?
    private boolean webappJar = true;
    public void setWebappJar(boolean webappJar) { this.webappJar = webappJar; }
    public boolean getWebappJar() { return webappJar; }

    // 此Web应用程序是否首先委托加载类?
    private boolean delegate = false;
    public boolean getDelegate() { return delegate; }
    public void setDelegate(boolean delegate) { this.delegate = delegate; }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(32);
        buf.append("Name: ");
        buf.append(getName());
        buf.append(", URL: ");
        buf.append(getURL());
        return buf.toString();
    }

    private static final String INDENT2 = "  ";
    private static final String INDENT4 = "    ";
    private static final String INDENT6 = "      ";

    /**
     * 生成String形式的web.xml, 该表单与存储在此对象中的表示形式相匹配.
     *
     * @return web.xml的完整内容为String
     */
    public String toXml() {
        StringBuilder sb = new StringBuilder(2048);
        // TODO - Various, icon, description 等元素跳过 - 主要是因为在解析web.xml时会忽略它们 - see above

        // NOTE - 元素需要按照2.3 DTD中定义的顺序编写，否则合并的web.xml将验证失败

        // NOTE - 根据所使用规范的版本, 需要跳过某些元素. 版本已经过验证，从2.2开始. 此方法中使用的版本测试利用了这一点.

        // Declaration
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        // Root element
        if (publicId != null) {
            sb.append("<!DOCTYPE web-app PUBLIC\n");
            sb.append("  \"");
            sb.append(publicId);
            sb.append("\"\n");
            sb.append("  \"");
            if (XmlIdentifiers.WEB_22_PUBLIC.equals(publicId)) {
                sb.append(XmlIdentifiers.WEB_22_SYSTEM);
            } else {
                sb.append(XmlIdentifiers.WEB_23_SYSTEM);
            }
            sb.append("\">\n");
            sb.append("<web-app>");
        } else {
            String javaeeNamespace = null;
            String webXmlSchemaLocation = null;
            String version = getVersion();
            if ("2.4".equals(version)) {
                javaeeNamespace = XmlIdentifiers.JAVAEE_1_4_NS;
                webXmlSchemaLocation = XmlIdentifiers.WEB_24_XSD;
            } else if ("2.5".equals(version)) {
                javaeeNamespace = XmlIdentifiers.JAVAEE_5_NS;
                webXmlSchemaLocation = XmlIdentifiers.WEB_25_XSD;
            } else if ("3.0".equals(version)) {
                javaeeNamespace = XmlIdentifiers.JAVAEE_6_NS;
                webXmlSchemaLocation = XmlIdentifiers.WEB_30_XSD;
            } else if ("3.1".equals(version)) {
                javaeeNamespace = XmlIdentifiers.JAVAEE_7_NS;
                webXmlSchemaLocation = XmlIdentifiers.WEB_31_XSD;
            }
            sb.append("<web-app xmlns=\"");
            sb.append(javaeeNamespace);
            sb.append("\"\n");
            sb.append("         xmlns:xsi=");
            sb.append("\"http://www.w3.org/2001/XMLSchema-instance\"\n");
            sb.append("         xsi:schemaLocation=\"");
            sb.append(javaeeNamespace);
            sb.append(" ");
            sb.append(webXmlSchemaLocation);
            sb.append("\"\n");
            sb.append("         version=\"");
            sb.append(getVersion());
            sb.append("\"");
            if ("2.4".equals(version)) {
                sb.append(">\n\n");
            } else {
                sb.append("\n         metadata-complete=\"true\">\n\n");
            }
        }

        appendElement(sb, INDENT2, "display-name", displayName);

        if (isDistributable()) {
            sb.append("  <distributable/>\n\n");
        }

        for (Map.Entry<String, String> entry : contextParams.entrySet()) {
            sb.append("  <context-param>\n");
            appendElement(sb, INDENT4, "param-name", entry.getKey());
            appendElement(sb, INDENT4, "param-value", entry.getValue());
            sb.append("  </context-param>\n");
        }
        sb.append('\n');

        // Servlet 2.3中引入了过滤器
        if (getMajorVersion() > 2 || getMinorVersion() > 2) {
            for (Map.Entry<String, FilterDef> entry : filters.entrySet()) {
                FilterDef filterDef = entry.getValue();
                sb.append("  <filter>\n");
                appendElement(sb, INDENT4, "description",
                        filterDef.getDescription());
                appendElement(sb, INDENT4, "display-name",
                        filterDef.getDisplayName());
                appendElement(sb, INDENT4, "filter-name",
                        filterDef.getFilterName());
                appendElement(sb, INDENT4, "filter-class",
                        filterDef.getFilterClass());
                // 从Servlet 3.0开始引入异步支持
                if (getMajorVersion() != 2) {
                    appendElement(sb, INDENT4, "async-supported",
                            filterDef.getAsyncSupported());
                }
                for (Map.Entry<String, String> param :
                        filterDef.getParameterMap().entrySet()) {
                    sb.append("    <init-param>\n");
                    appendElement(sb, INDENT6, "param-name", param.getKey());
                    appendElement(sb, INDENT6, "param-value", param.getValue());
                    sb.append("    </init-param>\n");
                }
                sb.append("  </filter>\n");
            }
            sb.append('\n');

            for (FilterMap filterMap : filterMaps) {
                sb.append("  <filter-mapping>\n");
                appendElement(sb, INDENT4, "filter-name",
                        filterMap.getFilterName());
                if (filterMap.getMatchAllServletNames()) {
                    sb.append("    <servlet-name>*</servlet-name>\n");
                } else {
                    for (String servletName : filterMap.getServletNames()) {
                        appendElement(sb, INDENT4, "servlet-name", servletName);
                    }
                }
                if (filterMap.getMatchAllUrlPatterns()) {
                    sb.append("    <url-pattern>*</url-pattern>\n");
                } else {
                    for (String urlPattern : filterMap.getURLPatterns()) {
                        appendElement(sb, INDENT4, "url-pattern", encodeUrl(urlPattern));
                    }
                }
                // Servlet 2.4中添加了调度程序
                if (getMajorVersion() > 2 || getMinorVersion() > 3) {
                    for (String dispatcher : filterMap.getDispatcherNames()) {
                        if (getMajorVersion() == 2 &&
                                DispatcherType.ASYNC.name().equals(dispatcher)) {
                            continue;
                        }
                        appendElement(sb, INDENT4, "dispatcher", dispatcher);
                    }
                }
                sb.append("  </filter-mapping>\n");
            }
            sb.append('\n');
        }

        // Servlet 2.3中引入了监听器
        if (getMajorVersion() > 2 || getMinorVersion() > 2) {
            for (String listener : listeners) {
                sb.append("  <listener>\n");
                appendElement(sb, INDENT4, "listener-class", listener);
                sb.append("  </listener>\n");
            }
            sb.append('\n');
        }

        for (Map.Entry<String, ServletDef> entry : servlets.entrySet()) {
            ServletDef servletDef = entry.getValue();
            sb.append("  <servlet>\n");
            appendElement(sb, INDENT4, "description",
                    servletDef.getDescription());
            appendElement(sb, INDENT4, "display-name",
                    servletDef.getDisplayName());
            appendElement(sb, INDENT4, "servlet-name", entry.getKey());
            appendElement(sb, INDENT4, "servlet-class",
                    servletDef.getServletClass());
            appendElement(sb, INDENT4, "jsp-file", servletDef.getJspFile());
            for (Map.Entry<String, String> param :
                    servletDef.getParameterMap().entrySet()) {
                sb.append("    <init-param>\n");
                appendElement(sb, INDENT6, "param-name", param.getKey());
                appendElement(sb, INDENT6, "param-value", param.getValue());
                sb.append("    </init-param>\n");
            }
            appendElement(sb, INDENT4, "load-on-startup",
                    servletDef.getLoadOnStartup());
            appendElement(sb, INDENT4, "enabled", servletDef.getEnabled());
            // 从Servlet 3.0开始引入异步支持
            if (getMajorVersion() != 2) {
                appendElement(sb, INDENT4, "async-supported",
                        servletDef.getAsyncSupported());
            }
            // Servlet/run-as在Servlet 2.3中引入
            if (getMajorVersion() > 2 || getMinorVersion() > 2) {
                if (servletDef.getRunAs() != null) {
                    sb.append("    <run-as>\n");
                    appendElement(sb, INDENT6, "role-name", servletDef.getRunAs());
                    sb.append("    </run-as>\n");
                }
            }
            for (SecurityRoleRef roleRef : servletDef.getSecurityRoleRefs()) {
                sb.append("    <security-role-ref>\n");
                appendElement(sb, INDENT6, "role-name", roleRef.getName());
                appendElement(sb, INDENT6, "role-link", roleRef.getLink());
                sb.append("    </security-role-ref>\n");
            }
            // 在Servlet 3.0中添加了multipart-config
            if (getMajorVersion() != 2) {
                MultipartDef multipartDef = servletDef.getMultipartDef();
                if (multipartDef != null) {
                    sb.append("    <multipart-config>\n");
                    appendElement(sb, INDENT6, "location",
                            multipartDef.getLocation());
                    appendElement(sb, INDENT6, "max-file-size",
                            multipartDef.getMaxFileSize());
                    appendElement(sb, INDENT6, "max-request-size",
                            multipartDef.getMaxRequestSize());
                    appendElement(sb, INDENT6, "file-size-threshold",
                            multipartDef.getFileSizeThreshold());
                    sb.append("    </multipart-config>\n");
                }
            }
            sb.append("  </servlet>\n");
        }
        sb.append('\n');

        for (Map.Entry<String, String> entry : servletMappings.entrySet()) {
            sb.append("  <servlet-mapping>\n");
            appendElement(sb, INDENT4, "servlet-name", entry.getValue());
            appendElement(sb, INDENT4, "url-pattern", encodeUrl(entry.getKey()));
            sb.append("  </servlet-mapping>\n");
        }
        sb.append('\n');

        if (sessionConfig != null) {
            sb.append("  <session-config>\n");
            appendElement(sb, INDENT4, "session-timeout",
                    sessionConfig.getSessionTimeout());
            if (majorVersion >= 3) {
                sb.append("    <cookie-config>\n");
                appendElement(sb, INDENT6, "name", sessionConfig.getCookieName());
                appendElement(sb, INDENT6, "domain",
                        sessionConfig.getCookieDomain());
                appendElement(sb, INDENT6, "path", sessionConfig.getCookiePath());
                appendElement(sb, INDENT6, "comment",
                        sessionConfig.getCookieComment());
                appendElement(sb, INDENT6, "http-only",
                        sessionConfig.getCookieHttpOnly());
                appendElement(sb, INDENT6, "secure",
                        sessionConfig.getCookieSecure());
                appendElement(sb, INDENT6, "max-age",
                        sessionConfig.getCookieMaxAge());
                sb.append("    </cookie-config>\n");
                for (SessionTrackingMode stm :
                        sessionConfig.getSessionTrackingModes()) {
                    appendElement(sb, INDENT4, "tracking-mode", stm.name());
                }
            }
            sb.append("  </session-config>\n\n");
        }

        for (Map.Entry<String, String> entry : mimeMappings.entrySet()) {
            sb.append("  <mime-mapping>\n");
            appendElement(sb, INDENT4, "extension", entry.getKey());
            appendElement(sb, INDENT4, "mime-type", entry.getValue());
            sb.append("  </mime-mapping>\n");
        }
        sb.append('\n');

        if (welcomeFiles.size() > 0) {
            sb.append("  <welcome-file-list>\n");
            for (String welcomeFile : welcomeFiles) {
                appendElement(sb, INDENT4, "welcome-file", welcomeFile);
            }
            sb.append("  </welcome-file-list>\n\n");
        }

        for (ErrorPage errorPage : errorPages.values()) {
            String exceptionType = errorPage.getExceptionType();
            int errorCode = errorPage.getErrorCode();

            if (exceptionType == null && errorCode == 0 && getMajorVersion() == 2) {
                // 默认错误页面仅从3.0开始支持
                continue;
            }
            sb.append("  <error-page>\n");
            if (errorPage.getExceptionType() != null) {
                appendElement(sb, INDENT4, "exception-type", exceptionType);
            } else if (errorPage.getErrorCode() > 0) {
                appendElement(sb, INDENT4, "error-code",
                        Integer.toString(errorCode));
            }
            appendElement(sb, INDENT4, "location", errorPage.getLocation());
            sb.append("  </error-page>\n");
        }
        sb.append('\n');

        // 在Servlet 2.4中添加了jsp-config. 在此之前，直接使用tag-libs并且不存在jsp-property-group
        if (taglibs.size() > 0 || jspPropertyGroups.size() > 0) {
            if (getMajorVersion() > 2 || getMinorVersion() > 3) {
                sb.append("  <jsp-config>\n");
            }
            for (Map.Entry<String, String> entry : taglibs.entrySet()) {
                sb.append("    <taglib>\n");
                appendElement(sb, INDENT6, "taglib-uri", entry.getKey());
                appendElement(sb, INDENT6, "taglib-location", entry.getValue());
                sb.append("    </taglib>\n");
            }
            if (getMajorVersion() > 2 || getMinorVersion() > 3) {
                for (JspPropertyGroup jpg : jspPropertyGroups) {
                    sb.append("    <jsp-property-group>\n");
                    for (String urlPattern : jpg.getUrlPatterns()) {
                        appendElement(sb, INDENT6, "url-pattern", encodeUrl(urlPattern));
                    }
                    appendElement(sb, INDENT6, "el-ignored", jpg.getElIgnored());
                    appendElement(sb, INDENT6, "page-encoding",
                            jpg.getPageEncoding());
                    appendElement(sb, INDENT6, "scripting-invalid",
                            jpg.getScriptingInvalid());
                    appendElement(sb, INDENT6, "is-xml", jpg.getIsXml());
                    for (String prelude : jpg.getIncludePreludes()) {
                        appendElement(sb, INDENT6, "include-prelude", prelude);
                    }
                    for (String coda : jpg.getIncludeCodas()) {
                        appendElement(sb, INDENT6, "include-coda", coda);
                    }
                    appendElement(sb, INDENT6, "deferred-syntax-allowed-as-literal",
                            jpg.getDeferredSyntax());
                    appendElement(sb, INDENT6, "trim-directive-whitespaces",
                            jpg.getTrimWhitespace());
                    appendElement(sb, INDENT6, "default-content-type",
                            jpg.getDefaultContentType());
                    appendElement(sb, INDENT6, "buffer", jpg.getBuffer());
                    appendElement(sb, INDENT6, "error-on-undeclared-namespace",
                            jpg.getErrorOnUndeclaredNamespace());
                    sb.append("    </jsp-property-group>\n");
                }
                sb.append("  </jsp-config>\n\n");
            }
        }

        // resource-env-ref在Servlet 2.3中引入
        if (getMajorVersion() > 2 || getMinorVersion() > 2) {
            for (ContextResourceEnvRef resourceEnvRef : resourceEnvRefs.values()) {
                sb.append("  <resource-env-ref>\n");
                appendElement(sb, INDENT4, "description",
                        resourceEnvRef.getDescription());
                appendElement(sb, INDENT4, "resource-env-ref-name",
                        resourceEnvRef.getName());
                appendElement(sb, INDENT4, "resource-env-ref-type",
                        resourceEnvRef.getType());
                // TODO mapped-name
                for (InjectionTarget target :
                        resourceEnvRef.getInjectionTargets()) {
                    sb.append("    <injection-target>\n");
                    appendElement(sb, INDENT6, "injection-target-class",
                            target.getTargetClass());
                    appendElement(sb, INDENT6, "injection-target-name",
                            target.getTargetName());
                    sb.append("    </injection-target>\n");
                }
                // TODO lookup-name
                sb.append("  </resource-env-ref>\n");
            }
            sb.append('\n');
        }

        for (ContextResource resourceRef : resourceRefs.values()) {
            sb.append("  <resource-ref>\n");
            appendElement(sb, INDENT4, "description",
                    resourceRef.getDescription());
            appendElement(sb, INDENT4, "res-ref-name", resourceRef.getName());
            appendElement(sb, INDENT4, "res-type", resourceRef.getType());
            appendElement(sb, INDENT4, "res-auth", resourceRef.getAuth());
            // Servlet 2.3中引入了resource-ref/res-sharing-scope
            if (getMajorVersion() > 2 || getMinorVersion() > 2) {
                appendElement(sb, INDENT4, "res-sharing-scope",
                        resourceRef.getScope());
            }
            // TODO mapped-name
            for (InjectionTarget target : resourceRef.getInjectionTargets()) {
                sb.append("    <injection-target>\n");
                appendElement(sb, INDENT6, "injection-target-class",
                        target.getTargetClass());
                appendElement(sb, INDENT6, "injection-target-name",
                        target.getTargetName());
                sb.append("    </injection-target>\n");
            }
            // TODO lookup-name
            sb.append("  </resource-ref>\n");
        }
        sb.append('\n');

        for (SecurityConstraint constraint : securityConstraints) {
            sb.append("  <security-constraint>\n");
            // Servlet 2.3中引入了security-constraint/display-name
            if (getMajorVersion() > 2 || getMinorVersion() > 2) {
                appendElement(sb, INDENT4, "display-name",
                        constraint.getDisplayName());
            }
            for (SecurityCollection collection : constraint.findCollections()) {
                sb.append("    <web-resource-collection>\n");
                appendElement(sb, INDENT6, "web-resource-name",
                        collection.getName());
                appendElement(sb, INDENT6, "description",
                        collection.getDescription());
                for (String urlPattern : collection.findPatterns()) {
                    appendElement(sb, INDENT6, "url-pattern", encodeUrl(urlPattern));
                }
                for (String method : collection.findMethods()) {
                    appendElement(sb, INDENT6, "http-method", method);
                }
                for (String method : collection.findOmittedMethods()) {
                    appendElement(sb, INDENT6, "http-method-omission", method);
                }
                sb.append("    </web-resource-collection>\n");
            }
            if (constraint.findAuthRoles().length > 0) {
                sb.append("    <auth-constraint>\n");
                for (String role : constraint.findAuthRoles()) {
                    appendElement(sb, INDENT6, "role-name", role);
                }
                sb.append("    </auth-constraint>\n");
            }
            if (constraint.getUserConstraint() != null) {
                sb.append("    <user-data-constraint>\n");
                appendElement(sb, INDENT6, "transport-guarantee",
                        constraint.getUserConstraint());
                sb.append("    </user-data-constraint>\n");
            }
            sb.append("  </security-constraint>\n");
        }
        sb.append('\n');

        if (loginConfig != null) {
            sb.append("  <login-config>\n");
            appendElement(sb, INDENT4, "auth-method",
                    loginConfig.getAuthMethod());
            appendElement(sb,INDENT4, "realm-name",
                    loginConfig.getRealmName());
            if (loginConfig.getErrorPage() != null ||
                        loginConfig.getLoginPage() != null) {
                sb.append("    <form-login-config>\n");
                appendElement(sb, INDENT6, "form-login-page",
                        loginConfig.getLoginPage());
                appendElement(sb, INDENT6, "form-error-page",
                        loginConfig.getErrorPage());
                sb.append("    </form-login-config>\n");
            }
            sb.append("  </login-config>\n\n");
        }

        for (String roleName : securityRoles) {
            sb.append("  <security-role>\n");
            appendElement(sb, INDENT4, "role-name", roleName);
            sb.append("  </security-role>\n");
        }

        for (ContextEnvironment envEntry : envEntries.values()) {
            sb.append("  <env-entry>\n");
            appendElement(sb, INDENT4, "description",
                    envEntry.getDescription());
            appendElement(sb, INDENT4, "env-entry-name", envEntry.getName());
            appendElement(sb, INDENT4, "env-entry-type", envEntry.getType());
            appendElement(sb, INDENT4, "env-entry-value", envEntry.getValue());
            // TODO mapped-name
            for (InjectionTarget target : envEntry.getInjectionTargets()) {
                sb.append("    <injection-target>\n");
                appendElement(sb, INDENT6, "injection-target-class",
                        target.getTargetClass());
                appendElement(sb, INDENT6, "injection-target-name",
                        target.getTargetName());
                sb.append("    </injection-target>\n");
            }
            // TODO lookup-name
            sb.append("  </env-entry>\n");
        }
        sb.append('\n');

        for (ContextEjb ejbRef : ejbRefs.values()) {
            sb.append("  <ejb-ref>\n");
            appendElement(sb, INDENT4, "description", ejbRef.getDescription());
            appendElement(sb, INDENT4, "ejb-ref-name", ejbRef.getName());
            appendElement(sb, INDENT4, "ejb-ref-type", ejbRef.getType());
            appendElement(sb, INDENT4, "home", ejbRef.getHome());
            appendElement(sb, INDENT4, "remote", ejbRef.getRemote());
            appendElement(sb, INDENT4, "ejb-link", ejbRef.getLink());
            // TODO mapped-name
            for (InjectionTarget target : ejbRef.getInjectionTargets()) {
                sb.append("    <injection-target>\n");
                appendElement(sb, INDENT6, "injection-target-class",
                        target.getTargetClass());
                appendElement(sb, INDENT6, "injection-target-name",
                        target.getTargetName());
                sb.append("    </injection-target>\n");
            }
            // TODO lookup-name
            sb.append("  </ejb-ref>\n");
        }
        sb.append('\n');

        // ejb-local-ref在Servlet 2.3中引入
        if (getMajorVersion() > 2 || getMinorVersion() > 2) {
            for (ContextLocalEjb ejbLocalRef : ejbLocalRefs.values()) {
                sb.append("  <ejb-local-ref>\n");
                appendElement(sb, INDENT4, "description",
                        ejbLocalRef.getDescription());
                appendElement(sb, INDENT4, "ejb-ref-name", ejbLocalRef.getName());
                appendElement(sb, INDENT4, "ejb-ref-type", ejbLocalRef.getType());
                appendElement(sb, INDENT4, "local-home", ejbLocalRef.getHome());
                appendElement(sb, INDENT4, "local", ejbLocalRef.getLocal());
                appendElement(sb, INDENT4, "ejb-link", ejbLocalRef.getLink());
                // TODO mapped-name
                for (InjectionTarget target : ejbLocalRef.getInjectionTargets()) {
                    sb.append("    <injection-target>\n");
                    appendElement(sb, INDENT6, "injection-target-class",
                            target.getTargetClass());
                    appendElement(sb, INDENT6, "injection-target-name",
                            target.getTargetName());
                    sb.append("    </injection-target>\n");
                }
                // TODO lookup-name
                sb.append("  </ejb-local-ref>\n");
            }
            sb.append('\n');
        }

        // service-ref在Servlet 2.4中引入
        if (getMajorVersion() > 2 || getMinorVersion() > 3) {
            for (ContextService serviceRef : serviceRefs.values()) {
                sb.append("  <service-ref>\n");
                appendElement(sb, INDENT4, "description",
                        serviceRef.getDescription());
                appendElement(sb, INDENT4, "display-name",
                        serviceRef.getDisplayname());
                appendElement(sb, INDENT4, "service-ref-name",
                        serviceRef.getName());
                appendElement(sb, INDENT4, "service-interface",
                        serviceRef.getInterface());
                appendElement(sb, INDENT4, "service-ref-type",
                        serviceRef.getType());
                appendElement(sb, INDENT4, "wsdl-file", serviceRef.getWsdlfile());
                appendElement(sb, INDENT4, "jaxrpc-mapping-file",
                        serviceRef.getJaxrpcmappingfile());
                String qname = serviceRef.getServiceqnameNamespaceURI();
                if (qname != null) {
                    qname = qname + ":";
                }
                qname = qname + serviceRef.getServiceqnameLocalpart();
                appendElement(sb, INDENT4, "service-qname", qname);
                Iterator<String> endpointIter = serviceRef.getServiceendpoints();
                while (endpointIter.hasNext()) {
                    String endpoint = endpointIter.next();
                    sb.append("    <port-component-ref>\n");
                    appendElement(sb, INDENT6, "service-endpoint-interface",
                            endpoint);
                    appendElement(sb, INDENT6, "port-component-link",
                            serviceRef.getProperty(endpoint));
                    sb.append("    </port-component-ref>\n");
                }
                Iterator<String> handlerIter = serviceRef.getHandlers();
                while (handlerIter.hasNext()) {
                    String handler = handlerIter.next();
                    sb.append("    <handler>\n");
                    ContextHandler ch = serviceRef.getHandler(handler);
                    appendElement(sb, INDENT6, "handler-name", ch.getName());
                    appendElement(sb, INDENT6, "handler-class",
                            ch.getHandlerclass());
                    sb.append("    </handler>\n");
                }
                // TODO handler-chains
                // TODO mapped-name
                for (InjectionTarget target : serviceRef.getInjectionTargets()) {
                    sb.append("    <injection-target>\n");
                    appendElement(sb, INDENT6, "injection-target-class",
                            target.getTargetClass());
                    appendElement(sb, INDENT6, "injection-target-name",
                            target.getTargetName());
                    sb.append("    </injection-target>\n");
                }
                // TODO lookup-name
                sb.append("  </service-ref>\n");
            }
            sb.append('\n');
        }

        if (!postConstructMethods.isEmpty()) {
            for (Entry<String, String> entry : postConstructMethods
                    .entrySet()) {
                sb.append("  <post-construct>\n");
                appendElement(sb, INDENT4, "lifecycle-callback-class",
                        entry.getKey());
                appendElement(sb, INDENT4, "lifecycle-callback-method",
                        entry.getValue());
                sb.append("  </post-construct>\n");
            }
            sb.append('\n');
        }

        if (!preDestroyMethods.isEmpty()) {
            for (Entry<String, String> entry : preDestroyMethods
                    .entrySet()) {
                sb.append("  <pre-destroy>\n");
                appendElement(sb, INDENT4, "lifecycle-callback-class",
                        entry.getKey());
                appendElement(sb, INDENT4, "lifecycle-callback-method",
                        entry.getValue());
                sb.append("  </pre-destroy>\n");
            }
            sb.append('\n');
        }

        // 在Servlet 2.4中引入了message-destination-ref，message-destination
        if (getMajorVersion() > 2 || getMinorVersion() > 3) {
            for (MessageDestinationRef mdr : messageDestinationRefs.values()) {
                sb.append("  <message-destination-ref>\n");
                appendElement(sb, INDENT4, "description", mdr.getDescription());
                appendElement(sb, INDENT4, "message-destination-ref-name",
                        mdr.getName());
                appendElement(sb, INDENT4, "message-destination-type",
                        mdr.getType());
                appendElement(sb, INDENT4, "message-destination-usage",
                        mdr.getUsage());
                appendElement(sb, INDENT4, "message-destination-link",
                        mdr.getLink());
                // TODO mapped-name
                for (InjectionTarget target : mdr.getInjectionTargets()) {
                    sb.append("    <injection-target>\n");
                    appendElement(sb, INDENT6, "injection-target-class",
                            target.getTargetClass());
                    appendElement(sb, INDENT6, "injection-target-name",
                            target.getTargetName());
                    sb.append("    </injection-target>\n");
                }
                // TODO lookup-name
                sb.append("  </message-destination-ref>\n");
            }
            sb.append('\n');

            for (MessageDestination md : messageDestinations.values()) {
                sb.append("  <message-destination>\n");
                appendElement(sb, INDENT4, "description", md.getDescription());
                appendElement(sb, INDENT4, "display-name", md.getDisplayName());
                appendElement(sb, INDENT4, "message-destination-name",
                        md.getName());
                // TODO mapped-name
                sb.append("  </message-destination>\n");
            }
            sb.append('\n');
        }

        // locale-encoding-mapping-list是在Servlet 2.4中引入的
        if (getMajorVersion() > 2 || getMinorVersion() > 3) {
            if (localeEncodingMappings.size() > 0) {
                sb.append("  <locale-encoding-mapping-list>\n");
                for (Map.Entry<String, String> entry :
                        localeEncodingMappings.entrySet()) {
                    sb.append("    <locale-encoding-mapping>\n");
                    appendElement(sb, INDENT6, "locale", entry.getKey());
                    appendElement(sb, INDENT6, "encoding", entry.getValue());
                    sb.append("    </locale-encoding-mapping>\n");
                }
                sb.append("  </locale-encoding-mapping-list>\n");
            }
        }

        // 在Servlet 3.1中引入了deny-uncovered-http-methods
        if (getMajorVersion() > 3 ||
                (getMajorVersion() == 3 && getMinorVersion() > 0)) {
            if (denyUncoveredHttpMethods) {
                sb.append("\n");
                sb.append("  <deny-uncovered-http-methods/>");
            }
        }

        sb.append("</web-app>");
        return sb.toString();
    }


    private String encodeUrl(String input) {
        try {
            return URLEncoder.encode(input, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // Impossible. UTF-8是必需的字符集
            return null;
        }
    }


    private static void appendElement(StringBuilder sb, String indent,
            String elementName, String value) {
        if (value == null) {
            return;
        }
        if (value.length() == 0) {
            sb.append(indent);
            sb.append('<');
            sb.append(elementName);
            sb.append("/>\n");
        } else {
            sb.append(indent);
            sb.append('<');
            sb.append(elementName);
            sb.append('>');
            sb.append(Escape.xml(value));
            sb.append("</");
            sb.append(elementName);
            sb.append(">\n");
        }
    }

    private static void appendElement(StringBuilder sb, String indent,
            String elementName, Object value) {
        if (value == null) return;
        appendElement(sb, indent, elementName, value.toString());
    }


    /**
     * 将提供的Web片段合并到此主web.xml中.
     *
     * @param fragments     要合并的片段
     * @return <code>true</code> 合并成功, 否则<code>false</code>
     */
    public boolean merge(Set<WebXml> fragments) {
        // 尽可能按字母顺序处理，以便检查所有内容是否存在

        // 合并规则因元素而异. See SRV.8.2.3

        WebXml temp = new WebXml();

        for (WebXml fragment : fragments) {
            if (!mergeMap(fragment.getContextParams(), contextParams,
                    temp.getContextParams(), fragment, "Context Parameter")) {
                return false;
            }
        }
        contextParams.putAll(temp.getContextParams());

        if (displayName == null) {
            for (WebXml fragment : fragments) {
                String value = fragment.getDisplayName();
                if (value != null) {
                    if (temp.getDisplayName() == null) {
                        temp.setDisplayName(value);
                    } else {
                        log.error(sm.getString(
                                "webXml.mergeConflictDisplayName",
                                fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            displayName = temp.getDisplayName();
        }

        // Note: 片段不允许，但我们也使用片段为每个主机和全局默认值，因此它可能出现在那里
        if (!denyUncoveredHttpMethods) {
            for (WebXml fragment : fragments) {
                if (fragment.getDenyUncoveredHttpMethods()) {
                    denyUncoveredHttpMethods = true;
                    break;
                }
            }
        }

        if (distributable) {
            for (WebXml fragment : fragments) {
                if (!fragment.isDistributable()) {
                    distributable = false;
                    break;
                }
            }
        }

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getEjbLocalRefs(), ejbLocalRefs,
                    temp.getEjbLocalRefs(), fragment)) {
                return false;
            }
        }
        ejbLocalRefs.putAll(temp.getEjbLocalRefs());

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getEjbRefs(), ejbRefs,
                    temp.getEjbRefs(), fragment)) {
                return false;
            }
        }
        ejbRefs.putAll(temp.getEjbRefs());

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getEnvEntries(), envEntries,
                    temp.getEnvEntries(), fragment)) {
                return false;
            }
        }
        envEntries.putAll(temp.getEnvEntries());

        for (WebXml fragment : fragments) {
            if (!mergeMap(fragment.getErrorPages(), errorPages,
                    temp.getErrorPages(), fragment, "Error Page")) {
                return false;
            }
        }
        errorPages.putAll(temp.getErrorPages());

        // 根据Servlet EG的'clarification', 主web.xml中的过滤器定义会覆盖片段中的过滤器定义, 片段中的过滤器定义会覆盖注解中的定义
        List<FilterMap> filterMapsToAdd = new ArrayList<>();
        for (WebXml fragment : fragments) {
            for (FilterMap filterMap : fragment.getFilterMappings()) {
                if (!filterMappingNames.contains(filterMap.getFilterName())) {
                    filterMapsToAdd.add(filterMap);
                }
            }
        }
        for (FilterMap filterMap : filterMapsToAdd) {
            // Additive
            addFilterMapping(filterMap);
        }

        for (WebXml fragment : fragments) {
            for (Map.Entry<String,FilterDef> entry :
                    fragment.getFilters().entrySet()) {
                if (filters.containsKey(entry.getKey())) {
                    mergeFilter(entry.getValue(),
                            filters.get(entry.getKey()), false);
                } else {
                    if (temp.getFilters().containsKey(entry.getKey())) {
                        if (!(mergeFilter(entry.getValue(),
                                temp.getFilters().get(entry.getKey()), true))) {
                            log.error(sm.getString(
                                    "webXml.mergeConflictFilter",
                                    entry.getKey(),
                                    fragment.getName(),
                                    fragment.getURL()));

                            return false;
                        }
                    } else {
                        temp.getFilters().put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        filters.putAll(temp.getFilters());

        for (WebXml fragment : fragments) {
            for (JspPropertyGroup jspPropertyGroup :
                    fragment.getJspPropertyGroups()) {
                // Always additive
                addJspPropertyGroup(jspPropertyGroup);
            }
        }

        for (WebXml fragment : fragments) {
            for (String listener : fragment.getListeners()) {
                // Always additive
                addListener(listener);
            }
        }

        for (WebXml fragment : fragments) {
            if (!mergeMap(fragment.getLocaleEncodingMappings(),
                    localeEncodingMappings, temp.getLocaleEncodingMappings(),
                    fragment, "Locale Encoding Mapping")) {
                return false;
            }
        }
        localeEncodingMappings.putAll(temp.getLocaleEncodingMappings());

        if (getLoginConfig() == null) {
            LoginConfig tempLoginConfig = null;
            for (WebXml fragment : fragments) {
                LoginConfig fragmentLoginConfig = fragment.loginConfig;
                if (fragmentLoginConfig != null) {
                    if (tempLoginConfig == null ||
                            fragmentLoginConfig.equals(tempLoginConfig)) {
                        tempLoginConfig = fragmentLoginConfig;
                    } else {
                        log.error(sm.getString(
                                "webXml.mergeConflictLoginConfig",
                                fragment.getName(),
                                fragment.getURL()));
                    }
                }
            }
            loginConfig = tempLoginConfig;
        }

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getMessageDestinationRefs(), messageDestinationRefs,
                    temp.getMessageDestinationRefs(), fragment)) {
                return false;
            }
        }
        messageDestinationRefs.putAll(temp.getMessageDestinationRefs());

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getMessageDestinations(), messageDestinations,
                    temp.getMessageDestinations(), fragment)) {
                return false;
            }
        }
        messageDestinations.putAll(temp.getMessageDestinations());

        for (WebXml fragment : fragments) {
            if (!mergeMap(fragment.getMimeMappings(), mimeMappings,
                    temp.getMimeMappings(), fragment, "Mime Mapping")) {
                return false;
            }
        }
        mimeMappings.putAll(temp.getMimeMappings());

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getResourceEnvRefs(), resourceEnvRefs,
                    temp.getResourceEnvRefs(), fragment)) {
                return false;
            }
        }
        resourceEnvRefs.putAll(temp.getResourceEnvRefs());

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getResourceRefs(), resourceRefs,
                    temp.getResourceRefs(), fragment)) {
                return false;
            }
        }
        resourceRefs.putAll(temp.getResourceRefs());

        for (WebXml fragment : fragments) {
            for (SecurityConstraint constraint : fragment.getSecurityConstraints()) {
                // Always additive
                addSecurityConstraint(constraint);
            }
        }

        for (WebXml fragment : fragments) {
            for (String role : fragment.getSecurityRoles()) {
                // Always additive
                addSecurityRole(role);
            }
        }

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getServiceRefs(), serviceRefs,
                    temp.getServiceRefs(), fragment)) {
                return false;
            }
        }
        serviceRefs.putAll(temp.getServiceRefs());

        // 根据Servlet EG的'clarification', 主web.xml中的过滤器定义会覆盖片段中的过滤器定义, 片段中的过滤器定义会覆盖注解中的定义
        //从web.xml中定义的片段中跳过servlet定义和映射
        List<Map.Entry<String,String>> servletMappingsToAdd = new ArrayList<>();
        for (WebXml fragment : fragments) {
            for (Map.Entry<String,String> servletMap :
                    fragment.getServletMappings().entrySet()) {
                if (!servletMappingNames.contains(servletMap.getValue()) &&
                        !servletMappings.containsKey(servletMap.getKey())) {
                    servletMappingsToAdd.add(servletMap);
                }
            }
        }

        // Add fragment mappings
        for (Map.Entry<String,String> mapping : servletMappingsToAdd) {
            addServletMappingDecoded(mapping.getKey(), mapping.getValue());
        }

        for (WebXml fragment : fragments) {
            for (Map.Entry<String,ServletDef> entry :
                    fragment.getServlets().entrySet()) {
                if (servlets.containsKey(entry.getKey())) {
                    mergeServlet(entry.getValue(),
                            servlets.get(entry.getKey()), false);
                } else {
                    if (temp.getServlets().containsKey(entry.getKey())) {
                        if (!(mergeServlet(entry.getValue(),
                                temp.getServlets().get(entry.getKey()), true))) {
                            log.error(sm.getString(
                                    "webXml.mergeConflictServlet",
                                    entry.getKey(),
                                    fragment.getName(),
                                    fragment.getURL()));

                            return false;
                        }
                    } else {
                        temp.getServlets().put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        servlets.putAll(temp.getServlets());

        if (sessionConfig.getSessionTimeout() == null) {
            for (WebXml fragment : fragments) {
                Integer value = fragment.getSessionConfig().getSessionTimeout();
                if (value != null) {
                    if (temp.getSessionConfig().getSessionTimeout() == null) {
                        temp.getSessionConfig().setSessionTimeout(value.toString());
                    } else if (value.equals(
                            temp.getSessionConfig().getSessionTimeout())) {
                        // 片段使用相同的值 - 没有冲突
                    } else {
                        log.error(sm.getString(
                                "webXml.mergeConflictSessionTimeout",
                                fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            if (temp.getSessionConfig().getSessionTimeout() != null) {
                sessionConfig.setSessionTimeout(
                        temp.getSessionConfig().getSessionTimeout().toString());
            }
        }

        if (sessionConfig.getCookieName() == null) {
            for (WebXml fragment : fragments) {
                String value = fragment.getSessionConfig().getCookieName();
                if (value != null) {
                    if (temp.getSessionConfig().getCookieName() == null) {
                        temp.getSessionConfig().setCookieName(value);
                    } else if (value.equals(
                            temp.getSessionConfig().getCookieName())) {
                        // 片段使用相同的值 - 没有冲突
                    } else {
                        log.error(sm.getString(
                                "webXml.mergeConflictSessionCookieName",
                                fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            sessionConfig.setCookieName(
                    temp.getSessionConfig().getCookieName());
        }
        if (sessionConfig.getCookieDomain() == null) {
            for (WebXml fragment : fragments) {
                String value = fragment.getSessionConfig().getCookieDomain();
                if (value != null) {
                    if (temp.getSessionConfig().getCookieDomain() == null) {
                        temp.getSessionConfig().setCookieDomain(value);
                    } else if (value.equals(
                            temp.getSessionConfig().getCookieDomain())) {
                        // 片段使用相同的值 - 没有冲突
                    } else {
                        log.error(sm.getString(
                                "webXml.mergeConflictSessionCookieDomain",
                                fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            sessionConfig.setCookieDomain(
                    temp.getSessionConfig().getCookieDomain());
        }
        if (sessionConfig.getCookiePath() == null) {
            for (WebXml fragment : fragments) {
                String value = fragment.getSessionConfig().getCookiePath();
                if (value != null) {
                    if (temp.getSessionConfig().getCookiePath() == null) {
                        temp.getSessionConfig().setCookiePath(value);
                    } else if (value.equals(
                            temp.getSessionConfig().getCookiePath())) {
                        // 片段使用相同的值 - 没有冲突
                    } else {
                        log.error(sm.getString(
                                "webXml.mergeConflictSessionCookiePath",
                                fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            sessionConfig.setCookiePath(
                    temp.getSessionConfig().getCookiePath());
        }
        if (sessionConfig.getCookieComment() == null) {
            for (WebXml fragment : fragments) {
                String value = fragment.getSessionConfig().getCookieComment();
                if (value != null) {
                    if (temp.getSessionConfig().getCookieComment() == null) {
                        temp.getSessionConfig().setCookieComment(value);
                    } else if (value.equals(
                            temp.getSessionConfig().getCookieComment())) {
                        // 片段使用相同的值 - 没有冲突
                    } else {
                        log.error(sm.getString(
                                "webXml.mergeConflictSessionCookieComment",
                                fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            sessionConfig.setCookieComment(
                    temp.getSessionConfig().getCookieComment());
        }
        if (sessionConfig.getCookieHttpOnly() == null) {
            for (WebXml fragment : fragments) {
                Boolean value = fragment.getSessionConfig().getCookieHttpOnly();
                if (value != null) {
                    if (temp.getSessionConfig().getCookieHttpOnly() == null) {
                        temp.getSessionConfig().setCookieHttpOnly(value.toString());
                    } else if (value.equals(
                            temp.getSessionConfig().getCookieHttpOnly())) {
                        // 片段使用相同的值 - 没有冲突
                    } else {
                        log.error(sm.getString(
                                "webXml.mergeConflictSessionCookieHttpOnly",
                                fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            if (temp.getSessionConfig().getCookieHttpOnly() != null) {
                sessionConfig.setCookieHttpOnly(
                        temp.getSessionConfig().getCookieHttpOnly().toString());
            }
        }
        if (sessionConfig.getCookieSecure() == null) {
            for (WebXml fragment : fragments) {
                Boolean value = fragment.getSessionConfig().getCookieSecure();
                if (value != null) {
                    if (temp.getSessionConfig().getCookieSecure() == null) {
                        temp.getSessionConfig().setCookieSecure(value.toString());
                    } else if (value.equals(
                            temp.getSessionConfig().getCookieSecure())) {
                        // 片段使用相同的值 - 没有冲突
                    } else {
                        log.error(sm.getString(
                                "webXml.mergeConflictSessionCookieSecure",
                                fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            if (temp.getSessionConfig().getCookieSecure() != null) {
                sessionConfig.setCookieSecure(
                        temp.getSessionConfig().getCookieSecure().toString());
            }
        }
        if (sessionConfig.getCookieMaxAge() == null) {
            for (WebXml fragment : fragments) {
                Integer value = fragment.getSessionConfig().getCookieMaxAge();
                if (value != null) {
                    if (temp.getSessionConfig().getCookieMaxAge() == null) {
                        temp.getSessionConfig().setCookieMaxAge(value.toString());
                    } else if (value.equals(
                            temp.getSessionConfig().getCookieMaxAge())) {
                        // 片段使用相同的值 - 没有冲突
                    } else {
                        log.error(sm.getString(
                                "webXml.mergeConflictSessionCookieMaxAge",
                                fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            if (temp.getSessionConfig().getCookieMaxAge() != null) {
                sessionConfig.setCookieMaxAge(
                        temp.getSessionConfig().getCookieMaxAge().toString());
            }
        }

        if (sessionConfig.getSessionTrackingModes().size() == 0) {
            for (WebXml fragment : fragments) {
                EnumSet<SessionTrackingMode> value =
                    fragment.getSessionConfig().getSessionTrackingModes();
                if (value.size() > 0) {
                    if (temp.getSessionConfig().getSessionTrackingModes().size() == 0) {
                        temp.getSessionConfig().getSessionTrackingModes().addAll(value);
                    } else if (value.equals(
                            temp.getSessionConfig().getSessionTrackingModes())) {
                        // 片段使用相同的值 - 没有冲突
                    } else {
                        log.error(sm.getString(
                                "webXml.mergeConflictSessionTrackingMode",
                                fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            sessionConfig.getSessionTrackingModes().addAll(
                    temp.getSessionConfig().getSessionTrackingModes());
        }

        for (WebXml fragment : fragments) {
            if (!mergeMap(fragment.getTaglibs(), taglibs,
                    temp.getTaglibs(), fragment, "Taglibs")) {
                return false;
            }
        }
        taglibs.putAll(temp.getTaglibs());

        for (WebXml fragment : fragments) {
            if (fragment.alwaysAddWelcomeFiles || welcomeFiles.size() == 0) {
                for (String welcomeFile : fragment.getWelcomeFiles()) {
                    addWelcomeFile(welcomeFile);
                }
            }
        }

        if (postConstructMethods.isEmpty()) {
            for (WebXml fragment : fragments) {
                if (!mergeLifecycleCallback(fragment.getPostConstructMethods(),
                        temp.getPostConstructMethods(), fragment,
                        "Post Construct Methods")) {
                    return false;
                }
            }
            postConstructMethods.putAll(temp.getPostConstructMethods());
        }

        if (preDestroyMethods.isEmpty()) {
            for (WebXml fragment : fragments) {
                if (!mergeLifecycleCallback(fragment.getPreDestroyMethods(),
                        temp.getPreDestroyMethods(), fragment,
                        "Pre Destroy Methods")) {
                    return false;
                }
            }
            preDestroyMethods.putAll(temp.getPreDestroyMethods());
        }

        return true;
    }

    private static <T extends ResourceBase> boolean mergeResourceMap(
            Map<String, T> fragmentResources, Map<String, T> mainResources,
            Map<String, T> tempResources, WebXml fragment) {
        for (T resource : fragmentResources.values()) {
            String resourceName = resource.getName();
            if (mainResources.containsKey(resourceName)) {
                mainResources.get(resourceName).getInjectionTargets().addAll(
                        resource.getInjectionTargets());
            } else {
                // 未在主web.xml中定义
                T existingResource = tempResources.get(resourceName);
                if (existingResource != null) {
                    if (!existingResource.equals(resource)) {
                        log.error(sm.getString(
                                "webXml.mergeConflictResource",
                                resourceName,
                                fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                } else {
                    tempResources.put(resourceName, resource);
                }
            }
        }
        return true;
    }

    private static <T> boolean mergeMap(Map<String,T> fragmentMap,
            Map<String,T> mainMap, Map<String,T> tempMap, WebXml fragment,
            String mapName) {
        for (Entry<String, T> entry : fragmentMap.entrySet()) {
            final String key = entry.getKey();
            if (!mainMap.containsKey(key)) {
                // 未在主web.xml中定义
                T value = entry.getValue();
                if (tempMap.containsKey(key)) {
                    if (value != null && !value.equals(
                            tempMap.get(key))) {
                        log.error(sm.getString(
                                "webXml.mergeConflictString",
                                mapName,
                                key,
                                fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                } else {
                    tempMap.put(key, value);
                }
            }
        }
        return true;
    }

    private static boolean mergeFilter(FilterDef src, FilterDef dest,
            boolean failOnConflict) {
        if (dest.getAsyncSupported() == null) {
            dest.setAsyncSupported(src.getAsyncSupported());
        } else if (src.getAsyncSupported() != null) {
            if (failOnConflict &&
                    !src.getAsyncSupported().equals(dest.getAsyncSupported())) {
                return false;
            }
        }

        if (dest.getFilterClass()  == null) {
            dest.setFilterClass(src.getFilterClass());
        } else if (src.getFilterClass() != null) {
            if (failOnConflict &&
                    !src.getFilterClass().equals(dest.getFilterClass())) {
                return false;
            }
        }

        for (Map.Entry<String,String> srcEntry :
                src.getParameterMap().entrySet()) {
            if (dest.getParameterMap().containsKey(srcEntry.getKey())) {
                if (failOnConflict && !dest.getParameterMap().get(
                        srcEntry.getKey()).equals(srcEntry.getValue())) {
                    return false;
                }
            } else {
                dest.addInitParameter(srcEntry.getKey(), srcEntry.getValue());
            }
        }
        return true;
    }

    private static boolean mergeServlet(ServletDef src, ServletDef dest,
            boolean failOnConflict) {
        // These tests should be unnecessary...
        if (dest.getServletClass() != null && dest.getJspFile() != null) {
            return false;
        }
        if (src.getServletClass() != null && src.getJspFile() != null) {
            return false;
        }


        if (dest.getServletClass() == null && dest.getJspFile() == null) {
            dest.setServletClass(src.getServletClass());
            dest.setJspFile(src.getJspFile());
        } else if (failOnConflict) {
            if (src.getServletClass() != null &&
                    (dest.getJspFile() != null ||
                            !src.getServletClass().equals(dest.getServletClass()))) {
                return false;
            }
            if (src.getJspFile() != null &&
                    (dest.getServletClass() != null ||
                            !src.getJspFile().equals(dest.getJspFile()))) {
                return false;
            }
        }

        // Additive
        for (SecurityRoleRef securityRoleRef : src.getSecurityRoleRefs()) {
            dest.addSecurityRoleRef(securityRoleRef);
        }

        if (dest.getLoadOnStartup() == null) {
            if (src.getLoadOnStartup() != null) {
                dest.setLoadOnStartup(src.getLoadOnStartup().toString());
            }
        } else if (src.getLoadOnStartup() != null) {
            if (failOnConflict &&
                    !src.getLoadOnStartup().equals(dest.getLoadOnStartup())) {
                return false;
            }
        }

        if (dest.getEnabled() == null) {
            if (src.getEnabled() != null) {
                dest.setEnabled(src.getEnabled().toString());
            }
        } else if (src.getEnabled() != null) {
            if (failOnConflict &&
                    !src.getEnabled().equals(dest.getEnabled())) {
                return false;
            }
        }

        for (Map.Entry<String,String> srcEntry :
                src.getParameterMap().entrySet()) {
            if (dest.getParameterMap().containsKey(srcEntry.getKey())) {
                if (failOnConflict && !dest.getParameterMap().get(
                        srcEntry.getKey()).equals(srcEntry.getValue())) {
                    return false;
                }
            } else {
                dest.addInitParameter(srcEntry.getKey(), srcEntry.getValue());
            }
        }

        if (dest.getMultipartDef() == null) {
            dest.setMultipartDef(src.getMultipartDef());
        } else if (src.getMultipartDef() != null) {
            return mergeMultipartDef(src.getMultipartDef(),
                    dest.getMultipartDef(), failOnConflict);
        }

        if (dest.getAsyncSupported() == null) {
            if (src.getAsyncSupported() != null) {
                dest.setAsyncSupported(src.getAsyncSupported().toString());
            }
        } else if (src.getAsyncSupported() != null) {
            if (failOnConflict &&
                    !src.getAsyncSupported().equals(dest.getAsyncSupported())) {
                return false;
            }
        }

        return true;
    }

    private static boolean mergeMultipartDef(MultipartDef src, MultipartDef dest,
            boolean failOnConflict) {

        if (dest.getLocation() == null) {
            dest.setLocation(src.getLocation());
        } else if (src.getLocation() != null) {
            if (failOnConflict &&
                    !src.getLocation().equals(dest.getLocation())) {
                return false;
            }
        }

        if (dest.getFileSizeThreshold() == null) {
            dest.setFileSizeThreshold(src.getFileSizeThreshold());
        } else if (src.getFileSizeThreshold() != null) {
            if (failOnConflict &&
                    !src.getFileSizeThreshold().equals(
                            dest.getFileSizeThreshold())) {
                return false;
            }
        }

        if (dest.getMaxFileSize() == null) {
            dest.setMaxFileSize(src.getMaxFileSize());
        } else if (src.getMaxFileSize() != null) {
            if (failOnConflict &&
                    !src.getMaxFileSize().equals(dest.getMaxFileSize())) {
                return false;
            }
        }

        if (dest.getMaxRequestSize() == null) {
            dest.setMaxRequestSize(src.getMaxRequestSize());
        } else if (src.getMaxRequestSize() != null) {
            if (failOnConflict &&
                    !src.getMaxRequestSize().equals(
                            dest.getMaxRequestSize())) {
                return false;
            }
        }

        return true;
    }


    private static boolean mergeLifecycleCallback(
            Map<String, String> fragmentMap, Map<String, String> tempMap,
            WebXml fragment, String mapName) {
        for (Entry<String, String> entry : fragmentMap.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            if (tempMap.containsKey(key)) {
                if (value != null && !value.equals(tempMap.get(key))) {
                    log.error(sm.getString("webXml.mergeConflictString",
                            mapName, key, fragment.getName(), fragment.getURL()));
                    return false;
                }
            } else {
                tempMap.put(key, value);
            }
        }
        return true;
    }


    /**
     * 按照Servlet规范中的规则，生成要按顺序处理的web-fragment.xml文件的子集.
     *
     * @param application    应用程序web.xml文件
     * @param fragments      片段名称到Web片段的映射
     * @param servletContext 片段与之关联的servlet上下文
     * 
     * @return 要处理的web-fragment.xml文件的有序列表
     */
    public static Set<WebXml> orderWebFragments(WebXml application,
            Map<String,WebXml> fragments, ServletContext servletContext) {

        Set<WebXml> orderedFragments = new LinkedHashSet<>();

        boolean absoluteOrdering =
            (application.getAbsoluteOrdering() != null);
        boolean orderingPresent = false;

        if (absoluteOrdering) {
            orderingPresent = true;
            // 只应处理列出的那些片段
            Set<String> requestedOrder = application.getAbsoluteOrdering();

            for (String requestedName : requestedOrder) {
                if (WebXml.ORDER_OTHERS.equals(requestedName)) {
                    // 此时添加所有未明确命名的片段
                    for (Entry<String, WebXml> entry : fragments.entrySet()) {
                        if (!requestedOrder.contains(entry.getKey())) {
                            WebXml fragment = entry.getValue();
                            if (fragment != null) {
                                orderedFragments.add(fragment);
                            }
                        }
                    }
                } else {
                    WebXml fragment = fragments.get(requestedName);
                    if (fragment != null) {
                        orderedFragments.add(fragment);
                    } else {
                        log.warn(sm.getString("webXml.wrongFragmentName",requestedName));
                    }
                }
            }
        } else {
            // Stage 1. 使所有依赖项成为双向的 - 这使下一阶段更简单.
            for (WebXml fragment : fragments.values()) {
                Iterator<String> before =
                        fragment.getBeforeOrdering().iterator();
                while (before.hasNext()) {
                    orderingPresent = true;
                    String beforeEntry = before.next();
                    if (!beforeEntry.equals(ORDER_OTHERS)) {
                        WebXml beforeFragment = fragments.get(beforeEntry);
                        if (beforeFragment == null) {
                            before.remove();
                        } else {
                            beforeFragment.addAfterOrdering(fragment.getName());
                        }
                    }
                }
                Iterator<String> after = fragment.getAfterOrdering().iterator();
                while (after.hasNext()) {
                    orderingPresent = true;
                    String afterEntry = after.next();
                    if (!afterEntry.equals(ORDER_OTHERS)) {
                        WebXml afterFragment = fragments.get(afterEntry);
                        if (afterFragment == null) {
                            after.remove();
                        } else {
                            afterFragment.addBeforeOrdering(fragment.getName());
                        }
                    }
                }
            }

            // Stage 2. 明确地隐藏在其他之前/之后的所有片段. 这是迭代的，所以下一阶段不必如此.
            for (WebXml fragment : fragments.values()) {
                if (fragment.getBeforeOrdering().contains(ORDER_OTHERS)) {
                    makeBeforeOthersExplicit(fragment.getAfterOrdering(), fragments);
                }
                if (fragment.getAfterOrdering().contains(ORDER_OTHERS)) {
                    makeAfterOthersExplicit(fragment.getBeforeOrdering(), fragments);
                }
            }

            // Stage 3. 分为三组
            Set<WebXml> beforeSet = new HashSet<>();
            Set<WebXml> othersSet = new HashSet<>();
            Set<WebXml> afterSet = new HashSet<>();

            for (WebXml fragment : fragments.values()) {
                if (fragment.getBeforeOrdering().contains(ORDER_OTHERS)) {
                    beforeSet.add(fragment);
                    fragment.getBeforeOrdering().remove(ORDER_OTHERS);
                } else if (fragment.getAfterOrdering().contains(ORDER_OTHERS)) {
                    afterSet.add(fragment);
                    fragment.getAfterOrdering().remove(ORDER_OTHERS);
                } else {
                    othersSet.add(fragment);
                }
            }

            // Stage 4. 对组进行去耦，使组中每个片段的排序要求仅引用组中的其他片段. 组外的排序要求将通过按顺序处理组来处理.
            //          Note: 只有在排序要求后才考虑. 由于第1阶段的处理，这没问题.
            decoupleOtherGroups(beforeSet);
            decoupleOtherGroups(othersSet);
            decoupleOtherGroups(afterSet);

            // Stage 5. 排序每个组
            //          Note: 只有在排序要求后才考虑. 由于第1阶段的处理，这没问题.
            orderFragments(orderedFragments, beforeSet);
            orderFragments(orderedFragments, othersSet);
            orderFragments(orderedFragments, afterSet);
        }

        // 始终包含容器片段
        Set<WebXml> containerFragments = new LinkedHashSet<>();
        // 找到所有容器片段并从有序列表中删除
        for (WebXml fragment : fragments.values()) {
            if (!fragment.getWebappJar()) {
                containerFragments.add(fragment);
                orderedFragments.remove(fragment);
            }
        }

        // 单元测试时避免使用NPE
        if (servletContext != null) {
            // 发布有序片段. 该应用程序不需要知道容器片段
            List<String> orderedJarFileNames = null;
            if (orderingPresent) {
                orderedJarFileNames = new ArrayList<>();
                for (WebXml fragment: orderedFragments) {
                    orderedJarFileNames.add(fragment.getJarName());
                }
            }
            servletContext.setAttribute(ServletContext.ORDERED_LIBS,
                    orderedJarFileNames);
        }

        // 处理的其余部分需要知道容器片段
        if (containerFragments.size() > 0) {
            Set<WebXml> result = new LinkedHashSet<>();
            if (containerFragments.iterator().next().getDelegate()) {
                result.addAll(containerFragments);
                result.addAll(orderedFragments);
            } else {
                result.addAll(orderedFragments);
                result.addAll(containerFragments);
            }
            return result;
        } else {
            return orderedFragments;
        }
    }

    private static void decoupleOtherGroups(Set<WebXml> group) {
        Set<String> names = new HashSet<>();
        for (WebXml fragment : group) {
            names.add(fragment.getName());
        }
        for (WebXml fragment : group) {
            Iterator<String> after = fragment.getAfterOrdering().iterator();
            while (after.hasNext()) {
                String entry = after.next();
                if (!names.contains(entry)) {
                    after.remove();
                }
            }
        }
    }
    private static void orderFragments(Set<WebXml> orderedFragments,
            Set<WebXml> unordered) {
        Set<WebXml> addedThisRound = new HashSet<>();
        Set<WebXml> addedLastRound = new HashSet<>();
        while (unordered.size() > 0) {
            Iterator<WebXml> source = unordered.iterator();
            while (source.hasNext()) {
                WebXml fragment = source.next();
                for (WebXml toRemove : addedLastRound) {
                    fragment.getAfterOrdering().remove(toRemove.getName());
                }
                if (fragment.getAfterOrdering().isEmpty()) {
                    addedThisRound.add(fragment);
                    orderedFragments.add(fragment);
                    source.remove();
                }
            }
            if (addedThisRound.size() == 0) {
                // Circular
                throw new IllegalArgumentException(
                        sm.getString("webXml.mergeConflictOrder"));
            }
            addedLastRound.clear();
            addedLastRound.addAll(addedThisRound);
            addedThisRound.clear();
        }
    }

    private static void makeBeforeOthersExplicit(Set<String> beforeOrdering,
            Map<String, WebXml> fragments) {
        for (String before : beforeOrdering) {
            if (!before.equals(ORDER_OTHERS)) {
                WebXml webXml = fragments.get(before);
                if (!webXml.getBeforeOrdering().contains(ORDER_OTHERS)) {
                    webXml.addBeforeOrderingOthers();
                    makeBeforeOthersExplicit(webXml.getAfterOrdering(), fragments);
                }
            }
        }
    }

    private static void makeAfterOthersExplicit(Set<String> afterOrdering,
            Map<String, WebXml> fragments) {
        for (String after : afterOrdering) {
            if (!after.equals(ORDER_OTHERS)) {
                WebXml webXml = fragments.get(after);
                if (!webXml.getAfterOrdering().contains(ORDER_OTHERS)) {
                    webXml.addAfterOrderingOthers();
                    makeAfterOthersExplicit(webXml.getBeforeOrdering(), fragments);
                }
            }
        }
    }
}
