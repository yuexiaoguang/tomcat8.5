package org.apache.coyote;

import java.util.concurrent.Executor;

import org.apache.tomcat.util.net.SSLHostConfig;

/**
 * 协议实现, 包括线程, etc.
 * Processor 是单线程的特定于流的协议, 将不适合Jk协议，如JNI.
 *
 * 这是由一个coyote连接器实现的主要接口. Adapter 是coyote servlet容器实现的主要接口.
 */
public interface ProtocolHandler {

    /**
     * 适配器, 用于调用连接器.
     *
     * @param adapter 要关联的适配器
     */
    public void setAdapter(Adapter adapter);
    public Adapter getAdapter();


    /**
     * 执行器, 提供对底层线程池的访问.
     *
     * @return 用于处理请求的执行器
     */
    public Executor getExecutor();


    /**
     * 初始化协议.
     *
     * @throws Exception 如果协议处理程序无法初始化
     */
    public void init() throws Exception;


    /**
     * 启动协议.
     *
     * @throws Exception 如果协议处理程序无法启动
     */
    public void start() throws Exception;


    /**
     * 暂停协议 (可选).
     *
     * @throws Exception 如果协议处理程序未能暂停
     */
    public void pause() throws Exception;


    /**
     * 恢复协议 (可选).
     *
     * @throws Exception 如果协议处理程序无法恢复
     */
    public void resume() throws Exception;


    /**
     * 停止协议.
     *
     * @throws Exception 如果协议处理程序无法停止
     */
    public void stop() throws Exception;


    /**
     * 销毁协议 (可选).
     *
     * @throws Exception 如果协议处理程序无法销毁
     */
    public void destroy() throws Exception;


    /**
     * 需要 APR/native 库
     *
     * @return <code>true</code> 如果这个Protocol Handler 需要APR/native 库, 否则 <code>false</code>
     */
    public boolean isAprRequired();


    /**
     * 这个ProtocolHandler 是否支持 sendfile?
     *
     * @return <code>true</code> 如果这个Protocol Handler 支持 sendfile,
     *         否则 <code>false</code>
     */
    public boolean isSendfileSupported();


    public void addSslHostConfig(SSLHostConfig sslHostConfig);
    public SSLHostConfig[] findSslHostConfigs();


    public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol);
    public UpgradeProtocol[] findUpgradeProtocols();
}
