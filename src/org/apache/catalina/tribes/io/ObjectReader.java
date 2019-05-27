package org.apache.catalina.tribes.io;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 用于连接java.nio TCP 消息. 这个对象将信息字节保存进<code>XByteBuffer</code>, 直到接收到一个完整的包.
 */
public class ObjectReader {

    private static final Log log = LogFactory.getLog(ObjectReader.class);
    protected static final StringManager sm = StringManager.getManager(ObjectReader.class);

    private XByteBuffer buffer;

    protected long lastAccess = System.currentTimeMillis();

    protected boolean accessed = false;
    private volatile boolean cancelled;

    public ObjectReader(int packetSize) {
        this.buffer = new XByteBuffer(packetSize, true);
    }
    /**
     * 为一个TCP NIO socket channel创建一个<code>ObjectReader</code>
     * @param channel - the channel to be read.
     */
    public ObjectReader(SocketChannel channel) {
        this(channel.socket());
    }

    /**
     * 为一个TCP socket创建一个<code>ObjectReader</code>
     * @param socket Socket
     */
    public ObjectReader(Socket socket) {
        try{
            this.buffer = new XByteBuffer(socket.getReceiveBufferSize(), true);
        }catch ( IOException x ) {
            //unable to get buffer size
            log.warn(sm.getString("objectReader.retrieveFailed.socketReceiverBufferSize"));
            this.buffer = new XByteBuffer(43800,true);
        }
    }

    public synchronized void access() {
        this.accessed = true;
        this.lastAccess = System.currentTimeMillis();
    }

    public synchronized void finish() {
        this.accessed = false;
        this.lastAccess = System.currentTimeMillis();
    }

    public synchronized boolean isAccessed() {
        return this.accessed;
    }

    /**
     * 追加新的字节到缓存区.
     * 
     * @param data 新的传输缓冲区
     * @param len 缓冲区长度
     * @param count 是否返回计数
     * 
     * @return 发送给回调的消息的数量 (or -1 if count == false)
     */
    public int append(ByteBuffer data, int len, boolean count) {
       buffer.append(data,len);
       int pkgCnt = -1;
       if ( count ) pkgCnt = buffer.countPackages();
       return pkgCnt;
   }

     public int append(byte[] data,int off,int len, boolean count) {
        buffer.append(data,off,len);
        int pkgCnt = -1;
        if ( count ) pkgCnt = buffer.countPackages();
        return pkgCnt;
    }

    /**
     * 将缓冲区发送到集群监听器 (callback). 消息是否完成?
     *
     * @return 接收的包或消息的数量
     */
    public ChannelMessage[] execute() {
        int pkgCnt = buffer.countPackages();
        ChannelMessage[] result = new ChannelMessage[pkgCnt];
        for (int i=0; i<pkgCnt; i++)  {
            ChannelMessage data = buffer.extractPackage(true);
            result[i] = data;
        }
        return result;
    }

    public int bufferSize() {
        return buffer.getLength();
    }


    public boolean hasPackage() {
        return buffer.countPackages(true)>0;
    }
    /**
     * 返回读取器读取的包数
     */
    public int count() {
        return buffer.countPackages();
    }

    public void close() {
        this.buffer = null;
    }

    public synchronized long getLastAccess() {
        return lastAccess;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public synchronized void setLastAccess(long lastAccess) {
        this.lastAccess = lastAccess;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

}
