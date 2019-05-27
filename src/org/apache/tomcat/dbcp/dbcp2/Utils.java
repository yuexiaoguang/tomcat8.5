package org.apache.tomcat.dbcp.dbcp2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Utility methods.
 */
public final class Utils {

    private static final ResourceBundle messages = ResourceBundle.getBundle(
            Utils.class.getPackage().getName() + ".LocalStrings");

    public static final boolean IS_SECURITY_ENABLED =
            System.getSecurityManager() != null;

    /** 任何以此值开头的SQL_STATE都被视为致命的断开连接 */
    public static final String DISCONNECTION_SQL_CODE_PREFIX = "08";

    /**
     * 致命连接错误的SQL代码.
     * <ul>
     *  <li>57P01 (ADMIN SHUTDOWN)</li>
     *  <li>57P02 (CRASH SHUTDOWN)</li>
     *  <li>57P03 (CANNOT CONNECT NOW)</li>
     *  <li>01002 (SQL92 disconnect error)</li>
     *  <li>JZ0C0 (Sybase disconnect error)</li>
     *  <li>JZ0C1 (Sybase disconnect error)</li>
     * </ul>
     */
    public static final Set<String> DISCONNECTION_SQL_CODES;

    static {
        DISCONNECTION_SQL_CODES = new HashSet<>();
        DISCONNECTION_SQL_CODES.add("57P01"); // ADMIN SHUTDOWN
        DISCONNECTION_SQL_CODES.add("57P02"); // CRASH SHUTDOWN
        DISCONNECTION_SQL_CODES.add("57P03"); // CANNOT CONNECT NOW
        DISCONNECTION_SQL_CODES.add("01002"); // SQL92 disconnect error
        DISCONNECTION_SQL_CODES.add("JZ0C0"); // Sybase disconnect error
        DISCONNECTION_SQL_CODES.add("JZ0C1"); // Sybase disconnect error
    }

    private Utils() {
        // not instantiable
    }

    /**
     * 关闭 ResultSet (可能是 null).
     *
     * @param rset ResultSet, 可能是 {@code null}
     */
    public static void closeQuietly(final ResultSet rset) {
        if (rset != null) {
            try {
                rset.close();
            } catch (final Exception e) {
                // ignored
            }
        }
    }

    /**
     * 关闭 Connection (可能是 null).
     *
     * @param conn a Connection, 可能是 {@code null}
     */
    public static void closeQuietly(final Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (final Exception e) {
                // ignored
            }
        }
    }

    /**
     * 关闭 Statement (可能是 null).
     *
     * @param stmt a Statement, 可能是 {@code null}
     */
    public static void closeQuietly(final Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (final Exception e) {
                // ignored
            }
        }
    }


    /**
     * 获取给定 key 的正确i18n消息
     * @param key The message key
     * @return the message
     */
    public static String getMessage(final String key) {
        return getMessage(key, (Object[]) null);
    }


    /**
     * 获取给定 key 的正确i18n消息，并使用提供的参数替换占位符.
     * 
     * @param key The message key
     * @param args The arguments
     * @return the message
     */
    public static String getMessage(final String key, final Object... args) {
        final String msg =  messages.getString(key);
        if (args == null || args.length == 0) {
            return msg;
        }
        final MessageFormat mf = new MessageFormat(msg);
        return mf.format(args, new StringBuffer(), null).toString();
    }
}
