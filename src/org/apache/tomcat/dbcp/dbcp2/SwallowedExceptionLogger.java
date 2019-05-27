package org.apache.tomcat.dbcp.dbcp2;

import org.apache.juli.logging.Log;
import org.apache.tomcat.dbcp.pool2.SwallowedExceptionListener;

/**
 * 记录忽略的异常.
 */
public class SwallowedExceptionLogger implements SwallowedExceptionListener{

    private final Log log;
    private final boolean logExpiredConnections;

    /**
     * @param log logger
     */
    public SwallowedExceptionLogger(final Log log) {
        this(log, true);
    }

    /**
     * @param log logger
     * @param logExpiredConnections false 禁止记录过期的连接事件
     */
    public SwallowedExceptionLogger(final Log log, final boolean logExpiredConnections) {
        this.log = log;
        this.logExpiredConnections = logExpiredConnections;
    }

    @Override
    public void onSwallowException(final Exception e) {
        if (logExpiredConnections || !(e instanceof LifetimeExceededException)) {
            log.warn(Utils.getMessage(
                    "swallowedExceptionLogger.onSwallowedException"), e);
        }
    }
}
