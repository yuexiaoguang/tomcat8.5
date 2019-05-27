package org.apache.tomcat.util.compat;

import java.util.Locale;

public class JreVendor {

    static {
        /**
         * 有几个地方Tomcat要么访问JVM内部（例如内存泄漏保护），要么JVM之间的功能支持不同（例如SPNEGO）.
         * 存在这些标志以使Tomcat能够基于JVM的供应商调整其行为. 在理想的世界中，这段代码不存在.
         */
        String vendor = System.getProperty("java.vendor", "");
        vendor = vendor.toLowerCase(Locale.ENGLISH);

        if (vendor.startsWith("oracle") || vendor.startsWith("sun")) {
            IS_ORACLE_JVM = true;
            IS_IBM_JVM = false;
        } else if (vendor.contains("ibm")) {
            IS_ORACLE_JVM = false;
            IS_IBM_JVM = true;
        } else {
            IS_ORACLE_JVM = false;
            IS_IBM_JVM = false;
        }
    }

    public static final boolean IS_ORACLE_JVM;

    public static final boolean IS_IBM_JVM;
}
