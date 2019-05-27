package org.apache.tomcat.dbcp.pool2;

/**
 * 此接口允许池化的对象提供有关何时以及如何使用对象池的可用信息.
 * 对象池可以, 但不需要, 在确定池化对象的状态时, 使用此信息做出更明智的决策 - 例如, 对象是否已被废弃.
 */
public interface TrackedUse {

    /**
     * 获取对象的最后使用时间, ms.
     */
    long getLastUsed();
}
