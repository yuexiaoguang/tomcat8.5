package org.apache.tomcat.util.net.jsse;

import javax.net.ssl.SSLSession;

import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SSLUtil;

/* JSSEImplementation:
   JSSE的具体实现类
*/

public class JSSEImplementation extends SSLImplementation {

    public JSSEImplementation() {
        // 确保keySizeCache现在作为连接器启动的一部分加载，否则将在首次使用时填充缓存，这将减慢该请求.
        JSSESupport.init();
    }

    @Override
    public SSLSupport getSSLSupport(SSLSession session) {
        return new JSSESupport(session);
    }

    @Override
    public SSLUtil getSSLUtil(SSLHostConfigCertificate certificate) {
        return new JSSEUtil(certificate);
    }

    @Override
    public boolean isAlpnSupported() {
        return JreCompat.isJre9Available();
    }
}
