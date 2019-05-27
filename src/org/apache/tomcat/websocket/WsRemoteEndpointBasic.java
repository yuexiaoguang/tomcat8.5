package org.apache.tomcat.websocket;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;

import javax.websocket.EncodeException;
import javax.websocket.RemoteEndpoint;

public class WsRemoteEndpointBasic extends WsRemoteEndpointBase
        implements RemoteEndpoint.Basic {

    WsRemoteEndpointBasic(WsRemoteEndpointImplBase base) {
        super(base);
    }


    @Override
    public void sendText(String text) throws IOException {
        base.sendString(text);
    }


    @Override
    public void sendBinary(ByteBuffer data) throws IOException {
        base.sendBytes(data);
    }


    @Override
    public void sendText(String fragment, boolean isLast) throws IOException {
        base.sendPartialString(fragment, isLast);
    }


    @Override
    public void sendBinary(ByteBuffer partialByte, boolean isLast)
            throws IOException {
        base.sendPartialBytes(partialByte, isLast);
    }


    @Override
    public OutputStream getSendStream() throws IOException {
        return base.getSendStream();
    }


    @Override
    public Writer getSendWriter() throws IOException {
        return base.getSendWriter();
    }


    @Override
    public void sendObject(Object o) throws IOException, EncodeException {
        base.sendObject(o);
    }
}
