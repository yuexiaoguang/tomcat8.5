package org.apache.tomcat.util.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;

/**
 * 可以在server.xml中的&lt;Connector&gt;元素中设置的属性.
 * 所有属性都以“socket”为前缀, 目前只适用于Nio连接器
 */
public class SocketProperties {

    /**
     * 启用/禁用套接字处理器缓存, 这个有界缓存存储SocketProcessor对象以减少GC
     * 默认是 500; -1 不限制; 0 禁用
     */
    protected int processorCache = 500;

    /**
     * 启用/禁用轮询器事件缓存, 这个有界缓存存储PollerEvent对象以减少轮询器的GC
     * 默认是 500; -1 不限制; 0 禁用; 大于 0 要保留在缓存中的最大对象数.
     */
    protected int eventCache = 500;

    /**
     * 启用/禁用网络缓冲区的直接缓冲区. 默认禁用
     */
    protected boolean directBuffer = false;

    /**
     * 启用/禁用SSL的网络缓冲区的直接缓冲区. 默认禁用
     */
    protected boolean directSslBuffer = false;

    /**
     * 套接字接收缓冲区大小 (SO_RCVBUF), 以字节为单位.
     * 如果未设置，则使用JVM默认值.
     */
    protected Integer rxBufSize = null;

    /**
     * 套接字发送缓冲区大小 (SO_SNDBUF), 以字节为单位.
     * 如果未设置，则使用JVM默认值.
     */
    protected Integer txBufSize = null;

    /**
     * 应用程序读取缓冲区大小, 以字节为单位.
     * 默认值是 rxBufSize
     */
    protected int appReadBufSize = 8192;

    /**
     * 应用程序写入缓冲区大小, 以字节为单位.
     * 默认值是 txBufSize
     */
    protected int appWriteBufSize = 8192;

    /**
     * 端点的NioChannel池大小, 这个值是多少个通道.
     * -1 不限制缓存, 0 没有缓存. 默认 500
     */
    protected int bufferPool = 500;

    /**
     * 要缓存的缓冲池大小, 以字节为单位.
     * -1 不限制缓存, 0 没有缓存. 默认 100MB (1024*1024*100 bytes)
     */
    protected int bufferPoolSize = 1024*1024*100;

    /**
     * TCP_NO_DELAY 选项. 如果未设置，则使用JVM默认值.
     */
    protected Boolean tcpNoDelay = Boolean.TRUE;

    /**
     * SO_KEEPALIVE 选项. 如果未设置，则使用JVM默认值.
     */
    protected Boolean soKeepAlive = null;

    /**
     * OOBINLINE 选项. 如果未设置，则使用JVM默认值.
     */
    protected Boolean ooBInline = null;

    /**
     * SO_REUSEADDR 选项. 如果未设置，则使用JVM默认值.
     */
    protected Boolean soReuseAddress = null;

    /**
     * SO_LINGER 选项, 和<code>soLingerTime</code>值一对.
     * 除非设置了两个属性，否则将使用JVM默认值.
     */
    protected Boolean soLingerOn = null;

    /**
     * SO_LINGER 选项, 和<code>soLingerOn</code>值一对.
     * 除非设置了两个属性，否则将使用JVM默认值.
     */
    protected Integer soLingerTime = null;

    /**
     * SO_TIMEOUT 选项. 默认 20000.
     */
    protected Integer soTimeout = Integer.valueOf(20000);

    /**
     * Performance preferences according to
     * http://docs.oracle.com/javase/1.5.0/docs/api/java/net/Socket.html#setPerformancePreferences(int,%20int,%20int)
     * 必须设置所有的三个性能属性，否则将使用JVM默认值.
     */
    protected Integer performanceConnectionTime = null;

    /**
     * Performance preferences according to
     * http://docs.oracle.com/javase/1.5.0/docs/api/java/net/Socket.html#setPerformancePreferences(int,%20int,%20int)
     * 必须设置所有的三个性能属性，否则将使用JVM默认值.
     */
    protected Integer performanceLatency = null;

