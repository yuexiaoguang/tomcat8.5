package org.apache.tomcat.jni;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** SSL Context
 */
public final class SSLContext {

    public static final byte[] DEFAULT_SESSION_ID_CONTEXT =
            new byte[] { 'd', 'e', 'f', 'a', 'u', 'l', 't' };

    /**
     * 创建新的SSL上下文.
     *
     * @param pool 要使用的池.
     * @param protocol 要使用的SSL协议. 它可以是以下任意组合:
     * <PRE>
     * {@link SSL#SSL_PROTOCOL_SSLV2}
     * {@link SSL#SSL_PROTOCOL_SSLV3}
     * {@link SSL#SSL_PROTOCOL_TLSV1}
     * {@link SSL#SSL_PROTOCOL_TLSV1_1}
     * {@link SSL#SSL_PROTOCOL_TLSV1_2}
     * {@link SSL#SSL_PROTOCOL_ALL} ( == 所有TLS版本, 不是 SSL)
     * </PRE>
     * @param mode 要使用的SSL模式
     * <PRE>
     * SSL_MODE_CLIENT
     * SSL_MODE_SERVER
     * SSL_MODE_COMBINED
     * </PRE>
     *
     * @return 指向新创建的SSL上下文的指针的Java表示形式
     *
     * @throws Exception 如果无法创建SSL上下文
     */
    public static native long make(long pool, int protocol, int mode) throws Exception;

    /**
     * 释放Context使用的资源
     * 
     * @param ctx 要释放的服务器或客户端上下文.
     * 
     * @return APR状态码.
     */
    public static native int free(long ctx);

    /**
     * 设置会话上下文ID. 通常是 host:port 组合.
     * 
     * @param ctx 要使用的上下文.
     * @param id  唯一标识此上下文的字符串.
     */
    public static native void setContextId(long ctx, String id);

    /**
     * 将BIOCallback与输入或输出数据捕获相关联.
     * <br>
     * 输出字符串中的第一个单词将包含表单中的错误级别:
     * <PRE>
     * [ERROR]  -- 严重错误消息
     * [WARN]   -- 警告信息
     * [INFO]   -- 信息消息
     * [DEBUG]  -- 调试消息
     * </PRE>
     * 回调可以使用该单词来确定应用程序日志记录级别, 通过拦截 <b>write</b> 调用.
     * 如果<b>bio</b>设置为 0, 不会显示任何错误消息. 默认是使用stderr输出流.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param bio 要使用的BIO 句柄, 使用SSL.newBIO创建
     * @param dir BIO 方向 (1 : 输入; 0 : 输出).
     */
    public static native void setBIO(long ctx, long bio, int dir);

    /**
     * 设置OpenSSL选项.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param options  有关options标志, 请查看SSL.SSL_OP_*.
     */
    public static native void setOptions(long ctx, int options);

    /**
     * 获取OpenSSL选项.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @return options  有关options标志, 请查看SSL.SSL_OP_*.
     */
    public static native int getOptions(long ctx);

    /**
     * 清除OpenSSL选项.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param options  有关options标志, 请查看SSL.SSL_OP_*.
     */
    public static native void clearOptions(long ctx, int options);

    /**
     * 返回在SSL握手中启用协商的所有密码套件.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * 
     * @return ciphers
     */
    public static native String[] getCiphers(long ctx);

    /**
     * 将<b>ctx</b>的“安静关闭”标志设置为<b>mode</b>. 从<b>ctx</b>创建的SSL对象继承当时有效的<b>mode</b>, 可能为0或1.
     * <br>
     * 通常在SSL连接完成时, 各方必须使用 L&lt;SSL_shutdown(3)|SSL_shutdown(3)&gt; 发出“关闭通知”警报消息, 以便彻底关闭.
     * <br>
     * 将“安静关闭”标志设置为1时, <b>SSL.shutdown</b> 将内部标志设置为 SSL_SENT_SHUTDOWN|SSL_RECEIVED_SHUTDOWN.
     * (<b>SSL_shutdown</b> 然后表现得像使用SSL_SENT_SHUTDOWN | SSL_RECEIVED_SHUTDOWN调用.)
     * 会话因此被认为是关闭的, 但没有“关闭通知”警报发送给对等方. 此行为违反了TLS标准. 默认值为TLS标准所述的正常关闭行为.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param mode True : 设置安静关闭.
     */
    public static native void setQuietShutdown(long ctx, boolean mode);

