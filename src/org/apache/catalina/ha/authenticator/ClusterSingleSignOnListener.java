package org.apache.catalina.ha.authenticator;

import org.apache.catalina.authenticator.SingleSignOnListener;
import org.apache.catalina.ha.session.ReplicatedSessionListener;

/**
 * {@link SingleSignOnListener}的集群扩展， 简单添加标记接口{@link ReplicatedSessionListener}, 它允许监听器与会话一起在集群中复制.
 */
public class ClusterSingleSignOnListener extends SingleSignOnListener implements
        ReplicatedSessionListener {

    private static final long serialVersionUID = 1L;

    public ClusterSingleSignOnListener(String ssoId) {
        super(ssoId);
    }
}
