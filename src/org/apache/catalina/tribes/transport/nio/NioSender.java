package org.apache.catalina.tribes.transport.nio;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

import org.apache.catalina.tribes.RemoteProcessException;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.transport.AbstractSender;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 该类不是线程安全的
 *
 * 这是状态机, 要处理的状态是:
 * - NOT_CONNECTED -&gt; connect() -&gt; CONNECTED
 * - CONNECTED -&gt; setMessage() -&gt; READY TO WRITE
 * - READY_TO_WRITE -&gt; write() -&gt; READY TO WRITE | READY TO READ
 * - READY_TO_READ -&gt; read() -&gt; READY_TO_READ | TRANSFER_COMPLETE
 * - TRANSFER_COMPLETE -&gt; CONNECTED
 */
public class NioSender extends AbstractSender {

    private static final Log log = LogFactory.getLog(NioSender.class);
    protected static final StringManager sm = StringManager.getManager(NioSender.class);


    protected Selector selector;
    protected SocketChannel socketChannel = null;
    protected DatagramChannel dataChannel = null;

    /*
     * STATE VARIABLES *
     */
    protected ByteBuffer readbuf = null;
    protected ByteBuffer writebuf = null;
    protected volatile byte[] current = null;
    protected final XByteBuffer ackbuf = new XByteBuffer(128,true);
    protected int remaining = 0;
    protected boolean complete;

    protected boolean connecting = false;

    public NioSender() {
        super();
    }

    /**
     * 发送数据的状态机.
     * 
     * @param key 要使用的key
     * @param waitForAck 等待 ack
     * @return <code>true</code>如果处理成功
     * @throws IOException 发生IO错误
     */
    public boolean process(SelectionKey key, boolean waitForAck) throws IOException {
        int ops = key.readyOps();
        key.interestOps(key.interestOps() & ~ops);
        // 如果已经断开连接
        if ((!isConnected()) && (!connecting)) throw new IOException(sm.getString("nioSender.sender.disconnected"));
        if ( !key.isValid() ) throw new IOException(sm.getString("nioSender.key.inValid"));
        if ( key.isConnectable() ) {
            if ( socketChannel.finishConnect() ) {
                completeConnect();
                if ( current != null ) key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                return false;
            } else  {
                // 等待连接结束
                key.interestOps(key.interestOps() | SelectionKey.OP_CONNECT);
                return false;
            }
        } else if ( key.isWritable() ) {
            boolean writecomplete = write();
            if ( writecomplete ) {
                //已经完成, 是否读取 ack?
                if ( waitForAck ) {
                    //注册来读取 ack
                    key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                } else {
                    //如果不读取ack, setMessage 将注册我们，为了下一个写入
                    //做一个检查, 我们没有办法验证一个socket是否断开连接, 因为没有注册 OP_READ on waitForAck=false
                    read();//这导致了开销
                    setRequestCount(getRequestCount()+1);
                    return true;
                }
            } else {
                //未完成, 再写一些
                key.interestOps(key.interestOps()|SelectionKey.OP_WRITE);
            }
        } else if ( key.isReadable() ) {
            boolean readcomplete = read();
            if ( readcomplete ) {
                setRequestCount(getRequestCount()+1);
                return true;
            } else {
                key.interestOps(key.interestOps() | SelectionKey.OP_READ);
            }
        } else {
            //未知状态, 永远不应该发生
            log.warn(sm.getString("nioSender.unknown.state", Integer.toString(ops)));
            throw new IOException(sm.getString("nioSender.unknown.state", Integer.toString(ops)));
        }
        return false;
    }

