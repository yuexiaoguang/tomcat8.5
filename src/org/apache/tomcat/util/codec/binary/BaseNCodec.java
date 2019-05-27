package org.apache.tomcat.util.codec.binary;

import org.apache.tomcat.util.buf.HexUtils;

/**
 * Base-N编码器和解码器的抽象超类.
 *
 * <p>
 * 这个类是线程安全的.
 * </p>
 */
@SuppressWarnings("deprecation")
public abstract class BaseNCodec {

    /**
     * 保存线程上下文, 以便类可以是线程安全的.
     *
     * 这个类本身不是线程安全的; 每个线程必须分配自己的副本.
     */
    static class Context {

        /**
         * 占位符, 为了基于逻辑处理的字节.
         * 按位运算存储, 并从此变量中提取编码或解码.
         */
        int ibitWorkArea;

        /**
         * 流缓冲区.
         */
        byte[] buffer;

        /**
         * 应在缓冲区中写入下一个字符的位置.
         */
        int pos;

        /**
         * 应从缓冲区读取下一个字符的位置.
         */
        int readPos;

        /**
         * 表示已达到EOF. 一旦达到EOF, 这个对象变得无用, 必须扔掉.
         */
        boolean eof;

        /**
         * 跟踪已写入当前行的字符数. 仅在编码时使用. 使用它来确保每个编码行永远不会超出lineLength (if lineLength &gt; 0).
         */
        int currentLinePos;

        /**
         * 写入缓冲区, 仅在编码时每3/5次读取, 并且在解码时每4/8次读取后发生. 此变量有助于跟踪.
         */
        int modulus;

        Context() {
        }

        @SuppressWarnings("boxing") // OK to ignore boxing here
        @Override
        public String toString() {
            return String.format("%s[buffer=%s, currentLinePos=%s, eof=%s, " +
                    "ibitWorkArea=%s, modulus=%s, pos=%s, " +
                    "readPos=%s]", this.getClass().getSimpleName(),
                    HexUtils.toHexString(buffer), currentLinePos, eof,
                    ibitWorkArea, modulus, pos, readPos);
        }
    }

    /**
     * EOF
     */
    static final int EOF = -1;

    /**
     *  每个RFC 2045第6.8节的MIME块大小.
     *
     * <p>
     * {@value}字符限制不计算结尾CRLF, 但计算所有其他字符, 包括任何等号.
     * </p>
     */
    public static final int MIME_CHUNK_SIZE = 76;

    /**
     * 每个RFC 1421第4.3.2.4节的PEM块大小.
     *
     * <p>
     * {@value}字符限制不计算结尾CRLF, 但计算所有其他字符, 包括任何等号.
     * </p>
     */
    public static final int PEM_CHUNK_SIZE = 64;

    private static final int DEFAULT_BUFFER_RESIZE_FACTOR = 2;

    /**
     * 定义默认缓冲区大小 - 对于至少一个编码的 块+分隔符 必须足够大
     */
    private static final int DEFAULT_BUFFER_SIZE = 128;

    /** 掩码用于提取8位, 用于解码字节 */
    protected static final int MASK_8BITS = 0xff;

    /**
     * 字节用于填充输出.
     */
    protected static final byte PAD_DEFAULT = '='; // 允许静态访问默认值

    protected final byte pad; // 实例变量, 以防以后需要改变

    /** 每个未编码的完整数据块中的字节数, e.g. 4 对于 Base64; 5 对于 Base32 */
    private final int unencodedBlockSize;

    /** 每个编码的完整数据块中的字节数, e.g. 3 对于 Base64; 8 对于 Base32 */
    private final int encodedBlockSize;

    /**
     * 用于编码的Chunksize. 解码时不使用.
     * 值为零或更小意味着没有编码数据的分块. 舍入到codedBlockSize的最接近的倍数.
     */
    protected final int lineLength;

    /**
     * 块分隔符的大小. 不会使用, 除非{@link #lineLength} &gt; 0.
     */
    private final int chunkSeparatorLength;

    /**
     * Note: <code>lineLength</code> 舍入到 {@link #encodedBlockSize}的最接近的倍数.
     * 如果 <code>chunkSeparatorLength</code> 是零, 然后禁用分块.
     * 
     * @param unencodedBlockSize 未编码块的大小 (e.g. Base64 = 3)
     * @param encodedBlockSize 编码块的大小 (e.g. Base64 = 4)
     * @param lineLength 如果 &gt; 0, 使用长度为<code>lineLength</code>的分块
     * @param chunkSeparatorLength 块分隔符长度
     */
    protected BaseNCodec(final int unencodedBlockSize, final int encodedBlockSize,
                         final int lineLength, final int chunkSeparatorLength) {
        this(unencodedBlockSize, encodedBlockSize, lineLength, chunkSeparatorLength, PAD_DEFAULT);
    }

