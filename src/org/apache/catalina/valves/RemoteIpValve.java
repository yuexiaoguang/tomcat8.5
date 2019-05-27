package org.apache.catalina.valves;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Globals;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.http.MimeHeaders;

/**
 * <p>
 * <a href="http://httpd.apache.org/docs/trunk/mod/mod_remoteip.html">mod_remoteip</a>的Tomcat端口,
 * 这个 valve 替换请求的客户端远程IP地址和主机名, 使用由代理或负载均衡器提供的IP地址列表, 通过一个请求 header (e.g. "X-Forwarded-For").
 * </p>
 * <p>
 * 这个valve的另一个功能是替换表面的方案(http/https) 和服务器端口号, 使用由代理或负载均衡器提供的方案, 通过一个请求 header (e.g. "X-Forwarded-Proto").
 * </p>
 * <p>
 * 这个 valve 按以下过程处理:
 * </p>
 * <p>
 * 如果进入的<code>request.getRemoteAddr()</code>匹配内部代理的 valve列表 :
 * </p>
 * <ul>
 * <li>在由前面的负载均衡器或代理传递的IP和主机名的逗号分隔列表上循环, 在请求的<code>$remoteIpHeader</code> Httpheader中 (默认值<code>x-forwarded-for</code>).
 * 值按右到左顺序处理.</li>
 * <li>对于列表中的每个 ip/host:
 * <ul>
 * <li>如果它与内部代理列表匹配, 忽略 ip/host</li>
 * <li>如果它与可信代理列表匹配, 添加 ip/host 到创建的代理 header</li>
 * <li>否则, ip/host 被声明为远程IP, 并停止循环.</li>
 * </ul>
 * </li>
 * <li>如果请求的 http header 叫做<code>$protocolHeader</code> (e.g. <code>x-forwarded-for</code>) 等同于
 * <code>protocolHeaderHttpsValue</code>配置参数的值 (默认 <code>https</code>), 那么<code>request.isSecure = true</code>,
 * <code>request.scheme = https</code> 和 <code>request.serverPort = 443</code>.
 * 注意, 将使用<code>$httpsServerPort</code>配置参数覆盖 443.</li>
 * </ul>
 * <table border="1">
 * <caption>配置参数</caption>
 * <tr>
 * <th>RemoteIpValve 属性</th>
 * <th>描述</th>
 * <th>等效的 mod_remoteip 指令</th>
 * <th>格式</th>
 * <th>默认值</th>
 * </tr>
 * <tr>
 * <td>remoteIpHeader</td>
 * <td>由该valve 读取的HTTP Header的名称，它包含从请求客户端开始的遍历IP地址列表</td>
 * <td>RemoteIPHeader</td>
 * <td>兼容 http header 名称</td>
 * <td>x-forwarded-for</td>
 * </tr>
 * <tr>
 * <td>internalProxies</td>
 * <td>与内部代理的IP地址匹配的正则表达式. 如果它们出现在<code>remoteIpHeader</code> 值中, 它们就是可信的, 并且不会出现在<code>proxiesHeader</code> 值中</td>
 * <td>RemoteIPInternalProxy</td>
 * <td>正则表达式 (由 {@link java.util.regex.Pattern java.util.regex}支持的语法)</td>
 * <td>10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|
 *     169\.254\.\d{1,3}\.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}|
 *     172\.1[6-9]{1}\.\d{1,3}\.\d{1,3}|172\.2[0-9]{1}\.\d{1,3}\.\d{1,3}|
 *     172\.3[0-1]{1}\.\d{1,3}\.\d{1,3}
 *     <br>
 * 默认情况下, 10/8, 192.168/16, 169.254/16, 127/8, 172.16/12 是允许的.</td>
 * </tr>
 * <tr>
 * <td>proxiesHeader</td>
 * <td>这个valve创建的 http header, 保存已处理的代理的列表
 * <code>remoteIpHeader</code></td>
 * <td>proxiesHeader</td>
 * <td>兼容 http header 名称</td>
 * <td>x-forwarded-by</td>
 * </tr>
 * <tr>
 * <td>trustedProxies</td>
 * <td>与可信代理的IP地址匹配的正则表达式.
 * 如果它们出现在<code>remoteIpHeader</code>值中, 它们就是可信的, 并且会出现在<code>proxiesHeader</code>值中</td>
 * <td>RemoteIPTrustedProxy</td>
 * <td>正则表达式 (由 {@link java.util.regex.Pattern java.util.regex}支持的语法)</td>
 * <td>&nbsp;</td>
 * </tr>
 * <tr>
 * <td>protocolHeader</td>
 * <td>此valve读取的HTTP header的名称，该header持有此请求的标志</td>
 * <td>N/A</td>
 * <td>兼容http header名称, 例如 <code>X-Forwarded-Proto</code>, <code>X-Forwarded-Ssl</code> 或 <code>Front-End-Https</code></td>
 * <td><code>null</code></td>
 * </tr>
 * <tr>
 * <td>protocolHeaderHttpsValue</td>
 * <td><code>protocolHeader</code>的值, 表示这是一个 Https 请求</td>
 * <td>N/A</td>
 * <td>例如<code>https</code> 或 <code>ON</code></td>
 * <td><code>https</code></td>
 * </tr>
 * <tr>
 * <td>httpServerPort</td>
 * <td>{@link javax.servlet.ServletRequest#getServerPort()}返回的值, 当<code>protocolHeader</code>表示<code>http</code>协议的时候</td>
 * <td>N/A</td>
 * <td>integer</td>
 * <td>80</td>
 * </tr>
 * <tr>
 * <td>httpsServerPort</td>
 * <td>{@link javax.servlet.ServletRequest#getServerPort()}返回的值, 当<code>protocolHeader</code>表示<code>https</code>协议的时候</td>
 * <td>N/A</td>
 * <td>integer</td>
 * <td>443</td>
 * </tr>
 * </table>
 * <p>
 * 这个 Valve 可能会被附加到任何 Container上, 根据您希望执行的过滤的粒度.
 * </p>
 * <p>
 * <strong>正则表达式 vs. IP 地址块:</strong> <code>mod_remoteip</code> 允许使用地址块
 * (e.g. <code>192.168/16</code>)来配置 <code>RemoteIPInternalProxy</code>和<code>RemoteIPTrustedProxy</code>;
 * 因为 Tomcat 没有一个库类似于 <a href="http://apr.apache.org/docs/apr/1.3/group__apr__network__io.html#gb74d21b8898b7c40bf7fd07ad3eb993d">apr_ipsubnet_test</a>,
 * <code>RemoteIpValve</code>使用正则表达式配置<code>internalProxies</code> 和 <code>trustedProxies</code>, 以和{@link RequestFilterValve}相同的方式.
 * </p>
 * <hr>
 * <p>
 * <strong>内部代理的示例</strong>
 * </p>
 * <p>
 * RemoteIpValve 配置:
 * </p>
 * <code>
 * &lt;Valve
 *   className="org.apache.catalina.valves.RemoteIpValve"
 *   internalProxies="192\.168\.0\.10|192\.168\.0\.11"
 *   remoteIpHeader="x-forwarded-for"
 *   proxiesHeader="x-forwarded-by"
 *   protocolHeader="x-forwarded-proto"
 *   /&gt;</code>
 * <table border="1">
 * <caption>请求值</caption>
 * <tr>
 * <th>属性</th>
 * <th>RemoteIpValve之前的值</th>
 * <th>RemoteIpValve之后的值</th>
 * </tr>
 * <tr>
 * <td>request.remoteAddr</td>
 * <td>192.168.0.10</td>
 * <td>140.211.11.130</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-for']</td>
 * <td>140.211.11.130, 192.168.0.10</td>
 * <td>null</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-by']</td>
 * <td>null</td>
 * <td>null</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-proto']</td>
 * <td>https</td>
 * <td>https</td>
 * </tr>
 * <tr>
 * <td>request.scheme</td>
 * <td>http</td>
 * <td>https</td>
 * </tr>
 * <tr>
 * <td>request.secure</td>
 * <td>false</td>
 * <td>true</td>
 * </tr>
 * <tr>
 * <td>request.serverPort</td>
 * <td>80</td>
 * <td>443</td>
 * </tr>
 * </table>
 * <p>
 * Note : <code>x-forwarded-by</code> header 是 null, 因为只有内部代理被请求遍历.
 * <code>x-forwarded-by</code>是 null, 因为所有代理都是可信的或内部的.
 * </p>
 * <hr>
 * <p>
 * <strong>可信代理的示例</strong>
 * </p>
 * <p>
 * RemoteIpValve 配置:
 * </p>
 * <code>
 * &lt;Valve
 *   className="org.apache.catalina.valves.RemoteIpValve"
 *   internalProxies="192\.168\.0\.10|192\.168\.0\.11"
 *   remoteIpHeader="x-forwarded-for"
 *   proxiesHeader="x-forwarded-by"
 *   trustedProxies="proxy1|proxy2"
 *   /&gt;</code>
 * <table border="1">
 * <caption>请求值</caption>
 * <tr>
 * <th>属性</th>
 * <th>RemoteIpValve之前的值</th>
 * <th>RemoteIpValve之后的值</th>
 * </tr>
 * <tr>
 * <td>request.remoteAddr</td>
 * <td>192.168.0.10</td>
 * <td>140.211.11.130</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-for']</td>
 * <td>140.211.11.130, proxy1, proxy2</td>
 * <td>null</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-by']</td>
 * <td>null</td>
 * <td>proxy1, proxy2</td>
 * </tr>
 * </table>
 * <p>
 * Note : <code>proxy1</code> 和 <code>proxy2</code>都是可信的代理, 都使用<code>x-forwarded-for</code> header进来的,
 * 都迁移进 <code>x-forwarded-by</code> header. <code>x-forwarded-by</code> 是 null, 因为所有代理都是可信的或内部的.
 * </p>
 * <hr>
 * <p>
 * <strong>内部代理和可信代理的示例</strong>
 * </p>
 * <p>
 * RemoteIpValve 配置:
 * </p>
 * <code>
 * &lt;Valve
 *   className="org.apache.catalina.valves.RemoteIpValve"
 *   internalProxies="192\.168\.0\.10|192\.168\.0\.11"
 *   remoteIpHeader="x-forwarded-for"
 *   proxiesHeader="x-forwarded-by"
 *   trustedProxies="proxy1|proxy2"
 *   /&gt;</code>
 * <table border="1">
 * <caption>请求值</caption>
 * <tr>
 * <th>属性</th>
 * <th>RemoteIpValve之前的值</th>
 * <th>RemoteIpValve之后的值</th>
 * </tr>
 * <tr>
 * <td>request.remoteAddr</td>
 * <td>192.168.0.10</td>
 * <td>140.211.11.130</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-for']</td>
 * <td>140.211.11.130, proxy1, proxy2, 192.168.0.10</td>
 * <td>null</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-by']</td>
 * <td>null</td>
 * <td>proxy1, proxy2</td>
 * </tr>
 * </table>
 * <p>
 * Note : <code>proxy1</code> and <code>proxy2</code>都是可信的代理, 都使用<code>x-forwarded-for</code> header进来的,
 * 都迁移进 <code>x-forwarded-by</code> header. 因为<code>192.168.0.10</code>是一个内部代理, 它没有出现在<code>x-forwarded-by</code>.
 * <code>x-forwarded-by</code> 是 null, 因为所有代理都是可信的或内部的.
 * </p>
 * <hr>
 * <p>
 * <strong>不信任的代理示例</strong>
 * </p>
 * <p>
 * RemoteIpValve 配置:
 * </p>
 * <code>
 * &lt;Valve
 *   className="org.apache.catalina.valves.RemoteIpValve"
 *   internalProxies="192\.168\.0\.10|192\.168\.0\.11"
 *   remoteIpHeader="x-forwarded-for"
 *   proxiesHeader="x-forwarded-by"
 *   trustedProxies="proxy1|proxy2"
 *   /&gt;</code>
 * <table border="1">
 * <caption>请求值</caption>
 * <tr>
 * <th>属性</th>
 * <th>Value Before RemoteIpValve</th>
 * <th>Value After RemoteIpValve</th>
 * </tr>
 * <tr>
 * <td>request.remoteAddr</td>
 * <td>192.168.0.10</td>
 * <td>untrusted-proxy</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-for']</td>
 * <td>140.211.11.130, untrusted-proxy, proxy1</td>
 * <td>140.211.11.130</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-by']</td>
 * <td>null</td>
 * <td>proxy1</td>
 * </tr>
 * </table>
 * <p>
 * Note : <code>x-forwarded-by</code>保存受信任的代理<code>proxy1</code>.
 * <code>x-forwarded-by</code>保存<code>140.211.11.130</code>, 因为<code>untrusted-proxy</code>不受信任,
 * 不相信<code>untrusted-proxy</code>是真实的远程地址. <code>request.remoteAddr</code> 是<code>proxy1</code>验证的 <code>untrusted-proxy</code> , 一个 IP.
 * </p>
 */
