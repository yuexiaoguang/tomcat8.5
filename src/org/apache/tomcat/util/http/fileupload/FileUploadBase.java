package org.apache.tomcat.util.http.fileupload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.tomcat.util.http.fileupload.MultipartStream.ItemInputStream;
import org.apache.tomcat.util.http.fileupload.util.Closeable;
import org.apache.tomcat.util.http.fileupload.util.FileItemHeadersImpl;
import org.apache.tomcat.util.http.fileupload.util.LimitedInputStream;
import org.apache.tomcat.util.http.fileupload.util.Streams;


/**
 * <p>用于处理文件上传的高级API.</p>
 *
 * <p>此类处理每个HTML片段的多个文件, 使用<code>multipart/mixed</code>编码类型发送,
 * 就像<a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>指定的那样.
 * 使用{@link #parseRequest(RequestContext)}获取与给定HTML片段关联的{@link org.apache.tomcat.util.http.fileupload.FileItem FileItems}列表.</p>
 *
 * <p>如何存储单个部分的数据由用于创建它们的工厂决定; 给定的部分可能在内存中，磁盘上或其他地方.</p>
 */
public abstract class FileUploadBase {

    // ---------------------------------------------------------- Class methods

    /**
     * <p>确定请求是否包含多部分内容.</p>
     *
     * <p><strong>NOTE:</strong>FileUpload 1.1发布后，此方法将移至<code>ServletFileUpload</code>类.
     * 不幸, 因为这个方法是 static, 在删除此方法之前, 无法提供替换.</p>
     *
     * @param ctx 要评估的请求上下文. 必须 non-null.
     *
     * @return <code>true</code> 如果请求是多部分的;
     *         <code>false</code> 否则.
     */
    public static final boolean isMultipartContent(RequestContext ctx) {
        String contentType = ctx.getContentType();
        if (contentType == null) {
            return false;
        }
        if (contentType.toLowerCase(Locale.ENGLISH).startsWith(MULTIPART)) {
            return true;
        }
        return false;
    }

    // ----------------------------------------------------- Manifest constants

    /**
     * HTTP内容类型header名称.
     */
    public static final String CONTENT_TYPE = "Content-type";

    /**
     * HTTP内容处置header名称.
     */
    public static final String CONTENT_DISPOSITION = "Content-disposition";

    /**
     * HTTP内容长度header名称.
     */
    public static final String CONTENT_LENGTH = "Content-length";

    /**
     * 表单数据的Content-disposition值.
     */
    public static final String FORM_DATA = "form-data";

    /**
     * 文件附件的 Content-disposition 值.
     */
    public static final String ATTACHMENT = "attachment";

    /**
     * HTTP内容类型header的一部分.
     */
    public static final String MULTIPART = "multipart/";

    /**
     * 多部分表单的HTTP内容类型 header.
     */
    public static final String MULTIPART_FORM_DATA = "multipart/form-data";

    /**
     * 多个上传的HTTP内容类型header.
     */
    public static final String MULTIPART_MIXED = "multipart/mixed";

    // ----------------------------------------------------------- Data members

    /**
     * 完整请求允许的最大大小, 而不是{@link #fileSizeMax}. 值-1表示没有最大值.
     */
    private long sizeMax = -1;

    /**
     * 单个上传文件允许的最大大小, 而不是 {@link #sizeMax}. 值-1表示没有最大值.
     */
    private long fileSizeMax = -1;

    /**
     * 读取部分header时要使用的内容编码.
     */
    private String headerEncoding;

    /**
     * 进度监听器.
     */
    private ProgressListener listener;

    // ----------------------------------------------------- Property accessors

    /**
     * 返回创建文件项时使用的工厂类.
     *
     * @return 新文件项的工厂类.
     */
    public abstract FileItemFactory getFileItemFactory();

    /**
     * 设置创建文件项时要使用的工厂类.
     *
     * @param factory 新文件项的工厂类.
     */
    public abstract void setFileItemFactory(FileItemFactory factory);

