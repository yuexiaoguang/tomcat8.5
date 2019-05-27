package org.apache.tomcat.util.buf;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 此类为ByteChunk和CharChunk实现了String缓存.
 */
public class StringCache {


    private static final Log log = LogFactory.getLog(StringCache.class);


    // ------------------------------------------------------- Static Variables


    /**
     * Enabled ?
     */
    protected static boolean byteEnabled = ("true".equals(System.getProperty(
            "tomcat.util.buf.StringCache.byte.enabled", "false")));


    protected static boolean charEnabled = ("true".equals(System.getProperty(
            "tomcat.util.buf.StringCache.char.enabled", "false")));


    protected static int trainThreshold = Integer.parseInt(System.getProperty(
            "tomcat.util.buf.StringCache.trainThreshold", "20000"));


    protected static int cacheSize = Integer.parseInt(System.getProperty(
            "tomcat.util.buf.StringCache.cacheSize", "200"));


    protected static final int maxStringSize =
            Integer.parseInt(System.getProperty(
                    "tomcat.util.buf.StringCache.maxStringSize", "128"));


   /**
     * 用于字节块.
     */
    protected static final HashMap<ByteEntry,int[]> bcStats =
            new HashMap<>(cacheSize);


    /**
     * 字节块的toString计数.
     */
    protected static int bcCount = 0;


    /**
     * 字节块的缓存.
     */
    protected static ByteEntry[] bcCache = null;


    /**
     * 用于字符块.
     */
    protected static final HashMap<CharEntry,int[]> ccStats =
            new HashMap<>(cacheSize);


    /**
     * char块的toString计数.
     */
    protected static int ccCount = 0;


    /**
     * 字符块的缓存.
     */
    protected static CharEntry[] ccCache = null;


    /**
     * 访问计数.
     */
    protected static int accessCount = 0;


    /**
     * 命中数.
     */
    protected static int hitCount = 0;


    // ------------------------------------------------------------ Properties


    public int getCacheSize() {
        return cacheSize;
    }


    public void setCacheSize(int cacheSize) {
        StringCache.cacheSize = cacheSize;
    }


    public boolean getByteEnabled() {
        return byteEnabled;
    }


    public void setByteEnabled(boolean byteEnabled) {
        StringCache.byteEnabled = byteEnabled;
    }


    public boolean getCharEnabled() {
        return charEnabled;
    }


    public void setCharEnabled(boolean charEnabled) {
        StringCache.charEnabled = charEnabled;
    }


    public int getTrainThreshold() {
        return trainThreshold;
    }


    public void setTrainThreshold(int trainThreshold) {
        StringCache.trainThreshold = trainThreshold;
    }


    public int getAccessCount() {
        return accessCount;
    }


    public int getHitCount() {
        return hitCount;
    }


    // -------------------------------------------------- Public Static Methods


    public void reset() {
        hitCount = 0;
        accessCount = 0;
        synchronized (bcStats) {
            bcCache = null;
            bcCount = 0;
        }
        synchronized (ccStats) {
            ccCache = null;
            ccCount = 0;
        }
    }


    public static String toString(ByteChunk bc) {

        // 如果缓存是 null, 然后禁用缓存, or we're still training
        if (bcCache == null) {
            String value = bc.toStringInternal();
            if (byteEnabled && (value.length() < maxStringSize)) {
                // If training, everything is synced
                synchronized (bcStats) {
                    // 如果在等待锁时, 在先前的调用上生成了缓存, 只返回刚刚计算的toString值
                    if (bcCache != null) {
                        return value;
                    }
                    // 两种情况: 刚刚超过 train 数量, 在这种情况下, 必须创建缓存, 或者只是更新字符串的计数
                    if (bcCount > trainThreshold) {
                        long t1 = System.currentTimeMillis();
                        // 根据出现次数对条目进行排序
                        TreeMap<Integer,ArrayList<ByteEntry>> tempMap =
                                new TreeMap<>();
                        for (Entry<ByteEntry,int[]> item : bcStats.entrySet()) {
                            ByteEntry entry = item.getKey();
                            int[] countA = item.getValue();
                            Integer count = Integer.valueOf(countA[0]);
                            // Add to the list for that count
                            ArrayList<ByteEntry> list = tempMap.get(count);
                            if (list == null) {
                                // Create list
                                list = new ArrayList<>();
                                tempMap.put(count, list);
                            }
                            list.add(entry);
                        }
                        // 分配正确大小的数组
                        int size = bcStats.size();
                        if (size > cacheSize) {
                            size = cacheSize;
                        }
                        ByteEntry[] tempbcCache = new ByteEntry[size];
                        // 使用字母顺序和哑插入排序填充它
                        ByteChunk tempChunk = new ByteChunk();
                        int n = 0;
                        while (n < size) {
                            Object key = tempMap.lastKey();
                            ArrayList<ByteEntry> list = tempMap.get(key);
                            for (int i = 0; i < list.size() && n < size; i++) {
                                ByteEntry entry = list.get(i);
                                tempChunk.setBytes(entry.name, 0,
                                        entry.name.length);
                                int insertPos = findClosest(tempChunk,
                                        tempbcCache, n);
                                if (insertPos == n) {
                                    tempbcCache[n + 1] = entry;
                                } else {
                                    System.arraycopy(tempbcCache, insertPos + 1,
                                            tempbcCache, insertPos + 2,
                                            n - insertPos - 1);
                                    tempbcCache[insertPos + 1] = entry;
                                }
                                n++;
                            }
                            tempMap.remove(key);
                        }
                        bcCount = 0;
                        bcStats.clear();
                        bcCache = tempbcCache;
                        if (log.isDebugEnabled()) {
                            long t2 = System.currentTimeMillis();
                            log.debug("ByteCache generation time: " +
                                    (t2 - t1) + "ms");
                        }
                    } else {
                        bcCount++;
                        // 为了查找分配新的ByteEntry
                        ByteEntry entry = new ByteEntry();
                        entry.value = value;
                        int[] count = bcStats.get(entry);
                        if (count == null) {
                            int end = bc.getEnd();
                            int start = bc.getStart();
                            // 创建字节数组和复制字节
                            entry.name = new byte[bc.getLength()];
                            System.arraycopy(bc.getBuffer(), start, entry.name,
                                    0, end - start);
                            // 设置编码
                            entry.charset = bc.getCharset();
                            // 将出现次数初始化为1
                            count = new int[1];
                            count[0] = 1;
                            // Set in the stats hash map
                            bcStats.put(entry, count);
                        } else {
                            count[0] = count[0] + 1;
                        }
                    }
                }
            }
            return value;
        } else {
            accessCount++;
            // 找到相应的字符串
            String result = find(bc);
            if (result == null) {
                return bc.toStringInternal();
            }
            // Note: 不关心统计数据的安全性
            hitCount++;
            return result;
        }

    }


