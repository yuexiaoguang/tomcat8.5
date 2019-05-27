package org.apache.tomcat.util.digester;

import org.xml.sax.Attributes;

/**
 * 创建一个新对象并将其推送到对象堆栈.  元素完成后, 该对象将被弹出
 */
public class ObjectCreateRule extends Rule {


    // ----------------------------------------------------------- Constructors


    /**
     * @param className 要创建的对象的Java类名
     */
    public ObjectCreateRule(String className) {
        this(className, (String) null);
    }


    /**
     * @param className 要创建的对象的Java类名
     * @param attributeName 属性名称，如果存在，则包含要创建的类名称的覆盖
     */
    public ObjectCreateRule(String className,
                            String attributeName) {
        this.className = className;
        this.attributeName = attributeName;
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 包含覆盖类名称的属性.
     */
    protected String attributeName = null;


    /**
     * 要创建的对象的Java类名.
     */
    protected String className = null;


    // --------------------------------------------------------- Public Methods


    /**
     * 处理此元素的开头.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间, 则返回空字符串
     * @param name 如果解析器是命名空间感知的本地名称, 或者只是元素名称
     * @param attributes 此元素的属性列表
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes)
            throws Exception {

        // 标识要实例化的类的名称
        String realClassName = className;
        if (attributeName != null) {
            String value = attributes.getValue(attributeName);
            if (value != null) {
                realClassName = value;
            }
        }
        if (digester.log.isDebugEnabled()) {
            digester.log.debug("[ObjectCreateRule]{" + digester.match +
                    "}New " + realClassName);
        }

        if (realClassName == null) {
            throw new NullPointerException("No class name specified for " +
                    namespace + " " + name);
        }

        // 实例化新对象并将其推送到上下文堆栈
        Class<?> clazz = digester.getClassLoader().loadClass(realClassName);
        Object instance = clazz.getConstructor().newInstance();
        digester.push(instance);
    }


    /**
     * 处理此元素的结尾.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间, 则返回空字符串
     * @param name 如果解析器是命名空间感知的本地名称, 或者只是元素名称
     */
    @Override
    public void end(String namespace, String name) throws Exception {

        Object top = digester.pop();
        if (digester.log.isDebugEnabled()) {
            digester.log.debug("[ObjectCreateRule]{" + digester.match +
                    "} Pop " + top.getClass().getName());
        }
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ObjectCreateRule[");
        sb.append("className=");
        sb.append(className);
        sb.append(", attributeName=");
        sb.append(attributeName);
        sb.append("]");
        return (sb.toString());
    }
}
