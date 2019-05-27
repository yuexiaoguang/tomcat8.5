package org.apache.tomcat.jni;

/** SSL Socket
 */
public class SSLSocket {

    /**
     * 在SSL连接上附加APR套接字.
     * 
     * @param ctx 要使用的SSLContext.
     * @param sock 已经进行物理连接或接受的APR套接字.
     * 
     * @return APR_STATUS 代码.
     * @throws Exception 发生错误
     */
    public static native int attach(long ctx, long sock)
        throws Exception;

    /**
     * 进行SSL握手.
     * 
     * @param thesocket 要使用的套接字
     * 
     * @return 握手状态
     */
    public static native int handshake(long thesocket);

    /**
     * 进行SSL重新协商.
     * SSL支持SSL目录的每个目录重新配置. 这是通过在读取请求后, 再发送响应之前, 执行重新配置参数的SSL重新协商来实现的.
     * 更详细一点: 重新协商发生在读取请求行和MIME标头之后，但在读取附加的请求主体之前.
     * 原因很简单，在HTTP协议中，通常在标题和正文之间没有确定的步骤 (只有100-continue功能和分块功能), 所以Apache没有这个步骤的API钩子.
     *
     * @param thesocket 要使用的套接字
     * @return 操作状态
     */
    public static native int renegotiate(long thesocket);

    /**
     * 在客户端证书验证中, 设置客户端证书验证类型和CA证书的最大深度.
     * <br>
     * 用于在开始重新协商之前更改连接的验证级别.
     * <br>
     * 以下级别可用:
     * <PRE>
     * SSL_CVERIFY_NONE           - 根本不需要客户端证书
     * SSL_CVERIFY_OPTIONAL       - 客户端可以出示有效证书
     * SSL_CVERIFY_REQUIRE        - 客户端必须出示有效证书
     * SSL_CVERIFY_OPTIONAL_NO_CA - 客户端可以提供有效证书，但不需要（成功）验证
     * </PRE>
     * <br>
     * @param sock  要更改的套接字.
     * @param level 客户端证书验证类型.
     * @param depth 从客户端到可信CA的链中允许的最大证书数. 使用0或更小的值可保持当前值不变
     */
    public static native void setVerify(long sock, int level, int depth);

    /**
     * 将SSL Info参数作为字节数组返回.
     *
     * @param sock 从中读取数据的套接字.
     * @param id 参数 id.
     * 
     * @return 包含info id值的字节数组
     * @throws Exception 发生错误
     */
    public static native byte[] getInfoB(long sock, int id)
        throws Exception;

    /**
     * 将SSL Info参数作为String返回.
     *
     * @param sock 从中读取数据的套接字.
     * @param id 参数 id.
     * 
     * @return 包含info id值的String.
     * @throws Exception 发生错误
     */
    public static native String getInfoS(long sock, int id)
        throws Exception;

    /**
     * 将SSL Info参数作为int返回.
     *
     * @param sock 从中读取数据的套接字.
     * @param id 参数 id.
     * 
     * @return 包含信息id值的整数; 或错误时为-1.
     * @throws Exception 发生错误
     */
    public static native int getInfoI(long sock, int id)
        throws Exception;


    /**
     * 获取通过ALPN协商的协议的名称. 仅在TLS握手完成后有效.
     *
     * @param sock                  Socket
     * @param negotiatedProtocol    用于存储约定协议的字节数组
     *
     * @return 商定协议的长度. 零意味着没有达成协议.
     */
    public static native int getALPN(long sock, byte[] negotiatedProtocol);

}
