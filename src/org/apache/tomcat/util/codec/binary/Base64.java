package org.apache.tomcat.util.codec.binary;

import java.math.BigInteger;

/**
 * 提供Base64编码和解码.
 *
 * <p>
 * This class implements section <cite>6.8. Base64 Content-Transfer-Encoding</cite> from RFC 2045 <cite>Multipurpose
 * Internet Mail Extensions (MIME) Part One: Format of Internet Message Bodies</cite> by Freed and Borenstein.
 * </p>
 * <p>
 * 可以使用各种构造函数, 以下列方式对类进行参数化:
 * </p>
 * <ul>
 * <li>URL安全模式: 默认关闭.</li>
 * <li>行长度: 默认 76. 不是4的倍数的行长度在编码数据中本质上仍然是4的倍数.
 * <li>行分隔符: 默认是 CRLF ("\r\n")</li>
 * </ul>
 * <p>
 * URL安全参数仅适用于编码操作. 解码无缝地处理两种模式.
 * </p>
 * <p>
 * 由于此类直接在字节流上运行, 而不是字符流, 它是硬编码的，只编码/解码与低于127 ASCII图表(ISO-8859-1, Windows-1252, UTF-8, etc)兼容的字符编码 .
 * </p>
 * <p>
 * 这个类是线程安全的.
 * </p>
 */
public class Base64 extends BaseNCodec {

    /**
     * BASE32字符长度为6位.
     * 它们通过采用3个八位字节块形成24位字符串的格式, 转换为4个BASE64字符.
     */
    private static final int BITS_PER_ENCODED_BYTE = 6;
    private static final int BYTES_PER_UNENCODED_BLOCK = 3;
    private static final int BYTES_PER_ENCODED_BLOCK = 4;

    /**
     * RFC 2045第2.1节中的块分隔符.
     *
     * <p>
     * N.B. 下一个主要版本可能会破坏兼容性并使此字段 private.
     * </p>
     */
    static final byte[] CHUNK_SEPARATOR = {'\r', '\n'};

