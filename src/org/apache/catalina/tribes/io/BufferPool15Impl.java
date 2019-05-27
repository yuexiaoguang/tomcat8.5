package org.apache.catalina.tribes.io;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

class BufferPool15Impl implements BufferPool.BufferPoolAPI {
    protected int maxSize;
    protected final AtomicInteger size = new AtomicInteger(0);
    protected final ConcurrentLinkedQueue<XByteBuffer> queue =
            new ConcurrentLinkedQueue<>();

    @Override
    public void setMaxSize(int bytes) {
        this.maxSize = bytes;
    }


    @Override
    public XByteBuffer getBuffer(int minSize, boolean discard) {
        XByteBuffer buffer = queue.poll();
        if ( buffer != null ) size.addAndGet(-buffer.getCapacity());
        if ( buffer == null ) buffer = new XByteBuffer(minSize,discard);
        else if ( buffer.getCapacity() <= minSize ) buffer.expand(minSize);
        buffer.setDiscard(discard);
        buffer.reset();
        return buffer;
    }

    @Override
    public void returnBuffer(XByteBuffer buffer) {
        if ( (size.get() + buffer.getCapacity()) <= maxSize ) {
            size.addAndGet(buffer.getCapacity());
            queue.offer(buffer);
        }
    }

    @Override
    public void clear() {
        queue.clear();
        size.set(0);
    }

    public int getMaxSize() {
        return maxSize;
    }

}
