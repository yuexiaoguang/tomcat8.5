package org.apache.tomcat.util.http.fileupload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import org.apache.tomcat.util.http.fileupload.FileUploadBase.FileUploadIOException;
import org.apache.tomcat.util.http.fileupload.util.Closeable;
import org.apache.tomcat.util.http.fileupload.util.Streams;


/**
 * <p>用于处理文件上传的低级API.
 *
 * <p>此类可用于处理符合<a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>中定义的MIME “multipart”格式的数据流.
 * 在恒定的内存使用情况下，可以处理流中的任意大量数据.
 *
 * <p>流的格式按以下方式定义:<br>
 *
 * <code>
 *   multipart-body := preamble 1*encapsulation close-delimiter epilogue<br>
 *   encapsulation := delimiter body CRLF<br>
 *   delimiter := "--" boundary CRLF<br>
 *   close-delimiter := "--" boundary "--"<br>
 *   preamble := &lt;ignore&gt;<br>
 *   epilogue := &lt;ignore&gt;<br>
 *   body := header-part CRLF body-part<br>
 *   header-part := 1*header CRLF<br>
 *   header := header-name ":" header-value<br>
 *   header-name := &lt;printable ascii characters except ":"&gt;<br>
 *   header-value := &lt;any ascii characters except CR &amp; LF&gt;<br>
 *   body-data := &lt;arbitrary data&gt;<br>
 * </code>
 *
 * <p>请注意，body-data可以包含另一个multipart实体. 对这种嵌套流的单遍处理的支持有限.
 * 嵌套流需要具有与父流相同长度的边界标记 (see {@link #setBoundary(byte[])}).
 *
 * <p>以下是此类的使用示例.<br>
 *
 * <pre>
 *   try {
 *     MultipartStream multipartStream = new MultipartStream(input, boundary);
 *     boolean nextPart = multipartStream.skipPreamble();
 *     OutputStream output;
 *     while(nextPart) {
 *       String header = multipartStream.readHeaders();
 *       // process headers
 *       // create some output stream
 *       multipartStream.readBodyData(output);
 *       nextPart = multipartStream.readBoundary();
 *     }
 *   } catch(MultipartStream.MalformedStreamException e) {
 *     // the stream failed to follow required syntax
 *   } catch(IOException e) {
 *     // a read or write error occurred
 *   }
 * </pre>
 */
public class MultipartStream {

    /**
     * 用于调用{@link ProgressListener}.
     */
    public static class ProgressNotifier {
        /**
         * 要调用的监听器.
         */
        private final ProgressListener listener;

        /**
         * 预期字节数, 或 -1.
         */
        private final long contentLength;

        /**
         * 到目前为止已读取的字节数.
         */
        private long bytesRead;

        /**
         * 到目前为止已读取的项目数.
         */
        private int items;

        /**
         * @param pListener 要调用的监听器.
         * @param pContentLength 预期的内容长度.
         */
        ProgressNotifier(ProgressListener pListener, long pContentLength) {
            listener = pListener;
            contentLength = pContentLength;
        }

        /**
         * 调用以指示已读取的字节数.
         *
         * @param pBytes 已读取的字节数.
         */
        void noteBytesRead(int pBytes) {
            /* 表示已从输入流中读取给定的字节数.
             */
            bytesRead += pBytes;
            notifyListener();
        }

        /**
         * 调用以指示已检测到新文件项.
         */
        void noteItem() {
            ++items;
            notifyListener();
        }

        /**
         * 调用通知监听器.
         */
        private void notifyListener() {
            if (listener != null) {
                listener.update(bytesRead, contentLength, items);
            }
        }

    }

    // ----------------------------------------------------- Manifest constants

    /**
     * 回车符ASCII码字符值.
     */
    public static final byte CR = 0x0D;

    /**
     * 换行符ASCII字符值.
     */
    public static final byte LF = 0x0A;

    /**
     * 短划线(-)ASCII字符值.
     */
    public static final byte DASH = 0x2D;

    /**
     * 将处理的<code>header-part</code>的最大长度 (10 kilobytes = 10240 bytes.).
     */
    public static final int HEADER_PART_SIZE_MAX = 10240;

