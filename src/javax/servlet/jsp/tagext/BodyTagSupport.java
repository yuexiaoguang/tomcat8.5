package javax.servlet.jsp.tagext;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;

/**
 * 定义实现了BodyTag的标签处理程序的基类.
 * <p>
 * 很多标签处理程序奖继承 BodyTagSupport而且重新定义了一些方法.
 */
public class BodyTagSupport extends TagSupport implements BodyTag {

    private static final long serialVersionUID = -7235752615580319833L;

    public BodyTagSupport() {
        super();
    }

    /**
     * 开始标记的默认处理.
     *
     * @return EVAL_BODY_BUFFERED
     * @throws JspException 如果在处理此标记时发生错误
     */
    @Override
    public int doStartTag() throws JspException {
        return EVAL_BODY_BUFFERED;
    }

    /**
     * 结束标记的默认处理.
     *
     * @return EVAL_PAGE
     * @throws JspException 如果在处理此标记时发生错误
     */
    @Override
    public int doEndTag() throws JspException {
        return super.doEndTag();
    }

    /**
     * 准备主体的计算: 隐藏bodyContent.
     *
     * @param b
     */
    @Override
    public void setBodyContent(BodyContent b) {
        this.bodyContent = b;
    }

    /**
     * 在第一个主体计算之前，准备主体的计算: 没有动作.
     *
     * @throws JspException 如果在处理此标记时发生错误
     */
    @Override
    public void doInitBody() throws JspException {
        // NOOP by default
    }

    /**
     * 主体计算之后: 不要重新评估和继续页面.
     *
     * @return SKIP_BODY
     * @throws JspException 如果在处理此标记时发生错误
     */
    @Override
    public int doAfterBody() throws JspException {
        return SKIP_BODY;
    }

    /**
     * 释放状态.
     */
    @Override
    public void release() {
        bodyContent = null;
        super.release();
    }

    /**
     * 获取当前的bodyContent.
     */
    public BodyContent getBodyContent() {
        return bodyContent;
    }

    /**
     * 获取JspWriter.
     */
    public JspWriter getPreviousOut() {
        return bodyContent.getEnclosingWriter();
    }

    /**
     * 这个BodyTag的当前 BodyContent.
     */
    protected transient BodyContent bodyContent;
}
