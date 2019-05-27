package org.apache.tomcat.jni;

/** Error
 */
public class Error extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * APR错误类型.
     */
    private final int error;

    /**
     * 问题的描述.
     */
    private final String description;

    /**
     * @param error 错误中的一个值
     * @param description 错误信息
     */
    private Error(int error, String description)
    {
        super(error + ": " + description);
        this.error = error;
        this.description = description;
    }

    /**
     * 获取异常的APR错误代码.
     */
    public int getError()
    {
        return error;
    }

    /**
     * 获取异常的APR描述.
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * 获取最后一个平台错误.
     * 
     * @return apr_status_t 最后一个平台错误, 在大多数平台上折叠成apr_status_t
     * 这将检索errno，或调用GetLastError()样式函数，并使用APR_FROM_OS_ERROR折叠它. 某些平台（例如OS2）没有这样的机制，因此可能不支持此调用.
     * 不要将此调用用于socket，send，recv等的套接字错误!
     */
    public static native int osError();

    /**
     * 获取最后一个平台套接字错误.
     * @return 最后一个套接字错误, 在所有平台上折叠成apr_status_t
     * 这将检索errno或调用GetLastSocketError()样式函数，并使用APR_FROM_OS_ERROR折叠它.
     */
    public static native int netosError();

    /**
     * 返回描述指定错误的可读字符串.
     * 
     * @param statcode 获取字符串的错误码.
     * @return 错误字符串.
    */
    public static native String strerror(int statcode);

}
