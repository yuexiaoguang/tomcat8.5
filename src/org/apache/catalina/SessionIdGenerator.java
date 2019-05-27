package org.apache.catalina;

public interface SessionIdGenerator {

    /**
     * @return 与此节点相关联的节点标识符，该节点标识符将包含在生成的会话ID中.
     */
    public String getJvmRoute();

    /**
     * 指定与此节点相关联的节点标识符，该节点标识符将包含在生成的会话ID中.
     *
     * @param jvmRoute  节点标识符
     */
    public void setJvmRoute(String jvmRoute);

    /**
     * @return 会话ID的字节数
     */
    public int getSessionIdLength();

    /**
     * 指定会话ID的字节数
     *
     * @param sessionIdLength   字节数
     */
    public void setSessionIdLength(int sessionIdLength);

    /**
     * 生成并返回新的会话标识符.
     *
     * @return 新生成的会话ID
     */
    public String generateSessionId();

    /**
     * 生成并返回新的会话标识符.
     *
     * @param route   在生成ID中包含的节点标识符
     * 
     * @return 新生成的会话ID
     */
    public String generateSessionId(String route);
}
