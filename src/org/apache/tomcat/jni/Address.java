package org.apache.tomcat.jni;

/** Address
 */
public class Address {

    public static final String APR_ANYADDR = "0.0.0.0";
    /**
     * 从apr_sockaddr_t填充Sockaddr类
     * 
     * @param info 要填充的Sockaddr类
     * @param sa 结构指针
     * @return <code>true</code>如果操作成功
     */
    public static native boolean fill(Sockaddr info, long sa);

    /**
     * 从apr_sockaddr_t创建Sockaddr对象.
     * 
     * @param sa 结构指针
     * @return 套接字地址
     */
    public static native Sockaddr getInfo(long sa);

    /**
     * 从主机名，地址系列和端口创建apr_sockaddr_t.
     * 
     * @param hostname 要解析的主机名或数字地址字符串, 或NULL以构建对应 0.0.0.0 or :: 的地址
     * @param family 要使用的地址系列, 或者APR_UNSPEC 由系统决定.
     * @param port 端口号.
     * @param flags 特殊处理标志:
     * <PRE>
     *       APR_IPV4_ADDR_OK          首先查询IPv4地址; 仅在第一个查询失败时查找IPv6地址;
     *                                 仅当系列为APR_UNSPEC且主机名不为NULL时才有效; 与APR_IPV6_ADDR_OK互斥
     *       APR_IPV6_ADDR_OK          首先查询IPv6地址; 仅在第一个查询失败时查找IPv4地址;
     *                                 仅当系列为APR_UNSPEC且主机名不为NULL且APR_HAVE_IPV6时才有效;与APR_IPV4_ADDR_OK互斥
     * </PRE>
     * @param p apr_sockaddr_t和相关存储的池.
     * @return 新的 apr_sockaddr_t.
     * @throws Exception 操作失败
     */
    public static native long info(String hostname, int family,
                                   int port, int flags, long p)
        throws Exception;
    /**
     * 从apr_sockaddr_t中查找主机名.
     * 
     * @param sa The apr_sockaddr_t.
     * @param flags 特殊处理标志.
     * 
     * @return 主机名.
     */
    public static native String getnameinfo(long sa, int flags);

    /**
     * 返回APR套接字地址中的IP地址（数字地址字符串格式）. APR将从apr_sockaddr_t池中为IP地址字符串分配存储空间.
     * 
     * @param sa 要引用的套接字地址.
     * @return IP 地址.
     */
    public static native String getip(long sa);

    /**
     * 给定apr_sockaddr_t和服务名称，设置服务的端口.
     * 
     * @param sockaddr 将设置其端口的apr_sockaddr_t
     * @param servname 要使用的服务的名称
     * 
     * @return APR状态码.
     */
    public static native int getservbyname(long sockaddr, String servname);

    /**
     * 从apr_socket_t返回apr_sockaddr_t.
     * 
     * @param which 想要apr_sockaddr_t用于哪个接口?
     * @param sock 要使用的套接字
     * 
     * @return 返回的 apr_sockaddr_t.
     * @throws Exception 发生错误
     */
    public static native long get(int which, long sock)
        throws Exception;

    /**
     * 查看两个APR套接字地址中的IP地址是否相同. 存在适当的逻辑用于将IPv4映射的IPv6地址与IPv4地址进行比较.
     *
     * @param a APR套接字地址之一.
     * @param b 另一个APR套接字地址.
     * @return <code>true</code> 如果地址相同
     */
    public static native boolean equal(long a, long b);

}