public class RemoteIpValve extends ValveBase {

    /**
     * 支持空格的逗号分隔字符串的{@link Pattern}
     */
    private static final Pattern commaSeparatedValuesPattern = Pattern.compile("\\s*,\\s*");

    /**
     * Logger
     */
    private static final Log log = LogFactory.getLog(RemoteIpValve.class);

    /**
     * 将给定逗号分隔的字符串转换为字符串数组
     * 
     * @param commaDelimitedStrings 要转换的字符串
     * @return array of String (non <code>null</code>)
     */
    protected static String[] commaDelimitedListToStringArray(String commaDelimitedStrings) {
        return (commaDelimitedStrings == null || commaDelimitedStrings.length() == 0) ? new String[0] : commaSeparatedValuesPattern
            .split(commaDelimitedStrings);
    }

    /**
     * 转换逗号分隔字符串中的字符串数组
     * 
     * @param stringList 要转换的字符串 list
     * @return The concatenated string
     */
    protected static String listToCommaDelimitedString(List<String> stringList) {
        if (stringList == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (Iterator<String> it = stringList.iterator(); it.hasNext();) {
            Object element = it.next();
            if (element != null) {
                result.append(element);
                if (it.hasNext()) {
                    result.append(", ");
                }
            }
        }
        return result.toString();
    }

    private int httpServerPort = 80;

    private int httpsServerPort = 443;

    private boolean changeLocalPort = false;

    private Pattern internalProxies = Pattern.compile(
            "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" +
            "192\\.168\\.\\d{1,3}\\.\\d{1,3}|" +
            "169\\.254\\.\\d{1,3}\\.\\d{1,3}|" +
            "127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" +
            "172\\.1[6-9]{1}\\.\\d{1,3}\\.\\d{1,3}|" +
            "172\\.2[0-9]{1}\\.\\d{1,3}\\.\\d{1,3}|" +
            "172\\.3[0-1]{1}\\.\\d{1,3}\\.\\d{1,3}");

    private String protocolHeader = null;

    private String protocolHeaderHttpsValue = "https";

    private String portHeader = null;

    private String proxiesHeader = "X-Forwarded-By";

    private String remoteIpHeader = "X-Forwarded-For";

    private boolean requestAttributesEnabled = true;

    private Pattern trustedProxies = null;


    public RemoteIpValve() {
        // 这个valve支持异步请求
        super(true);
    }


    public int getHttpsServerPort() {
        return httpsServerPort;
    }

    public int getHttpServerPort() {
        return httpServerPort;
    }

    public boolean isChangeLocalPort() {
        return changeLocalPort;
    }

    public void setChangeLocalPort(boolean changeLocalPort) {
        this.changeLocalPort = changeLocalPort;
    }

    /**
     * 获取 HTTP header的名称, 用于重写{@link Request#getServerPort()} 返回的值 (可选的依赖 {link {@link #isChangeLocalPort()} {@link Request#getLocalPort()}.
     *
     * @return  The HTTP header name
     */
    public String getPortHeader() {
        return portHeader;
    }

    /**
     * 设置 HTTP header的名称, 用于重写{@link Request#getServerPort()} 返回的值 (可选的依赖 {link {@link #isChangeLocalPort()} {@link Request#getLocalPort()}.
     *
     * @param portHeader  The HTTP header name
     */
    public void setPortHeader(String portHeader) {
        this.portHeader = portHeader;
    }

    /**
     * @return 定义内部代理的正则表达式
     */
    public String getInternalProxies() {
        if (internalProxies == null) {
            return null;
        }
        return internalProxies.toString();
    }

    /**
     * @return 协议 header (e.g. "X-Forwarded-Proto")
     */
    public String getProtocolHeader() {
        return protocolHeader;
    }

    /**
     * @return 传入HTTPS请求的协议头的值 (e.g. "https")
     */
    public String getProtocolHeaderHttpsValue() {
        return protocolHeaderHttpsValue;
    }

    /**
     * @return 代理头名称 (e.g. "X-Forwarded-By")
     */
    public String getProxiesHeader() {
        return proxiesHeader;
    }

    /**
     * @return 远程IP头名称 (e.g. "X-Forwarded-For")
     */
    public String getRemoteIpHeader() {
        return remoteIpHeader;
    }

    /**
     * @return <code>true</code>如果属性将被记录, 否则<code>false</code>
     */
    public boolean getRequestAttributesEnabled() {
        return requestAttributesEnabled;
    }

    /**
     * @return 定义可信代理的正则表达式
     */
    public String getTrustedProxies() {
        if (trustedProxies == null) {
            return null;
        }
        return trustedProxies.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        final String originalRemoteAddr = request.getRemoteAddr();
        final String originalRemoteHost = request.getRemoteHost();
        final String originalScheme = request.getScheme();
        final boolean originalSecure = request.isSecure();
        final int originalServerPort = request.getServerPort();
        final String originalProxiesHeader = request.getHeader(proxiesHeader);
        final String originalRemoteIpHeader = request.getHeader(remoteIpHeader);

        if (internalProxies !=null &&
                internalProxies.matcher(originalRemoteAddr).matches()) {
            String remoteIp = null;
            // 在 java 6中, proxiesHeaderValue 应该被声明为一个 java.util.Deque
            LinkedList<String> proxiesHeaderValue = new LinkedList<>();
            StringBuilder concatRemoteIpHeaderValue = new StringBuilder();

            for (Enumeration<String> e = request.getHeaders(remoteIpHeader); e.hasMoreElements();) {
                if (concatRemoteIpHeaderValue.length() > 0) {
                    concatRemoteIpHeaderValue.append(", ");
                }

                concatRemoteIpHeaderValue.append(e.nextElement());
            }

            String[] remoteIpHeaderValue = commaDelimitedListToStringArray(concatRemoteIpHeaderValue.toString());
            int idx;
            // 在 remoteIpHeaderValue上循环, 寻找第一个可信远程IP并构建代理链
            for (idx = remoteIpHeaderValue.length - 1; idx >= 0; idx--) {
                String currentRemoteIp = remoteIpHeaderValue[idx];
                remoteIp = currentRemoteIp;
                if (internalProxies.matcher(currentRemoteIp).matches()) {
                    // do nothing, internalProxies IP 不附加到
                } else if (trustedProxies != null &&
                        trustedProxies.matcher(currentRemoteIp).matches()) {
                    proxiesHeaderValue.addFirst(currentRemoteIp);
                } else {
                    idx--; // decrement idx because break statement doesn't do it
                    break;
                }
            }
            // 继续在remoteIpHeaderValue上循环, 来构建一个新的 remoteIpHeader的值
            LinkedList<String> newRemoteIpHeaderValue = new LinkedList<>();
            for (; idx >= 0; idx--) {
                String currentRemoteIp = remoteIpHeaderValue[idx];
                newRemoteIpHeaderValue.addFirst(currentRemoteIp);
            }
            if (remoteIp != null) {

                request.setRemoteAddr(remoteIp);
                request.setRemoteHost(remoteIp);

                // use request.coyoteRequest.mimeHeaders.setValue(str).setString(str) because request.addHeader(str, str) is no-op in Tomcat
                // 6.0
                if (proxiesHeaderValue.size() == 0) {
                    request.getCoyoteRequest().getMimeHeaders().removeHeader(proxiesHeader);
                } else {
                    String commaDelimitedListOfProxies = listToCommaDelimitedString(proxiesHeaderValue);
                    request.getCoyoteRequest().getMimeHeaders().setValue(proxiesHeader).setString(commaDelimitedListOfProxies);
                }
                if (newRemoteIpHeaderValue.size() == 0) {
                    request.getCoyoteRequest().getMimeHeaders().removeHeader(remoteIpHeader);
                } else {
                    String commaDelimitedRemoteIpHeaderValue = listToCommaDelimitedString(newRemoteIpHeaderValue);
                    request.getCoyoteRequest().getMimeHeaders().setValue(remoteIpHeader).setString(commaDelimitedRemoteIpHeaderValue);
                }
            }

            if (protocolHeader != null) {
                String protocolHeaderValue = request.getHeader(protocolHeader);
                if (protocolHeaderValue == null) {
                    // 不要编辑请求的 secure,scheme, serverPort 属性
                } else if (protocolHeaderHttpsValue.equalsIgnoreCase(protocolHeaderValue)) {
                    request.setSecure(true);
                    // 使用 request.coyoteRequest.scheme 替换掉 request.setScheme(), 因为 request.setScheme() is no-op in Tomcat 6.0
                    request.getCoyoteRequest().scheme().setString("https");

                    setPorts(request, httpsServerPort);
                } else {
                    request.setSecure(false);
                    // use request.coyoteRequest.scheme instead of request.setScheme() because request.setScheme() is no-op in Tomcat 6.0
                    request.getCoyoteRequest().scheme().setString("http");

                    setPorts(request, httpServerPort);
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Incoming request " + request.getRequestURI() + " with originalRemoteAddr '" + originalRemoteAddr
                          + "', originalRemoteHost='" + originalRemoteHost + "', originalSecure='" + originalSecure + "', originalScheme='"
                          + originalScheme + "' will be seen as newRemoteAddr='" + request.getRemoteAddr() + "', newRemoteHost='"
                          + request.getRemoteHost() + "', newScheme='" + request.getScheme() + "', newSecure='" + request.isSecure() + "'");
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Skip RemoteIpValve for request " + request.getRequestURI() + " with originalRemoteAddr '"
                        + request.getRemoteAddr() + "'");
            }
        }
        if (requestAttributesEnabled) {
            request.setAttribute(AccessLog.REMOTE_ADDR_ATTRIBUTE,
                    request.getRemoteAddr());
            request.setAttribute(Globals.REMOTE_ADDR_ATTRIBUTE,
                    request.getRemoteAddr());
            request.setAttribute(AccessLog.REMOTE_HOST_ATTRIBUTE,
                    request.getRemoteHost());
            request.setAttribute(AccessLog.PROTOCOL_ATTRIBUTE,
                    request.getProtocol());
            request.setAttribute(AccessLog.SERVER_PORT_ATTRIBUTE,
                    Integer.valueOf(request.getServerPort()));
        }
        try {
            getNext().invoke(request, response);
        } finally {
            request.setRemoteAddr(originalRemoteAddr);
            request.setRemoteHost(originalRemoteHost);

            request.setSecure(originalSecure);

            MimeHeaders headers = request.getCoyoteRequest().getMimeHeaders();
            // use request.coyoteRequest.scheme instead of request.setScheme() because request.setScheme() is no-op in Tomcat 6.0
            request.getCoyoteRequest().scheme().setString(originalScheme);

            request.setServerPort(originalServerPort);

            if (originalProxiesHeader == null || originalProxiesHeader.length() == 0) {
                headers.removeHeader(proxiesHeader);
            } else {
                headers.setValue(proxiesHeader).setString(originalProxiesHeader);
            }

            if (originalRemoteIpHeader == null || originalRemoteIpHeader.length() == 0) {
                headers.removeHeader(remoteIpHeader);
            } else {
                headers.setValue(remoteIpHeader).setString(originalRemoteIpHeader);
            }
        }
    }

