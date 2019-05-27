package org.apache.catalina.storeconfig;

import java.beans.IndexedPropertyDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.net.SocketProperties;

/**
 * 存储Connector 属性.
 * Connector有特殊设计. 一个 Connector只是一个ProtocolHandler的启动 Wrapper.
 * 意味着 ProtocolHandler 从Connector属性map获取所有的属性. 奇怪的是一些属性名称的修改和属性sslProtocol 需要特殊处理
 */
public class ConnectorStoreAppender extends StoreAppender {

    protected static final HashMap<String, String> replacements = new HashMap<>();
    static {
        replacements.put("backlog", "acceptCount");
        replacements.put("soLinger", "connectionLinger");
        replacements.put("soTimeout", "connectionTimeout");
        replacements.put("timeout", "connectionUploadTimeout");
        replacements.put("clientauth", "clientAuth");
        replacements.put("keystore", "keystoreFile");
        replacements.put("randomfile", "randomFile");
        replacements.put("rootfile", "rootFile");
        replacements.put("keypass", "keystorePass");
        replacements.put("keytype", "keystoreType");
        replacements.put("protocol", "sslProtocol");
        replacements.put("protocols", "sslProtocols");
    }

    @Override
    public void printAttributes(PrintWriter writer, int indent,
            boolean include, Object bean, StoreDescription desc)
            throws Exception {

        // Render a className attribute if requested
        if (include && desc != null && !desc.isStandard()) {
            writer.print(" className=\"");
            writer.print(bean.getClass().getName());
            writer.print("\"");
        }

        Connector connector = (Connector) bean;
        String protocol = connector.getProtocol();
        List<String> propertyKeys = getPropertyKeys(connector);
        // Create blank instance
        Object bean2 = new Connector(protocol);//defaultInstance(bean);
        Iterator<String> propertyIterator = propertyKeys.iterator();
        while (propertyIterator.hasNext()) {
            String key = propertyIterator.next();
            Object value = IntrospectionUtils.getProperty(bean, key);
            if (desc.isTransientAttribute(key)) {
                continue; // Skip the specified exceptions
            }
            if (value == null) {
                continue; // Null values are not persisted
            }
            if (!isPersistable(value.getClass())) {
                continue;
            }
            Object value2 = IntrospectionUtils.getProperty(bean2, key);
            if (value.equals(value2)) {
                // 属性有它默认的值
                continue;
            }
            if (isPrintValue(bean, bean2, key, desc)) {
                printValue(writer, indent, key, value);
            }
        }
        if (protocol != null && !"HTTP/1.1".equals(protocol))
            super.printValue(writer, indent, "protocol", protocol);

    }

    /**
     * 从Connector和当前ProtocolHandler获取所有的属性.
     *
     * @param bean The connector
     * 
     * @return Connector属性名称列表
     * @throws IntrospectionException 引入连接器错误
     */
    protected List<String> getPropertyKeys(Connector bean)
            throws IntrospectionException {
        ArrayList<String> propertyKeys = new ArrayList<>();
        // 获取这个bean的属性列表
        ProtocolHandler protocolHandler = bean.getProtocolHandler();
        // 获取这个bean的属性列表
        PropertyDescriptor descriptors[] = Introspector.getBeanInfo(
                bean.getClass()).getPropertyDescriptors();
        if (descriptors == null) {
            descriptors = new PropertyDescriptor[0];
        }
        for (PropertyDescriptor descriptor : descriptors) {
            if (descriptor instanceof IndexedPropertyDescriptor) {
                continue; // Indexed properties are not persisted
            }
            if (!isPersistable(descriptor.getPropertyType())
                    || (descriptor.getReadMethod() == null)
                    || (descriptor.getWriteMethod() == null)) {
                continue; // Must be a read-write primitive or String
            }
            if ("protocol".equals(descriptor.getName())
                    || "protocolHandlerClassName".equals(descriptor
                            .getName()))
                continue;
            propertyKeys.add(descriptor.getName());
        }
        // Add the properties of the protocol handler
        descriptors = Introspector.getBeanInfo(
                protocolHandler.getClass()).getPropertyDescriptors();
        if (descriptors == null) {
            descriptors = new PropertyDescriptor[0];
        }
        for (PropertyDescriptor descriptor : descriptors) {
            if (descriptor instanceof IndexedPropertyDescriptor) {
                continue; // Indexed properties are not persisted
            }
            if (!isPersistable(descriptor.getPropertyType())
                    || (descriptor.getReadMethod() == null)
                    || (descriptor.getWriteMethod() == null)) {
                continue; // Must be a read-write primitive or String
            }
            String key = descriptor.getName();
            if (replacements.get(key) != null) {
                key = replacements.get(key);
            }
            if (!propertyKeys.contains(key)) {
                propertyKeys.add(key);
            }
        }
        // Add the properties for the socket
        final String socketName = "socket.";
        descriptors = Introspector.getBeanInfo(
                SocketProperties.class).getPropertyDescriptors();
        if (descriptors == null) {
            descriptors = new PropertyDescriptor[0];
        }
        for (PropertyDescriptor descriptor : descriptors) {
            if (descriptor instanceof IndexedPropertyDescriptor) {
                continue; // Indexed properties are not persisted
            }
            if (!isPersistable(descriptor.getPropertyType())
                    || (descriptor.getReadMethod() == null)
                    || (descriptor.getWriteMethod() == null)) {
                continue; // Must be a read-write primitive or String
            }
            String key = descriptor.getName();
            if (replacements.get(key) != null) {
                key = replacements.get(key);
            }
            if (!propertyKeys.contains(key)) {
                // Add socket.[original name] if this is not a property
                // that could be set elsewhere
                propertyKeys.add(socketName + descriptor.getName());
            }
        }
        return propertyKeys;
    }

