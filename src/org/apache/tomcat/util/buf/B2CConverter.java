package org.apache.tomcat.util.buf;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.tomcat.util.res.StringManager;

/**
 * 基于NIO的字符解码器.
 */
public class B2CConverter {

    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    private static final Map<String, Charset> encodingToCharsetCache =
            new HashMap<>();

    // 受保护，因此单元测试可以使用它
    protected static final int LEFTOVER_SIZE = 9;

    static {
        for (Charset charset: Charset.availableCharsets().values()) {
            encodingToCharsetCache.put(
                    charset.name().toLowerCase(Locale.ENGLISH), charset);
            for (String alias : charset.aliases()) {
                encodingToCharsetCache.put(
                        alias.toLowerCase(Locale.ENGLISH), charset);
            }
        }
    }


    /**
     * 获取给定编码的Charset
     *
     * @param enc 所需字符集的编码名称
     *
     * @return 对应于请求的编码的Charset
     *
     * @throws UnsupportedEncodingException 如果请求的Charset不可用
     */
    public static Charset getCharset(String enc)
            throws UnsupportedEncodingException {

        // 编码名称应全部为ASCII
        String lowerCaseEnc = enc.toLowerCase(Locale.ENGLISH);

        return getCharsetLower(lowerCaseEnc);
    }


    /**
     * 仅在已知编码名称为小写时才使用.
     * 
     * @param lowerCaseEnc 小写所需字符集的编码名称
     *
     * @return 对应于请求的编码的Charset
     *
     * @throws UnsupportedEncodingException 如果请求的Charset不可用
     *
     * @deprecated Will be removed in Tomcat 9.0.x
     */
    @Deprecated
    public static Charset getCharsetLower(String lowerCaseEnc)
            throws UnsupportedEncodingException {

        Charset charset = encodingToCharsetCache.get(lowerCaseEnc);

        if (charset == null) {
            // 预先填充缓存意味着它必须是无效的
            throw new UnsupportedEncodingException(
                    sm.getString("b2cConverter.unknownEncoding", lowerCaseEnc));
        }
        return charset;
    }


    private final CharsetDecoder decoder;
    private ByteBuffer bb = null;
    private CharBuffer cb = null;

    /**
     * 剩余缓冲区用于不完整的字符.
     */
    private final ByteBuffer leftovers;

    public B2CConverter(Charset charset) {
        this(charset, false);
    }

    public B2CConverter(Charset charset, boolean replaceOnError) {
        byte[] left = new byte[LEFTOVER_SIZE];
        leftovers = ByteBuffer.wrap(left);
        CodingErrorAction action;
        if (replaceOnError) {
            action = CodingErrorAction.REPLACE;
        } else {
            action = CodingErrorAction.REPORT;
        }
        // 特例. 使用基于Apache Harmony的UTF-8解码器，因为它
        // - a) 拒绝JVM解码器没有的无效序列
        // - b) 某些无效序列失败得更快
        if (charset.equals(StandardCharsets.UTF_8)) {
            decoder = new Utf8Decoder();
        } else {
            decoder = charset.newDecoder();
        }
        decoder.onMalformedInput(action);
        decoder.onUnmappableCharacter(action);
    }

    /**
     * 重置解码器状态.
     */
    public void recycle() {
        decoder.reset();
        leftovers.position(0);
    }

