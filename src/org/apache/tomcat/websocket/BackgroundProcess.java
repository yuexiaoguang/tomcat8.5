package org.apache.tomcat.websocket;

public interface BackgroundProcess {

    void backgroundProcess();

    void setProcessPeriod(int period);

    int getProcessPeriod();
}
