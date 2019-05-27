package org.apache.tomcat.dbcp.pool2;

/**
 * 提供{@link PooledObject}可能存在的状态.
 */
public enum PooledObjectState {
    /**
     * 在队列中, 没有使用.
     */
    IDLE,

    /**
     * 正在使用.
     */
    ALLOCATED,

    /**
     * 在队列中, 目前正在测试可能的驱逐.
     */
    EVICTION,

    /**
     * 不在队列中, 目前正在测试可能的驱逐. 在测试时尝试借用该对象, 将其从队列中删除. 逐出测试完成后, 应将其返回到队列的顶部.
     * TODO: 考虑分配对象并忽略驱逐测试的结果.
     */
    EVICTION_RETURN_TO_HEAD,

    /**
     * 在队列中, 目前正在验证中.
     */
    VALIDATION,

    /**
     * 不在队列中, 目前正在验证中. 该对象在验证时借用, 因为testOnBorrow已配置, 它已从队列中删除并预先分配. 验证完成后应分配它.
     */
    VALIDATION_PREALLOCATED,

    /**
     * 不在队列中, 目前正在验证中. 尝试借用该对象时, 先前正在测试驱逐, 从而将其从队列中删除了. 验证完成后, 应将其返回到队列的顶部.
     */
    VALIDATION_RETURN_TO_HEAD,

    /**
     * 维护失败（例如驱逐测试或验证）, 将被销毁
     */
    INVALID,

    /**
     * 废弃, 失效.
     */
    ABANDONED,

    /**
     * 返回到池中.
     */
    RETURNING
}