    /**
     * 将给定的字节转换为字符.
     *
     * @param bc 字节输入
     * @param cc 字符输出
     * @param endOfInput    这是所有可用的数据吗
     *
     * @throws IOException 如果转换无法完成
     */
    public void convert(ByteChunk bc, CharChunk cc, boolean endOfInput)
            throws IOException {
        if ((bb == null) || (bb.array() != bc.getBuffer())) {
            // 如果有任何改变，创建一个新的字节缓冲
            bb = ByteBuffer.wrap(bc.getBuffer(), bc.getStart(), bc.getLength());
        } else {
            // 初始化字节缓冲区
            bb.limit(bc.getEnd());
            bb.position(bc.getStart());
        }
        if ((cb == null) || (cb.array() != cc.getBuffer())) {
            // 如果有任何改变，创建一个新的char缓冲区
            cb = CharBuffer.wrap(cc.getBuffer(), cc.getEnd(),
                    cc.getBuffer().length - cc.getEnd());
        } else {
            // 初始化char缓冲区
            cb.limit(cc.getBuffer().length);
            cb.position(cc.getEnd());
        }
        CoderResult result = null;
        // 如果存在剩余，则解析剩余的
        if (leftovers.position() > 0) {
            int pos = cb.position();
            // 循环，直到一个字符被解码或存在解码器错误
            do {
                leftovers.put(bc.substractB());
                leftovers.flip();
                result = decoder.decode(leftovers, cb, endOfInput);
                leftovers.position(leftovers.limit());
                leftovers.limit(leftovers.array().length);
            } while (result.isUnderflow() && (cb.position() == pos));
            if (result.isError() || result.isMalformed()) {
                result.throwException();
            }
            bb.position(bc.getStart());
            leftovers.position(0);
        }
        // 进行解码并将结果输入到字节块和char块中
        result = decoder.decode(bb, cb, endOfInput);
        if (result.isError() || result.isMalformed()) {
            result.throwException();
        } else if (result.isOverflow()) {
            //将当前位置传播到字节块和char块, 如果继续，char缓冲区将调整大小
            bc.setOffset(bb.position());
            cc.setEnd(cb.position());
        } else if (result.isUnderflow()) {
            // 将当前位置传播到字节块和char块
            bc.setOffset(bb.position());
            cc.setEnd(cb.position());
            // Put leftovers in the leftovers byte buffer
            if (bc.getLength() > 0) {
                leftovers.limit(leftovers.array().length);
                leftovers.position(bc.getLength());
                bc.substract(leftovers.array(), 0, bc.getLength());
            }
        }
    }

    /**
     * 将给定的字节转换为字符.
     *
     * @param bc 字节输入
     * @param cc 字符输出
     * @param ic 字节输入 channel
     * @param endOfInput    这是所有可用的数据吗
     *
     * @throws IOException 如果转换无法完成
     */
    public void convert(ByteBuffer bc, CharBuffer cc, ByteChunk.ByteInputChannel ic, boolean endOfInput)
            throws IOException {
        if ((bb == null) || (bb.array() != bc.array())) {
            // 如果有任何改变，创建一个新的字节缓冲
            bb = ByteBuffer.wrap(bc.array(), bc.arrayOffset() + bc.position(), bc.remaining());
        } else {
            // 初始化字节缓冲区
            bb.limit(bc.limit());
            bb.position(bc.position());
        }
        if ((cb == null) || (cb.array() != cc.array())) {
            // 如果有任何改变，创建一个新的char缓冲区
            cb = CharBuffer.wrap(cc.array(), cc.limit(), cc.capacity() - cc.limit());
        } else {
            // 初始化char缓冲区
            cb.limit(cc.capacity());
            cb.position(cc.limit());
        }
        CoderResult result = null;
        // 如果存在剩余，则解析剩余的
        if (leftovers.position() > 0) {
            int pos = cb.position();
            // 循环，直到一个字符被解码或存在解码器错误
            do {
                byte chr;
                if (bc.remaining() == 0) {
                    int n = ic.realReadBytes();
                    chr = n < 0 ? -1 : bc.get();
                } else {
                    chr = bc.get();
                }
                leftovers.put(chr);
                leftovers.flip();
                result = decoder.decode(leftovers, cb, endOfInput);
                leftovers.position(leftovers.limit());
                leftovers.limit(leftovers.array().length);
            } while (result.isUnderflow() && (cb.position() == pos));
            if (result.isError() || result.isMalformed()) {
                result.throwException();
            }
            bb.position(bc.position());
            leftovers.position(0);
        }
        // 进行解码并将结果输入到字节块和char块中
        result = decoder.decode(bb, cb, endOfInput);
        if (result.isError() || result.isMalformed()) {
            result.throwException();
        } else if (result.isOverflow()) {
            // 将当前位置传播到字节块和char块, 如果继续，char缓冲区将调整大小
            bc.position(bb.position());
            cc.limit(cb.position());
        } else if (result.isUnderflow()) {
            // 将当前位置传播到字节块和char块
            bc.position(bb.position());
            cc.limit(cb.position());
            // Put leftovers in the leftovers byte buffer
            if (bc.remaining() > 0) {
                leftovers.limit(leftovers.array().length);
                leftovers.position(bc.remaining());
                bc.get(leftovers.array(), 0, bc.remaining());
            }
        }
    }


    public Charset getCharset() {
        return decoder.charset();
    }
}
