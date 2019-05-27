package org.apache.tomcat.util.net;

/**
 * 定义用于管理SSL会话的接口. 在单个回话上操作的管理者.
 */
public interface SSLSessionManager {
    /**
     * 使SSL会话无效
     */
    public void invalidateSession();
}