    /**
     * 返回完整请求的最大允许大小, 而不是 {@link #getFileSizeMax()}.
     *
     * @return 允许的最大大小, 以字节为单位. 默认值-1表示没有限制.
     */
    public long getSizeMax() {
        return sizeMax;
    }

    /**
     * 设置完整请求的最大允许大小, 而不是 {@link #getFileSizeMax()}.
     *
     * @param sizeMax 允许的最大大小, 以字节为单位. 默认值-1表示没有限制.
     */
    public void setSizeMax(long sizeMax) {
        this.sizeMax = sizeMax;
    }

    /**
     * 返回单个上传文件的最大允许大小, 而不是 {@link #getSizeMax()}.
     *
     * @return 单个上传文件的最大大小.
     */
    public long getFileSizeMax() {
        return fileSizeMax;
    }

    /**
     * 设置单个上传文件的最大允许大小, 而不是 {@link #getSizeMax()}.
     *
     * @param fileSizeMax 单个上传文件的最大大小.
     */
    public void setFileSizeMax(long fileSizeMax) {
        this.fileSizeMax = fileSizeMax;
    }

    /**
     * 读取单个部分Header时使用的字符编码.
     * 如果未指定, 或为<code>null</code>, 将使用请求的编码. 如果没有指定, 或为<code>null</code>, 使用平台默认编码.
     *
     * @return The encoding used to read part headers.
     */
    public String getHeaderEncoding() {
        return headerEncoding;
    }

    /**
     * 读取单个部分Header时使用的字符编码.
     * 如果未指定, 或为<code>null</code>, 将使用请求的编码. 如果没有指定, 或为<code>null</code>, 使用平台默认编码.
     *
     * @param encoding 用于读取部分Header的编码.
     */
    public void setHeaderEncoding(String encoding) {
        headerEncoding = encoding;
    }

    // --------------------------------------------------------- Public methods

    /**
     * 处理符合<a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>的<code>multipart/form-data</code>流.
     *
     * @param ctx 要解析的请求的上下文.
     *
     * @return 从请求中解析的<code>FileItemStream</code>实例的迭代器, 按照它们传输的顺序.
     *
     * @throws FileUploadException 如果在读取/解析请求或存储文件时出现问题.
     * @throws IOException 发生 I/O 错误. 这可能是与客户端通信时的网络错误, 或在存储上传的内容时的问题.
     */
    public FileItemIterator getItemIterator(RequestContext ctx)
    throws FileUploadException, IOException {
        try {
            return new FileItemIteratorImpl(ctx);
        } catch (FileUploadIOException e) {
            // unwrap encapsulated SizeException
            throw (FileUploadException) e.getCause();
        }
    }

    /**
     * 处理符合<a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>的<code>multipart/form-data</code>流.
     *
     * @param ctx 要解析的请求的上下文.
     *
     * @return 从请求中解析的一组 <code>FileItem</code>, 按照它们传输的顺序.
     *
     * @throws FileUploadException 如果在读取/解析请求或存储文件时出现问题.
     */
    public List<FileItem> parseRequest(RequestContext ctx)
            throws FileUploadException {
        List<FileItem> items = new ArrayList<>();
        boolean successful = false;
        try {
            FileItemIterator iter = getItemIterator(ctx);
            FileItemFactory fac = getFileItemFactory();
            if (fac == null) {
                throw new NullPointerException("No FileItemFactory has been set.");
            }
            while (iter.hasNext()) {
                final FileItemStream item = iter.next();
                // Don't use getName() here to prevent an InvalidFileNameException.
                final String fileName = ((FileItemIteratorImpl.FileItemStreamImpl) item).name;
                FileItem fileItem = fac.createItem(item.getFieldName(), item.getContentType(),
                                                   item.isFormField(), fileName);
                items.add(fileItem);
                try {
                    Streams.copy(item.openStream(), fileItem.getOutputStream(), true);
                } catch (FileUploadIOException e) {
                    throw (FileUploadException) e.getCause();
                } catch (IOException e) {
                    throw new IOFileUploadException(String.format("Processing of %s request failed. %s",
                                                           MULTIPART_FORM_DATA, e.getMessage()), e);
                }
                final FileItemHeaders fih = item.getHeaders();
                fileItem.setHeaders(fih);
            }
            successful = true;
            return items;
        } catch (FileUploadIOException e) {
            throw (FileUploadException) e.getCause();
        } catch (IOException e) {
            throw new FileUploadException(e.getMessage(), e);
        } finally {
            if (!successful) {
                for (FileItem fileItem : items) {
                    try {
                        fileItem.delete();
                    } catch (Exception ignored) {
                        // ignored TODO perhaps add to tracker delete failure list somehow?
                    }
                }
            }
        }
    }

