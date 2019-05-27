package org.apache.catalina.valves;


import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * <code>RequestFilterValve</code>实现类, 基于远程客户端的IP地址, 可选地与服务器连接器端口号相结合.
 */
public final class RemoteAddrValve extends RequestFilterValve {

    private static final Log log = LogFactory.getLog(RemoteAddrValve.class);


    // --------------------------------------------------------- Public Methods

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        String property;
        if (getAddConnectorPort()) {
            property = request.getRequest().getRemoteAddr() + ";" + request.getConnector().getPort();
        } else {
            property = request.getRequest().getRemoteAddr();
        }
        process(property, request, response);
    }



    @Override
    protected Log getLog() {
        return log;
    }
}
