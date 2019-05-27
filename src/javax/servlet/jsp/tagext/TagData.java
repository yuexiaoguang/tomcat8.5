package javax.servlet.jsp.tagext;

import java.util.Hashtable;

/**
 * (仅在翻译时) 标记实例attribute/value信息.
 *
 * <p>
 * TagData仅作为TagExtraInfo的isValid, validate, getVariableInfo方法的参数, 在翻译时调用.
 */
public class TagData implements Cloneable {

    /**
     * 表示它的值是请求时间表达式(不再可用，因为TagData实例在翻译时使用).
     */
    public static final Object REQUEST_TIME_VALUE = new Object();


    /**
     * <pre>
     * static final Object[][] att = {{"connection", "conn0"}, {"id", "query0"}};
     * static final TagData td = new TagData(att);
     * </pre>
     *
     * 所有的值必须是String，除了 REQUEST_TIME_VALUE.
     * @param atts 静态属性和值. 可能是null.
     */
    public TagData(Object[] atts[]) {
        if (atts == null) {
            attributes = new Hashtable<>();
        } else {
            attributes = new Hashtable<>(atts.length);
        }

        if (atts != null) {
            for (int i = 0; i < atts.length; i++) {
                attributes.put((String) atts[i][0], atts[i][1]);
            }
        }
    }

    /**
     * @param attrs 保存值的hashtable.
     */
    public TagData(Hashtable<String, Object> attrs) {
        this.attributes = attrs;
    }

    /**
     * 标签的id 属性的值.
     */
    public String getId() {
        return getAttributeString(TagAttributeInfo.ID);
    }

    /**
     * 属性的值.
     * If a static value is specified for an attribute that accepts a
     * request-time attribute expression then that static value is returned,
     * even if the value is provided in the body of a &lt;jsp:attribute&gt;
     * action. The distinguished object REQUEST_TIME_VALUE is only returned if
     * the value is specified as a request-time attribute expression
     * or via the &lt;jsp:attribute&gt; action with a body that contains
     * dynamic content (scriptlets, scripting expressions, EL expressions,
     * standard actions, or custom actions).  Returns null if the attribute
     * is not set.
     *
     * @param attName 属性名称
     * @return 属性的值
     */
    public Object getAttribute(String attName) {
        return attributes.get(attName);
    }

    /**
     * 设置属性的值.
     *
     * @param attName 属性名称
     * @param value 属性的值.
     */
    public void setAttribute(String attName,
                             Object value) {
        attributes.put(attName, value);
    }

    /**
     * 获取给定属性的值.
     *
     * @param attName 属性名称
     * @return 属性的值
     * @throws ClassCastException 如果属性值不是String
     */
    public String getAttributeString(String attName) {
        Object o = attributes.get(attName);
        if (o == null) {
            return null;
        }
        return (String) o;
    }

    /**
     * 枚举属性.
     *
     * @return TagData中的属性的枚举
     */
    public java.util.Enumeration<String> getAttributes() {
        return attributes.keys();
    }

    private final Hashtable<String, Object> attributes;        // the tagname/value map
}
