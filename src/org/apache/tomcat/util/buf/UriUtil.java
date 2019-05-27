package org.apache.tomcat.util.buf;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * 用于处理URI和URL的实用程序类.
 */
public final class UriUtil {

    private static final char[] HEX =
        {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private static final Pattern PATTERN_EXCLAMATION_MARK = Pattern.compile("!/");
    private static final Pattern PATTERN_CARET = Pattern.compile("\\^/");
    private static final Pattern PATTERN_ASTERISK = Pattern.compile("\\*/");
    private static final Pattern PATTERN_CUSTOM;
    private static final String REPLACE_CUSTOM;

    private static final String WAR_SEPARATOR;

    static {
        String custom = System.getProperty("org.apache.tomcat.util.buf.UriUtil.WAR_SEPARATOR");
        if (custom == null) {
            WAR_SEPARATOR = "*/";
            PATTERN_CUSTOM = null;
            REPLACE_CUSTOM = null;
        } else {
            WAR_SEPARATOR = custom + "/";
            PATTERN_CUSTOM = Pattern.compile(Pattern.quote(WAR_SEPARATOR));
            StringBuffer sb = new StringBuffer(custom.length() * 3);
            // 故意使用平台的默认编码
            byte[] ba = custom.getBytes();
            for (int j = 0; j < ba.length; j++) {
                // 转换缓冲区中的每个字节
                byte toEncode = ba[j];
                sb.append('%');
                int low = toEncode & 0x0f;
                int high = (toEncode & 0xf0) >> 4;
                sb.append(HEX[high]);
                sb.append(HEX[low]);
            }
            REPLACE_CUSTOM = sb.toString();
        }
    }


    private UriUtil() {
        // Utility class. Hide default constructor
    }


    /**
     * 确定URI的方案中是否允许该字符.
     *
     * @param c 要测试的字符
     *
     * @return {@code true} 允许, 否则 {code @false}
     */
    private static boolean isSchemeChar(char c) {
        return Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.';
    }


    /**
     * 确定URI字符串是否具有<code>scheme</code>组件.
     *
     * @param uri 要测试的URI
     *
     * @return {@code true} 如果有方案, 否则 {code @false}
     */
    public static boolean hasScheme(CharSequence uri) {
        int len = uri.length();
        for(int i=0; i < len ; i++) {
            char c = uri.charAt(i);
            if(c == ':') {
                return i > 0;
            } else if(!UriUtil.isSchemeChar(c)) {
                return false;
            }
        }
        return false;
    }


    public static URL buildJarUrl(File jarFile) throws MalformedURLException {
        return buildJarUrl(jarFile, null);
    }


    public static URL buildJarUrl(File jarFile, String entryPath) throws MalformedURLException {
        return buildJarUrl(jarFile.toURI().toString(), entryPath);
    }


    public static URL buildJarUrl(String fileUrlString) throws MalformedURLException {
        return buildJarUrl(fileUrlString, null);
    }


    public static URL buildJarUrl(String fileUrlString, String entryPath) throws MalformedURLException {
        String safeString = makeSafeForJarUrl(fileUrlString);
        StringBuilder sb = new StringBuilder();
        sb.append("jar:");
        sb.append(safeString);
        sb.append("!/");
        if (entryPath != null) {
            sb.append(makeSafeForJarUrl(entryPath));
        }
        return new URL(sb.toString());
    }


    public static URL buildJarSafeUrl(File file) throws MalformedURLException {
        String safe = makeSafeForJarUrl(file.toURI().toString());
        return new URL(safe);
    }


    /*
     * 在markt的桌面上进行测试时, 每次迭代在使用String.replaceAll()时需要大约1420ns.
     *
     * 切换实现以使用预编译模式和Pattern.matcher(input).replaceAll(replacement)将此减少约10％.
     *
     * Note: 鉴于单次迭代的绝对时间非常短, 即使对于具有1000个JAR的Web应用程序，这只会增加~3ms.
     *       因此，不太可能需要进一步优化.
     */
    /*
     * 如果将来需要处理其他异常序列，请将其划分为单独的方法.
     */
    private static String makeSafeForJarUrl(String input) {
        // 因为 "!/" 在 JAR URL中有特殊意义, 确保序列被正确转义.
        String tmp = PATTERN_EXCLAMATION_MARK.matcher(input).replaceAll("%21/");
        // Tomcat 自定义的 jar:war: URL 处理视为 */ 和 ^/ 作为特殊的
        tmp = PATTERN_CARET.matcher(tmp).replaceAll("%5e/");
        tmp = PATTERN_ASTERISK.matcher(tmp).replaceAll("%2a/");
        if (PATTERN_CUSTOM != null) {
            tmp = PATTERN_CUSTOM.matcher(tmp).replaceAll(REPLACE_CUSTOM);
        }
        return tmp;
    }


    /**
     * 转换<code>war:file:...</code>格式的 URL 为<code>jar:file:...</code>.
     *
     * @param warUrl 要转换的WAR URL
     *
     * @return 等效的JAR URL
     *
     * @throws MalformedURLException 如果转换失败
     */
    public static URL warToJar(URL warUrl) throws MalformedURLException {
        // 假设规范是绝对的并且开始 war:file:/...
        String file = warUrl.getFile();
        if (file.contains("*/")) {
            file = file.replaceFirst("\\*/", "!/");
        } else if (file.contains("^/")) {
            file = file.replaceFirst("\\^/", "!/");
        } else if (PATTERN_CUSTOM != null) {
            file = file.replaceFirst(PATTERN_CUSTOM.pattern(), "!/");
        }

        return new URL("jar", warUrl.getHost(), warUrl.getPort(), file);
    }


    public static String getWarSeparator() {
        return WAR_SEPARATOR;
    }
}
