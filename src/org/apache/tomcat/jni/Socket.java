package org.apache.tomcat.jni;

import java.nio.ByteBuffer;

/** Socket
 */
public class Socket {

    /* 标准套接字定义 */
    public static final int SOCK_STREAM = 0;
    public static final int SOCK_DGRAM  = 1;
    /*
     * apr_sockopt Socket 选项定义
     */
    public static final int APR_SO_LINGER       = 1;    /** Linger */
    public static final int APR_SO_KEEPALIVE    = 2;    /** Keepalive */
    public static final int APR_SO_DEBUG        = 4;    /** Debug */
    public static final int APR_SO_NONBLOCK     = 8;    /** Non-blocking IO */
    public static final int APR_SO_REUSEADDR    = 16;   /** Reuse addresses */
    public static final int APR_SO_SNDBUF       = 64;   /** Send buffer */
    public static final int APR_SO_RCVBUF       = 128;  /** Receive buffer */
    public static final int APR_SO_DISCONNECTED = 256;  /** Disconnected */
    /** 对于SCTP套接字, 这在内部映射到STCP_NODELAY. */
    public static final int APR_TCP_NODELAY     = 512;
    public static final int APR_TCP_NOPUSH      = 1024; /** No push */
    /** 
     * 当设置APR_TCP_NOPUSH 和 APR_TCP_NODELAY时, 此标志仅在内部设置，以表示当 NOPUSH 关闭时应再次打开 APR_TCP_NODELAY
     */
    public static final int APR_RESET_NODELAY   = 2048;
    /** 
     * 设置在非阻塞套接字(timeout != 0), 在这个套接字上前一个read()没有完全填充缓冲区.
     * 下一个apr_socket_recv()将首先调用select()/poll(), 而不是直接进入read().
     * (也可以由应用程序设置, 以在下次读取之前强制执行select()/poll() 调用, 在应用程序期望立即读取失败的情况下.)
     */
    public static final int APR_INCOMPLETE_READ = 4096;
    /** 
     * 类似于APR_INCOMPLETE_READ, 但是用于写入
     */
    public static final int APR_INCOMPLETE_WRITE = 8192;
    /** 
     * 不接受IPv6监听套接字上的IPv4连接.
     */
    public static final int APR_IPV6_V6ONLY      = 16384;
    /** 
     * 在数据可用之前延迟接受新连接.
     */
    public static final int APR_TCP_DEFER_ACCEPT = 32768;

    /** 
     * 定义应该发生什么类型的套接字关闭.
     * apr_shutdown_how_e enum
     */
    public static final int APR_SHUTDOWN_READ      = 0; /** 不再允许读取请求 */
    public static final int APR_SHUTDOWN_WRITE     = 1; /** 不再允许写入请求 */
    public static final int APR_SHUTDOWN_READWRITE = 2; /** 不再允许读取或写入请求 */

    public static final int APR_IPV4_ADDR_OK = 0x01;
    public static final int APR_IPV6_ADDR_OK = 0x02;

    public static final int APR_UNSPEC = 0;
    public static final int APR_INET   = 1;
    public static final int APR_INET6  = 2;

    public static final int APR_PROTO_TCP  =   6; /** TCP  */
    public static final int APR_PROTO_UDP  =  17; /** UDP  */
    public static final int APR_PROTO_SCTP = 132; /** SCTP */

    /**
     * 是否对远程或本地套接字apr_interface_e感兴趣
     */
    public static final int APR_LOCAL  = 0;
    public static final int APR_REMOTE = 1;

    /* Socket.get types */
    public static final int SOCKET_GET_POOL = 0;
    public static final int SOCKET_GET_IMPL = 1;
    public static final int SOCKET_GET_APRS = 2;
    public static final int SOCKET_GET_TYPE = 3;

