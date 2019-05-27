package org.apache.tomcat.jni;

/** SSL
 */
public final class SSL {

    /*
     * 主要来自mod_ssl的类型定义
     */
    public static final int UNSET            = -1;
    /*
     * 定义证书算法类型
     */
    public static final int SSL_ALGO_UNKNOWN = 0;
    public static final int SSL_ALGO_RSA     = (1<<0);
    public static final int SSL_ALGO_DSA     = (1<<1);
    public static final int SSL_ALGO_ALL     = (SSL_ALGO_RSA|SSL_ALGO_DSA);

    public static final int SSL_AIDX_RSA     = 0;
    public static final int SSL_AIDX_DSA     = 1;
    public static final int SSL_AIDX_ECC     = 3;
    public static final int SSL_AIDX_MAX     = 4;
    /*
     * 定义临时RSA密钥和DH参数的ID
     */

    public static final int SSL_TMP_KEY_RSA_512  = 0;
    public static final int SSL_TMP_KEY_RSA_1024 = 1;
    public static final int SSL_TMP_KEY_RSA_2048 = 2;
    public static final int SSL_TMP_KEY_RSA_4096 = 3;
    public static final int SSL_TMP_KEY_DH_512   = 4;
    public static final int SSL_TMP_KEY_DH_1024  = 5;
    public static final int SSL_TMP_KEY_DH_2048  = 6;
    public static final int SSL_TMP_KEY_DH_4096  = 7;
    public static final int SSL_TMP_KEY_MAX      = 8;

    /*
     * 定义SSL选项
     */
    public static final int SSL_OPT_NONE           = 0;
    public static final int SSL_OPT_RELSET         = (1<<0);
    public static final int SSL_OPT_STDENVVARS     = (1<<1);
    public static final int SSL_OPT_EXPORTCERTDATA = (1<<3);
    public static final int SSL_OPT_FAKEBASICAUTH  = (1<<4);
    public static final int SSL_OPT_STRICTREQUIRE  = (1<<5);
    public static final int SSL_OPT_OPTRENEGOTIATE = (1<<6);
    public static final int SSL_OPT_ALL            = (SSL_OPT_STDENVVARS|SSL_OPT_EXPORTCERTDATA|SSL_OPT_FAKEBASICAUTH|SSL_OPT_STRICTREQUIRE|SSL_OPT_OPTRENEGOTIATE);

    /*
     * 定义SSL协议选项
     */
    public static final int SSL_PROTOCOL_NONE  = 0;
    public static final int SSL_PROTOCOL_SSLV2 = (1<<0);
    public static final int SSL_PROTOCOL_SSLV3 = (1<<1);
    public static final int SSL_PROTOCOL_TLSV1 = (1<<2);
    public static final int SSL_PROTOCOL_TLSV1_1 = (1<<3);
    public static final int SSL_PROTOCOL_TLSV1_2 = (1<<4);
    public static final int SSL_PROTOCOL_ALL   = (SSL_PROTOCOL_TLSV1 | SSL_PROTOCOL_TLSV1_1 | SSL_PROTOCOL_TLSV1_2);

    /*
     * 定义SSL验证级别
     */
    public static final int SSL_CVERIFY_UNSET          = UNSET;
    public static final int SSL_CVERIFY_NONE           = 0;
    public static final int SSL_CVERIFY_OPTIONAL       = 1;
    public static final int SSL_CVERIFY_REQUIRE        = 2;
    public static final int SSL_CVERIFY_OPTIONAL_NO_CA = 3;

    /* 
     * 使用SSL_VERIFY_NONE或SSL_VERIFY_PEER, 如果需要，最后2个选项与SSL_VERIFY_PEER一起“存储”
     */
    public static final int SSL_VERIFY_NONE                 = 0;
    public static final int SSL_VERIFY_PEER                 = 1;
    public static final int SSL_VERIFY_FAIL_IF_NO_PEER_CERT = 2;
    public static final int SSL_VERIFY_CLIENT_ONCE          = 4;
    public static final int SSL_VERIFY_PEER_STRICT          = (SSL_VERIFY_PEER|SSL_VERIFY_FAIL_IF_NO_PEER_CERT);

