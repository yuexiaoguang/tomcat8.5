package org.apache.catalina.startup;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * 读取引导Catalina 配置的工具类.
 */
public class CatalinaProperties {

    private static final Log log = LogFactory.getLog(CatalinaProperties.class);

    private static Properties properties = null;


    static {
        loadProperties();
    }


    /**
     * @param name 属性名
     */
    public static String getProperty(String name) {
        return properties.getProperty(name);
    }


    /**
     * 加载属性.
     */
    private static void loadProperties() {

        InputStream is = null;
        try {
            String configUrl = System.getProperty("catalina.config");
            if (configUrl != null) {
                is = (new URL(configUrl)).openStream();
            }
        } catch (Throwable t) {
            handleThrowable(t);
        }

        if (is == null) {
            try {
                File home = new File(Bootstrap.getCatalinaBase());
                File conf = new File(home, "conf");
                File propsFile = new File(conf, "catalina.properties");
                is = new FileInputStream(propsFile);
            } catch (Throwable t) {
                handleThrowable(t);
            }
        }

        if (is == null) {
            try {
                is = CatalinaProperties.class.getResourceAsStream
                    ("/org/apache/catalina/startup/catalina.properties");
            } catch (Throwable t) {
                handleThrowable(t);
            }
        }

        if (is != null) {
            try {
                properties = new Properties();
                properties.load(is);
            } catch (Throwable t) {
                handleThrowable(t);
                log.warn(t);
            } finally {
                try {
                    is.close();
                } catch (IOException ioe) {
                    log.warn("Could not close catalina.properties", ioe);
                }
            }
        }

        if ((is == null)) {
            // Do something
            log.warn("Failed to load catalina.properties");
            // That's fine - we have reasonable defaults.
            properties = new Properties();
        }

        // 将属性注册为系统属性
        Enumeration<?> enumeration = properties.propertyNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = properties.getProperty(name);
            if (value != null) {
                System.setProperty(name, value);
            }
        }
    }


    // 从ExceptionUtils复制, 因为该类在启动期间不可见
    private static void handleThrowable(Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // 所有其它的 Throwable实例将默默忽略
    }
}
