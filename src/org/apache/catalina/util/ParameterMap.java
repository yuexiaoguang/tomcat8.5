package org.apache.catalina.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.tomcat.util.res.StringManager;

/**
 * <strong>HashMap</strong>的扩展实现，包含一个<code>locked</code>属性.
 * 这个类可以用来安全地暴露Catalina内部参数映射对象到用户类，为了避免修改，不必克隆它们.
 * 当第一次创建, <code>ParmaeterMap</code>实例未锁定.
 */
public final class ParameterMap<K,V> implements Map<K,V>, Serializable {

    private static final long serialVersionUID = 2L;

    private final Map<K,V> delegatedMap;

    private final Map<K,V> unmodifiableDelegatedMap;


    public ParameterMap() {
        delegatedMap = new LinkedHashMap<>();
        unmodifiableDelegatedMap = Collections.unmodifiableMap(delegatedMap);
    }


    /**
     * @param initialCapacity 初始容量
     */
    public ParameterMap(int initialCapacity) {
        delegatedMap = new LinkedHashMap<>(initialCapacity);
        unmodifiableDelegatedMap = Collections.unmodifiableMap(delegatedMap);
    }


    /**
     * @param initialCapacity 初始容量
     * @param loadFactor 荷载系数
     */
    public ParameterMap(int initialCapacity, float loadFactor) {
        delegatedMap = new LinkedHashMap<>(initialCapacity, loadFactor);
        unmodifiableDelegatedMap = Collections.unmodifiableMap(delegatedMap);
    }


    /**
     * @param map 要复制的Map
     */
    public ParameterMap(Map<K,V> map) {
        delegatedMap = new LinkedHashMap<>(map);
        unmodifiableDelegatedMap = Collections.unmodifiableMap(delegatedMap);
    }


    /**
     * 这个参数map的当前锁定状态.
     */
    private boolean locked = false;


    /**
     * @return 这个参数map的当前锁定状态.
     */
    public boolean isLocked() {
        return locked;
    }


    /**
     * 设置这个参数map的当前锁定状态.
     *
     * @param locked The new locked state
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
    }


    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager("org.apache.catalina.util");


    /**
     * @exception IllegalStateException 如果这个map当前被锁定
     */
    @Override
    public void clear() {
        checkLocked();
        delegatedMap.clear();
    }


    /**
     * @exception IllegalStateException 如果这个map当前被锁定
     */
    @Override
    public V put(K key, V value) {
        checkLocked();
        return delegatedMap.put(key, value);
    }


    /**
     * @exception IllegalStateException 如果这个map当前被锁定
     */
    @Override
    public void putAll(Map<? extends K,? extends V> map) {
        checkLocked();
        delegatedMap.putAll(map);
    }


    /**
     * @exception IllegalStateException 如果这个map当前被锁定
     */
    @Override
    public V remove(Object key) {
        checkLocked();
        return delegatedMap.remove(key);
    }


    private void checkLocked() {
        if (locked) {
            throw new IllegalStateException(sm.getString("parameterMap.locked"));
        }
    }


    @Override
    public int size() {
        return delegatedMap.size();
    }


    @Override
    public boolean isEmpty() {
        return delegatedMap.isEmpty();
    }


    @Override
    public boolean containsKey(Object key) {
        return delegatedMap.containsKey(key);
    }


    @Override
    public boolean containsValue(Object value) {
        return delegatedMap.containsValue(value);
    }


    @Override
    public V get(Object key) {
        return delegatedMap.get(key);
    }


    /**
     * 返回一个<strong>不可编辑的</strong> {@link Set}视图, 包含这个map中的key, 如果它被锁定.
     */
    @Override
    public Set<K> keySet() {
        if (locked) {
            return unmodifiableDelegatedMap.keySet();
        }

        return delegatedMap.keySet();
    }


    /**
     * 返回一个<strong>不可编辑的</strong> {@link Collection}视图, 包含这个map中的值, 如果它被锁定.
     */
    @Override
    public Collection<V> values() {
        if (locked) {
            return unmodifiableDelegatedMap.values();
        }

        return delegatedMap.values();
    }


    /**
     * 返回一个<strong>不可编辑的</strong> {@link Set}视图, 包含这个map中的Entry, 如果它被锁定.
     */
    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        if (locked) {
            return unmodifiableDelegatedMap.entrySet();
        }

        return delegatedMap.entrySet();
    }
}
