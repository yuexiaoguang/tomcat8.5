package org.apache.tomcat.websocket;

import java.nio.ByteBuffer;

import javax.websocket.SendHandler;

class MessagePart {
    private final boolean fin;
    private final int rsv;
    private final byte opCode;
    private final ByteBuffer payload;
    private final SendHandler intermediateHandler;
    private volatile SendHandler endHandler;
    private final long blockingWriteTimeoutExpiry;

    public MessagePart( boolean fin, int rsv, byte opCode, ByteBuffer payload,
            SendHandler intermediateHandler, SendHandler endHandler,
            long blockingWriteTimeoutExpiry) {
        this.fin = fin;
        this.rsv = rsv;
        this.opCode = opCode;
        this.payload = payload;
        this.intermediateHandler = intermediateHandler;
        this.endHandler = endHandler;
        this.blockingWriteTimeoutExpiry = blockingWriteTimeoutExpiry;
    }


    public boolean isFin() {
        return fin;
    }


    public int getRsv() {
        return rsv;
    }


    public byte getOpCode() {
        return opCode;
    }


    public ByteBuffer getPayload() {
        return payload;
    }


    public SendHandler getIntermediateHandler() {
        return intermediateHandler;
    }


    public SendHandler getEndHandler() {
        return endHandler;
    }

    public void setEndHandler(SendHandler endHandler) {
        this.endHandler = endHandler;
    }

    public long getBlockingWriteTimeoutExpiry() {
        return blockingWriteTimeoutExpiry;
    }
}


