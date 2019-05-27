package org.apache.catalina;

/**
 * PersistentManager 本来是个更好的名字，但这会与实现名称发生冲突.
 */
public interface StoreManager extends DistributedManager {

    /**
     * @return 管理此Manager的持久会话存储的Store对象.
     */
    Store getStore();

    /**
     * 从这个Manager的活动Session中删除Session, 但不是从Store. (由PersistentValve使用)
     *
     * @param session 要移除的Session
     */
    void removeSuper(Session session);
}
