package org.apache.catalina.tribes.transport.nio;


public interface NioReceiverMBean {

    // Receiver Attributes
    public String getAddress();

    public boolean getDirect();

    public int getPort();

    public int getAutoBind();

    public int getSecurePort();

    public int getUdpPort();

    public long getSelectorTimeout();

    public int getMaxThreads();

    public int getMinThreads();

    public long getMaxIdleTime();

    public boolean getOoBInline();

    public int getRxBufSize();

    public int getTxBufSize();

    public int getUdpRxBufSize();

    public int getUdpTxBufSize();

    public boolean getSoKeepAlive();

    public boolean getSoLingerOn();

    public int getSoLingerTime();

    public boolean getSoReuseAddress();

    public boolean getTcpNoDelay();

    public int getTimeout();

    public boolean getUseBufferPool();

    public boolean isListening();

    // pool stats
    public int getPoolSize();

    public int getActiveCount();

    public long getTaskCount();

    public long getCompletedTaskCount();

}
