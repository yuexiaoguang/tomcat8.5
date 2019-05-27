package org.apache.jasper.compiler;

import javax.servlet.ServletContext;

import org.apache.tomcat.JarScanner;
import org.apache.tomcat.util.scan.StandardJarScanner;

/**
 * 给Jasper提供一个机制, 获取 JarScanner 实现的引用.
 */
public class JarScannerFactory {

    private JarScannerFactory() {
    }

    /**
     * 获取与指定的{@link ServletContext}关联的 {@link JarScanner}. 通过上下文参数获取.
     * @param ctxt The Servlet context
     * @return a scanner instance
     */
    public static JarScanner getJarScanner(ServletContext ctxt) {
        JarScanner jarScanner =
            (JarScanner) ctxt.getAttribute(JarScanner.class.getName());
        if (jarScanner == null) {
            ctxt.log(Localizer.getMessage("jsp.warning.noJarScanner"));
            jarScanner = new StandardJarScanner();
        }
        return jarScanner;
    }

}
