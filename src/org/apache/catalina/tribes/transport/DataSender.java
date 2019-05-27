package org.apache.catalina.tribes.transport;

import java.io.IOException;

public interface DataSender {
    public void connect() throws IOException;
    public void disconnect();
    public boolean isConnected();
    public void setRxBufSize(int size);
    public void setTxBufSize(int size);
    public boolean keepalive();
    public void setTimeout(long timeout);
    public void setKeepAliveCount(int maxRequests);
    public void setKeepAliveTime(long keepAliveTimeInMs);
    public int getRequestCount();
    public long getConnectTime();
}