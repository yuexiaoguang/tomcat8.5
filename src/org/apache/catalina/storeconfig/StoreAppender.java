package org.apache.catalina.storeconfig;

import java.beans.IndexedPropertyDescriptor;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.Iterator;

import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.descriptor.web.ResourceBase;
import org.apache.tomcat.util.security.Escape;

/**
 * 生成真正的xml 标签元素
 */
public class StoreAppender {

    /**
     * 表示持久属性的类的集合.
     */
    private static Class<?> persistables[] = { String.class, Integer.class,
            Integer.TYPE, Boolean.class, Boolean.TYPE, Byte.class, Byte.TYPE,
            Character.class, Character.TYPE, Double.class, Double.TYPE,
            Float.class, Float.TYPE, Long.class, Long.TYPE, Short.class,
            Short.TYPE, InetAddress.class };

    private int pos = 0;

    /**
     * 打印结尾的标签.
     *
     * @param aWriter The output writer
     * @param aDesc 当前元素的Store 描述
     * @throws Exception 发生存储错误
     */
    public void printCloseTag(PrintWriter aWriter, StoreDescription aDesc)
            throws Exception {
        aWriter.print("</");
        aWriter.print(aDesc.getTag());
        aWriter.println(">");
    }

    /**
     * 打印开始标签及其所有属性.
     *
     * @param aWriter The output writer
     * @param indent 缩进等级
     * @param bean 要保存的当前bean
     * @param aDesc 当前元素的Store 描述
     * @throws Exception 发生存储错误
     */
    public void printOpenTag(PrintWriter aWriter, int indent, Object bean,
            StoreDescription aDesc) throws Exception {
        aWriter.print("<");
        aWriter.print(aDesc.getTag());
        if (aDesc.isAttributes() && bean != null)
            printAttributes(aWriter, indent, bean, aDesc);
        aWriter.println(">");
    }

    /**
     * 打印整个标签及其属性
     *
     * @param aWriter The output writer
     * @param indent 缩进等级
     * @param bean 要保存的当前bean
     * @param aDesc 当前元素的Store 描述
     * @throws Exception 发生存储错误
     */
    public void printTag(PrintWriter aWriter, int indent, Object bean,
            StoreDescription aDesc) throws Exception {
        aWriter.print("<");
        aWriter.print(aDesc.getTag());
        if (aDesc.isAttributes() && bean != null)
            printAttributes(aWriter, indent, bean, aDesc);
        aWriter.println("/>");
    }

    /**
     * 打印标签的值.
     *
     * @param aWriter The output writer
     * @param tag 元素名称
     * @param content 元素内容
     * @throws Exception 发生存储错误
     */
    public void printTagContent(PrintWriter aWriter, String tag, String content)
            throws Exception {
        aWriter.print("<");
        aWriter.print(tag);
        aWriter.print(">");
        aWriter.print(Escape.xml(content));
        aWriter.print("</");
        aWriter.print(tag);
        aWriter.println(">");
    }

    /**
     * 打印值的数组.
     *
     * @param aWriter The output writer
     * @param tag 元素名称
     * @param indent 缩进等级
     * @param elements 元素值的数组
     */
    public void printTagValueArray(PrintWriter aWriter, String tag, int indent,
            String[] elements) {
        if (elements != null && elements.length > 0) {
            printIndent(aWriter, indent + 2);
            aWriter.print("<");
            aWriter.print(tag);
            aWriter.print(">");
            for (int i = 0; i < elements.length; i++) {
                printIndent(aWriter, indent + 4);
                aWriter.print(elements[i]);
                if (i + 1 < elements.length)
                    aWriter.println(",");
            }
            printIndent(aWriter, indent + 2);
            aWriter.print("</");
            aWriter.print(tag);
            aWriter.println(">");
        }
    }

    /**
     * 打印元素数组.
     *
     * @param aWriter The output writer
     * @param tag 元素名称
     * @param indent 缩进等级
     * @param elements 元素数组
     * @throws Exception 发生存储错误
     */
    public void printTagArray(PrintWriter aWriter, String tag, int indent,
            String[] elements) throws Exception {
        if (elements != null) {
            for (int i = 0; i < elements.length; i++) {
                printIndent(aWriter, indent);
                printTagContent(aWriter, tag, elements[i]);
            }
        }
    }

    /**
     * 打印一些空格.
     *
     * @param aWriter The output writer
     * @param indent 空格数量
     */
    public void printIndent(PrintWriter aWriter, int indent) {
        for (int i = 0; i < indent; i++) {
            aWriter.print(' ');
        }
        pos = indent;
    }

    /**
     * 保存指定JavaBean相关的属性, 加上一个<code>className</code>属性定义了这个bean的完全限定Java类名.
     *
     * @param writer PrintWriter to which we are storing
     * @param indent 缩进等级
     * @param bean 要保存属性的bean
     * @param desc 当前元素的Store 描述
     *
     * @exception Exception 保存期间发生异常
     */
    public void printAttributes(PrintWriter writer, int indent, Object bean,
            StoreDescription desc) throws Exception {

        printAttributes(writer, indent, true, bean, desc);

    }

