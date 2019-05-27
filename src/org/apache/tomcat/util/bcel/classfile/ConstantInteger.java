package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInput;
import java.io.IOException;

import org.apache.tomcat.util.bcel.Const;

/**
 * 表示对int对象的引用.
 */
public final class ConstantInteger extends Constant {

    private final int bytes;


    /**
     * 从文件数据初始化实例.
     *
     * @param file 输入流
     * @throws IOException
     */
    ConstantInteger(final DataInput file) throws IOException {
        super(Const.CONSTANT_Integer);
        this.bytes = file.readInt();
    }


    /**
     * @return data, i.e., 4 bytes.
     */
    public final int getBytes() {
        return bytes;
    }
}
