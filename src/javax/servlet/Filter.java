package javax.servlet;

import java.io.IOException;

/**
 * 过滤器是执行过滤任务的对象对资源请求(一个servlet 或静态内容), 或来自资源的响应, 或两者都是. <br>
 * <br>
 * 过滤器执行过滤使用<code>doFilter</code>方法. 每个过滤器都有一个FilterConfig对象来获取它的初始化参数, 以及可以使用的ServletContext引用,
 * 例如, 加载过滤任务所需的资源.
 * <p>
 * 过滤器配置在Web应用程序的部署描述符中
 * <p>
 * 为这种设计确定的示例是<br>
 * 1) 认证过滤器 <br>
 * 2) 日志和审计过滤器<br>
 * 3) 图像转换过滤器 <br>
 * 4) 数据压缩过滤器 <br>
 * 5) 加密过滤器 <br>
 * 6) 令牌过滤器 <br>
 * 7) 触发资源访问事件的过滤器 <br>
 * 8) XSL/T 过滤器 <br>
 * 9) Mime-type 链过滤器 <br>
 */
public interface Filter {

    /**
     * 由Web容器调用，以指示要将其放入服务中的过滤器. servlet容器只调用init方法一次，在初始化过滤器之后.
     * 在过滤器被要求做任何过滤之前，init方法必须成功完成.
     * <p>
     * Web容器不能将过滤器放入服务中，如果init方法:
     * <ul>
     * <li>Throws a ServletException</li>
     * <li>在Web容器定义的时间段内不返回</li>
     * </ul>
     *
     * @param filterConfig 与过滤器实例初始化相关的配置信息
     *
     * @throws ServletException 如果初始化失败
     */
    public void init(FilterConfig filterConfig) throws ServletException;

    /**
     * 容器每次调用过滤器的<code>doFilter</code>方法，一个请求/响应对通过链传递，因为客户端在链的结尾请求一个资源.
     * 传递到这个方法的FilterChain允许过滤器传递请求和响应到链中的下一个实体.
     * <p>
     * 这种方法的典型实现将遵循以下模式:- <br>
     * 1. 检查请求<br>
     * 2. 可选地用自定义实现包装请求对象，以过滤输入过滤的内容或header <br>
     * 3. 可选地用自定义实现包装响应对象，以过滤输出过滤的内容或header <br>
     * 4. a) 使用FilterChain对象执行链中的下一个实体 (<code>chain.doFilter()</code>), <br>
     * 4. b) 或者不传递请求/响应对到过滤器链中的下一个实体阻止请求处理<br>
     * 5. 在过滤器链中的下一个实体调用之后，在响应上直接设置标头.
     *
     * @param request  要处理的请求
     * @param response 请求关联的响应
     * @param chain    提供对该过滤器中的下一个过滤器的访问，以便将请求和响应传递来进一步处理
     *
     * @throws IOException 如果在该过滤器的请求处理过程中发生I/O错误
     * @throws ServletException 如果处理因其他原因失败
     */
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException;

    /**
     * 由Web容器调用，以指示过滤器正在被排除在服务之外. 这个方法对于所有线程只调用一次，在过滤器的doFilter方法结束或超时之后.
     * 在Web容器调用此方法之后，它不会再次调用这个过滤器实例的doFilter方法. <br>
     * <br>
     *
     * 此方法使过滤器有机会清理正在进行的任何资源(例如，内存、文件句柄、线程)确保任何持久状态与过滤器在内存中的当前状态同步.
     */
    public void destroy();

}
