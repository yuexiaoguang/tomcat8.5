package org.apache.catalina.manager.util;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import javax.security.auth.Subject;
import javax.servlet.http.HttpSession;

import org.apache.catalina.Session;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * HttpSessions的实用方法...
 */
public class SessionUtils {

    private SessionUtils() {
        super();
    }

    // org.apache.struts.Globals.LOCALE_KEY
    private static final String STRUTS_LOCALE_KEY = "org.apache.struts.action.LOCALE";//$NON-NLS-1$
    // javax.servlet.jsp.jstl.core.Config.FMT_LOCALE
    private static final String JSTL_LOCALE_KEY   = "javax.servlet.jsp.jstl.fmt.locale";//$NON-NLS-1$
    // org.springframework.web.servlet.i18n.SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME
    private static final String SPRING_LOCALE_KEY = "org.springframework.web.servlet.i18n.SessionLocaleResolver.LOCALE";//$NON-NLS-1$
    /**
     * 动态生成小写和大写字符串. 把中间大写的字符串放在这里!
     */
    private static final String[] LOCALE_TEST_ATTRIBUTES = new String[] {
        STRUTS_LOCALE_KEY, SPRING_LOCALE_KEY, JSTL_LOCALE_KEY, "Locale", "java.util.Locale" };
    /**
     * 为了有效运行, 列出通常使用的属性. 这将首先尝试，然后将尝试自动生成的大写和小写版本.
     */
    private static final String[] USER_TEST_ATTRIBUTES = new String[] {
        "Login", "User", "userName", "UserName", "Utilisateur",
        "SPRING_SECURITY_LAST_USERNAME"};

    /**
     * 尝试从会话中获取用户区域设置.
     * IMPLEMENTATION NOTE: 该方法对TaxeLeice 3、Struts 1.X和Spring JSF进行了明确的支持，查看浏览器元标记“接受语言”来选择显示什么语言.
     * @param in_session 会话
     * @return the locale
     */
    public static Locale guessLocaleFromSession(final Session in_session) {
        return guessLocaleFromSession(in_session.getSession());
    }
    public static Locale guessLocaleFromSession(final HttpSession in_session) {
        if (null == in_session) {
            return null;
        }
        try {
            Locale locale = null;

            // 首先搜索 "known locations"
            for (int i = 0; i < LOCALE_TEST_ATTRIBUTES.length; ++i) {
                Object obj = in_session.getAttribute(LOCALE_TEST_ATTRIBUTES[i]);
                if (obj instanceof Locale) {
                    locale = (Locale) obj;
                    break;
                }
                obj = in_session.getAttribute(LOCALE_TEST_ATTRIBUTES[i].toLowerCase(Locale.ENGLISH));
                if (obj instanceof Locale) {
                    locale = (Locale) obj;
                    break;
                }
                obj = in_session.getAttribute(LOCALE_TEST_ATTRIBUTES[i].toUpperCase(Locale.ENGLISH));
                if (obj instanceof Locale) {
                    locale = (Locale) obj;
                    break;
                }
            }

            if (null != locale) {
                return locale;
            }

            // Tapestry 3.0: Engine stored in session under "org.apache.tapestry.engine:" + config.getServletName()
            // TODO: Tapestry 4+
            final List<Object> tapestryArray = new ArrayList<>();
            for (Enumeration<String> enumeration = in_session.getAttributeNames(); enumeration.hasMoreElements();) {
                String name = enumeration.nextElement();
                if (name.indexOf("tapestry") > -1 && name.indexOf("engine") > -1 && null != in_session.getAttribute(name)) {//$NON-NLS-1$ //$NON-NLS-2$
                    tapestryArray.add(in_session.getAttribute(name));
                }
            }
            if (tapestryArray.size() == 1) {
                // 发现潜在的Engine! 调用 getLocale().
                Object probableEngine = tapestryArray.get(0);
                if (null != probableEngine) {
                    try {
                        Method readMethod = probableEngine.getClass().getMethod("getLocale", (Class<?>[])null);//$NON-NLS-1$
                        // Call the property getter and return the value
                        Object possibleLocale = readMethod.invoke(probableEngine, (Object[]) null);
                        if (possibleLocale instanceof Locale) {
                            locale = (Locale) possibleLocale;
                        }
                    } catch (Exception e) {
                        Throwable t = ExceptionUtils
                                .unwrapInvocationTargetException(e);
                        ExceptionUtils.handleThrowable(t);
                        // stay silent
                    }
                }
            }

            if (null != locale) {
                return locale;
            }

            // Last guess: 遍历所有属性, 查找 Locale
            // If there is only one, consider it to be /the/ locale
            final List<Object> localeArray = new ArrayList<>();
            for (Enumeration<String> enumeration = in_session.getAttributeNames(); enumeration.hasMoreElements();) {
                String name = enumeration.nextElement();
                Object obj = in_session.getAttribute(name);
                if (obj instanceof Locale) {
                    localeArray.add(obj);
                }
            }
            if (localeArray.size() == 1) {
                locale = (Locale) localeArray.get(0);
            }

            return locale;
        } catch (IllegalStateException ise) {
            //ignore: invalidated session
            return null;
        }
    }

