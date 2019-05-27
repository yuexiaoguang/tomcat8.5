package org.apache.catalina.util;

import javax.servlet.SessionCookieConfig;

import org.apache.catalina.Context;

public class SessionConfig {

    private static final String DEFAULT_SESSION_COOKIE_NAME = "JSESSIONID";
    private static final String DEFAULT_SESSION_PARAMETER_NAME = "jsessionid";

    /**
     * 为所提供的上下文确定会话cookie的名称.
     * @param context The context
     * @return 上下文的cookie名称
     */
    public static String getSessionCookieName(Context context) {

        String result = getConfiguredSessionCookieName(context);

        if (result == null) {
            result = DEFAULT_SESSION_COOKIE_NAME;
        }

        return result;
    }

    /**
     * 为所提供的上下文确定会话cookie的名称.
     * 
     * @param context The context
     * @return 会话的参数名
     */
    public static String getSessionUriParamName(Context context) {

        String result = getConfiguredSessionCookieName(context);

        if (result == null) {
            result = DEFAULT_SESSION_PARAMETER_NAME;
        }

        return result;
    }


    private static String getConfiguredSessionCookieName(Context context) {

        // Priority is:
        // 1. 上下文中定义的cookie名称
        // 2. 为应用程序配置的Cookie名称
        // 3. 规范定义的默认值
        if (context != null) {
            String cookieName = context.getSessionCookieName();
            if (cookieName != null && cookieName.length() > 0) {
                return cookieName;
            }

            SessionCookieConfig scc =
                context.getServletContext().getSessionCookieConfig();
            cookieName = scc.getName();
            if (cookieName != null && cookieName.length() > 0) {
                return cookieName;
            }
        }

        return null;
    }


    private SessionConfig() {
        // Utility class. Hide default constructor.
    }
}
