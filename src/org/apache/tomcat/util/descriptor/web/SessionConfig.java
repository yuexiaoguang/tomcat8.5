package org.apache.tomcat.util.descriptor.web;

import java.util.EnumSet;

import javax.servlet.SessionTrackingMode;

/**
 * 表示Web应用程序的会话配置元素, 作为部署描述符中<code>&lt;session-config&gt;</code>元素的表示.
 */
public class SessionConfig {
    private Integer sessionTimeout;
    private String cookieName;
    private String cookieDomain;
    private String cookiePath;
    private String cookieComment;
    private Boolean cookieHttpOnly;
    private Boolean cookieSecure;
    private Integer cookieMaxAge;
    private final EnumSet<SessionTrackingMode> sessionTrackingModes =
        EnumSet.noneOf(SessionTrackingMode.class);

    public Integer getSessionTimeout() {
        return sessionTimeout;
    }
    public void setSessionTimeout(String sessionTimeout) {
        this.sessionTimeout = Integer.valueOf(sessionTimeout);
    }

    public String getCookieName() {
        return cookieName;
    }
    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public String getCookieDomain() {
        return cookieDomain;
    }
    public void setCookieDomain(String cookieDomain) {
        this.cookieDomain = cookieDomain;
    }

    public String getCookiePath() {
        return cookiePath;
    }
    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }

    public String getCookieComment() {
        return cookieComment;
    }
    public void setCookieComment(String cookieComment) {
        this.cookieComment = cookieComment;
    }

    public Boolean getCookieHttpOnly() {
        return cookieHttpOnly;
    }
    public void setCookieHttpOnly(String cookieHttpOnly) {
        this.cookieHttpOnly = Boolean.valueOf(cookieHttpOnly);
    }

    public Boolean getCookieSecure() {
        return cookieSecure;
    }
    public void setCookieSecure(String cookieSecure) {
        this.cookieSecure = Boolean.valueOf(cookieSecure);
    }

    public Integer getCookieMaxAge() {
        return cookieMaxAge;
    }
    public void setCookieMaxAge(String cookieMaxAge) {
        this.cookieMaxAge = Integer.valueOf(cookieMaxAge);
    }

    public EnumSet<SessionTrackingMode> getSessionTrackingModes() {
        return sessionTrackingModes;
    }
    public void addSessionTrackingMode(String sessionTrackingMode) {
        sessionTrackingModes.add(
                SessionTrackingMode.valueOf(sessionTrackingMode));
    }

}
