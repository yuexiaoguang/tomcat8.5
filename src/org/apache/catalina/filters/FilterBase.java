package org.apache.catalina.filters;

import java.util.Enumeration;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * 提供通用初始化和简单no-op销毁的过滤器的基类.
 */
public abstract class FilterBase implements Filter {

    protected static final StringManager sm = StringManager.getManager(FilterBase.class);

    protected abstract Log getLogger();


    /**
     * 迭代配置参数并记录警告, 或为该过滤器中没有匹配的setter 的任何参数抛出异常.
     *
     * @param filterConfig 与初始化的过滤器实例相关联的配置信息
     *
     * @throws ServletException 如果{@link #isConfigProblemFatal()}返回{@code true}, 并且配置的参数没有匹配的setter
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Enumeration<String> paramNames = filterConfig.getInitParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            if (!IntrospectionUtils.setProperty(this, paramName,
                    filterConfig.getInitParameter(paramName))) {
                String msg = sm.getString("filterbase.noSuchProperty",
                        paramName, this.getClass().getName());
                if (isConfigProblemFatal()) {
                    throw new ServletException(msg);
                } else {
                    getLogger().warn(msg);
                }
            }
        }
    }

    @Override
    public void destroy() {
        // NOOP
    }

    /**
     * 如果调用setter或未知配置属性时出现异常，则会触发该过滤器的失败，将阻止Web应用程序启动.
     *
     * @return <code>true</code>如果一个问题触发这个过滤器的失败, 否则<code>false</code>
     */
    protected boolean isConfigProblemFatal() {
        return false;
    }
}
