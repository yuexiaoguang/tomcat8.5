package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.tomcat.util.res.StringManager;


/**
 * 表示Web应用程序的servlet定义, 作为部署描述符中<code>&lt;servlet&gt;</code>元素的表示.
 */
public class ServletDef implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final StringManager sm =
        StringManager.getManager(Constants.PACKAGE_NAME);

    // ------------------------------------------------------------- Properties


    /**
     * 这个servlet的描述.
     */
    private String description = null;

    public String getDescription() {
        return (this.description);
    }

    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * 此servlet的显示名称.
     */
    private String displayName = null;

    public String getDisplayName() {
        return (this.displayName);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }


    /**
     * 此servlet关联的小图标.
     */
    private String smallIcon = null;

    public String getSmallIcon() {
        return (this.smallIcon);
    }

    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }

    /**
     * 此servlet关联的大图标.
     */
    private String largeIcon = null;

    public String getLargeIcon() {
        return (this.largeIcon);
    }

    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }


    /**
     * 此servlet的名称，在为特定Web应用程序定义的servlet中必须是唯一的.
     */
    private String servletName = null;

    public String getServletName() {
        return (this.servletName);
    }

    public void setServletName(String servletName) {
        if (servletName == null || servletName.equals("")) {
            throw new IllegalArgumentException(
                    sm.getString("servletDef.invalidServletName", servletName));
        }
        this.servletName = servletName;
    }


    /**
     * 实现此servlet的Java类的标准名称.
     */
    private String servletClass = null;

    public String getServletClass() {
        return (this.servletClass);
    }

    public void setServletClass(String servletClass) {
        this.servletClass = servletClass;
    }


    /**
     * 此servlet定义适用的JSP文件的名称
     */
    private String jspFile = null;

    public String getJspFile() {
        return (this.jspFile);
    }

    public void setJspFile(String jspFile) {
        this.jspFile = jspFile;
    }


    /**
     * 此servlet的初始化参数集, 使用参数名的 Key.
     */
    private final Map<String, String> parameters = new HashMap<>();

    public Map<String, String> getParameterMap() {
        return (this.parameters);
    }

    /**
     * 将初始化参数添加到与此servlet关联的参数集.
     *
     * @param name 初始化参数名
     * @param value 初始化参数值
     */
    public void addInitParameter(String name, String value) {

        if (parameters.containsKey(name)) {
            // 规范没有定义这个，但TCK期望第一个定义优先
            return;
        }
        parameters.put(name, value);
    }

    /**
     * 此servlet的 load-on-startup 顺序
     */
    private Integer loadOnStartup = null;

    public Integer getLoadOnStartup() {
        return (this.loadOnStartup);
    }

    public void setLoadOnStartup(String loadOnStartup) {
        this.loadOnStartup = Integer.valueOf(loadOnStartup);
    }


    /**
     * 此servlet的run-as配置
     */
    private String runAs = null;

    public String getRunAs() {
        return (this.runAs);
    }

    public void setRunAs(String runAs) {
        this.runAs = runAs;
    }


    /**
     * 此servlet的 security-role-ref 集
     */
    private final Set<SecurityRoleRef> securityRoleRefs = new HashSet<>();

    public Set<SecurityRoleRef> getSecurityRoleRefs() {
        return (this.securityRoleRefs);
    }

    /**
     * 添加一个 security-role-ref 到此servlet关联的 security-role-ref 集中.
     * 
     * @param securityRoleRef 安全角色
     */
    public void addSecurityRoleRef(SecurityRoleRef securityRoleRef) {
        securityRoleRefs.add(securityRoleRef);
    }

    /**
     * 此servlet的多部分配置
     */
    private MultipartDef multipartDef = null;

    public MultipartDef getMultipartDef() {
        return this.multipartDef;
    }

    public void setMultipartDef(MultipartDef multipartDef) {
        this.multipartDef = multipartDef;
    }


    /**
     * 这个servlet是否支持异步.
     */
    private Boolean asyncSupported = null;

    public Boolean getAsyncSupported() {
        return this.asyncSupported;
    }

    public void setAsyncSupported(String asyncSupported) {
        this.asyncSupported = Boolean.valueOf(asyncSupported);
    }


    /**
     * 是否启用了此servlet.
     */
    private Boolean enabled = null;

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(String enabled) {
        this.enabled = Boolean.valueOf(enabled);
    }


    /**
     * 这个ServletDef可以被SCI覆盖吗?
     */
    private boolean overridable = false;

    public boolean isOverridable() {
        return overridable;
    }

    public void setOverridable(boolean overridable) {
        this.overridable = overridable;
    }
}
