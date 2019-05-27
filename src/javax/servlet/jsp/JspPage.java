package javax.servlet.jsp;

import javax.servlet.Servlet;

/**
 * 描述JSP页面实现类必须满足的泛型交互; 使用HTTP协议的页面使用HttpJspPage接口描述.
 *
 * <p><B>Two plus One Methods</B>
 * <p>
 * 接口定义了一个有3种方法的协议; 其中的两个是: jspInit() 和 jspDestroy()
 * 第三个方法时: _jspService() 取决于具体的协议并不能在java中通用的表达方式.
 * <p>
 * 实现此接口的类负责调用上面的方法在适当的时间基于方法调用相应的Servlet.
 * <p>
 * jspInit() 和 jspDestroy()方法可以被JSP作者定义, 但是 _jspService() 方法是根据JSP页面的内容由JSP处理器自动定义的.
 *
 * <p><B>_jspService()</B>
 * <p>
 *  _jspService() 方法对应于JSP页面的主体. 这个方法是由JSP容器自动定义的，不应该由JSP页面作者定义.
 * <p>
 * 如果父类是使用扩展属性指定, 父类可以执行它的service()方法的一些操，在调用 _jspService()方法之前或之后.
 * <p>
 * 特定的签名取决于JSP页面支持的协议.
 *
 * <pre>
 * public void _jspService(<em>ServletRequestSubtype</em> request,
 *                             <em>ServletResponseSubtype</em> response)
 *        throws ServletException, IOException;
 * </pre>
 */
public interface JspPage extends Servlet {

    /**
     * 调用jspInit()方法，当初始化JSP页面的时候. 这是JSP实现的职责(以及扩展属性所提到的类) 调用getServletConfig()方法将返回所需的值.
     *
     * JSP页面可以通过在声明元素中包含它的定义来重写这个方法.
     *
     * JSP页面应该重新定义init()方法.
     */
    public void jspInit();

    /**
     * 调用jspDestroy()方法，当销毁JSP页面的时候.
     *
     * JSP页面可以通过在声明元素中包含它的定义来重写这个方法.
     *
     * 一个JSP页面应该重新定义destroy()方法.
     */
    public void jspDestroy();

}