    public static final int SSL_OP_MICROSOFT_SESS_ID_BUG            = 0x00000001;
    public static final int SSL_OP_NETSCAPE_CHALLENGE_BUG           = 0x00000002;
    public static final int SSL_OP_NETSCAPE_REUSE_CIPHER_CHANGE_BUG = 0x00000008;
    public static final int SSL_OP_SSLREF2_REUSE_CERT_TYPE_BUG      = 0x00000010;
    public static final int SSL_OP_MICROSOFT_BIG_SSLV3_BUFFER       = 0x00000020;
    public static final int SSL_OP_MSIE_SSLV2_RSA_PADDING           = 0x00000040;
    public static final int SSL_OP_SSLEAY_080_CLIENT_DH_BUG         = 0x00000080;
    public static final int SSL_OP_TLS_D5_BUG                       = 0x00000100;
    public static final int SSL_OP_TLS_BLOCK_PADDING_BUG            = 0x00000200;

    /* 
     * 禁用OpenSSL 0.9.6d中添加的SSL 3.0 / TLS 1.0 CBC漏洞解决方法.
     * 通常（取决于应用程序协议）不需要变通方法. 不幸的是，一些损坏的SSL / TLS实现根本无法处理它，这就是我们将它包含在SSL_OP_ALL中的原因.
     */
    public static final int SSL_OP_DONT_INSERT_EMPTY_FRAGMENTS      = 0x00000800;

    /* SSL_OP_ALL: 各种bug变通办法应该是相当无害的. 这在0.9.7之前曾经是0x000FFFFFL. */
    public static final int SSL_OP_ALL                              = 0x00000FFF;
    /* 作为服务器，禁止在重新协商时恢复会话 */
    public static final int SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION = 0x00010000;
    /* 即使支持，也不要使用压缩 */
    public static final int SSL_OP_NO_COMPRESSION                         = 0x00020000;
    /* 允许不安全的遗留重新协商 */
    public static final int SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION      = 0x00040000;
    /* 如果设置，则在使用tmp_eddh参数时始终创建新密钥 */
    public static final int SSL_OP_SINGLE_ECDH_USE                  = 0x00080000;
    /* 如果设置，则在使用tmp_dh参数时始终创建新密钥 */
    public static final int SSL_OP_SINGLE_DH_USE                    = 0x00100000;
    /* 
     * 设置为在执行RSA操作时始终使用tmp_rsa键, 即使这违反了协议规范 */
    public static final int SSL_OP_EPHEMERAL_RSA                    = 0x00200000;
    /* 设置在服务器上，根据服务器的首选项选择密码 */
    public static final int SSL_OP_CIPHER_SERVER_PREFERENCE         = 0x00400000;
    /* 
     * 如果设置，服务器将允许客户端发出SSLv3.0版本号作为premaster secret中支持的最新版本,
     * 即使在客户端中声明是TLSv1.0（版本3.1）. 通常这是禁止的，以防止版本回滚攻击.
     */
    public static final int SSL_OP_TLS_ROLLBACK_BUG                 = 0x00800000;

    public static final int SSL_OP_NO_SSLv2                         = 0x01000000;
    public static final int SSL_OP_NO_SSLv3                         = 0x02000000;
    public static final int SSL_OP_NO_TLSv1                         = 0x04000000;
    public static final int SSL_OP_NO_TLSv1_2                       = 0x08000000;
    public static final int SSL_OP_NO_TLSv1_1                       = 0x10000000;

    public static final int SSL_OP_NO_TICKET                        = 0x00004000;

    // 当前版本的OpenSSL库不支持SSL_OP_PKCS1_CHECK_1和SSL_OP_PKCS1_CHECK_2标志.
    // See ssl.h changes in commit 7409d7ad517650db332ae528915a570e4e0ab88b (30 Apr 2011) of OpenSSL.
    /**
     * @deprecated Unsupported in the current version of OpenSSL
     */
    @Deprecated
    public static final int SSL_OP_PKCS1_CHECK_1                    = 0x08000000;
    /**
     * @deprecated Unsupported in the current version of OpenSSL
     */
    @Deprecated
    public static final int SSL_OP_PKCS1_CHECK_2                    = 0x10000000;
    public static final int SSL_OP_NETSCAPE_CA_DN_BUG               = 0x20000000;
    public static final int SSL_OP_NETSCAPE_DEMO_CIPHER_CHANGE_BUG  = 0x40000000;

