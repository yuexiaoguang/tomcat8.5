package org.apache.tomcat.util.net.openssl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.tomcat.util.net.Constants;

/**
 * 以正确的首选项顺序获取SSL协议
 */
public class OpenSSLProtocols {

    private List<String> openSSLProtocols = new ArrayList<>();

    public OpenSSLProtocols(String preferredJSSEProto) {
        Collections.addAll(openSSLProtocols, Constants.SSL_PROTO_TLSv1_2,
                Constants.SSL_PROTO_TLSv1_1, Constants.SSL_PROTO_TLSv1,
                Constants.SSL_PROTO_SSLv3, Constants.SSL_PROTO_SSLv2);
        if(openSSLProtocols.contains(preferredJSSEProto)) {
            openSSLProtocols.remove(preferredJSSEProto);
            openSSLProtocols.add(0, preferredJSSEProto);
        }
    }

    public String[] getProtocols() {
        return openSSLProtocols.toArray(new String[openSSLProtocols.size()]);
    }
}
