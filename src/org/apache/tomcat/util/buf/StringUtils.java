package org.apache.tomcat.util.buf;

import java.util.Arrays;
import java.util.Collection;

/**
 * 用于从输入的给定集合（不是java.util.Set）构建分隔列表并将该列表作为字符串返回或将其附加到现有StringBuilder的实用程序方法.
 * 如果给定的集合为null或为空，则返回空字符串.
 */
public final class StringUtils {

    private static final String EMPTY_STRING = "";

    private StringUtils() {
        // Utility class
    }


    public static String join(String[] array) {
        if (array == null) {
            return EMPTY_STRING;
        }
        return join(Arrays.asList(array));
    }


    public static void join(String[] array, char separator, StringBuilder sb) {
        if (array == null) {
            return;
        }
        join(Arrays.asList(array), separator, sb);
    }


    public static String join(Collection<String> collection) {
        return join(collection, ',');
    }


    public static String join(Collection<String> collection, char separator) {
        // Shortcut
        if (collection == null || collection.isEmpty()) {
            return EMPTY_STRING;
        }

        StringBuilder result = new StringBuilder();
        join(collection, separator, result);
        return result.toString();
    }


    public static void join(Iterable<String> iterable, char separator, StringBuilder sb) {
        join(iterable, separator,
                new Function<String>() {@Override public String apply(String t) { return t; }}, sb);
    }


    public static <T> void join(T[] array, char separator, Function<T> function,
            StringBuilder sb) {
        if (array == null) {
            return;
        }
        join(Arrays.asList(array), separator, function, sb);
    }


    public static <T> void join(Iterable<T> iterable, char separator, Function<T> function,
            StringBuilder sb) {
        if (iterable == null) {
            return;
        }
        boolean first = true;
        for (T value : iterable) {
            if (first) {
                first = false;
            } else {
                sb.append(separator);
            }
            sb.append(function.apply(value));
        }
    }


    public interface Function<T> {
        public String apply(T t);
    }
}