    /**
     * 打印connector的属性
     *
     * @param aWriter Current writer
     * @param indent 缩进级别
     * @param bean The connector bean
     * @param aDesc 连接器描述信息
     * @throws Exception Store error occurred
     */
    protected void storeConnectorAttributes(PrintWriter aWriter, int indent,
            Object bean, StoreDescription aDesc) throws Exception {
        if (aDesc.isAttributes()) {
            printAttributes(aWriter, indent, false, bean, aDesc);
        }
    }

    /**
     * 打印连接器属性的开放标签(override).
     */
    @Override
    public void printOpenTag(PrintWriter aWriter, int indent, Object bean,
            StoreDescription aDesc) throws Exception {
        aWriter.print("<");
        aWriter.print(aDesc.getTag());
        storeConnectorAttributes(aWriter, indent, bean, aDesc);
        aWriter.println(">");
    }

    /**
     * 打印连接器属性的标签(override).
     */
    @Override
    public void printTag(PrintWriter aWriter, int indent, Object bean,
            StoreDescription aDesc) throws Exception {
        aWriter.print("<");
        aWriter.print(aDesc.getTag());
        storeConnectorAttributes(aWriter, indent, bean, aDesc);
        aWriter.println("/>");
    }

    /**
     * 打印一个值，但替换某些属性名.
     */
    @Override
    public void printValue(PrintWriter writer, int indent, String name,
            Object value) {
        String repl = name;
        if (replacements.get(name) != null) {
            repl = replacements.get(name);
        }
        super.printValue(writer, indent, repl, value);
    }

    /**
     * 打印Connector值. <ul><li> 默认的 jkHome的特殊处理.
     * </li><li> 不要在server.xml中保存 catalina.base 路径</li><li></ul>
     */
    @Override
    public boolean isPrintValue(Object bean, Object bean2, String attrName,
            StoreDescription desc) {
        boolean isPrint = super.isPrintValue(bean, bean2, attrName, desc);
        if (isPrint) {
            if ("jkHome".equals(attrName)) {
                Connector connector = ((Connector) bean);
                File catalinaBase = getCatalinaBase();
                File jkHomeBase = getJkHomeBase((String) connector
                        .getProperty("jkHome"), catalinaBase);
                isPrint = !catalinaBase.equals(jkHomeBase);

            }
        }
        return isPrint;
    }

    protected File getCatalinaBase() {

        File file = new File(System.getProperty("catalina.base"));
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {
        }
        return (file);
    }

    protected File getJkHomeBase(String jkHome, File appBase) {

        File jkHomeBase;
        File file = new File(jkHome);
        if (!file.isAbsolute())
            file = new File(appBase, jkHome);
        try {
            jkHomeBase = file.getCanonicalFile();
        } catch (IOException e) {
            jkHomeBase = file;
        }
        return (jkHomeBase);
    }

}