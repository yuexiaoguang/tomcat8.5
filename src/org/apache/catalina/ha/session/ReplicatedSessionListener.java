package org.apache.catalina.ha.session;

import java.io.Serializable;

import org.apache.catalina.SessionListener;

/**
 * 标记接口，用于指示{@link SessionListener}实现类应该通过跨集群的会话复制.
 */
public interface ReplicatedSessionListener extends SessionListener, Serializable {
}