    /**
     * 创建一个套接字.
     * 
     * @param family 套接字的地址族 (e.g., APR_INET).
     * @param type 套接字的类型 (e.g., SOCK_STREAM).
     * @param protocol 套接字的协议 (e.g., APR_PROTO_TCP).
     * @param cont 要使用的父级池
     * 
     * @return 已设置的新套接字.
     * @throws Exception 创建套接字出错
     */
    public static native long create(int family, int type,
                                     int protocol, long cont)
        throws Exception;


    /**
     *关闭套接字的读取、写入.
     * <br>
     * 这实际上并没有关闭套接字描述符, 它只是控制哪些调用在套接字上仍然有效.
     *      
     * @param thesocket 要关闭的套接字
     * @param how 如何关闭套接字.  以下其中之一:
     * <PRE>
     * APR_SHUTDOWN_READ         不再允许读取请求
     * APR_SHUTDOWN_WRITE        不再允许写入请求
     * APR_SHUTDOWN_READWRITE    不再允许读取或写入请求
     * </PRE>
     * @return 操作状态
     */
    public static native int shutdown(long thesocket, int how);

    /**
     * 关闭套接字.
     * 
     * @param thesocket 要关闭的套接字
     * 
     * @return 操作状态
     */
    public static native int close(long thesocket);

    /**
     * 销毁与套接字关联的池
     * 
     * @param thesocket
     */
    public static native void destroy(long thesocket);

    /**
     * 将套接字绑定到其关联的端口
     * 
     * @param sock 要绑定的套接字
     * @param sa 要绑定的套接字地址.  这可能是发现是否有其他进程使用所选端口的地方.
     *      
     * @return 操作状态
     */
    public static native int bind(long sock, long sa);

    /**
     * 监听一个绑定的套接字以进行连接.
     * 
     * @param sock 要监听的套接字
     * @param backlog 套接字监听队列中允许的未完成连接数.  如果此值小于零, 监听队列大小设置为零.
     *                
     * @return 操作状态
     */
    public static native int listen(long sock, int backlog);

    /**
     * 接受新的连接请求
     * 
     * @param sock 要监听的套接字.
     * @param pool 新套接字的池.
     * 
     * @return  连接到发出连接请求的套接字的套接字副本. 这是应该用于所有未来通信的套接字.
     * @throws Exception 套接字接受错误
     */
    public static native long acceptx(long sock, long pool)
        throws Exception;

    /**
     * 接受新的连接请求
     * 
     * @param sock 正在监听的套接字.
     * 
     * @return 连接到发出连接请求的套接字的套接字副本. 这是应该用于所有未来通信的套接字.
     * @throws Exception 套接字接受错误
     */
    public static native long accept(long sock)
        throws Exception;

    /**
     * 设置OS级别接受过滤器.
     * 
     * @param sock 用于放置接受过滤器的套接字.
     * @param name 接受过滤器
     * @param args 接受过滤器的任何额外参数. 在此处传递NULL将删除接受的过滤器.
     *             
     * @return 操作状态
     */
    public static native int acceptfilter(long sock, String name, String args);

    /**
     * 如果处于OOB/Urgent标记，则查询指定的套接字
     * 
     * @param sock 要查询的套接字
     * @return <code>true</code>如果套接字处于OOB/Urgent标记,
     *         否则 <code>false</code>.
     */
    public static native boolean atmark(long sock);

    /**
     * 向同一台计算机或不同计算机上的套接字发出连接请求.
     * 
     * @param sock 希望用于连接的套接字
     * @param sa 希望连接的机器的地址.
     * 
     * @return 操作状态
     */
    public static native int connect(long sock, long sa);

    /**
     * 通过网络发送数据.
     * <PRE>
     * 默认情况下，此函数的作用类似于阻塞写入. 要改变这种行为, 使用 apr_socket_timeout_set() 或 APR_SO_NONBLOCK 套接字选项.
     *
     * 可能发送两个字节并返回错误.
     *
     * 永远不会返回APR_EINTR.
     * </PRE>
     * 
     * @param sock 用于发送数据的套接字.
     * @param buf 包含要发送的数据的缓冲区.
     * @param offset 字节缓冲区中的偏移量.
     * @param len 要写入的字节数; (-1) 表示全部数组.
     * 
     * @return 发送的字节数
     */
    public static native int send(long sock, byte[] buf, int offset, int len);

