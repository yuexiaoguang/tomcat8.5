package org.apache.catalina.tribes.group.interceptors;

import java.util.concurrent.atomic.AtomicLong;

public interface ThroughputInterceptorMBean {

    public int getOptionFlag();

    // Attributes
    public int getInterval();

    public void setInterval(int interval);

    // stats
    public double getLastCnt();

    public double getMbAppTx();

    public double getMbRx();

    public double getMbTx();

    public AtomicLong getMsgRxCnt();

    public AtomicLong getMsgTxCnt();

    public AtomicLong getMsgTxErr();

    public long getRxStart();

    public double getTimeTx();

    public long getTxStart();

    // Operations
    public void report(double timeTx);

}
