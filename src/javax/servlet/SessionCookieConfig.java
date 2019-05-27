package javax.servlet;

/**
 * 配置Web应用程序使用的会话cookie，该应用和获取这个SessionCookieConfig的ServletContext关联.
 */
public interface SessionCookieConfig {

    /**
     * 设置会话cookie名称.
     *
     * @param name 会话cookie的名称
     *
     * @throws IllegalStateException 如果相关的ServletContext已经初始化
     */
    public void setName(String name);

    public String getName();

    /**
     * 设置会话cookie的域名
     *
     * @param domain 会话cookie域名
     *
     * @throws IllegalStateException 如果相关的ServletContext已经初始化
     */
    public void setDomain(String domain);

    public String getDomain();

    /**
     * 设置会话cookie的路径.
     *
     * @param path 会话cookie的路径
     *
     * @throws IllegalStateException 如果相关的ServletContext已经初始化
     */
    public void setPath(String path);

    public String getPath();

    /**
     * 设置会话cookie的注释
     *
     * @param comment 会话cookie的注释
     *
     * @throws IllegalStateException 如果相关的ServletContext已经初始化
     */
    public void setComment(String comment);

    public String getComment();

    /**
     * 设置会话cookie的httpOnly标志.
     *
     * @param httpOnly 设置的httpOnly
     *
     * @throws IllegalStateException 如果相关的ServletContext已经初始化
     */
    public void setHttpOnly(boolean httpOnly);

    public boolean isHttpOnly();

    /**
     * 设置会话cookie的secure标志.
     *
     * @param secure 设置的secure
     *
     * @throws IllegalStateException 如果相关的ServletContext已经初始化
     */
    public void setSecure(boolean secure);

    public boolean isSecure();

    /**
     * 设置maximum 时间.
     *
     * @param MaxAge 设置的maximum 时间
     * @throws IllegalStateException 如果相关的ServletContext已经初始化
     */
    public void setMaxAge(int MaxAge);

    public int getMaxAge();

}
