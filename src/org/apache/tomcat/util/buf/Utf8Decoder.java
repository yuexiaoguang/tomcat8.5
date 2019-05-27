package org.apache.tomcat.util.buf;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

/**
 * 将字节解码为UTF-8. 从Apache Harmony中提取并修改为根据RFC3629拒绝从U+D800到U + DFFF的代码点.
 * 标准Java解码器不会拒绝这些. 它也被修改为拒绝大于U+10FFFF的代码点，标准Java解码器拒绝这些代码点, 但是和谐的代码点却没有.
 */
public class Utf8Decoder extends CharsetDecoder {

    // 下表包含有关UTF-8字符集的信息以及第1个字节与序列长度的对应关系
    // For information please visit http://www.ietf.org/rfc/rfc3629.txt
    //
    // Please note, o means 0, actually.
    // -------------------------------------------------------------------
    // 0 1 2 3 Value
    // -------------------------------------------------------------------
    // oxxxxxxx                            00000000 00000000 0xxxxxxx
    // 11oyyyyy 1oxxxxxx                   00000000 00000yyy yyxxxxxx
    // 111ozzzz 1oyyyyyy 1oxxxxxx          00000000 zzzzyyyy yyxxxxxx
    // 1111ouuu 1ouuzzzz 1oyyyyyy 1oxxxxxx 000uuuuu zzzzyyyy yyxxxxxx
    private static final int remainingBytes[] = {
            // 1owwwwww
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            // 11oyyyyy
            -1, -1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            // 111ozzzz
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            // 1111ouuu
            3, 3, 3, 3, 3, -1, -1, -1,
            // > 11110111
            -1, -1, -1, -1, -1, -1, -1, -1};
    private static final int remainingNumbers[] = {0, // 0 1 2 3
            4224, // (01o00000b << 6)+(1o000000b)
            401536, // (011o0000b << 12)+(1o000000b << 6)+(1o000000b)
            29892736 // (0111o000b << 18)+(1o000000b << 12)+(1o000000b <<
                     // 6)+(1o000000b)
    };
    private static final int lowerEncodingLimit[] = {-1, 0x80, 0x800, 0x10000};


    public Utf8Decoder() {
        super(StandardCharsets.UTF_8, 1.0f, 1.0f);
    }