    private void configureSocket() throws IOException {
        if (socketChannel!=null) {
            socketChannel.configureBlocking(false);
            socketChannel.socket().setSendBufferSize(getTxBufSize());
            socketChannel.socket().setReceiveBufferSize(getRxBufSize());
            socketChannel.socket().setSoTimeout((int)getTimeout());
            socketChannel.socket().setSoLinger(getSoLingerOn(),getSoLingerOn()?getSoLingerTime():0);
            socketChannel.socket().setTcpNoDelay(getTcpNoDelay());
            socketChannel.socket().setKeepAlive(getSoKeepAlive());
            socketChannel.socket().setReuseAddress(getSoReuseAddress());
            socketChannel.socket().setOOBInline(getOoBInline());
            socketChannel.socket().setSoLinger(getSoLingerOn(),getSoLingerTime());
            socketChannel.socket().setTrafficClass(getSoTrafficClass());
        } else if (dataChannel!=null) {
            dataChannel.configureBlocking(false);
            dataChannel.socket().setSendBufferSize(getUdpTxBufSize());
            dataChannel.socket().setReceiveBufferSize(getUdpRxBufSize());
            dataChannel.socket().setSoTimeout((int)getTimeout());
            dataChannel.socket().setReuseAddress(getSoReuseAddress());
            dataChannel.socket().setTrafficClass(getSoTrafficClass());
        }
    }

    private void completeConnect() {
        //连接, 注册我们自己来写入
        setConnected(true);
        connecting = false;
        setRequestCount(0);
        setConnectTime(System.currentTimeMillis());
    }



    protected boolean read() throws IOException {
        //如果没有消息, 直接返回
        if ( current == null ) return true;
        int read = isUdpBased()?dataChannel.read(readbuf) : socketChannel.read(readbuf);
        //end of stream
        if ( read == -1 ) throw new IOException(sm.getString("nioSender.unable.receive.ack"));
        //no data read
        else if ( read == 0 ) return false;
        readbuf.flip();
        ackbuf.append(readbuf,read);
        readbuf.clear();
        if (ackbuf.doesPackageExist() ) {
            byte[] ackcmd = ackbuf.extractDataPackage(true).getBytes();
            boolean ack = Arrays.equals(ackcmd,org.apache.catalina.tribes.transport.Constants.ACK_DATA);
            boolean fack = Arrays.equals(ackcmd,org.apache.catalina.tribes.transport.Constants.FAIL_ACK_DATA);
            if ( fack && getThrowOnFailedAck() ) throw new RemoteProcessException(sm.getString("nioSender.receive.failedAck"));
            return ack || fack;
        } else {
            return false;
        }
    }


    protected boolean write() throws IOException {
        if ( (!isConnected()) || (this.socketChannel==null && this.dataChannel==null)) {
            throw new IOException(sm.getString("nioSender.not.connected"));
        }
        if ( current != null ) {
            if ( remaining > 0 ) {
                //已经完全写入, 或者正在开始一个新的包
                //防止缓冲区重写
                int byteswritten = isUdpBased()?dataChannel.write(writebuf) : socketChannel.write(writebuf);
                if (byteswritten == -1 ) throw new EOFException();
                remaining -= byteswritten;
                //如果整个消息是从缓冲区写入的
                //重置位置计数器
                if ( remaining < 0 ) {
                    remaining = 0;
                }
            }
            return (remaining==0);
        }
        //没有消息发送, 可以认为这是完整的
        return true;
    }

    /**
     * connect - 在操作中阻塞
     *
     * @throws IOException
     * TODO Implement this org.apache.catalina.tribes.transport.IDataSender method
     */
    @Override
    public synchronized void connect() throws IOException {
        if ( connecting || isConnected()) return;
        connecting = true;
        if ( isConnected() ) throw new IOException(sm.getString("nioSender.already.connected"));
        if ( readbuf == null ) {
            readbuf = getReadBuffer();
        } else {
            readbuf.clear();
        }
        if ( writebuf == null ) {
            writebuf = getWriteBuffer();
        } else {
            writebuf.clear();
        }

        if (isUdpBased()) {
            InetSocketAddress daddr = new InetSocketAddress(getAddress(),getUdpPort());
            if ( dataChannel != null ) throw new IOException(sm.getString("nioSender.datagram.already.established"));
            dataChannel = DatagramChannel.open();
            configureSocket();
            dataChannel.connect(daddr);
            completeConnect();
            dataChannel.register(getSelector(),SelectionKey.OP_WRITE, this);

        } else {
            InetSocketAddress addr = new InetSocketAddress(getAddress(),getPort());
            if ( socketChannel != null ) throw new IOException(sm.getString("nioSender.socketChannel.already.established"));
            socketChannel = SocketChannel.open();
            configureSocket();
            if ( socketChannel.connect(addr) ) {
                completeConnect();
                socketChannel.register(getSelector(), SelectionKey.OP_WRITE, this);
            } else {
                socketChannel.register(getSelector(), SelectionKey.OP_CONNECT, this);
            }
        }
    }