    /**
     * 处理符合<a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>的<code>multipart/form-data</code>流.
     *
     * @param ctx 要解析的请求的上下文.
     *
     * @return 从请求中解析的一组<code>FileItem</code>实例.
     *
     * @throws FileUploadException 如果在读取/解析请求或存储文件时出现问题.
     *
     * @since 1.3
     */
    public Map<String, List<FileItem>> parseParameterMap(RequestContext ctx)
            throws FileUploadException {
        final List<FileItem> items = parseRequest(ctx);
        final Map<String, List<FileItem>> itemsMap = new HashMap<>(items.size());

        for (FileItem fileItem : items) {
            String fieldName = fileItem.getFieldName();
            List<FileItem> mappedItems = itemsMap.get(fieldName);

            if (mappedItems == null) {
                mappedItems = new ArrayList<>();
                itemsMap.put(fieldName, mappedItems);
            }

            mappedItems.add(fileItem);
        }

        return itemsMap;
    }

    // ------------------------------------------------------ Protected methods

    /**
     * 从<code>Content-type</code> header中检索边界.
     *
     * @param contentType 要从中提取边界值的内容类型header的值.
     *
     * @return 边界, 作为字节数组.
     */
    protected byte[] getBoundary(String contentType) {
        ParameterParser parser = new ParameterParser();
        parser.setLowerCaseNames(true);
        // 参数解析器可以处理null输入
        Map<String,String> params =
                parser.parse(contentType, new char[] {';', ','});
        String boundaryStr = params.get("boundary");

        if (boundaryStr == null) {
            return null;
        }
        byte[] boundary;
        boundary = boundaryStr.getBytes(StandardCharsets.ISO_8859_1);
        return boundary;
    }

    /**
     * 从<code>Content-disposition</code>标头中检索文件名.
     *
     * @param headers HTTP header对象.
     *
     * @return 当前<code>encapsulation</code>的文件名.
     */
    protected String getFileName(FileItemHeaders headers) {
        return getFileName(headers.getHeader(CONTENT_DISPOSITION));
    }

    /**
     * 返回给定的content-disposition header文件名.
     * 
     * @param pContentDisposition The content-disposition headers value.
     * 
     * @return The file name
     */
    private String getFileName(String pContentDisposition) {
        String fileName = null;
        if (pContentDisposition != null) {
            String cdl = pContentDisposition.toLowerCase(Locale.ENGLISH);
            if (cdl.startsWith(FORM_DATA) || cdl.startsWith(ATTACHMENT)) {
                ParameterParser parser = new ParameterParser();
                parser.setLowerCaseNames(true);
                // Parameter parser can handle null input
                Map<String,String> params =
                    parser.parse(pContentDisposition, ';');
                if (params.containsKey("filename")) {
                    fileName = params.get("filename");
                    if (fileName != null) {
                        fileName = fileName.trim();
                    } else {
                        // 即使没有值, 参数存在, 所以返回一个空文件名而不是没有文件名.
                        fileName = "";
                    }
                }
            }
        }
        return fileName;
    }

