package org.apache.tomcat.util.bcel;

/**
 * 项目的常量, 主要在JVM规范中定义.
 */
public final class Const {

    /** 字段，方法或类的访问标志之一.
     */
    public static final short ACC_FINAL      = 0x0010;

    /** 字段，方法或类的访问标志之一.
     */
    public static final short ACC_INTERFACE    = 0x0200;

    /** 字段，方法或类的访问标志之一.
     */
    public static final short ACC_ABSTRACT     = 0x0400;

    /** 字段，方法或类的访问标志之一.
     */
    public static final short ACC_ANNOTATION   = 0x2000;

    /** 将常量池条目标记为UTF-8类型.
     */
    public static final byte CONSTANT_Utf8           = 1;

    /** 将常量池条目标记为Integer类型.
     */
    public static final byte CONSTANT_Integer        = 3;

    /** 将常量池条目标记为Float类型.
     */
    public static final byte CONSTANT_Float          = 4;

    /** 将常量池条目标记为Long类型.
     */
    public static final byte CONSTANT_Long           = 5;

    /** 将常量池条目标记为Double类型.
     */
    public static final byte CONSTANT_Double         = 6;

    /** 将常量池条目标记为Class.
     */
    public static final byte CONSTANT_Class          = 7;

    /** 将常量池条目标记为Field引用.
     */
    public static final byte CONSTANT_Fieldref         = 9;

    /** 将常量池条目标记为String类型.
     */
    public static final byte CONSTANT_String         = 8;

    /** 将常量池条目标记为Method引用.
     */
    public static final byte CONSTANT_Methodref        = 10;

    /** 将常量池条目标记为接口方法引用.
     */
    public static final byte CONSTANT_InterfaceMethodref = 11;

    /** 将常量池条目标记为名称和类型.
     */
    public static final byte CONSTANT_NameAndType      = 12;

    /** 将常量池条目标记为Method句柄.
     */
    public static final byte CONSTANT_MethodHandle     = 15;

    /** 将常量池条目标记为Method类型.
     */
    public static final byte CONSTANT_MethodType       = 16;

    /** 将常量池条目标记为Invoke Dynamic
     */
    public static final byte CONSTANT_InvokeDynamic    = 18;

    /** 将常量池条目标记为模块引用.
     * Note: 早期访问Java 9支持- 目前可能会有变化.
     */
    public static final byte CONSTANT_Module             = 19;

    /** 将常量池条目标记为包引用.
     * Note: 早期访问Java 9支持- 目前可能会有变化.
     */
    public static final byte CONSTANT_Package            = 20;

    /**
     * 常量池中条目类型的名称.
     * 请改用getConstantName
     */
    private static final String[] CONSTANT_NAMES = {
    "", "CONSTANT_Utf8", "", "CONSTANT_Integer",
    "CONSTANT_Float", "CONSTANT_Long", "CONSTANT_Double",
    "CONSTANT_Class", "CONSTANT_String", "CONSTANT_Fieldref",
    "CONSTANT_Methodref", "CONSTANT_InterfaceMethodref",
    "CONSTANT_NameAndType", "", "", "CONSTANT_MethodHandle",
    "CONSTANT_MethodType", "", "CONSTANT_InvokeDynamic",
    "CONSTANT_Module", "CONSTANT_Package"};

    public static String getConstantName(int index) {
        return CONSTANT_NAMES[index];
    }
}
