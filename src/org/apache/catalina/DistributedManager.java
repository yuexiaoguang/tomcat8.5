package org.apache.catalina;

import java.util.Set;

/**
 * 会话管理器实现的接口，该接口不保存内存中所有会话的完整副本，但要知道每个会话的位置.
 * <p>
 * 使用BackupManager, 会话可以是主要的(此节点上的主副本), 备份(此节点上的备份副本) 或代理(只有这个节点上的会话ID).
 * 对于所有会话，包括代理会话，主节点和备份节点的标识是已知的.
 * <p>
 * 使用StoreManager 实现类, 会话可以是主要的(会话在内存中) 或代理(会话在Store中).
 */
public interface DistributedManager {

    /**
     * 返回主、备份和代理的总会话数.
     *
     * @return  集群的总会话数量.
     */
    public int getActiveSessionsFull();

    /**
     * 返回所有会话ID（主、备份和代理）列表.
     *
     * @return  集群中完整的会话ID集合.
     */
    public Set<String> getSessionIdsFull();
}
