package org.apache.tomcat.jni;

/**
 * Local socket.
 */
public class Local {

    /**
     * 创建一个套接字.
     * 
     * @param path 新套接字的地址.
     * @param cont 要使用的父级池
     * 
     * @return 已设置的新套接字.
     * @throws Exception 如果套接字创建失败
     */
    public static native long create(String path, long cont)
        throws Exception;

    /**
     * 将套接字绑定到其关联的端口
     * 
     * @param sock 要绑定的套接字
     * @param sa 要绑定的套接字地址. 这可能是我们将发现是否有其他进程使用所选端口的地方.
     *      
     * @return 操作状态
     */
    public static native int bind(long sock, long sa);

    /**
     * 监听绑定的套接字以进行连接.
     * 
     * @param sock 要监听的套接字
     * @param backlog 套接字监听队列中允许的未完成连接数. 如果此值小于零，则对于NT管道，实例数不受限制.
     *                
     * @return 操作状态
     */
    public static native int listen(long sock, int backlog);

    /**
     * 接受新的连接请求
     * 
     * @param sock 正在监听的套接字.
     * @return  连接到发出连接请求的套接字的套接字副本. 这是应该用于所有未来通信的套接字.
     *          
     * @throws Exception 如果接受失败
     */
    public static native long accept(long sock)
        throws Exception;

    /**
     * 向同一台计算机或不同计算机上的套接字发出连接请求.
     * 
     * @param sock 希望用于连接的套接字
     * @param sa 希望连接的机器的地址. 未使用NT管道.
     *           
     * @return 操作状态
     */
    public static native int connect(long sock, long sa);

}
