package org.apache.tomcat.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.websocket.SendHandler;
import javax.websocket.SendResult;

public class WsRemoteEndpointImplClient extends WsRemoteEndpointImplBase {

    private final AsyncChannelWrapper channel;

    public WsRemoteEndpointImplClient(AsyncChannelWrapper channel) {
        this.channel = channel;
    }


    @Override
    protected boolean isMasked() {
        return true;
    }


    @Override
    protected void doWrite(SendHandler handler, long blockingWriteTimeoutExpiry,
            ByteBuffer... data) {
        long timeout;
        for (ByteBuffer byteBuffer : data) {
            if (blockingWriteTimeoutExpiry == -1) {
                timeout = getSendTimeout();
                if (timeout < 1) {
                    timeout = Long.MAX_VALUE;
                }
            } else {
                timeout = blockingWriteTimeoutExpiry - System.currentTimeMillis();
                if (timeout < 0) {
                    SendResult sr = new SendResult(new IOException("Blocking write timeout"));
                    handler.onResult(sr);
                }
            }

            try {
                channel.write(byteBuffer).get(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                handler.onResult(new SendResult(e));
                return;
            }
        }
        handler.onResult(SENDRESULT_OK);
    }

    @Override
    protected void doClose() {
        channel.close();
    }
}