    /**
     * 从<code>Content-disposition</code> header中检索字段名称.
     *
     * @param headers 包含HTTP请求header的<code>Map</code>.
     *
     * @return 当前<code>encapsulation</code>的字段名.
     */
    protected String getFieldName(FileItemHeaders headers) {
        return getFieldName(headers.getHeader(CONTENT_DISPOSITION));
    }

    /**
     * 返回字段名称，该名称由content-disposition标头给出.
     * 
     * @param pContentDisposition content-dispositions header的值.
     * 
     * @return The field jake
     */
    private String getFieldName(String pContentDisposition) {
        String fieldName = null;
        if (pContentDisposition != null
                && pContentDisposition.toLowerCase(Locale.ENGLISH).startsWith(FORM_DATA)) {
            ParameterParser parser = new ParameterParser();
            parser.setLowerCaseNames(true);
            // Parameter parser can handle null input
            Map<String,String> params = parser.parse(pContentDisposition, ';');
            fieldName = params.get("name");
            if (fieldName != null) {
                fieldName = fieldName.trim();
            }
        }
        return fieldName;
    }

    /**
     * <p>解析<code>header-part</code>并返回key/value对.
     *
     * <p>如果有多个相同名称的header, 该名称将映射到包含值的逗号分隔列表.
     *
     * @param headerPart 当前<code>encapsulation</code>的<code>header-part</code>.
     *
     * @return 包含解析的HTTP 请求header的A <code>Map</code>.
     */
    protected FileItemHeaders getParsedHeaders(String headerPart) {
        final int len = headerPart.length();
        FileItemHeadersImpl headers = newFileItemHeaders();
        int start = 0;
        for (;;) {
            int end = parseEndOfLine(headerPart, start);
            if (start == end) {
                break;
            }
            StringBuilder header = new StringBuilder(headerPart.substring(start, end));
            start = end + 2;
            while (start < len) {
                int nonWs = start;
                while (nonWs < len) {
                    char c = headerPart.charAt(nonWs);
                    if (c != ' '  &&  c != '\t') {
                        break;
                    }
                    ++nonWs;
                }
                if (nonWs == start) {
                    break;
                }
                // Continuation line found
                end = parseEndOfLine(headerPart, nonWs);
                header.append(" ").append(headerPart.substring(nonWs, end));
                start = end + 2;
            }
            parseHeaderLine(headers, header.toString());
        }
        return headers;
    }

    /**
     * 创建 {@link FileItemHeaders}的实例.
     */
    protected FileItemHeadersImpl newFileItemHeaders() {
        return new FileItemHeadersImpl();
    }

    /**
     * 跳过字节直到当前行的结尾.
     * 
     * @param headerPart 正在解析的header.
     * @param end 最后一个字节的索引，尚未处理.
     *   
     * @return \r\n 序列的索引, 表示行的末尾.
     */
    private int parseEndOfLine(String headerPart, int end) {
        int index = end;
        for (;;) {
            int offset = headerPart.indexOf('\r', index);
            if (offset == -1  ||  offset + 1 >= headerPart.length()) {
                throw new IllegalStateException(
                    "Expected headers to be terminated by an empty line.");
            }
            if (headerPart.charAt(offset + 1) == '\n') {
                return offset;
            }
            index = offset + 1;
        }
    }

    /**
     * 读取下一个header行.
     * 
     * @param headers String with all headers.
     * @param header Map where to store the current header.
     */
    private void parseHeaderLine(FileItemHeadersImpl headers, String header) {
        final int colonOffset = header.indexOf(':');
        if (colonOffset == -1) {
            // 此header行格式错误, 跳过它.
            return;
        }
        String headerName = header.substring(0, colonOffset).trim();
        String headerValue =
            header.substring(header.indexOf(':') + 1).trim();
        headers.addHeader(headerName, headerValue);
    }

