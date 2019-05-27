package org.apache.catalina.tribes.transport;

import org.apache.catalina.tribes.io.ListenCallback;

public abstract class AbstractRxTask implements Runnable
{

    public static final int OPTION_DIRECT_BUFFER = ReceiverBase.OPTION_DIRECT_BUFFER;

    private ListenCallback callback;
    private RxTaskPool pool;
    private boolean doRun = true;
    private int options;
    protected boolean useBufferPool = true;

    public AbstractRxTask(ListenCallback callback) {
        this.callback = callback;
    }

    public void setTaskPool(RxTaskPool pool) {
        this.pool = pool;
    }

    public void setOptions(int options) {
        this.options = options;
    }

    public void setCallback(ListenCallback callback) {
        this.callback = callback;
    }

    public void setDoRun(boolean doRun) {
        this.doRun = doRun;
    }

    public RxTaskPool getTaskPool() {
        return pool;
    }

    public int getOptions() {
        return options;
    }

    public ListenCallback getCallback() {
        return callback;
    }

    public boolean isDoRun() {
        return doRun;
    }

    public void close()
    {
        doRun = false;
        notify();
    }

    public void setUseBufferPool(boolean usebufpool) {
        useBufferPool = usebufpool;
    }

    public boolean getUseBufferPool() {
        return useBufferPool;
    }
}