    /**
     * 通过网络发送数据.
     * <PRE>
     * 默认情况下，此函数的作用类似于阻塞写入. 要改变这种行为, 使用 apr_socket_timeout_set() 或 APR_SO_NONBLOCK 套接字选项.
     *
     * 可能发送两个字节并返回错误.
     *
     * 永远不会返回APR_EINTR.
     * </PRE>
     * 
     * @param sock 用于发送数据的套接字.
     * @param buf 包含要发送的数据的缓冲区.
     * @param offset 从中检索字节的第一个缓冲区数组中的偏移量; 必须是非负的且不大于buf.length
     * @param len 要访问的最大缓冲区数; 必须是非负的且不大于(buf.length - offset)
     *            
     * @return 发送的字节数
     */
    public static native int sendb(long sock, ByteBuffer buf,
                                   int offset, int len);

    /**
     * 通过网络发送数据而不重试
     * <PRE>
     * 默认情况下，此函数的作用类似于阻塞写入. 要改变这种行为, 使用 apr_socket_timeout_set() 或 APR_SO_NONBLOCK 套接字选项.
     *
     * 可能发送两个字节并返回错误.
     *
     * </PRE>
     * @param sock 用于发送数据的套接字.
     * @param buf 包含要发送的数据的缓冲区.
     * @param offset 从中检索字节的第一个缓冲区数组中的偏移量; 必须是非负的且不大于buf.length
     * @param len 要访问的最大缓冲区数; 必须是非负的且不大于(buf.length - offset)
     *            
     * @return 发送的字节数
     */
    public static native int sendib(long sock, ByteBuffer buf,
                                    int offset, int len);

    /**
     * 使用内部设置的ByteBuffer通过网络发送数据
     * 
     * @param sock 用于发送数据的套接字.
     * @param offset 从中检索字节的第一个缓冲区数组中的偏移量; 必须是非负的且不大于buf.length
     * @param len 要访问的最大缓冲区数; 必须是非负的且不大于(buf.length - offset)
     *            
     * @return 发送的字节数
     */
    public static native int sendbb(long sock,
                                   int offset, int len);

    /**
     *使用内部设置的ByteBuffer通过网络发送数据, 无需内部重试.
     * 
     * @param sock 用于发送数据的套接字.
     * @param offset 从中检索字节的第一个缓冲区数组中的偏移量; 必须是非负的且不大于buf.length
     * @param len 要访问的最大缓冲区数; 必须是非负的且不大于(buf.length - offset)
     *            
     * @return 发送的字节数
     */
    public static native int sendibb(long sock,
                                     int offset, int len);

    /**
     * 通过网络发送多个数据包.
     * <PRE>
     * 默认情况下，此函数的作用类似于阻塞写入. 要改变这种行为, 使用 apr_socket_timeout_set() 或 APR_SO_NONBLOCK 套接字选项.
     * 实际发送的字节数存储在参数3中.
     *
     * 可能发送两个字节并返回错误.
     *
     * 永远不会返回APR_EINTR.
     * </PRE>
     * 
     * @param sock 用于发送数据的套接字.
     * @param vec 从中获取数据的数组.
     * 
     * @return 发送的字节数
     */
    public static native int sendv(long sock, byte[][] vec);

    /**
     * @param sock 要发送的套接字
     * @param where 描述数据的发送位置的 apr_sockaddr_t
     * @param flags 要使用的标志
     * @param buf  要发送的数据
     * @param offset 字节缓冲区中的偏移量.
     * @param len  要发送的数据的长度
     * 
     * @return 发送的字节数
     */
    public static native int sendto(long sock, long where, int flags,
                                    byte[] buf, int offset, int len);

