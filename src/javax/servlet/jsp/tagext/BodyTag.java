package javax.servlet.jsp.tagext;

import javax.servlet.jsp.JspException;


/**
 * 让标记处理程序操作计算其主体的内容.
 * <p>
 * 标记处理程序负责操作主体内容. 例如，标记处理程序可以获取正文内容, 并使用bodyContent.getString方法将其转换为.
 * 或者标记处理程序可以获取正文内容并将其写入它的JspWriter，使用bodyContent.writeOut方法.
 * <p>
 * 实现BodyTag的标记处理程序被视为实现了IterationTag, 除了可以返回SKIP_BODY, EVAL_BODY_INCLUDE 或 EVAL_BODY_BUFFERED的doStartTag方法.
 * <p>
 * 如果返回EVAL_BODY_INCLUDE, 那么计算将发生在IterationTag中.
 * <p>
 * 如果返回EVAL_BODY_BUFFERED, 那么将创建一个BodyContent对象(由JSP编译器生成的代码)进行主体的计算.
 * 由JSP编译器生成的代码通过调用当前pageContext的pushBody方法获取BodyContent对象, 另外，它具有保存先前输出值的效果.
 * 页面编译器通过调用PageContext类的popBody方法返回这个对象; 调用也恢复了输出值.
 * <p>
 * 该接口提供了一个新属性，即 setter方法和一个新的action方法.
 * <p>
 */
public interface BodyTag extends IterationTag {

    /**
     * EVAL_BODY_BUFFERED 和 EVAL_BODY_AGAIN. 被标记为过时的鼓励不同术语的使用, 更具描述性.
     *
     * @deprecated As of Java JSP API 1.2, use BodyTag.EVAL_BODY_BUFFERED or
     *             IterationTag.EVAL_BODY_AGAIN.
     */
    @SuppressWarnings("dep-ann")
    // TCK signature test fails with annotation
    public static final int EVAL_BODY_TAG = 2;

    /**
     * 请求创建新的缓冲区, 在这个缓冲区上BodyContent计算这个标签的主体.
     * 如果它实现了BodyTag，会从doStartTag返回. 如果类没有实现BodyTag，将是一个不合法的返回值.
     */
    public static final int EVAL_BODY_BUFFERED = 2;

    /**
     * 设置bodyContent属性.
     * 这个方法通过JSP页面实现类对象调用，每次动作调用最多一次. 这个方法将在doInitBody之前执行.
     * 这个方法不会执行，如果是空标签或标签的doStartTag()方法返回SKIP_BODY或EVAL_BODY_INCLUDE的非空标签.
     * <p>
     * 当setBodyContent执行的时候, pageContext对象中的隐式对象的值已经改变.
     * 传递的BodyContent对象将不会有关于它的数据，但可能已经从以前的调用中重用（并清除）了.
     * <p>
     * BodyContent对象是可用的并有适当的内容，直到调用doEndTag方法之后, 在这种情况下，它可以被重用.
     *
     * @param b 
     */
    void setBodyContent(BodyContent b);

    /**
     * 准备计算主体.
     * 这个方法通过JSP页面实现类调用，在setBodyContent之后和第一次计算主体之前.
     * 这个方法不会执行，如果是空标签或标签的doStartTag()方法返回SKIP_BODY或EVAL_BODY_INCLUDE的非空标签.
     * <p>
     * JSP容器将重新同步AT_BEGIN 和NESTED变量的值(由关联的TagExtraInfo 或 TLD定义)，在调用doInitBody()之后.
     *
     * @throws JspException 如果在处理此标记时发生错误
     */
    void doInitBody() throws JspException;
}
