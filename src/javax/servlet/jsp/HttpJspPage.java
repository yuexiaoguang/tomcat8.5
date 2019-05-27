package javax.servlet.jsp;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 描述JSP页面的交互. 实现类在使用HTTP协议时必须满足.
 */
public interface HttpJspPage extends JspPage {

    /** _jspService()方法对应于JSP页面主体.
     * 这个方法是由JSP容器自动定义的，不应该由JSP页面作者定义.
     * <p>
     * 如果父类是使用扩展属性指定, 父类可以选择执行它的service()方法中的动作，在调用_jspService()方法之前或之后.
     *
     * @param request 向JSP提供客户端请求信息.
     * @param response 协助JSP向客户端发送响应.
     * @throws ServletException 在处理JSP过程中发生错误时抛出，容器应采取适当的行动来清理请求.
     * @throws IOException 如果在编写此页的响应时发生错误.
     */
    public void _jspService(HttpServletRequest request,
                            HttpServletResponse response) throws ServletException, IOException;
}
