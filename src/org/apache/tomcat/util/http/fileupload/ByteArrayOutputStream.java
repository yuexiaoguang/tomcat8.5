package org.apache.tomcat.util.http.fileupload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 此类实现一个输出流，其中数据被写入字节数组. 缓冲区会在数据写入时自动增长.
 * <p>
 * 可以使用<code>toByteArray()</code>和<code>toString()</code>检索数据.
 * <p>
 * 关闭一个<tt>ByteArrayOutputStream</tt>没有作用. 在关闭流之后可以调用此类中的方法，不会生成<tt>IOException</tt>.
 * <p>
 * 这是{@link java.io.ByteArrayOutputStream}类的替代实现. 原始实现仅在开始时分配32个字节.
 * 由于此类设计用于重载，因此它以1024字节开始. 与原始内容相比，它不会重新分配整个内存块，而是分配额外的缓冲区.
 * 这样，不需要对缓冲区进行垃圾回收，也不必将内容复制到新缓冲区. 此类的设计与原始类似. 唯一的例外是已被忽略的已弃用的toString(int)方法.
 */
public class ByteArrayOutputStream extends OutputStream {

    /** 单例空字节数组. */
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /** 缓冲区列表, 会成长, 并且不会减少. */
    private final List<byte[]> buffers = new ArrayList<>();
    /** 当前缓冲区的索引. */
    private int currentBufferIndex;
    /** 所有填充缓冲区中的总字节数. */
    private int filledBufferSum;
    /** 当前的缓冲区. */
    private byte[] currentBuffer;
    /** 写入的总字节数. */
    private int count;

    /**
     * 创建一个新的字节数组输出流. 缓冲区容量最初为1024字节
     */
    public ByteArrayOutputStream() {
        this(1024);
    }

    /**
     * @param size  初始大小
     * @throws IllegalArgumentException 如果大小是负数的
     */
    public ByteArrayOutputStream(int size) {
        if (size < 0) {
            throw new IllegalArgumentException(
                "Negative initial size: " + size);
        }
        synchronized (this) {
            needNewBuffer(size);
        }
    }

    /**
     * 通过分配新缓冲区或重新使用现有缓冲区来创建新缓冲区.
     *
     * @param newcount  缓冲区的大小
     */
    private void needNewBuffer(int newcount) {
        if (currentBufferIndex < buffers.size() - 1) {
            // 循环使用旧缓冲区
            filledBufferSum += currentBuffer.length;

            currentBufferIndex++;
            currentBuffer = buffers.get(currentBufferIndex);
        } else {
            // Creating new buffer
            int newBufferSize;
            if (currentBuffer == null) {
                newBufferSize = newcount;
                filledBufferSum = 0;
            } else {
                newBufferSize = Math.max(
                    currentBuffer.length << 1,
                    newcount - filledBufferSum);
                filledBufferSum += currentBuffer.length;
            }

            currentBufferIndex++;
            currentBuffer = new byte[newBufferSize];
            buffers.add(currentBuffer);
        }
    }

    /**
     * 将字节写入字节数组.
     * 
     * @param b 要写入的字节
     * @param off 开始偏移量
     * @param len 要写入的字节数
     */
    @Override
    public void write(byte[] b, int off, int len) {
        if ((off < 0)
                || (off > b.length)
                || (len < 0)
                || ((off + len) > b.length)
                || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        synchronized (this) {
            int newcount = count + len;
            int remaining = len;
            int inBufferPos = count - filledBufferSum;
            while (remaining > 0) {
                int part = Math.min(remaining, currentBuffer.length - inBufferPos);
                System.arraycopy(b, off + len - remaining, currentBuffer, inBufferPos, part);
                remaining -= part;
                if (remaining > 0) {
                    needNewBuffer(newcount);
                    inBufferPos = 0;
                }
            }
            count = newcount;
        }
    }

    /**
     * 将字节写入字节数组.
     * 
     * @param b 要写入的字节
     */
    @Override
    public synchronized void write(int b) {
        int inBufferPos = count - filledBufferSum;
        if (inBufferPos == currentBuffer.length) {
            needNewBuffer(count + 1);
            inBufferPos = 0;
        }
        currentBuffer[inBufferPos] = (byte) b;
        count++;
    }

    /**
     * 将指定输入流的全部内容写入此字节流. 来自输入流的字节直接读入此流的内部缓冲区.
     *
     * @param in 要读取的输入流
     * 
     * @return 从输入流中读取的总字节数 (并写入此流)
     * @throws IOException 如果在读取输入流时发生I/O错误
     */
    public synchronized int write(InputStream in) throws IOException {
        int readCount = 0;
        int inBufferPos = count - filledBufferSum;
        int n = in.read(currentBuffer, inBufferPos, currentBuffer.length - inBufferPos);
        while (n != -1) {
            readCount += n;
            inBufferPos += n;
            count += n;
            if (inBufferPos == currentBuffer.length) {
                needNewBuffer(currentBuffer.length);
                inBufferPos = 0;
            }
            n = in.read(currentBuffer, inBufferPos, currentBuffer.length - inBufferPos);
        }
        return readCount;
    }

    /**
     * 关闭<tt>ByteArrayOutputStream</tt>无效. 在关闭流之后可以调用此类中的方法，不会生成<tt>IOException</tt>.
     *
     * @throws IOException never (此方法不应声明此异常，但由于向后兼容性)
     */
    @Override
    public void close() throws IOException {
        //nop
    }

    /**
     * 将此字节流的全部内容写入指定的输出流.
     *
     * @param out  要写入的输出流
     * @throws IOException 如果发生I/O错误, 例如流被关闭
     */
    public synchronized void writeTo(OutputStream out) throws IOException {
        int remaining = count;
        for (byte[] buf : buffers) {
            int c = Math.min(buf.length, remaining);
            out.write(buf, 0, c);
            remaining -= c;
            if (remaining == 0) {
                break;
            }
        }
    }

    /**
     * 获取此字节流的当前内容作为字节数组. 结果与此流无关.
     *
     * @return 此输出流的当前内容, 作为字节数组
     */
    public synchronized byte[] toByteArray() {
        int remaining = count;
        if (remaining == 0) {
            return EMPTY_BYTE_ARRAY;
        }
        byte newbuf[] = new byte[remaining];
        int pos = 0;
        for (byte[] buf : buffers) {
            int c = Math.min(buf.length, remaining);
            System.arraycopy(buf, 0, newbuf, pos, c);
            pos += c;
            remaining -= c;
            if (remaining == 0) {
                break;
            }
        }
        return newbuf;
    }

    @Override
    public String toString() {
        return new String(toByteArray());
    }
}
