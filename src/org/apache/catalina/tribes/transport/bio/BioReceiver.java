package org.apache.catalina.tribes.transport.bio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.catalina.tribes.io.ObjectReader;
import org.apache.catalina.tribes.transport.AbstractRxTask;
import org.apache.catalina.tribes.transport.ReceiverBase;
import org.apache.catalina.tribes.transport.RxTaskPool;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class BioReceiver extends ReceiverBase implements Runnable {

    private static final Log log = LogFactory.getLog(BioReceiver.class);

    protected static final StringManager sm = StringManager.getManager(BioReceiver.class);

    protected ServerSocket serverSocket;

    public BioReceiver() {
        // NO-OP
    }

    @Override
    public void start() throws IOException {
        super.start();
        try {
            setPool(new RxTaskPool(getMaxThreads(),getMinThreads(),this));
        } catch (Exception x) {
            log.fatal(sm.getString("bioReceiver.threadpool.fail"), x);
            if ( x instanceof IOException ) throw (IOException)x;
            else throw new IOException(x.getMessage());
        }
        try {
            getBind();
            bind();
            String channelName = "";
            if (getChannel().getName() != null) channelName = "[" + getChannel().getName() + "]";
            Thread t = new Thread(this, "BioReceiver" + channelName);
            t.setDaemon(true);
            t.start();
        } catch (Exception x) {
            log.fatal(sm.getString("bioReceiver.start.fail"), x);
            if ( x instanceof IOException ) throw (IOException)x;
            else throw new IOException(x.getMessage());
        }
    }

    @Override
    public AbstractRxTask createRxTask() {
        return getReplicationThread();
    }

    protected BioReplicationTask getReplicationThread() {
        BioReplicationTask result = new BioReplicationTask(this);
        result.setOptions(getWorkerThreadOptions());
        result.setUseBufferPool(this.getUseBufferPool());
        return result;
    }

    @Override
    public void stop() {
        setListen(false);
        try {
            this.serverSocket.close();
        } catch (Exception x) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("bioReceiver.socket.closeFailed"), x);
            }
        }
        super.stop();
    }


    protected void bind() throws IOException {
        // 分配未绑定服务器socket channel
        serverSocket = new ServerSocket();
        // 设置服务器channel 将监听的端口号
        //serverSocket.bind(new InetSocketAddress(getBind(), getTcpListenPort()));
        bind(serverSocket,getPort(),getAutoBind());
    }


    @Override
    public void run() {
        try {
            listen();
        } catch (Exception x) {
            log.error(sm.getString("bioReceiver.run.fail"), x);
        }
    }

    public void listen() throws Exception {
        if (doListen()) {
            log.warn(sm.getString("bioReceiver.already.started"));
            return;
        }
        setListen(true);

        while ( doListen() ) {
            Socket socket = null;
            if ( getTaskPool().available() < 1 ) {
                if ( log.isWarnEnabled() )
                    log.warn(sm.getString("bioReceiver.threads.busy"));
            }
            BioReplicationTask task = (BioReplicationTask)getTaskPool().getRxTask();
            if ( task == null ) continue; //should never happen
            try {
                socket = serverSocket.accept();
            }catch ( Exception x ) {
                if ( doListen() ) throw x;
            }
            if ( !doListen() ) {
                task.setDoRun(false);
                task.serviceSocket(null,null);
                getExecutor().execute(task);
                break; //regular shutdown
            }
            if ( socket == null ) continue;
            socket.setReceiveBufferSize(getRxBufSize());
            socket.setSendBufferSize(getTxBufSize());
            socket.setTcpNoDelay(getTcpNoDelay());
            socket.setKeepAlive(getSoKeepAlive());
            socket.setOOBInline(getOoBInline());
            socket.setReuseAddress(getSoReuseAddress());
            socket.setSoLinger(getSoLingerOn(),getSoLingerTime());
            socket.setSoTimeout(getTimeout());
            ObjectReader reader = new ObjectReader(socket);
            task.serviceSocket(socket,reader);
            getExecutor().execute(task);
        }
    }
}