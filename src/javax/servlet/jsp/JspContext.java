package javax.servlet.jsp;

import java.util.Enumeration;

import javax.el.ELContext;

/**
 * <p>
 * <code>JspContext</code>作为PageContext类的基类，并抽象出所有并不特定于servlet的信息.
 * 允许在一个request/response Servlet的上下文外面使用Simple Tag Extension.
 * <p>
 * JspContext 提供许多工具到page/component作者和网页实现人员, 包括:
 * <ul>
 * <li>一个单一的API来管理不同作用域的命名空间
 * <li>一种机制来获得输出 JspWriter
 * <li>向脚本环境公开页面指令属性的机制
 * </ul>
 *
 * <p><B>用于容器生成代码的方法</B>
 * <p>
 * 下列方法启用<B>management of nested</B> JspWriter 流来实现Tag Extensions: <code>pushBody()</code>和<code>popBody()</code>
 *
 * <p><B>JSP作者的方法</B>
 * <p>
 * 一些方法提供<B>uniform access</B>对表示范围的不同对象. 实现必须使用与该范围相对应的底层机制, 因此信息可以在底层环境(e.g. Servlets)和JSP 页面之间来回传递.
 * 这些方法是:
 * <code>setAttribute()</code>,  <code>getAttribute()</code>,
 * <code>findAttribute()</code>,  <code>removeAttribute()</code>,
 * <code>getAttributesScope()</code>, <code>getAttributeNamesInScope()</code>.
 *
 * <p>
 * 下列方法提供<B>convenient access</B>给隐式对象:
 * <code>getOut()</code>
 *
 * <p>
 * 下列方法提供<B>programmatic access</b> 给Expression Language 计算器:
 * <code>getExpressionEvaluator()</code>, <code>getVariableResolver()</code>
 */
public abstract class JspContext {

    /**
     * (用于子类构造函数的调用, 通常是隐式的.)
     */
    public JspContext() {
        // NOOP by default
    }

    /**
     * 用页面范围语义注册指定的名称和值.
     * 如果传过来的值是<code>null</code>, 和调用<code>removeAttribute( name, PageContext.PAGE_SCOPE )</code>效果一样.
     *
     * @param name 要设置的属性的名称
     * @param value 与名称相关联的值, 或者null.
     * @throws NullPointerException 如果名称是 null
     */

    public abstract void setAttribute(String name, Object value);

    /**
     * 用适当的范围语义注册指定的名称和值.
     * 如果传过来的值是<code>null</code>, 等同于调用<code>removeAttribute( name, scope )</code>.
     *
     * @param name 要设置的属性的名称
     * @param value 与名称相关联的对象, 或者null
     * @param scope 将名称/对象关联的范围
     *
     * @throws NullPointerException 如果名称是 null
     * @throws IllegalArgumentException 如果范围无效
     * @throws IllegalStateException 如果范围是PageContext.SESSION_SCOPE, 但请求的页面不参与会话，或者会话已失效.
     */

    public abstract void setAttribute(String name, Object value, int scope);

    /**
     * 返回与页面范围中的名称相关联的对象，或者null.
     *
     * @param name 要获取的属性的名称
     * @return 与页面范围中的名称相关联的对象， 或者null.
     *
     * @throws NullPointerException 如果名称是null
     */

    public abstract Object getAttribute(String name);

    /**
     * 返回指定的范围内与名称关联的对象， 或者null.
     *
     * @param name 要设置的属性的名称
     * @param scope 将名称/对象关联的范围
     * @return 与指定范围内的名称关联的对象，或者 null.
     *
     * @throws NullPointerException 如果名称是null
     * @throws IllegalArgumentException 如果范围无效
     * @throws IllegalStateException 如果范围是PageContext.SESSION_SCOPE, 但请求的页面不参与会话，或者会话已失效.
     */

    public abstract Object getAttribute(String name, int scope);

    /**
     * 按顺序搜索页面、请求、会话（如果有效）和应用程序范围中的命名属性，并返回相关联的值，或者null.
     *
     * @param name 要搜索的属性的名称
     * @return 关联的值或null
     * @throws NullPointerException 如果名称是null
     */

    public abstract Object findAttribute(String name);

    /**
     * 从所有范围中移除与给定名称关联的对象引用. 如果没有这样的对象，什么也不做.
     *
     * @param name 要删除的对象的名称.
     * @throws NullPointerException 如果名称是null
     */

    public abstract void removeAttribute(String name);

    /**
     * 在给定范围内移除与指定名称相关联的对象引用. 如果没有这样的对象，什么也不做.
     *
     * @param name 要删除的对象的名称.
     * @param scope 要查找的范围.
     * @throws IllegalArgumentException 如果范围无效
     * @throws IllegalStateException 如果范围是PageContext.SESSION_SCOPE， 但请求的页面不参与会话，或者会话已失效.
     * @throws NullPointerException 如果名称是null
     */

    public abstract void removeAttribute(String name, int scope);

    /**
     * 获取定义给定属性的范围.
     *
     * @param name 返回范围的属性的名称
     * @return 与指定的名称相关联的对象的作用域，或 0
     * @throws NullPointerException 如果名称是null
     */

    public abstract int getAttributesScope(String name);

    /**
     * 枚举给定范围内的所有属性.
     *
     * @param scope 枚举所有属性的范围
     * @return 指定范围内所有属性名称的枚举
     * @throws IllegalArgumentException 如果范围无效
     * @throws IllegalStateException 如果范围是PageContext.SESSION_SCOPE， 但请求的页面不参与会话，或者会话已失效.
     */

    public abstract Enumeration<String> getAttributeNamesInScope(int scope);

    /**
     * out对象(JspWriter)的当前值.
     *
     * @return 客户端响应使用的当前JspWriter流
     */
    public abstract JspWriter getOut();


    public abstract ELContext getELContext();

    /**
     * 返回一个新的JspWriter对象.
     * 保存当前"out" JspWriter, 并更新JspContext的页面范围属性命名空间中的"out"属性的值.
     * <p>返回的JspWriter要实现所有的方法和行为好像无缓冲.  更具体地说:
     * </p>
     * <ul>
     *   <li>clear()必须抛出一个IOException</li>
     *   <li>clearBuffer()什么都不做</li>
     *   <li>getBufferSize()总是返回 0</li>
     *   <li>getRemaining()总是返回 0</li>
     * </ul>
     *
     * @param writer 返回的JspWriter发送输出的Writer.
     * @return 写入给定Writer的新的JspWriter.
     * @since 2.0
     */
    public JspWriter pushBody( java.io.Writer writer ) {
        return null; // XXX to implement
    }

    /**
     * 返回之前pushBody()保存的JspWriter "out", 并更新JspContext的页面范围属性命名空间中的"out"属性的值.
     *
     * @return 保存的JspWriter.
     */
    public JspWriter popBody() {
        return null; // XXX to implement
    }
}
