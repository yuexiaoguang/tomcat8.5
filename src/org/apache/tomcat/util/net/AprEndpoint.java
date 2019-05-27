package org.apache.tomcat.util.net;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.Address;
import org.apache.tomcat.jni.Error;
import org.apache.tomcat.jni.File;
import org.apache.tomcat.jni.Library;
import org.apache.tomcat.jni.OS;
import org.apache.tomcat.jni.Poll;
import org.apache.tomcat.jni.Pool;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLConf;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.jni.SSLContext.SNICallBack;
import org.apache.tomcat.jni.SSLSocket;
import org.apache.tomcat.jni.Sockaddr;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteBufferUtils;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.net.AbstractEndpoint.Acceptor.AcceptorState;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLHostConfig.Type;
import org.apache.tomcat.util.net.openssl.OpenSSLConf;
import org.apache.tomcat.util.net.openssl.OpenSSLEngine;


/**
 * APR定制的线程池, 提供以下服务:
 * <ul>
 * <li>套接字接受器线程</li>
 * <li>套接字轮询线程</li>
 * <li>Sendfile线程</li>
 * <li>工作者线程池</li>
 * </ul>
 *
 * 切换到Java 5时, 有机会使用虚拟机的线程池.
 */
public class AprEndpoint extends AbstractEndpoint<Long> implements SNICallBack {

    // -------------------------------------------------------------- Constants

    private static final Log log = LogFactory.getLog(AprEndpoint.class);

    // ----------------------------------------------------------------- Fields

    /**
     * 根APR内存池.
     */
    protected long rootPool = 0;


    /**
     * 服务器套接字"pointer".
     */
    protected long serverSock = 0;


    /**
     * 服务器套接字的APR内存池.
     */
    protected long serverSockPool = 0;


    /**
     * SSL context.
     */
    protected long sslContext = 0;


    private final Map<Long,AprSocketWrapper> connections = new ConcurrentHashMap<>();


    // ------------------------------------------------------------ Constructor

    public AprEndpoint() {
        // 需要覆盖maxConnections的默认值以使其与pollerSize对齐 (在两个合并之前)
        setMaxConnections(8 * 1024);
    }

    // ------------------------------------------------------------- Properties


    /**
     * 推迟接受.
     */
    protected boolean deferAccept = true;
    public void setDeferAccept(boolean deferAccept) { this.deferAccept = deferAccept; }
    @Override
    public boolean getDeferAccept() { return deferAccept; }


    private boolean ipv6v6only = false;
    public void setIpv6v6only(boolean ipv6v6only) { this.ipv6v6only = ipv6v6only; }
    public boolean getIpv6v6only() { return ipv6v6only; }


    /**
     * sendfile的大小 (= 可以提供的并发文件).
     */
    protected int sendfileSize = 1 * 1024;
    public void setSendfileSize(int sendfileSize) { this.sendfileSize = sendfileSize; }
    public int getSendfileSize() { return sendfileSize; }


    /**
     * 轮询间隔, 以微秒为单位. 值越小, 轮询器将使用的CPU越多, 但它对活动的反应会更快.
     */
    protected int pollTime = 2000;
    public int getPollTime() { return pollTime; }
    public void setPollTime(int pollTime) { if (pollTime > 0) { this.pollTime = pollTime; } }


    /*
     * 创建和配置端点时, APR库尚未初始化.
     * 此标志用于确定如果APR库指示它在初始化后支持发送文件，是否应更改useSendFile的默认值.
     * 如果useSendFile由配置设置, 该配置将始终优先.
     */
    private boolean useSendFileSet = false;
    @Override
    public void setUseSendfile(boolean useSendfile) {
        useSendFileSet = true;
        super.setUseSendfile(useSendfile);
    }
    /*
     * 供内部使用，以避免设置useSendFileSet标志
     */
    private void setUseSendfileInternal(boolean useSendfile) {
        super.setUseSendfile(useSendfile);
    }


    /**
     * 套接字轮询器.
     */
    protected Poller poller = null;
    public Poller getPoller() {
        return poller;
    }


    /**
     * 静态文件发送器.
     */
    protected Sendfile sendfile = null;
    public Sendfile getSendfile() {
        return sendfile;
    }


    @Override
    protected Type getSslConfigType() {
        return SSLHostConfig.Type.OPENSSL;
    }


    @Override
    public InetSocketAddress getLocalAddress() throws IOException {
        long s = serverSock;
        if (s == 0) {
            return null;
        } else {
            long sa;
            try {
                sa = Address.get(Socket.APR_LOCAL, s);
            } catch (IOException ioe) {
                // re-throw
                throw ioe;
            } catch (Exception e) {
                // wrap
                throw new IOException(e);
            }
            Sockaddr addr = Address.getInfo(sa);
            if (addr.hostname == null) {
                // any local address
                if (addr.family == Socket.APR_INET6) {
                    return new InetSocketAddress("::", addr.port);
                } else {
                    return new InetSocketAddress("0.0.0.0", addr.port);
                }
            }
            return new InetSocketAddress(addr.hostname, addr.port);
        }
    }


    /**
     * 此端点不支持<code>-1</code>以进行无限制连接, 也不支持在端点运行时设置此属性.
     *
     * {@inheritDoc}
     */
    @Override
    public void setMaxConnections(int maxConnections) {
        if (maxConnections == -1) {
            log.warn(sm.getString("endpoint.apr.maxConnections.unlimited",
                    Integer.valueOf(getMaxConnections())));
            return;
        }
        if (running) {
            log.warn(sm.getString("endpoint.apr.maxConnections.running",
                    Integer.valueOf(getMaxConnections())));
            return;
        }
        super.setMaxConnections(maxConnections);
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 获取保持活动的套接字的数量.
     *
     * @return Poller当前管理的打开套接字的数量
     */
    public int getKeepAliveCount() {
        if (poller == null) {
            return 0;
        }

        return poller.getConnectionCount();
    }


    /**
     * 获取sendfile套接字的数量.
     *
     * @return Sendfile轮询器当前管理的套接字数.
     */
    public int getSendfileCount() {
        if (sendfile == null) {
            return 0;
        }

        return sendfile.getSendfileCount();
    }


    // ----------------------------------------------- Public Lifecycle Methods


    /**
     * 初始化端点.
     */
    @Override
    public void bind() throws Exception {

        // 创建根APR内存池
        try {
            rootPool = Pool.create(0);
        } catch (UnsatisfiedLinkError e) {
            throw new Exception(sm.getString("endpoint.init.notavail"));
        }

        // 为服务器套接字创建池
        serverSockPool = Pool.create(rootPool);
        // 创建将绑定的APR地址
        String addressStr = null;
        if (getAddress() != null) {
            addressStr = getAddress().getHostAddress();
        }
        int family = Socket.APR_INET;
        if (Library.APR_HAVE_IPV6) {
            if (addressStr == null) {
                if (!OS.IS_BSD) {
                    family = Socket.APR_UNSPEC;
                }
            } else if (addressStr.indexOf(':') >= 0) {
                family = Socket.APR_UNSPEC;
            }
         }

        long inetAddress = Address.info(addressStr, family,
                getPort(), 0, rootPool);
        // 创建APR服务器套接字
        serverSock = Socket.create(Address.getInfo(inetAddress).family,
                Socket.SOCK_STREAM,
                Socket.APR_PROTO_TCP, rootPool);
        if (OS.IS_UNIX) {
            Socket.optSet(serverSock, Socket.APR_SO_REUSEADDR, 1);
        }
        if (Library.APR_HAVE_IPV6) {
            if (getIpv6v6only()) {
                Socket.optSet(serverSock, Socket.APR_IPV6_V6ONLY, 1);
            } else {
                Socket.optSet(serverSock, Socket.APR_IPV6_V6ONLY, 0);
            }
        }
        // 处理往往会丢弃非活动套接字的防火墙
        Socket.optSet(serverSock, Socket.APR_SO_KEEPALIVE, 1);
        // 绑定服务器套接字
        int ret = Socket.bind(serverSock, inetAddress);
        if (ret != 0) {
            throw new Exception(sm.getString("endpoint.init.bind", "" + ret, Error.strerror(ret)));
        }
        // 开始监听服务器套接字
        ret = Socket.listen(serverSock, getAcceptCount());
        if (ret != 0) {
            throw new Exception(sm.getString("endpoint.init.listen", "" + ret, Error.strerror(ret)));
        }
        if (OS.IS_WIN32 || OS.IS_WIN64) {
            // 在Windows上，在bind/listen之后设置reuseaddr标志
            Socket.optSet(serverSock, Socket.APR_SO_REUSEADDR, 1);
        }

        // 默认情况下启用Sendfile（如果尚未配置），但在不支持它的系统上使用会导致严重问题
        if (!useSendFileSet) {
            setUseSendfileInternal(Library.APR_HAS_SENDFILE);
        } else if (getUseSendfile() && !Library.APR_HAS_SENDFILE) {
            setUseSendfileInternal(false);
        }

        // 初始化接受器的线程计数默认值
        if (acceptorThreadCount == 0) {
            // FIXME: 多个接受线程似乎没有那么好用
            acceptorThreadCount = 1;
        }

        // 在数据可用之前延迟接受新连接
        // 只有Linux内核2.4 +已实现
        // 在其他平台上, 此调用是noop, 并将返回APR_ENOTIMPL.
        if (deferAccept) {
            if (Socket.optSet(serverSock, Socket.APR_TCP_DEFER_ACCEPT, 1) == Status.APR_ENOTIMPL) {
                deferAccept = false;
            }
        }

        // 如果需要，初始化SSL
        if (isSSLEnabled()) {
            for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
                sslHostConfig.setConfigType(getSslConfigType());
                createSSLContext(sslHostConfig);
            }
            SSLHostConfig defaultSSLHostConfig = sslHostConfigs.get(getDefaultSSLHostConfigName());
            if (defaultSSLHostConfig == null) {
                throw new IllegalArgumentException(sm.getString("endpoint.noSslHostConfig",
                        getDefaultSSLHostConfigName(), getName()));
            }
            Long defaultSSLContext = defaultSSLHostConfig.getOpenSslContext();
            sslContext = defaultSSLContext.longValue();
            SSLContext.registerDefault(defaultSSLContext, this);
        }
    }



