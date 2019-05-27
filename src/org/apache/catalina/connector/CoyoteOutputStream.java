package org.apache.catalina.connector;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import org.apache.tomcat.util.res.StringManager;

/**
 * servlet输出流实现类.
 */
public class CoyoteOutputStream extends ServletOutputStream {

    protected static final StringManager sm = StringManager.getManager(CoyoteOutputStream.class);


    // ----------------------------------------------------- Instance Variables

    protected OutputBuffer ob;


    // ----------------------------------------------------------- Constructors


    protected CoyoteOutputStream(OutputBuffer ob) {
        this.ob = ob;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 防止克隆.
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }


    // -------------------------------------------------------- Package Methods


    /**
     * 清空
     */
    void clear() {
        ob = null;
    }


    // --------------------------------------------------- OutputStream Methods


    @Override
    public void write(int i) throws IOException {
        boolean nonBlocking = checkNonBlockingWrite();
        ob.writeByte(i);
        if (nonBlocking) {
            checkRegisterForWrite();
        }
    }


    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }


    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        boolean nonBlocking = checkNonBlockingWrite();
        ob.write(b, off, len);
        if (nonBlocking) {
            checkRegisterForWrite();
        }
    }


    public void write(ByteBuffer from) throws IOException {
        boolean nonBlocking = checkNonBlockingWrite();
        ob.write(from);
        if (nonBlocking) {
            checkRegisterForWrite();
        }
    }


    /**
     * 将向客户端发送缓冲区.
     */
    @Override
    public void flush() throws IOException {
        boolean nonBlocking = checkNonBlockingWrite();
        ob.flush();
        if (nonBlocking) {
            checkRegisterForWrite();
        }
    }


    /**
     * 检查不允许的并发写入. 此对象没有状态信息，因此调用链是
     * CoyoteOutputStream->OutputBuffer->CoyoteResponse.
     *
     * @return <code>true</code>如果这个OutputStream 当前处于非阻塞模式.
     */
    private boolean checkNonBlockingWrite() {
        boolean nonBlocking = !ob.isBlocking();
        if (nonBlocking && !ob.isReady()) {
            throw new IllegalStateException(sm.getString("coyoteOutputStream.nbNotready"));
        }
        return nonBlocking;
    }


    /**
     * 检查是否有数据留在Coyote输出缓冲区(不是servlet输出缓冲区) 如果这样，则注册相关的套接字进行写入，从而缓冲区将被清空.
     * 容器会处理这个问题. 就应用程序而言, 在进程中有一个非阻塞写入. 它不知道数据是否在套接字缓冲区或Coyote缓冲器中缓冲.
     */
    private void checkRegisterForWrite() {
        ob.checkRegisterForWrite();
    }


    @Override
    public void close() throws IOException {
        ob.close();
    }

    @Override
    public boolean isReady() {
        return ob.isReady();
    }


    @Override
    public void setWriteListener(WriteListener listener) {
        ob.setWriteListener(listener);
    }
}

