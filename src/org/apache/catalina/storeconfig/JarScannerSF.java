package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.tomcat.JarScanFilter;
import org.apache.tomcat.JarScanner;

/**
 * 保存 server.xml Element JarScanner
 */
public class JarScannerSF extends StoreFactoryBase {

    /**
     * 保存指定的JarScanner 属性和子级 (JarScannerFilter)
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent 这个元素缩进的空格数量
     * @param aJarScanner 要保存属性的 JarScanner
     *
     * @exception Exception 保存期间发生异常
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aJarScanner,
            StoreDescription parentDesc) throws Exception {
        if (aJarScanner instanceof JarScanner) {
            JarScanner jarScanner = (JarScanner) aJarScanner;
            // 保存嵌套的 <JarScanFilter> 元素
            JarScanFilter jarScanFilter = jarScanner.getJarScanFilter();
            if (jarScanFilter != null) {
                storeElement(aWriter, indent, jarScanFilter);
            }
        }
    }

}