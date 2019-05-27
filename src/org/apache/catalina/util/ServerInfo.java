package org.apache.catalina.util;


import java.io.InputStream;
import java.util.Properties;

import org.apache.tomcat.util.ExceptionUtils;


/**
 * 简单实用模块，在集成Tomcat时可以轻松地插入服务器标识符.
 */
public class ServerInfo {


    // ------------------------------------------------------- Static Variables


    /**
     * 识别自己的服务器信息字符串.
     */
    private static final String serverInfo;

    /**
     * 服务器构建字符串.
     */
    private static final String serverBuilt;

    /**
     * 服务器版本号.
     */
    private static final String serverNumber;

    static {

        String info = null;
        String built = null;
        String number = null;

        Properties props = new Properties();
        try (InputStream is = ServerInfo.class.getResourceAsStream
                ("/org/apache/catalina/util/ServerInfo.properties")) {
            props.load(is);
            info = props.getProperty("server.info");
            built = props.getProperty("server.built");
            number = props.getProperty("server.number");
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
        }
        if (info == null)
            info = "Apache Tomcat 8.5.x-dev";
        if (built == null)
            built = "unknown";
        if (number == null)
            number = "8.5.x";

        serverInfo = info;
        serverBuilt = built;
        serverNumber = number;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * @return 此版本Tomcat的服务器标识.
     */
    public static String getServerInfo() {
        return (serverInfo);
    }

    /**
     * @return 此版本Tomcat的服务器建立时间.
     */
    public static String getServerBuilt() {
        return (serverBuilt);
    }

    /**
     * @return 服务器的版本号.
     */
    public static String getServerNumber() {
        return (serverNumber);
    }

    public static void main(String args[]) {
        System.out.println("Server version: " + getServerInfo());
        System.out.println("Server built:   " + getServerBuilt());
        System.out.println("Server number:  " + getServerNumber());
        System.out.println("OS Name:        " +
                           System.getProperty("os.name"));
        System.out.println("OS Version:     " +
                           System.getProperty("os.version"));
        System.out.println("Architecture:   " +
                           System.getProperty("os.arch"));
        System.out.println("JVM Version:    " +
                           System.getProperty("java.runtime.version"));
        System.out.println("JVM Vendor:     " +
                           System.getProperty("java.vm.vendor"));
    }

}
