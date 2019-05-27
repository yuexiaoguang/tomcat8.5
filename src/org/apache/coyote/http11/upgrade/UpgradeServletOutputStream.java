package org.apache.coyote.http11.upgrade;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import org.apache.coyote.ContainerThreadMarker;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

public class UpgradeServletOutputStream extends ServletOutputStream {

    private static final Log log = LogFactory.getLog(UpgradeServletOutputStream.class);
    private static final StringManager sm =
            StringManager.getManager(UpgradeServletOutputStream.class);

    private final UpgradeProcessorBase processor;
    private final SocketWrapperBase<?> socketWrapper;

    // 用于确保 isReady() 和 onWritePossible() 有一个一致的缓冲区视图.
    private final Object registeredLock = new Object();

    // 用于确保同一时间只有一个线程写入 socket, 以及写入之后使用未写入的数据持续不断的更新缓冲区.
    // 注意: 检查缓冲区是否包含数据时, 不需要持有这个锁, 取决于如何使用结果, 可能需要某种形式的同步 (see fireListenerLock for an example).
    private final Object writeLock = new Object();

    private volatile boolean flushing = false;

    private volatile boolean closed = false;

    // 启动阻塞模式
    private volatile WriteListener listener = null;

    // Guarded by registeredLock
    private boolean registered = false;



    public UpgradeServletOutputStream(UpgradeProcessorBase processor,
            SocketWrapperBase<?> socketWrapper) {
        this.processor = processor;
        this.socketWrapper = socketWrapper;
    }


    @Override
    public final boolean isReady() {
        if (listener == null) {
            throw new IllegalStateException(
                    sm.getString("upgrade.sos.canWrite.ise"));
        }
        if (closed) {
            return false;
        }

        // 确保 isReady() 和 onWritePossible() 拥有fireListener一致的视图, 当确定是否触发监听器时
        synchronized (registeredLock) {
            if (flushing) {
                // 因为 flushing 是 true, socket必须已经注册, 多次注册将导致问题.
                registered = true;
                return false;
            } else if (registered){
                // 已经注册了 socket用于写入, 多次注册将导致问题.
                return false;
            } else {
                boolean result = socketWrapper.isReadyForWrite();
                registered = !result;
                return result;
            }
        }
    }


    @Override
    public final void setWriteListener(WriteListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException(
                    sm.getString("upgrade.sos.writeListener.null"));
        }
        if (this.listener != null) {
            throw new IllegalArgumentException(
                    sm.getString("upgrade.sos.writeListener.set"));
        }
        if (closed) {
            throw new IllegalStateException(sm.getString("upgrade.sos.write.closed"));
        }
        this.listener = listener;
        // Container 负责第一次调用 onWritePossible().
        synchronized (registeredLock) {
            registered = true;
            // Container 负责第一次调用 onDataAvailable().
            if (ContainerThreadMarker.isContainerThread()) {
                processor.addDispatch(DispatchType.NON_BLOCKING_WRITE);
            } else {
                socketWrapper.registerWriteInterest();
            }
        }

    }


    final boolean isClosed() {
        return closed;
    }


    @Override
    public void write(int b) throws IOException {
        synchronized (writeLock) {
            preWriteChecks();
            writeInternal(new byte[] { (byte) b }, 0, 1);
        }
    }


    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        synchronized (writeLock) {
            preWriteChecks();
            writeInternal(b, off, len);
        }
    }


    @Override
    public void flush() throws IOException {
        preWriteChecks();
        flushInternal(listener == null, true);
    }


    private void flushInternal(boolean block, boolean updateFlushing) throws IOException {
        try {
            synchronized (writeLock) {
                if (updateFlushing) {
                    flushing = socketWrapper.flush(block);
                    if (flushing) {
                        socketWrapper.registerWriteInterest();
                    }
                } else {
                    socketWrapper.flush(block);
                }
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            onError(t);
            if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new IOException(t);
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        flushInternal((listener == null), false);
    }


    private void preWriteChecks() {
        if (listener != null && !socketWrapper.canWrite()) {
            throw new IllegalStateException(sm.getString("upgrade.sos.write.ise"));
        }
        if (closed) {
            throw new IllegalStateException(sm.getString("upgrade.sos.write.closed"));
        }
    }


    /**
     * 调用这个方法必须持有 writeLock.
     */
    private void writeInternal(byte[] b, int off, int len) throws IOException {
        if (listener == null) {
            // Simple case - blocking IO
            socketWrapper.write(true, b, off, len);
        } else {
            socketWrapper.write(false, b, off, len);
        }
    }


    final void onWritePossible() {
        try {
            if (flushing) {
                flushInternal(false, true);
                if (flushing) {
                    return;
                }
            } else {
                // 这可以填充写入缓冲区, 这时候下面调用 isReadyForWrite()将重新注册 socket 用于写入
                flushInternal(false, false);
            }
        } catch (IOException ioe) {
            onError(ioe);
            return;
        }

        // 确保 isReady() 和 onWritePossible() 拥有缓冲区和fireListener一致的视图, 当确定是否触发监听器时
        boolean fire = false;
        synchronized (registeredLock) {
            if (socketWrapper.isReadyForWrite()) {
                registered = false;
                fire = true;
            } else {
                registered = true;
            }
        }

        if (fire) {
            ClassLoader oldCL = processor.getUpgradeToken().getContextBind().bind(false, null);
            try {
                listener.onWritePossible();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                onError(t);
            } finally {
                processor.getUpgradeToken().getContextBind().unbind(false, oldCL);
            }
        }
    }


    private final void onError(Throwable t) {
        if (listener == null) {
            return;
        }
        ClassLoader oldCL = processor.getUpgradeToken().getContextBind().bind(false, null);
        try {
            listener.onError(t);
        } catch (Throwable t2) {
            ExceptionUtils.handleThrowable(t2);
            log.warn(sm.getString("upgrade.sos.onErrorFail"), t2);
        } finally {
            processor.getUpgradeToken().getContextBind().unbind(false, oldCL);
        }
        try {
            close();
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgrade.sos.errorCloseFail"), ioe);
            }
        }
    }
}
