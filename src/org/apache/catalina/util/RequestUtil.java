package org.apache.catalina.util;

/**
 * 通用请求解析和编码实用方法.
 */
public final class RequestUtil {

    /**
     * 过滤指定的消息字符串中HTML敏感的字符.
     * 这避免了在错误消息中经常报告的请求URL中包含JavaScript代码所造成的潜在攻击.
     *
     * @param message 要过滤的消息字符串
     *
     * @return the filtered message
     *
     * @deprecated This method will be removed in Tomcat 9
     */
    @Deprecated
    public static String filter(String message) {

        if (message == null) {
            return null;
        }

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
        return result.toString();
    }
}
