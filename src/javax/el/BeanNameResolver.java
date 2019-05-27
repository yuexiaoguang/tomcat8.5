package javax.el;

/**
 * 基本实现类，提供由应用程序开发人员扩展的最小默认实现.
 */
public abstract class BeanNameResolver {

    /**
     * 这个解析程序能解析给定的bean名称吗?
     *
     * @param beanName 要解析的bean 名称
     *
     * @return 这个默认实现总是返回<code>false</code>
     */
    public boolean isNameResolved(String beanName) {
        return false;
    }


    /**
     * 返回命名的bean.
     *
     * @param beanName 要返回的bean 名称
     *
     * @return 这个默认实现总是返回<code>null</code>
     */
    public Object getBean(String beanName) {
        return null;
    }


    /**
     * 设置给定名称的bean的值. 如果命名的bean不存在, 并且{@link #canCreateBean}返回<code>true</code>然后用给定值创建bean.
     *
     * @param beanName 设置/创建bean的名称
     * @param value    设置/创建bean的值
     *
     * @throws PropertyNotWritableException 如果bean是只读的
     */
    public void setBeanValue(String beanName, Object value)
            throws PropertyNotWritableException {
        throw new PropertyNotWritableException();
    }


    /**
     * 命名bean是只读的吗?
     *
     * @param beanName 感兴趣的bean的名称
     *
     * @return <code>true</code>如果bean是只读的, 否则<code>false</code>
     */
    public boolean isReadOnly(String beanName) {
        return true;
    }


    /**
     * 允许创建给定名称的bean吗?
     *
     * @param beanName 感兴趣的bean的名称
     *
     * @return <code>true</code>如果可以创建bean, 否则<code>false</code>
     */
    public boolean canCreateBean(String beanName) {
        return false;
    }
}
