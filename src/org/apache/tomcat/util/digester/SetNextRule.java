package org.apache.tomcat.util.digester;

import org.apache.tomcat.util.IntrospectionUtils;


/**
 * <p>在(top-1)(父)对象上调用方法, 传递顶部对象（子）作为参数.  它通常用于建立父子关系.</p>
 *
 * <p>此规则现在默认支持更灵活的方法匹配. 这可能会破坏（某些）针对1.1.1或更早版本编写的代码.
 * See {@link #isExactMatch()} for more details.</p>
 */
public class SetNextRule extends Rule {

    // ----------------------------------------------------------- Constructors

    /**
     * @param methodName 要调用的父方法的方法名称
     * @param paramType 父方法的参数的Java类
     *  (如果你想使用原始类型, 请改为指定相应的Java包装类, 例如<code>java.lang.Boolean</code>对应于<code>boolean</code> 参数)
     */
    public SetNextRule(String methodName,
                       String paramType) {

        this.methodName = methodName;
        this.paramType = paramType;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 要在父对象上调用的方法名称.
     */
    protected String methodName = null;


    /**
     * 方法所期望的参数类型的Java类名称.
     */
    protected String paramType = null;

    /**
     * 应该使用精确匹配. 默认不是.
     */
    protected boolean useExactMatch = false;

    // --------------------------------------------------------- Public Methods


    /**
     * <p>是否使用了精确匹配.</p>
     *
     * <p>此规则使用<code>org.apache.commons.beanutils.MethodUtils</code>反射相关对象，以便可以调用正确的方法.
     * 本来, 使用<code>MethodUtils.invokeExactMethod</code>. 这是非常严格的匹配，因此当存在匹配方法时，可能找不到匹配方法.
     * 启用精确匹配时，这仍然是行为.</p>
     *
     * <p>禁用精确匹配时, 使用<code>MethodUtils.invokeMethod</code>.
     * 当有多种方法具有正确的签名时，此方法可以找到更多方法但不太精确. 因此，如果要选择精确的签名，则可能需要启用此属性.</p>
     *
     * <p>默认设置是禁用精确匹配.</p>
     *
     * @return true 启用精确匹配
     * @since Digester Release 1.1.1
     */
    public boolean isExactMatch() {
        return useExactMatch;
    }

    /**
     * <p>是否启用精确匹配.</p>
     *
     * <p>See {@link #isExactMatch()}.</p>
     *
     * @param useExactMatch 该规则应该使用精确的方法匹配
     * @since Digester Release 1.1.1
     */
    public void setExactMatch(boolean useExactMatch) {

        this.useExactMatch = useExactMatch;
    }

    /**
     * 处理此元素的结尾.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间, 则返回空字符串
     * @param name 如果解析器是命名空间感知的本地名称, 或者只是元素名称
     */
    @Override
    public void end(String namespace, String name) throws Exception {

        // 确定要使用的对象
        Object child = digester.peek(0);
        Object parent = digester.peek(1);
        if (digester.log.isDebugEnabled()) {
            if (parent == null) {
                digester.log.debug("[SetNextRule]{" + digester.match +
                        "} Call [NULL PARENT]." +
                        methodName + "(" + child + ")");
            } else {
                digester.log.debug("[SetNextRule]{" + digester.match +
                        "} Call " + parent.getClass().getName() + "." +
                        methodName + "(" + child + ")");
            }
        }

        // 调用指定的方法
        IntrospectionUtils.callMethod1(parent, methodName,
                child, paramType, digester.getClassLoader());
    }


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("SetNextRule[");
        sb.append("methodName=");
        sb.append(methodName);
        sb.append(", paramType=");
        sb.append(paramType);
        sb.append("]");
        return (sb.toString());
    }
}
