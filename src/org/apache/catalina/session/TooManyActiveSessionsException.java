package org.apache.catalina.session;

/**
 * 活动的会话的最大值已经达到，服务器拒绝创建任何新的会话.
 */
public class TooManyActiveSessionsException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /**
     * 活动会话的最大数量.
     */
    private final int maxActiveSessions;

    /**
     * @param message 异常的描述信息.
     * @param maxActive 活动会话的最大值.
     */
    public TooManyActiveSessionsException(String message, int maxActive) {
        super(message);
        maxActiveSessions = maxActive;
    }

    /**
     * 获取会话的最大值.
     *
     * @return 会话的最大值.
     */
    public int getMaxActiveSessions() {
        return maxActiveSessions;
    }
}
