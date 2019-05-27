package org.apache.catalina.webresources;

import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.catalina.WebResource;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class Cache {

    private static final Log log = LogFactory.getLog(Cache.class);
    protected static final StringManager sm = StringManager.getManager(Cache.class);

    private static final long TARGET_FREE_PERCENT_GET = 5;
    private static final long TARGET_FREE_PERCENT_BACKGROUND = 10;

    // objectMaxSize must be < maxSize/20
    private static final int OBJECT_MAX_SIZE_FACTOR = 20;

    private final StandardRoot root;
    private final AtomicLong size = new AtomicLong(0);

    private long ttl = 5000;
    private long maxSize = 10 * 1024 * 1024;
    private int objectMaxSize = (int) maxSize/OBJECT_MAX_SIZE_FACTOR;

    private AtomicLong lookupCount = new AtomicLong(0);
    private AtomicLong hitCount = new AtomicLong(0);

    private final ConcurrentMap<String,CachedResource> resourceCache =
            new ConcurrentHashMap<>();

    public Cache(StandardRoot root) {
        this.root = root;
    }

    protected WebResource getResource(String path, boolean useClassLoaderResources) {

        if (noCache(path)) {
            return root.getResourceInternal(path, useClassLoaderResources);
        }

        lookupCount.incrementAndGet();

        CachedResource cacheEntry = resourceCache.get(path);

        if (cacheEntry != null && !cacheEntry.validateResource(useClassLoaderResources)) {
            removeCacheEntry(path);
            cacheEntry = null;
        }

        if (cacheEntry == null) {
            // 本地副本以确保一致性
            int objectMaxSizeBytes = getObjectMaxSizeBytes();
            CachedResource newCacheEntry =
                    new CachedResource(this, root, path, getTtl(), objectMaxSizeBytes);

            // 并发调用者将以相同的 CachedResource 实例结尾
            cacheEntry = resourceCache.putIfAbsent(path, newCacheEntry);

            if (cacheEntry == null) {
                // newCacheEntry 被插入缓存 - 验证它
                cacheEntry = newCacheEntry;
                cacheEntry.validateResource(useClassLoaderResources);

                // 即使资源内容大于 objectMaxSizeBytes
                // 缓存资源元数据仍然有好处

                long delta = cacheEntry.getSize();
                size.addAndGet(delta);

                if (size.get() > maxSize) {
                    // 快速处理无序的资源. 交换缓存的效率(较年轻的条目可能会在旧版本之前被删除), 因为这是在请求处理的关键路径上
                    long targetSize = maxSize * (100 - TARGET_FREE_PERCENT_GET) / 100;
                    long newSize = evict(targetSize, resourceCache.values().iterator());
                    if (newSize > maxSize) {
                        // 无法为该资源创建足够的空间
                        // 从缓存中删除它
                        removeCacheEntry(path);
                        log.warn(sm.getString("cache.addFail", path, root.getContext().getName()));
                    }
                }
            } else {
                // 另一个线程将缓存项添加到缓存中
                // 确保验证
                cacheEntry.validateResource(useClassLoaderResources);
            }
        } else {
            hitCount.incrementAndGet();
        }

        return cacheEntry;
    }

    protected WebResource[] getResources(String path, boolean useClassLoaderResources) {
        lookupCount.incrementAndGet();

        // 不要调用 noCache(path), 因为类加载器只缓存单个资源. 因此, 始终缓存集合

        CachedResource cacheEntry = resourceCache.get(path);

        if (cacheEntry != null && !cacheEntry.validateResources(useClassLoaderResources)) {
            removeCacheEntry(path);
            cacheEntry = null;
        }

        if (cacheEntry == null) {
            // 本地副本以确保一致性
            int objectMaxSizeBytes = getObjectMaxSizeBytes();
            CachedResource newCacheEntry =
                    new CachedResource(this, root, path, getTtl(), objectMaxSizeBytes);

            // 并发调用者将以相同的 CachedResource 实例结尾
            cacheEntry = resourceCache.putIfAbsent(path, newCacheEntry);

            if (cacheEntry == null) {
                // newCacheEntry 被插入缓存 - 验证它
                cacheEntry = newCacheEntry;
                cacheEntry.validateResources(useClassLoaderResources);

                // 内容不会被缓存，但仍然需要元数据大小
                long delta = cacheEntry.getSize();
                size.addAndGet(delta);

                if (size.get() > maxSize) {
                    // 快速处理无序的资源. 交换缓存的效率(较年轻的条目可能会在旧版本之前被删除), 因为这是在请求处理的关键路径上
                    long targetSize = maxSize * (100 - TARGET_FREE_PERCENT_GET) / 100;
                    long newSize = evict(targetSize, resourceCache.values().iterator());
                    if (newSize > maxSize) {
                        // 无法为该资源创建足够的空间
                        // 从缓存中删除它
                        removeCacheEntry(path);
                        log.warn(sm.getString("cache.addFail", path));
                    }
                }
            } else {
                // 另一个线程将缓存项添加到缓存中
                // 确保验证
                cacheEntry.validateResources(useClassLoaderResources);
            }
        } else {
            hitCount.incrementAndGet();
        }

        return cacheEntry.getWebResources();
    }

    protected void backgroundProcess() {
        // 用最近最少使用的第一个来创建所有缓存资源的有序集合. 这是一个后台过程，所以可以花时间先排序元素
        TreeSet<CachedResource> orderedResources =
                new TreeSet<>(new EvictionOrder());
        orderedResources.addAll(resourceCache.values());

        Iterator<CachedResource> iter = orderedResources.iterator();

        long targetSize =
                maxSize * (100 - TARGET_FREE_PERCENT_BACKGROUND) / 100;
        long newSize = evict(targetSize, iter);

        if (newSize > targetSize) {
            log.info(sm.getString("cache.backgroundEvictFail",
                    Long.valueOf(TARGET_FREE_PERCENT_BACKGROUND),
                    root.getContext().getName(),
                    Long.valueOf(newSize / 1024)));
        }
    }

    private boolean noCache(String path) {
        // 不要缓存类. 类加载器处理这个.
        // 不要缓存 JAR. ResourceSet 处理这个.
        if ((path.endsWith(".class") &&
                (path.startsWith("/WEB-INF/classes/") || path.startsWith("/WEB-INF/lib/")))
                ||
                (path.startsWith("/WEB-INF/lib/") && path.endsWith(".jar"))) {
            return true;
        }
        return false;
    }

    private long evict(long targetSize, Iterator<CachedResource> iter) {

        long now = System.currentTimeMillis();

        long newSize = size.get();

        while (newSize > targetSize && iter.hasNext()) {
            CachedResource resource = iter.next();

            // 不要终止在TTL中检查过的任何内容
            if (resource.getNextCheck() > now) {
                continue;
            }

            // 从缓存中删除条目
            removeCacheEntry(resource.getWebappPath());

            newSize = size.get();
        }

        return newSize;
    }

    void removeCacheEntry(String path) {
        // 同时调用相同路径, 只删除一次条目，只更新一次缓存大小.
        CachedResource cachedResource = resourceCache.remove(path);
        if (cachedResource != null) {
            long delta = cachedResource.getSize();
            size.addAndGet(-delta);
        }
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public long getMaxSize() {
        // 内部字节, 外部KB
        return maxSize / 1024;
    }

    public void setMaxSize(long maxSize) {
    	// 内部字节, 外部KB
        this.maxSize = maxSize * 1024;
    }

    public long getLookupCount() {
        return lookupCount.get();
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public void setObjectMaxSize(int objectMaxSize) {
        if (objectMaxSize * 1024L > Integer.MAX_VALUE) {
            log.warn(sm.getString("cache.objectMaxSizeTooBigBytes", Integer.valueOf(objectMaxSize)));
            this.objectMaxSize = Integer.MAX_VALUE;
        }
        // 内部字节, 外部KB
        this.objectMaxSize = objectMaxSize * 1024;
    }

    public int getObjectMaxSize() {
    	// 内部字节, 外部KB
        return objectMaxSize / 1024;
    }

    public int getObjectMaxSizeBytes() {
        return objectMaxSize;
    }

    void enforceObjectMaxSizeLimit() {
        long limit = maxSize / OBJECT_MAX_SIZE_FACTOR;
        if (limit > Integer.MAX_VALUE) {
            return;
        }
        if (objectMaxSize > limit) {
            log.warn(sm.getString("cache.objectMaxSizeTooBig",
                    Integer.valueOf(objectMaxSize / 1024), Integer.valueOf((int)limit / 1024)));
            objectMaxSize = (int) limit;
        }
    }

    public void clear() {
        resourceCache.clear();
        size.set(0);
    }

    public long getSize() {
        return size.get() / 1024;
    }

    private static class EvictionOrder implements Comparator<CachedResource> {

        @Override
        public int compare(CachedResource cr1, CachedResource cr2) {
            long nc1 = cr1.getNextCheck();
            long nc2 = cr2.getNextCheck();

            // 最古老的资源应该是第一 (所以迭代器从最老到最年轻.
            if (nc1 == nc2) {
                return 0;
            } else if (nc1 > nc2) {
                return -1;
            } else {
                return 1;
            }
        }
    }
}
