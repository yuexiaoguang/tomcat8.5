package org.apache.catalina.realm;

import java.security.cert.X509Certificate;

/**
 * 从一个X509Certificate 检索用户名.
 */
public interface X509UsernameRetriever {
	
    /**
     * 从一个X509Certificate 获取用户名.
     *
     * @param cert 包含用户名的凭据.
     * @return 从凭据的一个或多个字段获取的适当的用户名.
     */
    public String getUsername(X509Certificate cert);
}
