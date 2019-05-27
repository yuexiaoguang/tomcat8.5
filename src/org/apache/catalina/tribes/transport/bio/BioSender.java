package org.apache.catalina.tribes.transport.bio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;

import org.apache.catalina.tribes.RemoteProcessException;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.transport.AbstractSender;
import org.apache.catalina.tribes.transport.Constants;
import org.apache.catalina.tribes.transport.SenderState;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 仅用一个socket发送集群消息. 支持Ack 和保持 Alive 处理
 */
public class BioSender extends AbstractSender {

    private static final Log log = LogFactory.getLog(BioSender.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(BioSender.class);

    // ----------------------------------------------------- Instance Variables

    /**
     * 当前发送者 socket
     */
    private Socket socket = null;
    private OutputStream soOut = null;
    private InputStream soIn = null;

    protected final XByteBuffer ackbuf =
            new XByteBuffer(Constants.ACK_COMMAND.length, true);


    // ------------------------------------------------------------- Constructor

    public BioSender()  {
        // NO-OP
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 连接其他集群成员接收者
     */
    @Override
    public void connect() throws IOException {
        openSocket();
   }


    /**
     * 取消连接并关闭 socket
     */
    @Override
    public  void disconnect() {
        boolean connect = isConnected();
        closeSocket();
        if (connect) {
            if (log.isDebugEnabled())
                log.debug(sm.getString("bioSender.disconnect", getAddress().getHostAddress(), Integer.valueOf(getPort()), Long.valueOf(0)));
        }

    }

    /**
     * 发送消息.
     * @param data 要发送的数据
     * @param waitForAck 等待一个 ack
     * @throws IOException 发送消息时发生了IO错误
     */
    public  void sendMessage(byte[] data, boolean waitForAck) throws IOException {
        IOException exception = null;
        setAttempt(0);
        try {
             // 先尝试现有连接
             pushMessage(data,false,waitForAck);
        } catch (IOException x) {
            SenderState.getSenderState(getDestination()).setSuspect();
            exception = x;
            if (log.isTraceEnabled()) log.trace(sm.getString("bioSender.send.again", getAddress().getHostAddress(),Integer.valueOf(getPort())),x);
            while ( getAttempt()<getMaxRetryAttempts() ) {
                try {
                    setAttempt(getAttempt()+1);
                    // 再尝试新连接
                    pushMessage(data, true,waitForAck);
                    exception = null;
                } catch (IOException xx) {
                    exception = xx;
                    closeSocket();
                }
            }
        } finally {
            setRequestCount(getRequestCount()+1);
            keepalive();
            if ( exception != null ) throw exception;
        }
    }


    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("DataSender[(");
        buf.append(super.toString()).append(")");
        buf.append(getAddress()).append(":").append(getPort()).append("]");
        return buf.toString();
    }

    // --------------------------------------------------------- Protected Methods

    /**
     * 打开真实的 socket 并设置超时, 当启用 waitForAck时, 直接打开并返回 socket.
     * 
     * @throws IOException 打开socket错误
     */
    protected void openSocket() throws IOException {
       if(isConnected()) return ;
       try {
           socket = new Socket();
           InetSocketAddress sockaddr = new InetSocketAddress(getAddress(), getPort());
           socket.connect(sockaddr,(int)getTimeout());
           socket.setSendBufferSize(getTxBufSize());
           socket.setReceiveBufferSize(getRxBufSize());
           socket.setSoTimeout( (int) getTimeout());
           socket.setTcpNoDelay(getTcpNoDelay());
           socket.setKeepAlive(getSoKeepAlive());
           socket.setReuseAddress(getSoReuseAddress());
           socket.setOOBInline(getOoBInline());
           socket.setSoLinger(getSoLingerOn(),getSoLingerTime());
           socket.setTrafficClass(getSoTrafficClass());
           setConnected(true);
           soOut = socket.getOutputStream();
           soIn  = socket.getInputStream();
           setRequestCount(0);
           setConnectTime(System.currentTimeMillis());
           if (log.isDebugEnabled())
               log.debug(sm.getString("bioSender.openSocket", getAddress().getHostAddress(), Integer.valueOf(getPort()), Long.valueOf(0)));
      } catch (IOException ex1) {
          SenderState.getSenderState(getDestination()).setSuspect();
          if (log.isDebugEnabled())
              log.debug(sm.getString("bioSender.openSocket.failure",getAddress().getHostAddress(), Integer.valueOf(getPort()), Long.valueOf(0)), ex1);
          throw (ex1);
        }

     }

    /**
     * 关闭 socket.
     */
    protected void closeSocket() {
        if(isConnected()) {
             if (socket != null) {
                try {
                    socket.close();
                } catch (IOException x) {
                    // Ignore
                } finally {
                    socket = null;
                    soOut = null;
                    soIn = null;
                }
            }
            setRequestCount(0);
            setConnected(false);
            if (log.isDebugEnabled())
                log.debug(sm.getString("bioSender.closeSocket",getAddress().getHostAddress(), Integer.valueOf(getPort()), Long.valueOf(0)));
       }
    }

    /**
     * 每次只使用一个socket 推送消息.
     * 需要等待 ack, 并自动重试, 当写入消息失败. 发送错误关闭并重新打开套接字后.
     *
     * 成功发送更新统计数据后
     *
     * WARNING: 子类必须注意同一时间只能有一个线程调用这个 pushMessage!!!
     *
     * @param data Data to send
     * @param reconnect Do a reconnect (close socket then reopen)
     * @param waitForAck Wait for an acknowledgement
     * @throws IOException IO error writing data
     * @since 5.5.10
     */
    protected void pushMessage(byte[] data, boolean reconnect, boolean waitForAck) throws IOException {
        keepalive();
        if ( reconnect ) closeSocket();
        if (!isConnected()) openSocket();
        soOut.write(data);
        soOut.flush();
        if (waitForAck) waitForAck();
        SenderState.getSenderState(getDestination()).setReady();

    }

    /**
     * 等待来自其他服务器的确认.
     * FIXME Please, 不只等待三个字符, 更好地控制等待ACK消息是正确的.
     * @throws IOException 发生IO错误
     */
    protected void waitForAck() throws java.io.IOException {
        try {
            boolean ackReceived = false;
            boolean failAckReceived = false;
            ackbuf.clear();
            int bytesRead = 0;
            int i = soIn.read();
            while ((i != -1) && (bytesRead < Constants.ACK_COMMAND.length)) {
                bytesRead++;
                byte d = (byte)i;
                ackbuf.append(d);
                if (ackbuf.doesPackageExist() ) {
                    byte[] ackcmd = ackbuf.extractDataPackage(true).getBytes();
                    ackReceived = Arrays.equals(ackcmd,org.apache.catalina.tribes.transport.Constants.ACK_DATA);
                    failAckReceived = Arrays.equals(ackcmd,org.apache.catalina.tribes.transport.Constants.FAIL_ACK_DATA);
                    ackReceived = ackReceived || failAckReceived;
                    break;
                }
                i = soIn.read();
            }
            if (!ackReceived) {
                if (i == -1) throw new IOException(sm.getString("bioSender.ack.eof",getAddress(), Integer.valueOf(socket.getLocalPort())));
                else throw new IOException(sm.getString("bioSender.ack.wrong",getAddress(), Integer.valueOf(socket.getLocalPort())));
            } else if ( failAckReceived && getThrowOnFailedAck()) {
                throw new RemoteProcessException(sm.getString("bioSender.fail.AckReceived"));
            }
        } catch (IOException x) {
            String errmsg = sm.getString("bioSender.ack.missing", getAddress(), Integer.valueOf(socket.getLocalPort()), Long.valueOf(getTimeout()));
            if ( SenderState.getSenderState(getDestination()).isReady() ) {
                SenderState.getSenderState(getDestination()).setSuspect();
                if ( log.isWarnEnabled() ) log.warn(errmsg, x);
            } else {
                if ( log.isDebugEnabled() )log.debug(errmsg, x);
            }
            throw x;
        } finally {
            ackbuf.clear();
        }
    }
}