    public static final int SSL_CRT_FORMAT_UNDEF    = 0;
    public static final int SSL_CRT_FORMAT_ASN1     = 1;
    public static final int SSL_CRT_FORMAT_TEXT     = 2;
    public static final int SSL_CRT_FORMAT_PEM      = 3;
    public static final int SSL_CRT_FORMAT_NETSCAPE = 4;
    public static final int SSL_CRT_FORMAT_PKCS12   = 5;
    public static final int SSL_CRT_FORMAT_SMIME    = 6;
    public static final int SSL_CRT_FORMAT_ENGINE   = 7;

    public static final int SSL_MODE_CLIENT         = 0;
    public static final int SSL_MODE_SERVER         = 1;
    public static final int SSL_MODE_COMBINED       = 2;

    public static final int SSL_CONF_FLAG_CMDLINE       = 0x0001;
    public static final int SSL_CONF_FLAG_FILE          = 0x0002;
    public static final int SSL_CONF_FLAG_CLIENT        = 0x0004;
    public static final int SSL_CONF_FLAG_SERVER        = 0x0008;
    public static final int SSL_CONF_FLAG_SHOW_ERRORS   = 0x0010;
    public static final int SSL_CONF_FLAG_CERTIFICATE   = 0x0020;

    public static final int SSL_CONF_TYPE_UNKNOWN   = 0x0000;
    public static final int SSL_CONF_TYPE_STRING    = 0x0001;
    public static final int SSL_CONF_TYPE_FILE      = 0x0002;
    public static final int SSL_CONF_TYPE_DIR       = 0x0003;

    public static final int SSL_SHUTDOWN_TYPE_UNSET    = 0;
    public static final int SSL_SHUTDOWN_TYPE_STANDARD = 1;
    public static final int SSL_SHUTDOWN_TYPE_UNCLEAN  = 2;
    public static final int SSL_SHUTDOWN_TYPE_ACCURATE = 3;

    public static final int SSL_INFO_SESSION_ID                = 0x0001;
    public static final int SSL_INFO_CIPHER                    = 0x0002;
    public static final int SSL_INFO_CIPHER_USEKEYSIZE         = 0x0003;
    public static final int SSL_INFO_CIPHER_ALGKEYSIZE         = 0x0004;
    public static final int SSL_INFO_CIPHER_VERSION            = 0x0005;
    public static final int SSL_INFO_CIPHER_DESCRIPTION        = 0x0006;
    public static final int SSL_INFO_PROTOCOL                  = 0x0007;

    /* 
     * 要获取客户端证书颁发者的CountryName, 请使用SSL_INFO_CLIENT_I_DN + SSL_INFO_DN_COUNTRYNAME
     */
    public static final int SSL_INFO_CLIENT_S_DN               = 0x0010;
    public static final int SSL_INFO_CLIENT_I_DN               = 0x0020;
    public static final int SSL_INFO_SERVER_S_DN               = 0x0040;
    public static final int SSL_INFO_SERVER_I_DN               = 0x0080;

    public static final int SSL_INFO_DN_COUNTRYNAME            = 0x0001;
    public static final int SSL_INFO_DN_STATEORPROVINCENAME    = 0x0002;
    public static final int SSL_INFO_DN_LOCALITYNAME           = 0x0003;
    public static final int SSL_INFO_DN_ORGANIZATIONNAME       = 0x0004;
    public static final int SSL_INFO_DN_ORGANIZATIONALUNITNAME = 0x0005;
    public static final int SSL_INFO_DN_COMMONNAME             = 0x0006;
    public static final int SSL_INFO_DN_TITLE                  = 0x0007;
    public static final int SSL_INFO_DN_INITIALS               = 0x0008;
    public static final int SSL_INFO_DN_GIVENNAME              = 0x0009;
    public static final int SSL_INFO_DN_SURNAME                = 0x000A;
    public static final int SSL_INFO_DN_DESCRIPTION            = 0x000B;
    public static final int SSL_INFO_DN_UNIQUEIDENTIFIER       = 0x000C;
    public static final int SSL_INFO_DN_EMAILADDRESS           = 0x000D;

