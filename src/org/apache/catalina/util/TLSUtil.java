package org.apache.catalina.util;

import org.apache.catalina.Globals;
import org.apache.tomcat.util.net.SSLSupport;

public class TLSUtil {

    /**
     * 确定命名请求属性是否用于将有关连接的TLS配置的信息传递给应用程序.
     * 支持Servlet规范定义的标准请求属性, 以及Tomcat的特定属性.
     *
     * @param name  要测试的属性名
     *
     * @return {@code true} 如果属性用于传递TLS配置信息, 否则 {@code false}
     */
    public static boolean isTLSRequestAttribute(String name) {
        return Globals.CERTIFICATES_ATTR.equals(name) ||
                Globals.CIPHER_SUITE_ATTR.equals(name) ||
                Globals.KEY_SIZE_ATTR.equals(name)  ||
                Globals.SSL_SESSION_ID_ATTR.equals(name) ||
                Globals.SSL_SESSION_MGR_ATTR.equals(name) ||
                SSLSupport.PROTOCOL_VERSION_KEY.equals(name);
    }
}
