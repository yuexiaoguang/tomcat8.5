package javax.servlet.jsp;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * <p>
 * 定义了在运行时可用于JSP页面的许多工厂方法，以创建用于支持JSP实现的各种接口和类的实例.
 * <p>
 * PageContext和JspEngineInfo 类是惟一可以从工厂创建的实现依赖类.
 * <p>
 * JspFactory对象不应该被JSP页面作者使用.
 */
public abstract class JspFactory {

    private static volatile JspFactory deflt = null;

    public JspFactory() {
        // NOOP by default
    }

    /**
     * <p>
     * 为这个实现设置默认工厂. 对于JSP引擎运行时以外的任何主体调用此方法都是非法的.
     * </p>
     *
     * @param deflt 默认工厂实现类
     */

    public static void setDefaultFactory(JspFactory deflt) {
        JspFactory.deflt = deflt;
    }

    /**
     * 返回此实现的默认工厂.
     *
     * @return 此实现的默认工厂
     */

    public static JspFactory getDefaultFactory() {
        return deflt;
    }

    /**
     * <p>
     * 获取一个实现的实例， 依据javax.servlet.jsp.PageContext抽象类，通过调用Servlet
     * 和当前正在等待的请求和响应.
     * </p>
     *
     * <p>
     * 这个方法通常在更早之前调用，在JSP实现类的_jspService()方法的处理中，为了获取处理的请求的PageContext对象.
     * </p>
     * <p>
     * 此方法将调用PageContext.initialize()方法. 返回的PageContext被正确地初始化.
     * </p>
     * <p>
     * 所有通过这个方法获取的 PageContext对象应通过调用 releasePageContext()释放.
     * </p>
     *
     * @param servlet      请求servlet
     * @param request      当前请求
     * @param response     当前响应
     * @param errorPageURL 请求JSP的错误页面的URL, 或者null
     * @param needsSession true 如果JSP参与会话
     * @param buffer       缓冲区大小以字节为单位, {@link JspWriter#NO_BUFFER}不使用缓存, {@link JspWriter#DEFAULT_BUFFER}使用默认的.
     * @param autoflush    缓冲区溢出时，缓冲区是否自动刷新到输出流, 或者抛出IOException?
     *
     * @return the page context
     */

    public abstract PageContext getPageContext(Servlet servlet,
            ServletRequest request, ServletResponse response,
            String errorPageURL, boolean needsSession, int buffer,
            boolean autoflush);

    /**
     * <p>
     * 释放先前分配的PageContext对象. 调用PageContext.release().
     * 应该在JSP实现类的_jspService()方法返回之前调用此方法.
     * </p>
     *
     * @param pc 以前通过getPageContext()获取的PageContext
     */
    public abstract void releasePageContext(PageContext pc);

    /**
     * <p>
     * 调用以获取当前JSP引擎的特定于实现的信息.
     * </p>
     *
     * @return a 描述当前的JSP引擎的JspEngineInfo对象
     */

    public abstract JspEngineInfo getEngineInfo();

    /**
     * <p>
     * 获取和传过来的<code>ServletContext</code>关联的<code>JspApplicationContext</code>实例.
     * </p>
     *
     * @param context 当前Web应用程序的<code>ServletContext</code>
     * @return <code>JspApplicationContext</code>实例
     * @since 2.1
     */
    public abstract JspApplicationContext getJspApplicationContext(
            ServletContext context);
}
