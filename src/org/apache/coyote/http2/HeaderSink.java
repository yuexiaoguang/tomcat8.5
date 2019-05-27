package org.apache.coyote.http2;

import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;

/**
 * 用于忽略任何 header. 如果接收到新流的header, 一旦连接关闭的过程已经开始, 使用它.
 */
public class HeaderSink implements HeaderEmitter {

    @Override
    public void emitHeader(String name, String value) {
        // NO-OP
    }

    @Override
    public void validateHeaders() throws StreamException {
        // NO-OP
    }

    @Override
    public void setHeaderException(StreamException streamException) {
        // NO-OP
        // 连接已经关闭，因此不需要处理额外的错误
    }
}
