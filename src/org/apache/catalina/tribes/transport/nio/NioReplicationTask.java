package org.apache.catalina.tribes.transport.nio;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.ChannelReceiver;
import org.apache.catalina.tribes.RemoteProcessException;
import org.apache.catalina.tribes.UniqueId;
import org.apache.catalina.tribes.io.BufferPool;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.ListenCallback;
import org.apache.catalina.tribes.io.ObjectReader;
import org.apache.catalina.tribes.transport.AbstractRxTask;
import org.apache.catalina.tribes.transport.Constants;
import org.apache.catalina.tribes.util.Logs;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 一个工作者线程类, 可以排干channel并返回输入.
 * 每个实例都由拥有线程池对象的引用构造而成. 当启动的时候, 线程永远循环等待被唤醒, 来服务SelectionKey对象关联的channel.
 * 工作者通过调用它的serviceChannel()方法被安排任务. serviceChannel() 方法保存线程对象中的 key 引用, 然后调用 notify() 来唤醒它.
 * 当channel 已经被排干, 工作者线程返回它自身到它的父级池中.
 */
public class NioReplicationTask extends AbstractRxTask {

    private static final Log log = LogFactory.getLog(NioReplicationTask.class);
    protected static final StringManager sm = StringManager.getManager(NioReplicationTask.class);

    private ByteBuffer buffer = null;
    private SelectionKey key;
    private int rxBufSize;
    private final NioReceiver receiver;

    public NioReplicationTask (ListenCallback callback, NioReceiver receiver) {
        super(callback);
        this.receiver = receiver;
    }

    // 永远循环等待工作
    @Override
    public synchronized void run() {
        if ( buffer == null ) {
            int size = getRxBufSize();
            if (key.channel() instanceof DatagramChannel) {
                size = ChannelReceiver.MAX_UDP_SIZE;
            }
            if ( (getOptions() & OPTION_DIRECT_BUFFER) == OPTION_DIRECT_BUFFER) {
                buffer = ByteBuffer.allocateDirect(size);
            } else {
                buffer = ByteBuffer.allocate(size);
            }
        } else {
            buffer.clear();
        }
        if (key == null) {
            return; // just in case
        }
        if ( log.isTraceEnabled() )
            log.trace("Servicing key:"+key);

        try {
            ObjectReader reader = (ObjectReader)key.attachment();
            if ( reader == null ) {
                if ( log.isTraceEnabled() )
                    log.trace("No object reader, cancelling:"+key);
                cancelKey(key);
            } else {
                if ( log.isTraceEnabled() )
                    log.trace("Draining channel:"+key);

                drainChannel(key, reader);
            }
        } catch (Exception e) {
            // 这是正常的, 因为其它上面的 socket在确定的时间之后过期
            if ( e instanceof CancelledKeyException ) {
                // do nothing
            } else if ( e instanceof IOException ) {
                // 除非启用调试，否则不要为IO异常输出堆栈跟踪.
                if (log.isDebugEnabled()) log.debug ("IOException in replication worker, unable to drain channel. Probable cause: Keep alive socket closed["+e.getMessage()+"].", e);
                else log.warn (sm.getString("nioReplicationTask.unable.drainChannel.ioe", e.getMessage()));
            } else if ( log.isErrorEnabled() ) {
                // 这是一个真正的错误, 记录它.
                log.error(sm.getString("nioReplicationTask.exception.drainChannel"),e);
            }
            cancelKey(key);
        }
        key = null;
        // done, ready for more, return to pool
        getTaskPool().returnWorker (this);
    }

