package org.apache.tomcat.websocket.pojo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.Decoder.Binary;
import javax.websocket.Decoder.BinaryStream;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import org.apache.tomcat.util.res.StringManager;

/**
 * 处理整个消息的ByteBuffer具体实现.
 */
public class PojoMessageHandlerWholeBinary
        extends PojoMessageHandlerWholeBase<ByteBuffer> {

    private static final StringManager sm =
            StringManager.getManager(PojoMessageHandlerWholeBinary.class);

    private final List<Decoder> decoders = new ArrayList<>();

    private final boolean isForInputStream;

    public PojoMessageHandlerWholeBinary(Object pojo, Method method,
            Session session, EndpointConfig config,
            List<Class<? extends Decoder>> decoderClazzes, Object[] params,
            int indexPayload, boolean convert, int indexSession,
            boolean isForInputStream, long maxMessageSize) {
        super(pojo, method, session, params, indexPayload, convert,
                indexSession, maxMessageSize);

        // 更新会话处理的二进制文本大小
        if (maxMessageSize > -1 && maxMessageSize > session.getMaxBinaryMessageBufferSize()) {
            if (maxMessageSize > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(sm.getString(
                        "pojoMessageHandlerWhole.maxBufferSize"));
            }
            session.setMaxBinaryMessageBufferSize((int) maxMessageSize);
        }

        try {
            if (decoderClazzes != null) {
                for (Class<? extends Decoder> decoderClazz : decoderClazzes) {
                    if (Binary.class.isAssignableFrom(decoderClazz)) {
                        Binary<?> decoder = (Binary<?>) decoderClazz.getConstructor().newInstance();
                        decoder.init(config);
                        decoders.add(decoder);
                    } else if (BinaryStream.class.isAssignableFrom(
                            decoderClazz)) {
                        BinaryStream<?> decoder = (BinaryStream<?>)
                                decoderClazz.getConstructor().newInstance();
                        decoder.init(config);
                        decoders.add(decoder);
                    } else {
                        // Text decoder - ignore it
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(e);
        }
        this.isForInputStream = isForInputStream;
    }


    @Override
    protected Object decode(ByteBuffer message) throws DecodeException {
        for (Decoder decoder : decoders) {
            if (decoder instanceof Binary) {
                if (((Binary<?>) decoder).willDecode(message)) {
                    return ((Binary<?>) decoder).decode(message);
                }
            } else {
                byte[] array = new byte[message.limit() - message.position()];
                message.get(array);
                ByteArrayInputStream bais = new ByteArrayInputStream(array);
                try {
                    return ((BinaryStream<?>) decoder).decode(bais);
                } catch (IOException ioe) {
                    throw new DecodeException(message, sm.getString(
                            "pojoMessageHandlerWhole.decodeIoFail"), ioe);
                }
            }
        }
        return null;
    }


    @Override
    protected Object convert(ByteBuffer message) {
        byte[] array = new byte[message.remaining()];
        message.get(array);
        if (isForInputStream) {
            return new ByteArrayInputStream(array);
        } else {
            return array;
        }
    }


    @Override
    protected void onClose() {
        for (Decoder decoder : decoders) {
            decoder.destroy();
        }
    }
}
