package org.apache.el.util;

import java.security.AccessController;
import java.security.PrivilegedAction;

public class Validation {

    // Java keywords, boolean literals & the null literal in alphabetical order
    private static final String invalidIdentifiers[] = { "abstract", "assert",
        "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends",
        "false", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new",
        "null", "package", "private", "protected", "public", "return", "short",
        "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "true", "try", "void", "volatile",
        "while" };

    private static final boolean IS_SECURITY_ENABLED =
            (System.getSecurityManager() != null);

    private static final boolean SKIP_IDENTIFIER_CHECK;

    static {
        String skipIdentifierCheckStr;
        if (IS_SECURITY_ENABLED) {
            skipIdentifierCheckStr = AccessController.doPrivileged(
                    new PrivilegedAction<String>(){
                        @Override
                        public String run() {
                            return System.getProperty(
                                    "org.apache.el.parser.SKIP_IDENTIFIER_CHECK",
                                    "false");
                        }
                    }
            );
        } else {
            skipIdentifierCheckStr = System.getProperty(
                    "org.apache.el.parser.SKIP_IDENTIFIER_CHECK", "false");
        }
        SKIP_IDENTIFIER_CHECK = Boolean.parseBoolean(skipIdentifierCheckStr);
    }


    private Validation() {
        // Utility class. Hide default constructor
    }

    /**
     * 测试字符串是否是Java标识符.
     * 请注意，此方法的行为取决于系统属性 {@code org.apache.el.parser.SKIP_IDENTIFIER_CHECK}
     *
     * @param key 要测试的字符串
     *
     * @return {@code true} 如果提供的String应被视为Java标识符, 否则 false
     */
    public static boolean isIdentifier(String key) {

        if (SKIP_IDENTIFIER_CHECK) {
            return true;
        }

        // 不应该是这种情况，但检查一下
        if (key == null || key.length() == 0) {
            return false;
        }

        // 检查已知无效值的列表
        int i = 0;
        int j = invalidIdentifiers.length;
        while (i < j) {
            int k = (i + j) >>> 1; // Avoid overflow
            int result = invalidIdentifiers[k].compareTo(key);
            if (result == 0) {
                return false;
            }
            if (result < 0) {
                i = k + 1;
            } else {
                j = k;
            }
        }

        // 检查具有更多限制的起始字符
        if (!Character.isJavaIdentifierStart(key.charAt(0))) {
            return false;
        }

        // 检查允许使用的每个剩余字符
        for (int idx = 1; idx < key.length(); idx++) {
            if (!Character.isJavaIdentifierPart(key.charAt(idx))) {
                return false;
            }
        }

        return true;
    }
}
