package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInput;
import java.io.IOException;

import org.apache.tomcat.util.bcel.Const;

/**
 * 表示对 Long 对象的引用.
 */
public final class ConstantLong extends Constant {

    private final long bytes;


    /**
     * 从文件数据初始化实例.
     *
     * @param file 输入流
     * @throws IOException
     */
    ConstantLong(final DataInput input) throws IOException {
        super(Const.CONSTANT_Long);
        this.bytes = input.readLong();
    }


    /**
     * @return 数据, i.e., 8 bytes.
     */
    public final long getBytes() {
        return bytes;
    }
}