    /**
     * 密码套件可用于SSL握手协商.
     * <br>
     * 此复杂指令使用冒号分隔的密码规范字符串，该字符串由OpenSSL密码规范组成，以配置允许客户端在SSL握手阶段协商的密码套件.
     * 请注意，此指令可以在每个服务器和每个目录的上下文中使用. 在每个服务器上下文中，它在建立连接时应用于标准SSL握手.
     * 在每个目录上下文中, 它会在读取HTTP请求之后, 但在发送HTTP响应之前, 强制使用重新配置的密码套件重新进行SSL协商.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param ciphers OpenSSL密码规范.
     * 
     * @return <code>true</code> 操作成功
     * @throws Exception 发生错误
     */
    public static native boolean setCipherSuite(long ctx, String ciphers)
        throws Exception;

    /**
     * 为客户端身份验证, 设置串联PEM编码的CA CRL的文件或PEM编码的CA证书的目录.
     * <br>
     * 该指令设置一体化文件，您可以在其中组合证书颁发机构（CA）的证书吊销列表（CRL）. 这些用于客户端身份验证.
     * 这样的文件只是各种PEM编码的CRL文件的串联，按优先顺序排列.
     * <br>
     * 此目录中的文件必须是PEM编码的，并通过散列文件名进行访问. 所以通常你不能只把证书文件放在那里:
     * 您还必须创建名为 hash-value.N 的符号链接. 并且您应该始终确保此目录包含适当的符号链接.
     * 使用mod_ssl附带的Makefile来完成此任务.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param file 客户端身份验证的串联PEM编码CA CRL文件.
     * @param path 客户端身份验证的PEM编码CA证书目录.
     * 
     * @return <code>true</code> 操作成功
     * @throws Exception 发生错误
     */
    public static native boolean setCARevocation(long ctx, String file,
                                                 String path)
        throws Exception;

    /**
     * 设置PEM编码的服务器CA证书的文件
     * <br>
     * 该指令设置可选的一体化文件，您可以在其中组装证书颁发机构（CA）的证书，这些证书构成服务器证书的证书链.
     * 这从服务器证书的颁发CA证书开始，并且可以达到根CA证书. 这样的文件只是各种PEM编码的CA证书文件的串联，通常是证书链顺序.
     * <br>
     * 不过要小心: 仅当您使用基于单个（基于RSA或DSA）的服务器证书时，提供证书链才有效. 如果您使用的是耦合RSA + DSA证书对, 只有当两个证书实际使用相同的证书链时, 这才有效.
     * 否则浏览器会在这种情况下混淆.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param file PEM编码的服务器CA证书的文件.
     * @param skipfirst 如果链文件在证书文件中，则跳过第一个证书.
     *                  
     * @return <code>true</code> 操作成功
     */
    public static native boolean setCertificateChainFile(long ctx, String file,
                                                         boolean skipfirst);

    /**
     * 设置证书
     * <br>
     * 在PEM编码的证书上指向setCertificateFile. 如果证书已加密, 然后会提示您输入密码短语.
     * 请注意，kill -HUP会再次提示. 可以在构建时间下使用“make certificate”生成测试证书.
     * 请记住, 如果您同时拥有RSA和DSA证书, 则可以并行配置 (还允许使用DSA密码等.)
     * <br>
     * 如果密钥未与证书合并, 使用密钥参数指向密钥文件. 请记住，如果您同时拥有RSA和DSA私钥，则可以并行配置 (还允许使用DSA密码等.)
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param cert 证书文件.
     * @param key 私有密钥文件，如果不在证书中则使用.
     * @param password 证书密码. 如果为null且证书已加密, 将显示密码提示.
     * @param idx 证书索引SSL_AIDX_RSA或SSL_AIDX_DSA.
     * 
     * @return <code>true</code> 操作成功
     * @throws Exception 发生错误
     */
    public static native boolean setCertificate(long ctx, String cert,
                                                String key, String password,
                                                int idx)
        throws Exception;