    /**
     * Note: <code>lineLength</code> 舍入到 {@link #encodedBlockSize}的最接近的倍数.
     * 如果 <code>chunkSeparatorLength</code> 是零, 然后禁用分块.
     * 
     * @param unencodedBlockSize 未编码块的大小  (e.g. Base64 = 3)
     * @param encodedBlockSize 编码块的大小 (e.g. Base64 = 4)
     * @param lineLength 如果 &gt; 0, 使用长度为<code>lineLength</code>的分块
     * @param chunkSeparatorLength 块分隔符长度
     * @param pad 用作填充字节的字节.
     */
    protected BaseNCodec(final int unencodedBlockSize, final int encodedBlockSize,
                         final int lineLength, final int chunkSeparatorLength, final byte pad) {
        this.unencodedBlockSize = unencodedBlockSize;
        this.encodedBlockSize = encodedBlockSize;
        final boolean useChunking = lineLength > 0 && chunkSeparatorLength > 0;
        this.lineLength = useChunking ? (lineLength / encodedBlockSize) * encodedBlockSize : 0;
        this.chunkSeparatorLength = chunkSeparatorLength;

        this.pad = pad;
    }

    /**
     * 如果此对象具有用于读取的缓冲数据，则返回true.
     *
     * @param context 要使用的上下文
     * 
     * @return true 如果有数据仍然可供读取.
     */
    boolean hasData(final Context context) {
        return context.buffer != null;
    }

    /**
     * 返回可供读取的缓冲数据量.
     *
     * @param context 要使用的上下文
     * 
     * @return 可供读取的缓冲数据量.
     */
    int available(final Context context) {
        return context.buffer != null ? context.pos - context.readPos : 0;
    }

    /**
     * 获取默认缓冲区大小. 可以被覆盖.
     *
     * @return {@link #DEFAULT_BUFFER_SIZE}
     */
    protected int getDefaultBufferSize() {
        return DEFAULT_BUFFER_SIZE;
    }

    /**
     * 通过{@link #DEFAULT_BUFFER_RESIZE_FACTOR}增加缓冲区.
     * 
     * @param context 要使用的上下文
     */
    private byte[] resizeBuffer(final Context context) {
        if (context.buffer == null) {
            context.buffer = new byte[getDefaultBufferSize()];
            context.pos = 0;
            context.readPos = 0;
        } else {
            final byte[] b = new byte[context.buffer.length * DEFAULT_BUFFER_RESIZE_FACTOR];
            System.arraycopy(context.buffer, 0, b, 0, context.buffer.length);
            context.buffer = b;
        }
        return context.buffer;
    }

    /**
     * 确保缓冲区具有<code>size</code>字节的空间.
     *
     * @param size 所需的最小空间
     * @param context 要使用的上下文
     * 
     * @return 缓冲区
     */
    protected byte[] ensureBufferSize(final int size, final Context context){
        if ((context.buffer == null) || (context.buffer.length < context.pos + size)){
            return resizeBuffer(context);
        }
        return context.buffer;
    }

    /**
     * 将缓冲的数据提取到提供的byte[]数组中, 从 bPos开始, 一直到 bAvail. 返回实际提取的字节数.
     *
     * @param b 将保存提取到的缓冲数据的byte[]数组.
     * @param bPos 在byte[]数组中开始提取的位置.
     * @param bAvail 允许提取的字节数. 可能会提取的更少 (如果有更少的可用).
     * @param context 要使用的上下文
     * 
     * @return 成功提取到提供的byte[]数组中的字节数.
     */
    int readResults(final byte[] b, final int bPos, final int bAvail, final Context context) {
        if (context.buffer != null) {
            final int len = Math.min(available(context), bAvail);
            System.arraycopy(context.buffer, context.readPos, b, bPos, len);
            context.readPos += len;
            if (context.readPos >= context.pos) {
                context.buffer = null; // 因此 hasData() 将返回 false, 而且这个方法将返回 -1
            }
            return len;
        }
        return context.eof ? EOF : 0;
    }

    /**
     * 检查字节值是否为空格. 空格是指: space, tab, CR, LF
     * 
     * @param byteToCheck  要检查的字节
     *            
     * @return true 如果byte是空格, 否则false
     */
    protected static boolean isWhiteSpace(final byte byteToCheck) {
        switch (byteToCheck) {
            case ' ' :
            case '\n' :
            case '\r' :
            case '\t' :
                return true;
            default :
                return false;
        }
    }

    /**
     * 将包含二进制数据的byte[]编码为包含Base-N字母表中字符的String. 使用UTF8编码.
     *
     * @param pArray 包含二进制数据的字节数组
     * 
     * @return 仅包含Base-N字符数据的String
     */
    public String encodeToString(final byte[] pArray) {
        return StringUtils.newStringUtf8(encode(pArray));
    }

    /**
     * 将包含二进制数据的byte[]编码为包含相应字母表中字符的String. 使用UTF8编码.
     *
     * @param pArray 包含二进制数据的字节数组
     * 
     * @return 仅包含相应字母表中的字符数据的字符串.
    */
    public String encodeAsString(final byte[] pArray){
        return StringUtils.newStringUtf8(encode(pArray));
    }

