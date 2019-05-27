package org.apache.catalina.loader;

/**
 * Resource entry.
 */
public class ResourceEntry {

    /**
     * 加载此类时, 源文件的“最后修改”时间，自纪元以来的毫秒内.
     */
    public long lastModified = -1;


    /**
     * 加载的类.
     */
    public volatile Class<?> loadedClass = null;
}

