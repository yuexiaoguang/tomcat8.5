package org.apache.tomcat.websocket;

import javax.websocket.Decoder;

public class DecoderEntry {

    private final Class<?> clazz;
    private final Class<? extends Decoder> decoderClazz;

    public DecoderEntry(Class<?> clazz,
            Class<? extends Decoder> decoderClazz) {
        this.clazz = clazz;
        this.decoderClazz = decoderClazz;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public Class<? extends Decoder> getDecoderClazz() {
        return decoderClazz;
    }
}