package javax.servlet.http;

import java.io.IOException;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;

/**
 * {@link HttpUpgradeHandler}与升级过的HTTP连接进行交互.
 */
public interface WebConnection extends AutoCloseable {

    /**
     * 从客户端读取数据.
     *
     * @return the input stream
     *
     * @throws IOException 如果在获取流时发生I/O错误
     */
    ServletInputStream getInputStream() throws IOException;

    /**
     * 写入数据到客户端.
     *
     * @return the output stream
     *
     * @throws IOException 如果在获取流时发生I/O错误
     */
    ServletOutputStream getOutputStream() throws IOException;
}