    /**
     * 用于处理请求的缓冲区的默认长度.
     */
    protected static final int DEFAULT_BUFSIZE = 4096;

    /**
     * 一个字节序列，标记<code>header-part</code>的结尾 (<code>CRLFCRLF</code>).
     */
    protected static final byte[] HEADER_SEPARATOR = {CR, LF, CR, LF};

    /**
     * 一个字节序列，跟着一个分隔符，分隔符后面跟着一个封装 (<code>CRLF</code>).
     */
    protected static final byte[] FIELD_SEPARATOR = {CR, LF};

    /**
     * 一个字节序列，跟着流中最后一个封装的分隔符 (<code>--</code>).
     */
    protected static final byte[] STREAM_TERMINATOR = {DASH, DASH};

    /**
     * 在边界 (<code>CRLF--</code>)之前的字节序列.
     */
    protected static final byte[] BOUNDARY_PREFIX = {CR, LF, DASH, DASH};

    // ----------------------------------------------------------- Data members

    /**
     * 从中读取数据的输入流.
     */
    private final InputStream input;

    /**
     * 边界 token加上前缀<code>CRLF--</code>的长度.
     */
    private int boundaryLength;

    /**
     * 数据量, 以字节为单位, 必须保留在缓冲区中以便可靠地检测分隔符.
     */
    private final int keepRegion;

    /**
     * 分区流的字节序列.
     */
    private final byte[] boundary;

    /**
     * Knuth-Morris-Pratt搜索算法表
     */
    private int[] boundaryTable;

    /**
     * 用于处理请求的缓冲区的长度.
     */
    private final int bufSize;

    /**
     * 用于处理请求的缓冲区.
     */
    private final byte[] buffer;

    /**
     * 缓冲区中第一个有效字符的索引.
     * <br>
     * 0 <= head < bufSize
     */
    private int head;

    /**
     * 缓冲区中最后一个有效字符的索引 + 1.
     * <br>
     * 0 <= tail <= bufSize
     */
    private int tail;

    /**
     * 读取header时要使用的内容编码.
     */
    private String headerEncoding;

    /**
     * 进度通知器, 或 null.
     */
    private final ProgressNotifier notifier;

    // ----------------------------------------------------------- Constructors

    /**
     * <p> 请注意, 缓冲区必须至少足以包含边界字符串, 加上4个字符的CR/LF和双破折号, 加上至少一个字节的数据.
     * 缓冲区大小设置太小会降低性能.
     *
     * @param input    用作数据源的<code>InputStream</code>.
     * @param boundary 用于将流划分到<code>encapsulations</code>的token.
     * @param bufSize  要使用的缓冲区的大小, 以字节为单位.
     * @param pNotifier 通知器，用于调用进度监听器.
     *
     * @throws IllegalArgumentException 如果缓冲区太小
     *
     * @since 1.3.1
     */
    public MultipartStream(InputStream input,
            byte[] boundary,
            int bufSize,
            ProgressNotifier pNotifier) {

        if (boundary == null) {
            throw new IllegalArgumentException("boundary may not be null");
        }
        // 将CR/LF添加到边界以从主体数据token中删除结尾的CR/LF.
        this.boundaryLength = boundary.length + BOUNDARY_PREFIX.length;
        if (bufSize < this.boundaryLength + 1) {
            throw new IllegalArgumentException(
                    "The buffer size specified for the MultipartStream is too small");
        }

        this.input = input;
        this.bufSize = Math.max(bufSize, boundaryLength*2);
        this.buffer = new byte[this.bufSize];
        this.notifier = pNotifier;

        this.boundary = new byte[this.boundaryLength];
        this.boundaryTable = new int[this.boundaryLength + 1];
        this.keepRegion = this.boundary.length;

        System.arraycopy(BOUNDARY_PREFIX, 0, this.boundary, 0,
                BOUNDARY_PREFIX.length);
        System.arraycopy(boundary, 0, this.boundary, BOUNDARY_PREFIX.length,
                boundary.length);
        computeBoundaryTable();

        head = 0;
        tail = 0;
    }

