package org.apache.tomcat.util.http.fileupload.util.mime;

import java.io.IOException;
import java.io.OutputStream;

final class QuotedPrintableDecoder {

    /**
     * 从ascii hex转换的2个字节值中的第一个创建高半字节所需的移位值.
     */
    private static final int UPPER_NIBBLE_SHIFT = Byte.SIZE / 2;

    private QuotedPrintableDecoder() {
        // do nothing
    }

    /**
     * 解码编码的字节数据，将其写入给定的输出流.
     *
     * @param data   要解码的字节数据数组.
     * @param out    用于返回解码数据的输出流.
     *
     * @return 生成的字节数.
     * @throws IOException 如果在解码或写入流期间出现问题
     */
    public static int decode(byte[] data, OutputStream out) throws IOException {
        int off = 0;
        int length = data.length;
        int endOffset = off + length;
        int bytesWritten = 0;

        while (off < endOffset) {
            byte ch = data[off++];

            // 空格字符在编码时被翻译为'_', 所以需要将它们翻译回来.
            if (ch == '_') {
                out.write(' ');
            } else if (ch == '=') {
                // 找到了一个编码字符.  将3 char序列减少为1.
                // 但首先，请确保有两个字符可供使用.
                if (off + 1 >= endOffset) {
                    throw new IOException("Invalid quoted printable encoding; truncated escape sequence");
                }

                byte b1 = data[off++];
                byte b2 = data[off++];

                // 找到了一个编码的回车.  下一个char需要是换行符
                if (b1 == '\r') {
                    if (b2 != '\n') {
                        throw new IOException("Invalid quoted printable encoding; CR must be followed by LF");
                    }
                    // 这是编码插入的软换行符.  只是把它扔掉解码.
                } else {
                    // 要转换回单个字节的十六进制对.
                    int c1 = hexToBinary(b1);
                    int c2 = hexToBinary(b2);
                    out.write((c1 << UPPER_NIBBLE_SHIFT) | c2);
                    // 3 bytes in, one byte out
                    bytesWritten++;
                }
            } else {
                // simple character, just write it out.
                out.write(ch);
                bytesWritten++;
            }
        }

        return bytesWritten;
    }

    /**
     * 将十六进制数字转换为它所代表的二进制值.
     *
     * @param b 要转换的ascii十六进制字节 (0-0, A-F, a-f)
     * 
     * @return 十六进制字节的int值, 0-15
     * @throws IOException 如果字节不是有效的十六进制数字.
     */
    private static int hexToBinary(final byte b) throws IOException {
        // CHECKSTYLE IGNORE MagicNumber FOR NEXT 1 LINE
        final int i = Character.digit((char) b, 16);
        if (i == -1) {
            throw new IOException("Invalid quoted printable encoding: not a valid hex digit: " + b);
        }
        return i;
    }

}