    public static final int SSL_INFO_CLIENT_M_VERSION          = 0x0101;
    public static final int SSL_INFO_CLIENT_M_SERIAL           = 0x0102;
    public static final int SSL_INFO_CLIENT_V_START            = 0x0103;
    public static final int SSL_INFO_CLIENT_V_END              = 0x0104;
    public static final int SSL_INFO_CLIENT_A_SIG              = 0x0105;
    public static final int SSL_INFO_CLIENT_A_KEY              = 0x0106;
    public static final int SSL_INFO_CLIENT_CERT               = 0x0107;
    public static final int SSL_INFO_CLIENT_V_REMAIN           = 0x0108;

    public static final int SSL_INFO_SERVER_M_VERSION          = 0x0201;
    public static final int SSL_INFO_SERVER_M_SERIAL           = 0x0202;
    public static final int SSL_INFO_SERVER_V_START            = 0x0203;
    public static final int SSL_INFO_SERVER_V_END              = 0x0204;
    public static final int SSL_INFO_SERVER_A_SIG              = 0x0205;
    public static final int SSL_INFO_SERVER_A_KEY              = 0x0206;
    public static final int SSL_INFO_SERVER_CERT               = 0x0207;
    /* 
     * 返回客户端证书链.
     * 将证书链编号添加到该标志 (0 ... 验证深度)
     */
    public static final int SSL_INFO_CLIENT_CERT_CHAIN         = 0x0400;

    /* 目前仅支持OFF和SERVER */
    public static final long SSL_SESS_CACHE_OFF = 0x0000;
    public static final long SSL_SESS_CACHE_SERVER = 0x0002;

    public static final int SSL_SELECTOR_FAILURE_NO_ADVERTISE = 0;
    public static final int SSL_SELECTOR_FAILURE_CHOOSE_MY_LAST_PROTOCOL = 1;

    /* 返回OpenSSL版本号 (编译时版本, 如果版本号 < 1.1.0) */
    public static native int version();

    /* 返回OpenSSL版本字符串 (运行时版本) */
    public static native String versionString();

    /**
     * 初始化OpenSSL支持.
     * 需要在JVM的生命周期内调用此函数一次. 必须在之前调用 Library.init().
     * 
     * @param engine 支持外部加密设备 ("engine"), 通常是用于加密操作的硬件加速卡.
     * 
     * @return APR 状态码
     */
    public static native int initialize(String engine);

    /**
     * 获取FIPS模式的状态.
     *
     * @return FIPS_mode 返回码. <code>0</code> 如果OpenSSL未处于FIPS模式, <code>1</code> 如果OpenSSL处于FIPS模式.
     * @throws Exception 如果没有使用FIPS模式编译 tcnative.
     */
    public static native int fipsModeGet() throws Exception;

    /**
     * 启用/禁用FIPS模式.
     *
     * @param mode 1 - 启用, 0 - 禁用
     *
     * @return FIPS_mode_set 返回码
     * @throws Exception 如果没有使用FIPS模式编译tcnative, 或者如果{@code FIPS_mode_set()}调用返回了错误值.
     */
    public static native int fipsModeSet(int mode) throws Exception;

    /**
     * 将文件内容添加到PRNG
     * 
     * @param filename 包含随机数据的文件名. 如果为null, 则将测试默认文件.
     *        如果设置了该环境变量，则种子文件为$RANDFILE, 否则是 $HOME/.rnd.
     *        如果两个文件都不可用，则使用内置随机种子生成器.
     *        
     * @return <code>true</code>操作成功
     */
    public static native boolean randLoad(String filename);

    /**
     * 将多个随机字节（当前为1024）写入文件<code>filename</code>, 可以通过在以后的会话中调用randLoad来初始化PRNG.
     * 
     * @param filename 用于保存数据的文件名
     * 
     * @return <code>true</code>操作成功
     */
    public static native boolean randSave(String filename);

