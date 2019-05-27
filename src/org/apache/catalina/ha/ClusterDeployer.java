package org.apache.catalina.ha;

import java.io.File;
import java.io.IOException;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.tribes.ChannelListener;

/**
 * <b>ClusterDeployer</b>接口允许插入和退出不同的部署实现
 */
public interface ClusterDeployer extends ChannelListener {

    /**
     * 启动集群部署程序, 拥有的容器将调用此方法
     * 
     * @throws Exception - 如果未能启动集群
     */
    public void start() throws Exception;

    /**
     * 关闭集群部署程序, 拥有的容器将调用此方法
     * 
     * @throws LifecycleException 停止集群部署程序发生错误
     */
    public void stop() throws LifecycleException;

    /**
     * 安装新的Web应用程序, 其Web应用程序归档文件位于指定的URL, 使用指定的上下文名称将此容器和集群的所有其他成员放入该容器中.
     * <p>
     * 如果此应用程序在本地成功安装, <code>INSTALL_EVENT</code>类型的ContainerEvent将发送给所有注册的监听器,
     * 并将新创建的<code>Context</code>作为参数.
     *
     * @param contextName 应安装此应用程序的上下文名称(必须是唯一的)
     * @param webapp    包含要安装的Web应用的WAR文件或解压的目录结构
     *
     * @exception IllegalArgumentException 如果指定的上下文名称是异常的
     * @exception IllegalStateException 如果指定的上下文名称已经附加到现有的Web应用程序
     * @exception IOException 如果在安装过程中遇到输入/输出错误
     */
    public void install(String contextName, File webapp) throws IOException;

    /**
     * 删除现有的Web应用程序, 附加到指定的上下文名称.
     * 如果成功删除此应用程序, <code>REMOVE_EVENT</code>类型的ContainerEvent将被发送到注册的监听器, 用要删除的<code>Context</code>作为参数.
     * 删除Web应用的war文件或目录，如果在Host的 appBase存在.
     *
     * @param contextName 要删除的应用程序的上下文名称
     * @param undeploy 是否从服务器中删除Web应用程序
     *
     * @exception IllegalArgumentException 如果指定的上下文名称是异常的
     * @exception IllegalArgumentException 如果指定的上下文名称不标识当前安装的Web应用程序
     * @exception IOException 如果在安装过程中遇到输入/输出错误
     */
    public void remove(String contextName, boolean undeploy) throws IOException;

    /**
     * 从容器后台进程调用
     */
    public void backgroundProcess();

    /**
     * 返回集群部署程序关联的集群
     * 
     * @return CatalinaCluster
     */
    public CatalinaCluster getCluster();

    /**
     * 将集群部署程序与集群关联
     * 
     * @param cluster CatalinaCluster
     */
    public void setCluster(CatalinaCluster cluster);

}
