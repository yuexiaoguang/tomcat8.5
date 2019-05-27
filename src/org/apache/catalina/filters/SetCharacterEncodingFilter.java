package org.apache.catalina.filters;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * <p>设置用于解析传入请求的字符编码的示例过滤器, 无条件地或仅当客户端没有指定字符编码时.
 * 此过滤器的配置基于以下初始化参数:</p>
 * <ul>
 * <li><strong>encoding</strong> - 要为该请求配置的字符编码, 有条件地或无条件地基于<code>ignore</code>初始化参数.
 * 		这个参数是必须的, 因此没有默认的.</li>
 * <li><strong>ignore</strong> - 如果设置为"true", 客户端指定的任何字符编码被忽略, 并且设置<code>selectEncoding()</code>方法返回的值.
 * 			如果设置为"false, 只有在客户端还没有指定编码时, 调用<code>selectEncoding()</code>. 默认情况下, 这个参数设置为"false".</li>
 * </ul>
 *
 * <p>虽然这个过滤器可以使用不变, 它也很容易对它进行分类, 并让<code>selectEncoding()</code>方法更智能地选择什么编码, 根据输入请求的特征
 * (例如<code>Accept-Language</code>和<code>User-Agent</code> header的值, 或当前用户会话中隐藏的值.</p>
 */
public class SetCharacterEncodingFilter extends FilterBase {

    private static final Log log =
        LogFactory.getLog(SetCharacterEncodingFilter.class);


    // ----------------------------------------------------- Instance Variables

    /**
     * 为通过该过滤器的请求设置的默认字符编码.
     */
    private String encoding = null;
    public void setEncoding(String encoding) { this.encoding = encoding; }
    public String getEncoding() { return encoding; }


    /**
     * 如果客户端指定的字符编码被忽略?
     */
    private boolean ignore = false;
    public void setIgnore(boolean ignore) { this.ignore = ignore; }
    public boolean isIgnore() { return ignore; }


    // --------------------------------------------------------- Public Methods


    /**
     * 选择并设置用于解释此请求的请求参数的字符编码.
     *
     * @param request 正在处理的servlet请求
     * @param response 正在创建的servlet响应
     * @param chain 正在处理的过滤器链
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain)
        throws IOException, ServletException {

        // 有条件地选择和设置要使用的字符编码
        if (ignore || (request.getCharacterEncoding() == null)) {
            String characterEncoding = selectEncoding(request);
            if (characterEncoding != null) {
                request.setCharacterEncoding(characterEncoding);
            }
        }

        // 将控制传递到下一个过滤器
        chain.doFilter(request, response);
    }


    // ------------------------------------------------------ Protected Methods

    @Override
    protected Log getLogger() {
        return log;
    }


    /**
     * 选择要使用的适当字符编码, 基于当前请求或过滤器初始化参数的特性. 如果不设置字符编码, 返回<code>null</code>.
     * <p>
     * 默认实现无条件返回这个过滤器的<strong>encoding</strong>初始化参数配置的值.
     *
     * @param request 正在处理的servlet请求
     * @return 配置的编码
     */
    protected String selectEncoding(ServletRequest request) {
        return this.encoding;
    }
}