    /**
     * 迭代器, 由{@link FileUploadBase#getItemIterator(RequestContext)}返回.
     */
    private class FileItemIteratorImpl implements FileItemIterator {

        /**
         * {@link FileItemStream}的默认实现.
         */
        class FileItemStreamImpl implements FileItemStream {

            /**
             * 文件项内容类型.
             */
            private final String contentType;

            /**
             * 文件项字段名称.
             */
            private final String fieldName;

            /**
             * 文件项文件名.
             */
            private final String name;

            /**
             * 文件项是否为表单字段.
             */
            private final boolean formField;

            /**
             * 文件项输入流.
             */
            private final InputStream stream;

            /**
             * headers.
             */
            private FileItemHeaders headers;

            /**
             * @param pName 项目文件名, 或 null.
             * @param pFieldName 项目字段名称.
             * @param pContentType 项目内容类型, 或 null.
             * @param pFormField Whether 该项目是一个表单字段.
             * @param pContentLength 项目内容长度, 或 -1
             * 
             * @throws IOException 创建文件项失败.
             */
            FileItemStreamImpl(String pName, String pFieldName,
                    String pContentType, boolean pFormField,
                    long pContentLength) throws IOException {
                name = pName;
                fieldName = pFieldName;
                contentType = pContentType;
                formField = pFormField;
                final ItemInputStream itemStream = multi.newInputStream();
                InputStream istream = itemStream;
                if (fileSizeMax != -1) {
                    if (pContentLength != -1
                            &&  pContentLength > fileSizeMax) {
                        FileSizeLimitExceededException e =
                            new FileSizeLimitExceededException(
                                String.format("The field %s exceeds its maximum permitted size of %s bytes.",
                                        fieldName, Long.valueOf(fileSizeMax)),
                                pContentLength, fileSizeMax);
                        e.setFileName(pName);
                        e.setFieldName(pFieldName);
                        throw new FileUploadIOException(e);
                    }
                    istream = new LimitedInputStream(istream, fileSizeMax) {
                        @Override
                        protected void raiseError(long pSizeMax, long pCount)
                                throws IOException {
                            itemStream.close(true);
                            FileSizeLimitExceededException e =
                                new FileSizeLimitExceededException(
                                    String.format("The field %s exceeds its maximum permitted size of %s bytes.",
                                           fieldName, Long.valueOf(pSizeMax)),
                                    pCount, pSizeMax);
                            e.setFieldName(fieldName);
                            e.setFileName(name);
                            throw new FileUploadIOException(e);
                        }
                    };
                }
                stream = istream;
            }

            /**
             * 返回项目内容类型, 或 null.
             */
            @Override
            public String getContentType() {
                return contentType;
            }

            /**
             * 返回项目字段名称.
             */
            @Override
            public String getFieldName() {
                return fieldName;
            }

            /**
             * 返回项目文件名.
             *
             * @return 文件名, 或 null.
             * @throws InvalidFileNameException 文件名包含NUL字符，可能是安全攻击的指示符.
             * 	如果仍然使用这样的文件名, 捕获异常并使用 InvalidFileNameException#getName().
             */
            @Override
            public String getName() {
                return Streams.checkFileName(name);
            }

            /**
             * 是否是一个表单字段.
             *
             * @return True, 如果该项目是表单字段, 否则 false.
             */
            @Override
            public boolean isFormField() {
                return formField;
            }

            /**
             * 返回输入流，可用于读取项目内容.
             *
             * @return 打开的输入流.
             * @throws IOException 发生I/O错误.
             */
            @Override
            public InputStream openStream() throws IOException {
                if (((Closeable) stream).isClosed()) {
                    throw new FileItemStream.ItemSkippedException();
                }
                return stream;
            }

            /**
             * 关闭文件项.
             *
             * @throws IOException 发生I/O错误.
             */
            void close() throws IOException {
                stream.close();
            }