    @Override
    protected void createSSLContext(SSLHostConfig sslHostConfig) throws Exception {
        Set<SSLHostConfigCertificate> certificates = sslHostConfig.getCertificates(true);
        boolean firstCertificate = true;
        for (SSLHostConfigCertificate certificate : certificates) {
            if (SSLHostConfig.adjustRelativePath(certificate.getCertificateFile()) == null) {
                // This is required
                throw new Exception(sm.getString("endpoint.apr.noSslCertFile"));
            }
            if (firstCertificate) {
                // TODO: 在SSLUtilBase中复制代码. 考虑重构以减少重复
                firstCertificate = false;
                // 配置启用的协议
                List<String> enabledProtocols = SSLUtilBase.getEnabled("protocols", log,
                        true, sslHostConfig.getProtocols(),
                        OpenSSLEngine.IMPLEMENTED_PROTOCOLS_SET);
                sslHostConfig.setEnabledProtocols(
                        enabledProtocols.toArray(new String[enabledProtocols.size()]));
                // 配置启用的密码
                List<String> enabledCiphers = SSLUtilBase.getEnabled("ciphers", log,
                        false, sslHostConfig.getJsseCipherNames(),
                        OpenSSLEngine.AVAILABLE_CIPHER_SUITES);
                sslHostConfig.setEnabledCiphers(
                        enabledCiphers.toArray(new String[enabledCiphers.size()]));
            }
        }
        if (certificates.size() > 2) {
            // TODO: 可以删除此限制?
            throw new Exception(sm.getString("endpoint.apr.tooManyCertFiles"));
        }

        // SSL protocol
        int value = SSL.SSL_PROTOCOL_NONE;
        if (sslHostConfig.getProtocols().size() == 0) {
            // Native fallback used if protocols=""
            value = SSL.SSL_PROTOCOL_ALL;
        } else {
            for (String protocol : sslHostConfig.getEnabledProtocols()) {
                if (Constants.SSL_PROTO_SSLv2Hello.equalsIgnoreCase(protocol)) {
                    // NO-OP. OpenSSL始终支持SSLv2Hello
                } else if (Constants.SSL_PROTO_SSLv2.equalsIgnoreCase(protocol)) {
                    value |= SSL.SSL_PROTOCOL_SSLV2;
                } else if (Constants.SSL_PROTO_SSLv3.equalsIgnoreCase(protocol)) {
                    value |= SSL.SSL_PROTOCOL_SSLV3;
                } else if (Constants.SSL_PROTO_TLSv1.equalsIgnoreCase(protocol)) {
                    value |= SSL.SSL_PROTOCOL_TLSV1;
                } else if (Constants.SSL_PROTO_TLSv1_1.equalsIgnoreCase(protocol)) {
                    value |= SSL.SSL_PROTOCOL_TLSV1_1;
                } else if (Constants.SSL_PROTO_TLSv1_2.equalsIgnoreCase(protocol)) {
                    value |= SSL.SSL_PROTOCOL_TLSV1_2;
                } else {
                    // 不应该发生，因为构建启用的协议会删除无效值.
                    throw new Exception(sm.getString(
                            "endpoint.apr.invalidSslProtocol", protocol));
                }
            }
        }

        // Create SSL Context
        long ctx = 0;
        try {
            ctx = SSLContext.make(rootPool, value, SSL.SSL_MODE_SERVER);
        } catch (Exception e) {
            // 如果在AprLifecycleListener上禁用sslEngine，则会出现Exception，但无法从此处检查AprLifecycleListener设置
            throw new Exception(
                    sm.getString("endpoint.apr.failSslContextMake"), e);
        }

        if (sslHostConfig.getInsecureRenegotiation()) {
            SSLContext.setOptions(ctx, SSL.SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION);
        } else {
            SSLContext.clearOptions(ctx, SSL.SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION);
        }

        // 使用服务器的密码执行顺序 (而不是客户端的)
        String honorCipherOrderStr = sslHostConfig.getHonorCipherOrder();
        if (honorCipherOrderStr != null) {
            boolean honorCipherOrder = Boolean.valueOf(honorCipherOrderStr).booleanValue();
            if (honorCipherOrder) {
                SSLContext.setOptions(ctx, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
            } else {
                SSLContext.clearOptions(ctx, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
            }
        }

        // 如果请求，禁用压缩
        if (sslHostConfig.getDisableCompression()) {
            SSLContext.setOptions(ctx, SSL.SSL_OP_NO_COMPRESSION);
        } else {
            SSLContext.clearOptions(ctx, SSL.SSL_OP_NO_COMPRESSION);
        }

        // 禁用 TLS Session Tickets (RFC4507) 以保护完美的前向保密
        if (sslHostConfig.getDisableSessionTickets()) {
            SSLContext.setOptions(ctx, SSL.SSL_OP_NO_TICKET);
        } else {
            SSLContext.clearOptions(ctx, SSL.SSL_OP_NO_TICKET);
        }

        // 列出允许客户端协商的密码
        SSLContext.setCipherSuite(ctx, sslHostConfig.getCiphers());
        // 加载服务器密钥和证书
        // TODO: 确认idx不是特定于密钥/证书类型的
        int idx = 0;
        for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates(true)) {
            SSLContext.setCertificate(ctx,
                    SSLHostConfig.adjustRelativePath(certificate.getCertificateFile()),
                    SSLHostConfig.adjustRelativePath(certificate.getCertificateKeyFile()),
                    certificate.getCertificateKeyPassword(), idx++);
            // 设置证书链文件
            SSLContext.setCertificateChainFile(ctx,
                    SSLHostConfig.adjustRelativePath(certificate.getCertificateChainFile()), false);
        }
        // 支持客户端证书
        SSLContext.setCACertificate(ctx,
                SSLHostConfig.adjustRelativePath(sslHostConfig.getCaCertificateFile()),
                SSLHostConfig.adjustRelativePath(sslHostConfig.getCaCertificatePath()));
        // 设置撤销
        SSLContext.setCARevocation(ctx,
                SSLHostConfig.adjustRelativePath(
                        sslHostConfig.getCertificateRevocationListFile()),
                SSLHostConfig.adjustRelativePath(
                        sslHostConfig.getCertificateRevocationListPath()));
        // 客户端证书验证
        switch (sslHostConfig.getCertificateVerification()) {
        case NONE:
            value = SSL.SSL_CVERIFY_NONE;
            break;
        case OPTIONAL:
            value = SSL.SSL_CVERIFY_OPTIONAL;
            break;
        case OPTIONAL_NO_CA:
            value = SSL.SSL_CVERIFY_OPTIONAL_NO_CA;
            break;
        case REQUIRED:
            value = SSL.SSL_CVERIFY_REQUIRE;
            break;
        }
        SSLContext.setVerify(ctx, value, sslHostConfig.getCertificateVerificationDepth());
        // 目前，SSL不支持sendfile
        if (getUseSendfile()) {
            setUseSendfileInternal(false);
            if (useSendFileSet) {
                log.warn(sm.getString("endpoint.apr.noSendfileWithSSL"));
            }
        }

        if (negotiableProtocols.size() > 0) {
            ArrayList<String> protocols = new ArrayList<>();
            protocols.addAll(negotiableProtocols);
            protocols.add("http/1.1");
            String[] protocolsArray = protocols.toArray(new String[0]);
            SSLContext.setAlpnProtos(ctx, protocolsArray, SSL.SSL_SELECTOR_FAILURE_NO_ADVERTISE);
        }

        // 如果正在使用客户端身份验证, OpenSSL要求设置此项, 以便始终设置它, 以防应用程序配置为需要它
        SSLContext.setSessionIdContext(ctx, SSLContext.DEFAULT_SESSION_ID_CONTEXT);

        long cctx;
        OpenSSLConf openSslConf = sslHostConfig.getOpenSslConf();
        if (openSslConf != null) {
            // Create OpenSSLConfCmd context if used
            try {
                if (log.isDebugEnabled())
                    log.debug(sm.getString("endpoint.apr.makeConf"));
                cctx = SSLConf.make(rootPool,
                                    SSL.SSL_CONF_FLAG_FILE |
                                    SSL.SSL_CONF_FLAG_SERVER |
                                    SSL.SSL_CONF_FLAG_CERTIFICATE |
                                    SSL.SSL_CONF_FLAG_SHOW_ERRORS);
            } catch (Exception e) {
                throw new Exception(sm.getString("endpoint.apr.errMakeConf"), e);
            }
            if (cctx != 0) {
                // Check OpenSSLConfCmd if used
                if (log.isDebugEnabled())
                    log.debug(sm.getString("endpoint.apr.checkConf"));
                try {
                    if (!openSslConf.check(cctx)) {
                        log.error(sm.getString("endpoint.apr.errCheckConf"));
                        throw new Exception(sm.getString("endpoint.apr.errCheckConf"));
                    }
                } catch (Exception e) {
                    throw new Exception(sm.getString("endpoint.apr.errCheckConf"), e);
                }
                // Apply OpenSSLConfCmd if used
                if (log.isDebugEnabled())
                    log.debug(sm.getString("endpoint.apr.applyConf"));
                try {
                    if (!openSslConf.apply(cctx, ctx)) {
                        log.error(sm.getString("endpoint.apr.errApplyConf"));
                        throw new Exception(sm.getString("endpoint.apr.errApplyConf"));
                    }
                } catch (Exception e) {
                    throw new Exception(sm.getString("endpoint.apr.errApplyConf"), e);
                }
                // 重新配置启用的协议
                int opts = SSLContext.getOptions(ctx);
                List<String> enabled = new ArrayList<>();
                // 似乎无法在OpenSSL中显式禁用SSLv2Hello，因此始终启用它
                enabled.add(Constants.SSL_PROTO_SSLv2Hello);
                if ((opts & SSL.SSL_OP_NO_TLSv1) == 0) {
                    enabled.add(Constants.SSL_PROTO_TLSv1);
                }
                if ((opts & SSL.SSL_OP_NO_TLSv1_1) == 0) {
                    enabled.add(Constants.SSL_PROTO_TLSv1_1);
                }
                if ((opts & SSL.SSL_OP_NO_TLSv1_2) == 0) {
                    enabled.add(Constants.SSL_PROTO_TLSv1_2);
                }
                if ((opts & SSL.SSL_OP_NO_SSLv2) == 0) {
                    enabled.add(Constants.SSL_PROTO_SSLv2);
                }
                if ((opts & SSL.SSL_OP_NO_SSLv3) == 0) {
                    enabled.add(Constants.SSL_PROTO_SSLv3);
                }
                sslHostConfig.setEnabledProtocols(
                        enabled.toArray(new String[enabled.size()]));
                // 重新配置启用的密码
                sslHostConfig.setEnabledCiphers(SSLContext.getCiphers(ctx));
            }
        } else {
            cctx = 0;
        }

        sslHostConfig.setOpenSslConfContext(Long.valueOf(cctx));
        sslHostConfig.setOpenSslContext(Long.valueOf(ctx));
    }


    @Override
    protected void releaseSSLContext(SSLHostConfig sslHostConfig) {
        Long ctx = sslHostConfig.getOpenSslContext();
        if (ctx != null) {
            SSLContext.free(ctx.longValue());
            sslHostConfig.setOpenSslContext(null);
        }
        Long cctx = sslHostConfig.getOpenSslConfContext();
        if (cctx != null) {
            SSLConf.free(cctx.longValue());
            sslHostConfig.setOpenSslConfContext(null);
        }
    }


    @Override
    public long getSslContext(String sniHostName) {
        SSLHostConfig sslHostConfig = getSSLHostConfig(sniHostName);
        Long ctx = sslHostConfig.getOpenSslContext();
        if (ctx != null) {
            return ctx.longValue();
        }
        // Default
        return 0;
    }



    @Override
    public boolean isAlpnSupported() {
        // 如果正在使用TLS，则APR/native连接器始终支持ALPN，因为OpenSSL支持ALPN. 因此, 这相当于启用了SSL的测试.
        return isSSLEnabled();
    }


    /**
     * 启动APR端点, 创建acceptor，poller和sendfile线程.
     */
    @Override
    public void startInternal() throws Exception {

        if (!running) {
            running = true;
            paused = false;

            processorCache = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                    socketProperties.getProcessorCache());

            // Create worker collection
            if (getExecutor() == null) {
                createExecutor();
            }

            initializeConnectionLatch();

            // Start poller thread
            poller = new Poller();
            poller.init();
            Thread pollerThread = new Thread(poller, getName() + "-Poller");
            pollerThread.setPriority(threadPriority);
            pollerThread.setDaemon(true);
            pollerThread.start();

            // Start sendfile thread
            if (getUseSendfile()) {
                sendfile = new Sendfile();
                sendfile.init();
                Thread sendfileThread =
                        new Thread(sendfile, getName() + "-Sendfile");
                sendfileThread.setPriority(threadPriority);
                sendfileThread.setDaemon(true);
                sendfileThread.start();
            }

            startAcceptorThreads();
        }
    }


