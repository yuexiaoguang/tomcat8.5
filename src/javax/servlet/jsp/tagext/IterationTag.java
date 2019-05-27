package javax.servlet.jsp.tagext;

import javax.servlet.jsp.JspException;


/**
 * IterationTag接口继承了Tag， 定义了一个控制主体重新计算的额外的方法.
 *
 * <p>IterationTag 提供了一个新方法: <code>doAfterBody()</code>.
 *
 * <p>doAfterBody()方法在每个主体计算之后，不管其是否被重新计算，都会执行.
 * 如果doAfterBody()返回IterationTag.EVAL_BODY_AGAIN, 主体将被重新计算.
 * 如果doAfterBody()返回 Tag.SKIP_BODY, 主体将被跳过，将执行doEndTag().
 */
public interface IterationTag extends Tag {

    /**
     * 重新计算一些主体.
     *
     * 为了和JSP 1.1的兼容性, 这个值和被标记为过时的BodyTag.EVAL_BODY_TAG相同,
     */
    public static final int EVAL_BODY_AGAIN = 2;

    /**
     * 重新计算主体.
     *
     * <p>
     * 如果doAfterBody 返回 EVAL_BODY_AGAIN, 将重新计算主体(跟随另一个doAfterBody的执行).
     * 如果doAfterBody 返回 returns SKIP_BODY, 将不会计算主体, 而且将会调用doEndTag方法.
     *
     * <p>
     * 如果这个标记处理程序实现了 BodyTag ，而且 doAfterBody 返回SKIP_BODY, 输出的值将会使用 popBody方法保存到pageContext.
     *
     * <p>
     * 该方法重新调用可能会导致不同的行为，因为有可能是共享状态的一些变化, 或者是因为外部计算.
     *
     * <p>
     * JSP容器将将重新同步AT_BEGIN 和 NESTED 变量 (由关联的TagExtraInfo 或 TLD定义)，在调用 doAfterBody()之后.
     *
     * @return 是否需要对主体进行额外的计算
     * @throws JspException 如果在处理此标记时发生错误
     */
    int doAfterBody() throws JspException;
}
