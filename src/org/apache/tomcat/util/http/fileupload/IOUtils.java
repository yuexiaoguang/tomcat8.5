package org.apache.tomcat.util.http.fileupload;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 一般IO流操作.
 * <p>
 * Origin of code: Excalibur.
 */
public class IOUtils {
    // NOTE: 该类主要关注InputStream，OutputStream，Reader和Writer. 每个方法应至少将其中一个作为参数，或返回其中一个.

    private static final int EOF = -1;

    /**
     * 用于{@link #copyLarge(InputStream, OutputStream)}和{@link #copyLarge(Reader, Writer)}的默认缓冲区大小
     */
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    public IOUtils() {
        super();
    }

    /**
     * 无条件地关闭<code>Closeable</code>.
     * <p>
     * 等同于 {@link Closeable#close()}, 除了任何异常都将被忽略. 这通常用在finally块中.
     * <p>
     * Example code:
     *
     * <pre>
     * Closeable closeable = null;
     * try {
     *     closeable = new FileReader(&quot;foo.txt&quot;);
     *     // process closeable
     *     closeable.close();
     * } catch (Exception e) {
     *     // error handling
     * } finally {
     *     IOUtils.closeQuietly(closeable);
     * }
     * </pre>
     *
     * Closing all streams:
     *
     * <pre>
     * try {
     *     return IOUtils.copy(inputStream, outputStream);
     * } finally {
     *     IOUtils.closeQuietly(inputStream);
     *     IOUtils.closeQuietly(outputStream);
     * }
     * </pre>
     *
     * @param closeable 要关闭的对象, 可能为null或已经关闭
     * @since 2.0
     */
    public static void closeQuietly(final Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (final IOException ioe) {
            // ignore
        }
    }

    // copy from InputStream
    //-----------------------------------------------------------------------
    /**
     * 将字节从<code>InputStream</code>复制到<code>OutputStream</code>.
     * <p>
     * 此方法在内部缓冲输入, 不需要使用<code>BufferedInputStream</code>.
     * <p>
     * 复制完成后，大流(超过2GB)将返回<code>-1</code>的字节复制值，因为正确的字节数不能作为int返回.
     * 对于大流, 使用 <code>copyLarge(InputStream, OutputStream)</code>方法.
     *
     * @param input  要读取的<code>InputStream</code>
     * @param output  要写入的<code>OutputStream</code>
     * 
     * @return 复制的字节数, 或 -1 如果 &gt; Integer.MAX_VALUE
     * 
     * @throws NullPointerException 如果输入或输出为 null
     * @throws IOException 如果发生I/O错误
     * @since 1.1
     */
    public static int copy(InputStream input, OutputStream output) throws IOException {
        long count = copyLarge(input, output);
        if (count > Integer.MAX_VALUE) {
            return -1;
        }
        return (int) count;
    }

    /**
     * 将字节从大（超过2GB）<code>InputStream</code>复制到<code>OutputStream</code>.
     * <p>
     * 此方法在内部缓冲输入, 不需要使用<code>BufferedInputStream</code>.
     * <p>
     * 缓冲区大小由{@link #DEFAULT_BUFFER_SIZE}给出.
     *
     * @param input  要读取的<code>InputStream</code>
     * @param output  要写入的<code>OutputStream</code>
     * 
     * @return 复制的字节数
     * 
     * @throws NullPointerException 如果输入或输出为 null
     * @throws IOException 如果发生I/O错误
     * @since 1.3
     */
    public static long copyLarge(InputStream input, OutputStream output)
            throws IOException {

        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n = 0;
        while (EOF != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    /**
     * 从输入流中读取字节.
     * 此实现保证在放弃之前将读取尽可能多的字节; 对于{@link InputStream}的子类，情况可能并非总是如此.
     *
     * @param input 要读取的输入
     * @param buffer 目标
     * @param offset 缓冲区的初始偏移量
     * @param length 读取的长度, 必须 &gt;= 0
     * 
     * @return 读取的实际长度; 如果达到EOF，可能会低于要求
     * @throws IOException 如果发生读取错误
     * @since 2.2
     */
    public static int read(final InputStream input, final byte[] buffer, final int offset, final int length) throws IOException {
        if (length < 0) {
            throw new IllegalArgumentException("Length must not be negative: " + length);
        }
        int remaining = length;
        while (remaining > 0) {
            final int location = length - remaining;
            final int count = input.read(buffer, offset + location, remaining);
            if (EOF == count) { // EOF
                break;
            }
            remaining -= count;
        }
        return length - remaining;
    }

    /**
     * 读取请求的字节数，如果没有足够的话，则会失败.
     * <p>
     * 这允许{@link InputStream#read(byte[], int, int)}可能无法读取所请求的字节数（最有可能因为达到EOF）.
     *
     * @param input 要读取的输入
     * @param buffer 目标
     * @param offset 缓冲区的初始偏移量
     * @param length 读取的长度, 必须 &gt;= 0
     *
     * @throws IOException 如果读取文件有问题
     * @throws IllegalArgumentException 如果长度是负数
     * @throws EOFException 如果读取的字节数不正确
     * @since 2.2
     */
    public static void readFully(final InputStream input, final byte[] buffer, final int offset, final int length) throws IOException {
        final int actual = read(input, buffer, offset, length);
        if (actual != length) {
            throw new EOFException("Length to read: " + length + " actual: " + actual);
        }
    }

    /**
     * 读取请求的字节数，如果没有足够的话，则会失败.
     * <p>
     * 这允许{@link InputStream#read(byte[], int, int)}可能无法读取所请求的字节数（最有可能因为达到EOF）.
     *
     * @param input 要读取的输入
     * @param buffer 目标
     *
     * @throws IOException 如果读取文件有问题
     * @throws IllegalArgumentException 如果长度是负数
     * @throws EOFException 如果读取的字节数不正确
     * @since 2.2
     */
    public static void readFully(final InputStream input, final byte[] buffer) throws IOException {
        readFully(input, buffer, 0, buffer.length);
    }
}