    /**
     * 停止端点. 这将导致所有处理线程停止.
     */
    @Override
    public void stopInternal() {
        releaseConnectionLatch();
        if (!paused) {
            pause();
        }
        if (running) {
            running = false;
            poller.stop();
            for (SocketWrapperBase<Long> socketWrapper : connections.values()) {
                try {
                    socketWrapper.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            for (AbstractEndpoint.Acceptor acceptor : acceptors) {
                long waitLeft = 10000;
                while (waitLeft > 0 &&
                        acceptor.getState() != AcceptorState.ENDED &&
                        serverSock != 0) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    waitLeft -= 50;
                }
                if (waitLeft == 0) {
                    log.warn(sm.getString("endpoint.warn.unlockAcceptorFailed",
                            acceptor.getThreadName()));
                   // 如果Acceptor仍在运行，则强制关闭套接字.
                   if (serverSock != 0) {
                       Socket.shutdown(serverSock, Socket.APR_SHUTDOWN_READ);
                       serverSock = 0;
                   }
                }
            }
            try {
                poller.destroy();
            } catch (Exception e) {
                // Ignore
            }
            poller = null;
            connections.clear();
            if (getUseSendfile()) {
                try {
                    sendfile.destroy();
                } catch (Exception e) {
                    // Ignore
                }
                sendfile = null;
            }
            processorCache.clear();
        }
        shutdownExecutor();
    }


    /**
     * 释放APR内存池, 并关闭服务器套接字.
     */
    @Override
    public void unbind() throws Exception {
        if (running) {
            stop();
        }

        // 如果已初始化，则销毁池
        if (serverSockPool != 0) {
            Pool.destroy(serverSockPool);
            serverSockPool = 0;
        }

        // 如果已初始化，请关闭服务器套接字
        if (serverSock != 0) {
            Socket.close(serverSock);
            serverSock = 0;
        }

        if (sslContext != 0) {
            Long ctx = Long.valueOf(sslContext);
            SSLContext.unregisterDefault(ctx);
            for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
                sslHostConfig.setOpenSslContext(null);
            }
            sslContext = 0;
        }

        // 如果已初始化，请关闭所有APR内存池和资源
        if (rootPool != 0) {
            Pool.destroy(rootPool);
            rootPool = 0;
        }

        getHandler().recycle();
    }


    // ------------------------------------------------------ Protected Methods

    @Override
    protected AbstractEndpoint.Acceptor createAcceptor() {
        return new Acceptor();
    }


    /**
     * 处理指定的连接.
     * 
     * @param socketWrapper The socket wrapper
     * 
     * @return <code>true</code>如果套接字配置正确，处理可能会继续; <code>false</code>如果套接字需要立即关闭
     */
    protected boolean setSocketOptions(SocketWrapperBase<Long> socketWrapper) {
        long socket = socketWrapper.getSocket().longValue();
        // 处理连接
        int step = 1;
        try {

            // 1: Set socket options: timeout, linger, etc
            if (socketProperties.getSoLingerOn() && socketProperties.getSoLingerTime() >= 0)
                Socket.optSet(socket, Socket.APR_SO_LINGER, socketProperties.getSoLingerTime());
            if (socketProperties.getTcpNoDelay())
                Socket.optSet(socket, Socket.APR_TCP_NODELAY, (socketProperties.getTcpNoDelay() ? 1 : 0));
            Socket.timeoutSet(socket, socketProperties.getSoTimeout() * 1000);

            // 2: SSL handshake
            step = 2;
            if (sslContext != 0) {
                SSLSocket.attach(sslContext, socket);
                if (SSLSocket.handshake(socket) != 0) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("endpoint.err.handshake") + ": " + SSL.getLastError());
                    }
                    return false;
                }

