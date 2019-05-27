package org.apache.coyote.http2;

/**
 * 表示远程连接设置: i.e. 服务器和客户端通信时, 必须使用的设置.
 */
public class ConnectionSettingsRemote extends ConnectionSettingsBase<ConnectionException> {

    public ConnectionSettingsRemote(String connectionId) {
        super(connectionId);
    }


    @Override
    void throwException(String msg, Http2Error error) throws ConnectionException {
        throw new ConnectionException(msg, error);
    }
}
