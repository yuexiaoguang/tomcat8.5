package org.apache.coyote.http11;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.parser.HttpParser;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * 提供解析请求header和转换编码的HTTP的InputBuffer.
 */
public class Http11InputBuffer implements InputBuffer, ApplicationBufferHandler {

    // -------------------------------------------------------------- Constants

    private static final Log log = LogFactory.getLog(Http11InputBuffer.class);

    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Http11InputBuffer.class);


    private static final byte[] CLIENT_PREFACE_START =
            "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

    /**
     * 关联的 Coyote 请求.
     */
    private final Request request;


    /**
     * 关联的请求的Header.
     */
    private final MimeHeaders headers;


    private final boolean rejectIllegalHeaderName;

    /**
     * State.
     */
    private boolean parsingHeader;


    /**
     * 忽略输入 ? (在异常情况下)
     */
    private boolean swallowInput;


    /**
     * 读取缓冲区.
     */
    private ByteBuffer byteBuffer;


    /**
     * 缓冲区中header结尾的位置, 也是主体的开始位置.
     */
    private int end;


    /**
     * 提供访问底层socket的Wrapper.
     */
    private SocketWrapperBase<?> wrapper;


    /**
     * 底层输入缓冲区.
     */
    private InputBuffer inputStreamInputBuffer;


    /**
     * 过滤器库.
     * Note: Filter[Constants.CHUNKED_FILTER] 总是 "chunked" 过滤器.
     */
    private InputFilter[] filterLibrary;


    /**
     * 激活的过滤器 (有序).
     */
    private InputFilter[] activeFilters;


    /**
     * 最后一个激活的过滤器的索引.
     */
    private int lastActiveFilter;


    /**
     * 解析状态 - 用于非阻塞解析，以便在更多数据到达时使用, 可以在离开的地方捡起.
     */
    private boolean parsingRequestLine;
    private int parsingRequestLinePhase = 0;
    private boolean parsingRequestLineEol = false;
    private int parsingRequestLineStart = 0;
    private int parsingRequestLineQPos = -1;
    private HeaderParsePosition headerParsePos;
    private final HeaderParseData headerData = new HeaderParseData();

    /**
     * HTTP请求行的最大允许大小, 包括header和空白行.
     */
    private final int headerBufferSize;

    /**
     * NioChannel 读取缓冲区的已知大小.
     */
    private int socketReadBufferSize;


    // ----------------------------------------------------------- Constructors

    public Http11InputBuffer(Request request, int headerBufferSize,
            boolean rejectIllegalHeaderName) {

        this.request = request;
        headers = request.getMimeHeaders();

        this.headerBufferSize = headerBufferSize;
        this.rejectIllegalHeaderName = rejectIllegalHeaderName;

        filterLibrary = new InputFilter[0];
        activeFilters = new InputFilter[0];
        lastActiveFilter = -1;

        parsingHeader = true;
        parsingRequestLine = true;
        parsingRequestLinePhase = 0;
        parsingRequestLineEol = false;
        parsingRequestLineStart = 0;
        parsingRequestLineQPos = -1;
        headerParsePos = HeaderParsePosition.HEADER_START;
        swallowInput = true;

        inputStreamInputBuffer = new SocketInputBuffer();
    }


    // ------------------------------------------------------------- Properties

    /**
     * 向过滤器库添加输入过滤器.
     *
     * @throws NullPointerException 如果提供的过滤器是 null
     */
    void addFilter(InputFilter filter) {

        if (filter == null) {
            throw new NullPointerException(sm.getString("iib.filter.npe"));
        }

        InputFilter[] newFilterLibrary = new InputFilter[filterLibrary.length + 1];
        for (int i = 0; i < filterLibrary.length; i++) {
            newFilterLibrary[i] = filterLibrary[i];
        }
        newFilterLibrary[filterLibrary.length] = filter;
        filterLibrary = newFilterLibrary;

        activeFilters = new InputFilter[filterLibrary.length];
    }


    InputFilter[] getFilters() {
        return filterLibrary;
    }


    /**
     * 向过滤器库添加输入过滤器.
     */
    void addActiveFilter(InputFilter filter) {

        if (lastActiveFilter == -1) {
            filter.setBuffer(inputStreamInputBuffer);
        } else {
            for (int i = 0; i <= lastActiveFilter; i++) {
                if (activeFilters[i] == filter)
                    return;
            }
            filter.setBuffer(activeFilters[lastActiveFilter]);
        }

        activeFilters[++lastActiveFilter] = filter;

        filter.setRequest(request);
    }


    /**
     * 设置忽略输入标志.
     */
    void setSwallowInput(boolean swallowInput) {
        this.swallowInput = swallowInput;
    }


    // ---------------------------------------------------- InputBuffer Methods

    /**
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doRead(ApplicationBufferHandler)}
     */
    @Deprecated
    @Override
    public int doRead(ByteChunk chunk) throws IOException {

        if (lastActiveFilter == -1)
            return inputStreamInputBuffer.doRead(chunk);
        else
            return activeFilters[lastActiveFilter].doRead(chunk);

    }

    @Override
    public int doRead(ApplicationBufferHandler handler) throws IOException {

        if (lastActiveFilter == -1)
            return inputStreamInputBuffer.doRead(handler);
        else
            return activeFilters[lastActiveFilter].doRead(handler);

    }


    // ------------------------------------------------------- Protected Methods

    /**
     * 回收输入缓冲区. 应该在关闭连接时调用.
     */
    void recycle() {
        wrapper = null;
        request.recycle();

        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }

        byteBuffer.limit(0).position(0);
        lastActiveFilter = -1;
        parsingHeader = true;
        swallowInput = true;

        headerParsePos = HeaderParsePosition.HEADER_START;
        parsingRequestLine = true;
        parsingRequestLinePhase = 0;
        parsingRequestLineEol = false;
        parsingRequestLineStart = 0;
        parsingRequestLineQPos = -1;
        headerData.recycle();
    }


    /**
     * 当前HTTP请求的结束处理.
     * Note: 当前请求的所有字节都应该消耗掉了. 此方法只重置所有指针，以便准备解析下一个HTTP请求.
     */
    void nextRequest() {
        request.recycle();

        if (byteBuffer.position() > 0) {
            if (byteBuffer.remaining() > 0) {
                // 将剩余字节复制到缓冲区的开头
                byteBuffer.compact();
                byteBuffer.flip();
            } else {
                // 重置位置并限制为0
                byteBuffer.position(0).limit(0);
            }
        }

        // 回收过滤器
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }

        // 重置指针
        lastActiveFilter = -1;
        parsingHeader = true;
        swallowInput = true;

        headerParsePos = HeaderParsePosition.HEADER_START;
        parsingRequestLine = true;
        parsingRequestLinePhase = 0;
        parsingRequestLineEol = false;
        parsingRequestLineStart = 0;
        parsingRequestLineQPos = -1;
        headerData.recycle();
    }


    /**
     * 读取请求行. 这个函数是在HTTP请求报头解析期间使用的. 不要尝试使用它读取请求主体.
     *
     * @throws IOException  如果在底层socket 读取操作期间发生异常, 或者如果给定的缓冲区不够大，不能容纳整行.
     * @return true 如果数据被正确地发送; false 如果没有数据立即可用，线程应该被释放
     */
    boolean parseRequestLine(boolean keptAlive) throws IOException {

        // 检查状态
        if (!parsingRequestLine) {
            return true;
        }
        //
        // 跳过空白行
        //
        if (parsingRequestLinePhase < 2) {
            byte chr = 0;
            do {

                // 读取新字节
                if (byteBuffer.position() >= byteBuffer.limit()) {
                    if (keptAlive) {
                        // 还没有读取任何请求数据, 因此使用 keep-alive 超时.
                        wrapper.setReadTimeout(wrapper.getEndpoint().getKeepAliveTimeout());
                    }
                    if (!fill(false)) {
                        // 正在等待读取, 所以不再处于初始状态
                        parsingRequestLinePhase = 1;
                        return false;
                    }
                    // 请求的至少一个字节已被接收.
                    // 切换到套接字超时.
                    wrapper.setReadTimeout(wrapper.getEndpoint().getConnectionTimeout());
                }
                if (!keptAlive && byteBuffer.position() == 0 && byteBuffer.limit() >= CLIENT_PREFACE_START.length - 1) {
                    boolean prefaceMatch = true;
                    for (int i = 0; i < CLIENT_PREFACE_START.length && prefaceMatch; i++) {
                        if (CLIENT_PREFACE_START[i] != byteBuffer.get(i)) {
                            prefaceMatch = false;
                        }
                    }
                    if (prefaceMatch) {
                        // HTTP/2 序言匹配
                        parsingRequestLinePhase = -1;
                        return false;
                    }
                }
                // 设置开始时间，一旦开始读取数据 (即使只是跳过空白行)
                if (request.getStartTime() < 0) {
                    request.setStartTime(System.currentTimeMillis());
                }
                chr = byteBuffer.get();
            } while ((chr == Constants.CR) || (chr == Constants.LF));
            byteBuffer.position(byteBuffer.position() - 1);

            parsingRequestLineStart = byteBuffer.position();
            parsingRequestLinePhase = 2;
            if (log.isDebugEnabled()) {
                log.debug("Received ["
                        + new String(byteBuffer.array(), byteBuffer.position(), byteBuffer.remaining(), StandardCharsets.ISO_8859_1) + "]");
            }
        }
        if (parsingRequestLinePhase == 2) {
            //
            // 读取方法名称
            // 方法名称是 token
            //
            boolean space = false;
            while (!space) {
                // 读取新字节
                if (byteBuffer.position() >= byteBuffer.limit()) {
                    if (!fill(false)) // 读取行解析
                        return false;
                }
                // 规范表示方法名称是一个token，后面是一个SP，但也可以容忍多个SP和HT.
                int pos = byteBuffer.position();
                byte chr = byteBuffer.get();
                if (chr == Constants.SP || chr == Constants.HT) {
                    space = true;
                    request.method().setBytes(byteBuffer.array(), parsingRequestLineStart,
                            pos - parsingRequestLineStart);
                } else if (!HttpParser.isToken(chr)) {
                    byteBuffer.position(byteBuffer.position() - 1);
                    throw new IllegalArgumentException(sm.getString("iib.invalidmethod"));
                }
            }
            parsingRequestLinePhase = 3;
        }
        if (parsingRequestLinePhase == 3) {
            // 规范表示单个SP，但也可以容忍多个SP和HT
            boolean space = true;
            while (space) {
                // 读取新字节
                if (byteBuffer.position() >= byteBuffer.limit()) {
                    if (!fill(false)) // 读取行解析
                        return false;
                }
                byte chr = byteBuffer.get();
                if (!(chr == Constants.SP || chr == Constants.HT)) {
                    space = false;
                    byteBuffer.position(byteBuffer.position() - 1);
                }
            }
            parsingRequestLineStart = byteBuffer.position();
            parsingRequestLinePhase = 4;
        }
        if (parsingRequestLinePhase == 4) {
            // 标记当前缓冲区位置

            int end = 0;
            //
            // 读取 URI
            //
            boolean space = false;
            while (!space) {
                // 读取新字节
                if (byteBuffer.position() >= byteBuffer.limit()) {
                    if (!fill(false)) // 读取行解析
                        return false;
                }
                int pos = byteBuffer.position();
                byte chr = byteBuffer.get();
                if (chr == Constants.SP || chr == Constants.HT) {
                    space = true;
                    end = pos;
                } else if (chr == Constants.CR || chr == Constants.LF) {
                    // HTTP/0.9 风格请求
                    parsingRequestLineEol = true;
                    space = true;
                    end = pos;
                } else if (chr == Constants.QUESTION && parsingRequestLineQPos == -1) {
                    parsingRequestLineQPos = pos;
                } else if (HttpParser.isNotRequestTarget(chr)) {
                    throw new IllegalArgumentException(sm.getString("iib.invalidRequestTarget"));
                }
            }
            if (parsingRequestLineQPos >= 0) {
                request.queryString().setBytes(byteBuffer.array(), parsingRequestLineQPos + 1,
                        end - parsingRequestLineQPos - 1);
                request.requestURI().setBytes(byteBuffer.array(), parsingRequestLineStart,
                        parsingRequestLineQPos - parsingRequestLineStart);
            } else {
                request.requestURI().setBytes(byteBuffer.array(), parsingRequestLineStart,
                        end - parsingRequestLineStart);
            }
            parsingRequestLinePhase = 5;
        }
        if (parsingRequestLinePhase == 5) {
            // 规范表示单个SP，但也可以容忍多个SP和HT
            boolean space = true;
            while (space) {
                // 读取新字节
                if (byteBuffer.position() >= byteBuffer.limit()) {
                    if (!fill(false)) // 读取行解析
                        return false;
                }
                byte chr = byteBuffer.get();
                if (!(chr == Constants.SP || chr == Constants.HT)) {
                    space = false;
                    byteBuffer.position(byteBuffer.position() - 1);
                }
            }
            parsingRequestLineStart = byteBuffer.position();
            parsingRequestLinePhase = 6;

            // 标记当前缓冲位置
            end = 0;
        }
        if (parsingRequestLinePhase == 6) {
            //
            // 读取协议
            // 协议总是 "HTTP/" DIGIT "." DIGIT
            //
            while (!parsingRequestLineEol) {
                // 读取新字节
                if (byteBuffer.position() >= byteBuffer.limit()) {
                    if (!fill(false)) // 读取行解析
                        return false;
                }

                int pos = byteBuffer.position();
                byte chr = byteBuffer.get();
                if (chr == Constants.CR) {
                    end = pos;
                } else if (chr == Constants.LF) {
                    if (end == 0) {
                        end = pos;
                    }
                    parsingRequestLineEol = true;
                } else if (!HttpParser.isHttpProtocol(chr)) {
                    throw new IllegalArgumentException(sm.getString("iib.invalidHttpProtocol"));
                }
            }

            if ((end - parsingRequestLineStart) > 0) {
                request.protocol().setBytes(byteBuffer.array(), parsingRequestLineStart,
                        end - parsingRequestLineStart);
            } else {
                request.protocol().setString("");
            }
            parsingRequestLine = false;
            parsingRequestLinePhase = 0;
            parsingRequestLineEol = false;
            parsingRequestLineStart = 0;
            return true;
        }
        throw new IllegalStateException(
                "Invalid request line parse phase:" + parsingRequestLinePhase);
    }


    /**
     * 解析 HTTP header.
     */
    boolean parseHeaders() throws IOException {
        if (!parsingHeader) {
            throw new IllegalStateException(sm.getString("iib.parseheaders.ise.error"));
        }

        HeaderParseStatus status = HeaderParseStatus.HAVE_MORE_HEADERS;

        do {
            status = parseHeader();
            // 检查
            // (1) Header加上请求行大小不超过其限制
            // (2) 当读取正文时，有足够的字节来避免扩展缓冲区
            // 技术上, (2) 是技术限制, (1) 是逻辑限制来强制 headerBufferSize
            // 从分配缓冲区的方式以及如何读取空白行, 足够只检查 (1).
            if (byteBuffer.position() > headerBufferSize || byteBuffer.capacity() - byteBuffer.position() < socketReadBufferSize) {
                throw new IllegalArgumentException(sm.getString("iib.requestheadertoolarge.error"));
            }
        } while (status == HeaderParseStatus.HAVE_MORE_HEADERS);
        if (status == HeaderParseStatus.DONE) {
            parsingHeader = false;
            end = byteBuffer.position();
            return true;
        } else {
            return false;
        }
    }


    int getParsingRequestLinePhase() {
        return parsingRequestLinePhase;
    }


    /**
     * 结束请求 (消耗剩余字节).
     *
     * @throws IOException 发生了一个底层I/O错误
     */
    void endRequest() throws IOException {

        if (swallowInput && (lastActiveFilter != -1)) {
            int extraBytes = (int) activeFilters[lastActiveFilter].end();
            byteBuffer.position(byteBuffer.position() - extraBytes);
        }
    }


    /**
     * 缓冲区中可用字节 (注意，由于编码, 可能不对应).
     */
    int available(boolean read) {
        int available = byteBuffer.remaining();
        if ((available == 0) && (lastActiveFilter >= 0)) {
            for (int i = 0; (available == 0) && (i <= lastActiveFilter); i++) {
                available = activeFilters[i].available();
            }
        }
        if (available > 0 || !read) {
            return available;
        }

        try {
            fill(false);
            available = byteBuffer.remaining();
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("iib.available.readFail"), ioe);
            }
            // Not ideal. 表示数据可用, 触发一个读取, 并且将触发另一个 IOException.
            available = 1;
        }
        return available;
    }


    /**
     * 是否已读取所有请求主体? this 和 available() &gt; 0 有细微的差别, 因为必须用阻塞IO连接器来处理假的非阻塞读取.
     */
    boolean isFinished() {
        if (byteBuffer.limit() > byteBuffer.position()) {
            // 在缓冲区中读取的数据未完成
            return false;
        }

        /*
         * 不要在这里使用 fill(false), 因为在下面的情况下，BIO会阻塞 - 可能无限期地
         * - 客户端使用 keep-alive 而且连接仍然打开
         * - 客户端已经发送完整的请求
         * - 客户端没有发送下一个请求 (i.e. no pipelining)
         * - 应用程序已读取完整的请求
         */

        // 检查 InputFilters

        if (lastActiveFilter >= 0) {
            return activeFilters[lastActiveFilter].isFinished();
        } else {
            // No filters. 假设请求未完成. EOF 将表示请求结束.
            return false;
        }
    }

    ByteBuffer getLeftover() {
        int available = byteBuffer.remaining();
        if (available > 0) {
            return ByteBuffer.wrap(byteBuffer.array(), byteBuffer.position(), available);
        } else {
            return null;
        }
    }


    void init(SocketWrapperBase<?> socketWrapper) {

        wrapper = socketWrapper;
        wrapper.setAppReadBufHandler(this);

        int bufLength = headerBufferSize +
                wrapper.getSocketBufferHandler().getReadBuffer().capacity();
        if (byteBuffer == null || byteBuffer.capacity() < bufLength) {
            byteBuffer = ByteBuffer.allocate(bufLength);
            byteBuffer.position(0).limit(0);
        }
    }



    // --------------------------------------------------------- Private Methods

    /**
     * 尝试将一些数据读入输入缓冲区.
     *
     * @return <code>true</code> 如果在输入缓冲器中添加更多数据
     *         否则 <code>false</code>
     */
    private boolean fill(boolean block) throws IOException {

        if (parsingHeader) {
            if (byteBuffer.limit() >= headerBufferSize) {
                throw new IllegalArgumentException(sm.getString("iib.requestheadertoolarge.error"));
            }
        } else {
            byteBuffer.limit(end).position(end);
        }

        byteBuffer.mark();
        if (byteBuffer.position() < byteBuffer.limit()) {
            byteBuffer.position(byteBuffer.limit());
        }
        byteBuffer.limit(byteBuffer.capacity());
        int nRead = wrapper.read(block, byteBuffer);
        byteBuffer.limit(byteBuffer.position()).reset();
        if (nRead > 0) {
            return true;
        } else if (nRead == -1) {
            throw new EOFException(sm.getString("iib.eof.error"));
        } else {
            return false;
        }

    }


    /**
     * 解析一个 HTTP header.
     *
     * @return false 读取空白行之后 (表示解析HTTP header已经完成
     */
    private HeaderParseStatus parseHeader() throws IOException {

        //
        // 检查空白行
        //

        byte chr = 0;
        while (headerParsePos == HeaderParsePosition.HEADER_START) {

            // 读取新字节
            if (byteBuffer.position() >= byteBuffer.limit()) {
                if (!fill(false)) {// 解析 header
                    headerParsePos = HeaderParsePosition.HEADER_START;
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            chr = byteBuffer.get();

            if (chr == Constants.CR) {
                // Skip
            } else if (chr == Constants.LF) {
                return HeaderParseStatus.DONE;
            } else {
                byteBuffer.position(byteBuffer.position() - 1);
                break;
            }

        }

        if (headerParsePos == HeaderParsePosition.HEADER_START) {
            // 标记当前缓冲区位置
            headerData.start = byteBuffer.position();
            headerParsePos = HeaderParsePosition.HEADER_NAME;
        }

        //
        // 读取 header 名称
        // Header 名称总是 US-ASCII
        //

        while (headerParsePos == HeaderParsePosition.HEADER_NAME) {

            // 读取新字节
            if (byteBuffer.position() >= byteBuffer.limit()) {
                if (!fill(false)) { // 解析 header
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            int pos = byteBuffer.position();
            chr = byteBuffer.get();
            if (chr == Constants.COLON) {
                headerParsePos = HeaderParsePosition.HEADER_VALUE_START;
                headerData.headerValue = headers.addValue(byteBuffer.array(), headerData.start,
                        pos - headerData.start);
                pos = byteBuffer.position();
                // 标记当前缓冲区位置
                headerData.start = pos;
                headerData.realPos = pos;
                headerData.lastSignificantChar = pos;
                break;
            } else if (!HttpParser.isToken(chr)) {
                // Non-token 字符在header名称中是非法的
                // 解析继续进行，因此可以在上下文中报告错误
                headerData.lastSignificantChar = pos;
                byteBuffer.position(byteBuffer.position() - 1);
                // skipLine() 将处理错误
                return skipLine();
            }

            // chr 是header 名称的下一个字节. 转换为小写.
            if ((chr >= Constants.A) && (chr <= Constants.Z)) {
                byteBuffer.put(pos, (byte) (chr - Constants.LC_OFFSET));
            }
        }

        // 跳过行并忽略 header
        if (headerParsePos == HeaderParsePosition.HEADER_SKIPLINE) {
            return skipLine();
        }

        //
        // 读取 header 值 (可以跨越多条线)
        //

        while (headerParsePos == HeaderParsePosition.HEADER_VALUE_START ||
               headerParsePos == HeaderParsePosition.HEADER_VALUE ||
               headerParsePos == HeaderParsePosition.HEADER_MULTI_LINE) {

            if (headerParsePos == HeaderParsePosition.HEADER_VALUE_START) {
                // 跳过空格
                while (true) {
                    // 读取新字节
                    if (byteBuffer.position() >= byteBuffer.limit()) {
                        if (!fill(false)) {// 解析 header
                            // HEADER_VALUE_START
                            return HeaderParseStatus.NEED_MORE_DATA;
                        }
                    }

                    chr = byteBuffer.get();
                    if (!(chr == Constants.SP || chr == Constants.HT)) {
                        headerParsePos = HeaderParsePosition.HEADER_VALUE;
                        byteBuffer.position(byteBuffer.position() - 1);
                        break;
                    }
                }
            }
            if (headerParsePos == HeaderParsePosition.HEADER_VALUE) {

                // 读取字节直到行结束
                boolean eol = false;
                while (!eol) {

                    // 读取新字节
                    if (byteBuffer.position() >= byteBuffer.limit()) {
                        if (!fill(false)) {// 解析 header
                            // HEADER_VALUE
                            return HeaderParseStatus.NEED_MORE_DATA;
                        }
                    }

                    chr = byteBuffer.get();
                    if (chr == Constants.CR) {
                        // Skip
                    } else if (chr == Constants.LF) {
                        eol = true;
                    } else if (chr == Constants.SP || chr == Constants.HT) {
                        byteBuffer.put(headerData.realPos, chr);
                        headerData.realPos++;
                    } else {
                        byteBuffer.put(headerData.realPos, chr);
                        headerData.realPos++;
                        headerData.lastSignificantChar = headerData.realPos;
                    }
                }

                // 忽略行末尾的空格
                headerData.realPos = headerData.lastSignificantChar;

                // 检查新行的第一个字符. 如果字符是一个 LWS, 然后是多行 header
                headerParsePos = HeaderParsePosition.HEADER_MULTI_LINE;
            }
            // 读取新字节
            if (byteBuffer.position() >= byteBuffer.limit()) {
                if (!fill(false)) {// 解析 header
                    // HEADER_MULTI_LINE
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            chr = byteBuffer.get(byteBuffer.position());
            if (headerParsePos == HeaderParsePosition.HEADER_MULTI_LINE) {
                if ((chr != Constants.SP) && (chr != Constants.HT)) {
                    headerParsePos = HeaderParsePosition.HEADER_START;
                    break;
                } else {
                    // 在缓冲区中复制一个额外的空格 (因为必须在行之间插入至少一个空格)
                    byteBuffer.put(headerData.realPos, chr);
                    headerData.realPos++;
                    headerParsePos = HeaderParsePosition.HEADER_VALUE_START;
                }
            }
        }
        // 设置 header 值
        headerData.headerValue.setBytes(byteBuffer.array(), headerData.start,
                headerData.lastSignificantChar - headerData.start);
        headerData.recycle();
        return HeaderParseStatus.HAVE_MORE_HEADERS;
    }


    private HeaderParseStatus skipLine() throws IOException {
        headerParsePos = HeaderParsePosition.HEADER_SKIPLINE;
        boolean eol = false;

        // 读取字节直到行结束
        while (!eol) {

            // 读取新字节
            if (byteBuffer.position() >= byteBuffer.limit()) {
                if (!fill(false)) {
                    return HeaderParseStatus.NEED_MORE_DATA;
                }
            }

            int pos = byteBuffer.position();
            byte chr = byteBuffer.get();
            if (chr == Constants.CR) {
                // Skip
            } else if (chr == Constants.LF) {
                eol = true;
            } else {
                headerData.lastSignificantChar = pos;
            }
        }
        if (rejectIllegalHeaderName || log.isDebugEnabled()) {
            String message = sm.getString("iib.invalidheader",
                    new String(byteBuffer.array(), headerData.start,
                            headerData.lastSignificantChar - headerData.start + 1,
                            StandardCharsets.ISO_8859_1));
            if (rejectIllegalHeaderName) {
                throw new IllegalArgumentException(message);
            }
            log.debug(message);
        }

        headerParsePos = HeaderParsePosition.HEADER_START;
        return HeaderParseStatus.HAVE_MORE_HEADERS;
    }


    // ----------------------------------------------------------- Inner classes

    private static enum HeaderParseStatus {
        DONE, HAVE_MORE_HEADERS, NEED_MORE_DATA
    }


    private static enum HeaderParsePosition {
        /**
         * 开始一个新 header. 如果这里是 CRLF, 意味着没有别的header. 任何其他字符都可以启动一个 header 名称.
         */
        HEADER_START,
        /**
         * 读取一个 header 名称. header 的所有字符都是 HTTP_TOKEN_CHAR.
         * Header名称后面紧跟着 ':'. 不允许空格.<br>
         * 如果遇到任何非HTTP_TOKEN_CHAR (包括空格)在 ':'之前, 整行将被忽略.
         */
        HEADER_NAME,
        /**
         * 在header值开始之前跳过空格, 无论是在header 值的第一行上 (在 ':' 后面) 还是在行后面, 当已知后续行以SP或HT开头时.
         */
        HEADER_VALUE_START,
        /**
         * 读取 header 值. 要么在第一行上，要么在任何后续行上. 从HEADER_VALUE_START到达这个状态, 在行上遇到第一个non-SP/non-HT字节之后.
         */
        HEADER_VALUE,
        /**
         * 读取一个header的新行之前. 一旦下一个字节被看到, 状态改变, 但不改变位置.
         * 状态变为 HEADER_VALUE_START (如果第一个字节是 SP 或 HT), 或HEADER_START (否则).
         */
        HEADER_MULTI_LINE,
        /**
         * 读取所有字节直到下一个CRLF. 行被忽略.
         */
        HEADER_SKIPLINE
    }


    private static class HeaderParseData {
        /**
         * 当解析header 名称时: header的第一个字符.<br>
         * 当跳过损坏的 header 行时: header的第一个字符.<br>
         * 当解析 header 值时: ':'后面的第一个字符.
         */
        int start = 0;
        /**
         * 当解析header 名称时: 不使用 (一直为 0).<br>
         * 当跳过损坏的 header 行时: 不使用 (一直为 0).<br>
         * 当解析 header 值时: ':'后面的第一个字符.
         * 然后逐渐增加, 让header的更多字节被处理.
         * 来自buf[pos]的字节被复制到 buf[realPos]. 加上从[start] 到 [realPos-1]的字符串作为 header 的值, 并删除空格.<br>
         */
        int realPos = 0;
        /**
         * 当解析header 名称时: 不使用 (一直为 0).<br>
         * 当跳过损坏的 header 行时: 最后一个 non-CR/non-LF 字符.<br>
         * 当解析 header 值时: 最后一个 not-LWS 字符后的位置.<br>
         */
        int lastSignificantChar = 0;
        /**
         * 保存header的值的MB. 它是 null, 在解析 header 名称期间, 并在名称解析之后创建.
         */
        MessageBytes headerValue = null;
        public void recycle() {
            start = 0;
            realPos = 0;
            lastSignificantChar = 0;
            headerValue = null;
        }
    }


    // ------------------------------------- InputStreamInputBuffer Inner Class

    /**
     * 这个类是一个输入缓冲区，它将从一个输入流读取它的数据.
     */
    private class SocketInputBuffer implements InputBuffer {

        /**
         *
         * @deprecated Unused. Will be removed in Tomcat 9. Use
         *             {@link #doRead(ApplicationBufferHandler)}
         */
        @Deprecated
        @Override
        public int doRead(ByteChunk chunk) throws IOException {

            if (byteBuffer.position() >= byteBuffer.limit()) {
                // 应用程序正在读取始终是阻塞操作的HTTP请求主体.
                if (!fill(true))
                    return -1;
            }

            int length = byteBuffer.remaining();
            chunk.setBytes(byteBuffer.array(), byteBuffer.position(), length);
            byteBuffer.position(byteBuffer.limit());

            return length;
        }

        @Override
        public int doRead(ApplicationBufferHandler handler) throws IOException {

            if (byteBuffer.position() >= byteBuffer.limit()) {
                // 应用程序正在读取始终是阻塞操作的HTTP请求主体.
                if (!fill(true))
                    return -1;
            }

            int length = byteBuffer.remaining();
            handler.setByteBuffer(byteBuffer.duplicate());
            byteBuffer.position(byteBuffer.limit());

            return length;
        }
    }


    @Override
    public void setByteBuffer(ByteBuffer buffer) {
        byteBuffer = buffer;
    }


    @Override
    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }


    @Override
    public void expand(int size) {
        if (byteBuffer.capacity() >= size) {
            byteBuffer.limit(size);
        }
        ByteBuffer temp = ByteBuffer.allocate(size);
        temp.put(byteBuffer);
        byteBuffer = temp;
        byteBuffer.mark();
        temp = null;
    }
}