    /**
     * 创建随机数据到文件名
     * 
     * @param filename 用于保存数据的文件名
     * @param len 随机序列的长度，以字节为单位
     * @param base64 以Base64编码格式输出数据
     * 
     * @return <code>true</code> 操作成功
     */
    public static native boolean randMake(String filename, int len,
                                          boolean base64);

    /**
     * 设置全局随机文件名.
     * 
     * @param filename 要使用的文件名. 如果设置，它将用于SSL初始化以及明确未设置的所有上下文.
     */
    public static native void randSet(String filename);

    /**
     * 初始化新的BIO
     * 
     * @param pool 要使用的池.
     * @param callback 要使用的BIOCallback
     * 
     * @return 新的 BIO 句柄
     * @throws Exception 发生错误
     */
     public static native long newBIO(long pool, BIOCallback callback)
            throws Exception;

    /**
     * 关闭BIO并取消引用回调对象
     * 
     * @param bio 要关闭和销毁的BIO.
     * 
     * @return APR 状态码
     */
     public static native int closeBIO(long bio);

    /**
     * 设置全局密码回调以获取密码.
     * 
     * @param callback 要使用的PasswordCallback 实现.
     */
     public static native void setPasswordCallback(PasswordCallback callback);

    /**
     * 设置全局密码以解密证书和密钥.
     * 
     * @param password 要使用的密码.
     */
     public static native void setPassword(String password);

    /**
     * 返回上一个SSL错误字符串
     * 
     * @return 错误字符串
     */
    public static native String getLastError();

    /**
     * 如果OpenSSL支持所有请求的SSL_OP_ *, 则返回true.
     *
     * <i>请注意，对于tcnative版本 &lt; 1.1.25, 这个方法返回 <code>true</code>,
     * 当且仅当 <code>op</code>= {@link #SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION} 和tcnative支持该标志.</i>
     *
     * @param op 要测试的所有SSL_OP_* 的按位或.
     *
     * @return true 如果OpenSSL库支持所有SSL_OP_*.
     */
    public static native boolean hasOp(int op);

    /**
     * 返回握手完成的数量.
     * 
     * @param ssl SSL 指针
     * 
     * @return 数量
     */
    public static native int getHandshakeCount(long ssl);

    /*
     * 开始添加Twitter API
     */

    public static final int SSL_SENT_SHUTDOWN = 1;
    public static final int SSL_RECEIVED_SHUTDOWN = 2;

    public static final int SSL_ERROR_NONE             = 0;
    public static final int SSL_ERROR_SSL              = 1;
    public static final int SSL_ERROR_WANT_READ        = 2;
    public static final int SSL_ERROR_WANT_WRITE       = 3;
    public static final int SSL_ERROR_WANT_X509_LOOKUP = 4;
    public static final int SSL_ERROR_SYSCALL          = 5; /* look at error stack/return value/errno */
    public static final int SSL_ERROR_ZERO_RETURN      = 6;
    public static final int SSL_ERROR_WANT_CONNECT     = 7;
    public static final int SSL_ERROR_WANT_ACCEPT      = 8;

    /**
     * SSL_new
     * 
     * @param ctx 要使用的服务器或客户端上下文.
     * @param server true : 配置SSL实例以使用接受握手常规
     *               false : 配置SSL实例以使用连接握手常规
     *               
     * @return 指向SSL实例的指针 (SSL *)
     */
    public static native long newSSL(long ctx, boolean server);

    /**
     * SSL_set_bio
     * 
     * @param ssl SSL指针 (SSL *)
     * @param rbio 读取BIO指针 (BIO *)
     * @param wbio 写入BIO指针 (BIO *)
     */
    public static native void setBIO(long ssl, long rbio, long wbio);

    /**
     * SSL_get_error
     * 
     * @param ssl SSL指针 (SSL *)
     * @param ret TLS/SSL I/O 返回值
     * @return 错误状态
     */
    public static native int getError(long ssl, int ret);

    /**
     * BIO_ctrl_pending.
     * 
     * @param bio BIO指针 (BIO *)
     * 
     * @return 挂起的字节数
     */
    public static native int pendingWrittenBytesInBIO(long bio);

    /**
     * SSL_pending.
     * 
     * @param ssl SSL指针 (SSL *)
     * @return 挂起的字节数
     */
    public static native int pendingReadableBytesInSSL(long ssl);