    /**
     * 此数组是一个查找表, 可将6位正整数索引值转换为 "Base64 Alphabet", 等效于RFC 2045表1中指定的.
     */
    private static final byte[] STANDARD_ENCODE_TABLE = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
    };

    /**
     * 这是上面STANDARD_ENCODE_TABLE的副本, 但是将 + 和 / 替换为 - 和 _ , 使编码的Base64产生更多URL-SAFE.
     * This table is only used when the Base64's mode is set to URL-SAFE.
     */
    private static final byte[] URL_SAFE_ENCODE_TABLE = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_'
    };

    /**
     * 此数组是一个查找表, 用于转换Unicode字符从 "Base64 Alphabet" (如RFC 2045的表1中所指定) 到它们的6位正整数当量.
     * 不在Base64字母表中但在数组范围内的字符将转换为-1.
     *
     * Note: '+' 和 '-' 都会被编码成 62. '/' 和 '_' 都会被编码成 63. 这意味着解码器可以无缝地处理URL_SAFE和STANDARD base64.
     * (另一方面，编码器需要提前知道发射什么).
     */
    private static final byte[] DECODE_TABLE = {
        //   0   1   2   3   4   5   6   7   8   9   A   B   C   D   E   F
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, // 00-0f
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, // 10-1f
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, 62, -1, 63, // 20-2f + - /
            52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1, // 30-3f 0-9
            -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, // 40-4f A-O
            15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, 63, // 50-5f P-Z _
            -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, // 60-6f a-o
            41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51                      // 70-7a p-z
    };

    /**
     * Base64使用6位字段.
     */
    /** 掩码用于提取6位, 编码时使用 */
    private static final int MASK_6BITS = 0x3f;

    // 上面的静态final字段用于Base64上的原始静态byte[]方法.
    // 下面的私有成员字段与新的流方法一起使用, 这需要在encode()和decode()的调用之间保留一些状态.

    /**
     * 使用的编码表: STANDARD 或 URL_SAFE.
     * Note: 上面的DECODE_TABLE保持静态, 因为它能够解码STANDARD和URL_SAFE流, 但encodeTable必须是一个成员变量, 所以我们可以在两种模式之间切换.
     */
    private final byte[] encodeTable;

    // 目前只有一个解码表; 保持与Base32代码的一致性
    private final byte[] decodeTable = DECODE_TABLE;

    /**
     * 用于编码的行分隔符. 解码时不使用. 仅当 lineLength &gt; 0 时使用.
     */
    private final byte[] lineSeparator;

    /**
     * 确定缓冲区何时用完, 并需要调整大小.
     * <code>decodeSize = 3 + lineSeparator.length;</code>
     */
    private final int decodeSize;

    /**
     * 确定缓冲区何时用完, 并需要调整大小.
     * <code>encodeSize = 4 + lineSeparator.length;</code>
     */
    private final int encodeSize;

    /**
     * 创建用于解码（所有模式）和URL不安全模式编码的Base64编解码器.
     * <p>
     * 编码行长度为0时 (没有分块), 而编码表是 STANDARD_ENCODE_TABLE.
     * </p>
     *
     * <p>
     * 解码时支持所有变体.
     * </p>
     */
    public Base64() {
        this(0);
    }

    /**
     * 创建用于解码（所有模式）和在给定的URL安全模式下编码的Base64编解码器.
     * <p>
     * 编码时的行长为76, 行分隔符是 CRLF, 而编码表是 STANDARD_ENCODE_TABLE.
     * </p>
     *
     * <p>
     * 解码时支持所有变体.
     * </p>
     *
     * @param urlSafe 如果是<code>true</code>, 使用URL安全编码. 在大多数情况下, 应将其设置为<code>false</code>.
     */
    public Base64(final boolean urlSafe) {
        this(MIME_CHUNK_SIZE, CHUNK_SEPARATOR, urlSafe);
    }

    /**
     * 创建用于解码（所有模式）和URL不安全模式编码的Base64编解码器.
     * <p>
     * 在编码行长度在构造函数中给出时, 行分隔符是 CRLF, 而编码表是 STANDARD_ENCODE_TABLE.
     * </p>
     * <p>
     * 不是4的倍数的行长度在编码数据中本质上仍然是4的倍数.
     * </p>
     * <p>
     * 解码时支持所有变体.
     * </p>
     *
     * @param lineLength 每行编码数据最多为给定长度 (向下舍入到最接近的4的倍数). 
     * 		如果 lineLength &lt;= 0, 那么输出就不会分成几行 (块). 解码时忽略.
     */
    public Base64(final int lineLength) {
        this(lineLength, CHUNK_SEPARATOR);
    }

    /**
     * 创建用于解码（所有模式）和URL不安全模式编码的Base64编解码器.
     * <p>
     * 在编码行长度和行分隔符在构造函数中给出时, 而编码表是 STANDARD_ENCODE_TABLE.
     * </p>
     * <p>
     * 不是4的倍数的行长度在编码数据中本质上仍然是4的倍数.
     * </p>
     * <p>
     * 解码时支持所有变体.
     * </p>
     *
     * @param lineLength 每行编码数据最多为给定长度 (向下舍入到最接近的4的倍数). 
     * 		如果 lineLength &lt;= 0, 那么输出就不会分成几行 (块). 解码时忽略.
     * @param lineSeparator  每行编码数据将以此字节序列结束.
     * 
     * @throws IllegalArgumentException 当提供的lineSeparator包含一些base64字符时抛出.
     */
    public Base64(final int lineLength, final byte[] lineSeparator) {
        this(lineLength, lineSeparator, false);
    }

    /**
     * 创建用于解码（所有模式）和URL不安全模式编码的Base64编解码器.
     * <p>
     * 在编码行长度和行分隔符在构造函数中给出时, 而编码表是 STANDARD_ENCODE_TABLE.
     * </p>
     * <p>
     * 不是4的倍数的行长度在编码数据中本质上仍然是4的倍数.
     * </p>
     * <p>
     * 解码时支持所有变体.
     * </p>
     *
     * @param lineLength  每行编码数据最多为给定长度 (向下舍入到最接近的4的倍数). 
     * 		如果 lineLength &lt;= 0, 那么输出就不会分成几行 (块). 解码时忽略.
     * @param lineSeparator  每行编码数据将以此字节序列结束.
     * @param urlSafe  不是发出'+'和'/', 而是分别发出' - '和'_'. urlSafe仅适用于编码操作. 解码无缝地处理两种模式.
     *            <b>Note: 使用URL安全字母表时不添加填充.</b>
     *            
     * @throws IllegalArgumentException  当提供的lineSeparator包含一些base64字符时抛出.
     */
    public Base64(final int lineLength, final byte[] lineSeparator, final boolean urlSafe) {
        super(BYTES_PER_UNENCODED_BLOCK, BYTES_PER_ENCODED_BLOCK,
                lineLength,
                lineSeparator == null ? 0 : lineSeparator.length);
        // TODO 如果在length <= 0时没有要求拒绝无效行sep，则可以简化
        if (lineSeparator != null) {
            if (containsAlphabetOrPad(lineSeparator)) {
                final String sep = StringUtils.newStringUtf8(lineSeparator);
                throw new IllegalArgumentException("lineSeparator must not contain base64 characters: [" + sep + "]");
            }
            if (lineLength > 0){ // null line-sep强制没有分块, 而不是抛出IAE
                this.encodeSize = BYTES_PER_ENCODED_BLOCK + lineSeparator.length;
                this.lineSeparator = new byte[lineSeparator.length];
                System.arraycopy(lineSeparator, 0, this.lineSeparator, 0, lineSeparator.length);
            } else {
                this.encodeSize = BYTES_PER_ENCODED_BLOCK;
                this.lineSeparator = null;
            }
        } else {
            this.encodeSize = BYTES_PER_ENCODED_BLOCK;
            this.lineSeparator = null;
        }
        this.decodeSize = this.encodeSize - 1;
        this.encodeTable = urlSafe ? URL_SAFE_ENCODE_TABLE : STANDARD_ENCODE_TABLE;
    }

    /**
     * 返回当前的编码模式. True 是 URL-SAFE, 否则 false.
     *
     * @return true 如果处于URL-SAFE模式, 否则 false.
     */
    public boolean isUrlSafe() {
        return this.encodeTable == URL_SAFE_ENCODE_TABLE;
    }

    /**
     * <p>
     * 对所有提供的数据进行编码, 从 inPos 开始, 总共 inAvail 字节.
     * 必须至少调用两次: 一旦用数据编码, 而且一旦 inAvail 设置为 "-1", 来提醒编码器已达到EOF, 刷新最后剩余的字节 (如果不是3的倍数).
     * </p>
     * <p><b>Note: 使用URL安全字母表进行编码时不添加填充.</b></p>
     *
     * @param in 要base64编码的二进制数据的 byte[] 数组.
     * @param inPos 从中读取数据的开始位置.
     * @param inAvail 输入中可用于编码的字节数.
     * @param context 要使用的上下文
     */
    @Override
    void encode(final byte[] in, int inPos, final int inAvail, final Context context) {
        if (context.eof) {
            return;
        }
        // inAvail < 0 is how we're informed of EOF in the underlying data we're
        // encoding.
        if (inAvail < 0) {
            context.eof = true;
            if (0 == context.modulus && lineLength == 0) {
                return; // 没有剩余要处理和不使用分块
            }
            final byte[] buffer = ensureBufferSize(encodeSize, context);
            final int savedPos = context.pos;
            switch (context.modulus) { // 0-2
                case 0 : // nothing to do here
                    break;
                case 1 : // 8 bits = 6 + 2
                    // top 6 bits:
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea >> 2) & MASK_6BITS];
                    // remaining 2:
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea << 4) & MASK_6BITS];
                    // URL-SAFE 跳过填充以进一步减小大小.
                    if (encodeTable == STANDARD_ENCODE_TABLE) {
                        buffer[context.pos++] = pad;
                        buffer[context.pos++] = pad;
                    }
                    break;

                case 2 : // 16 bits = 6 + 6 + 4
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea >> 10) & MASK_6BITS];
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea >> 4) & MASK_6BITS];
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea << 2) & MASK_6BITS];
                    // URL-SAFE 跳过填充以进一步减小大小.
                    if (encodeTable == STANDARD_ENCODE_TABLE) {
                        buffer[context.pos++] = pad;
                    }
                    break;
                default:
                    throw new IllegalStateException("Impossible modulus "+context.modulus);
            }
            context.currentLinePos += context.pos - savedPos; // keep track of current line position
            // if currentPos == 0 we are at the start of a line, so don't add CRLF
            if (lineLength > 0 && context.currentLinePos > 0) {
                System.arraycopy(lineSeparator, 0, buffer, context.pos, lineSeparator.length);
                context.pos += lineSeparator.length;
            }
        } else {
            for (int i = 0; i < inAvail; i++) {
                final byte[] buffer = ensureBufferSize(encodeSize, context);
                context.modulus = (context.modulus+1) % BYTES_PER_UNENCODED_BLOCK;
                int b = in[inPos++];
                if (b < 0) {
                    b += 256;
                }
                context.ibitWorkArea = (context.ibitWorkArea << 8) + b; //  BITS_PER_BYTE
                if (0 == context.modulus) { // 3 bytes = 24 bits = 4 * 6 bits to extract
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea >> 18) & MASK_6BITS];
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea >> 12) & MASK_6BITS];
                    buffer[context.pos++] = encodeTable[(context.ibitWorkArea >> 6) & MASK_6BITS];
                    buffer[context.pos++] = encodeTable[context.ibitWorkArea & MASK_6BITS];
                    context.currentLinePos += BYTES_PER_ENCODED_BLOCK;
                    if (lineLength > 0 && lineLength <= context.currentLinePos) {
                        System.arraycopy(lineSeparator, 0, buffer, context.pos, lineSeparator.length);
                        context.pos += lineSeparator.length;
                        context.currentLinePos = 0;
                    }
                }
            }
        }
    }

    /**
     * <p>
     * 对所有提供的数据进行解码, 从 inPos 开始, 总共 inAvail 字节.
     * 必须至少调用两次: 一旦用数据编码, 而且一旦 inAvail 设置为 "-1", 来提醒编码器已达到EOF.
     * 解码时不需要 "-1" 调用, 但它并没有伤害.
     * </p>
     * <p>
     * 忽略所有非base64字符. 这就是处理分块（即76个字符）数据的方式, 因为CR和LF被默默地忽略了, 但也对其他字节有影响.
     * 这个方法订阅了垃圾进入，垃圾输出的理念: 它不会检查提供的数据是否有效.
     * </p>
     *
     * @param in 要base64编码的二进制数据的 byte[] 数组.
     * @param inPos 从中读取数据的开始位置.
     * @param inAvail 输入中可用于编码的字节数.
     * @param context 要使用的上下文
     */
    @Override
    void decode(final byte[] in, int inPos, final int inAvail, final Context context) {
        if (context.eof) {
            return;
        }
        if (inAvail < 0) {
            context.eof = true;
        }
        for (int i = 0; i < inAvail; i++) {
            final byte[] buffer = ensureBufferSize(decodeSize, context);
            final byte b = in[inPos++];
            if (b == pad) {
                // We're done.
                context.eof = true;
                break;
            }
            if (b >= 0 && b < DECODE_TABLE.length) {
                final int result = DECODE_TABLE[b];
                if (result >= 0) {
                    context.modulus = (context.modulus+1) % BYTES_PER_ENCODED_BLOCK;
                    context.ibitWorkArea = (context.ibitWorkArea << BITS_PER_ENCODED_BYTE) + result;
                    if (context.modulus == 0) {
                        buffer[context.pos++] = (byte) ((context.ibitWorkArea >> 16) & MASK_8BITS);
                        buffer[context.pos++] = (byte) ((context.ibitWorkArea >> 8) & MASK_8BITS);
                        buffer[context.pos++] = (byte) (context.ibitWorkArea & MASK_8BITS);
                    }
                }
            }
        }

        // Two forms of EOF as far as base64 decoder is concerned: actual
        // EOF (-1) and first time '=' character is encountered in stream.
        // This approach makes the '=' padding characters completely optional.
        if (context.eof && context.modulus != 0) {
            final byte[] buffer = ensureBufferSize(decodeSize, context);

            // We have some spare bits remaining
            // Output all whole multiples of 8 bits and ignore the rest
            switch (context.modulus) {
//              case 0 : // impossible, as excluded above
                case 1 : // 6 bits - ignore entirely
                    // TODO not currently tested; perhaps it is impossible?
                    break;
                case 2 : // 12 bits = 8 + 4
                    context.ibitWorkArea = context.ibitWorkArea >> 4; // dump the extra 4 bits
                    buffer[context.pos++] = (byte) ((context.ibitWorkArea) & MASK_8BITS);
                    break;
                case 3 : // 18 bits = 8 + 8 + 2
                    context.ibitWorkArea = context.ibitWorkArea >> 2; // dump 2 bits
                    buffer[context.pos++] = (byte) ((context.ibitWorkArea >> 8) & MASK_8BITS);
                    buffer[context.pos++] = (byte) ((context.ibitWorkArea) & MASK_8BITS);
                    break;
                default:
                    throw new IllegalStateException("Impossible modulus "+context.modulus);
            }
        }
    }

    /**
     * <code>octet</code>是否在base 64字母表中.
     *
     * @param octet 要测试的值
     * 
     * @return <code>true</code> 如果值是base 64字母表中定义的, 否则<code>false</code>.
     */
    public static boolean isBase64(final byte octet) {
        return octet == PAD_DEFAULT || (octet >= 0 && octet < DECODE_TABLE.length && DECODE_TABLE[octet] != -1);
    }

    /**
     * 测试给定的String, 以查看它是否仅包含Base64字母表中的有效字符. 目前该方法将空格视为有效.
     *
     * @param base64 要测试的字符串
     *            
     * @return <code>true</code> 如果String中的所有字符都是Base64字母表中的有效字符, 或者String是空的; 否则<code>false</code>
     */
    public static boolean isBase64(final String base64) {
        return isBase64(StringUtils.getBytesUtf8(base64));
    }

    /**
     * 测试给定的字节数组, 看它是否只包含Base64字母表中的有效字符. 目前，该方法将空格视为有效.
     *
     * @param arrayOctet 要测试的字节数组
     * @return <code>true</code> 如果所有字节都是Base64字母表中的有效字符，或者字节数组为空;
     *         否则<code>false</code>
     */
    public static boolean isBase64(final byte[] arrayOctet) {
        for (int i = 0; i < arrayOctet.length; i++) {
            if (!isBase64(arrayOctet[i]) && !isWhiteSpace(arrayOctet[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 使用base64算法对二进制数据进行编码，但不会对输出进行分块.
     *
     * @param binaryData 要编码的二进制数据
     * 
     * @return 包含UTF-8表示的Base64字符的byte[].
     */
    public static byte[] encodeBase64(final byte[] binaryData) {
        return encodeBase64(binaryData, false);
    }

    /**
     * 使用base64算法对二进制数据进行编码，但不会对输出进行分块.
     *
     * NOTE: 将此方法的行为从多行分块（commons-codec-1.4）更改为单行非分块（commons-codec-1.5）.
     *
     * @param binaryData 要编码的二进制数据
     * 
     * @return 包含Base64字符的字符串.
     */
    public static String encodeBase64String(final byte[] binaryData) {
        return StringUtils.newStringUtf8(encodeBase64(binaryData, false));
    }

    /**
     * 使用base64算法的URL安全变体对二进制数据进行编码，但不会对输出进行分块. url-safe变体发出 - 和 _ , 而不是+和/字符.
     * <b>Note: 没有添加填充.</b>
     * 
     * @param binaryData 要编码的二进制数据
     * 
     * @return 包含UTF-8表示的Base64字符的byte[].
     */
    public static byte[] encodeBase64URLSafe(final byte[] binaryData) {
        return encodeBase64(binaryData, false, true);
    }

    /**
     * 使用base64算法的URL安全变体对二进制数据进行编码，但不会对输出进行分块. url-safe变体发出 - 和 _ , 而不是+和/字符.
     * <b>Note: 没有添加填充.</b>
     * 
     * @param binaryData 要编码的二进制数据
     *            
     * @return 包含Base64字符的字符串.
     */
    public static String encodeBase64URLSafeString(final byte[] binaryData) {
        return StringUtils.newStringUtf8(encodeBase64(binaryData, false, true));
    }

    /**
     * 使用base64算法对二进制数据进行编码，并将编码输出分块为76个字符块
     *
     * @param binaryData 要编码的二进制数据
     * 
     * @return 以76个字符块为单位的Base64字符
     */
    public static byte[] encodeBase64Chunked(final byte[] binaryData) {
        return encodeBase64(binaryData, true);
    }

    /**
     * 使用base64算法对二进制数据进行编码，可选择将输出分块为76个字符块.
     *
     * @param binaryData 包含要编码的二进制数据的数组.
     * @param isChunked 如果是<code>true</code>，此编码器会将base64输出分块为76个字符块
     *            
     * @return Base64编码的数据.
     * @throws IllegalArgumentException 当输入数组需要大于{@link Integer＃MAX_VALUE}的输出数组时抛出
     */
    public static byte[] encodeBase64(final byte[] binaryData, final boolean isChunked) {
        return encodeBase64(binaryData, isChunked, false);
    }

    /**
     * 使用base64算法对二进制数据进行编码，可选的将输出分块为76个字符块.
     *
     * @param binaryData 包含要编码的二进制数据的数组.
     * @param isChunked  如果是<code>true</code> 此编码器将base64输出分块为76个字符块
     * @param urlSafe  如果是<code>true</code> 此编码器将发出 - 和 _ , 而不是通常的 + 和 / 字符.
     *            <b>Note: 使用URL安全字母表进行编码时不添加填充.</b>
     *            
     * @return Base64编码的数据.
     * @throws IllegalArgumentException 当输入数组需要大于{@link Integer#MAX_VALUE}的输出数组时抛出
     */
    public static byte[] encodeBase64(final byte[] binaryData, final boolean isChunked, final boolean urlSafe) {
        return encodeBase64(binaryData, isChunked, urlSafe, Integer.MAX_VALUE);
    }

    /**
     * 使用base64算法对二进制数据进行编码，可选的将输出分块为76个字符块.
     *
     * @param binaryData 包含要编码的二进制数据的数组.
     * @param isChunked 如果是<code>true</code> 此编码器将base64输出分块为76个字符块
     * @param urlSafe  如果是<code>true</code> 此编码器将发出 - 和 _ , 而不是通常的 + 和 / 字符.
     *            <b>Note: 使用URL安全字母表进行编码时不添加填充.</b>
     * @param maxResultSize  要接受的最大结果大小.
     *            
     * @return Base64编码的数据.
     * @throws IllegalArgumentException  当输入数组需要大于maxResultSize的输出数组时抛出
     */
    public static byte[] encodeBase64(final byte[] binaryData, final boolean isChunked,
                                      final boolean urlSafe, final int maxResultSize) {
        if (binaryData == null || binaryData.length == 0) {
            return binaryData;
        }

        // Create this so can use the super-class method
        // Also ensures that the same roundings are performed by the ctor and the code
        final Base64 b64 = isChunked ? new Base64(urlSafe) : new Base64(0, CHUNK_SEPARATOR, urlSafe);
        final long len = b64.getEncodedLength(binaryData);
        if (len > maxResultSize) {
            throw new IllegalArgumentException("Input array too big, the output array would be bigger (" +
                len +
                ") than the specified maximum size of " +
                maxResultSize);
        }

        return b64.encode(binaryData);
    }

    /**
     * 将Base64字符串解码为八位字节.
     * <p>
     * <b>Note:</b> 此方法无缝处理以URL安全或正常模式编码的数据.
     * </p>
     *
     * @param base64String 包含Base64数据的字符串
     *            
     * @return 包含解码数据的数组.
     */
    public static byte[] decodeBase64(final String base64String) {
        return new Base64().decode(base64String);
    }

    /**
     * 将Base64字符串解码为八位字节.
     * <p>
     * <b>Note:</b> 此方法无缝处理以URL安全或正常模式编码的数据.
     * </p>
     *
     * @param base64Data  包含Base64数据的字节数组
     *            
     * @return 包含解码数据的数组.
     */
    public static byte[] decodeBase64(final byte[] base64Data) {
        return decodeBase64(base64Data, 0, base64Data.length);
    }

    public  static byte[] decodeBase64(
            final byte[] base64Data, final int off, final int len) {
        return new Base64().decode(base64Data, off, len);
    }

    // Implementation of the Encoder Interface

    // 用于加密的整数编码的实现
    /**
     * 根据加密标准（如W3C的XML-Signature）对byte64编码的整数进行解码.
     *
     * @param pArray 包含base64字符数据的字节数组
     *            
     * @return A BigInteger
     */
    public static BigInteger decodeInteger(final byte[] pArray) {
        return new BigInteger(1, decodeBase64(pArray));
    }

    /**
     * 根据加密标准（如W3C的XML-Signature）编码为byte64编码的整数.
     *
     * @param bigInt
     *            
     * @return 包含base64字符数据的字节数组
     * @throws NullPointerException 如果入参是 null
     */
    public static byte[] encodeInteger(final BigInteger bigInt) {
        if (bigInt == null) {
            throw new NullPointerException("encodeInteger called with null parameter");
        }
        return encodeBase64(toIntegerBytes(bigInt), false);
    }

    /**
     * 返回没有符号位的<code>BigInteger</code>的字节数组表示形式.
     *
     * @param bigInt  要转换的<code>BigInteger</code>
     *            
     * @return BigInteger参数的字节数组表示形式
     */
    static byte[] toIntegerBytes(final BigInteger bigInt) {
        int bitlen = bigInt.bitLength();
        // round bitlen
        bitlen = ((bitlen + 7) >> 3) << 3;
        final byte[] bigBytes = bigInt.toByteArray();

        if (((bigInt.bitLength() % 8) != 0) && (((bigInt.bitLength() / 8) + 1) == (bitlen / 8))) {
            return bigBytes;
        }
        // set up params for copying everything but sign bit
        int startSrc = 0;
        int len = bigBytes.length;

        // if bigInt is exactly byte-aligned, just skip signbit in copy
        if ((bigInt.bitLength() % 8) == 0) {
            startSrc = 1;
            len--;
        }
        final int startDst = bitlen / 8 - len; // to pad w/ nulls as per spec
        final byte[] resizedBytes = new byte[bitlen / 8];
        System.arraycopy(bigBytes, startSrc, resizedBytes, startDst, len);
        return resizedBytes;
    }

    /**
     * <code>octet</code>是否在Base64字母表中.
     *
     * @param octet 要测试的值
     * 
     * @return <code>true</code>如果值是在Base64字母表中定义的; 否则<code>false</code>.
     */
    @Override
    protected boolean isInAlphabet(final byte octet) {
        return octet >= 0 && octet < decodeTable.length && decodeTable[octet] != -1;
    }
}
