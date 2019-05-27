package org.apache.catalina.tribes.group.interceptors;

public interface TcpFailureDetectorMBean {

    public int getOptionFlag();

    // Attributes
    public long getConnectTimeout();

    public boolean getPerformSendTest();

    public boolean getPerformReadTest();

    public long getReadTestTimeout();

    public int getRemoveSuspectsTimeout();

    public void setPerformReadTest(boolean performReadTest);

    public void setPerformSendTest(boolean performSendTest);

    public void setReadTestTimeout(long readTestTimeout);

    public void setConnectTimeout(long connectTimeout);

    public void setRemoveSuspectsTimeout(int removeSuspectsTimeout);

    // Operations
    public void checkMembers(boolean checkAll);
}