package org.apache.catalina.ha.backend;

/**
 * 发送数据到代理.
 */
public interface Sender {

  /**
   * 设置配置参数
   * 
   * @param config 心跳监听器配置
   * @throws Exception 发生的错误
   */
  public void init(HeartbeatListener config) throws Exception;

  /**
   * 将消息发送给代理服务器
   * 
   * @param mess 将发送的消息
   * @return <code>0</code>如果未发生错误, 否则<code>-1</code>
   * @throws Exception 发生的错误
   */
  public int send(String mess) throws Exception;
}