    public static String toString(CharChunk cc) {

        // 如果缓存是 null, 然后禁用缓存, or we're still training
        if (ccCache == null) {
            String value = cc.toStringInternal();
            if (charEnabled && (value.length() < maxStringSize)) {
                // If training, everything is synced
                synchronized (ccStats) {
                    // 如果在等待锁时, 在先前的调用上生成了缓存, 只返回刚刚计算的toString值
                    if (ccCache != null) {
                        return value;
                    }
                    // 两种情况: 刚刚超过 train 数量, 在这种情况下, 必须创建缓存, 或者只是更新字符串的计数
                    if (ccCount > trainThreshold) {
                        long t1 = System.currentTimeMillis();
                        // 根据出现次数对条目进行排序
                        TreeMap<Integer,ArrayList<CharEntry>> tempMap =
                                new TreeMap<>();
                        for (Entry<CharEntry,int[]> item : ccStats.entrySet()) {
                            CharEntry entry = item.getKey();
                            int[] countA = item.getValue();
                            Integer count = Integer.valueOf(countA[0]);
                            // Add to the list for that count
                            ArrayList<CharEntry> list = tempMap.get(count);
                            if (list == null) {
                                // Create list
                                list = new ArrayList<>();
                                tempMap.put(count, list);
                            }
                            list.add(entry);
                        }
                        // 分配正确大小的数组
                        int size = ccStats.size();
                        if (size > cacheSize) {
                            size = cacheSize;
                        }
                        CharEntry[] tempccCache = new CharEntry[size];
                        // 使用字母顺序和哑插入排序填充它
                        CharChunk tempChunk = new CharChunk();
                        int n = 0;
                        while (n < size) {
                            Object key = tempMap.lastKey();
                            ArrayList<CharEntry> list = tempMap.get(key);
                            for (int i = 0; i < list.size() && n < size; i++) {
                                CharEntry entry = list.get(i);
                                tempChunk.setChars(entry.name, 0,
                                        entry.name.length);
                                int insertPos = findClosest(tempChunk,
                                        tempccCache, n);
                                if (insertPos == n) {
                                    tempccCache[n + 1] = entry;
                                } else {
                                    System.arraycopy(tempccCache, insertPos + 1,
                                            tempccCache, insertPos + 2,
                                            n - insertPos - 1);
                                    tempccCache[insertPos + 1] = entry;
                                }
                                n++;
                            }
                            tempMap.remove(key);
                        }
                        ccCount = 0;
                        ccStats.clear();
                        ccCache = tempccCache;
                        if (log.isDebugEnabled()) {
                            long t2 = System.currentTimeMillis();
                            log.debug("CharCache generation time: " +
                                    (t2 - t1) + "ms");
                        }
                    } else {
                        ccCount++;
                        // 为了查找分配新的CharEntry
                        CharEntry entry = new CharEntry();
                        entry.value = value;
                        int[] count = ccStats.get(entry);
                        if (count == null) {
                            int end = cc.getEnd();
                            int start = cc.getStart();
                            // Create char array and copy chars
                            entry.name = new char[cc.getLength()];
                            System.arraycopy(cc.getBuffer(), start, entry.name,
                                    0, end - start);
                            // 将出现次数初始化为1
                            count = new int[1];
                            count[0] = 1;
                            // Set in the stats hash map
                            ccStats.put(entry, count);
                        } else {
                            count[0] = count[0] + 1;
                        }
                    }
                }
            }
            return value;
        } else {
            accessCount++;
            // 找到相应的字符串
            String result = find(cc);
            if (result == null) {
                return cc.toStringInternal();
            }
            // Note: 不关心统计数据的安全性
            hitCount++;
            return result;
        }

    }


