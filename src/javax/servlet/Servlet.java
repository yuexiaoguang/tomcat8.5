package javax.servlet;

import java.io.IOException;

/**
 * 定义所有servlet必须实现的方法.
 *
 * <p>
 * servlet是一个运行在web服务器的java小程序. servlet接收并响应来自Web客户端的请求，通常通过HTTP，超文本传输协议.
 *
 * <p>
 * 为了实现这个接口, 可以编写通用的servlet继承<code>javax.servlet.GenericServlet</code>或一个HTTP servlet继承
 * <code>javax.servlet.http.HttpServlet</code>.
 *
 * <p>
 * 此接口定义了初始化servlet、服务请求和从服务器中删除servlet的方法. 这些称为生命周期方法，并按以下顺序调用:
 * <ol>
 * <li>servlet被构造, 然后使用<code>init</code>方法初始化.
 * <li>处理所有从客户端到<code>service</code>方法的调用.
 * <li>servlet退出服务, 然后使用<code>destroy</code>方法销毁, 然后垃圾收集和结束.
 * </ol>
 *
 * <p>
 * 除了生命周期方法, 这个接口提供了<code>getServletConfig</code>方法, servlet可以用来获取任何启动信息,
 * 以及<code>getServletInfo</code>方法允许servlet返回关于自身的基础信息, 例如：作者, 版本号, 和版权.
 */
public interface Servlet {

    /**
     * 由servlet容器调用，以指示将servlet放入服务中.
     *
     * <p>
     * servlet容器调用<code>init</code>方法一次，初始化servlet之后. <code>init</code>方法在servlet可以接收任何请求之前必须成功完成.
     *
     * <p>
     * servlet容器不能将servlet放入服务中，如果<code>init</code>方法：
     * <ol>
     * <li>抛出一个<code>ServletException</code>
     * <li>没有在Web服务器定义的时间周期内返回
     * </ol>
     *
     *
     * @param config 包含servlet的配置和初始化参数
     *
     * @exception ServletException 如果发生了异常，干扰了servlet的正常操作
     */
    public void init(ServletConfig config) throws ServletException;

    /**
     *
     * 返回一个{@link ServletConfig}对象, 其中包含此servlet的初始化和启动参数.
     * 返回的<code>ServletConfig</code>对象是传递给<code>init</code>方法的那一个.
     *
     * <p>
     * 此接口的实现负责保存<code>ServletConfig</code>对象，这样这个方法就可以返回它了.
     * 继承了这个借口的{@link GenericServlet}类，就是这样做的.
     *
     * @return 初始化这个servlet的<code>ServletConfig</code>对象
     */
    public ServletConfig getServletConfig();

    /**
     * 由servlet容器调用，以允许servlet对请求作出响应.
     *
     * <p>
     * 这个方法只会在servlet的<code>init()</code>方法成功执行之后才会调用.
     *
     * <p>
     * 应该为抛出或发送错误的servlet设置响应的状态码.
     *
     *
     * <p>
     * servlet通常运行在可以同时处理多个请求的多线程的servlet容器内部.
     * 开发人员必须意识到同步访问任何共享资源（如文件、网络连接）以及servlet的类和实例变量.
     * 在java多线程编程的更多信息可在
     * <a href ="http://java.sun.com/Series/Tutorial/java/threads/multithreaded.html">
     * 多线程编程的java教程</a>.
     *
     *
     * @param req 包含客户端请求的对象
     *
     * @param res 包含servlet响应的对象
     *
     * @exception ServletException 如果出现了干扰servlet正常操作的异常
     *
     * @exception IOException 如果出现输入或输出异常
     */
    public void service(ServletRequest req, ServletResponse res)
            throws ServletException, IOException;

    /**
     * 返回有关servlet的信息，如作者、版本和版权.
     *
     * <p>
     * 此方法返回的字符串应该是纯文本，而不是任何类型的标记（如HTML、XML等）.
     */
    public String getServletInfo();

    /**
     * 由servlet容器调用，以指示servlet将被排除在服务之外.
     * 这个方法只会被所有线程调用一次，在servlet的<code>service</code>方法结束或超时之后.
     * 在servlet容器调用此方法之后, 不会再调用这个servlet的<code>service</code>方法.
     *
     * <p>
     * 此方法使servlet有机会清理正在进行的任何资源(例如，内存、文件句柄、线程)，确保任何持久状态与servlet在内存中的当前状态同步.
     */
    public void destroy();
}
