package org.apache.coyote.http11.upgrade;

import javax.servlet.http.HttpUpgradeHandler;

import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;


/**
 * 这个Tomcat特定的接口, 是由需要直接访问Tomcat I/O层的处理程序实现的，而不是通过servlet API实现的.
 */
public interface InternalHttpUpgradeHandler extends HttpUpgradeHandler {

    SocketState upgradeDispatch(SocketEvent status);

    void setSocketWrapper(SocketWrapperBase<?> wrapper);

    void setSslSupport(SSLSupport sslSupport);

    void pause();
}