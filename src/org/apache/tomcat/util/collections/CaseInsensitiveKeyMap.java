package org.apache.tomcat.util.collections;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.tomcat.util.res.StringManager;

/**
 * 使用不区分大小写的（使用{@link Locale＃ENGLISH}）字符串作为键的Map实现.
 * <p>
 * Key必须是 {@link String}实例. 请注意, 这意味着不允许<code>null</ code>键.
 * <p>
 * 此实现不是线程安全的.
 *
 * @param <V> 放置在此Map中的值的类型.
 */
public class CaseInsensitiveKeyMap<V> extends AbstractMap<String,V> {

    private static final StringManager sm =
            StringManager.getManager(CaseInsensitiveKeyMap.class);

    private final Map<Key,V> map = new HashMap<>();


    @Override
    public V get(Object key) {
        return map.get(Key.getInstance(key));
    }


    @Override
    public V put(String key, V value) {
        Key caseInsensitiveKey = Key.getInstance(key);
        if (caseInsensitiveKey == null) {
            throw new NullPointerException(sm.getString("caseInsensitiveKeyMap.nullKey"));
        }
        return map.put(caseInsensitiveKey, value);
    }


    /**
     * {@inheritDoc}
     * <p>
     * <b>请谨慎使用此方法</b>. 如果输入 Map 包含重复的 Key, 当以不区分大小写的方式比较 Key 时, 通过此方法插入时某些值将丢失.
     */
    @Override
    public void putAll(Map<? extends String, ? extends V> m) {
        super.putAll(m);
    }


    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(Key.getInstance(key));
    }


    @Override
    public V remove(Object key) {
        return map.remove(Key.getInstance(key));
    }


    @Override
    public Set<Entry<String, V>> entrySet() {
        return new EntrySet<>(map.entrySet());
    }


    private static class EntrySet<V> extends AbstractSet<Entry<String,V>> {

        private final Set<Entry<Key,V>> entrySet;

        public EntrySet(Set<Map.Entry<Key,V>> entrySet) {
            this.entrySet = entrySet;
        }

        @Override
        public Iterator<Entry<String,V>> iterator() {
            return new EntryIterator<>(entrySet.iterator());
        }

        @Override
        public int size() {
            return entrySet.size();
        }
    }


    private static class EntryIterator<V> implements Iterator<Entry<String,V>> {

        private final Iterator<Entry<Key,V>> iterator;

        public EntryIterator(Iterator<Entry<Key,V>> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Entry<String,V> next() {
            Entry<Key,V> entry = iterator.next();
            return new EntryImpl<>(entry.getKey().getKey(), entry.getValue());
        }

        @Override
        public void remove() {
            iterator.remove();
        }
    }


    private static class EntryImpl<V> implements Entry<String,V> {

        private final String key;
        private final V value;

        public EntryImpl(String key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }
    }

    private static class Key {

        private final String key;
        private final String lcKey;

        private Key(String key) {
            this.key = key;
            this.lcKey = key.toLowerCase(Locale.ENGLISH);
        }

        public String getKey() {
            return key;
        }

        @Override
        public int hashCode() {
            return lcKey.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Key other = (Key) obj;
            return lcKey.equals(other.lcKey);
        }

        public static Key getInstance(Object o) {
            if (o instanceof String) {
                return new Key((String) o);
            }
            return null;
        }
    }
}