    /**
     * BIO_write.
     * 
     * @param bio BIO指针
     * @param wbuf 缓冲区指针
     * @param wlen 写入长度
     * 
     * @return 写入的字节数
     */
    public static native int writeToBIO(long bio, long wbuf, int wlen);

    /**
     * BIO_read.
     * 
     * @param bio BIO指针
     * @param rbuf 缓冲区指针
     * @param rlen 读取长度
     * 
     * @return 读取的字节数
     */
    public static native int readFromBIO(long bio, long rbuf, int rlen);

    /**
     * SSL_write.
     * 
     * @param ssl SSL实例 (SSL *)
     * @param wbuf 缓冲区指针
     * @param wlen 写入长度
     * 
     * @return 写入的字节数
     */
    public static native int writeToSSL(long ssl, long wbuf, int wlen);

    /**
     * SSL_read
     * 
     * @param ssl SSL实例 (SSL *)
     * @param rbuf 缓冲区指针
     * @param rlen 读取长度
     * 
     * @return 读取的字节数
     */
    public static native int readFromSSL(long ssl, long rbuf, int rlen);

    /**
     * SSL_get_shutdown
     * 
     * @param ssl SSL实例 (SSL *)
     * 
     * @return 操作状态
     */
    public static native int getShutdown(long ssl);

    /**
     * SSL_set_shutdown
     * 
     * @param ssl SSL实例  (SSL *)
     * @param mode 关闭模式
     */
    public static native void setShutdown(long ssl, int mode);

    /**
     * SSL_free
     * 
     * @param ssl SSL实例 (SSL *)
     */
    public static native void freeSSL(long ssl);

    /**
     * 为给定的SSL实例连接内部和网络BIO.
     *
     * <b>Warning: 必须通过调用 freeBIO 显式的释放此资源</b>
     *
     * 在提供的SSL实例上调用freeSSL时, 将释放SSL的内部/应用程序数据BIO, 必须在返回的网络BIO上调用freeBIO.
     *
     * @param ssl SSL实例 (SSL *)
     * @return 指向网络BIO的指针 (BIO *)
     */
    public static native long makeNetworkBIO(long ssl);

    /**
     * BIO_free
     * 
     * @param bio BIO指针
     */
    public static native void freeBIO(long bio);

    /**
     * SSL_shutdown
     * 
     * @param ssl SSL实例 (SSL *)
     * 
     * @return 操作状态
     */
    public static native int shutdownSSL(long ssl);

    /**
     * 获取表示OpenSSL在此线程上遇到的最后一个错误的错误号.
     * 
     * @return 最后一个错误号
     */
    public static native int getLastErrorNumber();

    /**
     * SSL_get_cipher.
     * 
     * @param ssl SSL实例 (SSL *)
     * 
     * @return 密码名称
     */
    public static native String getCipherForSSL(long ssl);

    /**
     * SSL_get_version
     * 
     * @param ssl SSL实例 (SSL *)
     * 
     * @return 正在使用的SSL版本
     */
    public static native String getVersion(long ssl);

    /**
     * SSL_do_handshake
     * 
     * @param ssl SSL实例 (SSL *)
     * 
     * @return 握手状态
     */
    public static native int doHandshake(long ssl);

    /**
     * SSL_renegotiate
     * 
     * @param ssl SSL实例 (SSL *)
     * 
     * @return 操作状态
     */
    public static native int renegotiate(long ssl);

    /**
     * SSL_in_init.
     * 
     * @param ssl SSL实例 (SSL *)
     * 
     * @return 状态
     */
    public static native int isInInit(long ssl);

    /**
     * SSL_get0_next_proto_negotiated
     * 
     * @param ssl SSL实例 (SSL *)
     * 
     * @return NPN协议协商
     */
    public static native String getNextProtoNegotiated(long ssl);

    /*
     * End Twitter API Additions
     */

    /**
     * SSL_get0_alpn_selected
     * 
     * @param ssl SSL实例 (SSL *)
     * 
     * @return ALPN协议进行了协商
     */
    public static native String getAlpnSelected(long ssl);

    /**
     * 如果未发送，则获取对等证书链或{@code null}.
     * 
     * @param ssl SSL实例 (SSL *)
     * 
     * @return 证书链字节
     */
    public static native byte[][] getPeerCertChain(long ssl);