    /**
     * disconnect
     *
     * TODO Implement this org.apache.catalina.tribes.transport.IDataSender method
     */
    @Override
    public void disconnect() {
        try {
            connecting = false;
            setConnected(false);
            if (socketChannel != null) {
                try {
                    try {
                        socketChannel.socket().close();
                    } catch (Exception x) {
                        // Ignore
                    }
                    //error free close, all the way
                    //try {socket.shutdownOutput();}catch ( Exception x){}
                    //try {socket.shutdownInput();}catch ( Exception x){}
                    //try {socket.close();}catch ( Exception x){}
                    try {
                        socketChannel.close();
                    } catch (Exception x) {
                        // Ignore
                    }
                } finally {
                    socketChannel = null;
                }
            }
            if (dataChannel != null) {
                try {
                    try {
                        dataChannel.socket().close();
                    } catch (Exception x) {
                        // Ignore
                    }
                    //error free close, all the way
                    //try {socket.shutdownOutput();}catch ( Exception x){}
                    //try {socket.shutdownInput();}catch ( Exception x){}
                    //try {socket.close();}catch ( Exception x){}
                    try {
                        dataChannel.close();
                    } catch (Exception x) {
                        // Ignore
                    }
                } finally {
                    dataChannel = null;
                }
            }
        } catch ( Exception x ) {
            log.error(sm.getString("nioSender.unable.disconnect", x.getMessage()));
            if ( log.isDebugEnabled() ) log.debug(sm.getString("nioSender.unable.disconnect", x.getMessage()),x);
        }
    }

    public void reset() {
        if ( isConnected() && readbuf == null) {
            readbuf = getReadBuffer();
        }
        if ( readbuf != null ) readbuf.clear();
        if ( writebuf != null ) writebuf.clear();
        current = null;
        ackbuf.clear();
        remaining = 0;
        complete = false;
        setAttempt(0);
        setUdpBased(false);
    }

    private ByteBuffer getReadBuffer() {
        return getBuffer(getRxBufSize());
    }

    private ByteBuffer getWriteBuffer() {
        return getBuffer(getTxBufSize());
    }

    private ByteBuffer getBuffer(int size) {
        return (getDirectBuffer()?ByteBuffer.allocateDirect(size):ByteBuffer.allocate(size));
    }

    /**
    * sendMessage
    *
    * @param data ChannelMessage
    * @throws IOException
    * TODO Implement this org.apache.catalina.tribes.transport.IDataSender method
    */
    public void setMessage(byte[] data) throws IOException {
        setMessage(data,0,data.length);
    }

    public void setMessage(byte[] data,int offset, int length) throws IOException {
        if (data != null) {
            synchronized (this) {
                current = data;
                remaining = length;
                ackbuf.clear();
                if (writebuf != null) {
                    writebuf.clear();
                } else {
                    writebuf = getBuffer(length);
                }
                if (writebuf.capacity() < length) {
                    writebuf = getBuffer(length);
                }

                // TODO use ByteBuffer.wrap to avoid copying the data.
                writebuf.put(data,offset,length);
                writebuf.flip();
                if (isConnected()) {
                    if (isUdpBased())
                        dataChannel.register(getSelector(), SelectionKey.OP_WRITE, this);
                    else
                        socketChannel.register(getSelector(), SelectionKey.OP_WRITE, this);
                }
            }
        }
    }

    public byte[] getMessage() {
        return current;
    }


    public boolean isComplete() {
        return complete;
    }

    public Selector getSelector() {
        return selector;
    }

    public void setSelector(Selector selector) {
        this.selector = selector;
    }


    public void setComplete(boolean complete) {
        this.complete = complete;
    }
}
