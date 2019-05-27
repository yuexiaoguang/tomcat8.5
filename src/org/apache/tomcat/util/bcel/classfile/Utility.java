package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;

import org.apache.tomcat.util.bcel.Const;

/**
 * 工具类, 并不特别属于任何类.
 */
final class Utility {

    private Utility() {
        // Hide default constructor
    }

    /**
     * 缩短长类名 <em>str</em>, i.e., 去掉<em>prefix</em>, 如果类名以此字符串开头, 而且标志 <em>chopit</em> 是 true.
     * 斜杠 <em>/</em>被转换为点 <em>.</em>.
     *
     * @param str 长类名
     * @return 压缩的类名
     */
    static String compactClassName(final String str) {
        return str.replace('/', '.'); // Is `/' on all systems, even DOS
    }

    static String getClassName(final ConstantPool constant_pool, final int index) {
        Constant c = constant_pool.getConstant(index, Const.CONSTANT_Class);
        int i = ((ConstantClass) c).getNameIndex();

        // 最后从常量池中获取字符串
        c = constant_pool.getConstant(i, Const.CONSTANT_Utf8);
        String name = ((ConstantUtf8) c).getBytes();

        return compactClassName(name);
    }

    static void skipFully(final DataInput file, final int length) throws IOException {
        int total = file.skipBytes(length);
        if (total != length) {
            throw new EOFException();
        }
    }

    static void swallowFieldOrMethod(final DataInput file)
            throws IOException {
        // file.readUnsignedShort(); // Unused access flags
        // file.readUnsignedShort(); // name index
        // file.readUnsignedShort(); // signature index
        skipFully(file, 6);

        int attributes_count = file.readUnsignedShort();
        for (int i = 0; i < attributes_count; i++) {
            swallowAttribute(file);
        }
    }

    static void swallowAttribute(final DataInput file)
            throws IOException {
        //file.readUnsignedShort();   // Unused name index
        skipFully(file, 2);
        // Length of data in bytes
        int length = file.readInt();
        skipFully(file, length);
    }
}
