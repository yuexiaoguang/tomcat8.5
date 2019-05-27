package org.apache.coyote.http11;

import org.apache.tomcat.util.net.AbstractJsseEndpoint;
import org.apache.tomcat.util.net.openssl.OpenSSLImplementation;

public abstract class AbstractHttp11JsseProtocol<S>
        extends AbstractHttp11Protocol<S> {

    public AbstractHttp11JsseProtocol(AbstractJsseEndpoint<S> endpoint) {
        super(endpoint);
    }


    @Override
    protected AbstractJsseEndpoint<S> getEndpoint() {
        // Over-ridden to add cast
        return (AbstractJsseEndpoint<S>) super.getEndpoint();
    }


    protected String getSslImplementationShortName() {
        if (OpenSSLImplementation.class.getName().equals(getSslImplementationName())) {
            return "openssl";
        }
        return "jsse";
    }

    public String getSslImplementationName() { return getEndpoint().getSslImplementationName(); }
    public void setSslImplementationName(String s) { getEndpoint().setSslImplementationName(s); }


    public int getSniParseLimit() { return getEndpoint().getSniParseLimit(); }
    public void setSniParseLimit(int sniParseLimit) {
        getEndpoint().setSniParseLimit(sniParseLimit);
    }
}
