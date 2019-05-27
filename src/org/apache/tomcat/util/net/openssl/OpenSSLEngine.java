package org.apache.tomcat.util.net.openssl;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.net.ssl.SSLSessionContext;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.Buffer;
import org.apache.tomcat.jni.Pool;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.util.buf.ByteBufferUtils;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.net.SSLUtil;
import org.apache.tomcat.util.net.openssl.ciphers.OpenSSLCipherConfigurationParser;
import org.apache.tomcat.util.res.StringManager;

/**
 * 使用<a href="https://www.openssl.org/docs/crypto/BIO_s_bio.html#EXAMPLE">OpenSSL BIO abstractions</a>实现了{@link SSLEngine}.
 */
public final class OpenSSLEngine extends SSLEngine implements SSLUtil.ProtocolInfo {

    private static final Log logger = LogFactory.getLog(OpenSSLEngine.class);
    private static final StringManager sm = StringManager.getManager(OpenSSLEngine.class);

    private static final Certificate[] EMPTY_CERTIFICATES = new Certificate[0];

    public static final Set<String> AVAILABLE_CIPHER_SUITES;

    static {
        final Set<String> availableCipherSuites = new LinkedHashSet<>(128);
        final long aprPool = Pool.create(0);
        try {
            final long sslCtx = SSLContext.make(aprPool, SSL.SSL_PROTOCOL_ALL, SSL.SSL_MODE_SERVER);
            try {
                SSLContext.setOptions(sslCtx, SSL.SSL_OP_ALL);
                SSLContext.setCipherSuite(sslCtx, "ALL");
                final long ssl = SSL.newSSL(sslCtx, true);
                try {
                    for (String c: SSL.getCiphers(ssl)) {
                        // 过滤掉错误的输入.
                        if (c == null || c.length() == 0 || availableCipherSuites.contains(c)) {
                            continue;
                        }
                        availableCipherSuites.add(OpenSSLCipherConfigurationParser.openSSLToJsse(c));
                    }
                } finally {
                    SSL.freeSSL(ssl);
                }
            } finally {
                SSLContext.free(sslCtx);
            }
        } catch (Exception e) {
            logger.warn(sm.getString("engine.ciphersFailure"), e);
        } finally {
            Pool.destroy(aprPool);
        }
        AVAILABLE_CIPHER_SUITES = Collections.unmodifiableSet(availableCipherSuites);
    }

    private static final int MAX_PLAINTEXT_LENGTH = 16 * 1024; // 2^14
    private static final int MAX_COMPRESSED_LENGTH = MAX_PLAINTEXT_LENGTH + 1024;
    private static final int MAX_CIPHERTEXT_LENGTH = MAX_COMPRESSED_LENGTH + 1024;

    // 协议
    static final int VERIFY_DEPTH = 10;

