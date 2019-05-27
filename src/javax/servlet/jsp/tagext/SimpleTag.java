package javax.servlet.jsp.tagext;

import javax.servlet.jsp.JspContext;

/**
 * 定义Simple Tag Handler的接口.
 *
 * <p>Simple Tag Handler不同于 Classic Tag Handler，替代<code>doStartTag()</code>和<code>doEndTag()</code>,
 * <code>SimpleTag</code>接口提供一个简单的<code>doTag()</code>方法, 对于任何给定的标记执行，它只被调用一次.
 * 所有标记逻辑、迭代、主体计算等都将在这个单一的方法中执行. 因此，简单的标记处理程序具有等效的<code>BodyTag</code>, 但使用更简单的生命周期和接口.</p>
 *
 * <p>大多数SimpleTag 处理程序应该继承SimpleTagSupport.</p>
 *
 * <p><b>Lifecycle</b></p>
 *
 * <ol>
 *   <li>与传统的标记处理程序不同, 它从不由JSP容器缓存并重用.</li>
 *   <li>容器调用这个标签定义的每个属性的setter.</li>
 *   <li>容器调用<code>doTag()</code>方法. 所有标记逻辑、迭代、主体计算等都发生在该方法中.</li>
 * </ol>
 */
public interface SimpleTag extends JspTag {

    /**
     * 由容器调用来调用此标记.
     * 该方法的实现由标记库开发人员提供，并处理所有标记处理、主体迭代等.
     *
     * <p>
     * JSP容器将重新同步AT_BEGIN 和 AT_END变量(由关联的标记文件, TagExtraInfo, TLD定义)，
     * 在执行doTag()之后.
     *
     * @throws javax.servlet.jsp.JspException 如果在处理此标记时发生错误.
     * @throws javax.servlet.jsp.SkipPageException 如果页面（直接或间接）调用了调用此片段的标记处理程序将停止计算.
     * 容器必须抛出此异常, 如果 Classic Tag Handler返回Tag.SKIP_PAGE，或者如果Simple Tag Handler抛出 SkipPageException.
     * @throws java.io.IOException 如果写入流时出错.
     */
    public void doTag()
        throws javax.servlet.jsp.JspException, java.io.IOException;

    /**
     * 为协作目的设置此标记的父级.
     * <p>
     * 只有当这个标记调用嵌套在另一个标记调用中时，容器才会调用此方法.
     *
     * @param parent 包围此标记的标记
     */
    public void setParent( JspTag parent );

    /**
     * 返回此标记的父项，用于协作目的.
     *
     * @return 这个标记的父级
     */
    public JspTag getParent();

    /**
     * 由容器调用来提供这个标记处理程序.
     * 一个实现应该保存这个值.
     *
     * @param pc 此调用的页面上下文
     */
    public void setJspContext( JspContext pc );

    /**
     * 提供此标记的主体作为一个JspFragment对象, 能够由标记处理程序调用零次或多次.
     * <p>
     * 此方法由JSP页面实现对象调用，在<code>doTag()</code>之前. 如果页面中的action元素是空的, 这个方法永远不会被调用.
     *
     * @param jspBody 封装此标记主体的片段.
     */
    public void setJspBody( JspFragment jspBody );

}
