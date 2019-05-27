package javax.servlet.jsp.tagext;

import javax.servlet.jsp.JspException;

/**
 * 用于标签，声明它接受动态属性, 它必须实现这个接口.
 * 标记库描述符中标记的条目也必须配置为表示接受动态属性.
 * <br>
 * 对于此标记的标记库描述符中未声明的任何属性, 而不是在翻译时出错, 调用<code>setDynamicAttribute()</code>方法,
 * 具有属性的名称和值. 标记的作用是记住动态属性的名称和值.
 */
public interface DynamicAttributes {

    /**
     * 当声明接受动态属性的标签时，将传递一个未在标记库描述符中声明的属性.
     *
     * @param uri 属性的命名空间, 或者null 使用默认的命名空间.
     * @param localName 正在设置的属性的名称.
     * @param value 属性的值
     * @throws JspException 如果标记处理程序希望发出信号，它不接受给定属性. 容器不能对这个标签调用doStartTag() 或 doTag().
     */
    public void setDynamicAttribute(
        String uri, String localName, Object value )
        throws JspException;

}
