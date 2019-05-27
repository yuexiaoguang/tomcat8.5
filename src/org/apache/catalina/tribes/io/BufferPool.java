package org.apache.catalina.tribes.io;


import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class BufferPool {
    private static final Log log = LogFactory.getLog(BufferPool.class);

    public static final int DEFAULT_POOL_SIZE = 100*1024*1024; //100MB

    protected static final StringManager sm = StringManager.getManager(BufferPool.class);



    protected static volatile BufferPool instance = null;
    protected final BufferPoolAPI pool;

    private BufferPool(BufferPoolAPI pool) {
        this.pool = pool;
    }

    public XByteBuffer getBuffer(int minSize, boolean discard) {
        if ( pool != null ) return pool.getBuffer(minSize, discard);
        else return new XByteBuffer(minSize,discard);
    }

    public void returnBuffer(XByteBuffer buffer) {
        if ( pool != null ) pool.returnBuffer(buffer);
    }

    public void clear() {
        if ( pool != null ) pool.clear();
    }


    public static BufferPool getBufferPool() {
        if (instance == null) {
            synchronized (BufferPool.class) {
                if (instance == null) {
                   BufferPoolAPI pool = new BufferPool15Impl();
                   pool.setMaxSize(DEFAULT_POOL_SIZE);
                   log.info(sm.getString("bufferPool.created",
                           Integer.toString(DEFAULT_POOL_SIZE), pool.getClass().getName()));
                   instance = new BufferPool(pool);
                }
            }
        }
        return instance;
    }


    public static interface BufferPoolAPI {
        public void setMaxSize(int bytes);

        public XByteBuffer getBuffer(int minSize, boolean discard);

        public void returnBuffer(XByteBuffer buffer);

        public void clear();
    }
}
