package org.apache.tomcat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.Manifest;

/**
 * 提供抽象以供需要扫描JAR的各种类使用.
 * JRE提供的用于访问JAR的类({@link java.util.jar.JarFile}和{@link java.util.jar.JarInputStream})具有显着不同的性能特征，具体取决于用于访问JAR的URL的形式.
 * 对于基于文件的 JAR {@link java.net.URL}, {@link java.util.jar.JarFile}更快; 
 * 但对于非基于文件的{@link java.net.URL}, {@link java.util.jar.JarFile} 在临时目录中创建JAR的副本, 因此 {@link java.util.jar.JarInputStream} 更快.
 */
public interface Jar extends AutoCloseable {

    /**
     * @return 用于访问JAR文件的URL.
     */
    URL getJarFileURL();

    /**
     * 确定JAR中是否存在特定条目.
     *
     * @param name  要查找的条目
     * @return      实现将总是返回 {@code false}
     *
     * @throws IOException 如果在处理JAR文件条目时发生 I/O 错误
     *
     * @deprecated Unused. This will be removed in Tomcat 9 onwards.
     */
    @Deprecated
    boolean entryExists(String name) throws IOException;


    /**
     * 获取JAR中给定条目的{@link InputStream}. 调用者负责关闭流.
     *
     * @param name  要获取 {@link InputStream} 的条目
     * @return      指定条目的{@link InputStream}; 如果该条目不存在，则为null
     *
     * @throws IOException 如果在处理JAR文件时发生 I/O 错误
     */
    InputStream getInputStream(String name) throws IOException;

    /**
     * 获取JAR中给定资源的上次修改时间.
     *
     * @param name  要获取修改时间的条目
     *
     * @return 资源的最后修改时间 (和 {@link System#currentTimeMillis()} 的格式相同). 如果条目不存在，则返回-1
     *
     * @throws IOException 如果在处理JAR文件时发生 I/O 错误
     */
    long getLastModified(String name) throws IOException;

    /**
     * 关闭与此JAR关联的所有资源.
     */
    @Override
    void close();

    /**
     * 将内部指针移动到JAR中的下一个条目.
     */
    void nextEntry();

    /**
     * 获取当前条目的名称.
     *
     * @return  条目的名称
     */
    String getEntryName();

    /**
     * 获取当前条目的输入流.
     *
     * @return  输入流
     * @throws IOException  如果无法获得流
     */
    InputStream getEntryInputStream() throws IOException;

    /**
     * 以String形式获取此JAR中条目的URL.
     * 请注意, 对于嵌套在WAR文件中的JAR, 不会使用Tomcat 特定的 war:file:... 格式, 而是 jar:jar:file:... 格式 (将使用JRE不理解的内容).
     * 请注意，这意味着使用这些URL的任何代码将需要理解 jar:jar:file:... 格式, 并使用 {@link org.apache.tomcat.util.scan.JarFactory}确保正确访问资源.
     *
     * @param entry 生成URL的条目
     *
     * @return JAR中指定条目的URL
     */
    String getURL(String entry);

    /**
     * 获取JAR文件的清单.
     *
     * @return 此JAR文件的清单.
     *
     * @throws IOException 如果尝试获取清单时发生 I/O 错误
     */
    Manifest getManifest() throws IOException;

    /**
     * 将用于跟踪JAR条目的内部指针重置为JAR的开头.
     *
     * @throws IOException  如果指针无法重置
     */
    void reset() throws IOException;
}
