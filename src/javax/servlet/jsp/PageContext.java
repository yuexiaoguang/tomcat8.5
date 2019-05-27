package javax.servlet.jsp;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.tagext.BodyContent;

/**
 * <p>
 * 为servlet环境中使用JSP技术时提供有用的上下文信息.
 * <p>
 * PageContext实例提供对与JSP页面相关联的所有命名空间的访问, 提供对几个页面属性的访问, 以及实现细节之上的一层.
 * 隐式对象自动加入到pageContext.
 *
 * <p><code> PageContext </code>类是抽象类, 旨在扩展以提供与实现相关的实现, 通过JSP引擎的运行时环境的一致性.
 * PageContext 实例是通过JSP实现类调用JspFactory.getPageContext()方法获得的, 通过调用JspFactory.releasePageContext()释放.
 *
 * <p>
 * PageContext提供许多工具给page/component作者和页面实现者, 包括:
 * <ul>
 * <li>一个单一的API来管理不同作用域的命名空间
 * <li>访问各种公共对象的一些方便API
 * <li>一种机制来获得输出JspWriter
 * <li>一种管理页面会话使用情况的机制
 * <li>向脚本环境公开页面指令属性的机制
 * <li>将当前请求转发或包含到应用程序中其他活动组件的机制
 * <li>处理errorPage的异常处理机制
 * </ul>
 *
 * <p><B>用于容器生成代码的方法</B>
 * <p>有些方法打算由容器生成的代码使用, 不是由JSP页面作者或JSP标记库作者编写的代码.
 * <p>方法支持<B>lifecycle</B>是<code>initialize()</code>和<code>release()</code>
 *
 * <p>
 * 下列方法启用<B>管理嵌套</B> JspWriter流实现标签扩展: <code>pushBody()</code>
 *
 * <p><B>JSP作者的方法</B>
 * <p>
 * 下列方法提供<B>方便的访问</B>隐式对象:
 * <code>getException()</code>,  <code>getPage()</code>
 * <code>getRequest()</code>,  <code>getResponse()</code>,
 * <code>getSession()</code>,  <code>getServletConfig()</code>
 * and <code>getServletContext()</code>.
 *
 * <p>
 * 下列方法提供<B>重定向, 包括和错误处理</B>:
 * <code>forward()</code>,  <code>include()</code>, <code>handlePageException()</code>.
 */
public abstract class PageContext extends JspContext {

    public PageContext() {
        // NOOP by default
    }

    /**
     * Page scope: (默认的)引用在PageContext中仍然可用，直到从Servlet.service()返回.
     */
    public static final int PAGE_SCOPE = 1;

    /**
     * Request scope: 引用在Servlet关联的ServletRequest中仍然可用，直到当前请求完成.
     */
    public static final int REQUEST_SCOPE = 2;

    /**
     * Session scope (只有此页面参与会话才有效):
     * 引用在Servlet关联的HttpSession中仍然可用，直到HttpSession无效之后.
     */
    public static final int SESSION_SCOPE = 3;

    /**
     * Application scope: 引用在ServletContext中仍然可用，直到回收.
     */
    public static final int APPLICATION_SCOPE = 4;

    /**
     * 用于保存Servlet到这个 PageContext的名称表中的名称.
     */
    public static final String PAGE = "javax.servlet.jsp.jspPage";

    /**
     * 用于保存PageContext到它自己的名称表中的名称.
     */
    public static final String PAGECONTEXT = "javax.servlet.jsp.jspPageContext";

    /**
     * 用于保存ServletRequest到这个 PageContext的名称表中的名称.
     */
    public static final String REQUEST = "javax.servlet.jsp.jspRequest";

    /**
     * 用于保存ServletResponse到这个 PageContext的名称表中的名称.
     */
    public static final String RESPONSE = "javax.servlet.jsp.jspResponse";

    /**
     * 用于保存ServletConfig到这个 PageContext的名称表中的名称.
     */
    public static final String CONFIG = "javax.servlet.jsp.jspConfig";

    /**
     * 用于保存HttpSession到这个 PageContext的名称表中的名称.
     */
    public static final String SESSION = "javax.servlet.jsp.jspSession";
    /**
     * 用于保存当前JspWriter到这个 PageContext的名称表中的名称.
     */
    public static final String OUT = "javax.servlet.jsp.jspOut";

    /**
     * 用于保存当前ServletContext到这个 PageContext的名称表中的名称.
     */
    public static final String APPLICATION = "javax.servlet.jsp.jspApplication";

    /**
     * 用来存储捕获的异常到 ServletRequest属性列表和 PageContext名称表中的名称.
     */
    public static final String EXCEPTION = "javax.servlet.jsp.jspException";

