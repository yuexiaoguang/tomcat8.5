package org.apache.tomcat.dbcp.dbcp2;

/**
 * Constants for use with JMX
 */
public class Constants {
    public static final String JMX_CONNECTION_POOL_BASE_EXT = ",connectionpool=";
    public static final String JMX_CONNECTION_POOL_PREFIX = "connections";

    public static final String JMX_CONNECTION_BASE_EXT =
            JMX_CONNECTION_POOL_BASE_EXT +
            JMX_CONNECTION_POOL_PREFIX +
            ",connection=";

    public static final String JMX_STATEMENT_POOL_BASE_EXT =
            JMX_CONNECTION_BASE_EXT;
    public static final String JMX_STATEMENT_POOL_PREFIX = ",statementpool=statements";
}