    /**
     * 用在提供的SelectionKey对象上的工作者线程启动一个工作单元.
     * 这种方法是同步的, 因为是 run() 方法, 因此在一个给定的时间只能服务一个 key.
     * 在唤醒工作者线程之前, 在返回到主选择循环之前, 这个 key的集被更新来删除 OP_READ. 将导致选择器忽略这个channel的read-readiness, 在工作者线程服务于它期间.
     * @param key 要处理的 key
     */
    public synchronized void serviceChannel (SelectionKey key) {
        if ( log.isTraceEnabled() ) log.trace("About to service key:"+key);
        ObjectReader reader = (ObjectReader)key.attachment();
        if ( reader != null ) reader.setLastAccess(System.currentTimeMillis());
        this.key = key;
        key.interestOps (key.interestOps() & (~SelectionKey.OP_READ));
        key.interestOps (key.interestOps() & (~SelectionKey.OP_WRITE));
    }

    /**
     * 排干给定key关联的channel.
     * 此方法假定在调用之前已修改了key，以关闭OP_READ 感兴趣的选择. 当此方法完成时，它重新启用OP_READ, 并调用选择器上的 wakeup(),
     * 所以选择器将继续监视这个 channel.
     * 
     * @param key 要处理的 key
     * @param reader The reader
     * @throws Exception IO error
     */
    protected void drainChannel (final SelectionKey key, ObjectReader reader) throws Exception {
        reader.access();
        ReadableByteChannel channel = (ReadableByteChannel) key.channel();
        int count=-1;
        buffer.clear();         // make buffer empty
        SocketAddress saddr = null;

        if (channel instanceof SocketChannel) {
            // 循环，直到数据可用, channel 是非阻塞的
            while ((count = channel.read (buffer)) > 0) {
                buffer.flip();      // make buffer readable
                if ( buffer.hasArray() )
                    reader.append(buffer.array(),0,count,false);
                else
                    reader.append(buffer,count,false);
                buffer.clear();     // make buffer empty
                //do we have at least one package?
                if ( reader.hasPackage() ) break;
            }
        } else if (channel instanceof DatagramChannel) {
            DatagramChannel dchannel = (DatagramChannel)channel;
            saddr = dchannel.receive(buffer);
            buffer.flip();      // make buffer readable
            if ( buffer.hasArray() )
                reader.append(buffer.array(),0,buffer.limit()-buffer.position(),false);
            else
                reader.append(buffer,buffer.limit()-buffer.position(),false);
            buffer.clear();     // make buffer empty
            //did we get a package
            count = reader.hasPackage()?1:-1;
        }

        int pkgcnt = reader.count();

        if (count < 0 && pkgcnt == 0 ) {
            //end of stream, and no more packages to process
            remoteEof(key);
            return;
        }

        ChannelMessage[] msgs = pkgcnt == 0? ChannelData.EMPTY_DATA_ARRAY : reader.execute();

        registerForRead(key,reader);//注册用于读取新的数据, 在我们把它送走之前避免死锁

        for ( int i=0; i<msgs.length; i++ ) {
            /**
             * 发送ack, 如果您希望在完成请求之前将请求恢复到远程服务器.
             * 这被认为是异步请求
             */
            if (ChannelData.sendAckAsync(msgs[i].getOptions())) sendAck(key,(WritableByteChannel)channel,Constants.ACK_COMMAND,saddr);
            try {
                if ( Logs.MESSAGES.isTraceEnabled() ) {
                    try {
                        Logs.MESSAGES.trace("NioReplicationThread - Received msg:" + new UniqueId(msgs[i].getUniqueId()) + " at " + new java.sql.Timestamp(System.currentTimeMillis()));
                    }catch ( Throwable t ) {}
                }
                //处理消息
                getCallback().messageDataReceived(msgs[i]);
                /**
                 * 发送ack, 如果您希望在发送ack到远程服务器之前, 完成这个服务器上的请求
                     * 这被认为是同步请求
                 */
                if (ChannelData.sendAckSync(msgs[i].getOptions())) sendAck(key,(WritableByteChannel)channel,Constants.ACK_COMMAND,saddr);
            }catch ( RemoteProcessException e ) {
                if ( log.isDebugEnabled() ) log.error(sm.getString("nioReplicationTask.process.clusterMsg.failed"),e);
                if (ChannelData.sendAckSync(msgs[i].getOptions())) sendAck(key,(WritableByteChannel)channel,Constants.FAIL_ACK_COMMAND,saddr);
            }catch ( Exception e ) {
                log.error(sm.getString("nioReplicationTask.process.clusterMsg.failed"),e);
                if (ChannelData.sendAckSync(msgs[i].getOptions())) sendAck(key,(WritableByteChannel)channel,Constants.FAIL_ACK_COMMAND,saddr);
            }
            if ( getUseBufferPool() ) {
                BufferPool.getBufferPool().returnBuffer(msgs[i].getMessage());
                msgs[i].setMessage(null);
            }
        }

        if (count < 0) {
            remoteEof(key);
            return;
        }
    }

