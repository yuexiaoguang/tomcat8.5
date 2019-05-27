package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInput;
import java.io.IOException;

import org.apache.tomcat.util.bcel.Const;

/**
 * 此类表示常量池, i.e., 已解析的类文件的常量表.
 * 它可能包含 null 引用, 由于JVM规范在8字节常量(double, long)条目之后跳过条目.
 */
public class ConstantPool {

    private final Constant[] constant_pool;


    /**
     * @param input 输入流
     * @throws IOException
     * @throws ClassFormatException
     */
    ConstantPool(final DataInput input) throws IOException, ClassFormatException {
        final int constant_pool_count = input.readUnsignedShort();
        constant_pool = new Constant[constant_pool_count];
        /* 编译器未使用constant_pool[0], 可以由实现自由使用.
         */
        for (int i = 1; i < constant_pool_count; i++) {
            constant_pool[i] = Constant.readConstant(input);
            /* 引用JVM规范:
             * "所有八个字节常量占用常量池中的两个点. 如果这是常量池中的第n个字节, 然后下一个项目将被编号为n + 2"
             *
             * 因此必须增加索引计数器.
             */
            if (constant_pool[i] != null) {
                byte tag = constant_pool[i].getTag();
                if ((tag == Const.CONSTANT_Double) || (tag == Const.CONSTANT_Long)) {
                    i++;
                }
            }
        }
    }


    /**
     * 从常量池获得常量.
     *
     * @param  index 常量池中的索引
     * 
     * @return 常量值
     */
    public Constant getConstant( final int index ) {
        if (index >= constant_pool.length || index < 0) {
            throw new ClassFormatException("Invalid constant pool reference: " + index
                    + ". Constant pool size is: " + constant_pool.length);
        }
        return constant_pool[index];
    }


    /**
     * 从常量池中获取常量并检查它是否具有预期的类型.
     *
     * @param  index 常量池中的索引
     * @param  tag 预期常量的标记, i.e., 它的类型
     * 
     * @return 常量值
     * @throws  ClassFormatException 如果常量不是预期的类型
     */
    public Constant getConstant( final int index, final byte tag ) throws ClassFormatException {
        Constant c;
        c = getConstant(index);
        if (c == null) {
            throw new ClassFormatException("Constant pool at index " + index + " is null.");
        }
        if (c.getTag() != tag) {
            throw new ClassFormatException("Expected class `" + Const.getConstantName(tag)
                    + "' at index " + index + " and got " + c);
        }
        return c;
    }
}
