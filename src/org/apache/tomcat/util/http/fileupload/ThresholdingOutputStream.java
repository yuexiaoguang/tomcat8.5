package org.apache.tomcat.util.http.fileupload;

import java.io.IOException;
import java.io.OutputStream;


/**
 * 输出流，当向其写入指定数量的字节数据时触发事件. 该事件可以使用, 例如, 如果达到最大值则抛出异常, 或者在超过阈值时切换底层流类型.
 * <p>
 * 该类重写了所有<code>OutputStream</code>方法. 但是, 这些覆盖最终调用底层输出流实现中的相应方法.
 * <p>
 * NOTE: 该实现可以在实际达到阈值之前触发事件, 因为当挂起的写操作会导致超过阈值时触发.
 */
public abstract class ThresholdingOutputStream
    extends OutputStream
{

    // ----------------------------------------------------------- Data members


    /**
     * 触发事件的阈值.
     */
    private final int threshold;


    /**
     * 写入输出流的字节数.
     */
    private long written;


    /**
     * 是否已超出配置的阈值.
     */
    private boolean thresholdExceeded;


    // ----------------------------------------------------------- Constructors


    /**
     * @param threshold 触发事件的字节数.
     */
    public ThresholdingOutputStream(int threshold)
    {
        this.threshold = threshold;
    }


    // --------------------------------------------------- OutputStream methods


    /**
     * 将指定的字节写入此输出流.
     *
     * @param b 要写入的字节.
     *
     * @exception IOException 如果发生错误.
     */
    @Override
    public void write(int b) throws IOException
    {
        checkThreshold(1);
        getStream().write(b);
        written++;
    }


    /**
     * 将<code> b.length </ code>字节从指定的字节数组写入此输出流.
     *
     * @param b 要写入的字节数组.
     *
     * @exception IOException 如果发生错误.
     */
    @Override
    public void write(byte b[]) throws IOException
    {
        checkThreshold(b.length);
        getStream().write(b);
        written += b.length;
    }


    /**
     * 将从<code>off</code>开始的指定字节数组中的<code>len</code>字节写入此输出流.
     *
     * @param b   将从中写入数据的字节数组.
     * @param off 字节数组中的起始偏移量.
     * @param len 要写入的字节数.
     *
     * @exception IOException 如果发生错误.
     */
    @Override
    public void write(byte b[], int off, int len) throws IOException
    {
        checkThreshold(len);
        getStream().write(b, off, len);
        written += len;
    }


    /**
     * 刷新此输出流并强制写出任何缓冲的输出字节.
     *
     * @exception IOException 如果发生错误.
     */
    @Override
    public void flush() throws IOException
    {
        getStream().flush();
    }


    /**
     * 关闭此输出流并释放与此流关联的所有系统资源.
     *
     * @exception IOException 如果发生错误.
     */
    @Override
    public void close() throws IOException
    {
        try
        {
            flush();
        }
        catch (IOException ignored)
        {
            // ignore
        }
        getStream().close();
    }


    // --------------------------------------------------------- Public methods


    /**
     * 确定是否已超出此输出流的配置阈值.
     *
     * @return {@code true} 如果已达到阈值;
     *         {@code false} 否则.
     */
    public boolean isThresholdExceeded()
    {
        return written > threshold;
    }


    // ------------------------------------------------------ Protected methods


    /**
     * 检查是否写入指定的字节数将超出配置的阈值. 如果是这样, 触发事件以允许具体实现对此采取操作.
     *
     * @param count 要写入底层输出流的字节数.
     *
     * @exception IOException 如果发生错误.
     */
    protected void checkThreshold(int count) throws IOException
    {
        if (!thresholdExceeded && written + count > threshold)
        {
            thresholdExceeded = true;
            thresholdReached();
        }
    }

    // ------------------------------------------------------- Abstract methods


    /**
     * 返回底层输出流
     *
     * @return 底层输出流.
     *
     * @exception IOException 如果发生错误.
     */
    protected abstract OutputStream getStream() throws IOException;


    /**
     * 表示已达到配置的阈值, 并且子类应该采取此事件所需的任何操作. 这可能包括更改底层输出流.
     *
     * @exception IOException 如果发生错误.
     */
    protected abstract void thresholdReached() throws IOException;
}
