package org.apache.catalina.tribes.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 直接暴露字节数组的字节数组输出流
 */
public class DirectByteArrayOutputStream extends OutputStream {

    private final XByteBuffer buffer;

    public DirectByteArrayOutputStream(int size) {
        buffer = new XByteBuffer(size,false);
    }

    /**
     * 将指定字节写入输出流.
     *
     * @param b the <code>byte</code>.
     * @throws IOException 如果发生I/O 错误. 特别地, 将抛出一个<code>IOException</code>, 如果输出流已经关闭.
     */
    @Override
    public void write(int b) throws IOException {
        buffer.append((byte)b);
    }

    public int size() {
        return buffer.getLength();
    }

    public byte[] getArrayDirect() {
        return buffer.getBytesDirect();
    }

    public byte[] getArray() {
        return buffer.getBytes();
    }
}