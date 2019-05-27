package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Server;
import org.apache.catalina.Service;

public interface IStoreConfig {

    /**
     * 获取配置注册表
     *
     * @return 处理存储操作的注册表
     */
    StoreRegistry getRegistry();

    /**
     * 设置配置注册表
     *
     * @param aRegistry 处理存储操作的注册表
     */
    void setRegistry(StoreRegistry aRegistry);

    /**
     * 获取关联的server
     */
    Server getServer();

    /**
     * 设置关联的 server
     *
     * @param aServer 关联的 server
     */
    void setServer(Server aServer);

    /**
     * 保存当前的 StoreFactory Server.
     */
    void storeConfig();

    /**
     * 保存指定的 Server 属性.
     *
     * @param aServer 要保存的对象
     * @return <code>true</code>如果保存成功
     */
    boolean store(Server aServer);

    /**
     * 保存指定的Server 属性.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent 这个元素缩进的空格数量
     * @param aServer 要保存的对象
     * @throws Exception 发生存储错误
     */
    void store(PrintWriter aWriter, int indent, Server aServer) throws Exception;

    /**
     * 保存指定的Service 属性.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent 这个元素缩进的空格数量
     * @param aService 要保存的对象
     * @throws Exception 发生存储错误
     */
    void store(PrintWriter aWriter, int indent, Service aService) throws Exception;

    /**
     * 保存指定的Host 属性.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent 这个元素缩进的空格数量
     * @param aHost 要保存的对象
     * @throws Exception 发生存储错误
     */
    void store(PrintWriter aWriter, int indent, Host aHost) throws Exception;

    /**
     * 保存指定的Context 属性.
     *
     * @param aContext 要保存的对象
     * @return <code>true</code>保存成功
     */
    boolean store(Context aContext);

    /**
     * 保存指定的Context 属性.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent 这个元素缩进的空格数量
     * @param aContext 要保存的对象
     * @throws Exception 发生存储错误
     */
    void store(PrintWriter aWriter, int indent, Context aContext) throws Exception;
}