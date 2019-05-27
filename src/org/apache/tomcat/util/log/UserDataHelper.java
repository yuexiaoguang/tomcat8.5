package org.apache.tomcat.util.log;

import org.apache.juli.logging.Log;

/**
 * 与无效的输入数据关联的日志记录.
 * 开发人员可能希望记录所有无效的输入数据实例以协助调试，而在生产中，可能不希望记录任何无效数据的内容.
 * 可以使用以下设置:
 * <ul>
 * <li>NOTHING: 不记录.</li>
 * <li>DEBUG_ALL: 记录DEBUG日志级别的所有问题.</li>
 * <li>INFO_THEN_DEBUG: 在INFO日志级别记录第一个问题，并在DEBUG级别的以下TBD（可配置）秒内记录任何其他问题</li>
 * <li>INFO_ALL: 记录INFO日志级别的所有问题.</li>
 * </ul>
 * 默认, 使用INFO_THEN_DEBUG时抑制时间为24小时.
 *
 * NOTE: 这个类不是完全线程安全的. 使用INFO_THEN_DEBUG时, 可能会在删除DEBUG之前, 记录多条INFO消息.
 */
public class UserDataHelper {

    private final Log log;

    private final Config config;

    // 值0等效于使用INFO_ALL
    // 负值将触发无限抑制
    // 值是毫秒
    private final long suppressionTime;

    private volatile long lastInfoTime = 0;


    public UserDataHelper(Log log) {
        this.log = log;

        Config tempConfig;
        String configString = System.getProperty(
                "org.apache.juli.logging.UserDataHelper.CONFIG");
        if (configString == null) {
            tempConfig = Config.INFO_THEN_DEBUG;
        } else {
            try {
                tempConfig = Config.valueOf(configString);
            } catch (IllegalArgumentException iae) {
                // Ignore - use default
                tempConfig = Config.INFO_THEN_DEBUG;
            }
        }

        // 默认抑制时间为1天.
        suppressionTime = Integer.getInteger(
                "org.apache.juli.logging.UserDataHelper.SUPPRESSION_TIME",
                60 * 60 * 24).intValue() * 1000L;

        if (suppressionTime == 0) {
            tempConfig = Config.INFO_ALL;
        }

        config = tempConfig;
    }


    /**
     * 返回下一条日志消息的日志模式, 或<code>null</code> 如果不记录该消息.
     *
     * <p>
     * 如果启用<code>INFO_THEN_DEBUG</code> 配置, 此方法可能会更改此对象的内部状态.
     *
     * @return 日志模式, 或<code>null</code>
     */
    public Mode getNextMode() {
        if (Config.NONE == config) {
            return null;
        } else if (Config.DEBUG_ALL == config) {
            return log.isDebugEnabled() ? Mode.DEBUG : null;
        } else if (Config.INFO_THEN_DEBUG == config) {
            if (logAtInfo()) {
                return log.isInfoEnabled() ? Mode.INFO_THEN_DEBUG : null;
            } else {
                return log.isDebugEnabled() ? Mode.DEBUG : null;
            }
        } else if (Config.INFO_ALL == config) {
            return log.isInfoEnabled() ? Mode.INFO : null;
        }
        // Should never happen
        return null;
    }


    /*
     * 不是完全线程安全的，但足以满足此用例.
     */
    private boolean logAtInfo() {

        if (suppressionTime < 0 && lastInfoTime > 0) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (lastInfoTime + suppressionTime > now) {
            return false;
        }

        lastInfoTime = now;
        return true;
    }


    private static enum Config {
        NONE,
        DEBUG_ALL,
        INFO_THEN_DEBUG,
        INFO_ALL
    }

    /**
     * 下一条日志消息的日志模式.
     */
    public static enum Mode {
        DEBUG,
        INFO_THEN_DEBUG,
        INFO
    }
}