    private void setPorts(Request request, int defaultPort) {
        int port = defaultPort;
        if (portHeader != null) {
            String portHeaderValue = request.getHeader(portHeader);
            if (portHeaderValue != null) {
                try {
                    port = Integer.parseInt(portHeaderValue);
                } catch (NumberFormatException nfe) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString(
                                "remoteIpValve.invalidPortHeader",
                                portHeaderValue, portHeader), nfe);
                    }
                }
            }
        }
        request.setServerPort(port);
        if (changeLocalPort) {
            request.setLocalPort(port);
        }
    }

    /**
     * <p>
     * 服务器端口号, 如果 {@link #protocolHeader} 不是<code>null</code>, 并且不表示HTTP
     * </p>
     * <p>
     * 默认值 : 80
     * </p>
     * @param httpServerPort The server port
     */
    public void setHttpServerPort(int httpServerPort) {
        this.httpServerPort = httpServerPort;
    }

    /**
     * <p>
     * 服务器端口号, 如果 {@link #protocolHeader} 表示 HTTPS
     * </p>
     * <p>
     * 默认值 : 443
     * </p>
     * @param httpsServerPort The server port
     */
    public void setHttpsServerPort(int httpsServerPort) {
        this.httpsServerPort = httpsServerPort;
    }

    /**
     * <p>
     * 定义内部代理的正则表达式.
     * </p>
     * <p>
     * 默认值 : 10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|169\.254.\d{1,3}.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}
     * </p>
     * @param internalProxies 代理正则表达式
     */
    public void setInternalProxies(String internalProxies) {
        if (internalProxies == null || internalProxies.length() == 0) {
            this.internalProxies = null;
        } else {
            this.internalProxies = Pattern.compile(internalProxies);
        }
    }

    /**
     * <p>
     * 保存进入的协议的Header, 通常叫做<code>X-Forwarded-Proto</code>. 如果是<code>null</code>, 将不会编辑request.scheme和request.secure.
     * </p>
     * <p>
     * 默认值 : <code>null</code>
     * </p>
     * @param protocolHeader The header name
     */
    public void setProtocolHeader(String protocolHeader) {
        this.protocolHeader = protocolHeader;
    }

    /**
     * <p>
     * 协议标头的不区分大小写的值，以指示传入的HTTP请求使用SSL.
     * </p>
     * <p>
     * 默认值 : <code>https</code>
     * </p>
     * @param protocolHeaderHttpsValue The header name
     */
    public void setProtocolHeaderHttpsValue(String protocolHeaderHttpsValue) {
        this.protocolHeaderHttpsValue = protocolHeaderHttpsValue;
    }

    /**
     * <p>
     * proxiesHeader 指令指定一个header, 其中mod_remoteip将收集被信任的解析实际远程IP的所有中间客户端IP地址的列表.
     * Note: 中间的 RemoteIPTrustedProxy 地址记录在这个 header中, 在任何中间的 RemoteIPInternalProxy地址被丢弃期间.
     * </p>
     * <p>
     * http header的名称, 保存HTTP请求已遍历的可信代理列表.
     * </p>
     * <p>
     * 这个header的值可以逗号分隔.
     * </p>
     * <p>
     * 默认值 : <code>X-Forwarded-By</code>
     * </p>
     * @param proxiesHeader header名称
     */
    public void setProxiesHeader(String proxiesHeader) {
        this.proxiesHeader = proxiesHeader;
    }

    /**
     * <p>
     * 提取远程IP的HTTP header的名称.
     * </p>
     * <p>
     * 这个header的值可以是逗号分隔的.
     * </p>
     * <p>
     * 默认值 : <code>X-Forwarded-For</code>
     * </p>
     *
     * @param remoteIpHeader The header name
     */
    public void setRemoteIpHeader(String remoteIpHeader) {
        this.remoteIpHeader = remoteIpHeader;
    }

    /**
     * 这个valve是否设置用于请求的请求属性 IP 地址, Hostname, 协议, 端口?
     * 这通常是结合{@link AccessLog}使用的, 否则将记录原始的值. 默认是<code>true</code>.
     *
     * 属性集是:
     * <ul>
     * <li>org.apache.catalina.AccessLog.RemoteAddr</li>
     * <li>org.apache.catalina.AccessLog.RemoteHost</li>
     * <li>org.apache.catalina.AccessLog.Protocol</li>
     * <li>org.apache.catalina.AccessLog.ServerPort</li>
     * <li>org.apache.tomcat.remoteAddr</li>
     * </ul>
     *
     * @param requestAttributesEnabled  <code>true</code>导致属性被设置, <code>false</code>禁用属性的设置.
     */
    public void setRequestAttributesEnabled(boolean requestAttributesEnabled) {
        this.requestAttributesEnabled = requestAttributesEnabled;
    }

    /**
     * <p>
     * 定义受信任的代理的正则表达式, 当它们出现在 {@link #remoteIpHeader} header中.
     * </p>
     * <p>
     * 默认值 : 空列表, 没有外部代理被信任.
     * </p>
     * @param trustedProxies 正则表达式
     */
    public void setTrustedProxies(String trustedProxies) {
        if (trustedProxies == null || trustedProxies.length() == 0) {
            this.trustedProxies = null;
        } else {
            this.trustedProxies = Pattern.compile(trustedProxies);
        }
    }
}
