package org.apache.tomcat.util.threads;

public final class Constants {

    public static final long DEFAULT_THREAD_RENEWAL_DELAY = 1000L;

    /**
     * 安全已经开启?
     */
    public static final boolean IS_SECURITY_ENABLED = (System.getSecurityManager() != null);
}
