package org.apache.coyote.http11;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.Nio2Channel;
import org.apache.tomcat.util.net.Nio2Endpoint;


/**
 * HTTP/1.1 协议实现, 使用 NIO2.
 */
public class Http11Nio2Protocol extends AbstractHttp11JsseProtocol<Nio2Channel> {

    private static final Log log = LogFactory.getLog(Http11Nio2Protocol.class);


    public Http11Nio2Protocol() {
        super(new Nio2Endpoint());
    }


    @Override
    protected Log getLog() { return log; }


    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        if (isSSLEnabled()) {
            return ("https-" + getSslImplementationShortName()+ "-nio2");
        } else {
            return ("http-nio2");
        }
    }
}
