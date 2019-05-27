package org.apache.coyote.ajp;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AprEndpoint;


/**
 * 这是基于 APR/native的AJP协议处理程序实现.
 */
public class AjpAprProtocol extends AbstractAjpProtocol<Long> {

    private static final Log log = LogFactory.getLog(AjpAprProtocol.class);

    @Override
    protected Log getLog() { return log; }


    @Override
    public boolean isAprRequired() {
        // 重写, 因为这个协议实现需要 APR/native 库
        return true;
    }


    // ------------------------------------------------------------ Constructor

    public AjpAprProtocol() {
        super(new AprEndpoint());
    }


    // --------------------------------------------------------- Public Methods

    public int getPollTime() { return ((AprEndpoint)getEndpoint()).getPollTime(); }
    public void setPollTime(int pollTime) { ((AprEndpoint)getEndpoint()).setPollTime(pollTime); }


    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        return ("ajp-apr");
    }
}
