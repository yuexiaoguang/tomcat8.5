package org.apache.coyote.http11.upgrade;

import java.io.IOException;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;

import org.apache.coyote.UpgradeToken;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;

public class UpgradeProcessorInternal extends UpgradeProcessorBase {

    private static final Log log = LogFactory.getLog(UpgradeProcessorInternal.class);

    private final InternalHttpUpgradeHandler internalHttpUpgradeHandler;

    public UpgradeProcessorInternal(SocketWrapperBase<?> wrapper,
            UpgradeToken upgradeToken) {
        super(upgradeToken);
        this.internalHttpUpgradeHandler = (InternalHttpUpgradeHandler) upgradeToken.getHttpUpgradeHandler();
        /*
         * 在升级协议中保留超时时间.
         */
        wrapper.setReadTimeout(INFINITE_TIMEOUT);
        wrapper.setWriteTimeout(INFINITE_TIMEOUT);

        internalHttpUpgradeHandler.setSocketWrapper(wrapper);
    }


    @Override
    public SocketState dispatch(SocketEvent status) {
        return internalHttpUpgradeHandler.upgradeDispatch(status);
    }


    @Override
    public final void setSslSupport(SSLSupport sslSupport) {
        internalHttpUpgradeHandler.setSslSupport(sslSupport);
    }


    @Override
    public void pause() {
        internalHttpUpgradeHandler.pause();
    }


    @Override
    protected Log getLog() {
        return log;
    }


    // --------------------------------------------------- AutoCloseable methods

    @Override
    public void close() throws Exception {
        internalHttpUpgradeHandler.destroy();
    }


    // --------------------------------------------------- WebConnection methods

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return null;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return null;
    }
}