            /**
             * 返回文件项header.
             */
            @Override
            public FileItemHeaders getHeaders() {
                return headers;
            }

            /**
             * 设置文件项header.
             *
             * @param pHeaders 文件项header
             */
            @Override
            public void setHeaders(FileItemHeaders pHeaders) {
                headers = pHeaders;
            }

        }

        /**
         * 要处理的多部分流.
         */
        private final MultipartStream multi;

        /**
         * 通知器, 用于通知{@link ProgressListener}.
         */
        private final MultipartStream.ProgressNotifier notifier;

        /**
         * 分隔各个部分的边界.
         */
        private final byte[] boundary;

        /**
         * 目前处理的项目.
         */
        private FileItemStreamImpl currentItem;

        /**
         * 当前项目字段名称.
         */
        private String currentFieldName;

        /**
         * 目前是否正在跳过序言.
         */
        private boolean skipPreamble;

        /**
         * 是否仍可以读取当前项目.
         */
        private boolean itemValid;

        /**
         * 是否已到达文件的结尾.
         */
        private boolean eof;

        /**
         * @param ctx 请求上下文.
         * 
         * @throws FileUploadException 解析请求时发生错误.
         * @throws IOException 发生I/O错误.
         */
        FileItemIteratorImpl(RequestContext ctx)
                throws FileUploadException, IOException {
            if (ctx == null) {
                throw new NullPointerException("ctx parameter");
            }

            String contentType = ctx.getContentType();
            if ((null == contentType)
                    || (!contentType.toLowerCase(Locale.ENGLISH).startsWith(MULTIPART))) {
                throw new InvalidContentTypeException(String.format(
                        "the request doesn't contain a %s or %s stream, content type header is %s",
                        MULTIPART_FORM_DATA, MULTIPART_MIXED, contentType));
            }


            final long requestSize = ((UploadContext) ctx).contentLength();

            InputStream input; // N.B. 这最终在MultipartStream处理中关闭
            if (sizeMax >= 0) {
                if (requestSize != -1 && requestSize > sizeMax) {
                    throw new SizeLimitExceededException(String.format(
                            "the request was rejected because its size (%s) exceeds the configured maximum (%s)",
                            Long.valueOf(requestSize), Long.valueOf(sizeMax)),
                            requestSize, sizeMax);
                }
                // N.B. 这最终在MultipartStream处理中关闭
                input = new LimitedInputStream(ctx.getInputStream(), sizeMax) {
                    @Override
                    protected void raiseError(long pSizeMax, long pCount)
                            throws IOException {
                        FileUploadException ex = new SizeLimitExceededException(
                        String.format("the request was rejected because its size (%s) exceeds the configured maximum (%s)",
                               Long.valueOf(pCount), Long.valueOf(pSizeMax)),
                               pCount, pSizeMax);
                        throw new FileUploadIOException(ex);
                    }
                };
            } else {
                input = ctx.getInputStream();
            }

            String charEncoding = headerEncoding;
            if (charEncoding == null) {
                charEncoding = ctx.getCharacterEncoding();
            }

            boundary = getBoundary(contentType);
            if (boundary == null) {
                IOUtils.closeQuietly(input); // 避免可能的资源泄漏
                throw new FileUploadException("the request was rejected because no multipart boundary was found");
            }

            notifier = new MultipartStream.ProgressNotifier(listener, requestSize);
            try {
                multi = new MultipartStream(input, boundary, notifier);
            } catch (IllegalArgumentException iae) {
                IOUtils.closeQuietly(input); // 避免可能的资源泄漏
                throw new InvalidContentTypeException(
                        String.format("The boundary specified in the %s header is too long", CONTENT_TYPE), iae);
            }
            multi.setHeaderEncoding(charEncoding);

            skipPreamble = true;
            findNextItem();
        }

