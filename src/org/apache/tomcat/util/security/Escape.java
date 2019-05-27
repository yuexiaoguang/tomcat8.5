package org.apache.tomcat.util.security;

/**
 * 为不同上下文提供内容的实用方法. 对于使用数据的上下文来说，使用的转义是正确的，是至关重要的.
 */
public class Escape {

    /**
     * 在HTML中使用的转义内容. 这种转义适用于以下用途:
     * <ul>
     * <li>当转义数据直接放置在标签内时的元素内容, 如 &lt;p&gt;, &lt;td&gt; 等.</li>
     * <li>属性值使用&quot; 或 &#x27;引用时的属性值.</li>
     * </ul>
     *
     * @param content   要转义的内容
     *
     * @return  转义后的内容, 或 {@code null} 如果内容是{@code null}
     */
    public static String htmlElementContent(String content) {
        if (content == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '\'') {
                sb.append("&#39;");
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else if (c == '/') {
                sb.append("&#47;");
            } else {
                sb.append(c);
            }
        }

        return (sb.length() > content.length()) ? sb.toString() : content;
    }


    /**
     * 将对象转换为字符串, 通过 {@link Object#toString()}和HTML转义在HTML内容中使用的字符串.
     *
     * @param obj       要转换为字符串然后转义的对象
     *
     * @return 转义的内容, 或<code>&quot;?&quot;</code>如果 obj 是{@code null}
     */
    public static String htmlElementContext(Object obj) {
        if (obj == null) {
            return "?";
        }

        try {
            return htmlElementContent(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * XML中使用的转义内容.
     *
     * @param content   要转义的内容
     *
     * @return  转义后的内容, 或{@code null} 如果内容是{@code null}
     */
    public static String xml(String content) {
        return xml(null, content);
    }


    /**
     * XML中使用的转义内容.
     *
     * @param ifNull    要返回的值, 如果内容是 {@code null}
     * @param content   要转义的内容
     *
     * @return  转义后的内容; 或{@code ifNull}的值, 如果内容是 {@code null}
     */
    public static String xml(String ifNull, String content) {
        return xml(ifNull, false, content);
    }


    /**
     * XML中使用的转义内容.
     *
     * @param ifNull        要返回的值, 如果内容是 {@code null}
     * @param escapeCRLF    CR和LF是否应该转义?
     * @param content       要转义的内容
     *
     * @return  转义后的内容; 或{@code ifNull}的值, 如果内容是 {@code null}
     */
    public static String xml(String ifNull, boolean escapeCRLF, String content) {
        if (content == null) {
            return ifNull;
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '\'') {
                sb.append("&apos;");
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else if (escapeCRLF && c == '\r') {
                sb.append("&#13;");
            } else if (escapeCRLF && c == '\n') {
                sb.append("&#10;");
            } else {
                sb.append(c);
            }
        }

        return (sb.length() > content.length()) ? sb.toString(): content;
    }
}
