package org.apache.catalina.core;

import javax.servlet.SessionCookieConfig;
import javax.servlet.http.Cookie;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.util.SessionConfig;
import org.apache.tomcat.util.res.StringManager;

public class ApplicationSessionCookieConfig implements SessionCookieConfig {

    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager
            .getManager(Constants.Package);

    private boolean httpOnly;
    private boolean secure;
    private int maxAge = -1;
    private String comment;
    private String domain;
    private String name;
    private String path;
    private StandardContext context;

    public ApplicationSessionCookieConfig(StandardContext context) {
        this.context = context;
    }

    @Override
    public String getComment() {
        return comment;
    }

    @Override
    public String getDomain() {
        return domain;
    }

    @Override
    public int getMaxAge() {
        return maxAge;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public boolean isHttpOnly() {
        return httpOnly;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public void setComment(String comment) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", "comment",
                    context.getPath()));
        }
        this.comment = comment;
    }

    @Override
    public void setDomain(String domain) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", "domain name",
                    context.getPath()));
        }
        this.domain = domain;
    }

    @Override
    public void setHttpOnly(boolean httpOnly) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", "HttpOnly",
                    context.getPath()));
        }
        this.httpOnly = httpOnly;
    }

    @Override
    public void setMaxAge(int maxAge) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", "max age",
                    context.getPath()));
        }
        this.maxAge = maxAge;
    }

    @Override
    public void setName(String name) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", "name",
                    context.getPath()));
        }
        this.name = name;
    }

    @Override
    public void setPath(String path) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", "path",
                    context.getPath()));
        }
        this.path = path;
    }

    @Override
    public void setSecure(boolean secure) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", "secure",
                    context.getPath()));
        }
        this.secure = secure;
    }

    /**
     * 为给定的会话ID创建新的会话cookie
     *
     * @param context     Web应用程序的上下文
     * @param sessionId   将创建cookie的会话的ID
     * @param secure      会话cookie是否应配置为安全
     * @return 会话的cookie
     */
    public static Cookie createSessionCookie(Context context,
            String sessionId, boolean secure) {

        SessionCookieConfig scc =
            context.getServletContext().getSessionCookieConfig();

        // NOTE: 会话Cookie配置的优先级顺序为:
        //       1. Context配置
        //       2. SessionCookieConfig的值
        //       3. 默认

        Cookie cookie = new Cookie(
                SessionConfig.getSessionCookieName(context), sessionId);

        // 只应用默认值.
        cookie.setMaxAge(scc.getMaxAge());
        cookie.setComment(scc.getComment());

        if (context.getSessionCookieDomain() == null) {
            // 避免可能的 NPE
            if (scc.getDomain() != null) {
                cookie.setDomain(scc.getDomain());
            }
        } else {
            cookie.setDomain(context.getSessionCookieDomain());
        }

        // 如果请求安全，则始终设置安全
        if (scc.isSecure() || secure) {
            cookie.setSecure(true);
        }

        // 总是设置httpOnly 如果上下文被配置为
        if (scc.isHttpOnly() || context.getUseHttpOnly()) {
            cookie.setHttpOnly(true);
        }

        String contextPath = context.getSessionCookiePath();
        if (contextPath == null || contextPath.length() == 0) {
            contextPath = scc.getPath();
        }
        if (contextPath == null || contextPath.length() == 0) {
            contextPath = context.getEncodedPath();
        }
        if (context.getSessionCookiePathUsesTrailingSlash()) {
            // 处理需要'/'路径的ROOT上下文的特殊情况, 但是servlet规范使用一个空字符串
            // 也确保上下文的cookie使用的路径 /foo 不会变成 /foobar
            if (!contextPath.endsWith("/")) {
                contextPath = contextPath + "/";
            }
        } else {
            // 处理需要'/'路径的ROOT上下文的特殊情况, 但是servlet规范使用一个空字符串
            if (contextPath.length() == 0) {
                contextPath = "/";
            }
        }
        cookie.setPath(contextPath);

        return cookie;
    }
}