    /**
     * @param input    用作数据源的<code>InputStream</code>.
     * @param boundary 用于将流划分到<code>encapsulations</code>的token.
     * @param pNotifier 通知器，用于调用进度监听器.
     */
    MultipartStream(InputStream input,
            byte[] boundary,
            ProgressNotifier pNotifier) {
        this(input, boundary, DEFAULT_BUFSIZE, pNotifier);
    }

    // --------------------------------------------------------- Public methods

    /**
     * 检索读取单个部分header时使用的字符编码. 如果未指定, 或 <code>null</code>, 使用平台默认编码.
     *
     * @return 用于读取部分header的编码.
     */
    public String getHeaderEncoding() {
        return headerEncoding;
    }

    /**
     * 设置读取单个部分header时使用的字符编码. 如果未指定, 或 <code>null</code>, 使用平台默认编码.
     *
     * @param encoding 用于读取部分header的编码.
     */
    public void setHeaderEncoding(String encoding) {
        headerEncoding = encoding;
    }

    /**
     * 从<code>buffer</code>中读取一个字节，并根据需要重新填充.
     *
     * @return 输入流的下一个字节.
     *
     * @throws IOException 如果没有更多可用数据.
     */
    public byte readByte() throws IOException {
        // Buffer depleted ?
        if (head == tail) {
            head = 0;
            // Refill.
            tail = input.read(buffer, head, bufSize);
            if (tail == -1) {
                // 没有更多数据可用.
                throw new IOException("No more data is available");
            }
            if (notifier != null) {
                notifier.noteBytesRead(tail);
            }
        }
        return buffer[head++];
    }

    /**
     * 跳过 <code>boundary</code> token, 并检查流中是否包含更多<code>encapsulations</code>.
     *
     * @return <code>true</code> 如果此流中有更多封装; 否则<code>false</code>.
     *
     * @throws FileUploadIOException 如果从流中读取的字节超出了大小限制
     * @throws MalformedStreamException 如果流意外结束或无法遵循所需的语法.
     */
    public boolean readBoundary()
            throws FileUploadIOException, MalformedStreamException {
        byte[] marker = new byte[2];
        boolean nextChunk = false;

        head += boundaryLength;
        try {
            marker[0] = readByte();
            if (marker[0] == LF) {
                // 使用输入 type = image解决IE5 Mac bug问题.
                // 因为边界分隔符（不包括结尾CRLF）不得出现在任何文件中 (RFC 2046, section 5.1.1), 
                // 丢失的CR是由于错误的浏览器而不是包含类似边界的文件.
                return true;
            }

            marker[1] = readByte();
            if (arrayequals(marker, STREAM_TERMINATOR, 2)) {
                nextChunk = false;
            } else if (arrayequals(marker, FIELD_SEPARATOR, 2)) {
                nextChunk = true;
            } else {
                throw new MalformedStreamException(
                "Unexpected characters follow a boundary");
            }
        } catch (FileUploadIOException e) {
            // wraps a SizeException, re-throw as it will be unwrapped later
            throw e;
        } catch (IOException e) {
            throw new MalformedStreamException("Stream ended unexpectedly");
        }
        return nextChunk;
    }

    /**
     * <p>更改用于对流进行分区的边界token.
     *
     * <p>此方法允许单遍处理嵌套的多部分流.
     *
     * <p>嵌套流的边界标记需要与父流中的边界标记具有相同的长度.
     *
     * <p>在处理嵌套流之后, 恢复父流边界标记留给应用程序处理.
     *
     * @param boundary 用于解析嵌套流的边界.
     *
     * @throws IllegalBoundaryException 如果<code>boundary</code>的长度与当前正在解析的长度不同.
     */
    public void setBoundary(byte[] boundary)
            throws IllegalBoundaryException {
        if (boundary.length != boundaryLength - BOUNDARY_PREFIX.length) {
            throw new IllegalBoundaryException(
            "The length of a boundary token cannot be changed");
        }
        System.arraycopy(boundary, 0, this.boundary, BOUNDARY_PREFIX.length,
                boundary.length);
        computeBoundaryTable();
    }