    /**
     * 从网络读取数据.
     *
     * <PRE>
     * 默认情况下，此函数的作用类似于阻塞读取. 要改变这种行为, 使用 apr_socket_timeout_set() 或 APR_SO_NONBLOCK 套接字选项.
     * 实际发送的字节数存储在参数3中.
     *
     * 可以接收两个字节并返回APR_EOF或其他错误.
     *
     * 永远不会返回APR_EINTR.
     * </PRE>
     * 
     * @param sock 从中读取数据的套接字.
     * @param buf 用于存储数据的缓冲区.
     * @param offset 字节缓冲区中的偏移量.
     * @param nbytes 要读取的字节数, (-1) 表示整个数组.
     * 
     * @return 收到的字节数.
     */
    public static native int recv(long sock, byte[] buf, int offset, int nbytes);

    /**
     * 使用超时从网络读取数据.
     *
     * <PRE>
     * 默认情况下，此函数的作用类似于阻塞读取. 要改变这种行为, 使用 apr_socket_timeout_set() 或 APR_SO_NONBLOCK 套接字选项.
     * 实际发送的字节数存储在参数3中.
     *
     * 可以接收两个字节并返回APR_EOF或其他错误.
     *
     * 永远不会返回APR_EINTR.
     * </PRE>
     * 
     * @param sock 从中读取数据的套接字.
     * @param buf 用于存储数据的缓冲区.
     * @param offset 字节缓冲区中的偏移量.
     * @param nbytes 要读取的字节数, (-1) 表示整个数组.
     * @param timeout 套接字超时时间（以微秒为单位）.
     * 
     * @return 收到的字节数.
     */
    public static native int recvt(long sock, byte[] buf, int offset,
                                   int nbytes, long timeout);

    /**
     * 从网络读取数据.
     *
     * <PRE>
     * 默认情况下，此函数的作用类似于阻塞读取. 要改变这种行为, 使用 apr_socket_timeout_set() 或 APR_SO_NONBLOCK 套接字选项.
     * 实际发送的字节数存储在参数3中.
     *
     * 可以接收两个字节并返回APR_EOF或其他错误.
     *
     * 永远不会返回APR_EINTR.
     * </PRE>
     * 
     * @param sock 从中读取数据的套接字.
     * @param buf 用于存储数据的缓冲区.
     * @param offset 字节缓冲区中的偏移量.
     * @param nbytes 要读取的字节数, (-1) 表示整个数组.
     * 
     * @return 如果 &ge; 0, 返回值是读取的字节数. 请注意, 当前没有可用数据的非阻塞读取将返回{@link Status#EAGAIN}, EOF将返回{@link Status#APR_EOF}.
     */
    public static native int recvb(long sock, ByteBuffer buf,
                                   int offset, int nbytes);

    /**
     * 使用内部设置的ByteBuffer从网络读取数据.
     *
     * @param sock 从中读取数据的套接字.
     * @param offset 字节缓冲区中的偏移量.
     * @param nbytes 要读取的字节数, (-1) 表示整个数组.
     * 
     * @return 如果 &gt; 0, 返回值是读取的字节数; 如果 == 0, 返回值表示EOF; 如果 &lt; 0, 返回值是错误码.
     * 			当前没有可用数据的非阻塞读取将返回 {@link Status#EAGAIN}, 而不是零.
     */
    public static native int recvbb(long sock,
                                    int offset, int nbytes);
    /**
     * 使用超时从网络读取数据.
     *
     * <PRE>
     * 默认情况下，此函数的作用类似于阻塞读取. 要改变这种行为, 使用 apr_socket_timeout_set() 或 APR_SO_NONBLOCK 套接字选项.
     * 实际发送的字节数存储在参数3中.
     *
     * 可以接收两个字节并返回APR_EOF或其他错误.
     *
     * 永远不会返回APR_EINTR.
     * </PRE>
     * 
     * @param sock 从中读取数据的套接字.
     * @param buf 用于存储数据的缓冲区.
     * @param offset 字节缓冲区中的偏移量.
     * @param nbytes 要读取的字节数, (-1) 表示整个数组.
     * @param timeout 套接字超时时间（以微秒为单位）.
     * 
     * @return 收到的字节数.
     */
    public static native int recvbt(long sock, ByteBuffer buf,
                                    int offset, int nbytes, long timeout);
    
