package org.apache.catalina.tribes.group.interceptors;

public interface DomainFilterInterceptorMBean {

    public int getOptionFlag();

    public byte[] getDomain();

    public int getLogInterval();

    public void setLogInterval(int logInterval);

}
