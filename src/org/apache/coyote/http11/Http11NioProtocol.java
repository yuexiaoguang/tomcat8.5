package org.apache.coyote.http11;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;


/**
 * 抽象协议实现, 包括线程, etc.
 * Processor 是单线程的和特定于流的协议, 不适合 Jk 协议, 像 JNI一样.
 */
public class Http11NioProtocol extends AbstractHttp11JsseProtocol<NioChannel> {

    private static final Log log = LogFactory.getLog(Http11NioProtocol.class);


    public Http11NioProtocol() {
        super(new NioEndpoint());
    }


    @Override
    protected Log getLog() { return log; }


    // -------------------- Pool setup --------------------

    public void setPollerThreadCount(int count) {
        ((NioEndpoint)getEndpoint()).setPollerThreadCount(count);
    }

    public int getPollerThreadCount() {
        return ((NioEndpoint)getEndpoint()).getPollerThreadCount();
    }

    public void setSelectorTimeout(long timeout) {
        ((NioEndpoint)getEndpoint()).setSelectorTimeout(timeout);
    }

    public long getSelectorTimeout() {
        return ((NioEndpoint)getEndpoint()).getSelectorTimeout();
    }

    public void setPollerThreadPriority(int threadPriority) {
        ((NioEndpoint)getEndpoint()).setPollerThreadPriority(threadPriority);
    }

    public int getPollerThreadPriority() {
      return ((NioEndpoint)getEndpoint()).getPollerThreadPriority();
    }


    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        if (isSSLEnabled()) {
            return ("https-" + getSslImplementationShortName()+ "-nio");
        } else {
            return ("http-nio");
        }
    }
}
