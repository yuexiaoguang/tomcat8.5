package javax.servlet.jsp.tagext;

/**
 * 关于标签属性的信息, 在翻译时可用. 这个类从Tag Library Descriptor文件(TLD)实例化.
 *
 * <p>
 * 这里只包含生成代码所需的信息. 其他信息，例如SCHEMA 验证属于别处.
 */
public class TagAttributeInfo {
    public static final String ID = "id";

    /**
     * @param name 属性名.
     * @param required 标签实例中是否需要这个属性.
     * @param type 属性类型的名称.
     * @param reqTime 这个属性是否保存了一个 request-time属性.
     */
    public TagAttributeInfo(String name, boolean required, String type,
            boolean reqTime) {
        this(name, required, type, reqTime, false);
    }

    /**
     * @param name 属性名.
     * @param required 标签实例中是否需要这个属性.
     * @param type 属性类型的名称.
     * @param reqTime 这个属性是否保存了一个 request-time属性.
     * @param fragment 这个属性类型是否是JspFragment
     *
     * @since 2.0
     */
    public TagAttributeInfo(String name, boolean required, String type,
            boolean reqTime, boolean fragment) {
        this(name, required, type, reqTime, fragment, null, false, false, null, null);
    }

    /**
     * @param name 属性名.
     * @param required 标签实例中是否需要这个属性.
     * @param type 属性类型的名称.
     * @param reqTime 这个属性是否保存了一个 request-time属性.
     * @param fragment 这个属性类型是否是JspFragment
     * @param description 这个属性的描述
     * @param deferredValue 这个属性是否接受值表达式 作为属性值(作为String)，将其推迟到标记计算为止
     * @param deferredMethod 这个属性是否接受方法表达式 作为属性值(作为String)，将其推迟到标记计算为止
     * @param expectedTypeName 当计算延迟值时所期望的类型
     * @param methodSignature 如果延迟方法的预期方法签名
     *
     * @since JSP 2.1
     */
    public TagAttributeInfo(String name, boolean required, String type,
            boolean reqTime, boolean fragment, String description,
            boolean deferredValue, boolean deferredMethod,
            String expectedTypeName, String methodSignature) {
        this.name = name;
        this.required = required;
        this.type = type;
        this.reqTime = reqTime;
        this.fragment = fragment;
        this.description = description;
        this.deferredValue = deferredValue;
        this.deferredMethod = deferredMethod;
        this.expectedTypeName = expectedTypeName;
        this.methodSignature = methodSignature;
    }

    /**
     * 此属性的名称.
     */
    public String getName() {
        return name;
    }

    /**
     * 属性类型的名称.
     */
    public String getTypeName() {
        return type;
    }

    /**
     * 这个属性是否保存了一个 request-time属性.
     */
    public boolean canBeRequestTime() {
        return reqTime;
    }

    /**
     * 标签实例中是否需要这个属性.
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * 查找"id".
     *
     * @param a TagAttributeInfo数组
     * @return 名称是"id"的TagAttributeInfo引用
     */
    public static TagAttributeInfo getIdAttribute(TagAttributeInfo a[]) {
        for (int i = 0; i < a.length; i++) {
            if (a[i].getName().equals(ID)) {
                return a[i];
            }
        }
        return null;
    }

    /**
     * 这个属性是否是JspFragment类型.
     *
     * @since 2.0
     */
    public boolean isFragment() {
        return fragment;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(64);
        b.append("name = " + name + " ");
        b.append("type = " + type + " ");
        b.append("reqTime = " + reqTime + " ");
        b.append("required = " + required + " ");
        b.append("fragment = " + fragment + " ");
        b.append("deferredValue = " + deferredValue + " ");
        b.append("expectedTypeName = " + expectedTypeName + " ");
        b.append("deferredMethod = " + deferredMethod + " ");
        b.append("methodSignature = " + methodSignature);
        return b.toString();
    }

    /*
     * private fields
     */
    private final String name;

    private final String type;

    private final boolean reqTime;

    private final boolean required;

    /*
     * private fields for JSP 2.0
     */
    private final boolean fragment;

    /*
     * private fields for JSP 2.1
     */
    private final String description;

    private final boolean deferredValue;

    private final boolean deferredMethod;

    private final String expectedTypeName;

    private final String methodSignature;

    public boolean isDeferredMethod() {
        return deferredMethod;
    }

    public boolean isDeferredValue() {
        return deferredValue;
    }

    public String getDescription() {
        return description;
    }

    public String getExpectedTypeName() {
        return expectedTypeName;
    }

    public String getMethodSignature() {
        return methodSignature;
    }
}
