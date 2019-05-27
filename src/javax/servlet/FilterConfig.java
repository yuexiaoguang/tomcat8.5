package javax.servlet;

import java.util.Enumeration;

/**
 *
 * servlet容器在初始化期间将信息传递给过滤器的过滤器配置对象.
 */
public interface FilterConfig {

    /**
     * 获取过滤器的名称.
     *
     * @return 在部署描述符中定义的过滤器的过滤器名称.
     */
    public String getFilterName();

    /**
     * 返回一个调用者正在执行的{@link ServletContext}引用.
     *
     * @return {@link ServletContext}对象, 调用者用于与其servlet容器交互的
     */
    public ServletContext getServletContext();

    /**
     * 返回一个<code>String</code>包含指定的初始化参数的值, 或者<code>null</code>.
     *
     * @param name <code>String</code>指定初始化参数的名称
     *
     * @return <code>String</code>包含初始化参数的值
     */
    public String getInitParameter(String name);

    /**
     * 返回过滤器初始化参数的名称，作为一个<code>String</code>对象的<code>Enumeration</code>, 或一个空的<code>Enumeration</code>.
     */
    public Enumeration<String> getInitParameterNames();

}
