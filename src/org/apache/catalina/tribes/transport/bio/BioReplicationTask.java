package org.apache.catalina.tribes.transport.bio;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.io.BufferPool;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.ListenCallback;
import org.apache.catalina.tribes.io.ObjectReader;
import org.apache.catalina.tribes.transport.AbstractRxTask;
import org.apache.catalina.tribes.transport.Constants;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 一个工作者线程类, 可以排干channel并返回输入.
 * 每个实例都由拥有线程池对象的引用构造而成. 当启动的时候, 线程永远循环等待被唤醒, 来服务SelectionKey对象关联的channel.
 * 工作者通过调用它的serviceChannel()方法被安排任务. serviceChannel() 方法保存线程对象中的 key 引用, 然后调用 notify() 来唤醒它.
 * 当channel 已经被排干, 工作者线程返回它自身到它的父级池中.
 */
public class BioReplicationTask extends AbstractRxTask {

    private static final Log log = LogFactory.getLog(BioReplicationTask.class);

    protected static final StringManager sm = StringManager.getManager(BioReplicationTask.class);

    protected Socket socket;
    protected ObjectReader reader;

    public BioReplicationTask (ListenCallback callback) {
        super(callback);
    }

    // 永远循环等待工作
    @Override
    public synchronized void run()
    {
        if ( socket == null ) return;
        try {
            drainSocket();
        } catch ( Exception x ) {
            log.error(sm.getString("bioReplicationTask.unable.service"), x);
        }finally {
            try {
                socket.close();
            }catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("bioReplicationTask.socket.closeFailed"), e);
                }
            }
            try {
                reader.close();
            }catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("bioReplicationTask.reader.closeFailed"), e);
                }
            }
            reader = null;
            socket = null;
        }
        // done, ready for more, return to pool
        if ( getTaskPool() != null ) getTaskPool().returnWorker (this);
    }


    public synchronized void serviceSocket(Socket socket, ObjectReader reader) {
        this.socket = socket;
        this.reader = reader;
    }

    protected void execute(ObjectReader reader) throws Exception{
        int pkgcnt = reader.count();

        if ( pkgcnt > 0 ) {
            ChannelMessage[] msgs = reader.execute();
            for ( int i=0; i<msgs.length; i++ ) {
                /**
                 * 发送ack, 如果您希望在完成请求之前将请求恢复到远程服务器.
                 * 这被认为是异步请求
                 */
                if (ChannelData.sendAckAsync(msgs[i].getOptions())) sendAck(Constants.ACK_COMMAND);
                try {
                    //处理消息
                    getCallback().messageDataReceived(msgs[i]);
                    /**
                     * 发送ack, 如果您希望在发送ack到远程服务器之前, 完成这个服务器上的请求
                     * 这被认为是同步请求
                     */
                    if (ChannelData.sendAckSync(msgs[i].getOptions())) sendAck(Constants.ACK_COMMAND);
                }catch  ( Exception x ) {
                    if (ChannelData.sendAckSync(msgs[i].getOptions())) sendAck(Constants.FAIL_ACK_COMMAND);
                    log.error(sm.getString("bioReplicationTask.messageDataReceived.error"),x);
                }
                if ( getUseBufferPool() ) {
                    BufferPool.getBufferPool().returnBuffer(msgs[i].getMessage());
                    msgs[i].setMessage(null);
                }
            }
        }
    }

    /**
     * 排干给定key关联的channel.
     * 此方法假定在调用之前已修改了key，以关闭OP_READ 感兴趣的选择. 当此方法完成时，它重新启用OP_READ, 并调用选择器上的 wakeup(),
     * 所以选择器将继续监视这个 channel.
     * @throws Exception IO 异常或执行异常
     */
    protected void drainSocket() throws Exception {
        InputStream in = socket.getInputStream();
        // 循环，直到数据可用, channel 是非阻塞的
        byte[] buf = new byte[1024];
        int length = in.read(buf);
        while ( length >= 0 ) {
            int count = reader.append(buf,0,length,true);
            if ( count > 0 ) execute(reader);
            length = in.read(buf);
        }
    }


    /**
     * 发送应答确认 (6,2,3)
     * @param command 要写入的命令
     */
    protected void sendAck(byte[] command) {
        try {
            OutputStream out = socket.getOutputStream();
            out.write(command);
            out.flush();
            if (log.isTraceEnabled()) {
                log.trace("ACK sent to " + socket.getPort());
            }
        } catch ( java.io.IOException x ) {
            log.warn(sm.getString("bioReplicationTask.unable.sendAck", x.getMessage()));
        }
    }

    @Override
    public void close() {
        setDoRun(false);
        try {
            socket.close();
        }catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("bioReplicationTask.socket.closeFailed"), e);
            }
        }
        try {
            reader.close();
        }catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("bioReplicationTask.reader.closeFailed"), e);
            }
        }
        reader = null;
        socket = null;
        super.close();
    }
}
