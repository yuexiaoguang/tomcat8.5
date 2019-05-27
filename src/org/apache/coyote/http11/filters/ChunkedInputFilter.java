package org.apache.coyote.http11.filters;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.http11.Constants;
import org.apache.coyote.http11.InputFilter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

/**
 * 块输入过滤器. 解析块数据, 根据
 * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1">http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1</a><br>
 */
public class ChunkedInputFilter implements InputFilter, ApplicationBufferHandler {

    private static final StringManager sm = StringManager.getManager(
            ChunkedInputFilter.class.getPackage().getName());


    // -------------------------------------------------------------- Constants

    protected static final String ENCODING_NAME = "chunked";
    protected static final ByteChunk ENCODING = new ByteChunk();


    // ----------------------------------------------------- Static Initializer

    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(StandardCharsets.ISO_8859_1),
                0, ENCODING_NAME.length());
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 管道中的下一个缓冲区.
     */
    protected InputBuffer buffer;


    /**
     * 当前块中剩余的字节数.
     */
    protected int remaining = 0;


    /**
     * 用于读取字节的字节块.
     */
    protected ByteBuffer readChunk;


    /**
     * 已经读取结尾块之后设置为 true.
     */
    protected boolean endChunk = false;


    /**
     * 用于存储结尾header的字节块.
     */
    protected final ByteChunk trailingHeaders = new ByteChunk();


    /**
     * 如果设置为 true, 如果下一次调用 doRead() 必须解析一个 CRLF 对, 在做其它事情之前.
     */
    protected boolean needCRLFParse = false;


    /**
     * 正在解析的请求.
     */
    private Request request;


    /**
     * 扩展名的大小的限制.
     */
    private final long maxExtensionSize;


    /**
     * 尾部大小的限制.
     */
    private final int maxTrailerSize;


    /**
     * 处理的这个请求的扩展名的大小.
     */
    private long extensionSize;


    private final int maxSwallowSize;


    /**
     * 是否发生错误.
     */
    private boolean error;


    private final Set<String> allowedTrailerHeaders;

    // ----------------------------------------------------------- Constructors

    public ChunkedInputFilter(int maxTrailerSize, Set<String> allowedTrailerHeaders,
            int maxExtensionSize, int maxSwallowSize) {
        this.trailingHeaders.setLimit(maxTrailerSize);
        this.allowedTrailerHeaders = allowedTrailerHeaders;
        this.maxExtensionSize = maxExtensionSize;
        this.maxTrailerSize = maxTrailerSize;
        this.maxSwallowSize = maxSwallowSize;
    }


    // ---------------------------------------------------- InputBuffer Methods

    /**
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doRead(ApplicationBufferHandler)}
     */
    @Deprecated
    @Override
    public int doRead(ByteChunk chunk) throws IOException {
        if (endChunk) {
            return -1;
        }

        checkError();

        if(needCRLFParse) {
            needCRLFParse = false;
            parseCRLF(false);
        }

        if (remaining <= 0) {
            if (!parseChunkHeader()) {
                throwIOException(sm.getString("chunkedInputFilter.invalidHeader"));
            }
            if (endChunk) {
                parseEndChunk();
                return -1;
            }
        }

        int result = 0;

        if (readChunk == null || readChunk.position() >= readChunk.limit()) {
            if (readBytes() < 0) {
                throwIOException(sm.getString("chunkedInputFilter.eos"));
            }
        }

        if (remaining > readChunk.remaining()) {
            result = readChunk.remaining();
            remaining = remaining - result;
            chunk.setBytes(readChunk.array(), readChunk.arrayOffset() + readChunk.position(), result);
            readChunk.position(readChunk.limit());
        } else {
            result = remaining;
            chunk.setBytes(readChunk.array(), readChunk.arrayOffset() + readChunk.position(), remaining);
            readChunk.position(readChunk.position() + remaining);
            remaining = 0;
            //we need a CRLF
            if ((readChunk.position() + 1) >= readChunk.limit()) {
                //如果调用 parseCRLF, 在这里溢出缓冲区
                //因此推迟到下次调用 BZ 11117
                needCRLFParse = true;
            } else {
                parseCRLF(false); //直接解析 CRLF
            }
        }

        return result;
    }

    @Override
    public int doRead(ApplicationBufferHandler handler) throws IOException {
        if (endChunk) {
            return -1;
        }

        checkError();

        if(needCRLFParse) {
            needCRLFParse = false;
            parseCRLF(false);
        }

        if (remaining <= 0) {
            if (!parseChunkHeader()) {
                throwIOException(sm.getString("chunkedInputFilter.invalidHeader"));
            }
            if (endChunk) {
                parseEndChunk();
                return -1;
            }
        }

        int result = 0;

        if (readChunk == null || readChunk.position() >= readChunk.limit()) {
            if (readBytes() < 0) {
                throwIOException(sm.getString("chunkedInputFilter.eos"));
            }
        }

        if (remaining > readChunk.remaining()) {
            result = readChunk.remaining();
            remaining = remaining - result;
            if (readChunk != handler.getByteBuffer()) {
                handler.setByteBuffer(readChunk.duplicate());
            }
            readChunk.position(readChunk.limit());
        } else {
            result = remaining;
            if (readChunk != handler.getByteBuffer()) {
                handler.setByteBuffer(readChunk.duplicate());
                handler.getByteBuffer().limit(readChunk.position() + remaining);
            }
            readChunk.position(readChunk.position() + remaining);
            remaining = 0;
            //we need a CRLF
            if ((readChunk.position() + 1) >= readChunk.limit()) {
                //如果调用 parseCRLF, 在这里溢出缓冲区
                //因此推迟到下次调用 BZ 11117
                needCRLFParse = true;
            } else {
                parseCRLF(false); //直接解析 CRLF
            }
        }

        return result;
    }


    // ---------------------------------------------------- InputFilter Methods

    /**
     * 从请求中读取内容长度.
     */
    @Override
    public void setRequest(Request request) {
        this.request = request;
    }


    /**
     * 结束当前请求.
     */
    @Override
    public long end() throws IOException {
        long swallowed = 0;
        int read = 0;
        // 消耗额外的字节 : 解析流直到找到结束块
        while ((read = doRead(this)) >= 0) {
            swallowed += read;
            if (maxSwallowSize > -1 && swallowed > maxSwallowSize) {
                throwIOException(sm.getString("inputFilter.maxSwallow"));
            }
        }

        // 返回被消耗的额外字节数
        return readChunk.remaining();
    }


    /**
     * 缓冲区中仍然可用的字节数.
     */
    @Override
    public int available() {
        return readChunk != null ? readChunk.remaining() : 0;
    }


    /**
     * 在过滤器管道中设置下一个缓冲区.
     */
    @Override
    public void setBuffer(InputBuffer buffer) {
        this.buffer = buffer;
    }


    /**
     * 使过滤器准备好处理下一个请求.
     */
    @Override
    public void recycle() {
        remaining = 0;
        if (readChunk != null) {
            readChunk.position(0).limit(0);
        }
        endChunk = false;
        needCRLFParse = false;
        trailingHeaders.recycle();
        trailingHeaders.setLimit(maxTrailerSize);
        extensionSize = 0;
        error = false;
    }


    /**
     * 返回关联的编码的名称; 这里是"identity".
     */
    @Override
    public ByteChunk getEncodingName() {
        return ENCODING;
    }


    @Override
    public boolean isFinished() {
        return endChunk;
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * 从前一个缓冲区读取字节.
     * @return 已读取的字节计数
     * @throws IOException 读取错误
     */
    protected int readBytes() throws IOException {
        return buffer.doRead(this);
    }


    /**
     * 解析块的 header.
     * 块header 可以看起来像下面的一个:<br>
     * A10CRLF<br>
     * F23;chunk-extension to be ignoredCRLF
     *
     * <p>
     * CRLF 或 ';'之前的信息 (whatever comes first) 必须是有效的十六进制数字. 根据规范不会解析 F23IAMGONNAMESSTHISUP34CRLF 为一个有效的 header.
     * 
     * @return <code>true</code>如果成功解析块 header
     * @throws IOException 读取错误
     */
    protected boolean parseChunkHeader() throws IOException {

        int result = 0;
        boolean eol = false;
        int readDigit = 0;
        boolean extension = false;

        while (!eol) {

            if (readChunk == null || readChunk.position() >= readChunk.limit()) {
                if (readBytes() <= 0)
                    return false;
            }

            byte chr = readChunk.get(readChunk.position());
            if (chr == Constants.CR || chr == Constants.LF) {
                parseCRLF(false);
                eol = true;
            } else if (chr == Constants.SEMI_COLON && !extension) {
                // 扩展名开始的第一个 semi-colon 标记.
                // 更远的semi-colon可能出现在分隔的多个块扩展名.
                // 这些需要作为解析扩展名的一部分来处理.
                extension = true;
                extensionSize++;
            } else if (!extension) {
                // 不要读取trailer之后的数据
                int charValue = HexUtils.getDec(chr);
                if (charValue != -1 && readDigit < 8) {
                    readDigit++;
                    result = (result << 4) | charValue;
                } else {
                    //不允许无效, 在块 header 中没有十六进制字符
                    return false;
                }
            } else {
                // Extension 'parsing'
                // 注意，块扩展名既不解析也不验证. 当前只是忽略掉.
                extensionSize++;
                if (maxExtensionSize > -1 && extensionSize > maxExtensionSize) {
                    throwIOException(sm.getString("chunkedInputFilter.maxExtension"));
                }
            }

            // 解析 CRLF 增量位置
            if (!eol) {
                readChunk.position(readChunk.position() + 1);
            }
        }

        if (readDigit == 0 || result < 0) {
            return false;
        }

        if (result == 0) {
            endChunk = true;
        }

        remaining = result;
        return true;
    }


    /**
     * 解析块结束时的CRLF.
     *
     * @param   tolerant    是否使用宽容的解析 (LF 和 CRLF)? 推荐用于消息header (RFC2616, section 19.3).
     * @throws IOException CRLF解析出错
     */
    protected void parseCRLF(boolean tolerant) throws IOException {

        boolean eol = false;
        boolean crfound = false;

        while (!eol) {
            if (readChunk == null || readChunk.position() >= readChunk.limit()) {
                if (readBytes() <= 0) {
                    throwIOException(sm.getString("chunkedInputFilter.invalidCrlfNoData"));
                }
            }

            byte chr = readChunk.get(readChunk.position());
            if (chr == Constants.CR) {
                if (crfound) {
                    throwIOException(sm.getString("chunkedInputFilter.invalidCrlfCRCR"));
                }
                crfound = true;
            } else if (chr == Constants.LF) {
                if (!tolerant && !crfound) {
                    throwIOException(sm.getString("chunkedInputFilter.invalidCrlfNoCR"));
                }
                eol = true;
            } else {
                throwIOException(sm.getString("chunkedInputFilter.invalidCrlf"));
            }

            readChunk.position(readChunk.position() + 1);
        }
    }


    /**
     * 解析结尾块数据.
     * @throws IOException Error propagation
     */
    protected void parseEndChunk() throws IOException {
        // Handle optional trailer headers
        while (parseHeader()) {
            // Loop until we run out of headers
        }
    }


    private boolean parseHeader() throws IOException {

        MimeHeaders headers = request.getMimeHeaders();

        byte chr = 0;

        // Read new bytes if needed
        if (readChunk == null || readChunk.position() >= readChunk.limit()) {
            if (readBytes() <0) {
               throwEOFException(sm.getString("chunkedInputFilter.eosTrailer"));
            }
        }

        // 上面的 readBytes() 将设置 readChunk, 除非返回的值 < 0
        chr = readChunk.get(readChunk.position());

        // CRLF 终止请求
        if (chr == Constants.CR || chr == Constants.LF) {
            parseCRLF(false);
            return false;
        }

        // 当前缓冲位置
        int startPos = trailingHeaders.getEnd();

        //
        // 读取 header 名称
        // Header 名称总是 US-ASCII
        //

        boolean colon = false;
        while (!colon) {

            // Read new bytes if needed
            if (readChunk == null || readChunk.position() >= readChunk.limit()) {
                if (readBytes() <0) {
                    throwEOFException(sm.getString("chunkedInputFilter.eosTrailer"));
                }
            }

            // 上面的 readBytes() 将设置 readChunk, 除非返回的值 < 0
            chr = readChunk.get(readChunk.position());
            if ((chr >= Constants.A) && (chr <= Constants.Z)) {
                chr = (byte) (chr - Constants.LC_OFFSET);
            }

            if (chr == Constants.COLON) {
                colon = true;
            } else {
                trailingHeaders.append(chr);
            }

            readChunk.position(readChunk.position() + 1);

        }
        int colonPos = trailingHeaders.getEnd();

        //
        // Reading the header value (可以跨越多行)
        //

        boolean eol = false;
        boolean validLine = true;
        int lastSignificantChar = 0;

        while (validLine) {

            boolean space = true;

            // 跳过空格
            while (space) {

                // Read new bytes if needed
                if (readChunk == null || readChunk.position() >= readChunk.limit()) {
                    if (readBytes() <0) {
                        throwEOFException(sm.getString("chunkedInputFilter.eosTrailer"));
                    }
                }

                chr = readChunk.get(readChunk.position());
                if ((chr == Constants.SP) || (chr == Constants.HT)) {
                    readChunk.position(readChunk.position() + 1);
                    // 如果忽略 whitespace, 确保它对尾部header大小的限制进行计数
                    int newlimit = trailingHeaders.getLimit() -1;
                    if (trailingHeaders.getEnd() > newlimit) {
                        throwIOException(sm.getString("chunkedInputFilter.maxTrailer"));
                    }
                    trailingHeaders.setLimit(newlimit);
                } else {
                    space = false;
                }

            }

            // 读取字节直到行的结尾
            while (!eol) {

                // Read new bytes if needed
                if (readChunk == null || readChunk.position() >= readChunk.limit()) {
                    if (readBytes() <0) {
                        throwEOFException(sm.getString("chunkedInputFilter.eosTrailer"));
                    }
                }

                chr = readChunk.get(readChunk.position());
                if (chr == Constants.CR || chr == Constants.LF) {
                    parseCRLF(true);
                    eol = true;
                } else if (chr == Constants.SP) {
                    trailingHeaders.append(chr);
                } else {
                    trailingHeaders.append(chr);
                    lastSignificantChar = trailingHeaders.getEnd();
                }

                if (!eol) {
                    readChunk.position(readChunk.position() + 1);
                }
            }

            // 检查新的行的第一个字符. 如果字符是 LWS, 那么它是一个多行 header

            // Read new bytes if needed
            if (readChunk == null || readChunk.position() >= readChunk.limit()) {
                if (readBytes() <0) {
                    throwEOFException(sm.getString("chunkedInputFilter.eosTrailer"));
                }
            }

            chr = readChunk.get(readChunk.position());
            if ((chr != Constants.SP) && (chr != Constants.HT)) {
                validLine = false;
            } else {
                eol = false;
                // 在缓冲区中复制一个额外的空格 (因为必须在行之间插入至少一个空格)
                trailingHeaders.append(chr);
            }

        }

        String headerName = new String(trailingHeaders.getBytes(), startPos,
                colonPos - startPos, StandardCharsets.ISO_8859_1);

        if (allowedTrailerHeaders.contains(headerName.toLowerCase(Locale.ENGLISH))) {
            MessageBytes headerValue = headers.addValue(headerName);

            // Set the header value
            headerValue.setBytes(trailingHeaders.getBytes(), colonPos,
                    lastSignificantChar - colonPos);
        }

        return true;
    }


    private void throwIOException(String msg) throws IOException {
        error = true;
        throw new IOException(msg);
    }


    private void throwEOFException(String msg) throws IOException {
        error = true;
        throw new EOFException(msg);
    }


    private void checkError() throws IOException {
        if (error) {
            throw new IOException(sm.getString("chunkedInputFilter.error"));
        }
    }


    @Override
    public void setByteBuffer(ByteBuffer buffer) {
        readChunk = buffer;
    }


    @Override
    public ByteBuffer getByteBuffer() {
        return readChunk;
    }


    @Override
    public void expand(int size) {
        // no-op
    }
}