    /**
     * 设置内部会话缓存的大小.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param size 缓存大小
     * 
     * @return 值集
     */
    public static native long setSessionCacheSize(long ctx, long size);

    /**
     * 获取内部会话缓存的大小.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     */
    public static native long getSessionCacheSize(long ctx);

    /**
     * 设置内部会话高速缓存的超时时间（以秒为单位）.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param timeoutSeconds 超时时间
     * 
     * @return 值集
     */
    public static native long setSessionCacheTimeout(long ctx, long timeoutSeconds);

    /**
     * 获取内部会话高速缓存的超时时间（以秒为单位）.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     */
    public static native long getSessionCacheTimeout(long ctx);

    /**
     * 设置内部会话高速缓存的模式并返回先前使用的模式.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param mode 要设置的模式
     * 
     * @return 设置的值
     */
    public static native long setSessionCacheMode(long ctx, long mode);

    /**
     * 获取当前使用的内部会话缓存的模式.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     */
    public static native long getSessionCacheMode(long ctx);

    /*
     * 会话恢复统计方法.
     */
    public static native long sessionAccept(long ctx);
    public static native long sessionAcceptGood(long ctx);
    public static native long sessionAcceptRenegotiate(long ctx);
    public static native long sessionCacheFull(long ctx);
    public static native long sessionCbHits(long ctx);
    public static native long sessionConnect(long ctx);
    public static native long sessionConnectGood(long ctx);
    public static native long sessionConnectRenegotiate(long ctx);
    public static native long sessionHits(long ctx);
    public static native long sessionMisses(long ctx);
    public static native long sessionNumber(long ctx);
    public static native long sessionTimeouts(long ctx);

    /**
     * 设置TLS会话密钥. 这允许跨TFE共享密钥.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param keys 一些会话密钥
     */
    public static native void setSessionTicketKeys(long ctx, byte[] keys);

    /**
     * 为客户端身份验证设置串联PEM编码的CA证书的文件和目录
     * <br>
     * 该指令设置一体化文件，您可以在其中组装证书颁发机构证书（CA）. 这些用于客户端身份验证.
     * 这样的文件只是各种PEM编码的证书文件的串联, 按优先顺序排列. 这可以替代地并且另外用于路径.
     * <br>
     * 此目录中的文件必须是PEM编码的，并通过散列文件名进行访问. 所以通常你不能只把证书文件放在那里:
     * 您还必须创建名为 hash-value.N 的符号链接. 并且您应该始终确保此目录包含适当的符号链接.
     * 使用mod_ssl附带的Makefile来完成此任务.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param file 客户端身份验证的串联PEM编码CA证书文件.
     * @param path 客户端身份验证的PEM编码CA证书目录.
     * 
     * @return <code>true</code> 操作成功
     * 
     * @throws Exception 发生错误
     */
    public static native boolean setCACertificate(long ctx, String file,
                                                  String path)
        throws Exception;

    /**
     * 设置随机的文件.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param file 随机文件.
     */
    public static native void setRandom(long ctx, String file);

    /**
     * 设置SSL连接关闭类型
     * <br>
     * 以下级别可用:
     * <PRE>
     * SSL_SHUTDOWN_TYPE_STANDARD
     * SSL_SHUTDOWN_TYPE_UNCLEAN
     * SSL_SHUTDOWN_TYPE_ACCURATE
     * </PRE>
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param type 要使用的关闭类型.
     */
    public static native void setShutdownType(long ctx, int type);

