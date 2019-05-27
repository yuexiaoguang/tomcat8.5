package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInput;
import java.io.IOException;

import org.apache.tomcat.util.bcel.Const;

/**
 * 表示对Double对象的引用.
 */
public final class ConstantDouble extends Constant {

    private final double bytes;


    /**
     * 从文件数据初始化实例.
     *
     * @param file 输入流
     * @throws IOException
     */
    ConstantDouble(final DataInput file) throws IOException {
        super(Const.CONSTANT_Double);
        this.bytes = file.readDouble();
    }


    /**
     * @return data, i.e., 8 bytes.
     */
    public final double getBytes() {
        return bytes;
    }
}