    /**
     * 解码包含Base-N字母表中字符的String.
     *
     * @param pArray 包含Base-N字符数据的String
     * 
     * @return 包含二进制数据的字节数组
     */
    public byte[] decode(final String pArray) {
        return decode(StringUtils.getBytesUtf8(pArray));
    }

    /**
     * 解码包含Base-N字母表中字符的byte[].
     *
     * @param pArray 包含Base-N字符数据的字节数组
     * 
     * @return 包含二进制数据的字节数组
     */
    public byte[] decode(final byte[] pArray) {
        return decode(pArray, 0, pArray.length);
    }

    public byte[] decode(final byte[] pArray, final int off, final int len) {
        if (pArray == null || len == 0) {
            return new byte[0];
        }
        final Context context = new Context();
        decode(pArray, off, len, context);
        decode(pArray, off, EOF, context); // Notify decoder of EOF.
        final byte[] result = new byte[context.pos];
        readResults(result, 0, result.length, context);
        return result;
    }

    /**
     * 将包含二进制数据的byte[]编码为包含字母表中字符的byte[].
     *
     * @param pArray 包含二进制数据的字节数组
     * 
     * @return 仅包含基本N字母字符数据的字节数组
     */
    public byte[] encode(final byte[] pArray) {
        if (pArray == null || pArray.length == 0) {
            return pArray;
        }
        return encode(pArray, 0, pArray.length);
    }

    /**
     * 将包含二进制数据的byte[]编码为包含字母表中字符的byte[].
     *
     * @param pArray 包含二进制数据的字节数组
     * @param offset 子数组的初始偏移量.
     * @param length 子数组的长度.
     * 
     * @return 仅包含基本N字母字符数据的字节数组
     */
    public byte[] encode(final byte[] pArray, int offset, int length) {
        if (pArray == null || pArray.length == 0) {
            return pArray;
        }
        final Context context = new Context();
        encode(pArray, offset, length, context);
        encode(pArray, offset, EOF, context); // Notify encoder of EOF.
        final byte[] buf = new byte[context.pos - context.readPos];
        readResults(buf, 0, buf.length, context);
        return buf;
    }

    abstract void encode(byte[] pArray, int i, int length, Context context);

    abstract void decode(byte[] pArray, int i, int length, Context context);

    /**
     * 返回<code>octet</code>是否在当前字母表中. 不允许空格或PAD.
     *
     * @param value 要测试的值
     *
     * @return <code>true</code> 如果值是在当前字母表中定义的, 否则 <code>false</code>.
     */
    protected abstract boolean isInAlphabet(byte value);

    /**
     * 测试给定的字节数组，看它是否只包含字母表中的有效字符.
     * 该方法可选地将空格和PAD视为有效.
     *
     * @param arrayOctet 要测试的字节数组
     * @param allowWSPad 如果是 <code>true</code>, 允许空格和PAD
     *
     * @return <code>true</code> 如果所有字节都是字母表中的有效字符或字节数组为空;
     *         否则<code>false</code>
     */
    public boolean isInAlphabet(final byte[] arrayOctet, final boolean allowWSPad) {
        for (byte octet : arrayOctet) {
            if (!isInAlphabet(octet) &&
                    (!allowWSPad || (octet != pad) && !isWhiteSpace(octet))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 测试给定的String, 查看它是否仅包含字母表中的有效字符.
     * 该方法将空格和PAD视为有效.
     *
     * @param basen 要测试的字符串
     * 
     * @return <code>true</code> 如果字符串中的所有字符都是字母表中的有效字符，或者字符串为空; 否则<code>false</code>
     */
    public boolean isInAlphabet(final String basen) {
        return isInAlphabet(StringUtils.getBytesUtf8(basen), true);
    }

    /**
     * 测试给定的字节数组，看它是否包含字母表或PAD字符. 用于检查行结束数组
     *
     * @param arrayOctet 要测试的字节数组
     * 
     * @return <code>true</code> 如果任何字节是字母表或PAD中的有效字符; 否则<code>false</code>
     */
    protected boolean containsAlphabetOrPad(final byte[] arrayOctet) {
        if (arrayOctet == null) {
            return false;
        }
        for (final byte element : arrayOctet) {
            if (pad == element || isInAlphabet(element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算需要编码的数组所需的空间量.
     *
     * @param pArray 稍后将被编码的byte[]数组
     *
     * @return 编码数组所需的空间量. 返回 long类型, 因为 max-len 数组将需要 &gt; Integer.MAX_VALUE
     */
    public long getEncodedLength(final byte[] pArray) {
        // 计算非分块大小 - 需要向上舍入以允许填充长度, 以避免溢出的可能性
        long len = ((pArray.length + unencodedBlockSize-1)  / unencodedBlockSize) * (long) encodedBlockSize;
        if (lineLength > 0) { // 正在使用块
            // 四舍五入到最接近的倍数
            len += ((len + lineLength-1) / lineLength) * chunkSeparatorLength;
        }
        return len;
    }
}