    /**
     * 在客户端证书验证中设置客户端证书验证类型和CA证书的最大深度.
     * <br>
     * 该指令设置客户端身份验证的证书验证级别. 请注意，此指令可以在每个服务器和每个目录的上下文中使用.
     * 在每个服务器上下文中, 它适用于建立连接时标准SSL握手中使用的客户端身份验证过程.
     * 在每个目录上下文中, 它会在读取HTTP请求之后但在发送HTTP响应之前, 强制使用重新配置的客户端验证级别重新进行SSL协商.
     * <br>
     * 以下级别可用:
     * <PRE>
     * SSL_CVERIFY_NONE           - 根本不需要客户端证书
     * SSL_CVERIFY_OPTIONAL       - 客户端可以出示有效证书
     * SSL_CVERIFY_REQUIRE        - 客户端必须出示有效证书
     * SSL_CVERIFY_OPTIONAL_NO_CA - 客户可以提供有效证书，但不需要（成功）验证
     * </PRE>
     * <br>
     * 深度实际上是中间证书颁发者的最大数量, 即，在验证客户端证书时允许遵循的最大CA证书的数量.
     * 深度为0表示仅接受自签名客户端证书;
     * 默认深度为1表示客户端证书可以是自签名的, 或者必须由服务器直接知道的CA签名 (即CA的证书在<code>setCACertificatePath</code>下), 等.
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param level 客户端证书验证类型.
     * @param depth 客户端证书验证中CA证书的最大深度.
     */
    public static native void setVerify(long ctx, int level, int depth);

    public static native int setALPN(long ctx, byte[] proto, int len);

    /**
     * 当tc-native在TLS握手中遇到SNI扩展时，它将调用此方法来确定要用于连接的OpenSSL SSLContext.
     *
     * @param currentCtx   握手开始使用的OpenSSL SSLContext. 这将是与套接字关联的端点的默认OpenSSL SSLContext.
     * @param sniHostName  客户端请求的主机名
     *
     * @return 指向要用于给定主机的OpenSSL SSLContext的指针的Java表示形式; 如果没有可识别的SSLContext则为零
     */
    public static long sniCallBack(long currentCtx, String sniHostName) {
        SNICallBack sniCallBack = sniCallBacks.get(Long.valueOf(currentCtx));
        if (sniCallBack == null) {
            return 0;
        }
        return sniCallBack.getSslContext(sniHostName);
    }

    /**
     * 默认SSL上下文到SNICallBack实例（在Tomcat中这些是AprEndpoint的实例）, 将用于确定要使用的SSL上下文, 基于SNI主机名.
     * 它以这种方式构造，因为Tomcat实例可能具有多个启用TLS的端点，每个端点对于相同的主机名具有不同的SSL上下文映射.
     */
    private static final Map<Long,SNICallBack> sniCallBacks = new ConcurrentHashMap<>();

    /**
     * 注册一个OpenSSL SSLContext，用于启动可能使用SNI扩展的TLS连接，该组件将请求的主机名映射到正确的OpenSSL SSLContext，以用于连接的其余部分.
     *
     * @param defaultSSLContext  指向将用于启动TLS连接的OpenSSL SSLContext的指针的Java表示形式
     * @param sniCallBack  将通过使用<code> defaultSSLContext </ code>启动的连接接收的SNI主机名映射到正确的OpenSSL SSLContext的组件
     */
    public static void registerDefault(Long defaultSSLContext,
            SNICallBack sniCallBack) {
        sniCallBacks.put(defaultSSLContext, sniCallBack);
    }

    /**
     * 取消注册将不再用于启动可能使用SNI扩展的TLS连接的OpenSSL SSLContext.
     *
     * @param defaultSSLContext 指向将不再使用的OpenSSL SSLContext的指针的Java表示形式
     */
    public static void unregisterDefault(Long defaultSSLContext) {
        sniCallBacks.remove(defaultSSLContext);
    }


    /**
     * 由将接收回调的组件实现的接口，以根据客户端请求的主机名选择OpenSSL SSLContext.
     */
    public static interface SNICallBack {

        /**
         * 当客户端使用SNI扩展来请求特定TLS主机时，在TLS握手期间进行此回调.
         *
         * @param sniHostName 客户端请求的主机名
         *
         * @return 指向要用于给定主机的OpenSSL SSLContext的指针的Java表示形式，如果没有可识别的SSLContext则为零
         */
        public long getSslContext(String sniHostName);
    }

    /**
     * 允许将{@link CertificateVerifier}挂钩到握手处理中.
     * 这将调用{@code SSL_CTX_set_cert_verify_callback}，因此替换openssl使用的默认验证回调
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param verifier 验证者在握手期间调用.
     */
    public static native void setCertVerifyCallback(long ctx, CertificateVerifier verifier);