        /**
         * 查找下一个项目.
         *
         * @return True, 如果找到下一个项目, 否则 false.
         * @throws IOException 发生I/O错误.
         */
        private boolean findNextItem() throws IOException {
            if (eof) {
                return false;
            }
            if (currentItem != null) {
                currentItem.close();
                currentItem = null;
            }
            for (;;) {
                boolean nextPart;
                if (skipPreamble) {
                    nextPart = multi.skipPreamble();
                } else {
                    nextPart = multi.readBoundary();
                }
                if (!nextPart) {
                    if (currentFieldName == null) {
                        // Outer multipart terminated -> No more data
                        eof = true;
                        return false;
                    }
                    // Inner multipart terminated -> Return to parsing the outer
                    multi.setBoundary(boundary);
                    currentFieldName = null;
                    continue;
                }
                FileItemHeaders headers = getParsedHeaders(multi.readHeaders());
                if (currentFieldName == null) {
                    // We're parsing the outer multipart
                    String fieldName = getFieldName(headers);
                    if (fieldName != null) {
                        String subContentType = headers.getHeader(CONTENT_TYPE);
                        if (subContentType != null
                                &&  subContentType.toLowerCase(Locale.ENGLISH)
                                        .startsWith(MULTIPART_MIXED)) {
                            currentFieldName = fieldName;
                            // Multiple files associated with this field name
                            byte[] subBoundary = getBoundary(subContentType);
                            multi.setBoundary(subBoundary);
                            skipPreamble = true;
                            continue;
                        }
                        String fileName = getFileName(headers);
                        currentItem = new FileItemStreamImpl(fileName,
                                fieldName, headers.getHeader(CONTENT_TYPE),
                                fileName == null, getContentLength(headers));
                        currentItem.setHeaders(headers);
                        notifier.noteItem();
                        itemValid = true;
                        return true;
                    }
                } else {
                    String fileName = getFileName(headers);
                    if (fileName != null) {
                        currentItem = new FileItemStreamImpl(fileName,
                                currentFieldName,
                                headers.getHeader(CONTENT_TYPE),
                                false, getContentLength(headers));
                        currentItem.setHeaders(headers);
                        notifier.noteItem();
                        itemValid = true;
                        return true;
                    }
                }
                multi.discardBodyData();
            }
        }

        private long getContentLength(FileItemHeaders pHeaders) {
            try {
                return Long.parseLong(pHeaders.getHeader(CONTENT_LENGTH));
            } catch (Exception e) {
                return -1;
            }
        }

        /**
         * 是否有另一个{@link FileItemStream}实例可用.
         *
         * @throws FileUploadException 解析或处理文件项失败.
         * @throws IOException 读取文件项失败.
         * @return True, 如果有一个或多个其他文件可用, 否则 false.
         */
        @Override
        public boolean hasNext() throws FileUploadException, IOException {
            if (eof) {
                return false;
            }
            if (itemValid) {
                return true;
            }
            try {
                return findNextItem();
            } catch (FileUploadIOException e) {
                // unwrap encapsulated SizeException
                throw (FileUploadException) e.getCause();
            }
        }

        /**
         * 返回下一个可用的 {@link FileItemStream}.
         *
         * @throws java.util.NoSuchElementException 没有更多项目可用. 使用{@link #hasNext()} 防止这个异常.
         * @throws FileUploadException 解析或处理文件项失败.
         * @throws IOException读取文件项失败.
         * @return FileItemStream 实例, 提供对下一个文件项的访问.
         */
        @Override
        public FileItemStream next() throws FileUploadException, IOException {
            if (eof  ||  (!itemValid && !hasNext())) {
                throw new NoSuchElementException();
            }
            itemValid = false;
            return currentItem;
        }

    }

    /**
     * 抛出此异常是为了在{@link IOException}中隐藏内部{@link FileUploadException}.
     */
    public static class FileUploadIOException extends IOException {

        private static final long serialVersionUID = -3082868232248803474L;

