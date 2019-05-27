package org.apache.catalina.ha.session;
import org.apache.catalina.ha.ClusterMessage;

/**
 * 当在Tomcat集群节点中创建、修改、过期会话时使用SessionMessage类.<BR>
 *
 * 下列事件当前可用:
 * <ul>
 *   <li><pre>public static final int EVT_SESSION_CREATED</pre><li>
 *   <li><pre>public static final int EVT_SESSION_EXPIRED</pre><li>
 *   <li><pre>public static final int EVT_SESSION_ACCESSED</pre><li>
 *   <li><pre>public static final int EVT_GET_ALL_SESSIONS</pre><li>
 *   <li><pre>public static final int EVT_SESSION_DELTA</pre><li>
 *   <li><pre>public static final int EVT_ALL_SESSION_DATA</pre><li>
 *   <li><pre>public static final int EVT_ALL_SESSION_TRANSFERCOMPLETE</pre><li>
 *   <li><pre>public static final int EVT_CHANGE_SESSION_ID</pre><li>
 *   <li><pre>public static final int EVT_ALL_SESSION_NOCONTEXTMANAGER</pre><li>
 * </ul>
 *
 */
public interface SessionMessage extends ClusterMessage {

    /**
     * 在节点上创建会话时使用的事件类型
     */
    public static final int EVT_SESSION_CREATED = 1;
    /**
     * 会话已过期时使用的事件类型
     */
    public static final int EVT_SESSION_EXPIRED = 2;

    /**
     * 访问会话时使用的事件类型(即，更新最后更新时间时). 这是为了使复制的会话不会在网络上过期
     */
    public static final int EVT_SESSION_ACCESSED = 3;
    /**
     * 服务器首次联机时使用的事件类型.
     * 新启动服务器想要做的第一件事是从一个节点抓取所有会话并保持相同的状态
     */
    public static final int EVT_GET_ALL_SESSIONS = 4;
    /**
     * 当属性添加到会话时使用的事件类型, 属性将被发送到集群中的所有其他节点
     */
    public static final int EVT_SESSION_DELTA  = 13;

    /**
     * 当传输会话状态时使用的事件类型.
     */
    public static final int EVT_ALL_SESSION_DATA = 12;

    /**
     * 当会话状态完成传输时使用的事件类型.
     */
    public static final int EVT_ALL_SESSION_TRANSFERCOMPLETE = 14;

    /**
     * 更改Session ID时使用的事件类型.
     */
    public static final int EVT_CHANGE_SESSION_ID = 15;

    /**
     * 上下文管理器不存在时使用的事件类型.
     * 这在发送会话状态的管理器不存在时使用.
     */
    public static final int EVT_ALL_SESSION_NOCONTEXTMANAGER = 16;

    public String getContextName();

    public String getEventTypeString();

    /**
     * 返回事件类型
     * 
     * @return 事件类型EVT_XXXX之一
     */
    public int getEventType();
    /**
     * @return 会话的序列化数据
     */
    public byte[] getSession();
    /**
     * @return 会话ID
     */
    public String getSessionID();

}