    /**
     * 如果未发送，则获取对等证书或{@code null}.
     * 
     * @param ssl SSL实例 (SSL *)
     * 
     * @return 证书字节
     */
    public static native byte[] getPeerCertificate(long ssl);

    /**
     * 获取代表给定{@code errorNumber}的错误号.
     * 
     * @param errorNumber 错误码
     * 
     * @return 错误消息
     */
    public static native String getErrorString(long errorNumber);

    /**
     * SSL_get_time
     * 
     * @param ssl SSL实例 (SSL *)
     * 
     * @return 会话ssl建立的时间. 自纪元以来，时间以秒为单位
     */
    public static native long getTime(long ssl);

    /**
     * 在客户端证书验证中, 设置客户端证书验证类型和CA证书的最大深度.
     * <br>
     * 该指令设置客户端身份验证的证书验证级别. 请注意，此指令可以在每个服务器和每个目录的上下文中使用.
     * 在每个服务器上下文中，它应用于建立连接时在标准SSL握手中使用的客户端身份验证过程.
     * 在每个目录上下文中, 它会在读取HTTP请求之后但在发送HTTP响应之前, 强制使用重新配置的客户端验证级别重新进行SSL协商.
     * <br>
     * 以下级别可用:
     * <pre>
     * SSL_CVERIFY_NONE           - 根本不需要客户端证书
     * SSL_CVERIFY_OPTIONAL       - 客户端可以出示有效证书
     * SSL_CVERIFY_REQUIRE        - 客户端必须出示有效证书
     * SSL_CVERIFY_OPTIONAL_NO_CA - 客户端可以提供有效证书, 但不需要（成功）验证
     * </pre>
     * <br>
     * 深度实际上是中间证书颁发者的最大数量, 即在验证客户端证书时允许遵循的最大CA证书的数量.
     * 深度为0表示仅接受自签名客户端证书; 默认深度为1表示客户端证书可以是自签名的, 或者必须由服务器直接知道的CA签名 (即CA的证书在{@code setCACertificatePath}下).
     *
     * @param ssl SSL实例 (SSL *)
     * @param level 客户端证书验证类型.
     * @param depth 客户端证书验证中CA证书的最大深度.
     */
    public static native void setVerify(long ssl, int level, int depth);

    /**
     * 设置OpenSSL选项.
     * 
     * @param ssl SSL实例 (SSL *)
     * @param options  See SSL.SSL_OP_* for option flags.
     */
    public static native void setOptions(long ssl, int options);

    /**
     * 获取OpenSSL选项.
     * 
     * @param ssl SSL实例 (SSL *)
     * 
     * @return options  See SSL.SSL_OP_* for option flags.
     */
    public static native int getOptions(long ssl);

    /**
     * 返回在SSL握手中启用协商的所有密码套件.
     * 
     * @param ssl SSL实例 (SSL *)
     * 
     * @return ciphers
     */
    public static native String[] getCiphers(long ssl);

    /**
     * 返回SSL握手中可用于协商的密码套件.
     * <br>
     * 此复杂指令使用冒号分隔的密码规范字符串，该字符串由OpenSSL密码规范组成，以配置允许客户端在SSL握手阶段协商的密码套件.
     * 请注意，此指令可以在每个服务器和每个目录的上下文中使用. 在每个服务器上下文中，它在建立连接时应用于标准SSL握手.
     * 在每个目录上下文中, 它会在读取HTTP请求之后但在发送HTTP响应之前, 强制使用重新配置的密码套件重新进行SSL协商.
     * 
     * @param ssl SSL实例 (SSL *)
     * @param ciphers SSL密码规范
     * 
     * @return <code>true</code> 操作成功
     * @throws Exception 发生错误
     */
    public static native boolean setCipherSuites(long ssl, String ciphers)
            throws Exception;

    /**
     * 以字节数组表示形式, 返回会话的ID.
     *
     * @param ssl SSL实例  (SSL *)
     * @return 会话作为通过SSL_SESSION_get_id获得的字节数组表示.
     */
    public static native byte[] getSessionId(long ssl);
}
