package org.apache.tomcat.util.net.openssl;

import java.util.Enumeration;
import java.util.NoSuchElementException;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;

import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.util.res.StringManager;

/**
 * OpenSSL特定的{@link SSLSessionContext}实现.
 */
public class OpenSSLSessionContext implements SSLSessionContext {
    private static final StringManager sm = StringManager.getManager(OpenSSLSessionContext.class);
    private static final Enumeration<byte[]> EMPTY = new EmptyEnumeration();

    private final OpenSSLSessionStats stats;
    private final long context;

    OpenSSLSessionContext(long context) {
        this.context = context;
        stats = new OpenSSLSessionStats(context);
    }

    @Override
    public SSLSession getSession(byte[] bytes) {
        return null;
    }

    @Override
    public Enumeration<byte[]> getIds() {
        return EMPTY;
    }

    /**
     * 设置此上下文的SSL会话票证密钥.
     *
     * @param keys 会话票证密钥
     */
    public void setTicketKeys(byte[] keys) {
        if (keys == null) {
            throw new IllegalArgumentException(sm.getString("sessionContext.nullTicketKeys"));
        }
        SSLContext.setSessionTicketKeys(context, keys);
    }

    /**
     * 启用或禁用SSL会话的缓存.
     *
     * @param enabled {@code true}启用缓存, {@code false}禁用
     */
    public void setSessionCacheEnabled(boolean enabled) {
        long mode = enabled ? SSL.SSL_SESS_CACHE_SERVER : SSL.SSL_SESS_CACHE_OFF;
        SSLContext.setSessionCacheMode(context, mode);
    }

    /**
     * @return {@code true}如果启用了SSL会话的缓存, 否则{@code false}.
     */
    public boolean isSessionCacheEnabled() {
        return SSLContext.getSessionCacheMode(context) == SSL.SSL_SESS_CACHE_SERVER;
    }

    /**
     * @return 此上下文的统计信息.
     */
    public OpenSSLSessionStats stats() {
        return stats;
    }

    @Override
    public void setSessionTimeout(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException();
        }
        SSLContext.setSessionCacheTimeout(context, seconds);
    }

    @Override
    public int getSessionTimeout() {
        return (int) SSLContext.getSessionCacheTimeout(context);
    }

    @Override
    public void setSessionCacheSize(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        SSLContext.setSessionCacheSize(context, size);
    }

    @Override
    public int getSessionCacheSize() {
        return (int) SSLContext.getSessionCacheSize(context);
    }

    /**
     * 设置重用会话的上下文 (服务器端)
     *
     * @param sidCtx 可以是任何类型的二进制数据, 因此可以使用例如：应用程序的名称, 或主机名, 或服务名称
     * 
     * @return {@code true}成功, 否则{@code false}.
     */
    public boolean setSessionIdContext(byte[] sidCtx) {
        return SSLContext.setSessionIdContext(context, sidCtx);
    }

    private static final class EmptyEnumeration implements Enumeration<byte[]> {
        @Override
        public boolean hasMoreElements() {
            return false;
        }

        @Override
        public byte[] nextElement() {
            throw new NoSuchElementException();
        }
    }
}
