package org.apache.tomcat.util.http.parser;

import java.io.IOException;
import java.io.StringReader;

import org.apache.tomcat.util.collections.ConcurrentCache;

/**
 * 解析content-type header的结果的缓存.
 */
public class MediaTypeCache {

    private final ConcurrentCache<String,String[]> cache;

    public MediaTypeCache(int size) {
        cache = new ConcurrentCache<>(size);
    }

    /**
     * 在缓存中查找并返回缓存的值. 如果缓存中不存在匹配项, 创建一个新的解析器, 解析输入并将结果放在缓存中并返回给用户.
     *
     * @param input 要解析的content-type header值
     * 
     * @return      两个元素的String数组. 第一个元素是媒体类型而不是字符集，第二个元素是字符集
     */
    public String[] parse(String input) {
        String[] result = cache.get(input);

        if (result != null) {
            return result;
        }

        MediaType m = null;
        try {
            m = MediaType.parseMediaType(new StringReader(input));
        } catch (IOException e) {
            // Ignore - return null
        }
        if (m != null) {
            result = new String[] {m.toStringNoCharset(), m.getCharset()};
            cache.put(input, result);
        }

        return result;
    }
}