    /**
     * Performance preferences according to
     * http://docs.oracle.com/javase/1.5.0/docs/api/java/net/Socket.html#setPerformancePreferences(int,%20int,%20int)
     * 必须设置所有的三个性能属性，否则将使用JVM默认值.
     */
    protected Integer performanceBandwidth = null;

    /**
     * 超时间隔的最小频率，以避免在高流量期间来自轮询器的过量负载
     */
    protected long timeoutInterval = 1000;

    /**
     * 解锁超时时间, 以毫秒为单位.
     */
    protected int unlockTimeout = 250;

    public void setProperties(Socket socket) throws SocketException{
        if (rxBufSize != null)
            socket.setReceiveBufferSize(rxBufSize.intValue());
        if (txBufSize != null)
            socket.setSendBufferSize(txBufSize.intValue());
        if (ooBInline !=null)
            socket.setOOBInline(ooBInline.booleanValue());
        if (soKeepAlive != null)
            socket.setKeepAlive(soKeepAlive.booleanValue());
        if (performanceConnectionTime != null && performanceLatency != null &&
                performanceBandwidth != null)
            socket.setPerformancePreferences(
                    performanceConnectionTime.intValue(),
                    performanceLatency.intValue(),
                    performanceBandwidth.intValue());
        if (soReuseAddress != null)
            socket.setReuseAddress(soReuseAddress.booleanValue());
        if (soLingerOn != null && soLingerTime != null)
            socket.setSoLinger(soLingerOn.booleanValue(),
                    soLingerTime.intValue());
        if (soTimeout != null && soTimeout.intValue() >= 0)
            socket.setSoTimeout(soTimeout.intValue());
        if (tcpNoDelay != null)
            socket.setTcpNoDelay(tcpNoDelay.booleanValue());
    }

    public void setProperties(ServerSocket socket) throws SocketException{
        if (rxBufSize != null)
            socket.setReceiveBufferSize(rxBufSize.intValue());
        if (performanceConnectionTime != null && performanceLatency != null &&
                performanceBandwidth != null)
            socket.setPerformancePreferences(
                    performanceConnectionTime.intValue(),
                    performanceLatency.intValue(),
                    performanceBandwidth.intValue());
        if (soReuseAddress != null)
            socket.setReuseAddress(soReuseAddress.booleanValue());
        if (soTimeout != null && soTimeout.intValue() >= 0)
            socket.setSoTimeout(soTimeout.intValue());
    }

