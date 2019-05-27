package org.apache.tomcat.util.file;

import java.util.Set;

/**
 * <p>这是一个匹配文件globs的实用程序类.
 * 该类派生自org.apache.tools.ant.types.selectors.SelectorUtils.
 * </p>
 * <p>所有方法都是 static.</p>
 */
public final class Matcher {

    /**
     * 测试给定文件名是否与给定集中的文件名模式匹配. 匹配以区分大小写的方式执行.
     *
     * @param patternSet 要匹配的模式集合. 不能是<code>null</code>.
     * @param fileName 要匹配的文件名. 不能是<code>null</code>. 它必须只是一个文件名, 不包括路径.
     *
     * @return <code>true</code> 如果集合中有模式与文件名匹配, 否则<code>false</code>.
     */
    public static boolean matchName(Set<String> patternSet, String fileName) {
        char[] fileNameArray = fileName.toCharArray();
        for (String pattern: patternSet) {
            if (match(pattern, fileNameArray, true)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 测试字符串是否与模式匹配.
     * 该模式可能包含两个特殊字符:<br>
     * '*' 表示零个或多个字符<br>
     * '?' 意味着只有一个字符
     *
     * @param pattern 与之匹配的模式. 不能是<code>null</code>.
     * @param str     必须与模式匹配的字符串. 不能是<code>null</code>.
     * @param caseSensitive 是否应以区分大小写进行匹配.
     *
     *
     * @return <code>true</code> 如果字符串与模式匹配,
     *         否则<code>false</code>.
     */
    public static boolean match(String pattern, String str,
            boolean caseSensitive) {

        return match(pattern, str.toCharArray(), caseSensitive);
    }


    /**
     * 测试字符串是否与模式匹配.
     * 该模式可能包含两个特殊字符:<br>
     * '*' 表示零个或多个字符<br>
     * '?' 意味着只有一个字符
     *
     * @param pattern 与之匹配的模式. 不能是<code>null</code>.
     * @param strArr  必须与模式匹配的字符串. 不能是<code>null</code>.
     * @param caseSensitive 是否应以区分大小写进行匹配.
     *
     *
     * @return <code>true</code> 如果字符串与模式匹配,
     *         否则<code>false</code>
     */
    private static boolean match(String pattern, char[] strArr,
                                boolean caseSensitive) {
        char[] patArr = pattern.toCharArray();
        int patIdxStart = 0;
        int patIdxEnd = patArr.length - 1;
        int strIdxStart = 0;
        int strIdxEnd = strArr.length - 1;
        char ch;

        boolean containsStar = false;
        for (int i = 0; i < patArr.length; i++) {
            if (patArr[i] == '*') {
                containsStar = true;
                break;
            }
        }

        if (!containsStar) {
            // 没有 '*', 所以制作一条捷径
            if (patIdxEnd != strIdxEnd) {
                return false; // 模式和字符串的大小不同
            }
            for (int i = 0; i <= patIdxEnd; i++) {
                ch = patArr[i];
                if (ch != '?') {
                    if (different(caseSensitive, ch, strArr[i])) {
                        return false; // Character mismatch
                    }
                }
            }
            return true; // 字符串与模式匹配
        }

        if (patIdxEnd == 0) {
            return true; // 模式只包含 '*', 匹配所有的
        }

        // 处理第一个星之前的字符
        while (true) {
            ch = patArr[patIdxStart];
            if (ch == '*' || strIdxStart > strIdxEnd) {
                break;
            }
            if (ch != '?') {
                if (different(caseSensitive, ch, strArr[strIdxStart])) {
                    return false; // Character mismatch
                }
            }
            patIdxStart++;
            strIdxStart++;
        }
        if (strIdxStart > strIdxEnd) {
            // 使用字符串中的所有字符. 检查模式中是否只剩下 '*'. 如果是, 则成功. 否则失败.
            return allStars(patArr, patIdxStart, patIdxEnd);
        }

        // 处理最后一颗星之后字符
        while (true) {
            ch = patArr[patIdxEnd];
            if (ch == '*' || strIdxStart > strIdxEnd) {
                break;
            }
            if (ch != '?') {
                if (different(caseSensitive, ch, strArr[strIdxEnd])) {
                    return false; // Character mismatch
                }
            }
            patIdxEnd--;
            strIdxEnd--;
        }
        if (strIdxStart > strIdxEnd) {
            // 使用字符串中的所有字符. 检查模式中是否只剩下 '*'. 如果是, 则成功. 否则失败.
            return allStars(patArr, patIdxStart, patIdxEnd);
        }

        // 处理星之间的模式. padIdxStart 和 patIdxEnd 始终指向'*'.
        while (patIdxStart != patIdxEnd && strIdxStart <= strIdxEnd) {
            int patIdxTmp = -1;
            for (int i = patIdxStart + 1; i <= patIdxEnd; i++) {
                if (patArr[i] == '*') {
                    patIdxTmp = i;
                    break;
                }
            }
            if (patIdxTmp == patIdxStart + 1) {
                // 两颗星彼此相邻, 跳过第一个.
                patIdxStart++;
                continue;
            }
            // 在 strIdxStart 和 strIdxEnd 之间的str中找到 padIdxStart 和 padIdxTmp 之间的模式
            int patLength = (patIdxTmp - patIdxStart - 1);
            int strLength = (strIdxEnd - strIdxStart + 1);
            int foundIdx = -1;
            strLoop:
            for (int i = 0; i <= strLength - patLength; i++) {
                for (int j = 0; j < patLength; j++) {
                    ch = patArr[patIdxStart + j + 1];
                    if (ch != '?') {
                        if (different(caseSensitive, ch,
                                      strArr[strIdxStart + i + j])) {
                            continue strLoop;
                        }
                    }
                }

                foundIdx = strIdxStart + i;
                break;
            }

            if (foundIdx == -1) {
                return false;
            }

            patIdxStart = patIdxTmp;
            strIdxStart = foundIdx + patLength;
        }

        // 使用字符串中的所有字符. 检查模式中是否只剩下 '*'. 如果是, 则成功. 否则失败.
        return allStars(patArr, patIdxStart, patIdxEnd);
    }

    private static boolean allStars(char[] chars, int start, int end) {
        for (int i = start; i <= end; ++i) {
            if (chars[i] != '*') {
                return false;
            }
        }
        return true;
    }

    private static boolean different(
        boolean caseSensitive, char ch, char other) {
        return caseSensitive
            ? ch != other
            : Character.toUpperCase(ch) != Character.toUpperCase(other);
    }

}
