package javax.servlet.jsp.tagext;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;

/**
 * 用于定义实现标记的新标记处理程序的基类.
 *
 * <p>TagSupport 类是一个实用工具类，用于作为新的标记处理程序的基类.
 * TagSupport 类实现了Tag 和 IterationTag接口，并添加了其他便利方法，包括Tag中属性的 getter方法.
 * TagSupport有一个静态的方法，以便于协作标记之间的协调.
 *
 * <p>很多标签处理程序将继承TagSupport，并重定义了少量的方法.
 */
public class TagSupport implements IterationTag, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 查找与给定实例最接近的给定类类型的实例.
     * 这个方法使用Tag接口的getParent方法.此方法用于协作标记之间的协调.
     *
     * @param from 从哪里开始查找的实例.
     * @param klass Tag 或匹配的接口的子类
     * @return 实现接口的最近的祖先，或指定的类的实例
     */
    public static final Tag findAncestorWithClass(Tag from,
            // TCK signature test fails with generics
            @SuppressWarnings("rawtypes")
            Class klass) {
        boolean isInterface = false;

        if (from == null ||
            klass == null ||
            (!Tag.class.isAssignableFrom(klass) &&
             !(isInterface = klass.isInterface()))) {
            return null;
        }

        for (;;) {
            Tag tag = from.getParent();

            if (tag == null) {
                return null;
            }

            if ((isInterface && klass.isInstance(tag)) ||
                    ((Class<?>)klass).isAssignableFrom(tag.getClass())) {
                return tag;
            }
            from = tag;
        }
    }

    public TagSupport() {
        // NOOP by default
    }

    /**
     * 开始标记的默认处理, 返回 SKIP_BODY.
     *
     * @return SKIP_BODY
     * @throws JspException 如果在处理此标记时发生错误
     */
    @Override
    public int doStartTag() throws JspException {
        return SKIP_BODY;
    }

    /**
     * 结束标记的默认处理，返回 EVAL_PAGE.
     *
     * @return EVAL_PAGE
     * @throws JspException 如果在处理此标记时发生错误
     */
    @Override
    public int doEndTag() throws JspException {
        return EVAL_PAGE;
    }


    /**
     * 主体的默认处理.
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
        parent = null;
        id = null;
        if( values != null ) {
            values.clear();
        }
        values = null;
    }

    /**
     * 设置此标记的嵌套标记.
     *
     * @param t 父级标记.
     */
    @Override
    public void setParent(Tag t) {
        parent = t;
    }

    /**
     * 父级标记.
     *
     * @return 父级标记或 null
     */
    @Override
    public Tag getParent() {
        return parent;
    }

    /**
     * 设置这个标签的id 属性.
     *
     * @param id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 这个标签的id 属性的值; 或null.
     */
    public String getId() {
        return id;
    }

    /**
     * 设置页面上下文.
     *
     * @param pageContext The PageContext.
     */
    @Override
    public void setPageContext(PageContext pageContext) {
        this.pageContext = pageContext;
    }

    /**
     * 设置属性.
     *
     * @param k The key String.
     * @param o The value to associate.
     */
    public void setValue(String k, Object o) {
        if (values == null) {
            values = new Hashtable<>();
        }
        values.put(k, o);
    }

    /**
     * 获取指定key关联的值.
     *
     * @param k The string key.
     * @return 关联的值, 或 null.
     */
    public Object getValue(String k) {
        if (values == null) {
            return null;
        }
        return values.get(k);
    }

    /**
     * 移除与键关联的值.
     *
     * @param k The string key.
     */
    public void removeValue(String k) {
        if (values != null) {
            values.remove(k);
        }
    }

    /**
     * 枚举此标记处理程序保存的值的键.
     *
     * @return 所有键的枚举, 或 null 或一个空枚举.
     */
    public Enumeration<String> getValues() {
        if (values == null) {
            return null;
        }
        return values.keys();
    }

    // private fields

    private   Tag         parent;
    private   Hashtable<String, Object>   values;
    
    /**
     * 此标记的id属性的值; 或 null.
     */
    protected String      id;

    // protected fields

    /**
     * The PageContext.
     */
    protected transient PageContext pageContext;
}

