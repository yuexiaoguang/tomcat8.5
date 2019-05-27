package org.apache.catalina.tribes.group.interceptors;

public interface TcpPingInterceptorMBean {

    public int getOptionFlag();

    public long getInterval();

    public boolean getUseThread();

}
