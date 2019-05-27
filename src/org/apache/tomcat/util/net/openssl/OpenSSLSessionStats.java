package org.apache.tomcat.util.net.openssl;

import org.apache.tomcat.jni.SSLContext;

/**
 * OpenSSL会话上下文公开的统计信息.
 */
public final class OpenSSLSessionStats {

    private final long context;

    OpenSSLSessionStats(long context) {
        this.context = context;
    }

    /**
     * @return 内部会话高速缓存中的当前会话数.
     */
    public long number() {
        return SSLContext.sessionNumber(context);
    }

    /**
     * @return 客户端模式下启动的SSL/TLS握手次数.
     */
    public long connect() {
        return SSLContext.sessionConnect(context);
    }

    /**
     * @return 客户端模式下成功建立的SSL/TLS会话数.
     */
    public long connectGood() {
        return SSLContext.sessionConnectGood(context);
    }

    /**
     * @return 客户端模式下的启动重新协商次数.
     */
    public long connectRenegotiate() {
        return SSLContext.sessionConnectRenegotiate(context);
    }

    /**
     * @return 服务器模式下启动的SSL/TLS握手次数.
     */
    public long accept() {
        return SSLContext.sessionAccept(context);
    }

    /**
     * @return 服务器模式下成功建立的SSL/TLS会话数.
     */
    public long acceptGood() {
        return SSLContext.sessionAcceptGood(context);
    }

    /**
     * @return 服务器模式下的启动重新协商次数.
     */
    public long acceptRenegotiate() {
        return SSLContext.sessionAcceptRenegotiate(context);
    }

    /**
     * @return 成功重用会话的数量. 在客户端模式下, 成功重用{@code SSL_set_session}的会话集被计为命中.
     * 		在服务器模式下, 从内部或外部缓存成功检索的会话被计为命中.
     */
    public long hits() {
        return SSLContext.sessionHits(context);
    }

    /**
     * @return 在服务器模式下, 从外部会话高速缓存成功检索的会话数.
     */
    public long cbHits() {
        return SSLContext.sessionCbHits(context);
    }

    /**
     * @return 在服务器模式下, 未在内部会话高速缓存中找到的由客户端提出的会话数.
     */
    public long misses() {
        return SSLContext.sessionMisses(context);
    }

    /**
     * @return 客户端提出的会话数, 或者在服务器模式下的内部或外部会话高速缓存中找到的会话数, 但由于超时, 这是无效的.
     * 		这些会话未包含在{@link #hits()}计数中.
     */
    public long timeouts() {
        return SSLContext.sessionTimeouts(context);
    }

    /**
     * @return 由于超出了最大会话高速缓存大小而删除的会话数.
     */
    public long cacheFull() {
        return SSLContext.sessionCacheFull(context);
    }
}
