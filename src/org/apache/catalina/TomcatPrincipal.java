package org.apache.catalina;

import java.security.Principal;

import org.ietf.jgss.GSSCredential;

/**
 * 定义{@link Principal}额外的方法.
 */
public interface TomcatPrincipal extends Principal {

    /**
     * @return 暴露给应用的身份验证的Principal.
     */
    Principal getUserPrincipal();

    /**
     * @return 用户委派的凭证.
     */
    GSSCredential getGssCredential();

    /**
     * 调用注销, 在任何关联的JAASLoginContext上. 未来可能会扩展到其他功能要求.
     *
     * @throws Exception 如果有什么错误的注销. 使用Exception允许未来该方法包括其他的退出机制，可能抛出一个不同的异常到LoginContext
     *
     */
    void logout() throws Exception;
}
