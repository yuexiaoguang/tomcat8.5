package org.apache.catalina.tribes;

/**
 * <p>Title: RemoteProcessException</p>
 *
 * <p>Description: 发送者抛出的消息, 当 USE_SYNC_ACK 接收到一个 FAIL_ACK_COMMAND.<br>
 * 意味着接收到的远程的消息处理失败. 这个消息将被嵌入 ChannelException.FaultyMember
 * </p>
 */
public class RemoteProcessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RemoteProcessException() {
        super();
    }

    public RemoteProcessException(String message) {
        super(message);
    }

    public RemoteProcessException(String message, Throwable cause) {
        super(message, cause);
    }

    public RemoteProcessException(Throwable cause) {
        super(cause);
    }

}