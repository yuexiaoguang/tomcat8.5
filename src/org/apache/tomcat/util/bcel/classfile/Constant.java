package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInput;
import java.io.IOException;

import org.apache.tomcat.util.bcel.Const;

/**
 * 抽象超类，用于表示类文件的常量池中的不同常量类型. 这些类与JVM规范密切相关.
 */
public abstract class Constant {

    /* 事实上，这个标签是多余的，因为我们可以按类型区分不同的'Constant'对象, i.e., 通过 `instanceof'.
     * 在某些地方，无论如何我们都会使用switch()的标签.
     *
     * 首先，我们希望尽可能地匹配规范.
     * 其次，我们需要将标记作为索引从`CONSTANT_NAMES'数组中选择相应的类名.
     */
    protected final byte tag;


    Constant(final byte tag) {
        this.tag = tag;
    }


    /**
     * @return 常量的标记, i.e., 它的类型. 没有setTag()方法可以避免混淆.
     */
    public final byte getTag() {
        return tag;
    }


    /**
     * 从给定输入中读取一个常量, 类型取决于标记字节.
     *
     * @param input 输入流
     * @return Constant object
     */
    static Constant readConstant(final DataInput input) throws IOException,
            ClassFormatException {
        final byte b = input.readByte(); // Read tag byte
        int skipSize;
        switch (b) {
            case Const.CONSTANT_Class:
                return new ConstantClass(input);
            case Const.CONSTANT_Integer:
                return new ConstantInteger(input);
            case Const.CONSTANT_Float:
                return new ConstantFloat(input);
            case Const.CONSTANT_Long:
                return new ConstantLong(input);
            case Const.CONSTANT_Double:
                return new ConstantDouble(input);
            case Const.CONSTANT_Utf8:
                return ConstantUtf8.getInstance(input);
            case Const.CONSTANT_String:
            case Const.CONSTANT_MethodType:
            case Const.CONSTANT_Module:
            case Const.CONSTANT_Package:
                skipSize = 2; // unsigned short
                break;
            case Const.CONSTANT_MethodHandle:
                skipSize = 3; // unsigned byte, unsigned short
                break;
            case Const.CONSTANT_Fieldref:
            case Const.CONSTANT_Methodref:
            case Const.CONSTANT_InterfaceMethodref:
            case Const.CONSTANT_NameAndType:
            case Const.CONSTANT_InvokeDynamic:
                skipSize = 4; // unsigned short, unsigned short
                break;
            default:
                throw new ClassFormatException("Invalid byte tag in constant pool: " + b);
        }
        Utility.skipFully(input, skipSize);
        return null;
    }


    @Override
    public String toString() {
        return "[" + tag + "]";
    }
}
