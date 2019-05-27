package org.apache.catalina.ha;

import java.io.IOException;

import org.apache.catalina.Manager;
import org.apache.catalina.tribes.io.ReplicationStream;

/**
 * 所有集群管理器使用的公共接口.
 * 这样就可以有一种更可插拔的会话管理器来代替不同的算法.
 */
public interface ClusterManager extends Manager {

   /**
    * 从另一个节点接收到消息, 如果对接收复制消息感兴趣，这是需要实现的回调方法.
    * 
    * @param msg - 收到的信息.
    */
   public void messageDataReceived(ClusterMessage msg);

   /**
    * 当请求完成时, 复制阀门将通知管理器, 并且管理器将决定是否需要复制.
    * 如果需要复制, 管理器将创建会话消息，并将复制该会话消息. 集群决定它的发送位置.
    * 
    * @param sessionId - 刚刚完成的sessionId.
    * 
    * @return 要发送的SessionMessage.
    */
   public ClusterMessage requestCompleted(String sessionId);

   /**
    * 当管理器到期时，会话未绑定到请求.
    * 集群将周期性地请求一个应该过期的会话列表，并且应该跨线发送.
    * 
    * @return String[] 无效的会话
    */
   public String[] getInvalidatedSessions();

   /**
    * 返回管理器的名称, 在主机 /context名称和在引擎 hostname+/context.
    * @since 5.5.10
    */
   public String getName();

   /**
    * 设置管理器的名称, at host /context name and at engine hostname+/context
    * @param name 管理器的名称
    * @since 5.5.10
    */
   public void setName(String name);

   public CatalinaCluster getCluster();

   public void setCluster(CatalinaCluster cluster);

   /**
    * 打开流并使用对应的ClassLoader (Container), 切换线程上下文类加载器.
    *
    * @param data 数据
    * 
    * @return The object input stream
    * @throws IOException 发生的错误
    */
   public ReplicationStream getReplicationStream(byte[] data) throws IOException;

   public ReplicationStream getReplicationStream(byte[] data, int offset, int length) throws IOException;

   public boolean isNotifyListenersOnReplication();

   public ClusterManager cloneFromTemplate();
}
