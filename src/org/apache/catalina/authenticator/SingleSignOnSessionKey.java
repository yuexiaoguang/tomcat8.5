package org.apache.catalina.authenticator;

import java.io.Serializable;

import org.apache.catalina.Context;
import org.apache.catalina.Session;

/**
 * SSO用于识别会话的密钥. 使用该密钥而不是实际会话，以便于SSO信息在整个集群上复制，其中复制整个会话会产生显著的、不必要的开销.
 */
public class SingleSignOnSessionKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String sessionId;
    private final String contextName;
    private final String hostName;

    public SingleSignOnSessionKey(Session session) {
        this.sessionId = session.getId();
        Context context = session.getManager().getContext();
        this.contextName = context.getName();
        this.hostName = context.getParent().getName();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getContextName() {
        return contextName;
    }

    public String getHostName() {
        return hostName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result +
                ((sessionId == null) ? 0 : sessionId.hashCode());
        result = prime * result +
                ((contextName == null) ? 0 : contextName.hashCode());
        result = prime * result +
                ((hostName == null) ? 0 : hostName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        SingleSignOnSessionKey other = (SingleSignOnSessionKey) obj;
        if (sessionId == null) {
            if (other.sessionId != null) {
                return false;
            }
        } else if (!sessionId.equals(other.sessionId)) {
            return false;
        }
        if (contextName == null) {
            if (other.contextName != null) {
                return false;
            }
        } else if (!contextName.equals(other.contextName)) {
            return false;
        }
        if (hostName == null) {
            if (other.hostName != null) {
                return false;
            }
        } else if (!hostName.equals(other.hostName)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        // Session ID is 32. 标准文本是 36. Host could easily be 20+.
        // 上下文可以是从0向上的任何东西. 128 似乎是一个合理的大小，以适应大多数情况下不太大.
        StringBuilder sb = new StringBuilder(128);
        sb.append("Host: [");
        sb.append(hostName);
        sb.append("], Context: [");
        sb.append(contextName);
        sb.append("], SessionID: [");
        sb.append(sessionId);
        sb.append("]");
        return sb.toString();
    }
}
