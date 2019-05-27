package org.apache.tomcat.util.bcel.classfile;

/**
 * 表示Java类, i.e., Java .class文件中包含的数据结构, 常量池, 字段, 方法和命令.
 * 此类的目的是表示已解析或以其他方式存在的类文件.那些对以编程方式生成类感兴趣的人应该看ClassGen类.
 */
public class JavaClass {

    private final int access_flags;
    private final String class_name;
    private final String superclass_name;
    private final String[] interface_names;
    private final Annotations runtimeVisibleAnnotations; // 类中定义的"RuntimeVisibleAnnotations" 属性

    /**
     * @param class_name 这个类的类名.
     * @param superclass_name 这个类的超类的名称.
     * @param access_flags 由位标志定义的访问权限
     * @param constant_pool 常量数组
     * @param interfaces 实现的接口
     * @param runtimeVisibleAnnotations 类中定义的"RuntimeVisibleAnnotations" 属性, 或 null
     */
    JavaClass(final String class_name, final String superclass_name,
            final int access_flags, final ConstantPool constant_pool, final String[] interface_names,
            final Annotations runtimeVisibleAnnotations) {
        this.access_flags = access_flags;
        this.runtimeVisibleAnnotations = runtimeVisibleAnnotations;
        this.class_name = class_name;
        this.superclass_name = superclass_name;
        this.interface_names = interface_names;
    }

    /**
     * @return aka对象的访问标志. "modifiers".
     */
    public final int getAccessFlags() {
        return access_flags;
    }

    /**
     * 从类的“RuntimeVisibleAnnotations”属性返回注解条目.
     *
     * @return 条目数组或 {@code null}
     */
    public AnnotationEntry[] getAnnotationEntries() {
        if (runtimeVisibleAnnotations != null) {
            return runtimeVisibleAnnotations.getAnnotationEntries();
        }
        return null;
    }

    /**
     * @return 类名.
     */
    public String getClassName() {
        return class_name;
    }


    /**
     * @return 已实现接口的名称.
     */
    public String[] getInterfaceNames() {
        return interface_names;
    }


    /**
     * 返回此类的超类名称.
     * 在这个类是java.lang.Object的情况下, 它会返回自己 (java.lang.Object). 这可能是不正确的，但目前尚不修复以免破坏现有客户端.
     *
     * @return 超类名称.
     */
    public String getSuperclassName() {
        return superclass_name;
    }
}
