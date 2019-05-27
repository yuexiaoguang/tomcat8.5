package org.apache.tomcat;

import javax.servlet.ServletContext;

/**
 * 扫描JAR文件的Web应用程序和类加载器层次结构. 用途包括TLD扫描和web-fragment.xml扫描. 使用回调机制，以便调用者可以处理找到的每个JAR.
 */
public interface JarScanner {

    /**
     * 扫描提供的ServletContext和类加载器以获取JAR文件. 找到的每个JAR文件都将传递给回调处理程序处理.
     *
     * @param scanType      要执行的JAR扫描类型. 这将传递给过滤器，过滤器使用它来确定如何过滤结果
     * @param context       ServletContext - 用于定位和访问 WEB-INF/lib
     * @param callback      处理找到的JAR的处理程序
     */
    public void scan(JarScanType scanType, ServletContext context,
            JarScannerCallback callback);

    public JarScanFilter getJarScanFilter();

    public void setJarScanFilter(JarScanFilter jarScanFilter);
}
