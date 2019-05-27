package org.apache.tomcat.dbcp.pool2.impl;

/**
 * 将通过JMX公开的池对象的信息的接口.
 *
 * NOTE: 此接口仅用于定义将通过JMX提供的属性和方法. 它不能由客户端实现，因为它可能会在公共池的主要版本，次要版本和补丁版本之间发生更改.
 * 		 因此，实现此接口的客户端可能无法升级到新的次要版本或修补程序版本，而无需更改代码.
 */
public interface DefaultPooledObjectInfoMBean {
    /**
     * 获取创建池的对象的时间(使用与{@link System#currentTimeMillis()}相同的基础).
     *
     * @return The creation time for the pooled object
     */
    long getCreateTime();

    /**
     * 获取池的对象的创建时间.
     *
     * @return 创建时间格式为: <code>yyyy-MM-dd HH:mm:ss Z</code>
     */
    String getCreateTimeFormatted();

    /**
     * 获取上次轮询的对象被借用的时间 (类似于 {@link System#currentTimeMillis()}).
     *
     * @return 池中的对象上次被借用的时间
     */
    long getLastBorrowTime();

    /**
     * 获取池中的对象上次被借用的时间.
     *
     * @return 时间格式为: <code>yyyy-MM-dd HH:mm:ss Z</code>
     */
    String getLastBorrowTimeFormatted();

    /**
     * 获取上次借用池中的对象时记录的堆栈跟踪.
     *
     * @return 堆栈跟踪显示最后借用池化对象的代码
     */
    String getLastBorrowTrace();


    /**
     * 获取上次返回封装的对象的时间 (类似于{@link System#currentTimeMillis()}).
     *
     * @return 对象最后返回的时间
     */
    long getLastReturnTime();

    /**
     * 获取池中的对象最后返回的时间.
     *
     * @return 时间格式为: <code>yyyy-MM-dd HH:mm:ss Z</code>
     */
    String getLastReturnTimeFormatted();

    /**
     * 获取池中的对象的类名.
     */
    String getPooledObjectType();

    /**
     * 提供包装器的String形式以用于调试. 格式不固定，可能随时更改.
     */
    String getPooledObjectToString();

    /**
     * 获取此对象借用的次数.
     */
    long getBorrowedCount();
}