    public void setProperties(AsynchronousSocketChannel socket) throws IOException {
        if (rxBufSize != null)
            socket.setOption(StandardSocketOptions.SO_RCVBUF, rxBufSize);
        if (txBufSize != null)
            socket.setOption(StandardSocketOptions.SO_SNDBUF, txBufSize);
        if (soKeepAlive != null)
            socket.setOption(StandardSocketOptions.SO_KEEPALIVE, soKeepAlive);
        if (soReuseAddress != null)
            socket.setOption(StandardSocketOptions.SO_REUSEADDR, soReuseAddress);
        if (soLingerOn != null && soLingerOn.booleanValue() && soLingerTime != null)
            socket.setOption(StandardSocketOptions.SO_LINGER, soLingerTime);
        if (tcpNoDelay != null)
            socket.setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);
    }

    public void setProperties(AsynchronousServerSocketChannel socket) throws IOException {
        if (rxBufSize != null)
            socket.setOption(StandardSocketOptions.SO_RCVBUF, rxBufSize);
        if (soReuseAddress != null)
            socket.setOption(StandardSocketOptions.SO_REUSEADDR, soReuseAddress);
    }

    public boolean getDirectBuffer() {
        return directBuffer;
    }

    public boolean getDirectSslBuffer() {
        return directSslBuffer;
    }

    public boolean getOoBInline() {
        return ooBInline.booleanValue();
    }

    public int getPerformanceBandwidth() {
        return performanceBandwidth.intValue();
    }

    public int getPerformanceConnectionTime() {
        return performanceConnectionTime.intValue();
    }

    public int getPerformanceLatency() {
        return performanceLatency.intValue();
    }

    public int getRxBufSize() {
        return rxBufSize.intValue();
    }

    public boolean getSoKeepAlive() {
        return soKeepAlive.booleanValue();
    }

    public boolean getSoLingerOn() {
        return soLingerOn.booleanValue();
    }

    public int getSoLingerTime() {
        return soLingerTime.intValue();
    }

    public boolean getSoReuseAddress() {
        return soReuseAddress.booleanValue();
    }

    public int getSoTimeout() {
        return soTimeout.intValue();
    }

    public boolean getTcpNoDelay() {
        return tcpNoDelay.booleanValue();
    }

    public int getTxBufSize() {
        return txBufSize.intValue();
    }

    public int getBufferPool() {
        return bufferPool;
    }

    public int getBufferPoolSize() {
        return bufferPoolSize;
    }

    public int getEventCache() {
        return eventCache;
    }

    public int getAppReadBufSize() {
        return appReadBufSize;
    }

    public int getAppWriteBufSize() {
        return appWriteBufSize;
    }

    public int getProcessorCache() {
        return processorCache;
    }

    public long getTimeoutInterval() {
        return timeoutInterval;
    }

    public int getDirectBufferPool() {
        return bufferPool;
    }

    public void setPerformanceConnectionTime(int performanceConnectionTime) {
        this.performanceConnectionTime =
            Integer.valueOf(performanceConnectionTime);
    }

    public void setTxBufSize(int txBufSize) {
        this.txBufSize = Integer.valueOf(txBufSize);
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = Boolean.valueOf(tcpNoDelay);
    }

    public void setSoTimeout(int soTimeout) {
        this.soTimeout = Integer.valueOf(soTimeout);
    }

    public void setSoReuseAddress(boolean soReuseAddress) {
        this.soReuseAddress = Boolean.valueOf(soReuseAddress);
    }

    public void setSoLingerTime(int soLingerTime) {
        this.soLingerTime = Integer.valueOf(soLingerTime);
    }

    public void setSoKeepAlive(boolean soKeepAlive) {
        this.soKeepAlive = Boolean.valueOf(soKeepAlive);
    }

    public void setRxBufSize(int rxBufSize) {
        this.rxBufSize = Integer.valueOf(rxBufSize);
    }

    public void setPerformanceLatency(int performanceLatency) {
        this.performanceLatency = Integer.valueOf(performanceLatency);
    }

    public void setPerformanceBandwidth(int performanceBandwidth) {
        this.performanceBandwidth = Integer.valueOf(performanceBandwidth);
    }

    public void setOoBInline(boolean ooBInline) {
        this.ooBInline = Boolean.valueOf(ooBInline);
    }

    public void setDirectBuffer(boolean directBuffer) {
        this.directBuffer = directBuffer;
    }

    public void setDirectSslBuffer(boolean directSslBuffer) {
        this.directSslBuffer = directSslBuffer;
    }

    public void setSoLingerOn(boolean soLingerOn) {
        this.soLingerOn = Boolean.valueOf(soLingerOn);
    }

    public void setBufferPool(int bufferPool) {
        this.bufferPool = bufferPool;
    }

    public void setBufferPoolSize(int bufferPoolSize) {
        this.bufferPoolSize = bufferPoolSize;
    }

    public void setEventCache(int eventCache) {
        this.eventCache = eventCache;
    }

    public void setAppReadBufSize(int appReadBufSize) {
        this.appReadBufSize = appReadBufSize;
    }

    public void setAppWriteBufSize(int appWriteBufSize) {
        this.appWriteBufSize = appWriteBufSize;
    }

    public void setProcessorCache(int processorCache) {
        this.processorCache = processorCache;
    }

    public void setTimeoutInterval(long timeoutInterval) {
        this.timeoutInterval = timeoutInterval;
    }

    public void setDirectBufferPool(int directBufferPool) {
        this.bufferPool = directBufferPool;
    }

    public int getUnlockTimeout() {
        return unlockTimeout;
    }

    public void setUnlockTimeout(int unlockTimeout) {
        this.unlockTimeout = unlockTimeout;
    }
}
