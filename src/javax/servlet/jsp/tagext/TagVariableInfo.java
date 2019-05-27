package javax.servlet.jsp.tagext;

/**
 * 标记库中标记的变量信息; 这个类是从标记库描述符文件(TLD)实例化的，只在翻译时可用.
 * 这个对象应该是不可变的. 这个信息在JSP 1.2格式的顶级域名或以上才可用.
 */
public class TagVariableInfo {

    /**
     * @param nameGiven &lt;name-given&gt;的值
     * @param nameFromAttribute &lt;name-from-attribute&gt;的值
     * @param className &lt;variable-class&gt;的值
     * @param declare &lt;declare&gt;的值
     * @param scope &lt;scope&gt;的值
     */
    public TagVariableInfo(String nameGiven, String nameFromAttribute,
            String className, boolean declare, int scope) {
        this.nameGiven = nameGiven;
        this.nameFromAttribute = nameFromAttribute;
        this.className = className;
        this.declare = declare;
        this.scope = scope;
    }

    /**
     * &lt;name-given&gt; 元素的主体.
     *
     * @return 变量名作为常量
     */
    public String getNameGiven() {
        return nameGiven;
    }

    /**
     * &lt;name-from-attribute&gt; 元素的主体.
     * 提供变量名称的属性的名称. 需要&lt;name-given&gt; 或 &lt;name-from-attribute&gt; 其中之一.
     *
     * @return 其值定义变量名的属性
     */
    public String getNameFromAttribute() {
        return nameFromAttribute;
    }

    /**
     * &lt;variable-class&gt; 元素的主体.
     *
     * @return 变量的类名，或 'java.lang.String'如果没有在TLD中定义.
     */
    public String getClassName() {
        return className;
    }

    /**
     * &lt;declare&gt; 元素的主体.
     *
     * @return 是否要声明变量. 如果在TLD中未定义, 将返回'true'.
     */
    public boolean getDeclare() {
        return declare;
    }

    /**
     * &lt;scope&gt; 元素的主体.
     *
     * @return 变量的作用域. 将返回NESTED作用域， 如果在TLD中未定义.
     */
    public int getScope() {
        return scope;
    }

    /*
     * private fields
     */
    private final String nameGiven; // <name-given>
    private final String nameFromAttribute; // <name-from-attribute>
    private final String className; // <class>
    private final boolean declare; // <declare>
    private final int scope; // <scope>
}
