package org.apache.tomcat.util.http.fileupload.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.apache.tomcat.util.http.fileupload.InvalidFileNameException;

/**
 * 用于处理流的实用程序类.
 */
public final class Streams {

    private Streams() {
        // Does nothing
    }

    /**
     * {@link #copy(InputStream, OutputStream, boolean)}中使用的默认缓冲区大小.
     */
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * 将给定{@link InputStream}的内容复制到给定的{@link OutputStream}.
     * Shortcut for
     * <pre>
     *   copy(pInputStream, pOutputStream, new byte[8192]);
     * </pre>
     *
     * @param inputStream 正在读取的输入流. 保证在流上调用{@link InputStream#close()}.
     * @param outputStream 应写入数据的输出流. 可以为null，在这种情况下，输入流内容被简单地丢弃.
     * @param closeOutputStream True 保证在流上调用 {@link OutputStream#close()}.
     * 							False 最后只调用{@link OutputStream#flush()}.
     *
     * @return 已复制的字节数.
     * @throws IOException 发生I/O错误.
     */
    public static long copy(InputStream inputStream, OutputStream outputStream, boolean closeOutputStream)
            throws IOException {
        return copy(inputStream, outputStream, closeOutputStream, new byte[DEFAULT_BUFFER_SIZE]);
    }

    /**
     * 将给定{@link InputStream}的内容复制到给定的{@link OutputStream}.
     *
     * @param inputStream 正在读取的输入流. 保证在流上调用{@link InputStream#close()}.
     * @param outputStream 应写入数据的输出流. 可以为null，在这种情况下，输入流内容被简单地丢弃.
     * @param closeOutputStream True 保证在流上调用 {@link OutputStream#close()}.
     * 							False 最后只调用{@link OutputStream#flush()}.
     * @param buffer 用于复制数据的临时缓冲区.
     * 
     * @return 已复制的字节数.
     * @throws IOException 发生I/O错误.
     */
    public static long copy(InputStream inputStream,
            OutputStream outputStream, boolean closeOutputStream,
            byte[] buffer)
    throws IOException {
        OutputStream out = outputStream;
        InputStream in = inputStream;
        try {
            long total = 0;
            for (;;) {
                int res = in.read(buffer);
                if (res == -1) {
                    break;
                }
                if (res > 0) {
                    total += res;
                    if (out != null) {
                        out.write(buffer, 0, res);
                    }
                }
            }
            if (out != null) {
                if (closeOutputStream) {
                    out.close();
                } else {
                    out.flush();
                }
                out = null;
            }
            in.close();
            in = null;
            return total;
        } finally {
            IOUtils.closeQuietly(in);
            if (closeOutputStream) {
                IOUtils.closeQuietly(out);
            }
        }
    }

    /**
     * 读取{@link org.apache.tomcat.util.http.fileupload.FileItemStream}的内容为字符串.
     * 平台的默认字符编码用于将字节转换为字符.
     *
     * @param inputStream 要读取的输入流.
     * 
     * @return 流内容.
     * @throws IOException 发生I/O错误.
     */
    public static String asString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(inputStream, baos, true);
        return baos.toString();
    }

    /**
     * 读取{@link org.apache.tomcat.util.http.fileupload.FileItemStream}的内容为字符串, 使用给定的字符编码.
     *
     * @param inputStream 要读取的输入流.
     * @param encoding 字符编码, 通常为 "UTF-8".
     * 
     * @return 流内容.
     * @throws IOException 发生I/O错误.
     */
    public static String asString(InputStream inputStream, String encoding) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(inputStream, baos, true);
        return baos.toString(encoding);
    }

    /**
     * 给定的文件名是否有效，即它是否包含任何NUL字符.
     * 如果文件名有效, 它会在没有任何修改的情况下返回. 否则, 抛出 {@link InvalidFileNameException}.
     *
     * @param fileName 要检查的文件名
     * 
     * @return 未修改的文件名，如果有效.
     * @throws InvalidFileNameException 文件名无效.
     */
    public static String checkFileName(String fileName) {
        if (fileName != null  &&  fileName.indexOf('\u0000') != -1) {
            // pFileName.replace("\u0000", "\\0")
            final StringBuilder sb = new StringBuilder();
            for (int i = 0;  i < fileName.length();  i++) {
                char c = fileName.charAt(i);
                switch (c) {
                    case 0:
                        sb.append("\\0");
                        break;
                    default:
                        sb.append(c);
                        break;
                }
            }
            throw new InvalidFileNameException(fileName,
                    "Invalid file name: " + sb);
        }
        return fileName;
    }
}
