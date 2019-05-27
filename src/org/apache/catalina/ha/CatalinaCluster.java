package org.apache.catalina.ha;

import java.util.Map;

import org.apache.catalina.Cluster;
import org.apache.catalina.Manager;
import org.apache.catalina.Valve;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.Member;


/**
 * <b>CatalinaCluster</b>接口允许插入和退出不同的集群实现
 */
public interface CatalinaCluster extends Cluster {
    // ----------------------------------------------------- Instance Variables

    /**
     * 向集群中的所有成员发送消息
     * 
     * @param msg ClusterMessage
     */
    public void send(ClusterMessage msg);

    /**
     * 向集群中的特定成员发送消息.
     *
     * @param msg ClusterMessage
     * @param dest Member
     */
    public void send(ClusterMessage msg, Member dest);

    /**
     * @return <code>true</code>如果集群有成员.
     */
    public boolean hasMembers();

    /**
     * @return 包含当前参与集群的所有成员的数组.
     */
    public Member[] getMembers();

    /**
     * @return 表示此节点的成员.
     */
    public Member getLocalMember();

    public void addValve(Valve valve);

    public void addClusterListener(ClusterListener listener);

    public void removeClusterListener(ClusterListener listener);

    public void setClusterDeployer(ClusterDeployer deployer);

    public ClusterDeployer getClusterDeployer();

    /**
     * @return The map of managers
     */
    public Map<String,ClusterManager> getManagers();

    /**
     * 获取Manager
     * 
     * @param name 管理器名称
     * @return The manager
     */
    public Manager getManager(String name);

    /**
     * 为管理器获取新的集群名称.
     * 
     * @param name 重写名称(可选)
     * @param manager 管理器
     * @return 集群中的管理器名称
     */
    public String getManagerName(String name, Manager manager);

    public Valve[] getValves();

    public void setChannel(Channel channel);

    public Channel getChannel();

}