    /**
     * 尝试从会话中获取用户.
     * 
     * @param in_session The session
     * @return the user
     */
    public static Object guessUserFromSession(final Session in_session) {
        if (null == in_session) {
            return null;
        }
        if (in_session.getPrincipal() != null) {
            return in_session.getPrincipal().getName();
        }
        HttpSession httpSession = in_session.getSession();
        if (httpSession == null)
            return null;

        try {
            Object user = null;
            // First search "known locations"
            for (int i = 0; i < USER_TEST_ATTRIBUTES.length; ++i) {
                Object obj = httpSession.getAttribute(USER_TEST_ATTRIBUTES[i]);
                if (null != obj) {
                    user = obj;
                    break;
                }
                obj = httpSession.getAttribute(USER_TEST_ATTRIBUTES[i].toLowerCase(Locale.ENGLISH));
                if (null != obj) {
                    user = obj;
                    break;
                }
                obj = httpSession.getAttribute(USER_TEST_ATTRIBUTES[i].toUpperCase(Locale.ENGLISH));
                if (null != obj) {
                    user = obj;
                    break;
                }
            }

            if (null != user) {
                return user;
            }

            // Last guess: 遍历所有属性, 查找 java.security.Principal 或 javax.security.auth.Subject
            // If there is only one, consider it to be /the/ user
            final List<Object> principalArray = new ArrayList<>();
            for (Enumeration<String> enumeration = httpSession.getAttributeNames(); enumeration.hasMoreElements();) {
                String name = enumeration.nextElement();
                Object obj = httpSession.getAttribute(name);
                if (obj instanceof Principal || obj instanceof Subject) {
                    principalArray.add(obj);
                }
            }
            if (principalArray.size() == 1) {
                user = principalArray.get(0);
            }

            if (null != user) {
                return user;
            }

            return user;
        } catch (IllegalStateException ise) {
            //ignore: invalidated session
            return null;
        }
    }


    public static long getUsedTimeForSession(Session in_session) {
        try {
            long diffMilliSeconds = in_session.getThisAccessedTime() - in_session.getCreationTime();
            return diffMilliSeconds;
        } catch (IllegalStateException ise) {
            //ignore: invalidated session
            return -1;
        }
    }

    public static long getTTLForSession(Session in_session) {
        try {
            long diffMilliSeconds = (1000*in_session.getMaxInactiveInterval()) - (System.currentTimeMillis() - in_session.getThisAccessedTime());
            return diffMilliSeconds;
        } catch (IllegalStateException ise) {
            //ignore: invalidated session
            return -1;
        }
    }

    public static long getInactiveTimeForSession(Session in_session) {
        try {
            long diffMilliSeconds =  System.currentTimeMillis() - in_session.getThisAccessedTime();
            return diffMilliSeconds;
        } catch (IllegalStateException ise) {
            //ignore: invalidated session
            return -1;
        }
    }
}