    private void remoteEof(SelectionKey key) {
        // 关闭EOF上的 channel, 使 key 无效
        if ( log.isDebugEnabled() ) log.debug("Channel closed on the remote end, disconnecting");
        cancelKey(key);
    }

    protected void registerForRead(final SelectionKey key, ObjectReader reader) {
        if ( log.isTraceEnabled() )
            log.trace("Adding key for read event:"+key);
        reader.finish();
        //注册 OP_READ
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    if (key.isValid()) {
                        // 循环选择器，使该key再次激活
                        key.selector().wakeup();
                        // 假设是 OP_READ, OP_WRITE
                        int resumeOps = key.interestOps() | SelectionKey.OP_READ;
                        key.interestOps(resumeOps);
                        if ( log.isTraceEnabled() )
                            log.trace("Registering key for read:"+key);
                    }
                } catch (CancelledKeyException ckx ) {
                    NioReceiver.cancelledKey(key);
                    if ( log.isTraceEnabled() )
                        log.trace("CKX Cancelling key:"+key);

                } catch (Exception x) {
                    log.error(sm.getString("nioReplicationTask.error.register.key", key),x);
                }
            }
        };
        receiver.addEvent(r);
    }

    private void cancelKey(final SelectionKey key) {
        if ( log.isTraceEnabled() )
            log.trace("Adding key for cancel event:"+key);

        ObjectReader reader = (ObjectReader)key.attachment();
        if ( reader != null ) {
            reader.setCancelled(true);
            reader.finish();
        }
        Runnable cx = new Runnable() {
            @Override
            public void run() {
                if ( log.isTraceEnabled() )
                    log.trace("Cancelling key:"+key);

                NioReceiver.cancelledKey(key);
            }
        };
        receiver.addEvent(cx);
    }


    /**
     * 发送应答确认 (6,2,3), 发送它进行忙写, ACK 很小, 应该总是去缓存.
     * 
     * @param key 要使用的key
     * @param channel The channel
     * @param command 要写入的命令
     * @param udpaddr 目标地址
     */
    protected void sendAck(SelectionKey key, WritableByteChannel channel, byte[] command, SocketAddress udpaddr) {
        try {

            ByteBuffer buf = ByteBuffer.wrap(command);
            int total = 0;
            if (channel instanceof DatagramChannel) {
                DatagramChannel dchannel = (DatagramChannel)channel;
                //是否在使用一个共享的 channel, 文档中说它是线程安全的
                //TODO check optimization, one channel per thread?
                while ( total < command.length ) {
                    total += dchannel.send(buf, udpaddr);
                }
            } else {
                while ( total < command.length ) {
                    total += channel.write(buf);
                }
            }
            if (log.isTraceEnabled()) {
                log.trace("ACK sent to " +
                        ( (channel instanceof SocketChannel) ?
                          ((SocketChannel)channel).socket().getInetAddress() :
                          ((DatagramChannel)channel).socket().getInetAddress()));
            }
        } catch ( java.io.IOException x ) {
            log.warn(sm.getString("nioReplicationTask.unable.ack", x.getMessage()));
        }
    }

    public void setRxBufSize(int rxBufSize) {
        this.rxBufSize = rxBufSize;
    }

    public int getRxBufSize() {
        return rxBufSize;
    }
}
