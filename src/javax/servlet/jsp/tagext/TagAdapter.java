package javax.servlet.jsp.tagext;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;

/**
 * 用来协作classic Tag handlers 和 SimpleTag handlers.
 * <p>
 * 因为SimpleTag 不继承 Tag, 而且Tag.setParent()只接受 Tag 实例, 一个classic tag handler (实现了Tag)不能让SimpleTag作为它的父级.
 * 为了补救这个, 创建一个TagAdapter包装SimpleTag父级, 这个适配器将会传递给setParent().
 * A classic Tag Handler可以调用getAdaptee()检索封装的 SimpleTag实例.
 */
public class TagAdapter implements Tag {
    /** 被适配的simple tag. */
    private final SimpleTag simpleTagAdaptee;

    /** 这个标签的父级, 转换为类型标记. */
    private Tag parent;

    //是否已经确定了父级
    private boolean parentDetermined;

    /**
     * @param adaptee 适配SimpleTag为一个Tag.
     */
    public TagAdapter(SimpleTag adaptee) {
        if (adaptee == null) {
            // Cannot wrap a null adaptee.
            throw new IllegalArgumentException();
        }
        this.simpleTagAdaptee = adaptee;
    }

    /**
     * 不能被调用.
     *
     * @param pc
     * @throws UnsupportedOperationException
     */
    @Override
    public void setPageContext(PageContext pc) {
        throw new UnsupportedOperationException(
                "Illegal to invoke setPageContext() on TagAdapter wrapper");
    }

    /**
     * 不能被调用. 此标记的父级是getAdaptee().getParent().
     *
     * @param parentTag
     * @throws UnsupportedOperationException
     */
    @Override
    public void setParent(Tag parentTag) {
        throw new UnsupportedOperationException(
                "Illegal to invoke setParent() on TagAdapter wrapper");
    }

    /**
     * 返回此标记的父级, 总是getAdaptee().getParent().
     *
     * @return 被适配的标签的父级.
     */
    @Override
    public Tag getParent() {
        if (!parentDetermined) {
            JspTag adapteeParent = simpleTagAdaptee.getParent();
            if (adapteeParent != null) {
                if (adapteeParent instanceof Tag) {
                    this.parent = (Tag) adapteeParent;
                } else {
                    // 必须是SimpleTag - 没有定义其他类型.
                    this.parent = new TagAdapter((SimpleTag) adapteeParent);
                }
            }
            parentDetermined = true;
        }
        return this.parent;
    }

    /**
     * 被适配为Tag接口的标记.
     * 在JSP 2.0中应该为SimpleTag实例, 但是在未来的规范版本中还有其他类型的标签.
     *
     * @return 被适配的标记
     */
    public JspTag getAdaptee() {
        return this.simpleTagAdaptee;
    }

    /**
     * @throws UnsupportedOperationException
     * @throws JspException
     *             never thrown
     */
    @Override
    public int doStartTag() throws JspException {
        throw new UnsupportedOperationException(
                "Illegal to invoke doStartTag() on TagAdapter wrapper");
    }

    /**
     * @throws UnsupportedOperationException
     * @throws JspException
     *             never thrown
     */
    @Override
    public int doEndTag() throws JspException {
        throw new UnsupportedOperationException(
                "Illegal to invoke doEndTag() on TagAdapter wrapper");
    }

    /**
     * @throws UnsupportedOperationException
     */
    @Override
    public void release() {
        throw new UnsupportedOperationException(
                "Illegal to invoke release() on TagAdapter wrapper");
    }
}
