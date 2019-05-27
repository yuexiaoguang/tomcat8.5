package javax.servlet;

import java.io.IOException;
import java.util.Enumeration;

/**
 * 定义一个通用的、与协议无关的servlet. 在Web上编写一个HTTP servlet, 扩展{@link javax.servlet.http.HttpServlet}.
 * <p>
 * <code>GenericServlet</code>实现了<code>Servlet</code>和<code>ServletConfig</code>接口.
 * <code>GenericServlet</code>可以由servlet直接扩展, 尽管扩展特定于协议的子类是比较常见的，例如<code>HttpServlet</code>.
 * <p>
 * <code>GenericServlet</code>使得编写servlet更容易. 它提供了生命周期方法<code>init</code>和<code>destroy</code>的简单版本，
 * 以及<code>ServletConfig</code>接口的方法.
 * <code>GenericServlet</code>也实现了<code>log</code>方法, 在<code>ServletContext</code>接口中声明.
 * <p>
 * 编写通用的servlet, 只需要重写<code>service</code>方法.
 */
public abstract class GenericServlet implements Servlet, ServletConfig, java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private transient ServletConfig config;

    /**
     * 什么都不做. 所有的servlet初始化已经通过<code>init</code>方法完成.
     */
    public GenericServlet() {
        // NOOP
    }

    /**
     * 由servlet容器调用，以指示一个servlet将被排除在服务之外.
     */
    @Override
    public void destroy() {
        // NOOP by default
    }

    /**
     * 返回一个<code>String</code>包含指定的初始化参数的值, 或<code>null</code>.
     * <p>
     * 这种方法是为了方便而提供的. 它从servlet的<code>ServletConfig</code>对象获取命名参数的值.
     *
     * @param name 初始化参数的名称
     * @return 初始化参数的值
     */
    @Override
    public String getInitParameter(String name) {
        return getServletConfig().getInitParameter(name);
    }

    /**
     * 返回servlet的初始化参数，作为一个<code>String</code>对象的<code>Enumeration</code>, 或一个空的<code>Enumeration</code>.
     * <p>
     * 这种方法是为了方便而提供的. 它从servlet的<code>ServletConfig</code>对象获取参数名.
     */
    @Override
    public Enumeration<String> getInitParameterNames() {
        return getServletConfig().getInitParameterNames();
    }

    /**
     * 返回这个servlet的{@link ServletConfig}对象.
     */
    @Override
    public ServletConfig getServletConfig() {
        return config;
    }

    /**
     * 返回这个servlet运行的{@link ServletContext}引用.
     * <p>
     * 这种方法是为了方便而提供的. 它从servlet的<code>ServletConfig</code>对象获取上下文.
     */
    @Override
    public ServletContext getServletContext() {
        return getServletConfig().getServletContext();
    }

    /**
     * 返回有关servlet的信息, 例如作者, 版本, 与著作权.
     * 默认情况下, 此方法返回空字符串. 重写此方法以使其返回有意义的值.
     */
    @Override
    public String getServletInfo() {
        return "";
    }

    /**
     * 由servlet容器调用，以指示将servlet放入服务中.
     * <p>
     * 这个实现保存它从servlet容器接受的{@link ServletConfig}对象，为之后使用. 重写该方法的形式时, 调用<code>super.init(config)</code>.
     *
     * @param config 包含此servlet的配置信息的<code>ServletConfig</code>对象
     * @exception ServletException 如果出现中断servlet正常操作的异常
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        this.config = config;
        this.init();
    }

    /**
     * 一种可以被重写的便捷方法, 因此不需要调用<code>super.init(config)</code>.
     * <p>
     * 代替重写 {@link #init(ServletConfig)}, 简单重写这个方法，而且他将通过<code>GenericServlet.init(ServletConfig config)</code>调用.
     * <code>ServletConfig</code>对象仍然可以通过{@link #getServletConfig}取回.
     *
     * @exception ServletException 如果出现中断servlet正常操作的异常
     */
    public void init() throws ServletException {
        // NOOP by default
    }

    /**
     * 将指定的消息写入一个servlet日志文件, 使用servlet的名字作为前缀.
     *
     * @param msg 要写入日志文件的信息
     */
    public void log(String msg) {
        getServletContext().log(getServletName() + ": " + msg);
    }

    /**
     * 写入说明消息和<code>Throwable</code>异常的堆栈跟踪到servlet日志文件, 使用servlet的名称为前缀.
     *
     * @param message 描述错误或异常
     * @param t <code>java.lang.Throwable</code>错误或异常
     */
    public void log(String message, Throwable t) {
        getServletContext().log(getServletName() + ": " + message, t);
    }

    /**
     * 由servlet容器调用，以允许servlet对请求作出响应.
     * <p>
     * 这个方法被声明为抽象的, 例如<code>HttpServlet</code>, 必须重写它.
     *
     * @param req 包含客户端请求的<code>ServletRequest</code>对象
     * @param res 包含servlet响应的<code>ServletResponse</code>对象
     * @exception ServletException 如果出现妨碍servlet正常操作的异常
     * @exception IOException 如果出现输入或输出异常
     */
    @Override
    public abstract void service(ServletRequest req, ServletResponse res) throws ServletException, IOException;

    /**
     * 返回此servlet实例的名称.
     *
     * @return 此servlet实例的名称
     */
    @Override
    public String getServletName() {
        return config.getServletName();
    }
}
