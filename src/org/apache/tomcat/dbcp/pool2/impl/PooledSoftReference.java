package org.apache.tomcat.dbcp.pool2.impl;

import java.lang.ref.SoftReference;

/**
 * 扩展{@link DefaultPooledObject}以包装池化的软引用.
 *
 * <p>此类旨在是线程安全的.</p>
 *
 * @param <T> 包装的SoftReference引用的底层对象的类型.
 */
public class PooledSoftReference<T> extends DefaultPooledObject<T> {

    /** 由此对象封装的SoftReference */
    private volatile SoftReference<T> reference;

    /**
     * @param reference 由池管理的SoftReference
     */
    public PooledSoftReference(final SoftReference<T> reference) {
        super(null);  // 在父级中取消硬引用
        this.reference = reference;
    }

    /**
     * 返回封装的SoftReference引用的对象.
     * <p>
     * 请注意, 如果已清除引用, 这个方法将返回 null.
     *
     * @return SoftReference引用的对象
     */
    @Override
    public T getObject() {
        return reference.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("Referenced Object: ");
        result.append(getObject().toString());
        result.append(", State: ");
        synchronized (this) {
            result.append(getState().toString());
        }
        return result.toString();
        // TODO add other attributes
        // TODO 在父级中封装状态和其他属性显示
    }

    /**
     * 返回此对象包装的SoftReference.
     *
     * @return 底层 SoftReference
     */
    public synchronized SoftReference<T> getReference() {
        return reference;
    }

    /**
     * 设置封装的引用.
     *
     * <p>此方法的存在是为了允许池保存新的未注册引用以跟踪已从池中检出的对象.
     * 实际参数应该是{@link #getObject()}在调用此方法之前返回的同一对象的引用.</p>
     *
     * @param reference 新引用
     */
    public synchronized void setReference(final SoftReference<T> reference) {
        this.reference = reference;
    }
}