    @Override
    protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
        if (in.hasArray() && out.hasArray()) {
            return decodeHasArray(in, out);
        }
        return decodeNotHasArray(in, out);
    }


    private CoderResult decodeNotHasArray(ByteBuffer in, CharBuffer out) {
        int outRemaining = out.remaining();
        int pos = in.position();
        int limit = in.limit();
        try {
            while (pos < limit) {
                if (outRemaining == 0) {
                    return CoderResult.OVERFLOW;
                }
                int jchar = in.get();
                if (jchar < 0) {
                    jchar = jchar & 0x7F;
                    int tail = remainingBytes[jchar];
                    if (tail == -1) {
                        return CoderResult.malformedForLength(1);
                    }
                    if (limit - pos < 1 + tail) {
                        // 这里没有对无效序列进行早期测试，因为在下一个字节上偷看更难
                        return CoderResult.UNDERFLOW;
                    }
                    int nextByte;
                    for (int i = 0; i < tail; i++) {
                        nextByte = in.get() & 0xFF;
                        if ((nextByte & 0xC0) != 0x80) {
                            return CoderResult.malformedForLength(1 + i);
                        }
                        jchar = (jchar << 6) + nextByte;
                    }
                    jchar -= remainingNumbers[tail];
                    if (jchar < lowerEncodingLimit[tail]) {
                        // 本来应该用更少的八位字节编码
                        return CoderResult.malformedForLength(1);
                    }
                    pos += tail;
                }
                // Apache Tomcat添加了测试
                if (jchar >= 0xD800 && jchar <= 0xDFFF) {
                    return CoderResult.unmappableForLength(3);
                }
                // Apache Tomcat添加了测试
                if (jchar > 0x10FFFF) {
                    return CoderResult.unmappableForLength(4);
                }
                if (jchar <= 0xffff) {
                    out.put((char) jchar);
                    outRemaining--;
                } else {
                    if (outRemaining < 2) {
                        return CoderResult.OVERFLOW;
                    }
                    out.put((char) ((jchar >> 0xA) + 0xD7C0));
                    out.put((char) ((jchar & 0x3FF) + 0xDC00));
                    outRemaining -= 2;
                }
                pos++;
            }
            return CoderResult.UNDERFLOW;
        } finally {
            in.position(pos);
        }
    }


    private CoderResult decodeHasArray(ByteBuffer in, CharBuffer out) {
        int outRemaining = out.remaining();
        int pos = in.position();
        int limit = in.limit();
        final byte[] bArr = in.array();
        final char[] cArr = out.array();
        final int inIndexLimit = limit + in.arrayOffset();
        int inIndex = pos + in.arrayOffset();
        int outIndex = out.position() + out.arrayOffset();
        // 如果有人会改变过程中的限制, 他会面临后果
        for (; inIndex < inIndexLimit && outRemaining > 0; inIndex++) {
            int jchar = bArr[inIndex];
            if (jchar < 0) {
                jchar = jchar & 0x7F;
                // 如果第一个字节无效, 结尾将设置为 -1
                int tail = remainingBytes[jchar];
                if (tail == -1) {
                    in.position(inIndex - in.arrayOffset());
                    out.position(outIndex - out.arrayOffset());
                    return CoderResult.malformedForLength(1);
                }
                // 额外检查以尽快检测无效序列
                // Checks derived from Unicode 6.2, Chapter 3, Table 3-7
                // Check 2nd byte
                int tailAvailable = inIndexLimit - inIndex - 1;
                if (tailAvailable > 0) {
                    // First byte C2..DF, second byte 80..BF
                    if (jchar > 0x41 && jchar < 0x60 &&
                            (bArr[inIndex + 1] & 0xC0) != 0x80) {
                        in.position(inIndex - in.arrayOffset());
                        out.position(outIndex - out.arrayOffset());
                        return CoderResult.malformedForLength(1);
                    }
                    // First byte E0, second byte A0..BF
                    if (jchar == 0x60 && (bArr[inIndex + 1] & 0xE0) != 0xA0) {
                        in.position(inIndex - in.arrayOffset());
                        out.position(outIndex - out.arrayOffset());
                        return CoderResult.malformedForLength(1);
                    }
                    // First byte E1..EC, second byte 80..BF
                    if (jchar > 0x60 && jchar < 0x6D &&
                            (bArr[inIndex + 1] & 0xC0) != 0x80) {
                        in.position(inIndex - in.arrayOffset());
                        out.position(outIndex - out.arrayOffset());
                        return CoderResult.malformedForLength(1);
                    }
                    // First byte ED, second byte 80..9F
                    if (jchar == 0x6D && (bArr[inIndex + 1] & 0xE0) != 0x80) {
                        in.position(inIndex - in.arrayOffset());
                        out.position(outIndex - out.arrayOffset());
                        return CoderResult.malformedForLength(1);
                    }
                    // First byte EE..EF, second byte 80..BF
                    if (jchar > 0x6D && jchar < 0x70 &&
                            (bArr[inIndex + 1] & 0xC0) != 0x80) {
                        in.position(inIndex - in.arrayOffset());
                        out.position(outIndex - out.arrayOffset());
                        return CoderResult.malformedForLength(1);
                    }
                    // First byte F0, second byte 90..BF
                    if (jchar == 0x70 &&
                            ((bArr[inIndex + 1] & 0xFF) < 0x90 ||
                            (bArr[inIndex + 1] & 0xFF) > 0xBF)) {
                        in.position(inIndex - in.arrayOffset());
                        out.position(outIndex - out.arrayOffset());
                        return CoderResult.malformedForLength(1);
                    }
                    // First byte F1..F3, second byte 80..BF
                    if (jchar > 0x70 && jchar < 0x74 &&
                            (bArr[inIndex + 1] & 0xC0) != 0x80) {
                        in.position(inIndex - in.arrayOffset());
                        out.position(outIndex - out.arrayOffset());
                        return CoderResult.malformedForLength(1);
                    }
                    // First byte F4, second byte 80..8F
                    if (jchar == 0x74 &&
                            (bArr[inIndex + 1] & 0xF0) != 0x80) {
                        in.position(inIndex - in.arrayOffset());
                        out.position(outIndex - out.arrayOffset());
                        return CoderResult.malformedForLength(1);
                    }
                }
                // 如果存在和预期，检查第三个字节
                if (tailAvailable > 1 && tail > 1) {
                    if ((bArr[inIndex + 2] & 0xC0) != 0x80) {
                        in.position(inIndex - in.arrayOffset());
                        out.position(outIndex - out.arrayOffset());
                        return CoderResult.malformedForLength(2);
                    }
                }
                // 检查第四个字节（如果存在和预期）
                if (tailAvailable > 2 && tail > 2) {
                    if ((bArr[inIndex + 3] & 0xC0) != 0x80) {
                        in.position(inIndex - in.arrayOffset());
                        out.position(outIndex - out.arrayOffset());
                        return CoderResult.malformedForLength(3);
                    }
                }
                if (tailAvailable < tail) {
                    break;
                }
                for (int i = 0; i < tail; i++) {
                    int nextByte = bArr[inIndex + i + 1] & 0xFF;
                    if ((nextByte & 0xC0) != 0x80) {
                        in.position(inIndex - in.arrayOffset());
                        out.position(outIndex - out.arrayOffset());
                        return CoderResult.malformedForLength(1 + i);
                    }
                    jchar = (jchar << 6) + nextByte;
                }
                jchar -= remainingNumbers[tail];
                if (jchar < lowerEncodingLimit[tail]) {
                    // 应该用更少的八位字节编码
                    in.position(inIndex - in.arrayOffset());
                    out.position(outIndex - out.arrayOffset());
                    return CoderResult.malformedForLength(1);
                }
                inIndex += tail;
            }
            // Apache Tomcat添加了测试
            if (jchar >= 0xD800 && jchar <= 0xDFFF) {
                return CoderResult.unmappableForLength(3);
            }
            // Apache Tomcat添加了测试
            if (jchar > 0x10FFFF) {
                return CoderResult.unmappableForLength(4);
            }
            if (jchar <= 0xffff) {
                cArr[outIndex++] = (char) jchar;
                outRemaining--;
            } else {
                if (outRemaining < 2) {
                    return CoderResult.OVERFLOW;
                }
                cArr[outIndex++] = (char) ((jchar >> 0xA) + 0xD7C0);
                cArr[outIndex++] = (char) ((jchar & 0x3FF) + 0xDC00);
                outRemaining -= 2;
            }
        }
        in.position(inIndex - in.arrayOffset());
        out.position(outIndex - out.arrayOffset());
        return (outRemaining == 0 && inIndex < inIndexLimit) ?
                CoderResult.OVERFLOW :
                CoderResult.UNDERFLOW;
    }
}
