package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;

import org.apache.tomcat.util.res.StringManager;


/**
 * 表示Web应用程序的过滤器定义, 作为部署描述符中<code>&lt;filter&gt;</code>元素的定义.
 */
public class FilterDef implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final StringManager sm =
        StringManager.getManager(Constants.PACKAGE_NAME);

    // ------------------------------------------------------------- Properties


    /**
     * 此过滤器的描述.
     */
    private String description = null;

    public String getDescription() {
        return (this.description);
    }

    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * 此过滤器的显示名称.
     */
    private String displayName = null;

    public String getDisplayName() {
        return (this.displayName);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }


    /**
     * 与此定义关联的过滤器实例
     */
    private transient Filter filter = null;

    public Filter getFilter() {
        return filter;
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }


    /**
     * 实现此过滤器的Java类的标准名称.
     */
    private String filterClass = null;

    public String getFilterClass() {
        return (this.filterClass);
    }

    public void setFilterClass(String filterClass) {
        this.filterClass = filterClass;
    }


    /**
     * 此过滤器的名称, 在为特定Web应用程序定义的过滤器中必须是唯一的.
     */
    private String filterName = null;

    public String getFilterName() {
        return (this.filterName);
    }

    public void setFilterName(String filterName) {
        if (filterName == null || filterName.equals("")) {
            throw new IllegalArgumentException(
                    sm.getString("filterDef.invalidFilterName", filterName));
        }
        this.filterName = filterName;
    }


    /**
     * 与此过滤器关联的大图标.
     */
    private String largeIcon = null;

    public String getLargeIcon() {
        return (this.largeIcon);
    }

    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }


    /**
     * 此过滤器的初始化参数集, 使用参数名称作为 Key.
     */
    private final Map<String, String> parameters = new HashMap<>();

    public Map<String, String> getParameterMap() {

        return (this.parameters);

    }


    /**
     * 与此过滤器关联的小图标.
     */
    private String smallIcon = null;

    public String getSmallIcon() {
        return (this.smallIcon);
    }

    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }

    private String asyncSupported = null;

    public String getAsyncSupported() {
        return asyncSupported;
    }

    public void setAsyncSupported(String asyncSupported) {
        this.asyncSupported = asyncSupported;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 将初始化参数添加到与此过滤器关联的参数集.
     *
     * @param name 初始化参数名称
     * @param value 初始化参数值
     */
    public void addInitParameter(String name, String value) {

        if (parameters.containsKey(name)) {
            // 规范没有定义这个，但TCK期望第一个定义优先
            return;
        }
        parameters.put(name, value);

    }


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("FilterDef[");
        sb.append("filterName=");
        sb.append(this.filterName);
        sb.append(", filterClass=");
        sb.append(this.filterClass);
        sb.append("]");
        return (sb.toString());
    }
}
