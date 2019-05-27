package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInput;
import java.io.IOException;

import org.apache.tomcat.util.bcel.Const;

/**
 * 表示对（外部）类的引用.
 */
public final class ConstantClass extends Constant {

    private final int name_index; // 与ConstantString相同，但名称除外


    /**
     * 从文件数据初始化实例.
     *
     * @param file 输入流
     * @throws IOException
     */
    ConstantClass(final DataInput file) throws IOException {
        super(Const.CONSTANT_Class);
        this.name_index = file.readUnsignedShort();
    }


    /**
     * @return 在类名的常量池中命名索引.
     */
    public final int getNameIndex() {
        return name_index;
    }
}
