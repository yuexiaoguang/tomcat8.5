package org.apache.tomcat.util.http.fileupload;

/**
 * 如果文件名无效，则抛出此异常.
 * 文件名无效, 如果它包含NUL字符.
 * 攻击者可能会使用它来规避安全检查:
 * 例如, 恶意用户可能会上传名为 "foo.exe\0.png"的文件. 此文件名可能会通过安全检查 (i.e.检查后缀名 ".png"),
 * 同时, 取决于底层的C库, 它可能创建一个文件名为 "foo.exe", 因为NUL字符是C中的字符串终止符.
 */
public class InvalidFileNameException extends RuntimeException {

    private static final long serialVersionUID = 7922042602454350470L;

    /**
     * 导致异常的文件名.
     */
    private final String name;

    /**
     * @param pName 导致异常的文件名.
     * @param pMessage 人类可读的错误消息.
     */
    public InvalidFileNameException(String pName, String pMessage) {
        super(pMessage);
        name = pName;
    }

    /**
     * 返回无效的文件名.
     */
    public String getName() {
        return name;
    }

}
