package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Locale;

import javax.servlet.DispatcherType;

import org.apache.tomcat.util.buf.UDecoder;

/**
 * 表示Web应用程序的过滤器映射, 作为部署描述符中<code>&lt;filter-mapping&gt;</code>元素的表示.
 * 每个过滤器映射必须包含过滤器名称, 以及URL模式或servlet名称.
 */
public class FilterMap extends XmlEncodingBase implements Serializable {


    // ------------------------------------------------------------- Properties


    private static final long serialVersionUID = 1L;

    /**
     * 此映射与特定请求匹配时要执行的过滤器的名称.
     */

    public static final int ERROR = 1;
    public static final int FORWARD = 2;
    public static final int INCLUDE = 4;
    public static final int REQUEST = 8;
    public static final int ASYNC = 16;

    // 表示什么都没有设置. 这将被视为等于REQUEST
    private static final int NOT_SET = 0;

    private int dispatcherMapping = NOT_SET;

    private String filterName = null;

    public String getFilterName() {
        return (this.filterName);
    }

    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }


    /**
     * 此映射匹配的servlet名称.
     */
    private String[] servletNames = new String[0];

    public String[] getServletNames() {
        if (matchAllServletNames) {
            return new String[] {};
        } else {
            return (this.servletNames);
        }
    }

    public void addServletName(String servletName) {
        if ("*".equals(servletName)) {
            this.matchAllServletNames = true;
        } else {
            String[] results = new String[servletNames.length + 1];
            System.arraycopy(servletNames, 0, results, 0, servletNames.length);
            results[servletNames.length] = servletName;
            servletNames = results;
        }
    }


    /**
     * 此映射将匹配所有url模式
     */
    private boolean matchAllUrlPatterns = false;

    public boolean getMatchAllUrlPatterns() {
        return matchAllUrlPatterns;
    }


    /**
     * 此映射将匹配所有servlet名称
     */
    private boolean matchAllServletNames = false;

    public boolean getMatchAllServletNames() {
        return matchAllServletNames;
    }


    /**
     * 此映射匹配的URL模式.
     */
    private String[] urlPatterns = new String[0];

    public String[] getURLPatterns() {
        if (matchAllUrlPatterns) {
            return new String[] {};
        } else {
            return (this.urlPatterns);
        }
    }

    public void addURLPattern(String urlPattern) {
        addURLPatternDecoded(UDecoder.URLDecode(urlPattern, getCharset()));
    }
    public void addURLPatternDecoded(String urlPattern) {
        if ("*".equals(urlPattern)) {
            this.matchAllUrlPatterns = true;
        } else {
            String[] results = new String[urlPatterns.length + 1];
            System.arraycopy(urlPatterns, 0, results, 0, urlPatterns.length);
            results[urlPatterns.length] = UDecoder.URLDecode(urlPattern);
            urlPatterns = results;
        }
    }

    /**
     * 此方法将用于设置FilterMap的当前状态，表示应该应用的过滤器的状态.
     * 
     * @param dispatcherString 应与此过滤器匹配的调度程序类型
     */
    public void setDispatcher(String dispatcherString) {
        String dispatcher = dispatcherString.toUpperCase(Locale.ENGLISH);

        if (dispatcher.equals(DispatcherType.FORWARD.name())) {
            // apply FORWARD to the global dispatcherMapping.
            dispatcherMapping |= FORWARD;
        } else if (dispatcher.equals(DispatcherType.INCLUDE.name())) {
            // apply INCLUDE to the global dispatcherMapping.
            dispatcherMapping |= INCLUDE;
        } else if (dispatcher.equals(DispatcherType.REQUEST.name())) {
            // apply REQUEST to the global dispatcherMapping.
            dispatcherMapping |= REQUEST;
        }  else if (dispatcher.equals(DispatcherType.ERROR.name())) {
            // apply ERROR to the global dispatcherMapping.
            dispatcherMapping |= ERROR;
        }  else if (dispatcher.equals(DispatcherType.ASYNC.name())) {
            // apply ERROR to the global dispatcherMapping.
            dispatcherMapping |= ASYNC;
        }
    }

    public int getDispatcherMapping() {
        // per the SRV.6.2.5 absence of any dispatcher elements is
        // equivalent to a REQUEST value
        if (dispatcherMapping == NOT_SET) return REQUEST;

        return dispatcherMapping;
    }

    public String[] getDispatcherNames() {
        ArrayList<String> result = new ArrayList<>();
        if ((dispatcherMapping & FORWARD) != 0) {
            result.add(DispatcherType.FORWARD.name());
        }
        if ((dispatcherMapping & INCLUDE) != 0) {
            result.add(DispatcherType.INCLUDE.name());
        }
        if ((dispatcherMapping & REQUEST) != 0) {
            result.add(DispatcherType.REQUEST.name());
        }
        if ((dispatcherMapping & ERROR) != 0) {
            result.add(DispatcherType.ERROR.name());
        }
        if ((dispatcherMapping & ASYNC) != 0) {
            result.add(DispatcherType.ASYNC.name());
        }
        return result.toArray(new String[result.size()]);
    }

    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("FilterMap[");
        sb.append("filterName=");
        sb.append(this.filterName);
        for (int i = 0; i < servletNames.length; i++) {
            sb.append(", servletName=");
            sb.append(servletNames[i]);
        }
        for (int i = 0; i < urlPatterns.length; i++) {
            sb.append(", urlPattern=");
            sb.append(urlPatterns[i]);
        }
        sb.append("]");
        return (sb.toString());
    }
}
