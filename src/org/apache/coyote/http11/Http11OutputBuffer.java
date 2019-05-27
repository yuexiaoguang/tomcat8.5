package org.apache.coyote.http11;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.coyote.ActionCode;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.coyote.http11.filters.GzipOutputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * 提供HTTP header (允许响应在被提交之前被重置) 和到写入header的Socket的链接 (一旦提交) 和响应主体的缓冲区.
 * 注意，响应主体的缓冲发生在更高的等级上.
 */
public class Http11OutputBuffer implements OutputBuffer {

    // -------------------------------------------------------------- Variables

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Http11OutputBuffer.class);


    /**
     * Logger.
     */
    private static final Log log = LogFactory.getLog(Http11OutputBuffer.class);


    // ----------------------------------------------------- Instance Variables

    /**
     * 关联的Coyote 响应.
     */
    protected Response response;


    /**
     * 结束标志.
     */
    protected boolean responseFinished;


    /**
     * 用于创建header 的缓冲区.
     */
    protected final ByteBuffer headerBuffer;


    /**
     * 用于处理响应主体的过滤器库.
     */
    protected OutputFilter[] filterLibrary;


    /**
     * 当前请求的激活的过滤器.
     */
    protected OutputFilter[] activeFilters;


    /**
     * 最后一个激活的过滤器的索引.
     */
    protected int lastActiveFilter;


    /**
     * 底层输出缓冲区.
     */
    protected OutputBuffer outputStreamOutputBuffer;


    /**
     * 将写入数据的socket的Wrapper.
     */
    protected SocketWrapperBase<?> socketWrapper;


    /**
     * 为当前请求写入客户端的字节
     */
    protected long byteCount = 0;


    @Deprecated
    private boolean sendReasonPhrase = false;


    protected Http11OutputBuffer(Response response, int headerBufferSize, boolean sendReasonPhrase) {

        this.response = response;
        this.sendReasonPhrase = sendReasonPhrase;

        headerBuffer = ByteBuffer.allocate(headerBufferSize);

        filterLibrary = new OutputFilter[0];
        activeFilters = new OutputFilter[0];
        lastActiveFilter = -1;

        responseFinished = false;

        outputStreamOutputBuffer = new SocketOutputBuffer();

        if (sendReasonPhrase) {
            // 导致加载HttpMessages
            HttpMessages.getInstance(response.getLocale()).getMessage(200);
        }
    }


    // ------------------------------------------------------------- Properties

    /**
     * 向过滤器库添加输出过滤器.
     * 注意，调用此方法将当前活动的过滤器重置为无.
     *
     * @param filter 要添加的过滤器
     */
    public void addFilter(OutputFilter filter) {

        OutputFilter[] newFilterLibrary = new OutputFilter[filterLibrary.length + 1];
        for (int i = 0; i < filterLibrary.length; i++) {
            newFilterLibrary[i] = filterLibrary[i];
        }
        newFilterLibrary[filterLibrary.length] = filter;
        filterLibrary = newFilterLibrary;

        activeFilters = new OutputFilter[filterLibrary.length];
    }


    /**
     * 获取过滤器.
     *
     * @return 包含所有可能的过滤器的当前过滤器库
     */
    public OutputFilter[] getFilters() {
        return filterLibrary;
    }


    /**
     * 为当前响应向活动的过滤器添加一个输出过滤器.
     * <p>
     * 过滤器没有必要存在于 {@link #getFilters()}.
     * <p>
     * 只能将一个过滤器添加到响应中. 如果过滤器已被添加到此响应, 那么这个方法将会是一个 NO-OP.
     *
     * @param filter 要添加的过滤器
     */
    public void addActiveFilter(OutputFilter filter) {

        if (lastActiveFilter == -1) {
            filter.setBuffer(outputStreamOutputBuffer);
        } else {
            for (int i = 0; i <= lastActiveFilter; i++) {
                if (activeFilters[i] == filter)
                    return;
            }
            filter.setBuffer(activeFilters[lastActiveFilter]);
        }

        activeFilters[++lastActiveFilter] = filter;

        filter.setResponse(response);
    }


    // --------------------------------------------------- OutputBuffer Methods

    /**
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doWrite(ByteBuffer)}
     */
    @Deprecated
    @Override
    public int doWrite(ByteChunk chunk) throws IOException {

        if (!response.isCommitted()) {
            // 向连接器发送提交请求. 连接器应该验证 headers, 发送它们 (使用 sendHeaders) 并设置相应的过滤器.
            response.action(ActionCode.COMMIT, null);
        }

        if (lastActiveFilter == -1) {
            return outputStreamOutputBuffer.doWrite(chunk);
        } else {
            return activeFilters[lastActiveFilter].doWrite(chunk);
        }
    }


    @Override
    public int doWrite(ByteBuffer chunk) throws IOException {

        if (!response.isCommitted()) {
            // 向连接器发送提交请求. 连接器应该验证 headers, 发送它们 (使用 sendHeaders) 并设置相应的过滤器.
            response.action(ActionCode.COMMIT, null);
        }

        if (lastActiveFilter == -1) {
            return outputStreamOutputBuffer.doWrite(chunk);
        } else {
            return activeFilters[lastActiveFilter].doWrite(chunk);
        }
    }


    @Override
    public long getBytesWritten() {
        if (lastActiveFilter == -1) {
            return outputStreamOutputBuffer.getBytesWritten();
        } else {
            return activeFilters[lastActiveFilter].getBytesWritten();
        }
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 刷新响应.
     *
     * @throws IOException 发生底层 I/O 错误
     */
    public void flush() throws IOException {
        // 通过过滤器, 如果有 gzip 过滤器, 执行它来刷新
        for (int i = 0; i <= lastActiveFilter; i++) {
            if (activeFilters[i] instanceof GzipOutputFilter) {
                if (log.isDebugEnabled()) {
                    log.debug("Flushing the gzip filter at position " + i +
                            " of the filter chain...");
                }
                ((GzipOutputFilter) activeFilters[i]).flush();
                break;
            }
        }

        // 刷新当前缓冲区
        flushBuffer(isBlocking());
    }


    /**
     * 重置 header 缓冲区, 如果在写入header期间发生错误, 因此可以写入错误响应.
     */
    void resetHeaderBuffer() {
        headerBuffer.position(0).limit(headerBuffer.capacity());
    }


    /**
     * 回收输出缓冲区. 这应该在关闭连接时调用.
     */
    public void recycle() {
        nextRequest();
        socketWrapper = null;
    }


    /**
     * 当前HTTP请求的结束处理.
     * Note: 当前请求的所有字节都应该已经消耗掉了. 此方法只重置所有指针，以便我们准备解析下一个HTTP请求.
     */
    public void nextRequest() {
        // 回收过滤器
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }
        // 回收响应对象
        response.recycle();
        // 重置指针
        headerBuffer.position(0).limit(headerBuffer.capacity());
        lastActiveFilter = -1;
        responseFinished = false;
        byteCount = 0;
    }


    /**
     * 完成写入响应.
     *
     * @throws IOException 发生底层 I/O 错误
     */
    public void finishResponse() throws IOException {
        if (responseFinished) {
            return;
        }

        if (lastActiveFilter != -1) {
            activeFilters[lastActiveFilter].end();
        }

        flushBuffer(true);

        responseFinished = true;
    }


    public void init(SocketWrapperBase<?> socketWrapper) {
        this.socketWrapper = socketWrapper;
    }


    @SuppressWarnings("deprecation")
    public void sendAck() throws IOException {
        if (!response.isCommitted()) {
            if (sendReasonPhrase) {
                socketWrapper.write(isBlocking(), Constants.ACK_BYTES_REASON, 0, Constants.ACK_BYTES_REASON.length);
            } else {
                socketWrapper.write(isBlocking(), Constants.ACK_BYTES, 0, Constants.ACK_BYTES.length);
            }
            if (flushBuffer(true)) {
                throw new IOException(sm.getString("iob.failedwrite.ack"));
            }
        }
    }


    /**
     * 提交响应.
     *
     * @throws IOException 发生底层 I/O 错误
     */
    protected void commit() throws IOException {
        response.setCommitted(true);

        if (headerBuffer.position() > 0) {
            // 发送响应 header 缓冲区
            headerBuffer.flip();
            try {
                socketWrapper.write(isBlocking(), headerBuffer);
            } finally {
                headerBuffer.position(0).limit(headerBuffer.capacity());
            }
        }
    }


    /**
     * 发送响应状态行.
     */
    @SuppressWarnings("deprecation")
    public void sendStatus() {
        // 写入协议名
        write(Constants.HTTP_11_BYTES);
        headerBuffer.put(Constants.SP);

        // 写入状态码
        int status = response.getStatus();
        switch (status) {
        case 200:
            write(Constants._200_BYTES);
            break;
        case 400:
            write(Constants._400_BYTES);
            break;
        case 404:
            write(Constants._404_BYTES);
            break;
        default:
            write(status);
        }

        headerBuffer.put(Constants.SP);

        if (sendReasonPhrase) {
            // Write message
            String message = null;
            if (org.apache.coyote.Constants.USE_CUSTOM_STATUS_MSG_IN_HEADER &&
                    HttpMessages.isSafeInHttpHeader(response.getMessage())) {
                message = response.getMessage();
            }
            if (message == null) {
                write(HttpMessages.getInstance(
                        response.getLocale()).getMessage(status));
            } else {
                write(message);
            }
        } else {
            // 原因短语是可选的，但它之前的空格不是. 跳过发送原因短语. 客户端应该忽略它 (RFC 7230), 它只是浪费字节.
        }

        headerBuffer.put(Constants.CR).put(Constants.LF);
    }


    /**
     * Send a header.
     *
     * @param name Header name
     * @param value Header value
     */
    public void sendHeader(MessageBytes name, MessageBytes value) {
        write(name);
        headerBuffer.put(Constants.COLON).put(Constants.SP);
        write(value);
        headerBuffer.put(Constants.CR).put(Constants.LF);
    }


    /**
     * 结束header 阻塞.
     */
    public void endHeaders() {
        headerBuffer.put(Constants.CR).put(Constants.LF);
    }


    /**
     * 此方法将指定消息字节缓冲区的内容写入输出流, 不包括过滤器. 此方法用于写入响应header.
     *
     * @param mb 待写入数据
     */
    private void write(MessageBytes mb) {
        if (mb.getType() != MessageBytes.T_BYTES) {
            mb.toBytes();
            ByteChunk bc = mb.getByteChunk();
            // 需要过滤掉不包括TAB的CTL. ISO-8859-1 和 UTF-8值是可以的. 使用其它编码的字符串可能会出错.
            byte[] buffer = bc.getBuffer();
            for (int i = bc.getOffset(); i < bc.getLength(); i++) {
            	// byte 值被标记 i.e. -128 到 127
                // 使用未签名的值. 0 到 31 是 CTL, 因此被过滤(TAB 位置是 9). 127 是一个控件 (DEL).
                // 128 到 255的值都是可以的. 转换这些到给定的签名的 -128 到 -1.
                if ((buffer[i] > -1 && buffer[i] <= 31 && buffer[i] != 9) ||
                        buffer[i] == 127) {
                    buffer[i] = ' ';
                }
            }
        }
        write(mb.getByteChunk());
    }


    /**
     * 此方法将指定字节块的内容写入输出流, 不包括过滤器. 此方法用于写入响应 header.
     *
     * @param bc 待写入数据
     */
    private void write(ByteChunk bc) {
        // 将字节块写入输出缓冲区
        int length = bc.getLength();
        checkLengthBeforeWrite(length);
        headerBuffer.put(bc.getBytes(), bc.getStart(), length);
    }


    /**
     * 此方法将指定字节缓冲区的内容写入输出流, 不包括过滤器. 此方法用于写入响应 header.
     *
     * @param b 待写入数据
     */
    public void write(byte[] b) {
        checkLengthBeforeWrite(b.length);

        // 将字节块写入输出缓冲区
        headerBuffer.put(b);
    }


    /**
     * 此方法将指定字符串的内容写入输出流, 不包括过滤器. 此方法用于写入响应 header.
     *
     * @param s 待写入数据
     */
    private void write(String s) {
        if (s == null) {
            return;
        }

        // From the Tomcat 3.3 HTTP/1.0 connector
        int len = s.length();
        checkLengthBeforeWrite(len);
        for (int i = 0; i < len; i++) {
            char c = s.charAt (i);
            // Note: 对于许多字符串来说, 这显然是不正确的, 但是是当前servlet框架内唯一一致的方法.
            // 直到servlet输出流正确地编码它们的输出为止，它就已经足够了.
            if (((c <= 31) && (c != 9)) || c == 127 || c > 255) {
                c = ' ';
            }
            headerBuffer.put((byte) c);
        }
    }


    /**
     * 此方法将指定的整数写入输出流. 此方法用于写入响应 header.
     *
     * @param value 待写入数据
     */
    private void write(int value) {
        // From the Tomcat 3.3 HTTP/1.0 connector
        String s = Integer.toString(value);
        int len = s.length();
        checkLengthBeforeWrite(len);
        for (int i = 0; i < len; i++) {
            char c = s.charAt (i);
            headerBuffer.put((byte) c);
        }
    }


    /**
     * 检查缓冲区中是否有足够的空间写入请求的字节数.
     */
    private void checkLengthBeforeWrite(int length) {
        // "+ 4": BZ 57509. 为 CR/LF/COLON/SP 字符保留空间, 在写操作之后直接放入缓冲区.
        if (headerBuffer.position() + length + 4 > headerBuffer.capacity()) {
            throw new HeadersTooLargeException(
                    sm.getString("iob.responseheadertoolarge.error"));
        }
    }


    //------------------------------------------------------ Non-blocking writes

    /**
     * 写入剩余的缓冲数据.
     *
     * @param block     此方法是否阻塞, 直到缓冲区为空
     * @return  <code>true</code>如果数据保留在缓冲区中 (只能在非阻塞模式下发生)
     * 			<code>false</code>.
     * @throws IOException 写入数据错误
     */
    protected boolean flushBuffer(boolean block) throws IOException  {
        return socketWrapper.flush(block);
    }


    /**
     * 是否使用标准servlet阻塞IO进行输出?
     * @return <code>true</code> 如果是阻塞 IO
     */
    protected final boolean isBlocking() {
        return response.getWriteListener() == null;
    }


    protected final boolean isReady() {
        boolean result = !hasDataToWrite();
        if (!result) {
            socketWrapper.registerWriteInterest();
        }
        return result;
    }


    public boolean hasDataToWrite() {
        return socketWrapper.hasDataToWrite();
    }


    public void registerWriteInterest() {
        socketWrapper.registerWriteInterest();
    }


    // ------------------------------------------ SocketOutputBuffer Inner Class

    /**
     * 该类是将数据写入socket的输出缓冲区.
     */
    protected class SocketOutputBuffer implements OutputBuffer {

        /**
         * 写入块.
         *
         * @deprecated Unused. Will be removed in Tomcat 9. Use
         *             {@link #doWrite(ByteBuffer)}
         */
        @Deprecated
        @Override
        public int doWrite(ByteChunk chunk) throws IOException {
            int len = chunk.getLength();
            int start = chunk.getStart();
            byte[] b = chunk.getBuffer();
            socketWrapper.write(isBlocking(), b, start, len);
            byteCount += len;
            return len;
        }

        /**
         * 写入块.
         */
        @Override
        public int doWrite(ByteBuffer chunk) throws IOException {
            try {
                int len = chunk.remaining();
                socketWrapper.write(isBlocking(), chunk);
                len -= chunk.remaining();
                byteCount += len;
                return len;
            } catch (IOException ioe) {
                response.action(ActionCode.CLOSE_NOW, ioe);
                // Re-throw
                throw ioe;
            }
        }

        @Override
        public long getBytesWritten() {
            return byteCount;
        }
    }
}
