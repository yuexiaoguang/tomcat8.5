package org.apache.coyote.http2;

import org.apache.tomcat.util.net.SocketEvent;

class StreamRunnable implements Runnable {

    private final StreamProcessor processor;
    private final SocketEvent event;


    public StreamRunnable(StreamProcessor processor, SocketEvent event) {
        this.processor = processor;
        this.event = event;
    }


    @Override
    public void run() {
        processor.process(event);
    }
}
