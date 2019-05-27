package org.apache.tomcat.jni;

/** Sockaddr
 */
public class Sockaddr {

   /** 要使用的池... */
    public long pool;
    /** 主机名 */
    public String hostname;
    /** 端口号的字符串或端口的服务名称 */
    public String servname;
    /** 数字端口 */
    public int port;
    /** 家庭 */
    public int family;
    /** 如果apr_sockaddr_info_get()找到多个地址, 指向下一个地址. */
    public long next;

}
