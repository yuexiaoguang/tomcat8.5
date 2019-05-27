package org.apache.tomcat.jni;

/** PasswordCallback Interface
 */
public interface PasswordCallback {

    /**
     * 需要密码时调用
     * 
     * @param prompt 密码提示
     * @return 有效密码或 null
     */
    public String callback(String prompt);
}
