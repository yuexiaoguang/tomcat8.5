package org.apache.tomcat.websocket;

import java.nio.ByteBuffer;

import javax.websocket.PongMessage;

public class WsPongMessage implements PongMessage {

    private final ByteBuffer applicationData;


    public WsPongMessage(ByteBuffer applicationData) {
        byte[] dst = new byte[applicationData.limit()];
        applicationData.get(dst);
        this.applicationData = ByteBuffer.wrap(dst);
    }


    @Override
    public ByteBuffer getApplicationData() {
        return applicationData;
    }
}
