package javax.servlet;

import java.util.Enumeration;

/**
 * 在初始化期间servlet容器将信息传递给servlet的servlet配置对象.
 */
public interface ServletConfig {

    /**
     * 返回此servlet实例的名称.
     * 可以通过Web应用程序部署描述符中指定的服务器管理者提供的名称，或者对于未注册（因而未命名的）servlet实例将是servlet的类名.
     */
    public String getServletName();

    /**
     * 返回一个 {@link ServletContext}引用.
     *
     * @return 一个{@link ServletContext}对象, 调用者用于与其servlet容器交互的
     */
    public ServletContext getServletContext();

    /**
     * 返回一个<code>String</code>包含指定的初始化参数的值, 或<code>null</code>.
     *
     * @param name 参数名称
     */
    public String getInitParameter(String name);

    /**
     * 返回servlet的初始化参数的名称，作为一个<code>String</code>对象的<code>Enumeration</code>,
     * 或一个空的<code>Enumeration</code>.
     */
    public Enumeration<String> getInitParameterNames();
}