    /**
     * 计算用于Knuth-Morris-Pratt搜索算法的表.
     */
    private void computeBoundaryTable() {
        int position = 2;
        int candidate = 0;

        boundaryTable[0] = -1;
        boundaryTable[1] = 0;

        while (position <= boundaryLength) {
            if (boundary[position - 1] == boundary[candidate]) {
                boundaryTable[position] = candidate + 1;
                candidate++;
                position++;
            } else if (candidate > 0) {
                candidate = boundaryTable[candidate];
            } else {
                boundaryTable[position] = 0;
                position++;
            }
        }
    }

    /**
     * <p>读取当前的<code>encapsulation</code>的 <code>header-part</code>.
     *
     * <p>Header将逐字返回到输入流, 包括结尾的 <code>CRLF</code> 标记. 解析留给应用程序.
     *
     * <p><strong>TODO</strong> 允许限制最大header大小以防止滥用.
     *
     * @return 当前的<code>encapsulation</code>的 <code>header-part</code>.
     *
     * @throws FileUploadIOException 如果从流中读取的字节超出了大小限制.
     * @throws MalformedStreamException 如果流意外结束.
     */
    public String readHeaders() throws FileUploadIOException, MalformedStreamException {
        int i = 0;
        byte b;
        // 支持多字节字符
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int size = 0;
        while (i < HEADER_SEPARATOR.length) {
            try {
                b = readByte();
            } catch (FileUploadIOException e) {
                // wraps a SizeException, re-throw as it will be unwrapped later
                throw e;
            } catch (IOException e) {
                throw new MalformedStreamException("Stream ended unexpectedly");
            }
            if (++size > HEADER_PART_SIZE_MAX) {
                throw new MalformedStreamException(String.format(
                        "Header section has more than %s bytes (maybe it is not properly terminated)",
                        Integer.valueOf(HEADER_PART_SIZE_MAX)));
            }
            if (b == HEADER_SEPARATOR[i]) {
                i++;
            } else {
                i = 0;
            }
            baos.write(b);
        }

        String headers = null;
        if (headerEncoding != null) {
            try {
                headers = baos.toString(headerEncoding);
            } catch (UnsupportedEncodingException e) {
                // 如果不支持指定的编码，则回退到平台默认值.
                headers = baos.toString();
            }
        } else {
            headers = baos.toString();
        }

        return headers;
    }

    /**
     * <p>从当前<code>encapsulation</code>读取<code>body-data</code>, 并将它的内容写入到输出流.
     *
     * <p>使用常量大小的缓冲区，此方法可以处理任意大量数据.
     * (see {@link #MultipartStream(InputStream,byte[],int, MultipartStream.ProgressNotifier) constructor}).
     *
     * @param output 要写入数据的<code>Stream</code>. 可能是 null, 这种情况下等效于 {@link #discardBodyData()}.
     *
     * @return 写入的数据量.
     *
     * @throws MalformedStreamException 如果流意外结束.
     * @throws IOException              如果发生I/O错误.
     */
    public int readBodyData(OutputStream output)
            throws MalformedStreamException, IOException {
        return (int) Streams.copy(newInputStream(), output, false); // N.B. Streams.copy closes the input stream
    }

    /**
     * 创建一个新的 {@link ItemInputStream}.
     */
    ItemInputStream newInputStream() {
        return new ItemInputStream();
    }

    /**
     * <p>从当前<code>encapsulation</code>读取 <code>body-data</code>, 并丢弃它.
     *
     * <p>使用此方法可以跳过不需要或不理解的封装.
     *
     * @return 丢弃的数据量.
     *
     * @throws MalformedStreamException 如果流意外结束.
     * @throws IOException              如果发生I/O错误.
     */
    public int discardBodyData() throws MalformedStreamException, IOException {
        return readBodyData(null);
    }

