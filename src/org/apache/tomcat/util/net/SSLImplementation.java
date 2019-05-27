package org.apache.tomcat.util.net;

import javax.net.ssl.SSLSession;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.jsse.JSSEImplementation;
import org.apache.tomcat.util.res.StringManager;

/**
 * 为Tomcat特定机制提供工厂和基础实现，允许使用备用SSL/TLS实现，而无需实现完整的JSSE提供程序.
 */
public abstract class SSLImplementation {

    private static final Log logger = LogFactory.getLog(SSLImplementation.class);
    private static final StringManager sm = StringManager.getManager(SSLImplementation.class);

    /**
     * 使用给定的类名获取实现的实例（不是单例）.
     *
     * @param className 所需实现的类名, 或null以使用默认值 (当前 {@link JSSEImplementation}.
     *
     * @return 所需实现的实例
     *
     * @throws ClassNotFoundException 如果无法创建所请求的类的实例
     */
    public static SSLImplementation getInstance(String className)
            throws ClassNotFoundException {
        if (className == null)
            return new JSSEImplementation();

        try {
            Class<?> clazz = Class.forName(className);
            return (SSLImplementation) clazz.getConstructor().newInstance();
        } catch (Exception e) {
            String msg = sm.getString("sslImplementation.cnfe", className);
            if (logger.isDebugEnabled()) {
                logger.debug(msg, e);
            }
            throw new ClassNotFoundException(msg, e);
        }
    }


    public abstract SSLSupport getSSLSupport(SSLSession session);

    public abstract SSLUtil getSSLUtil(SSLHostConfigCertificate certificate);

    public abstract boolean isAlpnSupported();
}
