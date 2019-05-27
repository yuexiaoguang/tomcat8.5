package org.apache.tomcat.util.net;

import java.nio.ByteBuffer;

/**
 * 回调接口能够在发生缓冲区溢出异常时, 扩展缓冲区或替换缓冲区
 */
public interface ApplicationBufferHandler {

    public void setByteBuffer(ByteBuffer buffer);

    public ByteBuffer getByteBuffer();

    public void expand(int size);

}
