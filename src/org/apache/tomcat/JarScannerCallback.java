package org.apache.tomcat;

import java.io.File;
import java.io.IOException;

/**
 * 此接口由{@link JarScanner}的客户端实现, 以使他们能够接收已发现JAR的通知.
 */
public interface JarScannerCallback {

    /**
     * 找到了JAR, 可以通过提供的URL连接访问JAR以进行进一步处理. 调用者负责关闭JAR.
     *
     * @param jar        要处理的 JAR
     * @param webappPath Web应用程序中JAR的路径
     * @param isWebapp   是否在Web应用程序中找到JAR. 如果是 <code>false</code> JAR应视为由容器提供
     *
     * @throws IOException 如果在扫描JAR时发生 I/O 错误
     */
    public void scan(Jar jar, String webappPath, boolean isWebapp)
            throws IOException;

    /**
     * 找到了一个被视为解压缩JAR的目录. 可以通过提供的文件访问该目录以进行进一步处理.
     *
     * @param file       包含解压缩JAR的目录.
     * @param webappPath Web应用程序中文件的路径
     * @param isWebapp   指示是否在Web应用程序中找到JAR. 如果是<code>false</code>, JAR应视为由容器提供
     *
     * @throws IOException 如果在扫描JAR时发生 I/O 错误
     */
    public void scan(File file, String webappPath, boolean isWebapp) throws IOException;

    /**
     * 在Web应用程序的/WEB-INF/classes中找到了一个目录结构，应该作为解压缩的JAR处理.
     * 请注意，所有资源访问必须通过ServletContext来确保任何其他资源可见.
     *
     * @throws IOException 如果在扫描WEB-INF/classes时发生 I/O 错误
     */
    public void scanWebInfClasses() throws IOException;
}