    /**
     * 保存指定的JavaBean的相关属性.
     *
     * @param writer PrintWriter to which we are storing
     * @param indent 缩进等级
     * @param include 是否包括一个<code>className</code> 属性?
     * @param bean 要保存属性的bean
     * @param desc 这个bean的RegistryDescriptor
     *
     * @exception Exception 保存期间发生异常
     */
    public void printAttributes(PrintWriter writer, int indent,
            boolean include, Object bean, StoreDescription desc)
            throws Exception {

        // 呈现 className 属性
        if (include && desc != null && !desc.isStandard()) {
            writer.print(" className=\"");
            writer.print(bean.getClass().getName());
            writer.print("\"");
        }

        // 获取这个 bean的属性列表
        PropertyDescriptor descriptors[] = Introspector.getBeanInfo(
                bean.getClass()).getPropertyDescriptors();
        if (descriptors == null) {
            descriptors = new PropertyDescriptor[0];
        }

        // Create blank instance
        Object bean2 = defaultInstance(bean);
        for (int i = 0; i < descriptors.length; i++) {
            if (descriptors[i] instanceof IndexedPropertyDescriptor) {
                continue; // Indexed properties are not persisted
            }
            if (!isPersistable(descriptors[i].getPropertyType())
                    || (descriptors[i].getReadMethod() == null)
                    || (descriptors[i].getWriteMethod() == null)) {
                continue; // Must be a read-write primitive or String
            }
            if (desc.isTransientAttribute(descriptors[i].getName())) {
                continue; // Skip the specified exceptions
            }
            Object value = IntrospectionUtils.getProperty(bean, descriptors[i]
                    .getName());
            if (value == null) {
                continue; // Null values are not persisted
            }
            Object value2 = IntrospectionUtils.getProperty(bean2,
                    descriptors[i].getName());
            if (value.equals(value2)) {
                // The property has its default value
                continue;
            }
            printAttribute(writer, indent, bean, desc, descriptors[i].getName(), bean2, value);
        }

        if (bean instanceof ResourceBase) {
            ResourceBase resource = (ResourceBase) bean;
            for (Iterator<String> iter = resource.listProperties(); iter.hasNext();) {
                String name = iter.next();
                Object value = resource.getProperty(name);
                if (!isPersistable(value.getClass())) {
                    continue;
                }
                if (desc.isTransientAttribute(name)) {
                    continue; // Skip the specified exceptions
                }
                printValue(writer, indent, name, value);

            }
        }
    }

    /**
     * 保存指定的 JavaBean.
     *
     * @param writer PrintWriter to which we are storing
     * @param indent 缩进等级
     * @param bean 当前 bean
     * @param desc 这个bean的RegistryDescriptor
     * @param attributeName 要保存的属性名
     * @param bean2 用于比较的bean的默认实例
     * @param value 属性值
     */
    protected void printAttribute(PrintWriter writer, int indent, Object bean, StoreDescription desc, String attributeName, Object bean2, Object value) {
        if (isPrintValue(bean, bean2, attributeName, desc))
            printValue(writer, indent, attributeName, value);
    }

    /**
     * 确定属性是否需要存储.
     *
     * @param bean 原始bean
     * @param bean2 默认bean
     * @param attrName 属性名
     * @param desc 这个bean的StoreDescription
     * @return <code>true</code> 应该保存
     */
    public boolean isPrintValue(Object bean, Object bean2, String attrName,
            StoreDescription desc) {
        boolean printValue = false;

        Object value = IntrospectionUtils.getProperty(bean, attrName);
        if (value != null) {
            Object value2 = IntrospectionUtils.getProperty(bean2, attrName);
            printValue = !value.equals(value2);

        }
        return printValue;
    }

    /**
     * 生成指定的bean的默认实例.
     *
     * @param bean The bean
     * @return an object from same class as bean parameter
     * @throws ReflectiveOperationException 创建实例错误
     */
    public Object defaultInstance(Object bean) throws ReflectiveOperationException {
        return bean.getClass().getConstructor().newInstance();
    }

    /**
     * 打印属性值.
     *
     * @param writer PrintWriter to which we are storing
     * @param indent 缩进等级
     * @param name Attribute name
     * @param value Attribute value
     */
    public void printValue(PrintWriter writer, int indent, String name,
            Object value) {
        // Convert IP addresses to strings so they will be persisted
        if (value instanceof InetAddress) {
            value = ((InetAddress) value).getHostAddress();
        }
        if (!(value instanceof String)) {
            value = value.toString();
        }
        String strValue = Escape.xml((String) value);
        pos = pos + name.length() + strValue.length();
        if (pos > 60) {
            writer.println();
            printIndent(writer, indent + 4);
        } else {
            writer.print(' ');
        }
        writer.print(name);
        writer.print("=\"");
        writer.print(strValue);
        writer.print("\"");
    }

    /**
     * Given a string, this method replaces all occurrences of '&lt;', '&gt;',
     * '&amp;', and '"'.
     * @param input The string to escape
     * @return the escaped string
     * @deprecated This method will be removed in Tomcat 9
     */
    @Deprecated
    public String convertStr(String input) {

        StringBuffer filtered = new StringBuffer(input.length());
        char c;
        for (int i = 0; i < input.length(); i++) {
            c = input.charAt(i);
            if (c == '<') {
                filtered.append("&lt;");
            } else if (c == '>') {
                filtered.append("&gt;");
            } else if (c == '\'') {
                filtered.append("&apos;");
            } else if (c == '"') {
                filtered.append("&quot;");
            } else if (c == '&') {
                filtered.append("&amp;");
            } else {
                filtered.append(c);
            }
        }
        return (filtered.toString());
    }

    /**
     * 应该生成一个持久化属性的指定属性类型之一?
     *
     * @param clazz 要测试的Java class
     * @return <code>true</code>如果指定的类应该存储
     */
    protected boolean isPersistable(Class<?> clazz) {

        for (int i = 0; i < persistables.length; i++) {
            if (persistables[i] == clazz || persistables[i].isAssignableFrom(clazz)) {
                return true;
            }
        }
        return false;
    }
}
