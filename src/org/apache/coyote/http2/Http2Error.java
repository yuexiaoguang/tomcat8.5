package org.apache.coyote.http2;

public enum Http2Error {

    NO_ERROR            (0x00),
    PROTOCOL_ERROR      (0x01),
    INTERNAL_ERROR      (0x02),
    FLOW_CONTROL_ERROR  (0x03),
    SETTINGS_TIMEOUT    (0x04),
    STREAM_CLOSED       (0x05),
    FRAME_SIZE_ERROR    (0x06),
    REFUSED_STREAM      (0x07),
    CANCEL              (0x08),
    COMPRESSION_ERROR   (0x09),
    CONNECT_ERROR       (0x0a),
    ENHANCE_YOUR_CALM   (0x0b),
    INADEQUATE_SECURITY (0x0c),
    HTTP_1_1_REQUIRED   (0x0d);

    private final long code;

    private Http2Error(long code) {
        this.code = code;
    }


    public long getCode() {
        return code;
    }


    public byte[] getCodeBytes() {
        byte[] codeByte = new byte[4];
        ByteUtil.setFourBytes(codeByte, 0, code);
        return codeByte;
    }
}
