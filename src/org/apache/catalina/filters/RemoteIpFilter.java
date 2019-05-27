package org.apache.catalina.filters;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Globals;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.servlet4preview.http.PushBuilder;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * <p>
 * Servlet filter整合"X-Forwarded-For" 和 "X-Forwarded-Proto" HTTP header.
 * </p>
 * <p>
 * 这个servlet过滤器的大部分设计是<a href="http://httpd.apache.org/docs/trunk/mod/mod_remoteip.html">mod_remoteip</a>的端口,
 * 该servlet过滤器将请求的客户端远程IP地址和主机名替换为由代理或负载平衡器通过请求报头提交的IP地址列表 (即"X-Forwarded-For").
 * </p>
 * <p>
 * 这个servlet过滤器的另一个特点是替换明显的方案(http/https)和服务器端口，由代理或负载均衡器提出的方案，通过请求header (e.g. "X-Forwarded-Proto").
 * </p>
 * <p>
 * 此servlet过滤器如下进行:
 * </p>
 * <p>
 * 如果<code>request.getRemoteAddr()</code>匹配servlet过滤器的内部代理列表 :
 * </p>
 * <ul>
 * <li>在给定请求的<code>$remoteIpHeader</code> HTTP报头上由前面的负载均衡器或代理传递的逗号分隔的IP和主机名列表(默认值<code>x-forwarded-for</code>).
 * 值按右到左顺序处理.</li>
 * <li>对于列表中的每个IP/主机:
 * <ul>
 * <li>如果它与内部代理列表匹配, 忽略ip/host</li>
 * <li>如果它与可信代理列表匹配, ip/host被添加到创建的代理头</li>
 * <li>否则, ip/host声明为远程IP，停止循环.</li>
 * </ul>
 * </li>
 * <li>如果请求http header名为<code>$protocolHeader</code> (e.g. <code>x-forwarded-for</code>)等效于
 * <code>protocolHeaderHttpsValue</code>配置参数(默认<code>https</code>) 和 <code>request.isSecure = true</code>,
 * <code>request.scheme = https</code>和<code>request.serverPort = 443</code>.
 * 注意：443 可以用<code>$httpsServerPort</code>配置参数重写.</li>
 * </ul>
 * <table border="1">
 * <caption>配置参数</caption>
 * <tr>
 * <th>XForwardedFilter属性</th>
 * <th>描述</th>
 * <th>Equivalent mod_remoteip directive</th>
 * <th>格式</th>
 * <th>默认值</th>
 * </tr>
 * <tr>
 * <td>remoteIpHeader</td>
 * <td>此servlet过滤器读取的HTTP头的名称，它保存从请求客户端开始的遍历IP地址列表</td>
 * <td>RemoteIPHeader</td>
 * <td>兼容HTTP头名称</td>
 * <td>x-forwarded-for</td>
 * </tr>
 * <tr>
 * <td>internalProxies</td>
 * <td>与内部代理的IP地址匹配的正则表达式. 如果出现在<code>remoteIpHeader</code>值中, 他们会被信任并且不会出现在<code>proxiesHeader</code>值</td>
 * <td>RemoteIPInternalProxy</td>
 * <td>正则表达式 (在{@link java.util.regex.Pattern java.util.regex}语法支持下)</td>
 * <td>10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|
 *     169\.254\.\d{1,3}\.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}|
 *     172\.1[6-9]{1}\.\d{1,3}\.\d{1,3}|172\.2[0-9]{1}\.\d{1,3}\.\d{1,3}|
 *     172\.3[0-1]{1}\.\d{1,3}\.\d{1,3}
 *     <br>
 * 默认情况下, 10/8, 192.168/16, 169.254/16, 127/8, 172.16/12是允许的.</td>
 * </tr>
 * <tr>
 * <td>proxiesHeader</td>
 * <td>由该servlet过滤器创建的HTTP报头的名称，以保存在<code>remoteIpHeader</code>传入中已处理的代理的列表</td>
 * <td>RemoteIPProxiesHeader</td>
 * <td>兼容HTTP头名称</td>
 * <td>x-forwarded-by</td>
 * </tr>
 * <tr>
 * <td>trustedProxies</td>
 * <td>与可信代理的IP地址匹配的正则表达式. 如果出现在<code>remoteIpHeader</code>值中, 它们将被相信, 并且出现在<code>proxiesHeader</code>值中</td>
 * <td>RemoteIPTrustedProxy</td>
 * <td>正则表达式(在{@link java.util.regex.Pattern java.util.regex}语法支持下)</td>
 * <td>&nbsp;</td>
 * </tr>
 * <tr>
 * <td>protocolHeader</td>
 * <td>此servlet过滤器读取的HTTP头的名称，它保存此请求的标志</td>
 * <td>N/A</td>
 * <td>兼容HTTP头名称例如<code>X-Forwarded-Proto</code>, <code>X-Forwarded-Ssl</code>, <code>Front-End-Https</code></td>
 * <td><code>null</code></td>
 * </tr>
 * <tr>
 * <td>protocolHeaderHttpsValue</td>
 * <td><code>protocolHeader</code>的值指示它是一个HTTPS请求</td>
 * <td>N/A</td>
 * <td>字符串例如<code>https</code>或<code>ON</code></td>
 * <td><code>https</code></td>
 * </tr>
 * <tr>
 * <td>httpServerPort</td>
 * <td>{@link ServletRequest#getServerPort()}返回的值，当<code>protocolHeader</code>指示<code>http</code>协议的时候</td>
 * <td>N/A</td>
 * <td>integer</td>
 * <td>80</td>
 * </tr>
 * <tr>
 * <td>httpsServerPort</td>
 * <td>{@link ServletRequest#getServerPort()}返回的值，当<code>protocolHeader</code>指示<code>https</code>协议的时候</td>
 * <td>N/A</td>
 * <td>integer</td>
 * <td>443</td>
 * </tr>
 * </table>
 * <p>
 * <strong>正则表达式 vs. IP 地址块:</strong> <code>mod_remoteip</code>允许使用地址块 (即<code>192.168/16</code>)来配置
 * <code>RemoteIPInternalProxy</code>和<code>RemoteIPTrustedProxy</code>; 因为JVM 没有类似的库 <a
 * href="http://apr.apache.org/docs/apr/1.3/group__apr__network__io.html#gb74d21b8898b7c40bf7fd07ad3eb993d">apr_ipsubnet_test</a>,
 * 依赖正则表达式.
 * </p>
 * <hr>
 * <p>
 * <strong>带有内部代理的示例</strong>
 * </p>
 * <p>
 * XForwardedFilter配置:
 * </p>
 * <code>
 * &lt;filter&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;filter-class&gt;org.apache.catalina.filters.RemoteIpFilter&lt;/filter-class&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;internalProxies&lt;/param-name&gt;
 *       &lt;param-value&gt;192\.168\.0\.10|192\.168\.0\.11&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-for&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpProxiesHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-by&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;protocolHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-proto&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 * &lt;/filter&gt;
 *
 * &lt;filter-mapping&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 *    &lt;dispatcher&gt;REQUEST&lt;/dispatcher&gt;
 * &lt;/filter-mapping&gt;</code>
 * <table border="1">
 * <caption>Request Values</caption>
 * <tr>
 * <th>property</th>
 * <th>Value Before RemoteIpFilter</th>
 * <th>Value After RemoteIpFilter</th>
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
 * Note : <code>x-forwarded-by</code> header是null， 因为只有内部代理被请求遍历.
 * <code>x-forwarded-by</code>是 null， 因为所有代理都是可信的或内部的.
 * <hr>
 * <p>
 * <strong>使用可信代理的示例</strong>
 * </p>
 * <p>
 * RemoteIpFilter配置:
 * </p>
 * <code>
 * &lt;filter&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;filter-class&gt;org.apache.catalina.filters.RemoteIpFilter&lt;/filter-class&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;internalProxies&lt;/param-name&gt;
 *       &lt;param-value&gt;192\.168\.0\.10|192\.168\.0\.11&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-for&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpProxiesHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-by&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;trustedProxies&lt;/param-name&gt;
 *       &lt;param-value&gt;proxy1|proxy2&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 * &lt;/filter&gt;
 *
 * &lt;filter-mapping&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 *    &lt;dispatcher&gt;REQUEST&lt;/dispatcher&gt;
 * &lt;/filter-mapping&gt;</code>
 * <table border="1">
 * <caption>Request Values</caption>
 * <tr>
 * <th>property</th>
 * <th>Value Before RemoteIpFilter</th>
 * <th>Value After RemoteIpFilter</th>
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
 * Note : <code>proxy1</code>和<code>proxy2</code>都是来自<code>x-forwarded-for</code> header可信的代理, 
 * 它们都被迁移到<code>x-forwarded-by</code> header. <code>x-forwarded-by</code>是 null, 因为所有代理都是可信的或内部的.
 * </p>
 * <hr>
 * <p>
 * <strong>具有内部代理和可信代理的示例</strong>
 * </p>
 * <p>
 * RemoteIpFilter 配置:
 * </p>
 * <code>
 * &lt;filter&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;filter-class&gt;org.apache.catalina.filters.RemoteIpFilter&lt;/filter-class&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;internalProxies&lt;/param-name&gt;
 *       &lt;param-value&gt;192\.168\.0\.10|192\.168\.0\.11&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-for&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpProxiesHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-by&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;trustedProxies&lt;/param-name&gt;
 *       &lt;param-value&gt;proxy1|proxy2&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 * &lt;/filter&gt;
 *
 * &lt;filter-mapping&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 *    &lt;dispatcher&gt;REQUEST&lt;/dispatcher&gt;
 * &lt;/filter-mapping&gt;</code>
 * <table border="1">
 * <caption>Request Values</caption>
 * <tr>
 * <th>property</th>
 * <th>Value Before RemoteIpFilter</th>
 * <th>Value After RemoteIpFilter</th>
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
 * Note : <code>proxy1</code>和<code>proxy2</code>都是来自<code>x-forwarded-for</code> header可信的代理, 
 * 它们都被迁移到<code>x-forwarded-by</code> header. 因为<code>192.168.0.10</code>是一个内部代理, 它没有出现在<code>x-forwarded-by</code>.
 * <code>x-forwarded-by</code>是 null， 因为所有代理都是可信的或内部的.
 * </p>
 * <hr>
 * <p>
 * <strong>具有不可信代理的示例</strong>
 * </p>
 * <p>
 * RemoteIpFilter 配置:
 * </p>
 * <code>
 * &lt;filter&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;filter-class&gt;org.apache.catalina.filters.RemoteIpFilter&lt;/filter-class&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;internalProxies&lt;/param-name&gt;
 *       &lt;param-value&gt;192\.168\.0\.10|192\.168\.0\.11&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-for&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpProxiesHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-by&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;trustedProxies&lt;/param-name&gt;
 *       &lt;param-value&gt;proxy1|proxy2&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 * &lt;/filter&gt;
 *
 * &lt;filter-mapping&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 *    &lt;dispatcher&gt;REQUEST&lt;/dispatcher&gt;
 * &lt;/filter-mapping&gt;</code>
 * <table border="1">
 * <caption>Request Values</caption>
 * <tr>
 * <th>property</th>
 * <th>Value Before RemoteIpFilter</th>
 * <th>Value After RemoteIpFilter</th>
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
 * Note : <code>x-forwarded-by</code>保存信任的代理<code>proxy1</code>.
 * <code>x-forwarded-by</code>保存<code>140.211.11.130</code>, 因为<code>untrusted-proxy</code>是不可信的,
 * 不能信任<code>untrusted-proxy</code>是实际的远程IP. <code>request.remoteAddr</code>是<code>proxy1</code>验证IP的<code>untrusted-proxy</code>.
 * </p>
 * <hr>
 */
public class RemoteIpFilter implements Filter {
    public static class XForwardedRequest extends HttpServletRequestWrapper {

        static final ThreadLocal<SimpleDateFormat[]> threadLocalDateFormats = new ThreadLocal<SimpleDateFormat[]>() {
            @Override
            protected SimpleDateFormat[] initialValue() {
                return new SimpleDateFormat[] {
                    new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
                    new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
                    new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
                };

            }
        };

        protected final Map<String, List<String>> headers;

        protected int localPort;

        protected String remoteAddr;

        protected String remoteHost;

        protected String scheme;

        protected boolean secure;

        protected int serverPort;

        public XForwardedRequest(HttpServletRequest request) {
            super(request);
            this.localPort = request.getLocalPort();
            this.remoteAddr = request.getRemoteAddr();
            this.remoteHost = request.getRemoteHost();
            this.scheme = request.getScheme();
            this.secure = request.isSecure();
            this.serverPort = request.getServerPort();

            headers = new HashMap<>();
            for (Enumeration<String> headerNames = request.getHeaderNames(); headerNames.hasMoreElements();) {
                String header = headerNames.nextElement();
                headers.put(header, Collections.list(request.getHeaders(header)));
            }
        }

        @Override
        public long getDateHeader(String name) {
            String value = getHeader(name);
            if (value == null) {
                return -1;
            }
            DateFormat[] dateFormats = threadLocalDateFormats.get();
            Date date = null;
            for (int i = 0; ((i < dateFormats.length) && (date == null)); i++) {
                DateFormat dateFormat = dateFormats[i];
                try {
                    date = dateFormat.parse(value);
                } catch (ParseException ex) {
                    // Ignore
                }
            }
            if (date == null) {
                throw new IllegalArgumentException(value);
            }
            return date.getTime();
        }

        @Override
        public String getHeader(String name) {
            Map.Entry<String, List<String>> header = getHeaderEntry(name);
            if (header == null || header.getValue() == null || header.getValue().isEmpty()) {
                return null;
            }
            return header.getValue().get(0);
        }

        protected Map.Entry<String, List<String>> getHeaderEntry(String name) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry;
                }
            }
            return null;
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(headers.keySet());
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            Map.Entry<String, List<String>> header = getHeaderEntry(name);
            if (header == null || header.getValue() == null) {
                return Collections.enumeration(Collections.<String>emptyList());
            }
            return Collections.enumeration(header.getValue());
        }

        @Override
        public int getIntHeader(String name) {
            String value = getHeader(name);
            if (value == null) {
                return -1;
            }
            return Integer.parseInt(value);
        }

        @Override
        public int getLocalPort() {
            return localPort;
        }

        @Override
        public String getRemoteAddr() {
            return this.remoteAddr;
        }

        @Override
        public String getRemoteHost() {
            return this.remoteHost;
        }

        @Override
        public String getScheme() {
            return scheme;
        }

        @Override
        public int getServerPort() {
            return serverPort;
        }

        @Override
        public boolean isSecure() {
            return secure;
        }

        public void removeHeader(String name) {
            Map.Entry<String, List<String>> header = getHeaderEntry(name);
            if (header != null) {
                headers.remove(header.getKey());
            }
        }

        public void setHeader(String name, String value) {
            List<String> values = Arrays.asList(value);
            Map.Entry<String, List<String>> header = getHeaderEntry(name);
            if (header == null) {
                headers.put(name, values);
            } else {
                header.setValue(values);
            }

        }

        public void setLocalPort(int localPort) {
            this.localPort = localPort;
        }

        public void setRemoteAddr(String remoteAddr) {
            this.remoteAddr = remoteAddr;
        }

        public void setRemoteHost(String remoteHost) {
            this.remoteHost = remoteHost;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public void setServerPort(int serverPort) {
            this.serverPort = serverPort;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer();
            String scheme = getScheme();
            int port = getServerPort();
            if (port < 0) {
                port = 80; // Work around java.net.URL bug
            }
            url.append(scheme);
            url.append("://");
            url.append(getServerName());
            if ((scheme.equals("http") && (port != 80))
                || (scheme.equals("https") && (port != 443))) {
                url.append(':');
                url.append(port);
            }
            url.append(getRequestURI());

            return url;
        }

        public PushBuilder getPushBuilder() {
            ServletRequest current = getRequest();
            while (current instanceof ServletRequestWrapper) {
                current = ((ServletRequestWrapper) current).getRequest();
            }
            if (current instanceof RequestFacade) {
                return ((RequestFacade) current).newPushBuilder(this);
            } else {
                return null;
            }
        }
    }


    /**
     * 支持空格字符的逗号分隔字符串
     */
    private static final Pattern commaSeparatedValuesPattern = Pattern.compile("\\s*,\\s*");

    protected static final String HTTP_SERVER_PORT_PARAMETER = "httpServerPort";

    protected static final String HTTPS_SERVER_PORT_PARAMETER = "httpsServerPort";

    protected static final String INTERNAL_PROXIES_PARAMETER = "internalProxies";

    /**
     * Logger
     */
    private static final Log log = LogFactory.getLog(RemoteIpFilter.class);

    protected static final String PROTOCOL_HEADER_PARAMETER = "protocolHeader";

    protected static final String PROTOCOL_HEADER_HTTPS_VALUE_PARAMETER = "protocolHeaderHttpsValue";

    protected static final String PORT_HEADER_PARAMETER = "portHeader";

    protected static final String CHANGE_LOCAL_PORT_PARAMETER = "changeLocalPort";

    protected static final String PROXIES_HEADER_PARAMETER = "proxiesHeader";

    protected static final String REMOTE_IP_HEADER_PARAMETER = "remoteIpHeader";

    protected static final String TRUSTED_PROXIES_PARAMETER = "trustedProxies";

    /**
     * 将给定的逗号分隔的正则表达式列表转换为字符串数组
     *
     * @param commaDelimitedStrings 要分隔的字符串
     * @return array of patterns (non <code>null</code>)
     */
    protected static String[] commaDelimitedListToStringArray(String commaDelimitedStrings) {
        return (commaDelimitedStrings == null || commaDelimitedStrings.length() == 0) ? new String[0] : commaSeparatedValuesPattern
            .split(commaDelimitedStrings);
    }

    /**
     * 转换逗号分隔字符串中的字符串列表.
     *
     * @param stringList 字符串列表
     * @return concatenated string
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

    private boolean changeLocalPort = false;

    private String proxiesHeader = "X-Forwarded-By";

    private String remoteIpHeader = "X-Forwarded-For";

    private boolean requestAttributesEnabled = true;

    private Pattern trustedProxies = null;

    @Override
    public void destroy() {
        // NOOP
    }

    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {

        if (internalProxies != null &&
                internalProxies.matcher(request.getRemoteAddr()).matches()) {
            String remoteIp = null;
            // java 6, proxiesHeaderValue应声明为 java.util.Deque
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
            // 循环remoteIpHeaderValue 寻找第一个可信任的远程IP并构建代理链
            for (idx = remoteIpHeaderValue.length - 1; idx >= 0; idx--) {
                String currentRemoteIp = remoteIpHeaderValue[idx];
                remoteIp = currentRemoteIp;
                if (internalProxies.matcher(currentRemoteIp).matches()) {
                    // do nothing, internalProxies IPs are not appended to the
                } else if (trustedProxies != null &&
                        trustedProxies.matcher(currentRemoteIp).matches()) {
                    proxiesHeaderValue.addFirst(currentRemoteIp);
                } else {
                    idx--; // 减去idx，因为中断语句不执行
                    break;
                }
            }
            // 继续循环 remoteIpHeaderValue 来构建remoteIpHeader的值
            LinkedList<String> newRemoteIpHeaderValue = new LinkedList<>();
            for (; idx >= 0; idx--) {
                String currentRemoteIp = remoteIpHeaderValue[idx];
                newRemoteIpHeaderValue.addFirst(currentRemoteIp);
            }

            XForwardedRequest xRequest = new XForwardedRequest(request);
            if (remoteIp != null) {

                xRequest.setRemoteAddr(remoteIp);
                xRequest.setRemoteHost(remoteIp);

                if (proxiesHeaderValue.size() == 0) {
                    xRequest.removeHeader(proxiesHeader);
                } else {
                    String commaDelimitedListOfProxies = listToCommaDelimitedString(proxiesHeaderValue);
                    xRequest.setHeader(proxiesHeader, commaDelimitedListOfProxies);
                }
                if (newRemoteIpHeaderValue.size() == 0) {
                    xRequest.removeHeader(remoteIpHeader);
                } else {
                    String commaDelimitedRemoteIpHeaderValue = listToCommaDelimitedString(newRemoteIpHeaderValue);
                    xRequest.setHeader(remoteIpHeader, commaDelimitedRemoteIpHeaderValue);
                }
            }

            if (protocolHeader != null) {
                String protocolHeaderValue = request.getHeader(protocolHeader);
                if (protocolHeaderValue == null) {
                    // 不要修改请求的secure, scheme, serverPort 属性
                } else if (protocolHeaderHttpsValue.equalsIgnoreCase(protocolHeaderValue)) {
                    xRequest.setSecure(true);
                    xRequest.setScheme("https");
                    setPorts(xRequest, httpsServerPort);
                } else {
                    xRequest.setSecure(false);
                    xRequest.setScheme("http");
                    setPorts(xRequest, httpServerPort);
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Incoming request " + request.getRequestURI() + " with originalRemoteAddr '" + request.getRemoteAddr()
                        + "', originalRemoteHost='" + request.getRemoteHost() + "', originalSecure='" + request.isSecure()
                        + "', originalScheme='" + request.getScheme() + "', original[" + remoteIpHeader + "]='"
                        + concatRemoteIpHeaderValue + "', original[" + protocolHeader + "]='"
                        + (protocolHeader == null ? null : request.getHeader(protocolHeader)) + "' will be seen as newRemoteAddr='"
                        + xRequest.getRemoteAddr() + "', newRemoteHost='" + xRequest.getRemoteHost() + "', newScheme='"
                        + xRequest.getScheme() + "', newSecure='" + xRequest.isSecure() + "', new[" + remoteIpHeader + "]='"
                        + xRequest.getHeader(remoteIpHeader) + "', new[" + proxiesHeader + "]='" + xRequest.getHeader(proxiesHeader) + "'");
            }
            if (requestAttributesEnabled) {
                request.setAttribute(AccessLog.REMOTE_ADDR_ATTRIBUTE,
                        xRequest.getRemoteAddr());
                request.setAttribute(Globals.REMOTE_ADDR_ATTRIBUTE,
                        xRequest.getRemoteAddr());
                request.setAttribute(AccessLog.REMOTE_HOST_ATTRIBUTE,
                        xRequest.getRemoteHost());
                request.setAttribute(AccessLog.PROTOCOL_ATTRIBUTE,
                        xRequest.getProtocol());
                request.setAttribute(AccessLog.SERVER_PORT_ATTRIBUTE,
                        Integer.valueOf(xRequest.getServerPort()));
            }
            chain.doFilter(xRequest, response);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Skip RemoteIpFilter for request " + request.getRequestURI() + " with originalRemoteAddr '"
                        + request.getRemoteAddr() + "'");
            }
            chain.doFilter(request, response);
        }

    }

    private void setPorts(XForwardedRequest xrequest, int defaultPort) {
        int port = defaultPort;
        if (getPortHeader() != null) {
            String portHeaderValue = xrequest.getHeader(getPortHeader());
            if (portHeaderValue != null) {
                try {
                    port = Integer.parseInt(portHeaderValue);
                } catch (NumberFormatException nfe) {
                    log.debug("Invalid port value [" + portHeaderValue +
                            "] provided in header [" + getPortHeader() + "]");
                }
            }
        }
        xrequest.setServerPort(port);
        if (isChangeLocalPort()) {
            xrequest.setLocalPort(port);
        }
    }

    /**
     * 包装{@link XForwardedRequest}中输入的<code>request</code>, 如果http header <code>x-forwarded-for</code>不为空.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            doFilter((HttpServletRequest)request, (HttpServletResponse)response, chain);
        } else {
            chain.doFilter(request, response);
        }
    }

    public boolean isChangeLocalPort() {
        return changeLocalPort;
    }

    public int getHttpsServerPort() {
        return httpsServerPort;
    }

    public Pattern getInternalProxies() {
        return internalProxies;
    }

    public String getProtocolHeader() {
        return protocolHeader;
    }

    public String getPortHeader() {
        return portHeader;
    }

    public String getProtocolHeaderHttpsValue() {
        return protocolHeaderHttpsValue;
    }

    public String getProxiesHeader() {
        return proxiesHeader;
    }

    public String getRemoteIpHeader() {
        return remoteIpHeader;
    }

    /**
     * @return <code>true</code>如果属性将被记录, 否则<code>false</code>
     */
    public boolean getRequestAttributesEnabled() {
        return requestAttributesEnabled;
    }

    public Pattern getTrustedProxies() {
        return trustedProxies;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        if (filterConfig.getInitParameter(INTERNAL_PROXIES_PARAMETER) != null) {
            setInternalProxies(filterConfig.getInitParameter(INTERNAL_PROXIES_PARAMETER));
        }

        if (filterConfig.getInitParameter(PROTOCOL_HEADER_PARAMETER) != null) {
            setProtocolHeader(filterConfig.getInitParameter(PROTOCOL_HEADER_PARAMETER));
        }

        if (filterConfig.getInitParameter(PROTOCOL_HEADER_HTTPS_VALUE_PARAMETER) != null) {
            setProtocolHeaderHttpsValue(filterConfig.getInitParameter(PROTOCOL_HEADER_HTTPS_VALUE_PARAMETER));
        }

        if (filterConfig.getInitParameter(PORT_HEADER_PARAMETER) != null) {
            setPortHeader(filterConfig.getInitParameter(PORT_HEADER_PARAMETER));
        }

        if (filterConfig.getInitParameter(CHANGE_LOCAL_PORT_PARAMETER) != null) {
            setChangeLocalPort(Boolean.parseBoolean(filterConfig.getInitParameter(CHANGE_LOCAL_PORT_PARAMETER)));
        }

        if (filterConfig.getInitParameter(PROXIES_HEADER_PARAMETER) != null) {
            setProxiesHeader(filterConfig.getInitParameter(PROXIES_HEADER_PARAMETER));
        }

        if (filterConfig.getInitParameter(REMOTE_IP_HEADER_PARAMETER) != null) {
            setRemoteIpHeader(filterConfig.getInitParameter(REMOTE_IP_HEADER_PARAMETER));
        }

        if (filterConfig.getInitParameter(TRUSTED_PROXIES_PARAMETER) != null) {
            setTrustedProxies(filterConfig.getInitParameter(TRUSTED_PROXIES_PARAMETER));
        }

        if (filterConfig.getInitParameter(HTTP_SERVER_PORT_PARAMETER) != null) {
            try {
                setHttpServerPort(Integer.parseInt(filterConfig.getInitParameter(HTTP_SERVER_PORT_PARAMETER)));
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Illegal " + HTTP_SERVER_PORT_PARAMETER + " : " + e.getMessage());
            }
        }

        if (filterConfig.getInitParameter(HTTPS_SERVER_PORT_PARAMETER) != null) {
            try {
                setHttpsServerPort(Integer.parseInt(filterConfig.getInitParameter(HTTPS_SERVER_PORT_PARAMETER)));
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Illegal " + HTTPS_SERVER_PORT_PARAMETER + " : " + e.getMessage());
            }
        }
    }

    /**
     * <p>
     * 如果是<code>true</code>, {@link ServletRequest#getLocalPort()}和{@link ServletRequest#getServerPort()}的返回值
     * 将被这个Filter编辑，而不是{@link ServletRequest#getServerPort()}.
     * </p>
     * <p>
     * 默认值 : <code>false</code>
     * </p>
     * @param changeLocalPort The new flag value
     */
    public void setChangeLocalPort(boolean changeLocalPort) {
        this.changeLocalPort = changeLocalPort;
    }

    /**
     * <p>
     * 服务器端口号，如果{@link #protocolHeader}指示 HTTP (i.e. {@link #protocolHeader}不是 null，
     * 并且其值不同于{@link #protocolHeaderHttpsValue}).
     * </p>
     * <p>
     * 默认值 : 80
     * </p>
     * @param httpServerPort 使用的服务器端口号
     */
    public void setHttpServerPort(int httpServerPort) {
        this.httpServerPort = httpServerPort;
    }

    /**
     * <p>
     * 服务器端口号，如果 {@link #protocolHeader}指示 HTTPS
     * </p>
     * <p>
     * 默认值 : 443
     * </p>
     * @param httpsServerPort 使用的服务器端口号
     */
    public void setHttpsServerPort(int httpsServerPort) {
        this.httpsServerPort = httpsServerPort;
    }

    /**
     * <p>
     * 定义内部代理的正则表达式.
     * </p>
     * <p>
     * 默认值: 10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|169\.254.\d{1,3}.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}
     * </p>
     * @param internalProxies The regexp
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
     * 保存传入端口的报头, 通常称为<code>X-Forwarded-Port</code>.
     * 如果是<code>null</code>, 将使用{@link #httpServerPort}或{@link #httpsServerPort}.
     * </p>
     * <p>
     * 默认值 : <code>null</code>
     * </p>
     * @param portHeader The header name
     */
    public void setPortHeader(String portHeader) {
        this.portHeader = portHeader;
    }

    /**
     * <p>
     * 保持传入协议的报头, 通常称为<code>X-Forwarded-Proto</code>.
     * 如果是<code>null</code>, 将不会编辑request.scheme和request.secure.
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
     * 协议标头的不区分大小写的值，以指示传入的HTTP请求使用HTTPS.
     * </p>
     * <p>
     * 默认值 : <code>https</code>
     * </p>
     * @param protocolHeaderHttpsValue The header value
     */
    public void setProtocolHeaderHttpsValue(String protocolHeaderHttpsValue) {
        this.protocolHeaderHttpsValue = protocolHeaderHttpsValue;
    }

    /**
     * <p>
     * proxiesHeader指令指定一个header, 其中mod_remoteip 将收集被信任的中间客户端IP地址的列表，以解析实际的远程IP.
     * 注意中间的RemoteIPTrustedProxy地址记录在这个header中, 在任何中间的RemoteIPInternalProxy地址被弃用.
     * </p>
     * <p>
     * 持有HTTP请求遍历的可信代理列表的HTTP报头的名称.
     * </p>
     * <p>
     * 这个标头的值可以是逗号分隔的.
     * </p>
     * <p>
     * 默认值 : <code>X-Forwarded-By</code>
     * </p>
     * @param proxiesHeader The header name
     */
    public void setProxiesHeader(String proxiesHeader) {
        this.proxiesHeader = proxiesHeader;
    }

    /**
     * <p>
     * 提取远程IP的HTTP报头的名称.
     * </p>
     * <p>
     * 这个标头的值可以是逗号分隔的.
     * </p>
     * <p>
     * 默认值 : <code>X-Forwarded-For</code>
     * </p>
     * @param remoteIpHeader The header name
     */
    public void setRemoteIpHeader(String remoteIpHeader) {
        this.remoteIpHeader = remoteIpHeader;
    }

    /**
     * 此过滤器是否为请求使用的IP地址、主机名、协议和端口设置请求属性?
     * 这通常是结合 {@link AccessLog}使用的. 默认是<code>true</code>.
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
     * @param requestAttributesEnabled  <code>true</code>设置属性, <code>false</code>禁用属性设置.
     */
    public void setRequestAttributesEnabled(boolean requestAttributesEnabled) {
        this.requestAttributesEnabled = requestAttributesEnabled;
    }

    /**
     * <p>
     * 正则表达式定义受信任的代理，当他们出现在{@link #remoteIpHeader} header中.
     * </p>
     * <p>
     * 默认值 : 空列表, 没有外部代理被信任.
     * </p>
     * @param trustedProxies 可信任的代理表达式
     */
    public void setTrustedProxies(String trustedProxies) {
        if (trustedProxies == null || trustedProxies.length() == 0) {
            this.trustedProxies = null;
        } else {
            this.trustedProxies = Pattern.compile(trustedProxies);
        }
    }
}