        public FileUploadIOException() {
            super();
        }

        public FileUploadIOException(String message, Throwable cause) {
            super(message, cause);
        }

        public FileUploadIOException(String message) {
            super(message);
        }

        public FileUploadIOException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * 抛出以指示请求不是多部分请求.
     */
    public static class InvalidContentTypeException
            extends FileUploadException {

        private static final long serialVersionUID = -9073026332015646668L;

        public InvalidContentTypeException() {
            super();
        }

        public InvalidContentTypeException(String message) {
            super(message);
        }

        public InvalidContentTypeException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /**
     * 抛出以指示IOException.
     */
    public static class IOFileUploadException extends FileUploadException {

        private static final long serialVersionUID = -5858565745868986701L;

        public IOFileUploadException() {
            super();
        }

        public IOFileUploadException(String message, Throwable cause) {
            super(message, cause);
        }

        public IOFileUploadException(String message) {
            super(message);
        }

        public IOFileUploadException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * 如果超出请求允许的大小，则抛出此异常.
     */
    public abstract static class SizeException extends FileUploadException {

        private static final long serialVersionUID = -8776225574705254126L;

        /**
         * 请求的实际大小.
         */
        private final long actual;

        /**
         * 请求的最大允许大小.
         */
        private final long permitted;

        /**
         * @param message 详细信息.
         * @param actual 请求中的实际字节数.
         * @param permitted 请求大小限制，以字节为单位.
         */
        protected SizeException(String message, long actual, long permitted) {
            super(message);
            this.actual = actual;
            this.permitted = permitted;
        }

        /**
         * 请求的实际大小.
         */
        public long getActualSize() {
            return actual;
        }

        /**
         * 请求的最大允许大小.
         */
        public long getPermittedSize() {
            return permitted;
        }

    }

    /**
     * 抛出以指示请求大小超过配置的最大值.
     */
    public static class SizeLimitExceededException
            extends SizeException {

        private static final long serialVersionUID = -2474893167098052828L;

        /**
         * @param message   详细信息.
         * @param actual    实际的请求大小.
         * @param permitted 允许的最大请求大小.
         */
        public SizeLimitExceededException(String message, long actual,
                long permitted) {
            super(message, actual, permitted);
        }

    }

    /**
     * 抛出以指示A文件大小超过配置的最大值.
     */
    public static class FileSizeLimitExceededException
            extends SizeException {

        private static final long serialVersionUID = 8150776562029630058L;

        /**
         * 导致异常的项目的文件名.
         */
        private String fileName;

        /**
         * 导致异常的项目的字段名称.
         */
        private String fieldName;

        /**
         * @param message   详细信息.
         * @param actual    实际的请求大小.
         * @param permitted 允许的最大请求大小.
         */
        public FileSizeLimitExceededException(String message, long actual,
                long permitted) {
            super(message, actual, permitted);
        }

        /**
         * 返回导致异常的项的文件名.
         *
         * @return 文件名, 或 null.
         */
        public String getFileName() {
            return fileName;
        }

        /**
         * 设置导致异常的项目的文件名.
         *
         * @param pFileName 文件名.
         */
        public void setFileName(String pFileName) {
            fileName = pFileName;
        }

        /**
         * 返回导致异常的项的字段名称.
         *
         * @return 字段名称, 或 null.
         */
        public String getFieldName() {
            return fieldName;
        }

        /**
         * 设置导致异常的项的字段名称.
         *
         * @param pFieldName 项的字段名称.
         */
        public void setFieldName(String pFieldName) {
            fieldName = pFieldName;
        }

    }

    /**
     * 返回进度监听器.
     */
    public ProgressListener getProgressListener() {
        return listener;
    }

    /**
     * 设置进度监听器.
     *
     * @param pListener 进度监听器. 默认为 null.
     */
    public void setProgressListener(ProgressListener pListener) {
        listener = pListener;
    }

}