    /**
     * <p>
     * 用于初始化PageContext， 因此，JSP实现类可以使用它的_jspService()方法来服务传入的请求和响应.
     *
     * <p>
     * 这个方法通常从JspFactory.getPageContext()调用，为了初始化状态.
     *
     * <p>
     * 这个方法需要创建一个初始的JspWriter, 并在页面范围内关联"out"名称和新创建的对象.
     *
     * <p>
     * 此方法不应由页面或标记库作者使用.
     *
     * @param servlet 和这个PageContext关联的Servlet
     * @param request 当前正在等待此servlet的请求
     * @param response 此servlet当前正在进行的响应
     * @param errorPageURL 来自页面指令的errorpage的属性值或 null
     * @param needsSession 来自页面指令的session的属性值
     * @param bufferSize 来自页面指令的buffer的属性值
     * @param autoFlush 来自页面指令的autoflush的属性值
     *
     * @throws IOException during creation of JspWriter
     * @throws IllegalStateException 如果未正确初始化
     * @throws IllegalArgumentException 如果某个参数无效
     */
    public abstract void initialize(Servlet servlet, ServletRequest request,
        ServletResponse response, String errorPageURL, boolean needsSession,
        int bufferSize, boolean autoFlush)
        throws IOException, IllegalStateException, IllegalArgumentException;

    /**
     * <p>
     * 这种方法将"reset" PageContext的内部状态, 释放所有内部引用, 并准备PageContext以后调用initialize()重用.
     * 通常被JspFactory.releasePageContext()调用.
     *
     * <p>
     * 子类应封装此方法.
     *
     * <p>
     * 此方法不应由页面或标记库作者使用.
     */
    public abstract void release();

    /**
     * 这个PageContext的HttpSession或null
     */
    public abstract HttpSession getSession();

    /**
     * 页面对象的当前值(在一个Servlet环境中, 这是一个javax.servlet.Servlet对象).
     *
     * @return 这个PageContext关联的Page实现类实例
     */
    public abstract Object getPage();


    /**
     * 这个PageContext的ServletRequest.
     */
    public abstract ServletRequest getRequest();

    /**
     * 这个PageContext的ServletResponse.
     */
    public abstract ServletResponse getResponse();

    /**
     * 作为一个errorpage的异常.
     */
    public abstract Exception getException();

    /**
     * ServletConfig实例.
     */
    public abstract ServletConfig getServletConfig();

    /**
     * ServletContext实例.
     */
    public abstract ServletContext getServletContext();

    /**
     * <p>
     * 用于重新定向, 或者"forward"当前ServletRequest 和 ServletResponse到应用程序中的另一个活动组件.
     * </p>
     * <p>
     * 如果<I> relativeUrlPath </I>以一个"/"开头，那么指定的 URL相对于这个JSP的<code> ServletContext </code>的DOCROOT计算.
     * 如果路径不是以"/"开头，那么指定的 URL相对于映射到调用JSP的请求的URL计算.
     * </p>
     * <p>
     * 只有在<code> Thread </code>和JSP的<code> _jspService(...) </code>方法一起调用时才有效.
     * </p>
     * <p>
     * 一旦这个方法被成功调用, 调用<code> Thread </code>修改<code>ServletResponse </code>对象是非法的.
     * 通常, 调用者在调用这个方法之后直接从<code> _jspService(...) </code>返回.
     * </p>
     *
     * @param relativeUrlPath 指定到目标资源的相对URL路径，如上所述
     *
     * @throws IllegalStateException 如果<code> ServletResponse </code>不处于可以执行重定向的状态
     * @throws ServletException 如果重定向的页面抛出ServletException
     * @throws IOException 如果转发时发生I/O错误
     */
    public abstract void forward(String relativeUrlPath)
        throws ServletException, IOException;

    /**
     * <p>
     * 导致指定的资源作为当前ServletRequest 和 ServletResponse的一部分.
     * 请求的目标资源处理的输出被直接写入ServletResponse输出流.
     * </p>
     * <p>
     * 这个JSP的当前JspWriter "out"被刷新，是这个调用的副作用, 处理include之前.
     * </p>
     * <p>
     * 如果<I> relativeUrlPath </I>以一个"/"开头，那么指定的 URL相对于这个JSP的<code> ServletContext </code>的DOCROOT计算.
     * 如果路径不是以"/"开头，那么指定的 URL相对于映射到调用JSP的请求的URL计算.
     * </p>
     * <p>
     * 只有在<code> Thread </code>和JSP的<code> _jspService(...) </code>方法一起调用时才有效.
     * </p>
     *
     * @param relativeUrlPath 指定要包含的目标资源的相对URL路径
     *
     * @throws ServletException 如果重定向的页面抛出ServletException
     * @throws IOException 如果转发时发生I/O错误
     */
    public abstract void include(String relativeUrlPath)
        throws ServletException, IOException;

