package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInput;
import java.io.IOException;

import org.apache.tomcat.util.bcel.Const;

/**
 * 表示对float对象的引用.
 */
public final class ConstantFloat extends Constant {

    private final float bytes;


    /**
     * 从文件数据初始化实例.
     *
     * @param file 输入流
     * @throws IOException
     */
    ConstantFloat(final DataInput file) throws IOException {
        super(Const.CONSTANT_Float);
        this.bytes = file.readFloat();
    }


    /**
     * @return data, i.e., 4 bytes.
     */
    public final float getBytes() {
        return bytes;
    }
}
