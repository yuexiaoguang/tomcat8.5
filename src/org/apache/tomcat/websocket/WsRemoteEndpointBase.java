package org.apache.tomcat.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.websocket.RemoteEndpoint;

public abstract class WsRemoteEndpointBase implements RemoteEndpoint {

    protected final WsRemoteEndpointImplBase base;


    WsRemoteEndpointBase(WsRemoteEndpointImplBase base) {
        this.base = base;
    }


    @Override
    public final void setBatchingAllowed(boolean batchingAllowed) throws IOException {
        base.setBatchingAllowed(batchingAllowed);
    }


    @Override
    public final boolean getBatchingAllowed() {
        return base.getBatchingAllowed();
    }


    @Override
    public final void flushBatch() throws IOException {
        base.flushBatch();
    }


    @Override
    public final void sendPing(ByteBuffer applicationData) throws IOException,
            IllegalArgumentException {
        base.sendPing(applicationData);
    }


    @Override
    public final void sendPong(ByteBuffer applicationData) throws IOException,
            IllegalArgumentException {
        base.sendPong(applicationData);
    }
}
