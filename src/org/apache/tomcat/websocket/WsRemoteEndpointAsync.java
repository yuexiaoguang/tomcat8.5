package org.apache.tomcat.websocket;

import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;

public class WsRemoteEndpointAsync extends WsRemoteEndpointBase
        implements RemoteEndpoint.Async {

    WsRemoteEndpointAsync(WsRemoteEndpointImplBase base) {
        super(base);
    }


    @Override
    public long getSendTimeout() {
        return base.getSendTimeout();
    }


    @Override
    public void setSendTimeout(long timeout) {
        base.setSendTimeout(timeout);
    }


    @Override
    public void sendText(String text, SendHandler completion) {
        base.sendStringByCompletion(text, completion);
    }


    @Override
    public Future<Void> sendText(String text) {
        return base.sendStringByFuture(text);
    }


    @Override
    public Future<Void> sendBinary(ByteBuffer data) {
        return base.sendBytesByFuture(data);
    }


    @Override
    public void sendBinary(ByteBuffer data, SendHandler completion) {
        base.sendBytesByCompletion(data, completion);
    }


    @Override
    public Future<Void> sendObject(Object obj) {
        return base.sendObjectByFuture(obj);
    }


    @Override
    public void sendObject(Object obj, SendHandler completion) {
        base.sendObjectByCompletion(obj, completion);
    }
}