    /**
     * 查找第一个<code>encapsulation</code>的开始.
     *
     * @return <code>true</code>: 如果在流中找到<code>encapsulation</code>.
     *
     * @throws IOException 如果发生I/O错误.
     */
    public boolean skipPreamble() throws IOException {
        // 第一个分隔符可能没有CRLF.
        System.arraycopy(boundary, 2, boundary, 0, boundary.length - 2);
        boundaryLength = boundary.length - 2;
        computeBoundaryTable();
        try {
            // 丢弃分隔符以外的所有数据.
            discardBodyData();

            // Read boundary - 如果成功, 流包含封装.
            return readBoundary();
        } catch (MalformedStreamException e) {
            return false;
        } finally {
            // Restore delimiter.
            System.arraycopy(boundary, 0, boundary, 2, boundary.length - 2);
            boundaryLength = boundary.length;
            boundary[0] = CR;
            boundary[1] = LF;
            computeBoundaryTable();
        }
    }

    /**
     * 比较数组<code>a</code> 和 <code>b</code>中从第一个字节到 <code>count</code>个字节.
     *
     * @param a     要比较的第一个数组.
     * @param b     要比较的第二个数组.
     * @param count 应该比较多少个字节.
     *
     * @return <code>true</code> 如果相等.
     */
    public static boolean arrayequals(byte[] a,
            byte[] b,
            int count) {
        for (int i = 0; i < count; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 在<code>buffer</code>中搜索指定值的字节, 在指定的 <code>position</code>开始.
     *
     * @param value 要找的值.
     * @param pos   搜索的起始位置.
     *
     * @return 找到字节的位置, 从<code>buffer</code>的起始位置开始计数, 或<code>-1</code>未找到.
     */
    protected int findByte(byte value,
            int pos) {
        for (int i = pos; i < tail; i++) {
            if (buffer[i] == value) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 在<code>head</code>和<code>tail</code>分隔的<code>buffer</code>区域中搜索<code>boundary</code>.
     *
     * @return 发现边界的位置, 从<code>buffer</code>的起始位置开始计数, 或<code>-1</code>未找到.
     */
    protected int findSeparator() {

        int bufferPos = this.head;
        int tablePos = 0;

        while (bufferPos < this.tail) {
            while (tablePos >= 0 && buffer[bufferPos] != boundary[tablePos]) {
                tablePos = boundaryTable[tablePos];
            }
            bufferPos++;
            tablePos++;
            if (tablePos == boundaryLength) {
                return bufferPos - boundaryLength;
            }
        }
        return -1;
    }

    /**
     * 抛出以指示输入流无法遵循所需的语法.
     */
    public static class MalformedStreamException extends IOException {

        private static final long serialVersionUID = 6466926458059796677L;

        public MalformedStreamException() {
            super();
        }

        /**
         * @param message 详细信息.
         */
        public MalformedStreamException(String message) {
            super(message);
        }

    }

    /**
     * 尝试设置无效的边界token时抛出.
     */
    public static class IllegalBoundaryException extends IOException {


        private static final long serialVersionUID = -161533165102632918L;

        public IllegalBoundaryException() {
            super();
        }

        /**
         * @param message 详细信息.
         */
        public IllegalBoundaryException(String message) {
            super(message);
        }
    }

    /**
     * 用于读取项目内容的{@link InputStream}.
     */
    public class ItemInputStream extends InputStream implements Closeable {

        /**
         * 到目前为止已读取的字节数.
         */
        private long total;

        /**
         * 必须保留的字节数, 因为它们可能是边界的一部分.
         */
        private int pad;

        /**
         * 缓冲区中的当前偏移量.
         */
        private int pos;

        /**
         * 流是否已关闭.
         */
        private boolean closed;

        ItemInputStream() {
            findSeparator();
        }

        /**
         * 寻找分隔符.
         */
        private void findSeparator() {
            pos = MultipartStream.this.findSeparator();
            if (pos == -1) {
                if (tail - head > keepRegion) {
                    pad = keepRegion;
                } else {
                    pad = tail - head;
                }
            }
        }

        /**
         * 返回流已读取的字节数.
         */
        public long getBytesRead() {
            return total;
        }

        /**
         * 返回当前可用的字节数，不会阻塞.
         *
         * @throws IOException 发生I/O错误.
         * @return 缓冲区中的字节数.
         */
        @Override
        public int available() throws IOException {
            if (pos == -1) {
                return tail - head - pad;
            }
            return pos - head;
        }

        /**
         * 将负字节转换为整数时的偏移量.
         */
        private static final int BYTE_POSITIVE_OFFSET = 256;

        /**
         * 返回流中的下一个字节.
         *
         * @return 流中的下一个字节, 作为非负整数, 或 -1 用于 EOF.
         * @throws IOException 发生I/O错误.
         */
        @Override
        public int read() throws IOException {
            if (closed) {
                throw new FileItemStream.ItemSkippedException();
            }
            if (available() == 0 && makeAvailable() == 0) {
                return -1;
            }
            ++total;
            int b = buffer[head++];
            if (b >= 0) {
                return b;
            }
            return b + BYTE_POSITIVE_OFFSET;
        }

        /**
         * 将字节读入到给定缓冲区.
         *
         * @param b 要写入的目标缓冲区.
         * @param off 缓冲区中第一个字节的偏移量.
         * @param len 要读取的最大字节数.
         * 
         * @return 实际读取的字节数, 或 -1 用于 EOF.
         * @throws IOException 发生 I/O 错误.
         */
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new FileItemStream.ItemSkippedException();
            }
            if (len == 0) {
                return 0;
            }
            int res = available();
            if (res == 0) {
                res = makeAvailable();
                if (res == 0) {
                    return -1;
                }
            }
            res = Math.min(res, len);
            System.arraycopy(buffer, head, b, off, res);
            head += res;
            total += res;
            return res;
        }

        /**
         * 关闭输入流.
         *
         * @throws IOException 发生 I/O 错误.
         */
        @Override
        public void close() throws IOException {
            close(false);
        }

        /**
         * 关闭输入流.
         *
         * @param pCloseUnderlying 是否关闭底层流 (hard close)
         * 
         * @throws IOException 发生 I/O 错误.
         */
        public void close(boolean pCloseUnderlying) throws IOException {
            if (closed) {
                return;
            }
            if (pCloseUnderlying) {
                closed = true;
                input.close();
            } else {
                for (;;) {
                    int av = available();
                    if (av == 0) {
                        av = makeAvailable();
                        if (av == 0) {
                            break;
                        }
                    }
                    skip(av);
                }
            }
            closed = true;
        }

        /**
         * 跳过给定的字节数.
         *
         * @param bytes 要跳过的字节数.
         * 
         * @return 实际上已跳过的字节数.
         * @throws IOException 发生 I/O 错误.
         */
        @Override
        public long skip(long bytes) throws IOException {
            if (closed) {
                throw new FileItemStream.ItemSkippedException();
            }
            int av = available();
            if (av == 0) {
                av = makeAvailable();
                if (av == 0) {
                    return 0;
                }
            }
            long res = Math.min(av, bytes);
            head += res;
            return res;
        }

        /**
         * 尝试读取更多数据.
         *
         * @return 可用字节数
         * @throws IOException 发生 I/O 错误.
         */
        private int makeAvailable() throws IOException {
            if (pos != -1) {
                return 0;
            }

            // 将数据移动到缓冲区的开头.
            total += tail - head - pad;
            System.arraycopy(buffer, tail - pad, buffer, 0, pad);

            // 用新数据重新填充缓冲区.
            head = 0;
            tail = pad;

            for (;;) {
                int bytesRead = input.read(buffer, tail, bufSize - tail);
                if (bytesRead == -1) {
                    // 最后一个填充量留在缓冲区中.
                    // 边界不能在那里，所以发出错误信号.
                    final String msg = "Stream ended unexpectedly";
                    throw new MalformedStreamException(msg);
                }
                if (notifier != null) {
                    notifier.noteBytesRead(bytesRead);
                }
                tail += bytesRead;

                findSeparator();
                int av = available();

                if (av > 0 || pos != -1) {
                    return av;
                }
            }
        }

        /**
         * 是否关闭流.
         *
         * @return True, 如果流已关闭, 否则 false.
         */
        @Override
        public boolean isClosed() {
            return closed;
        }
    }
}
