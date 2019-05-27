package org.apache.tomcat.util.compat;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Deque;
import java.util.jar.JarFile;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.apache.tomcat.util.res.StringManager;

/**
 * 这是JRE兼容性的基本实现类, 并提供了基于Java 7的实现.
 * 子类可以扩展此类，并为以后的JRE版本提供替代实现
 */
public class JreCompat {

    private static final int RUNTIME_MAJOR_VERSION = 7;

    private static final JreCompat instance;
    private static StringManager sm =
            StringManager.getManager(JreCompat.class.getPackage().getName());
    private static final boolean jre9Available;
    private static final boolean jre8Available;


    static {
        // 这是具有Java 7最低Java版本的Tomcat 8. 可选功能所需的最新Java版本是Java 9.
        // 首先查找JVM最高的支持
        if (Jre9Compat.isSupported()) {
            instance = new Jre9Compat();
            jre9Available = true;
            jre8Available = true;
        }
        else if (Jre8Compat.isSupported()) {
            instance = new Jre8Compat();
            jre9Available = false;
            jre8Available = true;
        } else {
            instance = new JreCompat();
            jre9Available = false;
            jre8Available = false;
        }
    }


    public static JreCompat getInstance() {
        return instance;
    }


    // Java 8方法的Java 7实现

    public static boolean isJre8Available() {
        return jre8Available;
    }


    @SuppressWarnings("unused")
    public void setUseServerCipherSuitesOrder(SSLEngine engine, boolean useCipherSuitesOrder) {
        throw new UnsupportedOperationException(sm.getString("jreCompat.noServerCipherSuiteOrder"));
    }


    // Java 9方法的Java 7实现

    public static boolean isJre9Available() {
        return jre9Available;
    }


    /**
     * 提供的异常是否是一个java.lang.reflect.InaccessibleObjectException 实例.
     *
     * @param t 要测试的异常
     *
     * @return {@code true} 如果是, 否则{@code false}
     */
    public boolean isInstanceOfInaccessibleObjectException(Throwable t) {
        // Java 9之前不存在异常
        return false;
    }


    /**
     * 设置服务器为ALPN接受的应用程序协议
     *
     * @param sslParameters    连接的SSL参数
     * @param protocols        该连接允许的应用程序协议
     */
    public void setApplicationProtocols(SSLParameters sslParameters, String[] protocols) {
        throw new UnsupportedOperationException(sm.getString("jreCompat.noApplicationProtocols"));
    }


    /**
     * 获取已经协商的应用程序协议，以便与给定的SSLEngine关联.
     *
     * @param sslEngine 要获得协商协议的SSLEngine
     *
     * @return 协商协议的名称
     */
    public String getApplicationProtocol(SSLEngine sslEngine) {
        throw new UnsupportedOperationException(sm.getString("jreCompat.noApplicationProtocol"));
    }


    /**
     * 禁用JAR URL连接的缓存. 对于Java 8及更早版本，这也会禁用所有URL连接的缓存.
     *
     * @throws IOException 如果无法创建虚拟JAR URLConnection
     */
    public void disableCachingForJarUrlConnections() throws IOException {
        // 这个JAR不存在并不重要 - 只要URL格式正确
        URL url = new URL("jar:file://dummy.jar!/");
        URLConnection uConn = url.openConnection();
        uConn.setDefaultUseCaches(false);
    }


    /**
     * 在JVM启动时获取模块路径上所有JAR的URL，并将它们添加到提供的Deque中.
     *
     * @param classPathUrlsToProcess    要添加模块的Deque
     */
    public void addBootModulePath(Deque<URL> classPathUrlsToProcess) {
        // NO-OP for Java 7. There is no module path.
    }


    /**
     * 创建一个新的JarFile实例.
     * 在Java 9及更高版本上运行时，JarFile将识别多版本JAR. 虽然这个包中并不严格要求，但它是作为一种便利方法提供的.
     *
     * @param s 要打开的JAR文件
     *
     * @return 基于提供的路径的JarFile实例
     *
     * @throws IOException  如果创建JarFile实例时发生I/O错误
     */
    public final JarFile jarFileNewInstance(String s) throws IOException {
        return jarFileNewInstance(new File(s));
    }


    /**
     * 创建一个新的JarFile实例. 在Java 9及更高版本上运行时，JarFile将识别多版本JAR.
     *
     * @param f 要打开的JAR文件
     *
     * @return 基于提供的文件的JarFile实例
     *
     * @throws IOException  如果创建JarFile实例时发生I/O错误
     */
    public JarFile jarFileNewInstance(File f) throws IOException {
        return new JarFile(f);
    }


    /**
     * 这个JarFile是一个多版本的JAR文件吗.
     *
     * @param jarFile   要测试的JarFile
     *
     * @return {@code true} 如果它是多版本JAR文件，并配置为这样.
     */
    public boolean jarFileIsMultiRelease(JarFile jarFile) {
        // Java 8不支持多版本，因此默认为false
        return false;
    }


    public int jarFileRuntimeMajorVersion() {
        return RUNTIME_MAJOR_VERSION;
    }
}