    /**
     * 使用内部设置的ByteBuffer从带有超时时间的网络读取数据
     * 
     * @param sock 从中读取数据的套接字.
     * @param offset 字节缓冲区中的偏移量.
     * @param nbytes 要读取的字节数, (-1) 表示整个数组.
     * @param timeout 套接字超时时间（以微秒为单位）.
     * 
     * @return 收到的字节数.
     */
    public static native int recvbbt(long sock,
                                     int offset, int nbytes, long timeout);

    /**
     * @param from 用于填写接收者信息的apr_sockaddr_t
     * @param sock 要使用的套接字
     * @param flags 要使用的标志
     * @param buf  要使用的缓冲区
     * @param offset 字节缓冲区中的偏移量.
     * @param nbytes 要读取的字节数, (-1) 表示整个数组.
     * 
     * @return 收到的字节数.
     */
    public static native int recvfrom(long from, long sock, int flags,
                                      byte[] buf, int offset, int nbytes);

    /**
     * 设置指定套接字的套接字选项
     * 
     * @param sock 要设置的套接字.
     * @param opt 想要配置的选项. 以下其中之一:
     * <PRE>
     * APR_SO_DEBUG      --  打开调试信息
     * APR_SO_KEEPALIVE  --  保持连接活跃
     * APR_SO_LINGER     --  如果数据存在，则关闭
     * APR_SO_NONBLOCK   --  打开/关闭套接字的阻塞
     *                       启用此选项时, 使用APR_STATUS_IS_EAGAIN()宏来查看发送或接收函数是否无法在不阻塞的情况下传输数据.
     * APR_SO_REUSEADDR  --  验证提供给bind的地址时使用的规则应该允许重用本地地址.
     * APR_SO_SNDBUF     --  设置 SendBufferSize
     * APR_SO_RCVBUF     --  设置 ReceiveBufferSize
     * </PRE>
     * @param on 选项值.
     * 
     * @return 操作状态
     */
    public static native int optSet(long sock, int opt, int on);

    /**
     * 查询指定套接字的套接字选项
     * 
     * @param sock 要查询的套接字
     * @param opt 想要查询的选项. 以下其中之一:
     * <PRE>
     * APR_SO_DEBUG      --  打开调试信息
     * APR_SO_KEEPALIVE  --  保持连接活跃
     * APR_SO_LINGER     --  如果数据存在，则关闭
     * APR_SO_NONBLOCK   --  打开/关闭套接字的阻塞
     * APR_SO_REUSEADDR  --  验证提供给bind的地址时使用的规则应该允许重用本地地址.
     * APR_SO_SNDBUF     --  设置 SendBufferSize
     * APR_SO_RCVBUF     --  设置 ReceiveBufferSize
     * APR_SO_DISCONNECTED -- 查询套接字的断开状态. (目前仅在Windows上使用)
     * </PRE>
     * 
     * @return 调用时返回套接字选项.
     * @throws Exception 发生错误
     */
    public static native int optGet(long sock, int opt)
        throws Exception;

    /**
     * 设置指定套接字的套接字超时时间.
     * 
     * @param sock 要设置的套接字.
     * @param t 超时时间（以微秒为单位）.
     * <PRE>
     * t &gt; 0  -- 如果指定的时间过去而没有读取或写入数据, 则读取和写入调用将返回APR_TIMEUP
     * t == 0 -- 读取和写入调用不会阻塞
     * t &lt; 0  -- 读取和写入调用阻塞
     * </PRE>
     * 
     * @return 操作状态
     */
    public static native int timeoutSet(long sock, long t);