    private static final String[] IMPLEMENTED_PROTOCOLS = {
        Constants.SSL_PROTO_SSLv2Hello,
        Constants.SSL_PROTO_SSLv2,
        Constants.SSL_PROTO_SSLv3,
        Constants.SSL_PROTO_TLSv1,
        Constants.SSL_PROTO_TLSv1_1,
        Constants.SSL_PROTO_TLSv1_2
    };
    public static final Set<String> IMPLEMENTED_PROTOCOLS_SET =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(IMPLEMENTED_PROTOCOLS)));

    // Header (5) + Data (2^14) + Compression (1024) + Encryption (1024) + MAC (20) + Padding (256)
    static final int MAX_ENCRYPTED_PACKET_LENGTH = MAX_CIPHERTEXT_LENGTH + 5 + 20 + 256;

    static final int MAX_ENCRYPTION_OVERHEAD_LENGTH = MAX_ENCRYPTED_PACKET_LENGTH - MAX_PLAINTEXT_LENGTH;

    enum ClientAuthMode {
        NONE,
        OPTIONAL,
        REQUIRE,
    }

    private static final String INVALID_CIPHER = "SSL_NULL_WITH_NULL_NULL";

    private static final long EMPTY_ADDR = Buffer.address(ByteBuffer.allocate(0));

    // OpenSSL状态
    private long ssl;
    private long networkBIO;

    /**
     * 0 - 不接受, 1 - 隐含接受, 通过 wrap()/unwrap(), 2 - 通过beginHandshake()调用显式接受
     */
    private int accepted;
    private boolean handshakeFinished;
    private int currentHandshake;
    private boolean receivedShutdown;
    private volatile boolean destroyed;

    // 使用无效的cipherSuite, 直到握手完成
    private volatile String cipher;
    private volatile String applicationProtocol;

    private volatile Certificate[] peerCerts;
    @Deprecated
    private volatile javax.security.cert.X509Certificate[] x509PeerCerts;
    private volatile ClientAuthMode clientAuth = ClientAuthMode.NONE;

    // SSL引擎状态变量
    private boolean isInboundDone;
    private boolean isOutboundDone;
    private boolean engineClosed;
    private boolean sendHandshakeError = false;

    private final boolean clientMode;
    private final String fallbackApplicationProtocol;
    private final OpenSSLSessionContext sessionContext;
    private final boolean alpn;
    private final boolean initialized;

    private String selectedProtocol = null;

    private final OpenSSLSession session;

    /**
     * @param sslCtx  OpenSSL {@code SSL_CTX}对象
     * @param fallbackApplicationProtocol 后备应用程序协议
     * @param clientMode {@code true}如果这是用于客户端, 否则{@code false}
     * @param sessionContext 这个{@link SSLEngine}所属的{@link OpenSslSessionContext}.
     * @param alpn {@code true}如果应该使用alpn, 否则{@code false}
     */
    OpenSSLEngine(long sslCtx, String fallbackApplicationProtocol,
            boolean clientMode, OpenSSLSessionContext sessionContext,
            boolean alpn) {
        this(sslCtx, fallbackApplicationProtocol, clientMode, sessionContext,
             alpn, false);
    }

    /**
     * @param sslCtx OpenSSL {@code SSL_CTX}对象
     * @param fallbackApplicationProtocol 后备应用程序协议
     * @param clientMode {@code true}如果这是用于客户端, 否则{@code false}
     * @param sessionContext 这个{@link SSLEngine}所属的{@link OpenSslSessionContext}.
     * @param alpn {@code true}如果应该使用alpn, 否则{@code false}
     * @param initialized {@code true}如果此实例获得其协议, 来自{@code SSL_CTX} {@code sslCtx}的密码和客户端验证
     */
    OpenSSLEngine(long sslCtx, String fallbackApplicationProtocol,
            boolean clientMode, OpenSSLSessionContext sessionContext, boolean alpn,
            boolean initialized) {
        if (sslCtx == 0) {
            throw new IllegalArgumentException(sm.getString("engine.noSSLContext"));
        }
        session = new OpenSSLSession();
        destroyed = true;
        ssl = SSL.newSSL(sslCtx, !clientMode);
        networkBIO = SSL.makeNetworkBIO(ssl);
        destroyed = false;
        this.fallbackApplicationProtocol = fallbackApplicationProtocol;
        this.clientMode = clientMode;
        this.sessionContext = sessionContext;
        this.alpn = alpn;
        this.initialized = initialized;
    }

    @Override
    public String getNegotiatedProtocol() {
        return selectedProtocol;
    }

    /**
     * 销毁这个引擎.
     */
    public synchronized void shutdown() {
        if (!destroyed) {
            destroyed = true;
            SSL.freeBIO(networkBIO);
            SSL.freeSSL(ssl);
            ssl = networkBIO = 0;

            // 内部错误可能导致停机而不标记发动机关闭
            isInboundDone = isOutboundDone = engineClosed = true;
        }
    }

    /**
     * 将纯文本数据写入OpenSSL内部BIO
     *
     * 使用src.remaining == 0调用此函数是未定义的.
     */
    private int writePlaintextData(final ByteBuffer src) {
        final int pos = src.position();
        final int limit = src.limit();
        final int len = Math.min(limit - pos, MAX_PLAINTEXT_LENGTH);
        final int sslWrote;

        if (src.isDirect()) {
            final long addr = Buffer.address(src) + pos;
            sslWrote = SSL.writeToSSL(ssl, addr, len);
            if (sslWrote >= 0) {
                src.position(pos + sslWrote);
                return sslWrote;
            }
        } else {
            ByteBuffer buf = ByteBuffer.allocateDirect(len);
            try {
                final long addr = memoryAddress(buf);

                src.limit(pos + len);

                buf.put(src);
                src.limit(limit);

                sslWrote = SSL.writeToSSL(ssl, addr, len);
                if (sslWrote >= 0) {
                    src.position(pos + sslWrote);
                    return sslWrote;
                } else {
                    src.position(pos);
                }
            } finally {
                buf.clear();
                ByteBufferUtils.cleanDirectBuffer(buf);
            }
        }

        throw new IllegalStateException(
                sm.getString("engine.writeToSSLFailed", Integer.toString(sslWrote)));
    }

    /**
     * 将加密数据写入OpenSSL网络BIO.
     */
    private int writeEncryptedData(final ByteBuffer src) {
        final int pos = src.position();
        final int len = src.remaining();
        if (src.isDirect()) {
            final long addr = Buffer.address(src) + pos;
            final int netWrote = SSL.writeToBIO(networkBIO, addr, len);
            if (netWrote >= 0) {
                src.position(pos + netWrote);
                return netWrote;
            }
        } else {
            ByteBuffer buf = ByteBuffer.allocateDirect(len);
            try {
                final long addr = memoryAddress(buf);

                buf.put(src);

                final int netWrote = SSL.writeToBIO(networkBIO, addr, len);
                if (netWrote >= 0) {
                    src.position(pos + netWrote);
                    return netWrote;
                } else {
                    src.position(pos);
                }
            } finally {
                buf.clear();
                ByteBufferUtils.cleanDirectBuffer(buf);
            }
        }

        return -1;
    }

    /**
     * 从OpenSSL内部BIO读取纯文本数据
     */
    private int readPlaintextData(final ByteBuffer dst) {
        if (dst.isDirect()) {
            final int pos = dst.position();
            final long addr = Buffer.address(dst) + pos;
            final int len = dst.limit() - pos;
            final int sslRead = SSL.readFromSSL(ssl, addr, len);
            if (sslRead > 0) {
                dst.position(pos + sslRead);
                return sslRead;
            }
        } else {
            final int pos = dst.position();
            final int limit = dst.limit();
            final int len = Math.min(MAX_ENCRYPTED_PACKET_LENGTH, limit - pos);
            final ByteBuffer buf = ByteBuffer.allocateDirect(len);
            try {
                final long addr = memoryAddress(buf);

                final int sslRead = SSL.readFromSSL(ssl, addr, len);
                if (sslRead > 0) {
                    buf.limit(sslRead);
                    dst.limit(pos + sslRead);
                    dst.put(buf);
                    dst.limit(limit);
                    return sslRead;
                }
            } finally {
                buf.clear();
                ByteBufferUtils.cleanDirectBuffer(buf);
            }
        }

        return 0;
    }

    /**
     * 从OpenSSL网络BIO读取加密数据
     */
    private int readEncryptedData(final ByteBuffer dst, final int pending) {
        if (dst.isDirect() && dst.remaining() >= pending) {
            final int pos = dst.position();
            final long addr = Buffer.address(dst) + pos;
            final int bioRead = SSL.readFromBIO(networkBIO, addr, pending);
            if (bioRead > 0) {
                dst.position(pos + bioRead);
                return bioRead;
            }
        } else {
            final ByteBuffer buf = ByteBuffer.allocateDirect(pending);
            try {
                final long addr = memoryAddress(buf);

                final int bioRead = SSL.readFromBIO(networkBIO, addr, pending);
                if (bioRead > 0) {
                    buf.limit(bioRead);
                    int oldLimit = dst.limit();
                    dst.limit(dst.position() + bioRead);
                    dst.put(buf);
                    dst.limit(oldLimit);
                    return bioRead;
                }
            } finally {
                buf.clear();
                ByteBufferUtils.cleanDirectBuffer(buf);
            }
        }

        return 0;
    }

    @Override
    public synchronized SSLEngineResult wrap(final ByteBuffer[] srcs, final int offset, final int length, final ByteBuffer dst) throws SSLException {

        // 检查以确保引擎尚未关闭
        if (destroyed) {
            return new SSLEngineResult(SSLEngineResult.Status.CLOSED, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, 0, 0);
        }

        // Throw required runtime exceptions
        if (srcs == null || dst == null) {
            throw new IllegalArgumentException(sm.getString("engine.nullBuffer"));
        }
        if (offset >= srcs.length || offset + length > srcs.length) {
            throw new IndexOutOfBoundsException(sm.getString("engine.invalidBufferArray",
                    Integer.toString(offset), Integer.toString(length),
                    Integer.toString(srcs.length)));
        }
        if (dst.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }

        // 准备OpenSSL以在服务器模式下工作并接收握手
        if (accepted == 0) {
            beginHandshakeImplicitly();
        }

        // 在握手或close_notify阶段, 检查是否调用了wrap而不考虑握手状态.
        SSLEngineResult.HandshakeStatus handshakeStatus = getHandshakeStatus();

        if ((!handshakeFinished || engineClosed) && handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
            return new SSLEngineResult(getEngineStatus(), SSLEngineResult.HandshakeStatus.NEED_UNWRAP, 0, 0);
        }

        int bytesProduced = 0;
        int pendingNet;

        // 检查网络BIO中的待处理数据
        pendingNet = SSL.pendingWrittenBytesInBIO(networkBIO);
        if (pendingNet > 0) {
            // 是否有足够的空间来编写加密数据?
            int capacity = dst.remaining();
            if (capacity < pendingNet) {
                return new SSLEngineResult(SSLEngineResult.Status.BUFFER_OVERFLOW, handshakeStatus, 0, 0);
            }

            // 将待处理数据从网络BIO写入dst缓冲区
            try {
                bytesProduced = readEncryptedData(dst, pendingNet);
            } catch (Exception e) {
                throw new SSLException(e);
            }

            // 如果设置了isOuboundDone, 那么来自网络BIO的数据就是close_notify消息 -- 不需要等待收到对等方的close_notify消息 -- 关机.
            if (isOutboundDone) {
                shutdown();
            }

            return new SSLEngineResult(getEngineStatus(), getHandshakeStatus(), 0, bytesProduced);
        }

        // 网络BIO中没有待处理的数据 -- 加密任何应用程序数据
        int bytesConsumed = 0;
        int endOffset = offset + length;
        for (int i = offset; i < endOffset; ++i) {
            final ByteBuffer src = srcs[i];
            if (src == null) {
                throw new IllegalArgumentException(sm.getString("engine.nullBufferInArray"));
            }
            while (src.hasRemaining()) {

                // 将纯文本应用程序数据写入SSL引擎
                try {
                    bytesConsumed += writePlaintextData(src);
                } catch (Exception e) {
                    throw new SSLException(e);
                }

                // 检查引擎是否将数据写入网络BIO
                pendingNet = SSL.pendingWrittenBytesInBIO(networkBIO);
                if (pendingNet > 0) {
                    // 是否有足够的空间来编写加密数据?
                    int capacity = dst.remaining();
                    if (capacity < pendingNet) {
                        return new SSLEngineResult(
                                SSLEngineResult.Status.BUFFER_OVERFLOW, getHandshakeStatus(), bytesConsumed, bytesProduced);
                    }

                    // 将待处理数据从网络BIO写入dst缓冲区
                    try {
                        bytesProduced += readEncryptedData(dst, pendingNet);
                    } catch (Exception e) {
                        throw new SSLException(e);
                    }

                    return new SSLEngineResult(getEngineStatus(), getHandshakeStatus(), bytesConsumed, bytesProduced);
                }
            }
        }
        return new SSLEngineResult(getEngineStatus(), getHandshakeStatus(), bytesConsumed, bytesProduced);
    }

    @Override
    public synchronized SSLEngineResult unwrap(final ByteBuffer src, final ByteBuffer[] dsts, final int offset, final int length) throws SSLException {
        // 检查以确保引擎尚未关闭
        if (destroyed) {
            return new SSLEngineResult(SSLEngineResult.Status.CLOSED, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, 0, 0);
        }

        // 抛出所需的运行时异常
        if (src == null || dsts == null) {
            throw new IllegalArgumentException(sm.getString("engine.nullBuffer"));
        }
        if (offset >= dsts.length || offset + length > dsts.length) {
            throw new IndexOutOfBoundsException(sm.getString("engine.invalidBufferArray",
                    Integer.toString(offset), Integer.toString(length),
                    Integer.toString(dsts.length)));
        }
        int capacity = 0;
        final int endOffset = offset + length;
        for (int i = offset; i < endOffset; i++) {
            ByteBuffer dst = dsts[i];
            if (dst == null) {
                throw new IllegalArgumentException(sm.getString("engine.nullBufferInArray"));
            }
            if (dst.isReadOnly()) {
                throw new ReadOnlyBufferException();
            }
            capacity += dst.remaining();
        }

        // 准备OpenSSL以在服务器模式下工作并接收握手
        if (accepted == 0) {
            beginHandshakeImplicitly();
        }

        // 在握手或close_notify阶段, 检查是否调用unwrap, 而不考虑握手状态.
        SSLEngineResult.HandshakeStatus handshakeStatus = getHandshakeStatus();
        if ((!handshakeFinished || engineClosed) && handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            return new SSLEngineResult(getEngineStatus(), SSLEngineResult.HandshakeStatus.NEED_WRAP, 0, 0);
        }

        int len = src.remaining();

        // 防止协议溢出攻击向量
        if (len > MAX_ENCRYPTED_PACKET_LENGTH) {
            isInboundDone = true;
            isOutboundDone = true;
            engineClosed = true;
            shutdown();
            throw new SSLException(sm.getString("engine.oversizedPacket"));
        }

        // 将加密数据写入网络BIO
        int written = -1;
        try {
            written = writeEncryptedData(src);
        } catch (Exception e) {
            throw new SSLException(e);
        }
        // 如果没有写入，OpenSSL可以向这些调用返回0或-1
        if (written < 0) {
            written = 0;
        }

        // 在完成握手之前，不会有任何应用程序数据
        //
        // 首先检查handshakeFinished以消除额外JNI调用的开销，如果可能的话.
        int pendingApp = pendingReadableBytesInSSL();
        if (!handshakeFinished) {
            pendingApp = 0;
        }
        int bytesProduced = 0;
        int idx = offset;
        // 是否有足够的空间来写入解密数据?
        if (capacity < pendingApp) {
            return new SSLEngineResult(SSLEngineResult.Status.BUFFER_OVERFLOW, getHandshakeStatus(), written, 0);
        }

        while (pendingApp > 0) {
            // 将解密数据写入dsts缓冲区
            while (idx < endOffset) {
                ByteBuffer dst = dsts[idx];
                if (!dst.hasRemaining()) {
                    idx++;
                    continue;
                }

                if (pendingApp <= 0) {
                    break;
                }

                int bytesRead;
                try {
                    bytesRead = readPlaintextData(dst);
                } catch (Exception e) {
                    throw new SSLException(e);
                }

                if (bytesRead == 0) {
                    break;
                }

                bytesProduced += bytesRead;
                pendingApp -= bytesRead;
                capacity -= bytesRead;

                if (!dst.hasRemaining()) {
                    idx++;
                }
            }
            if (capacity == 0) {
                break;
            } else if (pendingApp == 0) {
                pendingApp = pendingReadableBytesInSSL();
            }
        }

        // 检查是否收到来自对等方的close_notify消息
        if (!receivedShutdown && (SSL.getShutdown(ssl) & SSL.SSL_RECEIVED_SHUTDOWN) == SSL.SSL_RECEIVED_SHUTDOWN) {
            receivedShutdown = true;
            closeOutbound();
            closeInbound();
        }
        if (bytesProduced == 0 && written == 0) {
            return new SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW, getHandshakeStatus(), 0, 0);
        } else {
            return new SSLEngineResult(getEngineStatus(), getHandshakeStatus(), written, bytesProduced);
        }
    }

    private int pendingReadableBytesInSSL()
            throws SSLException {
        // NOTE: 在调用pendingReadableBytesInSSL之前调用假读取是必要的，因为如果OpenSSL尚未启动当前TLS记录，SSL_pending将返回0
        // See https://www.openssl.org/docs/manmaster/ssl/SSL_pending.html
        int lastPrimingReadResult = SSL.readFromSSL(ssl, EMPTY_ADDR, 0); // priming read
        // 检查SSL_read是否返回<= 0. 在这种情况下，需要检查错误，看看它是否是致命的.
        if (lastPrimingReadResult <= 0) {
            checkLastError();
        }
        return SSL.pendingReadableBytesInSSL(ssl);
    }

    @Override
    public Runnable getDelegatedTask() {
        // 目前，不委托SSL计算任务
        return null;
    }

    @Override
    public synchronized void closeInbound() throws SSLException {
        if (isInboundDone) {
            return;
        }

        isInboundDone = true;
        engineClosed = true;

        shutdown();

        if (accepted != 0 && !receivedShutdown) {
            throw new SSLException(sm.getString("engine.inboundClose"));
        }
    }

    @Override
    public synchronized boolean isInboundDone() {
        return isInboundDone || engineClosed;
    }

    @Override
    public synchronized void closeOutbound() {
        if (isOutboundDone) {
            return;
        }

        isOutboundDone = true;
        engineClosed = true;

        if (accepted != 0 && !destroyed) {
            int mode = SSL.getShutdown(ssl);
            if ((mode & SSL.SSL_SENT_SHUTDOWN) != SSL.SSL_SENT_SHUTDOWN) {
                SSL.shutdownSSL(ssl);
            }
        } else {
            // 引擎在初始握手前关闭
            shutdown();
        }
    }

    @Override
    public synchronized boolean isOutboundDone() {
        return isOutboundDone;
    }

    @Override
    public String[] getSupportedCipherSuites() {
        Set<String> availableCipherSuites = AVAILABLE_CIPHER_SUITES;
        return availableCipherSuites.toArray(new String[availableCipherSuites.size()]);
    }

    @Override
    public synchronized String[] getEnabledCipherSuites() {
        if (destroyed) {
            return new String[0];
        }
        String[] enabled = SSL.getCiphers(ssl);
        if (enabled == null) {
            return new String[0];
        } else {
            for (int i = 0; i < enabled.length; i++) {
                String mapped = OpenSSLCipherConfigurationParser.openSSLToJsse(enabled[i]);
                if (mapped != null) {
                    enabled[i] = mapped;
                }
            }
            return enabled;
        }
    }

    @Override
    public synchronized void setEnabledCipherSuites(String[] cipherSuites) {
        if (initialized) {
            return;
        }
        if (cipherSuites == null) {
            throw new IllegalArgumentException(sm.getString("engine.nullCipherSuite"));
        }
        if (destroyed) {
            return;
        }
        final StringBuilder buf = new StringBuilder();
        for (String cipherSuite : cipherSuites) {
            if (cipherSuite == null) {
                break;
            }
            String converted = OpenSSLCipherConfigurationParser.jsseToOpenSSL(cipherSuite);
            if (!AVAILABLE_CIPHER_SUITES.contains(cipherSuite)) {
                logger.debug(sm.getString("engine.unsupportedCipher", cipherSuite, converted));
            }
            if (converted != null) {
                cipherSuite = converted;
            }

            buf.append(cipherSuite);
            buf.append(':');
        }

        if (buf.length() == 0) {
            throw new IllegalArgumentException(sm.getString("engine.emptyCipherSuite"));
        }
        buf.setLength(buf.length() - 1);

        final String cipherSuiteSpec = buf.toString();
        try {
            SSL.setCipherSuites(ssl, cipherSuiteSpec);
        } catch (Exception e) {
            throw new IllegalStateException(sm.getString("engine.failedCipherSuite", cipherSuiteSpec), e);
        }
    }

    @Override
    public String[] getSupportedProtocols() {
        return IMPLEMENTED_PROTOCOLS.clone();
    }

    @Override
    public synchronized String[] getEnabledProtocols() {
        if (destroyed) {
            return new String[0];
        }
        List<String> enabled = new ArrayList<>();
        // 似乎无法在OpenSSL中显式禁用SSLv2Hello，因此始终启用它
        enabled.add(Constants.SSL_PROTO_SSLv2Hello);
        int opts = SSL.getOptions(ssl);
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
        int size = enabled.size();
        if (size == 0) {
            return new String[0];
        } else {
            return enabled.toArray(new String[size]);
        }
    }

    @Override
    public synchronized void setEnabledProtocols(String[] protocols) {
        if (initialized) {
            return;
        }
        if (protocols == null) {
            // 这从API文档中是正确的
            throw new IllegalArgumentException();
        }
        if (destroyed) {
            return;
        }
        boolean sslv2 = false;
        boolean sslv3 = false;
        boolean tlsv1 = false;
        boolean tlsv1_1 = false;
        boolean tlsv1_2 = false;
        for (String p : protocols) {
            if (!IMPLEMENTED_PROTOCOLS_SET.contains(p)) {
                throw new IllegalArgumentException(sm.getString("engine.unsupportedProtocol", p));
            }
            if (p.equals(Constants.SSL_PROTO_SSLv2)) {
                sslv2 = true;
            } else if (p.equals(Constants.SSL_PROTO_SSLv3)) {
                sslv3 = true;
            } else if (p.equals(Constants.SSL_PROTO_TLSv1)) {
                tlsv1 = true;
            } else if (p.equals(Constants.SSL_PROTO_TLSv1_1)) {
                tlsv1_1 = true;
            } else if (p.equals(Constants.SSL_PROTO_TLSv1_2)) {
                tlsv1_2 = true;
            }
        }
        // 启用所有，然后禁用不想要的
        SSL.setOptions(ssl, SSL.SSL_OP_ALL);

        if (!sslv2) {
            SSL.setOptions(ssl, SSL.SSL_OP_NO_SSLv2);
        }
        if (!sslv3) {
            SSL.setOptions(ssl, SSL.SSL_OP_NO_SSLv3);
        }
        if (!tlsv1) {
            SSL.setOptions(ssl, SSL.SSL_OP_NO_TLSv1);
        }
        if (!tlsv1_1) {
            SSL.setOptions(ssl, SSL.SSL_OP_NO_TLSv1_1);
        }
        if (!tlsv1_2) {
            SSL.setOptions(ssl, SSL.SSL_OP_NO_TLSv1_2);
        }
    }

    @Override
    public SSLSession getSession() {
        return session;
    }

    @Override
    public synchronized void beginHandshake() throws SSLException {
        if (engineClosed || destroyed) {
            throw new SSLException(sm.getString("engine.engineClosed"));
        }
        switch (accepted) {
            case 0:
                handshake();
                accepted = 2;
                break;
            case 1:
                // 用户没有通过自己调用此方法来启动握手, 但是已经通过wrap()或unwrap()隐式启动了握手.
                // 因为这是用户第一次调用此方法, 跑出异常是不公平的.  从用户的角度来看, 从未要求重新协商.

                accepted = 2; // 下次用户调用此方法时, 应该抛出异常.
                break;
            case 2:
                renegotiate();
                break;
            default:
                throw new Error();
        }
    }

    private void beginHandshakeImplicitly() throws SSLException {
        handshake();
        accepted = 1;
    }

    private void handshake() throws SSLException {
        currentHandshake = SSL.getHandshakeCount(ssl);
        int code = SSL.doHandshake(ssl);
        if (code <= 0) {
            checkLastError();
        } else {
            if (alpn) {
                selectedProtocol = SSL.getAlpnSelected(ssl);
                if (selectedProtocol == null) {
                    selectedProtocol = SSL.getNextProtoNegotiated(ssl);
                }
            }
            session.lastAccessedTime = System.currentTimeMillis();
            // 如果SSL_do_handshake返回> 0, 意味着握手已经结束. 意味着可以直接更新handshakeFinished, 从而消除对SSL.isInInit(...)的不必要的调用
            handshakeFinished = true;
        }
    }

    private synchronized void renegotiate() throws SSLException {
        int code = SSL.renegotiate(ssl);
        if (code <= 0) {
            checkLastError();
        }
        handshakeFinished = false;
        peerCerts = null;
        x509PeerCerts = null;
        currentHandshake = SSL.getHandshakeCount(ssl);
        int code2 = SSL.doHandshake(ssl);
        if (code2 <= 0) {
            checkLastError();
        }
    }

    private void checkLastError() throws SSLException {
        long error = SSL.getLastErrorNumber();
        if (error != SSL.SSL_ERROR_NONE) {
            String err = SSL.getErrorString(error);
            if (logger.isDebugEnabled()) {
                logger.debug(sm.getString("engine.openSSLError", Long.toString(error), err));
            }
            // 握手期间可能会发生许多错误，需要报告
            if (!handshakeFinished) {
                sendHandshakeError = true;
            } else {
                throw new SSLException(err);
            }
        }
    }

    private static long memoryAddress(ByteBuffer buf) {
        return Buffer.address(buf);
    }

    private SSLEngineResult.Status getEngineStatus() {
        return engineClosed ? SSLEngineResult.Status.CLOSED : SSLEngineResult.Status.OK;
    }

    @Override
    public synchronized SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        if (accepted == 0 || destroyed) {
            return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
        }

        // 检查是否处于初始握手阶段
        if (!handshakeFinished) {

            // 网络BIO中有待处理的数据 -- 调用 wrap
            if (sendHandshakeError || SSL.pendingWrittenBytesInBIO(networkBIO) != 0) {
                if (sendHandshakeError) {
                    // 最后一次包装后, 认为它将会完成
                    sendHandshakeError = false;
                    currentHandshake++;
                }
                return SSLEngineResult.HandshakeStatus.NEED_WRAP;
            }

            // 没有待发送的数据发送给对等方
            // 检查是否完成了握手
            int handshakeCount = SSL.getHandshakeCount(ssl);
            if (handshakeCount != currentHandshake) {
                if (alpn) {
                    selectedProtocol = SSL.getAlpnSelected(ssl);
                    if (selectedProtocol == null) {
                        selectedProtocol = SSL.getNextProtoNegotiated(ssl);
                    }
                }
                session.lastAccessedTime = System.currentTimeMillis();
                handshakeFinished = true;
                return SSLEngineResult.HandshakeStatus.FINISHED;
            }

            // 没有待处理的数据, 但仍然握手
            // 必须等待对等方发送更多数据
            return SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
        }

        // 检查是否处于关机阶段
        if (engineClosed) {
            // 等待发送close_notify消息
            if (SSL.pendingWrittenBytesInBIO(networkBIO) != 0) {
                return SSLEngineResult.HandshakeStatus.NEED_WRAP;
            }

            // 必须等待接收close_notify消息
            return SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
        }

        return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
    }

    @Override
    public void setUseClientMode(boolean clientMode) {
        if (clientMode != this.clientMode) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean getUseClientMode() {
        return clientMode;
    }

    @Override
    public void setNeedClientAuth(boolean b) {
        setClientAuth(b ? ClientAuthMode.REQUIRE : ClientAuthMode.NONE);
    }

    @Override
    public boolean getNeedClientAuth() {
        return clientAuth == ClientAuthMode.REQUIRE;
    }

    @Override
    public void setWantClientAuth(boolean b) {
        setClientAuth(b ? ClientAuthMode.OPTIONAL : ClientAuthMode.NONE);
    }

    @Override
    public boolean getWantClientAuth() {
        return clientAuth == ClientAuthMode.OPTIONAL;
    }

    private void setClientAuth(ClientAuthMode mode) {
        if (clientMode) {
            return;
        }
        synchronized (this) {
            if (clientAuth == mode) {
                // 如果模式相同，则无需发出任何JNI调用
                return;
            }
            switch (mode) {
                case NONE:
                    SSL.setVerify(ssl, SSL.SSL_CVERIFY_NONE, VERIFY_DEPTH);
                    break;
                case REQUIRE:
                    SSL.setVerify(ssl, SSL.SSL_CVERIFY_REQUIRE, VERIFY_DEPTH);
                    break;
                case OPTIONAL:
                    SSL.setVerify(ssl, SSL.SSL_CVERIFY_OPTIONAL, VERIFY_DEPTH);
                    break;
            }
            clientAuth = mode;
        }
    }

    @Override
    public void setEnableSessionCreation(boolean b) {
        if (b) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean getEnableSessionCreation() {
        return false;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        // 由于用户可能已创建OpenSslEngine而未使用它，因此请调用shutdown.
        shutdown();
    }

    private class OpenSSLSession implements SSLSession {

        // 由于内存原因延迟初始化
        private Map<String, Object> values;

        // 上次访问时间
        private long lastAccessedTime = -1;

        @Override
        public byte[] getId() {
            byte[] id;
            synchronized (OpenSSLEngine.this) {
                if (destroyed) {
                    throw new IllegalStateException(sm.getString("engine.noSession"));
                }
                id = SSL.getSessionId(ssl);
            }

            return id;
        }

        @Override
        public SSLSessionContext getSessionContext() {
            return sessionContext;
        }

        @Override
        public long getCreationTime() {
            // 需要乘以1000，因为OpenSSL使用秒，但我们需要毫秒.
            long creationTime = 0;
            synchronized (OpenSSLEngine.this) {
                if (destroyed) {
                    throw new IllegalStateException(sm.getString("engine.noSession"));
                }
                creationTime = SSL.getTime(ssl);
            }
            return creationTime * 1000L;
        }

        @Override
        public long getLastAccessedTime() {
            return (lastAccessedTime > 0) ? lastAccessedTime : getCreationTime();
        }

        @Override
        public void invalidate() {
            // NOOP
        }

        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public void putValue(String name, Object value) {
            if (name == null) {
                throw new IllegalArgumentException(sm.getString("engine.nullName"));
            }
            if (value == null) {
                throw new IllegalArgumentException(sm.getString("engine.nullValue"));
            }
            Map<String, Object> values = this.values;
            if (values == null) {
                // 使用2的大小可以减小内存开销
                values = this.values = new HashMap<>(2);
            }
            Object old = values.put(name, value);
            if (value instanceof SSLSessionBindingListener) {
                ((SSLSessionBindingListener) value).valueBound(new SSLSessionBindingEvent(this, name));
            }
            notifyUnbound(old, name);
        }

        @Override
        public Object getValue(String name) {
            if (name == null) {
                throw new IllegalArgumentException(sm.getString("engine.nullName"));
            }
            if (values == null) {
                return null;
            }
            return values.get(name);
        }

        @Override
        public void removeValue(String name) {
            if (name == null) {
                throw new IllegalArgumentException(sm.getString("engine.nullName"));
            }
            Map<String, Object> values = this.values;
            if (values == null) {
                return;
            }
            Object old = values.remove(name);
            notifyUnbound(old, name);
        }

        @Override
        public String[] getValueNames() {
            Map<String, Object> values = this.values;
            if (values == null || values.isEmpty()) {
                return new String[0];
            }
            return values.keySet().toArray(new String[values.size()]);
        }

        private void notifyUnbound(Object value, String name) {
            if (value instanceof SSLSessionBindingListener) {
                ((SSLSessionBindingListener) value).valueUnbound(new SSLSessionBindingEvent(this, name));
            }
        }

        @Override
        public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
            // 这些是延迟创建的，以减少内存开销
            Certificate[] c = peerCerts;
            if (c == null) {
                byte[] clientCert;
                byte[][] chain;
                synchronized (OpenSSLEngine.this) {
                    if (destroyed || SSL.isInInit(ssl) != 0) {
                        throw new SSLPeerUnverifiedException(sm.getString("engine.unverifiedPeer"));
                    }
                    chain = SSL.getPeerCertChain(ssl);
                    if (!clientMode) {
                        // 如果在服务器端使用SSL_get_peer_cert_chain(...)将不包括远程对等证书.
                        // 在这种情况下, 使用SSL_get_peer_certificate来获取它, 并在以后将它添加到数组中.
                        clientCert = SSL.getPeerCertificate(ssl);
                    } else {
                        clientCert = null;
                    }
                }
                if (chain == null && clientCert == null) {
                    return null;
                }
                int len = 0;
                if (chain != null) {
                    len += chain.length;
                }

                int i = 0;
                Certificate[] certificates;
                if (clientCert != null) {
                    len++;
                    certificates = new Certificate[len];
                    certificates[i++] = new OpenSSLX509Certificate(clientCert);
                } else {
                    certificates = new Certificate[len];
                }
                if (chain != null) {
                    int a = 0;
                    for (; i < certificates.length; i++) {
                        certificates[i] = new OpenSSLX509Certificate(chain[a++]);
                    }
                }
                c = peerCerts = certificates;
            }
            return c;
        }

        @Override
        public Certificate[] getLocalCertificates() {
            // FIXME (if possible): 在OpenSSL API中不可用
            return EMPTY_CERTIFICATES;
        }

        @Deprecated
        @Override
        public javax.security.cert.X509Certificate[] getPeerCertificateChain()
                throws SSLPeerUnverifiedException {
            // 这些是延迟创建的，以减少内存开销
            javax.security.cert.X509Certificate[] c = x509PeerCerts;
            if (c == null) {
                byte[][] chain;
                synchronized (OpenSSLEngine.this) {
                    if (destroyed || SSL.isInInit(ssl) != 0) {
                        throw new SSLPeerUnverifiedException(sm.getString("engine.unverifiedPeer"));
                    }
                    chain = SSL.getPeerCertChain(ssl);
                }
                if (chain == null) {
                    throw new SSLPeerUnverifiedException(sm.getString("engine.unverifiedPeer"));
                }
                javax.security.cert.X509Certificate[] peerCerts =
                        new javax.security.cert.X509Certificate[chain.length];
                for (int i = 0; i < peerCerts.length; i++) {
                    try {
                        peerCerts[i] = javax.security.cert.X509Certificate.getInstance(chain[i]);
                    } catch (javax.security.cert.CertificateException e) {
                        throw new IllegalStateException(e);
                    }
                }
                c = x509PeerCerts = peerCerts;
            }
            return c;
        }

        @Override
        public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            Certificate[] peer = getPeerCertificates();
            if (peer == null || peer.length == 0) {
                return null;
            }
            return principal(peer);
        }

        @Override
        public Principal getLocalPrincipal() {
            Certificate[] local = getLocalCertificates();
            if (local == null || local.length == 0) {
                return null;
            }
            return principal(local);
        }

        private Principal principal(Certificate[] certs) {
            return ((java.security.cert.X509Certificate) certs[0]).getIssuerX500Principal();
        }

        @Override
        public String getCipherSuite() {
            if (cipher == null) {
                String ciphers;
                synchronized (OpenSSLEngine.this) {
                    if (!handshakeFinished) {
                        return INVALID_CIPHER;
                    }
                    if (destroyed) {
                        return INVALID_CIPHER;
                    }
                    ciphers = SSL.getCipherForSSL(ssl);
                }
                String c = OpenSSLCipherConfigurationParser.openSSLToJsse(ciphers);
                if (c != null) {
                    cipher = c;
                }
            }
            return cipher;
        }

        @Override
        public String getProtocol() {
            String applicationProtocol = OpenSSLEngine.this.applicationProtocol;
            if (applicationProtocol == null) {
                synchronized (OpenSSLEngine.this) {
                    if (destroyed) {
                        throw new IllegalStateException(sm.getString("engine.noSession"));
                    }
                    applicationProtocol = SSL.getNextProtoNegotiated(ssl);
                }
                if (applicationProtocol == null) {
                    applicationProtocol = fallbackApplicationProtocol;
                }
                if (applicationProtocol != null) {
                    OpenSSLEngine.this.applicationProtocol = applicationProtocol.replace(':', '_');
                } else {
                    OpenSSLEngine.this.applicationProtocol = applicationProtocol = "";
                }
            }
            String version;
            synchronized (OpenSSLEngine.this) {
                if (destroyed) {
                    throw new IllegalStateException(sm.getString("engine.noSession"));
                }
                version = SSL.getVersion(ssl);
            }
            if (applicationProtocol.isEmpty()) {
                return version;
            } else {
                return version + ':' + applicationProtocol;
            }
        }

        @Override
        public String getPeerHost() {
            // 目前在Tomcat中不可用 (需要在引擎创建期间传递)
            return null;
        }

        @Override
        public int getPeerPort() {
            // 目前在Tomcat中不可用 (需要在引擎创建期间传递)
            return 0;
        }

        @Override
        public int getPacketBufferSize() {
            return MAX_ENCRYPTED_PACKET_LENGTH;
        }

        @Override
        public int getApplicationBufferSize() {
            return MAX_PLAINTEXT_LENGTH;
        }
    }
}
