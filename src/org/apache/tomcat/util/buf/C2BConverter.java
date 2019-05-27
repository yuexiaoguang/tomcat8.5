package org.apache.tomcat.util.buf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/**
 * 基于NIO的字符编码器.
 */
public final class C2BConverter {

    private final CharsetEncoder encoder;
    private ByteBuffer bb = null;
    private CharBuffer cb = null;

    /**
     * 剩余缓冲区, 用于多字符字符.
     */
    private final CharBuffer leftovers;

    public C2BConverter(Charset charset) {
        encoder = charset.newEncoder();
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE)
                .onMalformedInput(CodingErrorAction.REPLACE);
        char[] left = new char[4];
        leftovers = CharBuffer.wrap(left);
    }

    /**
     * 重置编码器状态.
     */
    public void recycle() {
        encoder.reset();
        leftovers.position(0);
    }

    public boolean isUndeflow() {
        return (leftovers.position() > 0);
    }

    /**
     * 将给定的字符转换为字节.
     *
     * @param cc 字符输入
     * @param bc 字节输出
     * 
     * @throws IOException 发生编码错误
     */
    public void convert(CharChunk cc, ByteChunk bc) throws IOException {
        if ((bb == null) || (bb.array() != bc.getBuffer())) {
            // 如果有任何改变，创建一个新的字节缓冲
            bb = ByteBuffer.wrap(bc.getBuffer(), bc.getEnd(), bc.getBuffer().length - bc.getEnd());
        } else {
            // 初始化字节缓冲区
            bb.limit(bc.getBuffer().length);
            bb.position(bc.getEnd());
        }
        if ((cb == null) || (cb.array() != cc.getBuffer())) {
            // 如果有任何改变，创建一个新的char缓冲区
            cb = CharBuffer.wrap(cc.getBuffer(), cc.getStart(), cc.getLength());
        } else {
            // 初始化字符缓冲区
            cb.limit(cc.getEnd());
            cb.position(cc.getStart());
        }
        CoderResult result = null;
        // 如果存在剩余，则解析剩余的
        if (leftovers.position() > 0) {
            int pos = bb.position();
            // 循环, 直到编码一个字符或存在编码器错误
            do {
                leftovers.put((char) cc.substract());
                leftovers.flip();
                result = encoder.encode(leftovers, bb, false);
                leftovers.position(leftovers.limit());
                leftovers.limit(leftovers.array().length);
            } while (result.isUnderflow() && (bb.position() == pos));
            if (result.isError() || result.isMalformed()) {
                result.throwException();
            }
            cb.position(cc.getStart());
            leftovers.position(0);
        }
        // 进行解码并将结果输入到字节块和char块中
        result = encoder.encode(cb, bb, false);
        if (result.isError() || result.isMalformed()) {
            result.throwException();
        } else if (result.isOverflow()) {
            // 将当前位置传播到字节块和char块
            bc.setEnd(bb.position());
            cc.setOffset(cb.position());
        } else if (result.isUnderflow()) {
            // 将当前位置传播到字节块和char块
            bc.setEnd(bb.position());
            cc.setOffset(cb.position());
            // Put leftovers in the leftovers char buffer
            if (cc.getLength() > 0) {
                leftovers.limit(leftovers.array().length);
                leftovers.position(cc.getLength());
                cc.substract(leftovers.array(), 0, cc.getLength());
            }
        }
    }

    /**
     * 将给定的字符转换为字节.
     *
     * @param cc 字符输入
     * @param bc 字节输出
     * @throws IOException 发生编码错误
     */
    public void convert(CharBuffer cc, ByteBuffer bc) throws IOException {
        if ((bb == null) || (bb.array() != bc.array())) {
            // 如果有任何改变，创建一个新的字节缓冲
            bb = ByteBuffer.wrap(bc.array(), bc.limit(), bc.capacity() - bc.limit());
        } else {
            // 初始化字节缓冲区
            bb.limit(bc.capacity());
            bb.position(bc.limit());
        }
        if ((cb == null) || (cb.array() != cc.array())) {
            // 如果有任何改变，创建一个新的char缓冲区
            cb = CharBuffer.wrap(cc.array(), cc.arrayOffset() + cc.position(), cc.remaining());
        } else {
            // 初始化char缓冲区
            cb.limit(cc.limit());
            cb.position(cc.position());
        }
        CoderResult result = null;
        // 如果存在剩余，则解析剩余的
        if (leftovers.position() > 0) {
            int pos = bb.position();
            // 循环, 直到编码一个字符或存在编码器错误
            do {
                leftovers.put(cc.get());
                leftovers.flip();
                result = encoder.encode(leftovers, bb, false);
                leftovers.position(leftovers.limit());
                leftovers.limit(leftovers.array().length);
            } while (result.isUnderflow() && (bb.position() == pos));
            if (result.isError() || result.isMalformed()) {
                result.throwException();
            }
            cb.position(cc.position());
            leftovers.position(0);
        }
        // 进行解码并将结果输入到字节块和char块中
        result = encoder.encode(cb, bb, false);
        if (result.isError() || result.isMalformed()) {
            result.throwException();
        } else if (result.isOverflow()) {
            // 将当前位置传播到字节块和char块
            bc.limit(bb.position());
            cc.position(cb.position());
        } else if (result.isUnderflow()) {
            // 将当前位置传播到字节块和char块
            bc.limit(bb.position());
            cc.position(cb.position());
            // Put leftovers in the leftovers char buffer
            if (cc.remaining() > 0) {
                leftovers.limit(leftovers.array().length);
                leftovers.position(cc.remaining());
                cc.get(leftovers.array(), 0, cc.remaining());
            }
        }
    }

    public Charset getCharset() {
        return encoder.charset();
    }
}
