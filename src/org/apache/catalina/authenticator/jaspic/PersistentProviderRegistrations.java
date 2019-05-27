package org.apache.catalina.authenticator.jaspic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.SAXException;

/**
 * 用于加载和保存JASPIC持久化提供者注册的实用工具类.
 */
final class PersistentProviderRegistrations {

    private static final Log log = LogFactory.getLog(PersistentProviderRegistrations.class);
    private static final StringManager sm =
            StringManager.getManager(PersistentProviderRegistrations.class);


    private PersistentProviderRegistrations() {
        // Utility class. Hide default constructor
    }


    static Providers loadProviders(File configFile) {
        try (InputStream is = new FileInputStream(configFile)) {
            // 读取XML输入文件
            Digester digester = new Digester();

            try {
                digester.setFeature("http://apache.org/xml/features/allow-java-encodings", true);
                digester.setValidating(true);
                digester.setNamespaceAware(true);
            } catch (Exception e) {
                throw new SecurityException(e);
            }

            // 创建一个对象来保存解析结果并将其放在digester的堆栈顶部
            Providers result = new Providers();
            digester.push(result);

            // Configure the digester
            digester.addObjectCreate("jaspic-providers/provider", Provider.class.getName());
            digester.addSetProperties("jaspic-providers/provider");
            digester.addSetNext("jaspic-providers/provider", "addProvider", Provider.class.getName());

            digester.addObjectCreate("jaspic-providers/provider/property", Property.class.getName());
            digester.addSetProperties("jaspic-providers/provider/property");
            digester.addSetNext("jaspic-providers/provider/property", "addProperty", Property.class.getName());

            // Parse the input
            digester.parse(is);

            return result;
        } catch (IOException | SAXException e) {
            throw new SecurityException(e);
        }
    }


    static void writeProviders(Providers providers, File configFile) {
        File configFileOld = new File(configFile.getAbsolutePath() + ".old");
        File configFileNew = new File(configFile.getAbsolutePath() + ".new");

        // 删除遗留临时文件
        if (configFileOld.exists()) {
            if (configFileOld.delete()) {
                throw new SecurityException(sm.getString(
                        "persistentProviderRegistrations.existsDeleteFail",
                        configFileOld.getAbsolutePath()));
            }
        }
        if (configFileNew.exists()) {
            if (configFileNew.delete()) {
                throw new SecurityException(sm.getString(
                        "persistentProviderRegistrations.existsDeleteFail",
                        configFileNew.getAbsolutePath()));
            }
        }

        // 将提供者写入临时新文件
        try (OutputStream fos = new FileOutputStream(configFileNew);
                Writer writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(
                    "<?xml version='1.0' encoding='utf-8'?>\n" +
                    "<jaspic-providers\n" +
                    "    xmlns=\"http://tomcat.apache.org/xml\"\n" +
                    "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "    xsi:schemaLocation=\"http://tomcat.apache.org/xml jaspic-providers.xsd\"\n" +
                    "    version=\"1.0\">\n");
            for (Provider provider : providers.providers) {
                writer.write("  <provider");
                writeOptional("className", provider.getClassName(), writer);
                writeOptional("layer", provider.getLayer(), writer);
                writeOptional("appContext", provider.getAppContext(), writer);
                writeOptional("description", provider.getDescription(), writer);
                writer.write(">\n");
                for (Entry<String,String> entry : provider.getProperties().entrySet()) {
                    writer.write("    <property name=\"");
                    writer.write(entry.getKey());
                    writer.write("\" value=\"");
                    writer.write(entry.getValue());
                    writer.write("\"/>\n");
                }
                writer.write("  </provider>\n");
            }
            writer.write("</jaspic-providers>\n");
        } catch (IOException e) {
            configFileNew.delete();
            throw new SecurityException(e);
        }

        // 将当前文件移走
        if (configFile.isFile()) {
            if (!configFile.renameTo(configFileOld)) {
                throw new SecurityException(sm.getString("persistentProviderRegistrations.moveFail",
                        configFile.getAbsolutePath(), configFileOld.getAbsolutePath()));
            }
        }

        // 将新文件移动到位
        if (!configFileNew.renameTo(configFile)) {
            throw new SecurityException(sm.getString("persistentProviderRegistrations.moveFail",
                    configFileNew.getAbsolutePath(), configFile.getAbsolutePath()));
        }

        // 移除旧文件
        if (configFileOld.exists() && !configFileOld.delete()) {
            log.warn(sm.getString("persistentProviderRegistrations.deleteFail",
                    configFileOld.getAbsolutePath()));
        }
    }


    private static void writeOptional(String name, String value, Writer writer) throws IOException {
        if (value != null) {
            writer.write(" " + name + "=\"");
            writer.write(value);
            writer.write("\"");
        }
    }


    public static class Providers {
        private final List<Provider> providers = new ArrayList<>();

        public void addProvider(Provider provider) {
            providers.add(provider);
        }

        public List<Provider> getProviders() {
            return providers;
        }
    }


    public static class Provider {
        private String className;
        private String layer;
        private String appContext;
        private String description;
        private final Map<String,String> properties = new HashMap<>();


        public String getClassName() {
            return className;
        }
        public void setClassName(String className) {
            this.className = className;
        }


        public String getLayer() {
            return layer;
        }
        public void setLayer(String layer) {
            this.layer = layer;
        }


        public String getAppContext() {
            return appContext;
        }
        public void setAppContext(String appContext) {
            this.appContext = appContext;
        }


        public String getDescription() {
            return description;
        }
        public void setDescription(String description) {
            this.description = description;
        }


        public void addProperty(Property property) {
            properties.put(property.getName(), property.getValue());
        }
        void addProperty(String name, String value) {
            properties.put(name, value);
        }
        public Map<String,String> getProperties() {
            return properties;
        }
    }


    public static class Property {
        private String name;
        private String value;


        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }


        public String getValue() {
            return value;
        }
        public void setValue(String value) {
            this.value = value;
        }
    }
}
