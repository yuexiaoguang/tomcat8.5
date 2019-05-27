package org.apache.juli.logging;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 硬编码的 java.util.logging commons-logging 实现.
 */
class DirectJDKLog implements Log {
    // no reason to hide this - but good reasons to not hide
    public final Logger logger;

    /** 备用配置 reader 和控制台格式
     */
    private static final String SIMPLE_FMT="java.util.logging.SimpleFormatter";
    private static final String SIMPLE_CFG="org.apache.juli.JdkLoggerConfig"; //doesn't exist
    private static final String FORMATTER="org.apache.juli.formatter";

    static {
        if( System.getProperty("java.util.logging.config.class") ==null  &&
                System.getProperty("java.util.logging.config.file") ==null ) {
            // 默认配置 - it sucks. 至少覆盖控制台的格式化程序
            try {
                Class.forName(SIMPLE_CFG).getConstructor().newInstance();
            } catch( Throwable t ) {
            }
            try {
                Formatter fmt= (Formatter) Class.forName(System.getProperty(
                        FORMATTER, SIMPLE_FMT)).getConstructor().newInstance();
                // 也可能用户编辑 jre/lib/logging.properties - 但在大多数情况下这真的很愚蠢
                Logger root=Logger.getLogger("");
                for (Handler handler : root.getHandlers()) {
                    // 只关心控制台 - 无论如何，这就是默认配置中使用的内容
                    if (handler instanceof  ConsoleHandler) {
                        handler.setFormatter(fmt);
                    }
                }
            } catch( Throwable t ) {
                // 可能不包括 - 将使用丑陋的默认值.
            }

        }
    }

    public DirectJDKLog(String name ) {
        logger=Logger.getLogger(name);
    }

    @Override
    public final boolean isErrorEnabled() {
        return logger.isLoggable(Level.SEVERE);
    }

    @Override
    public final boolean isWarnEnabled() {
        return logger.isLoggable(Level.WARNING);
    }

    @Override
    public final boolean isInfoEnabled() {
        return logger.isLoggable(Level.INFO);
    }

    @Override
    public final boolean isDebugEnabled() {
        return logger.isLoggable(Level.FINE);
    }

    @Override
    public final boolean isFatalEnabled() {
        return logger.isLoggable(Level.SEVERE);
    }

    @Override
    public final boolean isTraceEnabled() {
        return logger.isLoggable(Level.FINER);
    }

    @Override
    public final void debug(Object message) {
        log(Level.FINE, String.valueOf(message), null);
    }

    @Override
    public final void debug(Object message, Throwable t) {
        log(Level.FINE, String.valueOf(message), t);
    }

    @Override
    public final void trace(Object message) {
        log(Level.FINER, String.valueOf(message), null);
    }

    @Override
    public final void trace(Object message, Throwable t) {
        log(Level.FINER, String.valueOf(message), t);
    }

    @Override
    public final void info(Object message) {
        log(Level.INFO, String.valueOf(message), null);
    }

    @Override
    public final void info(Object message, Throwable t) {
        log(Level.INFO, String.valueOf(message), t);
    }

    @Override
    public final void warn(Object message) {
        log(Level.WARNING, String.valueOf(message), null);
    }

    @Override
    public final void warn(Object message, Throwable t) {
        log(Level.WARNING, String.valueOf(message), t);
    }

    @Override
    public final void error(Object message) {
        log(Level.SEVERE, String.valueOf(message), null);
    }

    @Override
    public final void error(Object message, Throwable t) {
        log(Level.SEVERE, String.valueOf(message), t);
    }

    @Override
    public final void fatal(Object message) {
        log(Level.SEVERE, String.valueOf(message), null);
    }

    @Override
    public final void fatal(Object message, Throwable t) {
        log(Level.SEVERE, String.valueOf(message), t);
    }

    // 从公共日志. 这将是java.util.logging不好的首要原因 - 设计可能非常糟糕 !

    private void log(Level level, String msg, Throwable ex) {
        if (logger.isLoggable(level)) {
            // Hack (?) 获得堆栈跟踪.
            Throwable dummyException=new Throwable();
            StackTraceElement locations[]=dummyException.getStackTrace();
            // 调用者将是第三个元素
            String cname = "unknown";
            String method = "unknown";
            if (locations != null && locations.length >2) {
                StackTraceElement caller = locations[2];
                cname = caller.getClassName();
                method = caller.getMethodName();
            }
            if (ex==null) {
                logger.logp(level, cname, method, msg);
            } else {
                logger.logp(level, cname, method, msg, ex);
            }
        }
    }

    static Log getInstance(String name) {
        return new DirectJDKLog( name );
    }
}
