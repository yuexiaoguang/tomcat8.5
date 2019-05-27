package org.apache.coyote;

import org.apache.tomcat.util.net.SocketEvent;

/**
 * 表示基于coyote的servlet容器中的入口点.
 */
public interface Adapter {

    /**
     * 通知所有监听器
     *
     * @param req The request object
     * @param res The response object
     *
     * @exception Exception 如果在处理请求过程中发生错误. 常见错误是:
     *   <ul><li>IOException 如果处理包含的servlet发生输入/输出错误 (否则它由顶级错误处理机制处理)
     *       <li>ServletException 如果处理包含的servlet发生异常 (否则它由顶级错误处理机制处理)
     *  </ul>
     *  Tomcat应该能够处理和记录任何其他异常 (包括非受检异常)
     */
    public void service(Request req, Response res) throws Exception;

    /**
     * 准备给定的请求/响应以进行处理. 此方法要求请求对象已经填充了HTTP报头可用的信息.
     *
     * @param req The request object
     * @param res The response object
     *
     * @return <code>true</code>如果处理可以继续, 否则 <code>false</code>将对响应设置适当的错误
     *
     * @throws Exception 如果处理意外失败
     */
    public boolean prepare(Request req, Response res) throws Exception;

    public boolean asyncDispatch(Request req,Response res, SocketEvent status)
            throws Exception;

    public void log(Request req, Response res, long time);

    /**
     * 断言请求和响应已被回收. 如果他们没有，然后记录警告并强制回收. 这种方法被称为安全检查, 当一个处理器被回收并返回到一个池中以重用时.
     *
     * @param req Request
     * @param res Response
     */
    public void checkRecycled(Request req, Response res);

    /**
     * 提供用于为与连接器关联的组件注册MBean的域的名称.
     *
     * @return MBean 域的名称
     */
    public String getDomain();
}
