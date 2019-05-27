package org.apache.catalina.valves;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 网络爬虫可以触发成千上万个会话的创建，因为它们爬行某个站点可能会导致内存显著的消耗.
 * 这个 Valve 确保爬虫与单个会话关联 - 就像正常的用户 - 不管它们是否提供了请求的会话token.
 */
public class CrawlerSessionManagerValve extends ValveBase implements HttpSessionBindingListener {

    private static final Log log = LogFactory.getLog(CrawlerSessionManagerValve.class);

    private final Map<String, String> clientIpSessionId = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdClientIp = new ConcurrentHashMap<>();

    private String crawlerUserAgents = ".*[bB]ot.*|.*Yahoo! Slurp.*|.*Feedfetcher-Google.*";
    private Pattern uaPattern = null;

    private String crawlerIps = null;
    private Pattern ipPattern = null;

    private int sessionInactiveInterval = 60;


    /**
     * 可以配置异步支持.
     */
    public CrawlerSessionManagerValve() {
        super(true);
    }


    /**
     * 指定基于提供的User-Agent header来标识爬虫的正则表达式 (使用 {@link Pattern}).
     * 默认是 ".*GoogleBot.*|.*bingbot.*|.*Yahoo! Slurp.*"
     *
     * @param crawlerUserAgents 正则表达式, 使用  {@link Pattern}
     */
    public void setCrawlerUserAgents(String crawlerUserAgents) {
        this.crawlerUserAgents = crawlerUserAgents;
        if (crawlerUserAgents == null || crawlerUserAgents.length() == 0) {
            uaPattern = null;
        } else {
            uaPattern = Pattern.compile(crawlerUserAgents);
        }
    }

    /**
     * @return 当前正则表达式, 用于匹配用户代理.
     */
    public String getCrawlerUserAgents() {
        return crawlerUserAgents;
    }


    /**
     * 指定基于提供的IP 地址来标识爬虫的正则表达式 (使用 {@link Pattern}).
     * 没有默认的IPs.
     *
     * @param crawlerIps 正则表达式, 使用 {@link Pattern}
     */
    public void setCrawlerIps(String crawlerIps) {
        this.crawlerIps = crawlerIps;
        if (crawlerIps == null || crawlerIps.length() == 0) {
            ipPattern = null;
        } else {
            ipPattern = Pattern.compile(crawlerIps);
        }
    }

    /**
     * @return 当前正则表达式, 用于匹配IP 地址.
     */
    public String getCrawlerIps() {
        return crawlerIps;
    }


    /**
     * 指定爬虫的session的会话超时时间 (in seconds).
     * 通常低于用户会话. 默认是 60 秒.
     *
     * @param sessionInactiveInterval   超时时间
     */
    public void setSessionInactiveInterval(int sessionInactiveInterval) {
        this.sessionInactiveInterval = sessionInactiveInterval;
    }

    /**
     * @return  会话超时时间, 秒
     */
    public int getSessionInactiveInterval() {
        return sessionInactiveInterval;
    }


    public Map<String, String> getClientIpSessionId() {
        return clientIpSessionId;
    }


    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();

        uaPattern = Pattern.compile(crawlerUserAgents);
    }


    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {

        boolean isBot = false;
        String sessionId = null;
        String clientIp = request.getRemoteAddr();

        if (log.isDebugEnabled()) {
            log.debug(request.hashCode() + ": ClientIp=" + clientIp + ", RequestedSessionId="
                    + request.getRequestedSessionId());
        }

        // 如果传入的请求具有有效的会话ID, 不需要任何动作
        if (request.getSession(false) == null) {

            // 是否是一个爬虫 - check the UA headers
            Enumeration<String> uaHeaders = request.getHeaders("user-agent");
            String uaHeader = null;
            if (uaHeaders.hasMoreElements()) {
                uaHeader = uaHeaders.nextElement();
            }

            // 如果有多个 UA header - 假设不是一个机器人
            if (uaHeader != null && !uaHeaders.hasMoreElements()) {

                if (log.isDebugEnabled()) {
                    log.debug(request.hashCode() + ": UserAgent=" + uaHeader);
                }

                if (uaPattern.matcher(uaHeader).matches()) {
                    isBot = true;

                    if (log.isDebugEnabled()) {
                        log.debug(request.hashCode() + ": Bot found. UserAgent=" + uaHeader);
                    }
                }
            }

            if (ipPattern != null && ipPattern.matcher(clientIp).matches()) {
                isBot = true;

                if (log.isDebugEnabled()) {
                    log.debug(request.hashCode() + ": Bot found. IP=" + clientIp);
                }
            }

            // 如果是一个机器人, 是否是已知的session ID?
            if (isBot) {
                sessionId = clientIpSessionId.get(clientIp);
                if (sessionId != null) {
                    request.setRequestedSessionId(sessionId);
                    if (log.isDebugEnabled()) {
                        log.debug(request.hashCode() + ": SessionID=" + sessionId);
                    }
                }
            }
        }

        getNext().invoke(request, response);

        if (isBot) {
            if (sessionId == null) {
                // BOT是否创建了一个会话, 如果是的话，记下来
                HttpSession s = request.getSession(false);
                if (s != null) {
                    clientIpSessionId.put(clientIp, s.getId());
                    sessionIdClientIp.put(s.getId(), clientIp);
                    // 会话过期时, 将调用 #valueUnbound()
                    s.setAttribute(this.getClass().getName(), this);
                    s.setMaxInactiveInterval(sessionInactiveInterval);

                    if (log.isDebugEnabled()) {
                        log.debug(request.hashCode() + ": New bot session. SessionID=" + s.getId());
                    }
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(
                            request.hashCode() + ": Bot session accessed. SessionID=" + sessionId);
                }
            }
        }
    }


    @Override
    public void valueBound(HttpSessionBindingEvent event) {
        // NOOP
    }


    @Override
    public void valueUnbound(HttpSessionBindingEvent event) {
        String clientIp = sessionIdClientIp.remove(event.getSession().getId());
        if (clientIp != null) {
            clientIpSessionId.remove(clientIp);
        }
    }
}
