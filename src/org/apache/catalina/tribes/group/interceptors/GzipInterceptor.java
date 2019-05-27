package org.apache.catalina.tribes.group.interceptors;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class GzipInterceptor extends ChannelInterceptorBase {

    private static final Log log = LogFactory.getLog(GzipInterceptor.class);
    protected static final StringManager sm = StringManager.getManager(GzipInterceptor.class);

    public static final int DEFAULT_BUFFER_SIZE = 2048;

    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload) throws ChannelException {
        try {
            byte[] data = compress(msg.getMessage().getBytes());
            msg.getMessage().trim(msg.getMessage().getLength());
            msg.getMessage().append(data,0,data.length);
            super.sendMessage(destination, msg, payload);
        } catch ( IOException x ) {
            log.error(sm.getString("gzipInterceptor.compress.failed"));
            throw new ChannelException(x);
        }
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        try {
            byte[] data = decompress(msg.getMessage().getBytes());
            msg.getMessage().trim(msg.getMessage().getLength());
            msg.getMessage().append(data,0,data.length);
            super.messageReceived(msg);
        } catch ( IOException x ) {
            log.error(sm.getString("gzipInterceptor.decompress.failed"),x);
        }
    }

    public static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        GZIPOutputStream gout = new GZIPOutputStream(bout);
        gout.write(data);
        gout.flush();
        gout.close();
        return bout.toByteArray();
    }

    /**
     * @param data  要解压缩的数据
     * @return      解压缩的数据
     * @throws IOException Compression error
     */
    public static byte[] decompress(byte[] data) throws IOException {
        ByteArrayOutputStream bout =
            new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
        ByteArrayInputStream bin = new ByteArrayInputStream(data);
        GZIPInputStream gin = new GZIPInputStream(bin);
        byte[] tmp = new byte[DEFAULT_BUFFER_SIZE];
        int length = gin.read(tmp);
        while (length > -1) {
            bout.write(tmp, 0, length);
            length = gin.read(tmp);
        }
        return bout.toByteArray();
    }
}
