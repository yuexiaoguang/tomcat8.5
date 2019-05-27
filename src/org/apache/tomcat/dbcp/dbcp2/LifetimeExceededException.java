package org.apache.tomcat.dbcp.dbcp2;

/**
 * 超出连接的最大生命周期时抛出异常.
 */
 class LifetimeExceededException extends Exception {

    private static final long serialVersionUID = -3783783104516492659L;

    public LifetimeExceededException() {
        super();
    }

    public LifetimeExceededException(final String message) {
        super(message);
    }
}
