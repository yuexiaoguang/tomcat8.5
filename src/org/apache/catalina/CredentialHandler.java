package org.apache.catalina;

/**
 * {@link Realm}使用这个接口比较用户提供的凭据和{@link Realm}中保存的这个用户的凭据.
 */
public interface CredentialHandler {

    /**
     * 检查输入凭据是否与存储的凭据相匹配
     *
     * @param inputCredentials  用户提供的凭据
     * @param storedCredentials {@link Realm}中保存的凭据
     *
     * @return <code>true</code>如果 inputCredentials 匹配storedCredentials, 否则<code>false</code>
     */
    boolean matches(String inputCredentials, String storedCredentials);

    /**
     * 生成给定输入凭据的等效存储凭据.
     *
     * @param inputCredentials  用户提供的凭据
     *
     * @return 给定输入凭据的等效存储凭据
     */
    String mutate(String inputCredentials);
}
