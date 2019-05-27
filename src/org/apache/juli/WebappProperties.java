package org.apache.juli;

/**
 * 供Web应用程序关联的类加载器使用的接口，使其能够向JULI提供有关与其关联的Web应用程序的其他信息.
 * 对于任何应用, {@link #getWebappName()}, {@link #getHostName()}, {@link #getServiceName()}的组合必须唯一.
 */
public interface WebappProperties {

    /**
     * 返回用于与类加载器关联的Web应用程序用于日志记录的名称.
     *
     * @return 用于Web应用程序的名称，如果没有，则返回null.
     */
    String getWebappName();

    /**
     * 返回用于Host 的日志记录系统的名称，其中部署了与类加载器关联的Web应用程序.
     *
     * @return 用于部署Web应用程序的 Host 的名称; 如果没有，则为null.
     */
    String getHostName();

    /**
     * 返回用于 Service 的日志记录系统的名称，其中部署了与类加载器关联的Host.
     *
     * @return 用于部署 Host 的 Service 的名称，如果没有，则为null.
     */
    String getServiceName();

    /**
     * 使JULI能够确定Web应用程序是否包含本地配置，而JULI不必查找在SecurityManager下运行时可能无权执行的文件.
     *
     * @return {@code true} 如果Web应用程序在 /WEB-INF/classes/logging.properties 的标准位置包含日志记录配置.
     */
    boolean hasLoggingConfig();
}
