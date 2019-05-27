package org.apache.catalina.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;


/**
 * 包含 I/O-相关工具方法
 */
public class IOTools {
    protected static final int DEFAULT_BUFFER_SIZE=4*1024; //4k

    private IOTools() {
      //Ensure non-instantiability
    }

    /**
     * 从读取器读取输入并将其写入写入器，直到读取器不再输入.
     *
     * @param reader the reader to read from.
     * @param writer the writer to write to.
     * @param buf 用于缓冲区的char 数组
     * @throws IOException IO error
     */
    public static void flow( Reader reader, Writer writer, char[] buf )
        throws IOException {
        int numRead;
        while ( (numRead = reader.read(buf) ) >= 0) {
            writer.write(buf, 0, numRead);
        }
    }

    /**
     * 从输入流读取输入，并将其写入输出流，直到输入流没有更多输入.
     *
     * @param reader the reader to read from.
     * @param writer the writer to write to.
     * @throws IOException IO error
     */
    public static void flow( Reader reader, Writer writer )
        throws IOException {
        char[] buf = new char[DEFAULT_BUFFER_SIZE];
        flow( reader, writer, buf );
    }


    /**
     * 从输入流读取输入，并将其写入输出流，直到输入流没有更多输入, 使用默认大小的缓冲区(4kB).
     *
     * @param is input stream the input stream to read from.
     * @param os output stream the output stream to write to.
     *
     * @throws IOException If an I/O error occurs during the copy
     */
    public static void flow(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
        int numRead;
        while ( (numRead = is.read(buf) ) >= 0) {
            os.write(buf, 0, numRead);
        }
    }
}
