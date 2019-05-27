package javax.el;

import java.util.Iterator;

public abstract class ELResolver {

    public static final String TYPE = "type";

    public static final String RESOLVABLE_AT_DESIGN_TIME = "resolvableAtDesignTime";

    /**
     * @param context 求值使用的EL 上下文
     * @param base 要找到属性的基对象
     * @param property 要返回其值的属性
     * @return 提供的属性的值
     * @throws NullPointerException 如果提供的上下文是<code>null</code>
     * @throws PropertyNotFoundException 如果提供给解析器的基/属性组合是解析器可以处理的，但找不到匹配或找到匹配但不可读
     * @throws ELException 在解析属性时包装任何异常抛出
     */
    public abstract Object getValue(ELContext context, Object base, Object property);

    /**
     * 执行给定对象的方法. 默认的实现总是返回<code>null</code>.
     *
     * @param context    求值使用的EL 上下文
     * @param base       要找到该方法的基本对象
     * @param method     要执行的方法
     * @param paramTypes 要执行的方法的参数的类型
     * @param params     要执行的方法的参数
     *
     * @return 总是<code>null</code>
     */
    public Object invoke(ELContext context, Object base, Object method,
            Class<?>[] paramTypes, Object[] params) {
        return null;
    }

    /**
     * @param context 求值使用的EL 上下文
     * @param base 要找到属性的基本对象
     * @param property 要返回其类型的属性
     * @return 所提供属性的类型
     * @throws NullPointerException 如果提供的上下文是<code>null</code>
     * @throws PropertyNotFoundException 如果提供给解析器的基本/属性组合是解析器可以处理的，但找不到匹配或找到匹配但不可读
     * @throws ELException 在解析属性时包装任何异常抛出
     */
    public abstract Class<?> getType(ELContext context, Object base, Object property);

    /**
     * @param context  求值使用的EL 上下文
     * @param base     要找到属性的基本对象
     * @param property 要设置其值的属性
     * @param value    属性的值
     * @throws NullPointerException 如果提供的上下文是<code>null</code>
     * @throws PropertyNotFoundException 如果提供给解析器的基本/属性组合是解析器可以处理的，但找不到匹配
     * @throws PropertyNotWritableException 如果提供给解析器的基本/属性组合是解析器可以处理的，但匹配不可读
     * @throws ELException 在解析属性时包装任何异常抛出
     */
    public abstract void setValue(ELContext context, Object base,
            Object property, Object value);

    /**
     * @param context 求值使用的EL 上下文
     * @param base 要找到属性的基本对象
     * @param property 要检查只读状态的属性
     * @return <code>true</code>如果所识别的属性是只读的, 否则<code>false</code>
     * @throws NullPointerException 如果提供的上下文是<code>null</code>
     * @throws PropertyNotFoundException 如果提供给解析器的基本/属性组合是解析器可以处理的，但找不到匹配
     * @throws ELException 在解析属性时包装任何异常抛出
     */
    public abstract boolean isReadOnly(ELContext context, Object base,
            Object property);

    public abstract Iterator<java.beans.FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base);

    public abstract Class<?> getCommonPropertyType(ELContext context,
            Object base);

    /**
     * 将给定对象转换为给定类型. 默认的实现总是返回<code>null</code>.
     *
     * @param context 求值使用的EL 上下文
     * @param obj     要转换的对象
     * @param type    要转换对象的类型
     *
     * @return 总是<code>null</code>
     */
    public Object convertToType(ELContext context, Object obj, Class<?> type) {
        context.setPropertyResolved(false);
        return null;
    }
}