    /**
     * 为下一个协议协商扩展设置下一个协议
     * 
     * @param ctx 要使用的服务器上下文.
     * @param nextProtos 以逗号分隔的优先级顺序的协议列表
     *
     * @deprecated use {@link #setNpnProtos(long, String[], int)}
     */
    @Deprecated
    public static void setNextProtos(long ctx, String nextProtos) {
        setNpnProtos(ctx, nextProtos.split(","), SSL.SSL_SELECTOR_FAILURE_CHOOSE_MY_LAST_PROTOCOL);
    }

    /**
     * 为下一个协议协商扩展设置下一个协议
     * 
     * @param ctx 要使用的服务器上下文.
     * @param nextProtos 按优先顺序的协议
     * @param selectorFailureBehavior see {@link SSL#SSL_SELECTOR_FAILURE_NO_ADVERTISE}
     *                                and {@link SSL#SSL_SELECTOR_FAILURE_CHOOSE_MY_LAST_PROTOCOL}
     */
    public static native void setNpnProtos(long ctx, String[] nextProtos, int selectorFailureBehavior);

    /**
     * 为应用层协议协商扩展设置应用层协议
     * 
     * @param ctx 要使用的服务器上下文.
     * @param alpnProtos 按优先顺序的协议
     * @param selectorFailureBehavior see {@link SSL#SSL_SELECTOR_FAILURE_NO_ADVERTISE}
     *                                and {@link SSL#SSL_SELECTOR_FAILURE_CHOOSE_MY_LAST_PROTOCOL}
     */
    public static native void setAlpnProtos(long ctx, String[] alpnProtos, int selectorFailureBehavior);

    /**
     * 设置DH参数
     * 
     * @param ctx 要使用的服务器上下文.
     * @param cert DH param文件 (can be generated from e.g. {@code openssl dhparam -rand - 2048 > dhparam.pem} -
     *             see the <a href="https://www.openssl.org/docs/apps/dhparam.html">OpenSSL documentation</a>).
     *             
     * @throws Exception 发生错误
     */
    public static native void setTmpDH(long ctx, String cert)
            throws Exception;

    /**
     * 按名称设置ECDH椭圆曲线
     * 
     * @param ctx 要使用的服务器上下文.
     * @param curveName 要使用的椭圆曲线的名称 (可以从 {@code openssl ecparam -list_curves} 中获取可用的名称).
     *             
     * @throws Exception An error occurred
     */
    public static native void setTmpECDHByCurveName(long ctx, String curveName)
            throws Exception;

    /**
     * 设置重用会话的上下文（仅限服务器端）
     *
     * @param ctx 要使用的服务器上下文.
     * @param sidCtx 可以是任何类型的二进制数据, 因此可以使用例如 应用程序的名称、主机名、服务名称
     *               
     * @return {@code true} 成功, {@code false} 失败.
     */
    public static native boolean setSessionIdContext(long ctx, byte[] sidCtx);

    /**
     * 设置 CertificateRaw
     * <br>
     * 使用密钥库证书和密钥来填充BIOP
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param cert 带有DER编码证书的字节数组.
     * @param key 具有PEM格式的私钥文件的字节数组.
     * @param sslAidxRsa 证书索引SSL_AIDX_RSA或SSL_AIDX_DSA.
     * 
     * @return {@code true} 成功, {@code false} 失败.
     */
    public static native boolean setCertificateRaw(long ctx, byte[] cert, byte[] key, int sslAidxRsa);

    /**
     * 将证书添加到证书链. 应该从主机证书的颁发者开始按顺序添加Certs, 并将证书链处理到CA.
     *
     * <br>
     * 使用密钥库证书链来填充BIOP
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param cert 带有DER编码证书的字节数组.
     * 
     * @return {@code true} 成功, {@code false} 失败.
     */
    public static native boolean addChainCertificateRaw(long ctx, byte[] cert);

    /**
     * 添加接受的CA证书作为对等证书的颁发者
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param cert 带有DER编码证书的字节数组.
     * 
     * @return {@code true} 成功, {@code false} 失败.
     */
    public static native boolean addClientCACertificateRaw(long ctx, byte[] cert);
}
