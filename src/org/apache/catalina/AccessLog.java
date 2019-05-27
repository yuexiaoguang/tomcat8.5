package org.apache.catalina;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;


/**
 * 用于{@link Valve}提供访问日志.
 * Tomcat内部使用它来标识记录访问请求的阀门，因此在处理链中被拒绝的请求仍然可以添加到访问日志中.
 */
public interface AccessLog {

    /**
     * 请求属性名称，用于覆盖AccessLog记录的远程地址.
     */
    public static final String REMOTE_ADDR_ATTRIBUTE =
        "org.apache.catalina.AccessLog.RemoteAddr";

    /**
     * 请求属性名称，用于覆盖AccessLog记录的远程主机名.
     */
    public static final String REMOTE_HOST_ATTRIBUTE =
        "org.apache.catalina.AccessLog.RemoteHost";

    /**
     * 请求属性名称，用于覆盖AccessLog记录的协议.
     */
    public static final String PROTOCOL_ATTRIBUTE =
        "org.apache.catalina.AccessLog.Protocol";

    /**
     * 请求属性名称，用于覆盖AccessLog记录的服务器端口.
     */
    public static final String SERVER_PORT_ATTRIBUTE =
        "org.apache.catalina.AccessLog.ServerPort";


    /**
     * 使用指定的处理时间将请求/响应添加到访问日志中.
     *
     * @param request   要记录的Request
     * @param response  要记录的Response
     * @param time      处理请求/响应的时间，以毫秒为单位(如果未知使用 0 )
     */
    public void log(Request request, Response response, long time);

    /**
     * 这个阀门是否设置请求属性：IP 地址, hostname, protocol, port?
     * 通常和{@link org.apache.catalina.valves.AccessLogValve}一起使用，否则将记录原始值.
     *
     * 设置的属性是:
     * <ul>
     * <li>org.apache.catalina.RemoteAddr</li>
     * <li>org.apache.catalina.RemoteHost</li>
     * <li>org.apache.catalina.Protocol</li>
     * <li>org.apache.catalina.ServerPost</li>
     * </ul>
     *
     * @param requestAttributesEnabled  <code>true</code>设置属性, <code>false</code>禁用属性设置.
     */
    public void setRequestAttributesEnabled(boolean requestAttributesEnabled);

    /**
     * @return <code>true</code>如果属性将被记录, 否则<code>false</code>
     */
    public boolean getRequestAttributesEnabled();
}
