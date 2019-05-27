package org.apache.catalina.manager;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.catalina.Session;
import org.apache.catalina.manager.util.SessionUtils;

public class JspHelper {

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private JspHelper() {
        super();
    }

    /**
     * 尝试从会话中获取用户区域设置.
     * IMPLEMENTATION NOTE: 此方法对TabeStruts 3和Struts 1 .x有明确的支持
     *
     * @param in_session 应该从中猜出区域设置的会话
     *
     * @return String
     */
    public static String guessDisplayLocaleFromSession(Session in_session) {
        return localeToString(SessionUtils.guessLocaleFromSession(in_session));
    }
    private static String localeToString(Locale locale) {
        if (locale != null) {
            return escapeXml(locale.toString());//locale.getDisplayName();
        } else {
            return "";
        }
    }

    /**
     * 尝试从会话中获取用户名.
     * 
     * @param in_session Servlet会话
     * @return 用户名
     */
    public static String guessDisplayUserFromSession(Session in_session) {
        Object user = SessionUtils.guessUserFromSession(in_session);
        return escapeXml(user);
    }


    public static String getDisplayCreationTimeForSession(Session in_session) {
        try {
            if (in_session.getCreationTime() == 0) {
                return "";
            }
            DateFormat formatter = new SimpleDateFormat(DATE_TIME_FORMAT);
            return formatter.format(new Date(in_session.getCreationTime()));
        } catch (IllegalStateException ise) {
            //ignore: invalidated session
            return "";
        }
    }

    public static String getDisplayLastAccessedTimeForSession(Session in_session) {
        try {
            if (in_session.getLastAccessedTime() == 0) {
                return "";
            }
            DateFormat formatter = new SimpleDateFormat(DATE_TIME_FORMAT);
            return formatter.format(new Date(in_session.getLastAccessedTime()));
        } catch (IllegalStateException ise) {
            //ignore: invalidated session
            return "";
        }
    }

    public static String getDisplayUsedTimeForSession(Session in_session) {
        try {
            if (in_session.getCreationTime() == 0) {
                return "";
            }
        } catch (IllegalStateException ise) {
            //ignore: invalidated session
            return "";
        }
        return secondsToTimeString(SessionUtils.getUsedTimeForSession(in_session)/1000);
    }

    public static String getDisplayTTLForSession(Session in_session) {
        try {
            if (in_session.getCreationTime() == 0) {
                return "";
            }
        } catch (IllegalStateException ise) {
            //ignore: invalidated session
            return "";
        }
        return secondsToTimeString(SessionUtils.getTTLForSession(in_session)/1000);
    }

    public static String getDisplayInactiveTimeForSession(Session in_session) {
        try {
            if (in_session.getCreationTime() == 0) {
                return "";
            }
        } catch (IllegalStateException ise) {
            //ignore: invalidated session
            return "";
        }
        return secondsToTimeString(SessionUtils.getInactiveTimeForSession(in_session)/1000);
    }

    public static String secondsToTimeString(long in_seconds) {
        StringBuilder buff = new StringBuilder(9);
        if (in_seconds < 0) {
            buff.append('-');
            in_seconds = -in_seconds;
        }
        long rest = in_seconds;
        long hour = rest / 3600;
        rest = rest % 3600;
        long minute = rest / 60;
        rest = rest % 60;
        long second = rest;
        if (hour < 10) {
            buff.append('0');
        }
        buff.append(hour);
        buff.append(':');
        if (minute < 10) {
            buff.append('0');
        }
        buff.append(minute);
        buff.append(':');
        if (second < 10) {
            buff.append('0');
        }
        buff.append(second);
        return buff.toString();
    }


    /*
     * Following copied from org.apache.taglibs.standard.tag.common.core.Util v1.1.2
     */

    private static final int HIGHEST_SPECIAL = '>';
    private static final char[][] specialCharactersRepresentation =
            new char[HIGHEST_SPECIAL + 1][];
    static {
        specialCharactersRepresentation['&'] = "&amp;".toCharArray();
        specialCharactersRepresentation['<'] = "&lt;".toCharArray();
        specialCharactersRepresentation['>'] = "&gt;".toCharArray();
        specialCharactersRepresentation['"'] = "&#034;".toCharArray();
        specialCharactersRepresentation['\''] = "&#039;".toCharArray();
    }

    public static String escapeXml(Object obj) {
        String value = null;
        try {
            value = (obj == null) ? null : obj.toString();
        } catch (Exception e) {
            // Ignore
        }
        return escapeXml(value);
    }

    /**
     * 执行下列子字符串替换 (促进输出 XML/HTML 页面):
     *
     *    &amp; -&gt; &amp;amp;
     *    &lt; -&gt; &amp;lt;
     *    &gt; -&gt; &amp;gt;
     *    " -&gt; &amp;#034;
     *    ' -&gt; &amp;#039;
     *
     * See also OutSupport.writeEscapedXml().
     * 
     * @param buffer 要转义的XML
     * @return 转义的 XML
     */
    @SuppressWarnings("null") // escapedBuffer cannot be null
    public static String escapeXml(String buffer) {
        if (buffer == null) {
            return "";
        }
        int start = 0;
        int length = buffer.length();
        char[] arrayBuffer = buffer.toCharArray();
        StringBuilder escapedBuffer = null;

        for (int i = 0; i < length; i++) {
            char c = arrayBuffer[i];
            if (c <= HIGHEST_SPECIAL) {
                char[] escaped = specialCharactersRepresentation[c];
                if (escaped != null) {
                    // 创建StringBuilder保存转义的XML字符串
                    if (start == 0) {
                        escapedBuffer = new StringBuilder(length + 5);
                    }
                    // 添加未转义的部分
                    if (start < i) {
                        escapedBuffer.append(arrayBuffer,start,i-start);
                    }
                    start = i + 1;
                    // 添加转义的 xml
                    escapedBuffer.append(escaped);
                }
            }
        }
        // 不需要XML转义
        if (start == 0) {
            return buffer;
        }
        // 添加剩余的未转义的部分
        if (start < length) {
            escapedBuffer.append(arrayBuffer,start,length-start);
        }
        return escapedBuffer.toString();
    }

    public static String formatNumber(long number) {
        return NumberFormat.getNumberInstance().format(number);
    }
}
