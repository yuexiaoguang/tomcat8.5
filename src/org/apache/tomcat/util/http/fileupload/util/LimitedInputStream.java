package org.apache.tomcat.util.http.fileupload.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 限制其数据大小的输入流. 如果内容长度未知，则使用此流.
 */
public abstract class LimitedInputStream extends FilterInputStream implements Closeable {

    /**
     * 项目的最大大小, 以字节为单位.
     */
    private final long sizeMax;

    /**
     * 当前的字节数.
     */
    private long count;

    /**
     * 此流是否已关闭.
     */
    private boolean closed;

    /**
     * @param inputStream 应受限制的输入流.
     * @param pSizeMax 限制; 源流不应返回此字节数.
     */
    public LimitedInputStream(InputStream inputStream, long pSizeMax) {
        super(inputStream);
        sizeMax = pSizeMax;
    }

    /**
     * 已超出输入流限制.
     *
     * @param pSizeMax 输入流限制, 以字节为单位.
     * @param pCount 实际的字节数.
     * 
     * @throws IOException 被调用的方法应该引发IOException.
     */
    protected abstract void raiseError(long pSizeMax, long pCount)
            throws IOException;

    /**
     * 是否达到输入流限制.
     *
     * @throws IOException 超出给定限制.
     */
    private void checkLimit() throws IOException {
        if (count > sizeMax) {
            raiseError(sizeMax, count);
        }
    }

    /**
     * 从此输入流中读取下一个数据字节.
     * 值字节作为<code>int</code>返回，范围是<code>0</code>到<code>255</code>.
     * 如果没有可用的字节, 因为已到达流的末尾, 返回<code>-1</code>. 此方法将阻塞, 直到输入数据可用, 检测到流的末尾, 或抛出异常.
     * <p>
     * 这个方法执行<code>in.read()</code>并返回结果.
     *
     * @return     下一个数据字节, 或<code>-1</code>如果到达流的末尾.
     * @throws  IOException  如果发生I/O错误.
     */
    @Override
    public int read() throws IOException {
        int res = super.read();
        if (res != -1) {
            count++;
            checkLimit();
        }
        return res;
    }

    /**
     * 将此输入流中的<code>len</code>字节数据读入一个字节数组.
     * 如果<code>len</code>不为零, 该方法将阻塞, 直到某些输入可用; 否则，不读取任何字节，并返回<code>0</code>.
     * <p>
     * 这个方法执行<code>in.read(b, off, len)</code>并返回结果.
     *
     * @param      b     读取数据的缓冲区.
     * @param      off   目标数组<code>b</code>中的起始偏移量.
     * @param      len   读取的最大字节数.
     * 
     * @return     读入缓冲区的总字节数, 如果由于已到达流的末尾而没有更多数据, 则为<code>-1</code>.
     *             
     * @throws  NullPointerException 如果<code>b</code>是<code>null</code>.
     * @throws  IndexOutOfBoundsException 如果<code>off</code>是负数, <code>len</code>是负数,
     * 		或<code>len</code>大于<code>b.length - off</code>
     * @throws  IOException  如果发生I/O错误.
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int res = super.read(b, off, len);
        if (res > 0) {
            count += res;
            checkLimit();
        }
        return res;
    }

    /**
     * 此流是否已关闭.
     *
     * @return True流已关闭, 否则 false.
     * @throws IOException 如果发生I/O错误.
     */
    @Override
    public boolean isClosed() throws IOException {
        return closed;
    }

    /**
     * 关闭此输入流并释放与该流关联的所有系统资源.
     * 方法简单地执行<code>in.close()</code>.
     *
     * @throws  IOException  如果发生I/O错误.
     */
    @Override
    public void close() throws IOException {
        closed = true;
        super.close();
    }

}
