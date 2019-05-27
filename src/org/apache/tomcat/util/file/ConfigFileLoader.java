package org.apache.tomcat.util.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

import org.apache.tomcat.util.res.StringManager;

/**
 * 此类用于从给定位置String获取配置文件的{@link InputStream}.
 * 这允许比必须直接从文件系统加载这些文件更大的灵活性.
 */
public class ConfigFileLoader {

    private static final StringManager sm = StringManager.getManager(ConfigFileLoader.class
            .getPackage().getName());

    private static final File CATALINA_BASE_FILE;
    private static final URI CATALINA_BASE_URI;

    static {
        String catalinaBase = System.getProperty("catalina.base");
        if (catalinaBase != null) {
            CATALINA_BASE_FILE = new File(catalinaBase);
            CATALINA_BASE_URI = CATALINA_BASE_FILE.toURI();
        } else {
            CATALINA_BASE_FILE = null;
            CATALINA_BASE_URI = null;
        }
    }

    private ConfigFileLoader() {
        // Utility class. Hide the default constructor.
    }


    /**
     * 从指定位置加载资源.
     *
     * @param location 资源的位置. 该位置可以是URL或文件路径. 相对路径将根据CATALINA_BASE进行解析.
     *
     * @return 给定资源的InputStream. 调用者负责在不再使用该流时关闭该流.
     *
     * @throws IOException 如果无法使用提供的位置创建InputStream
     */
    public static InputStream getInputStream(String location) throws IOException {
        // 位置最初始终是添加URI支持之前的文件，因此请先尝试文件.

        File f = new File(location);
        if (!f.isAbsolute()) {
            f = new File(CATALINA_BASE_FILE, location);
        }
        if (f.isFile()) {
            return new FileInputStream(f);
        }

        // 文件无效，请尝试URI.
        // 使用resolve()使代码能够处理未指向文件的相对路径
        URI uri;
        if (CATALINA_BASE_URI != null) {
            uri = CATALINA_BASE_URI.resolve(location);
        } else {
            uri = URI.create(location);
        }

        // 获取需要的输入流
        try {
            URL url = uri.toURL();
            return url.openConnection().getInputStream();
        } catch (IllegalArgumentException e) {
            throw new IOException(sm.getString("configFileLoader.cannotObtainURL", location), e);
        }
    }
}
