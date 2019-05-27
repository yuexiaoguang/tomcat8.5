package org.apache.coyote.http2;

import java.util.Map;

/**
 * 表示本地连接设置, i.e. 客户端和服务器通信时使用的配置. 客户端调用setter和设置生效之间会有延迟.
 * 当调用setter 时, 新值被添加到等待的设置的集合中. 一旦接收到 ACK, 新值移动到当前设置. 在等待 ACK 期间, getter将返回当前设置和待设置.
 * 这类不验证传递给setter的值. 如果使用无效的值，客户端将响应 (几乎可以肯定，通过关闭连接), 像HTTP/2规范中定义的那样.
 */
public class ConnectionSettingsLocal extends ConnectionSettingsBase<IllegalArgumentException> {

    private boolean sendInProgress = false;


    public ConnectionSettingsLocal(String connectionId) {
        super(connectionId);
    }


    @Override
    protected synchronized void set(Setting setting, Long value) {
        checkSend();
        if (current.get(setting).longValue() == value.longValue()) {
            pending.remove(setting);
        } else {
            pending.put(setting, value);
        }
    }


    synchronized byte[] getSettingsFrameForPending() {
        checkSend();
        int payloadSize = pending.size() * 6;
        byte[] result = new byte[9 + payloadSize];

        ByteUtil.setThreeBytes(result, 0, payloadSize);
        result[3] = FrameType.SETTINGS.getIdByte();
        // No flags
        // Stream is zero
        // Payload
        int pos = 9;
        for (Map.Entry<Setting,Long> setting : pending.entrySet()) {
            ByteUtil.setTwoBytes(result, pos, setting.getKey().getId());
            pos += 2;
            ByteUtil.setFourBytes(result, pos, setting.getValue().longValue());
            pos += 4;
        }
        sendInProgress = true;
        return result;
    }


    synchronized boolean ack() {
        if (sendInProgress) {
            sendInProgress = false;
            current.putAll(pending);
            pending.clear();
            return true;
        } else {
            return false;
        }
    }


    private void checkSend() {
        if (sendInProgress) {
            // 编码错误. 不需要 i18n
            throw new IllegalStateException();
        }
    }


    @Override
    void throwException(String msg, Http2Error error) throws IllegalArgumentException {
        throw new IllegalArgumentException(msg);
    }
}
