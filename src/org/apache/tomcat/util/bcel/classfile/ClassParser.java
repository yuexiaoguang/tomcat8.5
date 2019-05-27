package org.apache.tomcat.util.bcel.classfile;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tomcat.util.bcel.Const;

/**
 * 解析给定Java .class文件的包装类.
 * 当发生I/O错误或不一致时，适当的异常将返回给调用方.
 */
public final class ClassParser {

    private static final int MAGIC = 0xCAFEBABE;

    private final DataInput dataInputStream;
    private String class_name, superclass_name;
    private int access_flags; // 已解析类的访问权限
    private String[] interface_names; // 已实现接口的名称
    private ConstantPool constant_pool; // 常量集合
    private Annotations runtimeVisibleAnnotations; // 类中定义的"RuntimeVisibleAnnotations" 属性
    private static final int BUFSIZE = 8192;

    private static final String[] INTERFACES_EMPTY_ARRAY = new String[0];

    /**
     * 从给定流中解析类.
     *
     * @param inputStream 输入流
     */
    public ClassParser(final InputStream inputStream) {
        this.dataInputStream = new DataInputStream(new BufferedInputStream(inputStream, BUFSIZE));
    }


    /**
     * 解析给定的Java类文件并返回表示包含数据的对象, i.e., 常量, 方法, 字段和命令.
     * 如果文件不是一个有效的.class文件, 抛出<em>ClassFormatException</em>. (这不包括由java解释器执行的字节代码的验证).
     *
     * @return 表示已解析的类文件的类对象
     * @throws  IOException 如果读取字节码时发生I/O异常
     * @throws  ClassFormatException 如果字节码无效
     */
    public JavaClass parse() throws IOException, ClassFormatException {
        /****************** Read headers ********************************/
        // 检查类文件的魔术标签
        readID();
        // 获取编译器版本
        readVersion();
        /****************** Read constant pool and related **************/
        // 读取常量池条目
        readConstantPool();
        // 获取类信息
        readClassInfo();
        // 获取接口信息, i.e., 实现的接口
        readInterfaces();
        /****************** Read class fields and methods ***************/
        // 读取类字段, i.e., 类的变量
        readFields();
        // 读取类方法, i.e., 类中的函数
        readMethods();
        // 读取类属性
        readAttributes();

        // 返回在新对象中收集的信息
        return new JavaClass(class_name, superclass_name,
                access_flags, constant_pool, interface_names,
                runtimeVisibleAnnotations);
    }


    /**
     * 读取有关该类属性的信息.
     * 
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readAttributes() throws IOException, ClassFormatException {
        final int attributes_count = dataInputStream.readUnsignedShort();
        for (int i = 0; i < attributes_count; i++) {
            ConstantUtf8 c;
            String name;
            int name_index;
            int length;
            // 从常量池中通过`name_index'间接获取类名
            name_index = dataInputStream.readUnsignedShort();
            c = (ConstantUtf8) constant_pool.getConstant(name_index,
                    Const.CONSTANT_Utf8);
            name = c.getBytes();
            // 以字节为单位的数据长度
            length = dataInputStream.readInt();

            if (name.equals("RuntimeVisibleAnnotations")) {
                if (runtimeVisibleAnnotations != null) {
                    throw new ClassFormatException(
                            "RuntimeVisibleAnnotations attribute is not allowed more than once in a class file");
                }
                runtimeVisibleAnnotations = new Annotations(dataInputStream, constant_pool);
            } else {
                // 跳过所有其他属性
                Utility.skipFully(dataInputStream, length);
            }
        }
    }


    /**
     * 读取有关该类及其超类的信息.
     * 
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readClassInfo() throws IOException, ClassFormatException {
        access_flags = dataInputStream.readUnsignedShort();
        /* 接口是隐式抽象的, 应根据JVM规范设置标志.
         */
        if ((access_flags & Const.ACC_INTERFACE) != 0) {
            access_flags |= Const.ACC_ABSTRACT;
        }
        if (((access_flags & Const.ACC_ABSTRACT) != 0)
                && ((access_flags & Const.ACC_FINAL) != 0)) {
            throw new ClassFormatException("Class can't be both final and abstract");
        }

        int class_name_index = dataInputStream.readUnsignedShort();
        class_name = Utility.getClassName(constant_pool, class_name_index);

        int superclass_name_index = dataInputStream.readUnsignedShort();
        if (superclass_name_index > 0) {
            // 可能是零 -> 类是 java.lang.Object
            superclass_name = Utility.getClassName(constant_pool, superclass_name_index);
        } else {
            superclass_name = "java.lang.Object";
        }
    }


    /**
     * 读取常量池条目.
     * 
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readConstantPool() throws IOException, ClassFormatException {
        constant_pool = new ConstantPool(dataInputStream);
    }


    /**
     * 读取有关该类字段的信息, i.e., 它的变量.
     * 
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readFields() throws IOException, ClassFormatException {
        final int fields_count = dataInputStream.readUnsignedShort();
        for (int i = 0; i < fields_count; i++) {
            Utility.swallowFieldOrMethod(dataInputStream);
        }
    }


    /******************** Private utility methods **********************/
    /**
     * 检查文件的标题是否正常. 当然，这必须是连续文件读取的第一个操作.
     * 
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readID() throws IOException, ClassFormatException {
        if (dataInputStream.readInt() != MAGIC) {
            throw new ClassFormatException("It is not a Java .class file");
        }
    }


    /**
     * 读取有关此类实现的接口的信息.
     * 
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readInterfaces() throws IOException, ClassFormatException {
        final int interfaces_count = dataInputStream.readUnsignedShort();
        if (interfaces_count > 0) {
            interface_names = new String[interfaces_count];
            for (int i = 0; i < interfaces_count; i++) {
                int index = dataInputStream.readUnsignedShort();
                interface_names[i] = Utility.getClassName(constant_pool, index);
            }
        } else {
            interface_names = INTERFACES_EMPTY_ARRAY;
        }
    }


    /**
     * 读取有关类方法的信息.
     * 
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readMethods() throws IOException, ClassFormatException {
        final int methods_count = dataInputStream.readUnsignedShort();
        for (int i = 0; i < methods_count; i++) {
            Utility.swallowFieldOrMethod(dataInputStream);
        }
    }


    /**
     * 读取创建该文件的编译器的主要版本和次要版本.
     * 
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readVersion() throws IOException, ClassFormatException {
        // file.readUnsignedShort(); // Unused minor
        // file.readUnsignedShort(); // Unused major
        Utility.skipFully(dataInputStream, 4);
    }
}
