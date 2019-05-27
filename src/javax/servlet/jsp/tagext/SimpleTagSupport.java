package javax.servlet.jsp.tagext;

import java.io.IOException;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspException;

/**
 * 定义一个标签处理程序实现SimpleTag的基类.
 * <p>
 * SimpleTagSupport类是一个实用类，用于作为新的简单标记处理程序的基类.
 */
public class SimpleTagSupport implements SimpleTag {
    /** 对封闭标签的引用. */
    private JspTag parentTag;

    /** 标记执行的JSP上下文. */
    private JspContext jspContext;

    /** 标记主体. */
    private JspFragment jspBody;

    public SimpleTagSupport() {
        // NOOP by default
    }

    /**
     * 标记的默认处理不起作用.
     *
     * @throws JspException 子类可以抛出JspException 指示在处理此标记时发生的错误.
     * @throws javax.servlet.jsp.SkipPageException 如果页面（直接或间接）调用了调用此片段的标记处理程序将停止计算.
     * 容器必须抛出此异常, 如果 Classic Tag Handler返回Tag.SKIP_PAGE，或者如果Simple Tag Handler抛出 SkipPageException.
     * @throws IOException 子类可以抛出IOException， 如果写入输出流出错
     */
    @Override
    public void doTag() throws JspException, IOException {
        // NOOP by default
    }

    /**
     * 为协作目的设置此标记的父级.
     * <p>
     * 只有当这个标记调用嵌套在另一个标记调用中时，容器才会调用此方法.
     *
     * @param parent 包围此标记的标记
     */
    @Override
    public void setParent( JspTag parent ) {
        this.parentTag = parent;
    }

    /**
     * 返回此标记的父项，用于协作目的.
     *
     * @return 这个标记的父级
     */
    @Override
    public JspTag getParent() {
        return this.parentTag;
    }

    /**
     * 保存提供的JSP上下文.
     *
     * @param pc 此调用的页面上下文
     */
    @Override
    public void setJspContext( JspContext pc ) {
        this.jspContext = pc;
    }

    /**
     * 返回容器传入的页面上下文.
     *
     * @return 此调用的页面上下文
     */
    protected JspContext getJspContext() {
        return this.jspContext;
    }

    /**
     * 保存提供的JspFragment.
     *
     * @param jspBody 封装此标记主体的片段. 如果页面中的action元素是空的, 永远不会调用这个方法.
     */
    @Override
    public void setJspBody( JspFragment jspBody ) {
        this.jspBody = jspBody;
    }

    /**
     * 返回容器传入的主体.
     *
     * @return 封装此标记主体的片段, 或者null.
     */
    protected JspFragment getJspBody() {
        return this.jspBody;
    }

    /**
     * 查找与给定实例最接近的给定类类型的实例.
     * 这个方法使用Tag或SimpleTag接口的getParent方法.
     *
     * <p>
     * 规范的当前版本只提供了一种形式化的方式来指示标记处理程序的可观察类型: 它的标记处理程序实现类, 使用标签元素的tag-class子元素指定.
     * 类型应该是标记处理程序实现类的一个子类型或空的.
     *
     * @param from 从哪里开始查找的实例.
     * @param klass JspTag 或匹配的接口的子类
     * @return 实现接口的最近的祖先，或指定的类的实例
     */
    public static final JspTag findAncestorWithClass(
        JspTag from, Class<?> klass)
    {
        boolean isInterface = false;

        if (from == null || klass == null || (!JspTag.class.isAssignableFrom(klass) &&
                !(isInterface = klass.isInterface()))) {
            return null;
        }

        for (;;) {
            JspTag parent = null;
            if( from instanceof SimpleTag ) {
                parent = ((SimpleTag)from).getParent();
            }
            else if( from instanceof Tag ) {
                parent = ((Tag)from).getParent();
            }
            if (parent == null) {
                return null;
            }

            if (parent instanceof TagAdapter) {
                parent = ((TagAdapter) parent).getAdaptee();
            }

            if ((isInterface && klass.isInstance(parent)) ||
                    klass.isAssignableFrom(parent.getClass())) {
                return parent;
            }

            from = parent;
        }
    }
}
