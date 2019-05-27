package org.apache.catalina;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

/**
 * <p><b>Valve</b>是与特定容器相关联的请求处理组件. 
 * 一系列Valves通常相互连接成一个 Pipeline. Valve的详细的合同被包含在<code>invoke()</code>方法的描述中</p>
 *
 * <b>HISTORICAL NOTE</b>: 概念被命名为"Valve",是因为阀门是你在现实世界中使用的管道来控制、修改流经它的流体.
 */
public interface Valve {


    //-------------------------------------------------------------- Properties

    /**
     * 返回pipeline中的下一个Valve.
     */
    public Valve getNext();


    /**
     * 设置pipeline中的下一个Valve.
     *
     * @param valve 下一个valve, 或<code>null</code>
     */
    public void setNext(Valve valve);


    //---------------------------------------------------------- Public Methods


    /**
     * 执行周期任务, 例如重新加载, etc. 该方法将在该容器的类加载上下文被调用.
     * 异常将被捕获和记录.
     */
    public void backgroundProcess();


    /**
     * <p>按照此Valve的要求执行请求处理.</p>
     *
     * <p>单个Valve可以按指定的顺序执行下列操作:</p>
     * <ul>
     * <li>检查、修改指定的请求和响应的属性.
     * <li>检查指定请求的属性, 生成相应的响应, 并将控制权返回给调用者.
     * <li>检查指定Request和Response的属性, 包装这些对象中的一个或两个，以补充它们的功能，并传递它们
     * <li>如果没有生成相应的响应（或者未返回控制权）, 通过执行<code>context.invokeNext()</code>调用下一个Valve.
     * <li>检查、但不修改得到的响应的属性(随后执行的Valve或Container创建的响应).
     * </ul>
     *
     * <p>Valve<b>绝对不能</b>做下面的事情:</p>
     * <ul>
     * <li>修改已经用于管理流程控制的请求的属性(例如,在实现类中试图修改（应从连接到主机或上下文的管道发送请求的）虚拟主机).
     * <li>创建一个完整的响应<strong>以及</strong>传递Request 和Response给下一个Valve.
     * <li>从与请求相关联的输入流中消耗字节, 除非它能完全产生响应, 或者在传递请求之前包装它.
     * <li>修改包含响应的HTTP头，在<code>invokeNext()</code>方法返回之后.
     * <li>在与指定响应相关联的输出流上执行任何操作,在<code>invokeNext()</code>方法返回之后
     * </ul>
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException 如果发生输入输出错误, 或者随后执行的Valve, Filter, Servlet抛出
     * @exception ServletException 如果servlet错误发生, 或者随后执行的Valve, Filter, Servlet抛出
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException;


    public boolean isAsyncSupported();
}