    // ----------------------------------------------------- Protected Methods


    /**
     * 将给定的字节块与字节数组进行比较.
     * 
     * @param name 要比较的名称
     * @param compareTo 要比较的数据
     * 
     * @return -1, 0, +1 如果低于，等于或高于String.
     */
    protected static final int compare(ByteChunk name, byte[] compareTo) {
        int result = 0;

        byte[] b = name.getBuffer();
        int start = name.getStart();
        int end = name.getEnd();
        int len = compareTo.length;

        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (b[i + start] > compareTo[i]) {
                result = 1;
            } else if (b[i + start] < compareTo[i]) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length > (end - start)) {
                result = -1;
            } else if (compareTo.length < (end - start)) {
                result = 1;
            }
        }
        return result;
    }


    /**
     * 在缓存中查找其名称对应的条目并返回关联的String.
     * 
     * @param name 要找的名称
     * 
     * @return 相应的值
     */
    protected static final String find(ByteChunk name) {
        int pos = findClosest(name, bcCache, bcCache.length);
        if ((pos < 0) || (compare(name, bcCache[pos].name) != 0)
                || !(name.getCharset().equals(bcCache[pos].charset))) {
            return null;
        } else {
            return bcCache[pos].value;
        }
    }


    /**
     * 在排序的 Map 元素数组中查找其名称对应的条目.
     * 这将返回给定数组中最接近的低于或相等项的索引.
     * 
     * @param name 要查找的名称
     * @param array 要查看的数组
     * @param len 数组的有效长度
     * 
     * @return 最佳匹配的位置
     */
    protected static final int findClosest(ByteChunk name, ByteEntry[] array,
            int len) {

        int a = 0;
        int b = len - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }

        if (compare(name, array[0].name) < 0) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) >>> 1;
            int result = compare(name, array[i].name);
            if (result == 1) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = compare(name, array[b].name);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }

    }


    /**
     * 将给定的char 块与char数组进行比较.
     * 
     * @param name 要比较的名称
     * @param compareTo 要比较的数据
     * 
     * @return -1, 0, +1 如果低于，等于或高于String.
     */
    protected static final int compare(CharChunk name, char[] compareTo) {
        int result = 0;

        char[] c = name.getBuffer();
        int start = name.getStart();
        int end = name.getEnd();
        int len = compareTo.length;

        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (c[i + start] > compareTo[i]) {
                result = 1;
            } else if (c[i + start] < compareTo[i]) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length > (end - start)) {
                result = -1;
            } else if (compareTo.length < (end - start)) {
                result = 1;
            }
        }
        return result;
    }


    /**
     * 在缓存中查找其名称对应的条目并返回关联的String.
     * 
     * @param name 要查找的名称
     * 
     * @return 相应的值
     */
    protected static final String find(CharChunk name) {
        int pos = findClosest(name, ccCache, ccCache.length);
        if ((pos < 0) || (compare(name, ccCache[pos].name) != 0)) {
            return null;
        } else {
            return ccCache[pos].value;
        }
    }


    /**
     * 在排序的 Map 元素数组中查找其名称对应的条目.
     * 这将返回给定数组中最接近的低于或相等项的索引.
     * 
     * @param name 要查找的名称
     * @param array 要查找的数组
     * @param len 数组的有效长度
     * 
     * @return 最佳匹配的位置
     */
    protected static final int findClosest(CharChunk name, CharEntry[] array,
            int len) {

        int a = 0;
        int b = len - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }

        if (compare(name, array[0].name) < 0 ) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) >>> 1;
            int result = compare(name, array[i].name);
            if (result == 1) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = compare(name, array[b].name);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }

    }


    // -------------------------------------------------- ByteEntry Inner Class


    private static class ByteEntry {

        private byte[] name = null;
        private Charset charset = null;
        private String value = null;

        @Override
        public String toString() {
            return value;
        }
        @Override
        public int hashCode() {
            return value.hashCode();
        }
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ByteEntry) {
                return value.equals(((ByteEntry) obj).value);
            }
            return false;
        }
    }


    // -------------------------------------------------- CharEntry Inner Class


    private static class CharEntry {

        private char[] name = null;
        private String value = null;

        @Override
        public String toString() {
            return value;
        }
        @Override
        public int hashCode() {
            return value.hashCode();
        }
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CharEntry) {
                return value.equals(((CharEntry) obj).value);
            }
            return false;
        }
    }
}
