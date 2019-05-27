package org.apache.catalina.util;

import java.util.Locale;

/**
 * 用于管理上下文名称的实用工具类.
 */
public final class ContextName {
    public static final String ROOT_NAME = "ROOT";
    private static final String VERSION_MARKER = "##";
    private static final String FWD_SLASH_REPLACEMENT = "#";

    private final String baseName;
    private final String path;
    private final String version;
    private final String name;


    /**
     * 从上下文名称, 显示名称, 基础名称, 目录名称, WAR名称, context.xml名称创建一个实例.
     *
     * @param name  要用作此对象的基础的名称
     * @param stripFileExtension    如果指定的名称后存在 .war 或 .xml 文件扩展名 , 是否删除它?
     */
    public ContextName(String name, boolean stripFileExtension) {

        String tmp1 = name;

        // 转换 Context 名称和显示名称为基础名称

        // 去除开头的 "/"
        if (tmp1.startsWith("/")) {
            tmp1 = tmp1.substring(1);
        }

        // 替换剩下的 /
        tmp1 = tmp1.replaceAll("/", FWD_SLASH_REPLACEMENT);

        // 插入 ROOT 名称
        if (tmp1.startsWith(VERSION_MARKER) || "".equals(tmp1)) {
            tmp1 = ROOT_NAME + tmp1;
        }

        // 删除所有的文件扩展名
        if (stripFileExtension &&
                (tmp1.toLowerCase(Locale.ENGLISH).endsWith(".war") ||
                        tmp1.toLowerCase(Locale.ENGLISH).endsWith(".xml"))) {
            tmp1 = tmp1.substring(0, tmp1.length() -4);
        }

        baseName = tmp1;

        String tmp2;
        // 提取版本号
        int versionIndex = baseName.indexOf(VERSION_MARKER);
        if (versionIndex > -1) {
            version = baseName.substring(versionIndex + 2);
            tmp2 = baseName.substring(0, versionIndex);
        } else {
            version = "";
            tmp2 = baseName;
        }

        if (ROOT_NAME.equals(tmp2)) {
            path = "";
        } else {
            path = "/" + tmp2.replaceAll(FWD_SLASH_REPLACEMENT, "/");
        }

        if (versionIndex > -1) {
            this.name = path + VERSION_MARKER + version;
        } else {
            this.name = path;
        }
    }

    /**
     * 从路径和版本构造一个实例.
     *
     * @param path      要使用的Context路径
     * @param version   要使用的Context版本
     */
    public ContextName(String path, String version) {
        // Path 不能是 null, '/' 或 '/ROOT'
        if (path == null || "/".equals(path) || "/ROOT".equals(path)) {
            this.path = "";
        } else {
            this.path = path;
        }

        // Version 不能是 null
        if (version == null) {
            this.version = "";
        } else {
            this.version = version;
        }

        // 名称是 path + version
        if ("".equals(this.version)) {
            name = this.path;
        } else {
            name = this.path + VERSION_MARKER + this.version;
        }

        // Base name is converted path + version
        StringBuilder tmp = new StringBuilder();
        if ("".equals(this.path)) {
            tmp.append(ROOT_NAME);
        } else {
            tmp.append(this.path.substring(1).replaceAll("/",
                    FWD_SLASH_REPLACEMENT));
        }
        if (this.version.length() > 0) {
            tmp.append(VERSION_MARKER);
            tmp.append(this.version);
        }
        this.baseName = tmp.toString();
    }

    public String getBaseName() {
        return baseName;
    }

    public String getPath() {
        return path;
    }

    public String getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        StringBuilder tmp = new StringBuilder();
        if ("".equals(path)) {
            tmp.append('/');
        } else {
            tmp.append(path);
        }

        if (!"".equals(version)) {
            tmp.append(VERSION_MARKER);
            tmp.append(version);
        }

        return tmp.toString();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
