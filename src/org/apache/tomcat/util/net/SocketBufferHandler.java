package org.apache.tomcat.util.net;

import java.nio.ByteBuffer;

import org.apache.tomcat.util.buf.ByteBufferUtils;

public class SocketBufferHandler {

    private volatile boolean readBufferConfiguredForWrite = true;
    private volatile ByteBuffer readBuffer;

    private volatile boolean writeBufferConfiguredForWrite = true;
    private volatile ByteBuffer writeBuffer;

    private final boolean direct;

    public SocketBufferHandler(int readBufferSize, int writeBufferSize,
            boolean direct) {
        this.direct = direct;
        if (direct) {
            readBuffer = ByteBuffer.allocateDirect(readBufferSize);
            writeBuffer = ByteBuffer.allocateDirect(writeBufferSize);
        } else {
            readBuffer = ByteBuffer.allocate(readBufferSize);
            writeBuffer = ByteBuffer.allocate(writeBufferSize);
        }
    }


    public void configureReadBufferForWrite() {
        setReadBufferConfiguredForWrite(true);
    }


    public void configureReadBufferForRead() {
        setReadBufferConfiguredForWrite(false);
    }


    private void setReadBufferConfiguredForWrite(boolean readBufferConFiguredForWrite) {
        // 如果缓冲区已处于正确状态，则为NO-OP
        if (this.readBufferConfiguredForWrite != readBufferConFiguredForWrite) {
            if (readBufferConFiguredForWrite) {
                // 切换到写入模式
                int remaining = readBuffer.remaining();
                if (remaining == 0) {
                    readBuffer.clear();
                } else {
                    readBuffer.compact();
                }
            } else {
                // 切换到读取模式
                readBuffer.flip();
            }
            this.readBufferConfiguredForWrite = readBufferConFiguredForWrite;
        }
    }


    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }


    public boolean isReadBufferEmpty() {
        if (readBufferConfiguredForWrite) {
            return readBuffer.position() == 0;
        } else {
            return readBuffer.remaining() == 0;
        }
    }


    public void configureWriteBufferForWrite() {
        setWriteBufferConfiguredForWrite(true);
    }


    public void configureWriteBufferForRead() {
        setWriteBufferConfiguredForWrite(false);
    }


    private void setWriteBufferConfiguredForWrite(boolean writeBufferConfiguredForWrite) {
        // 如果缓冲区已处于正确状态，则为NO-OP
        if (this.writeBufferConfiguredForWrite != writeBufferConfiguredForWrite) {
            if (writeBufferConfiguredForWrite) {
                // 切换到写入模式
                int remaining = writeBuffer.remaining();
                if (remaining == 0) {
                    writeBuffer.clear();
                } else {
                    writeBuffer.compact();
                    writeBuffer.position(remaining);
                    writeBuffer.limit(writeBuffer.capacity());
                }
            } else {
                // 切换到读取模式
                writeBuffer.flip();
            }
            this.writeBufferConfiguredForWrite = writeBufferConfiguredForWrite;
        }
    }


    public boolean isWriteBufferWritable() {
        if (writeBufferConfiguredForWrite) {
            return writeBuffer.hasRemaining();
        } else {
            return writeBuffer.remaining() == 0;
        }
    }


    public ByteBuffer getWriteBuffer() {
        return writeBuffer;
    }


    public boolean isWriteBufferEmpty() {
        if (writeBufferConfiguredForWrite) {
            return writeBuffer.position() == 0;
        } else {
            return writeBuffer.remaining() == 0;
        }
    }


    public void reset() {
        readBuffer.clear();
        readBufferConfiguredForWrite = true;
        writeBuffer.clear();
        writeBufferConfiguredForWrite = true;
    }


    public void expand(int newSize) {
        configureReadBufferForWrite();
        readBuffer = ByteBufferUtils.expand(readBuffer, newSize);
        configureWriteBufferForWrite();
        writeBuffer = ByteBufferUtils.expand(writeBuffer, newSize);
    }

    public void free() {
        if (direct) {
            ByteBufferUtils.cleanDirectBuffer(readBuffer);
            ByteBufferUtils.cleanDirectBuffer(writeBuffer);
        }
    }
}