                if (negotiableProtocols.size() > 0) {
                    byte[] negotiated = new byte[256];
                    int len = SSLSocket.getALPN(socket, negotiated);
                    String negotiatedProtocol =
                            new String(negotiated, 0, len, StandardCharsets.UTF_8);
                    if (negotiatedProtocol.length() > 0) {
                        socketWrapper.setNegotiatedProtocol(negotiatedProtocol);
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("endpoint.alpn.negotiated", negotiatedProtocol));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            if (log.isDebugEnabled()) {
                if (step == 2) {
                    log.debug(sm.getString("endpoint.err.handshake"), t);
                } else {
                    log.debug(sm.getString("endpoint.err.unexpected"), t);
                }
            }
            // Tell to close the socket
            return false;
        }
        return true;
    }


    /**
     * 分配指定大小的新轮询器.
     * 
     * @param size 大小
     * @param pool 将从中分配轮询器的池
     * @param timeout 超时时间
     * 
     * @return 轮询器指针
     */
    protected long allocatePoller(int size, long pool, int timeout) {
        try {
            return Poll.create(size, pool, 0, timeout * 1000);
        } catch (Error e) {
            if (Status.APR_STATUS_IS_EINVAL(e.getError())) {
                log.info(sm.getString("endpoint.poll.limitedpollsize", "" + size));
                return 0;
            } else {
                log.error(sm.getString("endpoint.poll.initfail"), e);
                return -1;
            }
        }
    }

    /**
     * 处理给定套接字. 接受套接字时调用此方法.
     * 
     * @param socket The socket
     * 
     * @return <code>true</code>如果套接字配置正确，处理可能会继续; <code>false</code>如果套接字需要立即关闭
     */
    protected boolean processSocketWithOptions(long socket) {
        try {
            // 关闭期间, 执行器可能为 null - avoid NPE
            if (running) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("endpoint.debug.socket",
                            Long.valueOf(socket)));
                }
                AprSocketWrapper wrapper = new AprSocketWrapper(Long.valueOf(socket), this);
                wrapper.setKeepAliveLeft(getMaxKeepAliveRequests());
                wrapper.setSecure(isSSLEnabled());
                wrapper.setReadTimeout(getConnectionTimeout());
                wrapper.setWriteTimeout(getConnectionTimeout());
                connections.put(Long.valueOf(socket), wrapper);
                getExecutor().execute(new SocketWithOptionsProcessor(wrapper));
            }
        } catch (RejectedExecutionException x) {
            log.warn("Socket processing request was rejected for:"+socket,x);
            return false;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // 意味着我们有一个OOM或类似的创建一个线程, 或者池及其队列已满
            log.error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }


    /**
     * 处理给定的套接字. 通常保持活动或升级协议.
     *
     * @param socket    要处理的套接字
     * @param event     要处理的事件
     *
     * @return <code>true</code>如果处理正常完成;
     *         <code>false</code>表示发生错误，应该关闭套接字
     */
    protected boolean processSocket(long socket, SocketEvent event) {
        SocketWrapperBase<Long> socketWrapper = connections.get(Long.valueOf(socket));
        return processSocket(socketWrapper, event, true);
    }


    @Override
    protected SocketProcessorBase<Long> createSocketProcessor(
            SocketWrapperBase<Long> socketWrapper, SocketEvent event) {
        return new SocketProcessor(socketWrapper, event);
    }


    private void closeSocket(long socket) {
        // 一旦调用它, 将不再需要从套接字到包装器的映射.
        SocketWrapperBase<Long> wrapper = connections.remove(Long.valueOf(socket));
        if (wrapper != null) {
            // 转换为避免必须捕获从未抛出的IOE.
            ((AprSocketWrapper) wrapper).close();
        }
    }

    /*
     * 只有在轮询器当前不可能使用套接字时, 才应调用此方法. 直接从已知的错误条件中调用它通常是个坏主意.
     */
    private void destroySocket(long socket) {
        connections.remove(Long.valueOf(socket));
        if (log.isDebugEnabled()) {
            String msg = sm.getString("endpoint.debug.destroySocket",
                    Long.valueOf(socket));
            if (log.isTraceEnabled()) {
                log.trace(msg, new Exception());
            } else {
                log.debug(msg);
            }
        }
        // 如果直接调用此方法，请务必小心. 如果为同一个套接字调用两次，则JVM将为核心.
        // 目前仅从Poller.closePollset()调用此方法, 以确保在调用start()之后的stop()时, 保持活动的连接已关闭.
        if (socket != 0) {
            Socket.destroy(socket);
            countDownConnection();
        }
    }

    @Override
    protected Log getLog() {
        return log;
    }

    // --------------------------------------------------- Acceptor Inner Class
    /**
     * 后台线程监听传入的TCP/IP连接并将其移交给适当的处理器.
     */
    protected class Acceptor extends AbstractEndpoint.Acceptor {

        private final Log log = LogFactory.getLog(AprEndpoint.Acceptor.class);

        @Override
        public void run() {

            int errorDelay = 0;

            // 循环直到收到关机命令
            while (running) {

                // 如果端点暂停，则循环
                while (paused && running) {
                    state = AcceptorState.PAUSED;
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                if (!running) {
                    break;
                }
                state = AcceptorState.RUNNING;

                try {
                    //如果达到了最大连接数, 等待
                    countUpOrAwaitConnection();

                    long socket = 0;
                    try {
                        // 从服务器套接字接受下一个传入连接
                        socket = Socket.accept(serverSock);
                        if (log.isDebugEnabled()) {
                            long sa = Address.get(Socket.APR_REMOTE, socket);
                            Sockaddr addr = Address.getInfo(sa);
                            log.debug(sm.getString("endpoint.apr.remoteport",
                                    Long.valueOf(socket),
                                    Long.valueOf(addr.port)));
                        }
                    } catch (Exception e) {
                        // 没有得到套接字
                        countDownConnection();
                        if (running) {
                            // 如有必要，请引入延迟
                            errorDelay = handleExceptionWithDelay(errorDelay);
                            // re-throw
                            throw e;
                        } else {
                            break;
                        }
                    }
                    // 成功接受, 重置错误延迟
                    errorDelay = 0;

                    if (running && !paused) {
                        // 将此套接字移至适当的处理器
                        if (!processSocketWithOptions(socket)) {
                            // 立即关闭套接字
                            closeSocket(socket);
                        }
                    } else {
                        // 立即关闭套接字
                        // 没有代码路径可以将套接字添加到Poller, 所以使用destroySocket()
                        destroySocket(socket);
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    String msg = sm.getString("endpoint.accept.fail");
                    if (t instanceof Error) {
                        Error e = (Error) t;
                        if (e.getError() == 233) {
                            // 在HP-UX上不是错误，因此请记录为警告
                            // 所以它可以在该平台上过滤掉
                            // See bug 50273
                            log.warn(msg, t);
                        } else {
                            log.error(msg, t);
                        }
                    } else {
                            log.error(msg, t);
                    }
                }
                // 处理器完成后将自行回收
            }
            state = AcceptorState.ENDED;
        }
    }


    // -------------------------------------------------- SocketInfo Inner Class

    public static class SocketInfo {
        public long socket;
        public long timeout;
        public int flags;
        public boolean read() {
            return (flags & Poll.APR_POLLIN) == Poll.APR_POLLIN;
        }
        public boolean write() {
            return (flags & Poll.APR_POLLOUT) == Poll.APR_POLLOUT;
        }
        public static int merge(int flag1, int flag2) {
            return ((flag1 & Poll.APR_POLLIN) | (flag2 & Poll.APR_POLLIN))
                | ((flag1 & Poll.APR_POLLOUT) | (flag2 & Poll.APR_POLLOUT));
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Socket: [");
            sb.append(socket);
            sb.append("], timeout: [");
            sb.append(timeout);
            sb.append("], flags: [");
            sb.append(flags);
            return sb.toString();
        }
    }


    // ---------------------------------------------- SocketTimeouts Inner Class

    public static class SocketTimeouts {
        protected int size;

        protected long[] sockets;
        protected long[] timeouts;
        protected int pos = 0;

        public SocketTimeouts(int size) {
            this.size = 0;
            sockets = new long[size];
            timeouts = new long[size];
        }

        public void add(long socket, long timeout) {
            sockets[size] = socket;
            timeouts[size] = timeout;
            size++;
        }

        /**
         * 从轮询器中删除指定的套接字.
         *
         * @param socket 要删除的套接字
         *
         * @return 配置的套接字超时时间; 如果套接字不在套接字超时列表中，则为零
         */
        public long remove(long socket) {
            long result = 0;
            for (int i = 0; i < size; i++) {
                if (sockets[i] == socket) {
                    result = timeouts[i];
                    sockets[i] = sockets[size - 1];
                    timeouts[i] = timeouts[size - 1];
                    size--;
                    break;
                }
            }
            return result;
        }

        public long check(long date) {
            while (pos < size) {
                if (date >= timeouts[pos]) {
                    long result = sockets[pos];
                    sockets[pos] = sockets[size - 1];
                    timeouts[pos] = timeouts[size - 1];
                    size--;
                    return result;
                }
                pos++;
            }
            pos = 0;
            return 0;
        }

    }


    // -------------------------------------------------- SocketList Inner Class

    public static class SocketList {
        protected volatile int size;
        protected int pos;

        protected long[] sockets;
        protected long[] timeouts;
        protected int[] flags;

        protected SocketInfo info = new SocketInfo();

        public SocketList(int size) {
            this.size = 0;
            pos = 0;
            sockets = new long[size];
            timeouts = new long[size];
            flags = new int[size];
        }

        public int size() {
            return this.size;
        }

        public SocketInfo get() {
            if (pos == size) {
                return null;
            } else {
                info.socket = sockets[pos];
                info.timeout = timeouts[pos];
                info.flags = flags[pos];
                pos++;
                return info;
            }
        }

        public void clear() {
            size = 0;
            pos = 0;
        }

        public boolean add(long socket, long timeout, int flag) {
            if (size == sockets.length) {
                return false;
            } else {
                for (int i = 0; i < size; i++) {
                    if (sockets[i] == socket) {
                        flags[i] = SocketInfo.merge(flags[i], flag);
                        return true;
                    }
                }
                sockets[size] = socket;
                timeouts[size] = timeout;
                flags[size] = flag;
                size++;
                return true;
            }
        }

        public boolean remove(long socket) {
            for (int i = 0; i < size; i++) {
                if (sockets[i] == socket) {
                    sockets[i] = sockets[size - 1];
                    timeouts[i] = timeouts[size - 1];
                    flags[size] = flags[size -1];
                    size--;
                    return true;
                }
            }
            return false;
        }

        public void duplicate(SocketList copy) {
            copy.size = size;
            copy.pos = pos;
            System.arraycopy(sockets, 0, copy.sockets, 0, size);
            System.arraycopy(timeouts, 0, copy.timeouts, 0, size);
            System.arraycopy(flags, 0, copy.flags, 0, size);
        }

    }

    // ------------------------------------------------------ Poller Inner Class

    public class Poller implements Runnable {

        /**
         * 轮询器的指针.
         */
        private long[] pollers = null;

        /**
         * 实际轮询器大小.
         */
        private int actualPollerSize = 0;

        /**
         * 轮询器中留下的点的数量.
         */
        private int[] pollerSpace = null;

        /**
         * 此轮询器使用的低级别轮询器的数量.
         */
        private int pollerCount;

        /**
         * 轮询调用的超时时间.
         */
        private int pollerTime;

        /**
         * 轮询器超时时间变量，根据正在使用的轮询集数量进行调整，以便所有轮询集的总轮询时间保持等于pollTime.
         */
        private int nextPollerTime;

        /**
         * Root pool.
         */
        private long pool = 0;

        /**
         * 套接字描述符.
         */
        private long[] desc;

        /**
         * 要添加到轮询器的套接字列表.
         */
        private SocketList addList = null;  // Modifications guarded by this


        /**
         * 要关闭的套接字列表.
         */
        private SocketList closeList = null; // Modifications guarded by this


        /**
         * 用于存储超时的结构.
         */
        private SocketTimeouts timeouts = null;


        /**
         * 最后一次维护. 维持大约每一秒运行一次 (运行之间可能会稍微长一点).
         */
        private long lastMaintain = System.currentTimeMillis();


        /**
         * 此Poller中当前连接的数量. Poller的正确操作取决于这个数字是否正确.
         * 如果它不正确, 可能Poller将进入一个等待循环，在该循环中它等待下一个连接被添加到Poller，然后它应该仍然轮询现有的连接.
         * 虽然在撰写本评论时没有必要, 它已作为AtomicInteger实现, 以确保它保持线程安全.
         */
        private AtomicInteger connectionCount = new AtomicInteger(0);
        public int getConnectionCount() { return connectionCount.get(); }


        private volatile boolean pollerRunning = true;

        /**
         * 创建轮询器. 有一些版本的APR, 最大轮询器大小为62 (重新编译APR是必要的，以消除此限制).
         */
        protected synchronized void init() {

            pool = Pool.create(serverSockPool);

            // 默认情况下, 单个轮询器
            int defaultPollerSize = getMaxConnections();

            if ((OS.IS_WIN32 || OS.IS_WIN64) && (defaultPollerSize > 1024)) {
                // 获得合理性能的每个轮询器的最大值为1024
                // 调整轮询器大小，使其不会达到限制. 这是XP/Server 2003的限制，已在Vista/Server 2008中修复.
                actualPollerSize = 1024;
            } else {
                actualPollerSize = defaultPollerSize;
            }

            timeouts = new SocketTimeouts(defaultPollerSize);

            // 目前, 设置超时是没用的, 但它可以再次使用，因为普通的轮询器可以更快地使用维护.
            // 可能不值得打扰.
            long pollset = allocatePoller(actualPollerSize, pool, -1);
            if (pollset == 0 && actualPollerSize > 1024) {
                actualPollerSize = 1024;
                pollset = allocatePoller(actualPollerSize, pool, -1);
            }
            if (pollset == 0) {
                actualPollerSize = 62;
                pollset = allocatePoller(actualPollerSize, pool, -1);
            }

            pollerCount = defaultPollerSize / actualPollerSize;
            pollerTime = pollTime / pollerCount;
            nextPollerTime = pollerTime;

            pollers = new long[pollerCount];
            pollers[0] = pollset;
            for (int i = 1; i < pollerCount; i++) {
                pollers[i] = allocatePoller(actualPollerSize, pool, -1);
            }

            pollerSpace = new int[pollerCount];
            for (int i = 0; i < pollerCount; i++) {
                pollerSpace[i] = actualPollerSize;
            }

            /*
             * x2 - 一个描述符用于套接字，一个用于事件.
             * x2 - 某些APR实现为不同的条目返回同一套接字的多个事件. 每个套接字在任何时候最多注册两个事件（读和写）.
             *
             * 因此，大小是实际的轮询器大小 *4.
             */
            desc = new long[actualPollerSize * 4];
            connectionCount.set(0);
            addList = new SocketList(defaultPollerSize);
            closeList = new SocketList(defaultPollerSize);
        }


        /*
         * 此方法是同步的，因此一旦此方法完成，就无法将套接字添加到Poller的addList中.
         */
        protected synchronized void stop() {
            pollerRunning = false;
        }


        /**
         * 销毁轮询器.
         */
        protected synchronized void destroy() {
            // 在做任何事之前等待pollerTime, 以便轮询线程退出, 否则仍然在轮询器中的套接字的并行销毁可能会导致问题
            try {
                this.notify();
                this.wait(pollerCount * pollTime / 1000);
            } catch (InterruptedException e) {
                // Ignore
            }
            // 关闭关闭队列中的所有套接字
            SocketInfo info = closeList.get();
            while (info != null) {
                // 确保没有尝试添加套接字以及关闭套接字
                addList.remove(info.socket);
                // 在关闭套接字之前，请确保套接字不在轮询器中
                removeFromPoller(info.socket);
                // Poller此时没有运行，所以直接使用destroySocket()
                destroySocket(info.socket);
                info = closeList.get();
            }
            closeList.clear();
            // 关闭添加队列中的所有套接字
            info = addList.get();
            while (info != null) {
                // 在关闭套接字之前，请确保套接字不在轮询器中
                removeFromPoller(info.socket);
                // Poller此时没有运行，所以直接使用destroySocket()
                destroySocket(info.socket);
                info = addList.get();
            }
            addList.clear();
            // 关闭仍在轮询器中的所有套接字
            for (int i = 0; i < pollerCount; i++) {
                int rv = Poll.pollset(pollers[i], desc);
                if (rv > 0) {
                    for (int n = 0; n < rv; n++) {
                        destroySocket(desc[n*2+1]);
                    }
                }
            }
            Pool.destroy(pool);
            connectionCount.set(0);
        }


        /**
         * 将指定的套接字和关联的池添加到轮询器.
         * 套接字将添加到临时数组中, 并且在等于pollTime的最大时间之后首先轮询 (在多数情况下, 延迟会低很多).
         * Note: 如果读取和写入都是false, 套接字只会检查超时; 如果套接字已存在于轮询器中, 将生成一个回调事件, 并将从轮询器中删除套接字.
         *
         * @param socket 添加到轮询器的套接字
         * @param timeout 用于此连接的超时时间, 以毫秒为单位
         * @param flags 要轮询的事件 (Poll.APR_POLLIN 或 Poll.APR_POLLOUT)
         */
        private void add(long socket, long timeout, int flags) {
            if (log.isDebugEnabled()) {
                String msg = sm.getString("endpoint.debug.pollerAdd",
                        Long.valueOf(socket), Long.valueOf(timeout),
                        Integer.valueOf(flags));
                if (log.isTraceEnabled()) {
                    log.trace(msg, new Exception());
                } else {
                    log.debug(msg);
                }
            }
            if (timeout <= 0) {
                // Always put a timeout in
                timeout = Integer.MAX_VALUE;
            }
            synchronized (this) {
                // 将套接字添加到列表中. 在轮询之前，新添加的套接字最多会等待pollTime.
                if (addList.add(socket, timeout, flags)) {
                    this.notify();
                }
            }
        }


        /**
         * 将指定的套接字添加到其中一个轮询器. 只能从{@link Poller#run()}调用.
         */
        private boolean addToPoller(long socket, int events) {
            int rv = -1;
            for (int i = 0; i < pollers.length; i++) {
                if (pollerSpace[i] > 0) {
                    rv = Poll.add(pollers[i], socket, events);
                    if (rv == Status.APR_SUCCESS) {
                        pollerSpace[i]--;
                        connectionCount.incrementAndGet();
                        return true;
                    }
                }
            }
            return false;
        }


        /*
         * 这只是从SocketWrapper调用，以确保每个socket只调用一次. 多次调用它通常会导致JVM崩溃.
         */
        private synchronized void close(long socket) {
            closeList.add(socket, 0, 0);
            this.notify();
        }


        /**
         * 从轮询器中删除指定的套接字. 只能从{@link Poller#run()}调用.
         */
        private void removeFromPoller(long socket) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.debug.pollerRemove",
                        Long.valueOf(socket)));
            }
            int rv = -1;
            for (int i = 0; i < pollers.length; i++) {
                if (pollerSpace[i] < actualPollerSize) {
                    rv = Poll.remove(pollers[i], socket);
                    if (rv != Status.APR_NOTFOUND) {
                        pollerSpace[i]++;
                        connectionCount.decrementAndGet();
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("endpoint.debug.pollerRemoved",
                                    Long.valueOf(socket)));
                        }
                        break;
                    }
                }
            }
            timeouts.remove(socket);
        }

        /**
         * 超时检查. 只能从{@link Poller#run()}调用.
         */
        private synchronized void maintain() {
            long date = System.currentTimeMillis();
            // 保持每1s最多运行一次, 虽然它可能会被调用更多
            if ((date - lastMaintain) < 1000L) {
                return;
            } else {
                lastMaintain = date;
            }
            long socket = timeouts.check(date);
            while (socket != 0) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("endpoint.debug.socketTimeout",
                            Long.valueOf(socket)));
                }
                SocketWrapperBase<Long> socketWrapper = connections.get(Long.valueOf(socket));
                socketWrapper.setError(new SocketTimeoutException());
                processSocket(socketWrapper, SocketEvent.ERROR, true);
                socket = timeouts.check(date);
            }

        }

        @Override
        public String toString() {
            StringBuffer buf = new StringBuffer();
            buf.append("Poller");
            long[] res = new long[actualPollerSize * 2];
            for (int i = 0; i < pollers.length; i++) {
                int count = Poll.pollset(pollers[i], res);
                buf.append(" [ ");
                for (int j = 0; j < count; j++) {
                    buf.append(desc[2*j+1]).append(" ");
                }
                buf.append("]");
            }
            return buf.toString();
        }

        /**
         * 将套接字添加到Poller的后台线程, 检查轮询器是否触发事件, 并在事件发生时将相关的套接字关闭到适当的处理器.
         */
        @Override
        public void run() {

            SocketList localAddList = new SocketList(getMaxConnections());
            SocketList localCloseList = new SocketList(getMaxConnections());

            // 循环, 直到收到关机命令
            while (pollerRunning) {

                // 如果轮询器为空，请检查超时.
                while (pollerRunning && connectionCount.get() < 1 &&
                        addList.size() < 1 && closeList.size() < 1) {
                    try {
                        if (getConnectionTimeout() > 0 && pollerRunning) {
                            maintain();
                        }
                        synchronized (this) {
                            // 确保自上面的检查以来没有在addList或closeList中放置套接字.
                            // 如果没有这个检查，可能会有10秒暂停而没有处理，因为add()/close()中的notify()调用没有任何效果，因为它发生在输入此同步块之前
                            if (addList.size() < 1 && closeList.size() < 1) {
                                this.wait(10000);
                            }
                        }
                    } catch (InterruptedException e) {
                        // Ignore
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                        getLog().warn(sm.getString("endpoint.timeout.err"));
                    }
                }

                // 如果轮询器已停止，请不要添加或轮询
                if (!pollerRunning) {
                    break;
                }

                try {
                    // 复制添加和删除列表，以便最小化同步
                    synchronized (this) {
                        if (closeList.size() > 0) {
                            // 复制到另一个列表, 以便最小化同步
                            closeList.duplicate(localCloseList);
                            closeList.clear();
                        } else {
                            localCloseList.clear();
                        }
                    }
                    synchronized (this) {
                        if (addList.size() > 0) {
                            // 复制到另一个列表, 以便最小化同步
                            addList.duplicate(localAddList);
                            addList.clear();
                        } else {
                            localAddList.clear();
                        }
                    }

                    // Remove sockets
                    if (localCloseList.size() > 0) {
                        SocketInfo info = localCloseList.get();
                        while (info != null) {
                            localAddList.remove(info.socket);
                            removeFromPoller(info.socket);
                            destroySocket(info.socket);
                            info = localCloseList.get();
                        }
                    }

                    // 添加正在等待轮询器的套接字
                    if (localAddList.size() > 0) {
                        SocketInfo info = localAddList.get();
                        while (info != null) {
                            if (log.isDebugEnabled()) {
                                log.debug(sm.getString(
                                        "endpoint.debug.pollerAddDo",
                                        Long.valueOf(info.socket)));
                            }
                            timeouts.remove(info.socket);
                            AprSocketWrapper wrapper = connections.get(
                                    Long.valueOf(info.socket));
                            if (wrapper == null) {
                                continue;
                            }
                            if (info.read() || info.write()) {
                                wrapper.pollerFlags = wrapper.pollerFlags |
                                        (info.read() ? Poll.APR_POLLIN : 0) |
                                        (info.write() ? Poll.APR_POLLOUT : 0);
                                // 套接字只能添加到轮询器一次. 添加两次将返回错误，这将关闭套接字.
                                // 因此，请确保要添加的套接字不在轮询器中.
                                removeFromPoller(info.socket);
                                if (!addToPoller(info.socket, wrapper.pollerFlags)) {
                                    closeSocket(info.socket);
                                } else {
                                    timeouts.add(info.socket,
                                            System.currentTimeMillis() +
                                                    info.timeout);
                                }
                            } else {
                                // Should never happen.
                                closeSocket(info.socket);
                                getLog().warn(sm.getString(
                                        "endpoint.apr.pollAddInvalid", info));
                            }
                            info = localAddList.get();
                        }
                    }

                    // 轮询指定的时间间隔
                    for (int i = 0; i < pollers.length; i++) {

                        // 要求重新分配池的标志
                        boolean reset = false;

                        int rv = 0;
                        // 迭代每个轮询器, 但不需要轮询空轮询器
                        if (pollerSpace[i] < actualPollerSize) {
                            rv = Poll.poll(pollers[i], nextPollerTime, desc, true);
                            // 重置 nextPollerTime
                            nextPollerTime = pollerTime;
                        } else {
                            // 跳过空轮询集, 意味着跳过pollerTime微秒的等待时间.
                            // 如果跳过大多数轮询集，则此循环将比预期更紧密，这可能导致高于预期的CPU使用率.
                            // 扩展nextPollerTime, 可确保此循环始终需要大约相同的时间来执行.
                            nextPollerTime += pollerTime;
                        }
                        if (rv > 0) {
                            rv = mergeDescriptors(desc, rv);
                            pollerSpace[i] += rv;
                            connectionCount.addAndGet(-rv);
                            for (int n = 0; n < rv; n++) {
                                if (getLog().isDebugEnabled()) {
                                    log.debug(sm.getString(
                                            "endpoint.debug.pollerProcess",
                                            Long.valueOf(desc[n*2+1]),
                                            Long.valueOf(desc[n*2])));
                                }
                                long timeout = timeouts.remove(desc[n*2+1]);
                                AprSocketWrapper wrapper = connections.get(
                                        Long.valueOf(desc[n*2+1]));
                                if (wrapper == null) {
                                    // Socket在另一个线程中关闭，同时仍然在Poller中，但在新数据到达之前没有从Poller中删除.
                                    continue;
                                }
                                wrapper.pollerFlags = wrapper.pollerFlags & ~((int) desc[n*2]);
                                // 检查套接字是否出现异常并将此套接字交给工作者
                                if (((desc[n*2] & Poll.APR_POLLHUP) == Poll.APR_POLLHUP)
                                        || ((desc[n*2] & Poll.APR_POLLERR) == Poll.APR_POLLERR)
                                        || ((desc[n*2] & Poll.APR_POLLNVAL) == Poll.APR_POLLNVAL)) {
                                    // 需要触发错误处理. 轮询器可能会返回错误代码加上它正在等待的标志，或者它可能只返回错误代码.
                                    // 可以在这里处理错误, 但如果这样做的话, 应用程序代码中的错误不会出现任何异常.
                                    // 通过信号读/写是可能的, 将尝试读/写, 失败, 这将触发应用程序可以看到的异常.
                                    // 首先检查返回标志, 然后是套接字注册的内容
                                    if ((desc[n*2] & Poll.APR_POLLIN) == Poll.APR_POLLIN) {
                                        // 在非阻塞读取期间可能发生错误
                                        if (!processSocket(desc[n*2+1], SocketEvent.OPEN_READ)) {
                                            // Close socket and clear pool
                                            closeSocket(desc[n*2+1]);
                                        }
                                    } else if ((desc[n*2] & Poll.APR_POLLOUT) == Poll.APR_POLLOUT) {
                                        // 在非阻塞写入期间可能发生错误
                                        if (!processSocket(desc[n*2+1], SocketEvent.OPEN_WRITE)) {
                                            // Close socket and clear pool
                                            closeSocket(desc[n*2+1]);
                                        }
                                    } else if ((wrapper.pollerFlags & Poll.APR_POLLIN) == Poll.APR_POLLIN) {
                                        // 无法分析发生错误时发生了什么，但是套接字已注册为非阻塞读取，请使用它
                                        if (!processSocket(desc[n*2+1], SocketEvent.OPEN_READ)) {
                                            // Close socket and clear pool
                                            closeSocket(desc[n*2+1]);
                                        }
                                    } else if ((wrapper.pollerFlags & Poll.APR_POLLOUT) == Poll.APR_POLLOUT) {
                                        // 无法判断错误发生时发生了什么，但是套接字已注册为非阻塞写入，请使用它
                                        if (!processSocket(desc[n*2+1], SocketEvent.OPEN_WRITE)) {
                                            // Close socket and clear pool
                                            closeSocket(desc[n*2+1]);
                                        }
                                    } else {
                                        // Close socket and clear pool
                                        closeSocket(desc[n*2+1]);
                                    }
                                } else if (((desc[n*2] & Poll.APR_POLLIN) == Poll.APR_POLLIN)
                                        || ((desc[n*2] & Poll.APR_POLLOUT) == Poll.APR_POLLOUT)) {
                                    boolean error = false;
                                    if (((desc[n*2] & Poll.APR_POLLIN) == Poll.APR_POLLIN) &&
                                            !processSocket(desc[n*2+1], SocketEvent.OPEN_READ)) {
                                        error = true;
                                        // Close socket and clear pool
                                        closeSocket(desc[n*2+1]);
                                    }
                                    if (!error &&
                                            ((desc[n*2] & Poll.APR_POLLOUT) == Poll.APR_POLLOUT) &&
                                            !processSocket(desc[n*2+1], SocketEvent.OPEN_WRITE)) {
                                        // Close socket and clear pool
                                        error = true;
                                        closeSocket(desc[n*2+1]);
                                    }
                                    if (!error && wrapper.pollerFlags != 0) {
                                        // 如果socket已注册多个事件, 但仅发生了一些事件, 重新注册剩余的事件.
                                        // timeout是System.currentTimeMillis()的值，它被设置为套接字将超时的点.
                                        // 添加到轮询器时, 从现在开始的超时（以毫秒为单位）是必需的.
                                        // 所以首先, 减去当前时间戳
                                        if (timeout > 0) {
                                            timeout = timeout - System.currentTimeMillis();
                                        }
                                        // 如果套接字现在已经过期了, 使用非常短的超时重新添加它
                                        if (timeout <= 0) {
                                            timeout = 1;
                                        }
                                        // 应该是不可能的，但以防万一，因为超时将被转换为int.
                                        if (timeout > Integer.MAX_VALUE) {
                                            timeout = Integer.MAX_VALUE;
                                        }
                                        add(desc[n*2+1], (int) timeout, wrapper.pollerFlags);
                                    }
                                } else {
                                    // Unknown event
                                    getLog().warn(sm.getString(
                                            "endpoint.apr.pollUnknownEvent",
                                            Long.valueOf(desc[n*2])));
                                    // Close socket and clear pool
                                    closeSocket(desc[n*2+1]);
                                }
                            }
                        } else if (rv < 0) {
                            int errn = -rv;
                            // 任何非超时或中断错误都至关重要
                            if ((errn != Status.TIMEUP) && (errn != Status.EINTR)) {
                                if (errn >  Status.APR_OS_START_USERERR) {
                                    errn -=  Status.APR_OS_START_USERERR;
                                }
                                getLog().error(sm.getString(
                                        "endpoint.apr.pollError",
                                        Integer.valueOf(errn),
                                        Error.strerror(errn)));
                                // 销毁并重新分配轮询器
                                reset = true;
                            }
                        }

                        if (reset && pollerRunning) {
                            // 重新分配当前的轮询器
                            int count = Poll.pollset(pollers[i], desc);
                            long newPoller = allocatePoller(actualPollerSize, pool, -1);
                            // 暂时不要恢复连接, 因为还没有测试过
                            pollerSpace[i] = actualPollerSize;
                            connectionCount.addAndGet(-count);
                            Poll.destroy(pollers[i]);
                            pollers[i] = newPoller;
                        }

                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    getLog().warn(sm.getString("endpoint.poll.error"), t);
                }
                try {
                    // 处理套接字超时
                    if (getConnectionTimeout() > 0 && pollerRunning) {
                        // 这适用于所有内容, 并且只使用一种超时机制, 但是使用旧维护可以使非事件轮询器更快一些.
                        maintain();
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    getLog().warn(sm.getString("endpoint.timeout.err"), t);
                }
            }

            synchronized (this) {
                this.notifyAll();
            }
        }


        private int mergeDescriptors(long[] desc, int startCount) {
            /*
             * https://bz.apache.org/bugzilla/show_bug.cgi?id=57653#c6建议仅在OSX和BSD上进行此合并.
             *
             * https://bz.apache.org/bugzilla/show_bug.cgi?id=56313 建议相同, 或类似, 问题发生在Windows上.
             * Notes: 仅填充数组的第一个startCount * 2元素.
             *        数组是事件，套接字，事件，套接字等.
             */
            HashMap<Long,Long> merged = new HashMap<>(startCount);
            for (int n = 0; n < startCount; n++) {
                Long old = merged.put(Long.valueOf(desc[2*n+1]), Long.valueOf(desc[2*n]));
                if (old != null) {
                    // 这是一个替代. 合并新值和旧值
                    merged.put(Long.valueOf(desc[2*n+1]),
                            Long.valueOf(desc[2*n] | old.longValue()));
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("endpoint.apr.pollMergeEvents",
                                Long.valueOf(desc[2*n+1]), Long.valueOf(desc[2*n]), old));
                    }
                }
            }
            int i = 0;
            for (Map.Entry<Long,Long> entry : merged.entrySet()) {
                desc[i++] = entry.getValue().longValue();
                desc[i++] = entry.getKey().longValue();
            }
            return merged.size();
        }
    }


    // ----------------------------------------------- SendfileData Inner Class

    public static class SendfileData extends SendfileDataBase {
        // File
        protected long fd;
        protected long fdpool;
        // Socket and socket pool
        protected long socket;

        public SendfileData(String filename, long pos, long length) {
            super(filename, pos, length);
        }
    }


    // --------------------------------------------------- Sendfile Inner Class

    public class Sendfile implements Runnable {

        protected long sendfilePollset = 0;
        protected long pool = 0;
        protected long[] desc;
        protected HashMap<Long, SendfileData> sendfileData;

        protected int sendfileCount;
        public int getSendfileCount() { return sendfileCount; }

        protected ArrayList<SendfileData> addS;

        private volatile boolean sendfileRunning = true;

        /**
         * 创建sendfile轮询器. 有一些版本的APR, 最大轮询器大小为62 (重新编译APR是必要的，以消除此限制).
         */
        protected void init() {
            pool = Pool.create(serverSockPool);
            int size = sendfileSize;
            if (size <= 0) {
                size = (OS.IS_WIN32 || OS.IS_WIN64) ? (1 * 1024) : (16 * 1024);
            }
            sendfilePollset = allocatePoller(size, pool, getConnectionTimeout());
            if (sendfilePollset == 0 && size > 1024) {
                size = 1024;
                sendfilePollset = allocatePoller(size, pool, getConnectionTimeout());
            }
            if (sendfilePollset == 0) {
                size = 62;
                sendfilePollset = allocatePoller(size, pool, getConnectionTimeout());
            }
            desc = new long[size * 2];
            sendfileData = new HashMap<>(size);
            addS = new ArrayList<>();
        }

        /**
         * 销毁轮询器.
         */
        protected void destroy() {
            sendfileRunning = false;
            // 在做任何事情之前等待polltime, 以便轮询线程退出, 否则仍然在轮询器中的套接字的并行销毁可能会导致问题
            try {
                synchronized (this) {
                    this.notify();
                    this.wait(pollTime / 1000);
                }
            } catch (InterruptedException e) {
                // Ignore
            }
            // 关闭添加队列中剩余的任何套接字
            for (int i = (addS.size() - 1); i >= 0; i--) {
                SendfileData data = addS.get(i);
                closeSocket(data.socket);
            }
            // 关闭仍在轮询器中的所有套接字
            int rv = Poll.pollset(sendfilePollset, desc);
            if (rv > 0) {
                for (int n = 0; n < rv; n++) {
                    closeSocket(desc[n*2+1]);
                }
            }
            Pool.destroy(pool);
            sendfileData.clear();
        }

        /**
         * 将sendfile数据添加到sendfile轮询器.
         * 请注意, 在大多数情况下, 对sendfile的初始非阻塞调用将立即返回, 并将在内核中异步处理.
         * 结果是, 轮询器永远不会被使用.
         *
         * @param data 包含对应发送的数据的引用
         * @return true 如果所有数据都已立即发送, 否则 false
         */
        public SendfileState add(SendfileData data) {
            // 从给定的数据初始化fd
            try {
                data.fdpool = Socket.pool(data.socket);
                data.fd = File.open
                    (data.fileName, File.APR_FOPEN_READ
                     | File.APR_FOPEN_SENDFILE_ENABLED | File.APR_FOPEN_BINARY,
                     0, data.fdpool);
                // 将套接字设置为非阻塞模式
                Socket.timeoutSet(data.socket, 0);
                while (sendfileRunning) {
                    long nw = Socket.sendfilen(data.socket, data.fd,
                                               data.pos, data.length, 0);
                    if (nw < 0) {
                        if (!(-nw == Status.EAGAIN)) {
                            Pool.destroy(data.fdpool);
                            data.socket = 0;
                            return SendfileState.ERROR;
                        } else {
                            // 打破循环, 并将套接字添加到轮询器.
                            break;
                        }
                    } else {
                        data.pos += nw;
                        data.length -= nw;
                        if (data.length == 0) {
                            // 整个文件已发送
                            Pool.destroy(data.fdpool);
                            // 将套接字设置为阻塞模式
                            Socket.timeoutSet(data.socket, getConnectionTimeout() * 1000);
                            return SendfileState.DONE;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.sendfile.error"), e);
                return SendfileState.ERROR;
            }
            // 将套接字添加到列表中. 在轮询之前，新添加的套接字最多会等待pollTime
            synchronized (this) {
                addS.add(data);
                this.notify();
            }
            return SendfileState.PENDING;
        }

        /**
         * 从轮询器中删除套接字.
         *
         * @param data 应该删除的sendfile数据
         */
        protected void remove(SendfileData data) {
            int rv = Poll.remove(sendfilePollset, data.socket);
            if (rv == Status.APR_SUCCESS) {
                sendfileCount--;
            }
            sendfileData.remove(Long.valueOf(data.socket));
        }

        /**
         * 后台线程监听传入的TCP/IP连接并将其移交给适当的处理器.
         */
        @Override
        public void run() {

            long maintainTime = 0;
            // 循环, 直到收到关机命令
            while (sendfileRunning) {

                // 如果端点暂停，则循环
                while (sendfileRunning && paused) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
                // 如果轮询器为空，则循环
                while (sendfileRunning && sendfileCount < 1 && addS.size() < 1) {
                    // 重置维护时间.
                    maintainTime = 0;
                    try {
                        synchronized (this) {
                            this.wait();
                        }
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                // 如果轮询器已停止，请不要添加或轮询
                if (!sendfileRunning) {
                    break;
                }

                try {
                    // 将套接字添加到轮询器
                    if (addS.size() > 0) {
                        synchronized (this) {
                            for (int i = (addS.size() - 1); i >= 0; i--) {
                                SendfileData data = addS.get(i);
                                int rv = Poll.add(sendfilePollset, data.socket, Poll.APR_POLLOUT);
                                if (rv == Status.APR_SUCCESS) {
                                    sendfileData.put(Long.valueOf(data.socket), data);
                                    sendfileCount++;
                                } else {
                                    getLog().warn(sm.getString(
                                            "endpoint.sendfile.addfail",
                                            Integer.valueOf(rv),
                                            Error.strerror(rv)));
                                    // 什么都不能做: 立即关闭套接字
                                    closeSocket(data.socket);
                                }
                            }
                            addS.clear();
                        }
                    }

                    maintainTime += pollTime;
                    // 指定间隔的池
                    int rv = Poll.poll(sendfilePollset, pollTime, desc, false);
                    if (rv > 0) {
                        for (int n = 0; n < rv; n++) {
                            // 获取sendfile状态
                            SendfileData state =
                                sendfileData.get(Long.valueOf(desc[n*2+1]));
                            // Problem events
                            if (((desc[n*2] & Poll.APR_POLLHUP) == Poll.APR_POLLHUP)
                                    || ((desc[n*2] & Poll.APR_POLLERR) == Poll.APR_POLLERR)) {
                                // Close socket and clear pool
                                remove(state);
                                // 销毁应该关闭文件的文件描述符池
                                // 关闭套接字, 因为响应不完整
                                closeSocket(state.socket);
                                continue;
                            }
                            // 使用sendfile写一些数据
                            long nw = Socket.sendfilen(state.socket, state.fd,
                                                       state.pos,
                                                       state.length, 0);
                            if (nw < 0) {
                                // Close socket and clear pool
                                remove(state);
                                // 关闭套接字, 因为响应不完整
                                // 这也会关闭文件.
                                closeSocket(state.socket);
                                continue;
                            }

                            state.pos += nw;
                            state.length -= nw;
                            if (state.length == 0) {
                                remove(state);
                                switch (state.keepAliveState) {
                                case NONE: {
                                    // 关闭套接字，因为这是非keep-alive请求的结束.
                                    closeSocket(state.socket);
                                    break;
                                }
                                case PIPELINED: {
                                    // 销毁应该关闭文件的文件描述符池
                                    Pool.destroy(state.fdpool);
                                    Socket.timeoutSet(state.socket, getConnectionTimeout() * 1000);
                                    // 处理流水线请求数据
                                    if (!processSocket(state.socket, SocketEvent.OPEN_READ)) {
                                        closeSocket(state.socket);
                                    }
                                    break;
                                }
                                case OPEN: {
                                    // 销毁应该关闭文件的文件描述符池
                                    Pool.destroy(state.fdpool);
                                    Socket.timeoutSet(state.socket, getConnectionTimeout() * 1000);
                                    // 将套接字放回轮询器以处理进一步的请求
                                    getPoller().add(state.socket, getKeepAliveTimeout(),
                                            Poll.APR_POLLIN);
                                    break;
                                }
                                }
                            }
                        }
                    } else if (rv < 0) {
                        int errn = -rv;
                        /* 任何非超时或中断错误都至关重要 */
                        if ((errn != Status.TIMEUP) && (errn != Status.EINTR)) {
                            if (errn >  Status.APR_OS_START_USERERR) {
                                errn -=  Status.APR_OS_START_USERERR;
                            }
                            getLog().error(sm.getString(
                                    "endpoint.apr.pollError",
                                    Integer.valueOf(errn),
                                    Error.strerror(errn)));
                            // Handle poll critical failure
                            synchronized (this) {
                                destroy();
                                init();
                            }
                            continue;
                        }
                    }
                    // 为sendfile轮询器调用maintain
                    if (getConnectionTimeout() > 0 &&
                            maintainTime > 1000000L && sendfileRunning) {
                        rv = Poll.maintain(sendfilePollset, desc, false);
                        maintainTime = 0;
                        if (rv > 0) {
                            for (int n = 0; n < rv; n++) {
                                // 获取sendfile状态
                                SendfileData state = sendfileData.get(Long.valueOf(desc[n]));
                                // Close socket and clear pool
                                remove(state);
                                // 销毁应该关闭文件的文件描述符池
                                // 关闭套接字, 因为响应不完整
                                closeSocket(state.socket);
                            }
                        }
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    getLog().error(sm.getString("endpoint.poll.error"), t);
                }
            }

            synchronized (this) {
                this.notifyAll();
            }

        }

    }


    // --------------------------------- SocketWithOptionsProcessor Inner Class

    /**
     * 这个类相当于Worker, 但只会在外部Executor线程池中使用. 这也将设置套接字选项并进行握手.
     *
     * 在accept()之后调用.
     */
    protected class SocketWithOptionsProcessor implements Runnable {

        protected SocketWrapperBase<Long> socket = null;


        public SocketWithOptionsProcessor(SocketWrapperBase<Long> socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            synchronized (socket) {
                if (!deferAccept) {
                    if (setSocketOptions(socket)) {
                        getPoller().add(socket.getSocket().longValue(),
                                getConnectionTimeout(), Poll.APR_POLLIN);
                    } else {
                        // 关闭套接字和池
                        closeSocket(socket.getSocket().longValue());
                        socket = null;
                    }
                } else {
                    // 处理来自此套接字的请求
                    if (!setSocketOptions(socket)) {
                        // 关闭套接字和池
                        closeSocket(socket.getSocket().longValue());
                        socket = null;
                        return;
                    }
                    // 处理来自此套接字的请求
                    Handler.SocketState state = getHandler().process(socket,
                            SocketEvent.OPEN_READ);
                    if (state == Handler.SocketState.CLOSED) {
                        // 关闭套接字和池
                        closeSocket(socket.getSocket().longValue());
                        socket = null;
                    }
                }
            }
        }
    }


    // -------------------------------------------- SocketProcessor Inner Class


    /**
     * 这个类相当于Worker, 但只会在外部Executor线程池中使用.
     */
    protected class SocketProcessor extends  SocketProcessorBase<Long> {

        public SocketProcessor(SocketWrapperBase<Long> socketWrapper, SocketEvent event) {
            super(socketWrapper, event);
        }

        @Override
        protected void doRun() {
            try {
                // 处理来自此套接字的请求
                SocketState state = getHandler().process(socketWrapper, event);
                if (state == Handler.SocketState.CLOSED) {
                    // 关闭套接字和池
                    closeSocket(socketWrapper.getSocket().longValue());
                }
            } finally {
                socketWrapper = null;
                event = null;
                // 返回到缓存
                if (running && !paused) {
                    processorCache.push(this);
                }
            }
        }
    }


    public static class AprSocketWrapper extends SocketWrapperBase<Long> {

        private static final int SSL_OUTPUT_BUFFER_SIZE = 8192;

        private final ByteBuffer sslOutputBuffer;

        private final Object closedLock = new Object();
        private volatile boolean closed = false;

        // 该字段只能由Poller#run()使用
        private int pollerFlags = 0;


        public AprSocketWrapper(Long socket, AprEndpoint endpoint) {
            super(socket, endpoint);

            // TODO 使socketWriteBuffer大小可配置，并将SSL和app缓冲区大小设置与NIO和NIO2对齐.
            if (endpoint.isSSLEnabled()) {
                sslOutputBuffer = ByteBuffer.allocateDirect(SSL_OUTPUT_BUFFER_SIZE);
                sslOutputBuffer.position(SSL_OUTPUT_BUFFER_SIZE);
            } else {
                sslOutputBuffer = null;
            }

            socketBufferHandler = new SocketBufferHandler(6 * 1500, 6 * 1500, true);
        }


        @Override
        public int read(boolean block, byte[] b, int off, int len) throws IOException {
            int nRead = populateReadBuffer(b, off, len);
            if (nRead > 0) {
                return nRead;
                /*
                 * 因为自上次填充缓冲区以来, 可能已经到达更多字节, 此时, 它是一个执行非阻塞读取的选项.
                 * 但是，如果读取返回流结束，则正确处理该情况会增加复杂性. 因此, 在这一刻, 是为了简单.
                 */
            }

            // 尽可能地填充读缓冲区.
            nRead = fillReadBuffer(block);

            // 使用刚读取的数据, 尽可能多地填充剩余的字节数组
            if (nRead > 0) {
                socketBufferHandler.configureReadBufferForRead();
                nRead = Math.min(nRead, len);
                socketBufferHandler.getReadBuffer().get(b, off, nRead);
            }
            return nRead;
        }


        @Override
        public int read(boolean block, ByteBuffer to) throws IOException {
            int nRead = populateReadBuffer(to);
            if (nRead > 0) {
                return nRead;
                /*
                 * 因为自上次填充缓冲区以来, 可能已经到达更多字节, 此时, 它是一个执行非阻塞读取的选项.
                 * 但是，如果读取返回流结束，则正确处理该情况会增加复杂性. 因此, 在这一刻, 是为了简单.
                 */
            }

            // 套接字读缓冲区容量是socket.appReadBufSize
            int limit = socketBufferHandler.getReadBuffer().capacity();
            if (to.isDirect() && to.remaining() >= limit) {
                to.limit(to.position() + limit);
                nRead = fillReadBuffer(block, to);
            } else {
                // 尽可能地填充读缓冲区.
                nRead = fillReadBuffer(block);

                // 使用刚读取的数据, 尽可能多地填充剩余的字节数组
                if (nRead > 0) {
                    nRead = populateReadBuffer(to);
                }
            }
            return nRead;
        }


        private int fillReadBuffer(boolean block) throws IOException {
            socketBufferHandler.configureReadBufferForWrite();
            return fillReadBuffer(block, socketBufferHandler.getReadBuffer());
        }


        private int fillReadBuffer(boolean block, ByteBuffer to) throws IOException {
            if (closed) {
                throw new IOException(sm.getString("socket.apr.closed", getSocket()));
            }

            Lock readLock = getBlockingStatusReadLock();
            WriteLock writeLock = getBlockingStatusWriteLock();

            boolean readDone = false;
            int result = 0;
            readLock.lock();
            try {
                if (getBlockingStatus() == block) {
                    if (block) {
                        Socket.timeoutSet(getSocket().longValue(), getReadTimeout() * 1000);
                    }
                    result = Socket.recvb(getSocket().longValue(), to, to.position(),
                            to.remaining());
                    readDone = true;
                }
            } finally {
                readLock.unlock();
            }

            if (!readDone) {
                writeLock.lock();
                try {
                    // 设置此套接字的当前设置
                    setBlockingStatus(block);
                    if (block) {
                        Socket.timeoutSet(getSocket().longValue(), getReadTimeout() * 1000);
                    } else {
                        Socket.timeoutSet(getSocket().longValue(), 0);
                    }
                    // Downgrade the lock
                    readLock.lock();
                    try {
                        writeLock.unlock();
                        result = Socket.recvb(getSocket().longValue(), to, to.position(),
                                to.remaining());
                    } finally {
                        readLock.unlock();
                    }
                } finally {
                    // 应该已经在上面发布但可能没有在某些异常路径上
                    if (writeLock.isHeldByCurrentThread()) {
                        writeLock.unlock();
                    }
                }
            }

            if (result > 0) {
                to.position(to.position() + result);
                return result;
            } else if (result == 0 || -result == Status.EAGAIN) {
                return 0;
            } else if ((-result) == Status.ETIMEDOUT || (-result) == Status.TIMEUP) {
                if (block) {
                    throw new SocketTimeoutException(sm.getString("iib.readtimeout"));
                } else {
                    // 当轮询器没有发信号通知有读取数据时, 尝试从套接字读取似乎表现得像OSX上的短暂超时阻塞读取, 而不是非阻塞读取.
                    // 如果没有读取数据, 将结果超时视为未返回数据的非阻塞读取.
                    return 0;
                }
            } else if (-result == Status.APR_EOF) {
                return -1;
            } else if ((OS.IS_WIN32 || OS.IS_WIN64) &&
                    (-result == Status.APR_OS_START_SYSERR + 10053)) {
                // Windows上的10053连接已中止
                throw new EOFException(sm.getString("socket.apr.clientAbort"));
            } else {
                throw new IOException(sm.getString("socket.apr.read.error",
                        Integer.valueOf(-result), getSocket(), this));
            }
        }


        @Override
        public boolean isReadyForRead() throws IOException {
            socketBufferHandler.configureReadBufferForRead();

            if (socketBufferHandler.getReadBuffer().remaining() > 0) {
                return true;
            }

            fillReadBuffer(false);

            boolean isReady = socketBufferHandler.getReadBuffer().position() > 0;
            return isReady;
        }


        @Override
        public void close() {
            getEndpoint().getHandler().release(this);
            synchronized (closedLock) {
                // 如果相同的套接字关闭两次，APR通常会崩溃，因此请确保不会发生这种情况.
                if (closed) {
                    return;
                }
                closed = true;
                if (sslOutputBuffer != null) {
                    ByteBufferUtils.cleanDirectBuffer(sslOutputBuffer);
                }
                ((AprEndpoint) getEndpoint()).getPoller().close(getSocket().longValue());
            }
        }


        @Override
        public boolean isClosed() {
            synchronized (closedLock) {
                return closed;
            }
        }


        @Override
        protected void writeByteBufferBlocking(ByteBuffer from) throws IOException {
            if (from.isDirect()) {
                super.writeByteBufferBlocking(from);
            } else {
                // 套接字写缓冲区的容量是socket.appWriteBufSize
                ByteBuffer writeBuffer = socketBufferHandler.getWriteBuffer();
                int limit = writeBuffer.capacity();
                while (from.remaining() >= limit) {
                    socketBufferHandler.configureWriteBufferForWrite();
                    transfer(from, writeBuffer);
                    doWrite(true);
                }

                if (from.remaining() > 0) {
                    socketBufferHandler.configureWriteBufferForWrite();
                    transfer(from, writeBuffer);
                }
            }
        }


        @Override
        protected boolean writeByteBufferNonBlocking(ByteBuffer from) throws IOException {
            if (from.isDirect()) {
                return super.writeByteBufferNonBlocking(from);
            } else {
                // 套接字写缓冲区的容量是socket.appWriteBufSize
                ByteBuffer writeBuffer = socketBufferHandler.getWriteBuffer();
                int limit = writeBuffer.capacity();
                while (from.remaining() >= limit) {
                    socketBufferHandler.configureWriteBufferForWrite();
                    transfer(from, writeBuffer);
                    int newPosition = writeBuffer.position() + limit;
                    doWrite(false);
                    if (writeBuffer.position() != newPosition) {
                        // 没有在最后的非阻塞写中写入全部数据.
                        // 退出循环.
                        return true;
                    }
                }

                if (from.remaining() > 0) {
                    socketBufferHandler.configureWriteBufferForWrite();
                    transfer(from, writeBuffer);
                }

                return false;
            }
        }


        @Override
        protected void doWrite(boolean block, ByteBuffer from) throws IOException {
            if (closed) {
                throw new IOException(sm.getString("socket.apr.closed", getSocket()));
            }

            Lock readLock = getBlockingStatusReadLock();
            WriteLock writeLock = getBlockingStatusWriteLock();

            readLock.lock();
            try {
                if (getBlockingStatus() == block) {
                    if (block) {
                        Socket.timeoutSet(getSocket().longValue(), getWriteTimeout() * 1000);
                    }
                    doWriteInternal(from);
                    return;
                }
            } finally {
                readLock.unlock();
            }

            writeLock.lock();
            try {
                // 设置此套接字的当前设置
                setBlockingStatus(block);
                if (block) {
                    Socket.timeoutSet(getSocket().longValue(), getWriteTimeout() * 1000);
                } else {
                    Socket.timeoutSet(getSocket().longValue(), 0);
                }

                // 降级锁
                readLock.lock();
                try {
                    writeLock.unlock();
                    doWriteInternal(from);
                } finally {
                    readLock.unlock();
                }
            } finally {
                // 应该已经在上面发布但可能没有在某些异常路径上
                if (writeLock.isHeldByCurrentThread()) {
                    writeLock.unlock();
                }
            }
        }


        private void doWriteInternal(ByteBuffer from) throws IOException {
            int thisTime;

            do {
                thisTime = 0;
                if (getEndpoint().isSSLEnabled()) {
                    if (sslOutputBuffer.remaining() == 0) {
                        // 缓冲区上次写完了
                        sslOutputBuffer.clear();
                        transfer(from, sslOutputBuffer);
                        sslOutputBuffer.flip();
                    } else {
                        // 缓冲区仍有来自先前尝试写入的数据, APR + SSL要求在重新尝试写入时, 传递完全相同的参数
                    }
                    thisTime = Socket.sendb(getSocket().longValue(), sslOutputBuffer,
                            sslOutputBuffer.position(), sslOutputBuffer.limit());
                    if (thisTime > 0) {
                        sslOutputBuffer.position(sslOutputBuffer.position() + thisTime);
                    }
                } else {
                    thisTime = Socket.sendb(getSocket().longValue(), from, from.position(),
                            from.remaining());
                    if (thisTime > 0) {
                        from.position(from.position() + thisTime);
                    }
                }
                if (Status.APR_STATUS_IS_EAGAIN(-thisTime)) {
                    thisTime = 0;
                } else if (-thisTime == Status.APR_EOF) {
                    throw new EOFException(sm.getString("socket.apr.clientAbort"));
                } else if ((OS.IS_WIN32 || OS.IS_WIN64) &&
                        (-thisTime == Status.APR_OS_START_SYSERR + 10053)) {
                    // Windows上的10053连接已中止
                    throw new EOFException(sm.getString("socket.apr.clientAbort"));
                } else if (thisTime < 0) {
                    throw new IOException(sm.getString("socket.apr.write.error",
                            Integer.valueOf(-thisTime), getSocket(), this));
                }
            } while ((thisTime > 0 || getBlockingStatus()) && from.hasRemaining());

            // 如果缓冲区中还有数据，则套接字将被注册以进一步向上写入堆栈.
            // 这是为了确保套接字仅注册一次写入，因为容器和用户代码都可以触发写入注册.
        }


        @Override
        public void registerReadInterest() {
            // 确保已将已关闭的套接字添加到轮询器中
            synchronized (closedLock) {
                if (closed) {
                    return;
                }
                Poller p = ((AprEndpoint) getEndpoint()).getPoller();
                if (p != null) {
                    p.add(getSocket().longValue(), getReadTimeout(), Poll.APR_POLLIN);
                }
            }
        }


        @Override
        public void registerWriteInterest() {
            // 确保已将已关闭的套接字添加到轮询器中
            synchronized (closedLock) {
                if (closed) {
                    return;
                }
                ((AprEndpoint) getEndpoint()).getPoller().add(
                        getSocket().longValue(), getWriteTimeout(), Poll.APR_POLLOUT);
            }
        }


        @Override
        public SendfileDataBase createSendfileData(String filename, long pos, long length) {
            return new SendfileData(filename, pos, length);
        }


        @Override
        public SendfileState processSendfile(SendfileDataBase sendfileData) {
            ((SendfileData) sendfileData).socket = getSocket().longValue();
            return ((AprEndpoint) getEndpoint()).getSendfile().add((SendfileData) sendfileData);
        }


        @Override
        protected void populateRemoteAddr() {
            if (closed) {
                return;
            }
            try {
                long socket = getSocket().longValue();
                long sa = Address.get(Socket.APR_REMOTE, socket);
                remoteAddr = Address.getip(sa);
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noRemoteAddr", getSocket()), e);
            }
        }


        @Override
        protected void populateRemoteHost() {
            if (closed) {
                return;
            }
            try {
                long socket = getSocket().longValue();
                long sa = Address.get(Socket.APR_REMOTE, socket);
                remoteHost = Address.getnameinfo(sa, 0);
                if (remoteAddr == null) {
                    remoteAddr = Address.getip(sa);
                }
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noRemoteHost", getSocket()), e);
            }
        }


        @Override
        protected void populateRemotePort() {
            if (closed) {
                return;
            }
            try {
                long socket = getSocket().longValue();
                long sa = Address.get(Socket.APR_REMOTE, socket);
                Sockaddr addr = Address.getInfo(sa);
                remotePort = addr.port;
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noRemotePort", getSocket()), e);
            }
        }


        @Override
        protected void populateLocalName() {
            if (closed) {
                return;
            }
            try {
                long socket = getSocket().longValue();
                long sa = Address.get(Socket.APR_LOCAL, socket);
                localName =Address.getnameinfo(sa, 0);
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noLocalName"), e);
            }
        }


        @Override
        protected void populateLocalAddr() {
            if (closed) {
                return;
            }
            try {
                long socket = getSocket().longValue();
                long sa = Address.get(Socket.APR_LOCAL, socket);
                localAddr = Address.getip(sa);
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noLocalAddr"), e);
            }
        }


        @Override
        protected void populateLocalPort() {
            if (closed) {
                return;
            }
            try {
                long socket = getSocket().longValue();
                long sa = Address.get(Socket.APR_LOCAL, socket);
                Sockaddr addr = Address.getInfo(sa);
                localPort = addr.port;
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noLocalPort"), e);
            }
        }


        @Override
        public SSLSupport getSslSupport(String clientCertProvider) {
            if (getEndpoint().isSSLEnabled()) {
                return new  AprSSLSupport(this, clientCertProvider);
            } else {
                return null;
            }
        }


        @Override
        public void doClientAuth(SSLSupport sslSupport) throws IOException {
            long socket = getSocket().longValue();
            // 配置连接以要求证书
            try {
                SSLSocket.setVerify(socket, SSL.SSL_CVERIFY_REQUIRE, -1);
                SSLSocket.renegotiate(socket);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                throw new IOException(sm.getString("socket.sslreneg"), t);
            }
        }


        @Override
        public void setAppReadBufHandler(ApplicationBufferHandler handler) {
            // no-op
        }

        String getSSLInfoS(int id) {
            synchronized (closedLock) {
                if (closed) {
                    return null;
                }
                try {
                    return SSLSocket.getInfoS(getSocket().longValue(), id);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        int getSSLInfoI(int id) {
            synchronized (closedLock) {
                if (closed) {
                    return 0;
                }
                try {
                    return SSLSocket.getInfoI(getSocket().longValue(), id);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        byte[] getSSLInfoB(int id) {
            synchronized (closedLock) {
                if (closed) {
                    return null;
                }
                try {
                    return SSLSocket.getInfoB(getSocket().longValue(), id);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}
