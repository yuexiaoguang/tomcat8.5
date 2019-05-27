package org.apache.coyote.http11.upgrade;

import java.io.IOException;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import org.apache.coyote.ContainerThreadMarker;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

public class UpgradeServletInputStream extends ServletInputStream {

    private static final Log log = LogFactory.getLog(UpgradeServletInputStream.class);
    private static final StringManager sm =
            StringManager.getManager(UpgradeServletInputStream.class);

    private final UpgradeProcessorBase processor;
    private final SocketWrapperBase<?> socketWrapper;

    private volatile boolean closed = false;
    private volatile boolean eof = false;
    // Start in blocking-mode
    private volatile Boolean ready = Boolean.TRUE;
    private volatile ReadListener listener = null;


    public UpgradeServletInputStream(UpgradeProcessorBase processor,
            SocketWrapperBase<?> socketWrapper) {
        this.processor = processor;
        this.socketWrapper = socketWrapper;
    }


    @Override
    public final boolean isFinished() {
        if (listener == null) {
            throw new IllegalStateException(
                    sm.getString("upgrade.sis.isFinished.ise"));
        }
        return eof;
    }


    @Override
    public final boolean isReady() {
        if (listener == null) {
            throw new IllegalStateException(
                    sm.getString("upgrade.sis.isReady.ise"));
        }

        if (eof || closed) {
            return false;
        }

        // 如果已知当前状态, 返回它.
        if (ready != null) {
            return ready.booleanValue();
        }

        try {
            ready = Boolean.valueOf(socketWrapper.isReadyForRead());
        } catch (IOException e) {
            onError(e);
        }
        return ready.booleanValue();
    }


    @Override
    public final void setReadListener(ReadListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException(
                    sm.getString("upgrade.sis.readListener.null"));
        }
        if (this.listener != null) {
            throw new IllegalArgumentException(
                    sm.getString("upgrade.sis.readListener.set"));
        }
        if (closed) {
            throw new IllegalStateException(sm.getString("upgrade.sis.read.closed"));
        }

        this.listener = listener;

        // Container 用于第一次调用 onDataAvailable().
        if (ContainerThreadMarker.isContainerThread()) {
            processor.addDispatch(DispatchType.NON_BLOCKING_READ);
        } else {
            socketWrapper.registerReadInterest();
        }

        // 切换到非阻塞. 不知道数据是否可用.
        ready = null;
    }


    @Override
    public final int read() throws IOException {
        preReadChecks();

        return readInternal();
    }


    @Override
    public final int readLine(byte[] b, int off, int len) throws IOException {
        preReadChecks();

        if (len <= 0) {
            return 0;
        }
        int count = 0, c;

        while ((c = readInternal()) != -1) {
            b[off++] = (byte) c;
            count++;
            if (c == '\n' || count == len) {
                break;
            }
        }
        return count > 0 ? count : -1;
    }


    @Override
    public final int read(byte[] b, int off, int len) throws IOException {
        preReadChecks();

        try {
            int result = socketWrapper.read(listener == null, b, off, len);
            if (result == -1) {
                eof = true;
            }
            return result;
        } catch (IOException ioe) {
            close();
            throw ioe;
        }
    }



    @Override
    public void close() throws IOException {
        eof = true;
        closed = true;
    }


    private void preReadChecks() {
        if (listener != null && (ready == null || !ready.booleanValue())) {
            throw new IllegalStateException(sm.getString("upgrade.sis.read.ise"));
        }
        if (closed) {
            throw new IllegalStateException(sm.getString("upgrade.sis.read.closed"));
        }
        // 不再知道数据是否可用
        ready = null;
    }


    private int readInternal() throws IOException {
        // 非阻塞的单字节读取需要特殊处理, 因此，所有的单字节读取都通过此方法运行.
        byte[] b = new byte[1];
        int result;
        try {
            result = socketWrapper.read(listener == null, b, 0, 1);
        } catch (IOException ioe) {
            close();
            throw ioe;
        }
        if (result == 0) {
            return -1;
        } else if (result == -1) {
            eof = true;
            return -1;
        } else {
            return b[0] & 0xFF;
        }
    }


    final void onDataAvailable() {
        if (listener == null) {
            return;
        }
        ready = Boolean.TRUE;
        ClassLoader oldCL = processor.getUpgradeToken().getContextBind().bind(false, null);
        try {
            if (!eof) {
                listener.onDataAvailable();
            }
            if (eof) {
                listener.onAllDataRead();
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            onError(t);
        } finally {
            processor.getUpgradeToken().getContextBind().unbind(false, oldCL);
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
            log.warn(sm.getString("upgrade.sis.onErrorFail"), t2);
        } finally {
            processor.getUpgradeToken().getContextBind().unbind(false, oldCL);
        }
        try {
            close();
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgrade.sis.errorCloseFail"), ioe);
            }
        }
        ready = Boolean.FALSE;
    }


    final boolean isClosed() {
        return closed;
    }
}
