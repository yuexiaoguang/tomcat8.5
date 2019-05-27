package org.apache.tomcat.jni;

/** Multicast
 */
public class Multicast {

    /**
     * 加入 Multicast Group
     * 
     * @param sock 要加入多播组的套接字
     * @param join 要加入的多播组的地址
     * @param iface 要使用的接口的地址. 如果传递NULL, 将使用默认的多播接口. (OS Dependent)
     * @param source 接受传输的源地址 (non-NULL 暗示源特定组播)
     *               
     * @return 操作状态
     */
    public static native int join(long sock, long join,
                                  long iface, long source);

    /**
     * 离开 Multicast Group. 所有参数必须与apr_mcast_join相同.
     * 
     * @param sock 要离开多播组的套接字
     * @param addr 要离开的多播组的地址
     * @param iface 要使用的接口的地址. 如果传递NULL, 将使用默认的多播接口. (OS Dependent)
     * @param source 接受传输的源地址 (non-NULL 暗示源特定组播)
     *               
     * @return 操作状态
     */
    public static native int leave(long sock, long addr,
                                   long iface, long source);

    /**
     * 为多播传输设置多播生存时间（ttl）.
     * 
     * @param sock 用于设置多播ttl的套接字
     * @param ttl 分配的时间. 0-255, default=1
     * <br><b>Remark :</b> 如果 TTL 是 0, 只有本地计算机上的套接字才能看到数据包, 并且仅在启用了多播环时.
     * 
     * @return 操作状态
     */
    public static native int hops(long sock, int ttl);

    /**
     * 切换IP组播环
     * 
     * @param sock 用于设置多播环的套接字
     * @param opt false=禁用, true=启用
     * 
     * @return 操作状态
     */
    public static native int loopback(long sock, boolean opt);


    /**
     * 设置要用于传出多播传输的接口.
     * 
     * @param sock 用于设置多播接口的套接字
     * @param iface 用于多播的接口的地址
     * 
     * @return 操作状态
     */
    public static native int ointerface(long sock, long iface);

}
