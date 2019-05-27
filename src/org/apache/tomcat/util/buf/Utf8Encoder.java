package org.apache.tomcat.util.buf;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

/**
 * 使用UTF-8将字符编码为字节. 从Apache Harmony中提取并应用了一些小错误修复.
 */
public class Utf8Encoder extends CharsetEncoder {

    public Utf8Encoder() {
        super(StandardCharsets.UTF_8, 1.1f, 4.0f);
    }

    @Override
    protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out) {
        if (in.hasArray() && out.hasArray()) {
            return encodeHasArray(in, out);
        }
        return encodeNotHasArray(in, out);
    }

    private CoderResult encodeHasArray(CharBuffer in, ByteBuffer out) {
        int outRemaining = out.remaining();
        int pos = in.position();
        int limit = in.limit();
        byte[] bArr;
        char[] cArr;
        int x = pos;
        bArr = out.array();
        cArr = in.array();
        int outPos = out.position();
        int rem = in.remaining();
        for (x = pos; x < pos + rem; x++) {
            int jchar = (cArr[x] & 0xFFFF);

            if (jchar <= 0x7F) {
                if (outRemaining < 1) {
                    in.position(x);
                    out.position(outPos);
                    return CoderResult.OVERFLOW;
                }
                bArr[outPos++] = (byte) (jchar & 0xFF);
                outRemaining--;
            } else if (jchar <= 0x7FF) {

                if (outRemaining < 2) {
                    in.position(x);
                    out.position(outPos);
                    return CoderResult.OVERFLOW;
                }
                bArr[outPos++] = (byte) (0xC0 + ((jchar >> 6) & 0x1F));
                bArr[outPos++] = (byte) (0x80 + (jchar & 0x3F));
                outRemaining -= 2;

            } else if (jchar >= 0xD800 && jchar <= 0xDFFF) {

                // in必须有一个字节.
                if (limit <= x + 1) {
                    in.position(x);
                    out.position(outPos);
                    return CoderResult.UNDERFLOW;
                }

                if (outRemaining < 4) {
                    in.position(x);
                    out.position(outPos);
                    return CoderResult.OVERFLOW;
                }

                // The surrogate pair starts with a low-surrogate.
                if (jchar >= 0xDC00) {
                    in.position(x);
                    out.position(outPos);
                    return CoderResult.malformedForLength(1);
                }

                int jchar2 = cArr[x + 1] & 0xFFFF;

                // The surrogate pair ends with a high-surrogate.
                if (jchar2 < 0xDC00) {
                    in.position(x);
                    out.position(outPos);
                    return CoderResult.malformedForLength(1);
                }

                // Note, Unicode标量值n定义如下:
                // n = (jchar-0xD800)*0x400+(jchar2-0xDC00)+0x10000
                // Where jchar is a high-surrogate, jchar2 is a low-surrogate.
                int n = (jchar << 10) + jchar2 + 0xFCA02400;

                bArr[outPos++] = (byte) (0xF0 + ((n >> 18) & 0x07));
                bArr[outPos++] = (byte) (0x80 + ((n >> 12) & 0x3F));
                bArr[outPos++] = (byte) (0x80 + ((n >> 6) & 0x3F));
                bArr[outPos++] = (byte) (0x80 + (n & 0x3F));
                outRemaining -= 4;
                x++;

            } else {

                if (outRemaining < 3) {
                    in.position(x);
                    out.position(outPos);
                    return CoderResult.OVERFLOW;
                }
                bArr[outPos++] = (byte) (0xE0 + ((jchar >> 12) & 0x0F));
                bArr[outPos++] = (byte) (0x80 + ((jchar >> 6) & 0x3F));
                bArr[outPos++] = (byte) (0x80 + (jchar & 0x3F));
                outRemaining -= 3;
            }
            if (outRemaining == 0) {
                in.position(x + 1);
                out.position(outPos);
                // 如果输入和输出都用尽了, 返回 UNDERFLOW
                if (x + 1 == limit) {
                    return CoderResult.UNDERFLOW;
                } else {
                    return CoderResult.OVERFLOW;
                }
            }

        }
        if (rem != 0) {
            in.position(x);
            out.position(outPos);
        }
        return CoderResult.UNDERFLOW;
    }

    private CoderResult encodeNotHasArray(CharBuffer in, ByteBuffer out) {
        int outRemaining = out.remaining();
        int pos = in.position();
        int limit = in.limit();
        try {
            while (pos < limit) {
                if (outRemaining == 0) {
                    return CoderResult.OVERFLOW;
                }

                int jchar = (in.get() & 0xFFFF);

                if (jchar <= 0x7F) {

                    if (outRemaining < 1) {
                        return CoderResult.OVERFLOW;
                    }
                    out.put((byte) jchar);
                    outRemaining--;

                } else if (jchar <= 0x7FF) {

                    if (outRemaining < 2) {
                        return CoderResult.OVERFLOW;
                    }
                    out.put((byte) (0xC0 + ((jchar >> 6) & 0x1F)));
                    out.put((byte) (0x80 + (jchar & 0x3F)));
                    outRemaining -= 2;

                } else if (jchar >= 0xD800 && jchar <= 0xDFFF) {

                    // in必须有一个字节.
                    if (limit <= pos + 1) {
                        return CoderResult.UNDERFLOW;
                    }

                    if (outRemaining < 4) {
                        return CoderResult.OVERFLOW;
                    }

                    // The surrogate pair starts with a low-surrogate.
                    if (jchar >= 0xDC00) {
                        return CoderResult.malformedForLength(1);
                    }

                    int jchar2 = (in.get() & 0xFFFF);

                    // The surrogate pair ends with a high-surrogate.
                    if (jchar2 < 0xDC00) {
                        return CoderResult.malformedForLength(1);
                    }

                    // Note, Unicode标量值n定义如下:
                    // n = (jchar-0xD800)*0x400+(jchar2-0xDC00)+0x10000
                    // Where jchar is a high-surrogate, jchar2 is a low-surrogate.
                    int n = (jchar << 10) + jchar2 + 0xFCA02400;

                    out.put((byte) (0xF0 + ((n >> 18) & 0x07)));
                    out.put((byte) (0x80 + ((n >> 12) & 0x3F)));
                    out.put((byte) (0x80 + ((n >> 6) & 0x3F)));
                    out.put((byte) (0x80 + (n & 0x3F)));
                    outRemaining -= 4;
                    pos++;

                } else {

                    if (outRemaining < 3) {
                        return CoderResult.OVERFLOW;
                    }
                    out.put((byte) (0xE0 + ((jchar >> 12) & 0x0F)));
                    out.put((byte) (0x80 + ((jchar >> 6) & 0x3F)));
                    out.put((byte) (0x80 + (jchar & 0x3F)));
                    outRemaining -= 3;
                }
                pos++;
            }
        } finally {
            in.position(pos);
        }
        return CoderResult.UNDERFLOW;
    }
}