    /**
     * <p>
     * 导致指定的资源作为当前ServletRequest 和 ServletResponse的一部分.
     * 请求的目标资源处理的输出被直接写入getOut()返回的当前JspWriter.
     * </p>
     * <p>
     * 如果flush是true, 这个JSP的当前JspWriter "out"在处理include之前被刷新，是一个副作用.
     * 否则, JspWriter "out"不会被刷新.
     * </p>
     * <p>
     * 如果<I> relativeUrlPath </I>以一个"/"开头，那么指定的 URL相对于这个JSP的<code> ServletContext </code>的DOCROOT计算.
     * 如果路径不是以"/"开头，那么指定的 URL相对于映射到调用JSP的请求的URL计算.
     * </p>
     * <p>
     * 只有在<code> Thread </code>和JSP的<code> _jspService(...) </code>方法一起调用时才有效.
     * </p>
     *
     * @param relativeUrlPath 指定要包含的目标资源的相对URL路径
     * @param flush True：如果JspWriter在include之前被刷新, 或者false.
     *
     * @throws ServletException 如果重定向的页面抛出ServletException
     * @throws IOException 如果转发时发生I/O错误
     */
    public abstract void include(String relativeUrlPath, boolean flush)
        throws ServletException, IOException;

    /**
     * <p>
     * 此方法用于处理'page'等级的异常，通过转发异常到指定的错误页面.
     * 如果不能转发(例如，因为响应已经提交), 应该使用与实现相关的机制来调用错误页面(e.g. "including"错误页面).
     *
     * <p>
     * 如果页面中没有定义错误页面, 应该重新抛出异常，这样标准的servlet错误处理就接管了.
     * <p>
     * JSP在调用之前，实现类通常会清理任何本地状态，此后将立即返回. 在这个调用之后生成任何输出到客户端，或修改任何ServletResponse状态，都是非法的.
     *
     * <p>
     * 此方法保留向后兼容性的原因.  新生成的代码应该使用 PageContext.handlePageException(Throwable).
     *
     * @param e 要处理的异常
     *
     * @throws ServletException 如果在调用错误页时发生错误
     * @throws IOException 如果在调用错误页时发生I/O错误
     * @throws NullPointerException 如果异常是 null
     */
    public abstract void handlePageException(Exception e)
        throws ServletException, IOException;

    /**
     * <p>
     * 此方法用于处理'page'等级的异常，通过转发异常到指定的错误页面.
     * 如果不能转发(例如，因为响应已经提交), 应该使用与实现相关的机制来调用错误页面(e.g. "including"错误页面).
     *
     * <p>
     * 如果页面中没有定义错误页面, 应该重新抛出异常，这样标准的servlet错误处理就接管了.
     *
     * <p>
     * 此方法用于处理'page'等级的异常，通过转发异常到指定的错误页面, 或者如果没指定, 执行某些依赖于实现的操作.
     *
     * <p>
     * JSP在调用之前，实现类通常会清理任何本地状态，此后将立即返回. 在这个调用之后生成任何输出到客户端，或修改任何ServletResponse状态，都是非法的.
     *
     * @param t 要处理的异常
     *
     * @throws ServletException 如果在调用错误页时发生错误
     * @throws IOException 如果在调用错误页时发生I/O错误
     * @throws NullPointerException 如果异常是 null
     */
    public abstract void handlePageException(Throwable t)
        throws ServletException, IOException;

    /**
     * 返回一个新创建的BodyContent对象, 保存当前"out" JspWriter, 并更新PageContext的页面范围属性命名空间中的"out"属性值.
     */
    public BodyContent pushBody() {
        return null; // XXX to implement
    }


    /**
     * 提供对错误信息的方便访问.
     *
     * @return 包含错误信息的ErrorData实例, 从请求属性中获得, 按照servlet规范.
     * 		如果这不是一个错误页面(即, 如果页面指令的isErrorPage属性没有设置为"true"), 这些信息毫无意义.
     *
     * @since 2.0
     */
    public ErrorData getErrorData() {
        int status = 0;

        Integer status_code = (Integer)getRequest().getAttribute(
                RequestDispatcher.ERROR_STATUS_CODE);
        // 避免 NPE 如果没哟设置属性
        if (status_code != null) {
            status = status_code.intValue();
        }

        return new ErrorData(
            (Throwable)getRequest().getAttribute(
                    RequestDispatcher.ERROR_EXCEPTION),
            status,
            (String)getRequest().getAttribute(
                    RequestDispatcher.ERROR_REQUEST_URI),
            (String)getRequest().getAttribute(
                    RequestDispatcher.ERROR_SERVLET_NAME)
            );
    }
}
