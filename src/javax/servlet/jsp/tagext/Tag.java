package javax.servlet.jsp.tagext;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;


/**
 * 标记接口定义标记处理程序和JSP页面实现类之间的基本协议. 它定义了在开始和结束标记中要调用的生命周期和方法.
*/
public interface Tag extends JspTag {

    /**
     * 跳过主体计算.
     */
    public static final int SKIP_BODY = 0;

    /**
     * 计算主体，输出到现有输出流.
     */
    public static final int EVAL_BODY_INCLUDE = 1;

    /**
     * 跳过页面的其余部分.
     */
    public static final int SKIP_PAGE = 5;

    /**
     * 继续计算页面.
     */
    public static final int EVAL_PAGE = 6;


    /**
     * 设置当前页面上下文.
     * 此方法由JSP页面实现对象调用，在doStartTag()之前.
     * <p>
     * 这个值不是由doEndTag()重置，并且必须通过页面实现显式地重置.
     *
     * @param pc 此标记处理程序的页面上下文.
     */
    void setPageContext(PageContext pc);


    /**
     * 设置这个标签处理程序的父级(最近的父级标记处理程序).
     * 此方法由JSP页面实现对象调用，在doStartTag()之前.
     * <p>
     * 这个值不是由doEndTag()重置，并且必须通过页面实现显式地重置.
     *
     * @param t 父级标签, 或 null.
     */
    void setParent(Tag t);


    /**
     * 获取这个标签处理程序的父级(最近的父级标记处理程序).
     *
     * <p>
     * getParent()方法可以用于在运行时导航嵌套的标记处理程序结构，以进行自定义操作之间的协作;
     * 例如, TagSupport中的findAncestorWithClass()方法提供了一种方便的方法来做这个.
     *
     * <p>
     * 这个额外的约束可以由知道那个特定的标记库的专门的容器运用, 在JSP标准标记库的情况下.
     *
     * @return 当前父级, 或者null.
     */
    Tag getParent();

    /**
     * 处理此实例的开始标记. 此方法由JSP页面实现对象调用.
     *
     * <p>
     * doStartTag方法假设属性pageContext 和父级已经被设置. 它还假定任何属性作为属性公开了.
     * 调用此方法时, 主体还没有计算.
     *
     * <p>
     * 这个方法返回 Tag.EVAL_BODY_INCLUDE 或 BodyTag.EVAL_BODY_BUFFERED 指示action的主体应该被计算，或者SKIP_BODY.
     *
     * @return EVAL_BODY_INCLUDE 如果标签要处理主体, SKIP_BODY 如果它不想处理它.
     * @throws JspException 如果在处理此标记时发生错误
     */
    int doStartTag() throws JspException;


    /**
     * 处理此实例的结束标记.
     * 在所有标记处理程序中，JSP页面实现对象调用此方法.
     *
     * <p>
     * 如果该方法返回 EVAL_PAGE, 页面的其余部分继续进行计算.
     * 如果该方法返回SKIP_PAGE, 页面的其余部分不会被计算，请求完成, 封装的标签的doEndTag()方法不会被调用.
     * 如果该请求被转发或包含在另一个页面中(或 Servlet), 只停止当前页面的计算.
     *
     * @return 指示是否继续计算JSP页面.
     * @throws JspException 如果在处理此标记时发生错误
     */
    int doEndTag() throws JspException;

    /**
     * 调用标记处理程序释放状态.
     * 页面编译器保证JSP页面实现对象将在所有标记处理程序上调用此方法, 可能有多次调用 doStartTag 和 doEndTag.
     */
    void release();

}
