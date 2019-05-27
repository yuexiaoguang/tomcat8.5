package org.apache.tomcat.dbcp.pool2.impl;

import java.util.List;
import java.util.Map;

/**
 * 定义将通过JMX提供的方法.
 *
 * NOTE: 此接口仅用于定义将通过JMX提供的属性和方法. 它不能由客户端实现，因为它可能会在公共池的主要版本，次要版本和补丁版本之间发生更改.
 *       因此，实现此接口的客户端可能无法升级到新的次要版本或修补程序版本，而无需更改代码.
 *
 * @param <K> 池中维护的Key类型.
 */
public interface GenericKeyedObjectPoolMXBean<K> {
    // Expose getters for configuration settings
    boolean getBlockWhenExhausted();
    
    boolean getFairness();
    
    boolean getLifo();
    
    int getMaxIdlePerKey();
    
    int getMaxTotal();
    
    int getMaxTotalPerKey();
    
    long getMaxWaitMillis();
    
    long getMinEvictableIdleTimeMillis();
    
    int getMinIdlePerKey();
    
    int getNumActive();
    
    int getNumIdle();
    
    int getNumTestsPerEvictionRun();
    
    boolean getTestOnCreate();
    
    boolean getTestOnBorrow();
    
    boolean getTestOnReturn();
    
    boolean getTestWhileIdle();
    
    long getTimeBetweenEvictionRunsMillis();
    
    boolean isClosed();
    
    
    
    // Expose getters for monitoring attributes
    Map<String,Integer> getNumActivePerKey();
    
    long getBorrowedCount();
    
    long getReturnedCount();
    
    long getCreatedCount();
    
    long getDestroyedCount();
    
    long getDestroyedByEvictorCount();
    
    long getDestroyedByBorrowValidationCount();
    
    long getMeanActiveTimeMillis();
    
    long getMeanIdleTimeMillis();
    
    long getMeanBorrowWaitTimeMillis();
    
    long getMaxBorrowWaitTimeMillis();
    
    String getCreationStackTrace();
    
    int getNumWaiters();
    
    Map<String,Integer> getNumWaitersByKey();
    
    Map<String,List<DefaultPooledObjectInfo>> listAllObjects();
}
