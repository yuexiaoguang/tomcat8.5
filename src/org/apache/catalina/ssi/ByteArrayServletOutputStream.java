package org.apache.catalina.ssi;

import java.io.ByteArrayOutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;


/**
 * 封装<code>SsiInclude</code>到内部
 */
public class ByteArrayServletOutputStream extends ServletOutputStream {
    /**
     * 保存流的缓冲区.
     */
    protected final ByteArrayOutputStream buf;


    public ByteArrayServletOutputStream() {
        buf = new ByteArrayOutputStream();
    }


    /**
     * @return the byte array.
     */
    public byte[] toByteArray() {
        return buf.toByteArray();
    }


    /**
     * 写入缓冲区
     *
     * @param b The parameter to write
     */
    @Override
    public void write(int b) {
        buf.write(b);
    }

    /**
     * TODO SERVLET 3.1
     */
    @Override
    public boolean isReady() {
        return false;
    }


    /**
     * TODO SERVLET 3.1
     */
    @Override
    public void setWriteListener(WriteListener listener) {
    }
}
