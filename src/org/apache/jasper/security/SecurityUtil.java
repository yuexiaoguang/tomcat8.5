package org.apache.jasper.security;

import org.apache.jasper.Constants;

/**
 * 安全相关的操作工具类.
 */
public final class SecurityUtil{

    private static final boolean packageDefinitionEnabled =
         System.getProperty("package.definition") == null ? false : true;

    /**
     * 返回<code>SecurityManager</code> 只有当 Security 是启用的, 并且启用了包保护机制.
     *  
     * @return <code>true</code> if package protection is enabled
     */
    public static boolean isPackageProtectionEnabled(){
        if (packageDefinitionEnabled && Constants.IS_SECURITY_ENABLED){
            return true;
        }
        return false;
    }


    /**
     * 过滤指定的消息字符串中HTML敏感的字符.
     * 这避免了在错误URL中经常报告的请求URL中包含JavaScript代码所引起的潜在攻击.
     *
     * @param message 要过滤的消息
     * @return the HTML filtered message
     *
     * @deprecated This method will be removed in Tomcat 9
     */
    @Deprecated
    public static String filter(String message) {

        if (message == null)
            return (null);

        char content[] = new char[message.length()];
        message.getChars(0, message.length(), content, 0);
        StringBuilder result = new StringBuilder(content.length + 50);
        for (int i = 0; i < content.length; i++) {
            switch (content[i]) {
            case '<':
                result.append("&lt;");
                break;
            case '>':
                result.append("&gt;");
                break;
            case '&':
                result.append("&amp;");
                break;
            case '"':
                result.append("&quot;");
                break;
            default:
                result.append(content[i]);
            }
        }
        return (result.toString());
    }
}