    /**
     * 查询指定套接字的套接字超时时间.
     * 
     * @param sock 要查询的套接字
     * 
     * @return 从查询返回的套接字超时时间.
     * @throws Exception 发生错误
     */
    public static native long timeoutGet(long sock)
        throws Exception;

    /**
     * 将文件从打开的文件描述符发送到套接字，以及可选的标头和结尾.
     * <br>
     * 默认情况下，此函数的作用类似于阻塞写入.  To change要改变这种行为, 使用 apr_socket_timeout_set() 或 APR_SO_NONBLOCK 套接字选项.
     * 实际发送的字节数存储在len参数中. 无条件地通过引用传递offset参数; 它的值永远不会被apr_socket_sendfile() 函数修改.
     * 
     * @param sock 正在写入的套接字
     * @param file 要从中读取的打开的文件
     * @param headers 包含要发送的标头的数组
     * @param trailers 包含要发送的结尾的数组
     * @param offset 应该开始写入的文件中的偏移量
     * @param len 从文件发送的字节数
     * @param flags 映射到OS特定标志的APR标志
     * 
     * @return 实际发送的字节数, 包括 headers, file, trailers
     */
    public static native long sendfile(long sock, long file, byte [][] headers,
                                       byte[][] trailers, long offset,
                                       long len, int flags);

    /**
     * 发送没有报头和报尾数组的文件.
     * 
     * @param sock 正在写入的套接字
     * @param file 要从中读取的打开的文件
     * @param offset 应该开始写入的文件中的偏移量
     * @param len 从文件发送的字节数
     * @param flags 映射到OS特定标志的APR标志
     * 
     * @return 实际发送的字节数
     */
    public static native long sendfilen(long sock, long file, long offset,
                                        long len, int flags);

    /**
     * 从关联的套接字池创建子池.
     * 
     * @param thesocket 要使用的套接字
     * 
     * @return 指向池的指针
     * @throws Exception 发生错误
     */
    public static native long pool(long thesocket)
        throws Exception;

    /**
     * 获取套接字结构成员
     * 
     * @param socket 要使用的套接字
     * @param what 要获取的结构成员
     * <PRE>
     * SOCKET_GET_POOL  - 套接字池
     * SOCKET_GET_IMPL  - 套接字实现对象
     * SOCKET_GET_APRS  - APR 套接字
     * SOCKET_GET_TYPE  - 套接字类型
     * </PRE>
     * 
     * @return 结构成员地址
     */
    private static native long get(long socket, int what);

    /**
     * 设置内部发送ByteBuffer.
     * 此函数将为连续的sendbb调用, 预设内部Java ByteBuffer.
     * 
     * @param sock 要使用的套接字
     * @param buf ByteBuffer
     */
    public static native void setsbb(long sock, ByteBuffer buf);

    /**
     * 设置内部接收ByteBuffer.
     * 此函数将为连续的 revcvbb/recvbbt 调用, 预设内部Java ByteBuffer.
     * 
     * @param sock 要使用的套接字
     * @param buf ByteBuffer
     */
    public static native void setrbb(long sock, ByteBuffer buf);

    /**
     * 设置与当前套接字关联的数据.
     * 
     * @param sock 当前打开的套接字.
     * @param data 要与套接字关联的用户数据.
     * @param key 与数据相关联的Key.
     * 
     * @return 操作状态
     */
      public static native int dataSet(long sock, String key, Object data);

    /**
     * 返回与当前套接字关联的数据
     * 
     * @param sock 当前打开的套接字.
     * @param key 与用户数据关联的Key.
     * 
     * @return 数据; 或 null 发生错误.
     */
     public static native Object dataGet(long sock, String key);

}
