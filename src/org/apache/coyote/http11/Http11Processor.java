package org.apache.coyote.http11;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.ErrorState;
import org.apache.coyote.Request;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.UpgradeToken;
import org.apache.coyote.http11.filters.BufferedInputFilter;
import org.apache.coyote.http11.filters.ChunkedInputFilter;
import org.apache.coyote.http11.filters.ChunkedOutputFilter;
import org.apache.coyote.http11.filters.GzipOutputFilter;
import org.apache.coyote.http11.filters.IdentityInputFilter;
import org.apache.coyote.http11.filters.IdentityOutputFilter;
import org.apache.coyote.http11.filters.SavedRequestInputFilter;
import org.apache.coyote.http11.filters.VoidInputFilter;
import org.apache.coyote.http11.filters.VoidOutputFilter;
import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.Ascii;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.log.UserDataHelper;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SendfileDataBase;
import org.apache.tomcat.util.net.SendfileKeepAliveState;
import org.apache.tomcat.util.net.SendfileState;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

public class Http11Processor extends AbstractProcessor {

    private static final Log log = LogFactory.getLog(Http11Processor.class);

    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Http11Processor.class);


    private final UserDataHelper userDataHelper;

    /**
     * Input.
     */
    protected final Http11InputBuffer inputBuffer;


    /**
     * Output.
     */
    protected final Http11OutputBuffer outputBuffer;


    /**
     * 过滤器库中多少个内部过滤器, 因此，在寻找可插拔过滤器时，它们会跳过.
     */
    private int pluggableFilterIndex = Integer.MAX_VALUE;


    /**
     * Keep-alive.
     */
    protected volatile boolean keepAlive = true;


    /**
     * 指示Socket 是否应该保持打开状态 (e.g. 对于 keep alive 或发送文件.
     */
    protected boolean openSocket = false;


    /**
     * 指示是否完整读取请求 header.
     */
    protected boolean readComplete = true;

    /**
     * HTTP/1.1 标志.
     */
    protected boolean http11 = true;


    /**
     * HTTP/0.9 标志.
     */
    protected boolean http09 = false;


    /**
     * 请求的内容分隔符 (如果是 false, 在请求结束时，连接将被关闭).
     */
    protected boolean contentDelimitation = true;


    /**
     * 定义受限的用户代理的正则表达式.
     */
    protected Pattern restrictedUserAgents = null;


    /**
     * Keep-Alive 请求的最大数量.
     */
    protected int maxKeepAliveRequests = -1;


    /**
     * 上传超时时间. 在Apache HTTPD 服务器中是5分钟.
     */
    protected int connectionUploadTimeout = 300000;


    /**
     * 禁止在上传时设置不同的超时时间.
     */
    protected boolean disableUploadTimeout = false;


    /**
     * 允许的压缩等级.
     */
    protected int compressionLevel = 0;


    /**
     * 要压缩的内容的最小大小.
     */
    protected int compressionMinSize = 2048;


    /**
     * 保存的post 最大大小.
     */
    protected int maxSavePostSize = 4 * 1024;


    /**
     * 定义不使用GZIP的用户代理的正则表达式
     */
    protected Pattern noCompressionUserAgents = null;


    /**
     * 启用压缩的 MIMES 列表.
     * Note: 拼写不正确，但不能在不打破兼容性的前提下改变
     */
    protected String[] compressableMimeTypes;


    /**
     * Host 名称 (用于避免主机名称上无用的B2C转换).
     */
    protected char[] hostNameC = new char[0];


    /**
     * 允许一个自定的服务器 header, 为了 tin-foil hat folks.
     */
    private String server = null;


    /*
     * 应用是否应该为删除的HTTP Server header提供值.
     * Note: 如果设置了{@link #server}, 将重写应用提供的所有值.
     */
    private boolean serverRemoveAppProvidedValues = false;

    /**
     * HTTP连接升级后使用的新协议实例.
     */
    protected UpgradeToken upgradeToken = null;


    /**
     * Sendfile data.
     */
    protected SendfileDataBase sendfileData = null;


    /**
     * UpgradeProtocol information
     */
    private final Map<String,UpgradeProtocol> httpUpgradeProtocols;

    private final boolean allowHostHeaderMismatch;


    public Http11Processor(int maxHttpHeaderSize, boolean allowHostHeaderMismatch,
            boolean rejectIllegalHeaderName, AbstractEndpoint<?> endpoint, int maxTrailerSize,
            Set<String> allowedTrailerHeaders, int maxExtensionSize, int maxSwallowSize,
            Map<String,UpgradeProtocol> httpUpgradeProtocols, boolean sendReasonPhrase) {

        super(endpoint);
        userDataHelper = new UserDataHelper(log);

        inputBuffer = new Http11InputBuffer(request, maxHttpHeaderSize,rejectIllegalHeaderName);
        request.setInputBuffer(inputBuffer);

        outputBuffer = new Http11OutputBuffer(response, maxHttpHeaderSize, sendReasonPhrase);
        response.setOutputBuffer(outputBuffer);

        // Create and add the identity filters.
        inputBuffer.addFilter(new IdentityInputFilter(maxSwallowSize));
        outputBuffer.addFilter(new IdentityOutputFilter());

        // Create and add the chunked filters.
        inputBuffer.addFilter(new ChunkedInputFilter(maxTrailerSize, allowedTrailerHeaders,
                maxExtensionSize, maxSwallowSize));
        outputBuffer.addFilter(new ChunkedOutputFilter());

        // Create and add the void filters.
        inputBuffer.addFilter(new VoidInputFilter());
        outputBuffer.addFilter(new VoidOutputFilter());

        // Create and add buffered input filter
        inputBuffer.addFilter(new BufferedInputFilter());

        // Create and add the chunked filters.
        //inputBuffer.addFilter(new GzipInputFilter());
        outputBuffer.addFilter(new GzipOutputFilter());

        pluggableFilterIndex = inputBuffer.getFilters().length;

        this.httpUpgradeProtocols = httpUpgradeProtocols;
        this.allowHostHeaderMismatch = allowHostHeaderMismatch;
    }


    /**
     * 设置压缩级别.
     *
     * @param compression <code>on</code>, <code>force</code>, <code>off</code>其中之一;
     * 						或字节的最小压缩大小
     */
    public void setCompression(String compression) {
        if (compression.equals("on")) {
            this.compressionLevel = 1;
        } else if (compression.equals("force")) {
            this.compressionLevel = 2;
        } else if (compression.equals("off")) {
            this.compressionLevel = 0;
        } else {
            try {
                // 解析 compression 为一个 int, 得到最小压缩大小
                compressionMinSize = Integer.parseInt(compression);
                this.compressionLevel = 1;
            } catch (Exception e) {
                this.compressionLevel = 0;
            }
        }
    }

    /**
     * 设置最小大小以触发压缩.
     *
     * @param compressionMinSize 字节压缩所需的最小内容长度
     */
    public void setCompressionMinSize(int compressionMinSize) {
        this.compressionMinSize = compressionMinSize;
    }


    /**
     * 设置不压缩的用户代理模式. 由 {@link Pattern}支持的正则表达式. e.g.: <code>gorilla|desesplorer|tigrus</code>.
     *
     * @param noCompressionUserAgents 不压缩的用户代理字符串的正则表达式
     */
    public void setNoCompressionUserAgents(String noCompressionUserAgents) {
        if (noCompressionUserAgents == null || noCompressionUserAgents.length() == 0) {
            this.noCompressionUserAgents = null;
        } else {
            this.noCompressionUserAgents =
                Pattern.compile(noCompressionUserAgents);
        }
    }


    /**
     * @param compressibleMimeTypes See
     *        {@link Http11Processor#setCompressibleMimeTypes(String[])}
     * @deprecated Use
     *             {@link Http11Processor#setCompressibleMimeTypes(String[])}
     */
    @Deprecated
    public void setCompressableMimeTypes(String[] compressibleMimeTypes) {
        setCompressibleMimeTypes(compressibleMimeTypes);
    }


    /**
     * 设置可压缩的 mime-type 列表 (当使用大量连接器时, 这种方法是最好的, 最好让它们都引用一个数组).
     *
     * @param compressibleMimeTypes 应该启用要压缩的MIME 类型
     */
    public void setCompressibleMimeTypes(String[] compressibleMimeTypes) {
        this.compressableMimeTypes = compressibleMimeTypes;
    }


    /**
     * 返回压缩等级.
     *
     * @return 当前压缩等级 (off/on/force)
     */
    public String getCompression() {
        switch (compressionLevel) {
        case 0:
            return "off";
        case 1:
            return "on";
        case 2:
            return "force";
        }
        return "off";
    }


    /**
     * 检查字符串数组中的任何条目是否以指定的值开始
     *
     * @param sArray the StringArray
     * @param value string
     */
    private static boolean startsWithStringArray(String sArray[], String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < sArray.length; i++) {
            if (value.startsWith(sArray[i])) {
                return true;
            }
        }
        return false;
    }


    /**
     * 设置受限制的用户代理列表 (将连接器降级为 HTTP/1.0 模式). 由 {@link Pattern}支持的正则表达式.
     *
     * @param restrictedUserAgents 由 {@link Pattern}支持的正则表达式 "gorilla|desesplorer|tigrus"
     */
    public void setRestrictedUserAgents(String restrictedUserAgents) {
        if (restrictedUserAgents == null ||
                restrictedUserAgents.length() == 0) {
            this.restrictedUserAgents = null;
        } else {
            this.restrictedUserAgents = Pattern.compile(restrictedUserAgents);
        }
    }


    /**
     * 设置允许的Keep-Alive 请求的最大数量.
     * 这是为了防止DoS攻击. 设置为负值禁用限制.
     *
     * @param mkar 最大数量
     */
    public void setMaxKeepAliveRequests(int mkar) {
        maxKeepAliveRequests = mkar;
    }


    /**
     * 获取允许的Keep-Alive 请求的最大数量.
     * 这是为了防止DoS攻击. 设置为负值禁用限制.
     */
    public int getMaxKeepAliveRequests() {
        return maxKeepAliveRequests;
    }


    /**
     * 设置将在SSL模式中缓冲的POST的最大大小.
     * 当接收到安全约束需要客户端证书的POST时, 当SSL握手发生以获得证书时，需要缓冲POST主体.
     *
     * @param msps 缓冲的POST的最大大小, 字节
     */
    public void setMaxSavePostSize(int msps) {
        maxSavePostSize = msps;
    }


    /**
     * 将在SSL模式中缓冲的POST的最大大小.
     *
     * @return 字节
     */
    public int getMaxSavePostSize() {
        return maxSavePostSize;
    }


    /**
     * 设置在请求主体上传期间是否使用单独的连接超时.
     *
     * @param isDisabled {@code true} 禁用单独的上传超时
     */
    public void setDisableUploadTimeout(boolean isDisabled) {
        disableUploadTimeout = isDisabled;
    }

    /**
     * 在请求主体上传期间是否使用单独的连接超时.
     *
     * @return {@code true} 禁用单独的上传超时
     */
    public boolean getDisableUploadTimeout() {
        return disableUploadTimeout;
    }

    /**
     * 设置上传超时时间.
     *
     * @param timeout 超时时间, 毫秒
     */
    public void setConnectionUploadTimeout(int timeout) {
        connectionUploadTimeout = timeout ;
    }

    /**
     * 获取上传超时时间.
     *
     * @return 超时时间, 毫秒
     */
    public int getConnectionUploadTimeout() {
        return connectionUploadTimeout;
    }


    /**
     * 设置服务器 header 名称.
     *
     * @param server header 名称
     */
    public void setServer(String server) {
        if (server == null || server.equals("")) {
            this.server = null;
        } else {
            this.server = server;
        }
    }


    public void setServerRemoveAppProvidedValues(boolean serverRemoveAppProvidedValues) {
        this.serverRemoveAppProvidedValues = serverRemoveAppProvidedValues;
    }


    /**
     * 检查资源是否可以被压缩, 检查资源是否可以被压缩.
     */
    private boolean isCompressible() {

        // 检查内容是否已经被压缩
        MessageBytes contentEncodingMB =
            response.getMimeHeaders().getValue("Content-Encoding");

        if ((contentEncodingMB != null)
            && (contentEncodingMB.indexOf("gzip") != -1)) {
            return false;
        }

        // 如果是强制模式, 总是压缩 (仅测试目的)
        if (compressionLevel == 2) {
            return true;
        }

        // 检查是否有足够的长度来触发压缩
        long contentLength = response.getContentLengthLong();
        if ((contentLength == -1)
            || (contentLength > compressionMinSize)) {
            // 检查兼容的 MIME-TYPE
            if (compressableMimeTypes != null) {
                return (startsWithStringArray(compressableMimeTypes, response.getContentType()));
            }
        }

        return false;
    }


    /**
     * 检查此资源是否应使用压缩. 如果客户端支持该资源，则可以压缩该资源.
     */
    private boolean useCompression() {

        // 检查浏览器是否支持GZIP编码
        MessageBytes acceptEncodingMB =
            request.getMimeHeaders().getValue("accept-encoding");

        if ((acceptEncodingMB == null)
            || (acceptEncodingMB.indexOf("gzip") == -1)) {
            return false;
        }

        // 如果是强制模式, 总是压缩 (仅测试目的)
        if (compressionLevel == 2) {
            return true;
        }

        // 检查不兼容的浏览器
        if (noCompressionUserAgents != null) {
            MessageBytes userAgentValueMB =
                request.getMimeHeaders().getValue("user-agent");
            if(userAgentValueMB != null) {
                String userAgentValue = userAgentValueMB.toString();

                if (noCompressionUserAgents.matcher(userAgentValue).matches()) {
                    return false;
                }
            }
        }

        return true;
    }


    /**
     * 专用工具方法: 在ByteChunk中查找一个小写字节的序列.
     */
    private static int findBytes(ByteChunk bc, byte[] b) {

        byte first = b[0];
        byte[] buff = bc.getBuffer();
        int start = bc.getStart();
        int end = bc.getEnd();

        // 查找第一个 char
        int srcEnd = b.length;

        for (int i = start; i <= (end - srcEnd); i++) {
            if (Ascii.toLower(buff[i]) != first) {
                continue;
            }
            // 找到第一个 char, 现在查找一个匹配
            int myPos = i+1;
            for (int srcPos = 1; srcPos < srcEnd;) {
                if (Ascii.toLower(buff[myPos++]) != b[srcPos++]) {
                    break;
                }
                if (srcPos == srcEnd) {
                    return i - start; // found it
                }
            }
        }
        return -1;
    }


    /**
     * 由于HTTP状态代码，确定是否必须删除连接. 使用和Apache/httpd相同的代码列表.
     */
    private static boolean statusDropsConnection(int status) {
        return status == 400 /* SC_BAD_REQUEST */ ||
               status == 408 /* SC_REQUEST_TIMEOUT */ ||
               status == 411 /* SC_LENGTH_REQUIRED */ ||
               status == 413 /* SC_REQUEST_ENTITY_TOO_LARGE */ ||
               status == 414 /* SC_REQUEST_URI_TOO_LONG */ ||
               status == 500 /* SC_INTERNAL_SERVER_ERROR */ ||
               status == 503 /* SC_SERVICE_UNAVAILABLE */ ||
               status == 501 /* SC_NOT_IMPLEMENTED */;
    }


    /**
     * 向当前请求添加输入过滤器. 如果不支持编码, 将返回一个 501 响应.
     */
    private void addInputFilter(InputFilter[] inputFilters, String encodingName) {

        // 将编码名称转换为小写, 由于传输编码名称不区分大小写. (RFC2616, section 3.6)
        encodingName = encodingName.trim().toLowerCase(Locale.ENGLISH);

        if (encodingName.equals("identity")) {
            // Skip
        } else if (encodingName.equals("chunked")) {
            inputBuffer.addActiveFilter
                (inputFilters[Constants.CHUNKED_FILTER]);
            contentDelimitation = true;
        } else {
            for (int i = pluggableFilterIndex; i < inputFilters.length; i++) {
                if (inputFilters[i].getEncodingName().toString().equals(encodingName)) {
                    inputBuffer.addActiveFilter(inputFilters[i]);
                    return;
                }
            }
            // 不支持的传输编码
            // 501 - Unimplemented
            response.setStatus(501);
            setErrorState(ErrorState.CLOSE_CLEAN, null);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("http11processor.request.prepare") +
                          " Unsupported transfer encoding [" + encodingName + "]");
            }
        }
    }


    @Override
    public SocketState service(SocketWrapperBase<?> socketWrapper)
        throws IOException {
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

        // 设置 I/O
        setSocketWrapper(socketWrapper);
        inputBuffer.init(socketWrapper);
        outputBuffer.init(socketWrapper);

        // Flags
        keepAlive = true;
        openSocket = false;
        readComplete = true;
        boolean keptAlive = false;
        SendfileState sendfileState = SendfileState.DONE;

        while (!getErrorState().isError() && keepAlive && !isAsync() && upgradeToken == null &&
                sendfileState == SendfileState.DONE && !endpoint.isPaused()) {

            // 解析请求 header
            try {
                if (!inputBuffer.parseRequestLine(keptAlive)) {
                    if (inputBuffer.getParsingRequestLinePhase() == -1) {
                        return SocketState.UPGRADING;
                    } else if (handleIncompleteRequestLineRead()) {
                        break;
                    }
                }

                if (endpoint.isPaused()) {
                    // 503 - Service unavailable
                    response.setStatus(503);
                    setErrorState(ErrorState.CLOSE_CLEAN, null);
                } else {
                    keptAlive = true;
                    // 每次通过JMX改变限制
                    request.getMimeHeaders().setLimit(endpoint.getMaxHeaderCount());
                    if (!inputBuffer.parseHeaders()) {
                        // 已经读取了部分请求, 不要回收它，而是把它与Socket关联起来
                        openSocket = true;
                        readComplete = false;
                        break;
                    }
                    if (!disableUploadTimeout) {
                        socketWrapper.setReadTimeout(connectionUploadTimeout);
                    }
                }
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("http11processor.header.parse"), e);
                }
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                break;
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                UserDataHelper.Mode logMode = userDataHelper.getNextMode();
                if (logMode != null) {
                    String message = sm.getString("http11processor.header.parse");
                    switch (logMode) {
                        case INFO_THEN_DEBUG:
                            message += sm.getString("http11processor.fallToDebug");
                            //$FALL-THROUGH$
                        case INFO:
                            log.info(message, t);
                            break;
                        case DEBUG:
                            log.debug(message, t);
                    }
                }
                // 400 - Bad Request
                response.setStatus(400);
                setErrorState(ErrorState.CLOSE_CLEAN, t);
                getAdapter().log(request, response, 0);
            }

            // 请求升级?
            Enumeration<String> connectionValues = request.getMimeHeaders().values("Connection");
            boolean foundUpgrade = false;
            while (connectionValues.hasMoreElements() && !foundUpgrade) {
                foundUpgrade = connectionValues.nextElement().toLowerCase(
                        Locale.ENGLISH).contains("upgrade");
            }

            if (foundUpgrade) {
                // 检查协议
                String requestedProtocol = request.getHeader("Upgrade");

                UpgradeProtocol upgradeProtocol = httpUpgradeProtocols.get(requestedProtocol);
                if (upgradeProtocol != null) {
                    if (upgradeProtocol.accept(request)) {
                        // TODO 了解如何在此时处理请求主体.
                        response.setStatus(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
                        response.setHeader("Connection", "Upgrade");
                        response.setHeader("Upgrade", requestedProtocol);
                        action(ActionCode.CLOSE,  null);
                        getAdapter().log(request, response, 0);

                        InternalHttpUpgradeHandler upgradeHandler =
                                upgradeProtocol.getInternalUpgradeHandler(
                                        getAdapter(), cloneRequest(request));
                        UpgradeToken upgradeToken = new UpgradeToken(upgradeHandler, null, null);
                        action(ActionCode.UPGRADE, upgradeToken);
                        return SocketState.UPGRADING;
                    }
                }
            }

            if (!getErrorState().isError()) {
                // 设置过滤器, 并解析一些请求 header
                rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);
                try {
                    prepareRequest();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("http11processor.request.prepare"), t);
                    }
                    // 500 - Internal Server Error
                    response.setStatus(500);
                    setErrorState(ErrorState.CLOSE_CLEAN, t);
                    getAdapter().log(request, response, 0);
                }
            }

            if (maxKeepAliveRequests == 1) {
                keepAlive = false;
            } else if (maxKeepAliveRequests > 0 &&
                    socketWrapper.decrementKeepAlive() <= 0) {
                keepAlive = false;
            }

            // 在适配器中处理请求
            if (!getErrorState().isError()) {
                try {
                    rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
                    getAdapter().service(request, response);
                    // 在发生严重错误之前提交响应时的处理. 在设置状态为500和设置errorException时抛出一个 ServletException.
                    // 如果失败, 那么很可能已经提交了响应, 因此不能尝试设置 header.
                    if(keepAlive && !getErrorState().isError() && !isAsync() &&
                            statusDropsConnection(response.getStatus())) {
                        setErrorState(ErrorState.CLOSE_CLEAN, null);
                    }
                } catch (InterruptedIOException e) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                } catch (HeadersTooLargeException e) {
                    log.error(sm.getString("http11processor.request.process"), e);
                    // 响应不应该被提交，但无论如何检查它是安全的
                    if (response.isCommitted()) {
                        setErrorState(ErrorState.CLOSE_NOW, e);
                    } else {
                        response.reset();
                        response.setStatus(500);
                        setErrorState(ErrorState.CLOSE_CLEAN, e);
                        response.setHeader("Connection", "close"); // TODO: Remove
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.error(sm.getString("http11processor.request.process"), t);
                    // 500 - Internal Server Error
                    response.setStatus(500);
                    setErrorState(ErrorState.CLOSE_CLEAN, t);
                    getAdapter().log(request, response, 0);
                }
            }

            // 完成请求的处理
            rp.setStage(org.apache.coyote.Constants.STAGE_ENDINPUT);
            if (!isAsync()) {
                // 如果这是异步请求，则请求在完成时结束. AsyncContext 负责在这种情况下调用 endRequest().
                endRequest();
            }
            rp.setStage(org.apache.coyote.Constants.STAGE_ENDOUTPUT);

            // 如果有一个错误, 确保请求被计算为错误, 并更新统计计数器
            if (getErrorState().isError()) {
                response.setStatus(500);
            }

            if (!isAsync() || getErrorState().isError()) {
                request.updateCounters();
                if (getErrorState().isIoAllowed()) {
                    inputBuffer.nextRequest();
                    outputBuffer.nextRequest();
                }
            }

            if (!disableUploadTimeout) {
                int soTimeout = endpoint.getConnectionTimeout();
                if(soTimeout > 0) {
                    socketWrapper.setReadTimeout(soTimeout);
                } else {
                    socketWrapper.setReadTimeout(0);
                }
            }

            rp.setStage(org.apache.coyote.Constants.STAGE_KEEPALIVE);

            sendfileState = processSendfile(socketWrapper);
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (getErrorState().isError() || endpoint.isPaused()) {
            return SocketState.CLOSED;
        } else if (isAsync()) {
            return SocketState.LONG;
        } else if (isUpgrade()) {
            return SocketState.UPGRADING;
        } else {
            if (sendfileState == SendfileState.PENDING) {
                return SocketState.SENDFILE;
            } else {
                if (openSocket) {
                    if (readComplete) {
                        return SocketState.OPEN;
                    } else {
                        return SocketState.LONG;
                    }
                } else {
                    return SocketState.CLOSED;
                }
            }
        }
    }


    private Request cloneRequest(Request source) throws IOException {
        Request dest = new Request();

        // 传递到HTTP升级过程的请求的副本所需的最小信息

        dest.decodedURI().duplicate(source.decodedURI());
        dest.method().duplicate(source.method());
        dest.getMimeHeaders().duplicate(source.getMimeHeaders());
        dest.requestURI().duplicate(source.requestURI());

        return dest;

    }
    private boolean handleIncompleteRequestLineRead() {
        // 没有读完请求，所以保持socket打开
        openSocket = true;
        // 检查是否已经读取了请求行中的任何一个
        if (inputBuffer.getParsingRequestLinePhase() > 1) {
            // 开始读取请求行.
            if (endpoint.isPaused()) {
                // 部分处理请求，因此需要响应
                response.setStatus(503);
                setErrorState(ErrorState.CLOSE_CLEAN, null);
                getAdapter().log(request, response, 0);
                return false;
            } else {
                // 需要保持与socket相关的处理器
                readComplete = false;
            }
        }
        return true;
    }


    private void checkExpectationAndResponseStatus() {
        if (request.hasExpectation() &&
                (response.getStatus() < 200 || response.getStatus() > 299)) {
            // 客户端发送预期: 100-continue , 但是接收到一个非2xx 的响应. 禁用 keep-alive, 确保连接关闭.
            // 一些客户端可能继续发送主体, 一些可能发送下一个请求.
            // 无法区分, 因此关闭连接以强制客户端发送下一个请求.
            inputBuffer.setSwallowInput(false);
            keepAlive = false;
        }
    }


    /**
     * 在读取请求 header之后, 必须设置请求过滤器.
     */
    private void prepareRequest() {

        http11 = true;
        http09 = false;
        contentDelimitation = false;

        if (endpoint.isSSLEnabled()) {
            request.scheme().setString("https");
        }
        MessageBytes protocolMB = request.protocol();
        if (protocolMB.equals(Constants.HTTP_11)) {
            http11 = true;
            protocolMB.setString(Constants.HTTP_11);
        } else if (protocolMB.equals(Constants.HTTP_10)) {
            http11 = false;
            keepAlive = false;
            protocolMB.setString(Constants.HTTP_10);
        } else if (protocolMB.equals("")) {
            // HTTP/0.9
            http09 = true;
            http11 = false;
            keepAlive = false;
        } else {
            // 不支持的协议
            http11 = false;
            // Send 505; 不支持的 HTTP 版本
            response.setStatus(505);
            setErrorState(ErrorState.CLOSE_CLEAN, null);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("http11processor.request.prepare")+
                          " Unsupported HTTP version \""+protocolMB+"\"");
            }
        }

        MimeHeaders headers = request.getMimeHeaders();

        // 检查连接 header
        MessageBytes connectionValueMB = headers.getValue(Constants.CONNECTION);
        if (connectionValueMB != null) {
            ByteChunk connectionValueBC = connectionValueMB.getByteChunk();
            if (findBytes(connectionValueBC, Constants.CLOSE_BYTES) != -1) {
                keepAlive = false;
            } else if (findBytes(connectionValueBC,
                                 Constants.KEEPALIVE_BYTES) != -1) {
                keepAlive = true;
            }
        }

        if (http11) {
            MessageBytes expectMB = headers.getValue("expect");
            if (expectMB != null) {
                if (expectMB.indexOfIgnoreCase("100-continue", 0) != -1) {
                    inputBuffer.setSwallowInput(false);
                    request.setExpectation(true);
                } else {
                    response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
                    setErrorState(ErrorState.CLOSE_CLEAN, null);
                }
            }
        }

        // 检查 user-agent header
        if (restrictedUserAgents != null && (http11 || keepAlive)) {
            MessageBytes userAgentValueMB = headers.getValue("user-agent");
            // 检查限制列表, 并调整相应的 http11 和 keepAlive 标记
            if(userAgentValueMB != null) {
                String userAgentValue = userAgentValueMB.toString();
                if (restrictedUserAgents != null &&
                        restrictedUserAgents.matcher(userAgentValue).matches()) {
                    http11 = false;
                    keepAlive = false;
                }
            }
        }


        // 检查 host header
        MessageBytes hostValueMB = null;
        try {
            hostValueMB = headers.getUniqueValue("host");
        } catch (IllegalArgumentException iae) {
            // 不允许多个 Host header
            // 400 - Bad request
            response.setStatus(400);
            setErrorState(ErrorState.CLOSE_CLEAN, null);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("http11processor.request.multipleHosts"));
            }
        }
        if (http11 && hostValueMB == null) {
            // 400 - Bad request
            response.setStatus(400);
            setErrorState(ErrorState.CLOSE_CLEAN, null);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("http11processor.request.prepare")+
                          " host header missing");
            }
        }

        // 检查完全 URI (包括协议://host:port/)
        ByteChunk uriBC = request.requestURI().getByteChunk();
        if (uriBC.startsWithIgnoreCase("http", 0)) {

            int pos = uriBC.indexOf("://", 0, 3, 4);
            int uriBCStart = uriBC.getStart();
            int slashPos = -1;
            if (pos != -1) {
                pos += 3;
                byte[] uriB = uriBC.getBytes();
                slashPos = uriBC.indexOf('/', pos);
                int atPos = uriBC.indexOf('@', pos);
                if (slashPos == -1) {
                    slashPos = uriBC.getLength();
                    // Set URI as "/"
                    request.requestURI().setBytes
                        (uriB, uriBCStart + pos - 2, 1);
                } else {
                    request.requestURI().setBytes
                        (uriB, uriBCStart + slashPos,
                         uriBC.getLength() - slashPos);
                }
                // 跳过用户信息
                if (atPos != -1) {
                    pos = atPos + 1;
                }
                if (http11) {
                    // 主机header丢失是非法的，但上面处理过
                    if (hostValueMB != null) {
                        // 请求行中的 host 必须和 Host header一致
                        if (!hostValueMB.getByteChunk().equals(
                                uriB, uriBCStart + pos, slashPos - pos)) {
                            if (allowHostHeaderMismatch) {
                                // 正在应用RFC 2616的要求. 如果 host header 和请求行不一致, 请求行优先
                                hostValueMB = headers.setValue("host");
                                hostValueMB.setBytes(uriB, uriBCStart + pos, slashPos - pos);
                            } else {
                                // 正在应用RFC 7230的要求. 如果 host header 和请求行不一致, 触发一个 400 响应.
                                response.setStatus(400);
                                setErrorState(ErrorState.CLOSE_CLEAN, null);
                                if (log.isDebugEnabled()) {
                                    log.debug(sm.getString("http11processor.request.inconsistentHosts"));
                                }
                            }
                        }
                    }
                } else {
                    // Not HTTP/1.1 - 没有 Host header, 因此生成一个, 因为 Tomcat 内部假设已经设置了
                    hostValueMB = headers.setValue("host");
                    hostValueMB.setBytes(uriB, uriBCStart + pos, slashPos - pos);
                }
            }
        }

        // 输入过滤器设置
        InputFilter[] inputFilters = inputBuffer.getFilters();

        // 解析 transfer-encoding header
        if (http11) {
            MessageBytes transferEncodingValueMB = headers.getValue("transfer-encoding");
            if (transferEncodingValueMB != null) {
                String transferEncodingValue = transferEncodingValueMB.toString();
                // 解析逗号分隔的列表. 忽略"identity" 代码
                int startPos = 0;
                int commaPos = transferEncodingValue.indexOf(',');
                String encodingName = null;
                while (commaPos != -1) {
                    encodingName = transferEncodingValue.substring(startPos, commaPos);
                    addInputFilter(inputFilters, encodingName);
                    startPos = commaPos + 1;
                    commaPos = transferEncodingValue.indexOf(',', startPos);
                }
                encodingName = transferEncodingValue.substring(startPos);
                addInputFilter(inputFilters, encodingName);
            }
        }

        // 解析 content-length header
        long contentLength = request.getContentLengthLong();
        if (contentLength >= 0) {
            if (contentDelimitation) {
                // contentDelimitation 是 true, 块编码正在被使用，但是块编码不应该与内容长度一起使用. RFC 2616, section 4.4,
                // bullet 3 states Content-Length 在这种情况下必须被忽略 - 因此删除它.
                headers.removeHeader("content-length");
                request.setContentLength(-1);
            } else {
                inputBuffer.addActiveFilter
                        (inputFilters[Constants.IDENTITY_FILTER]);
                contentDelimitation = true;
            }
        }

        parseHost(hostValueMB);

        if (!contentDelimitation) {
            // 如果没有内容长度 (损坏的 HTTP/1.0 或 HTTP/1.1), 假设客户端没有被破坏，没有发送一个主体
            inputBuffer.addActiveFilter
                    (inputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
        }

        if (getErrorState().isError()) {
            getAdapter().log(request, response, 0);
        }
    }


    /**
     * 提交响应时, 必须验证一组 header, 并设置响应过滤器.
     */
    @Override
    protected final void prepareResponse() throws IOException {

        boolean entityBody = true;
        contentDelimitation = false;

        OutputFilter[] outputFilters = outputBuffer.getFilters();

        if (http09 == true) {
            // HTTP/0.9
            outputBuffer.addActiveFilter(outputFilters[Constants.IDENTITY_FILTER]);
            outputBuffer.commit();
            return;
        }

        int statusCode = response.getStatus();
        if (statusCode < 200 || statusCode == 204 || statusCode == 205 ||
                statusCode == 304) {
            // 没有实体主体
            outputBuffer.addActiveFilter
                (outputFilters[Constants.VOID_FILTER]);
            entityBody = false;
            contentDelimitation = true;
            if (statusCode == 205) {
                // RFC 7231要求服务器在这种情况下显式地发出空的响应
                response.setContentLength(0);
            } else {
                response.setContentLength(-1);
            }
        }

        MessageBytes methodMB = request.method();
        if (methodMB.equals("HEAD")) {
            // No entity body
            outputBuffer.addActiveFilter
                (outputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
        }

        // Sendfile support
        if (endpoint.getUseSendfile()) {
            prepareSendfile(outputFilters);
        }

        // 检查压缩
        boolean isCompressible = false;
        boolean useCompression = false;
        if (entityBody && (compressionLevel > 0) && sendfileData == null) {
            isCompressible = isCompressible();
            if (isCompressible) {
                useCompression = useCompression();
            }
            // 修改 content-length 为 -1, 强制为块
            if (useCompression) {
                response.setContentLength(-1);
            }
        }

        MimeHeaders headers = response.getMimeHeaders();
        // SC_NO_CONTENT 响应可能包含实体 header
        if (entityBody || statusCode == HttpServletResponse.SC_NO_CONTENT) {
            String contentType = response.getContentType();
            if (contentType != null) {
                headers.setValue("Content-Type").setString(contentType);
            }
            String contentLanguage = response.getContentLanguage();
            if (contentLanguage != null) {
                headers.setValue("Content-Language")
                    .setString(contentLanguage);
            }
        }

        long contentLength = response.getContentLengthLong();
        boolean connectionClosePresent = false;
        if (contentLength != -1) {
            headers.setValue("Content-Length").setLong(contentLength);
            outputBuffer.addActiveFilter
                (outputFilters[Constants.IDENTITY_FILTER]);
            contentDelimitation = true;
        } else {
            // 如果响应代码支持实体主体, 而且正在  HTTP 1.1 上, 那么使用块, 除非有一个 Connection: close header
            connectionClosePresent = isConnectionClose(headers);
            if (entityBody && http11 && !connectionClosePresent) {
                outputBuffer.addActiveFilter
                    (outputFilters[Constants.CHUNKED_FILTER]);
                contentDelimitation = true;
                headers.addValue(Constants.TRANSFERENCODING).setString(Constants.CHUNKED);
            } else {
                outputBuffer.addActiveFilter
                    (outputFilters[Constants.IDENTITY_FILTER]);
            }
        }

        if (useCompression) {
            outputBuffer.addActiveFilter(outputFilters[Constants.GZIP_FILTER]);
            headers.setValue("Content-Encoding").setString("gzip");
        }
        // 如果它可能被压缩, 设置 Vary header
        if (isCompressible) {
            // Make Proxies happy via Vary (from mod_deflate)
            MessageBytes vary = headers.getValue("Vary");
            if (vary == null) {
                // Add a new Vary header
                headers.setValue("Vary").setString("Accept-Encoding");
            } else if (vary.equals("*")) {
                // No action required
            } else {
                // 合并到当前 header
                headers.setValue("Vary").setString(
                        vary.getString() + ",Accept-Encoding");
            }
        }

        // Add date header unless application has already set one (e.g. in a Caching Filter)
        if (headers.getValue("Date") == null) {
            headers.addValue("Date").setString(
                    FastHttpDateFormat.getCurrentDate());
        }

        // FIXME: 添加转换编码header

        if ((entityBody) && (!contentDelimitation)) {
            // 请求后关闭连接, 并添加 connection: close header
            keepAlive = false;
        }

        // 可能禁用了 keep-alive, 在Connection header开始前检查.
        checkExpectationAndResponseStatus();

        // 如果知道这个请求早就坏了, 添加Connection: close header.
        if (keepAlive && statusDropsConnection(statusCode)) {
            keepAlive = false;
        }
        if (!keepAlive) {
            // 避免添加 close header 两次
            if (!connectionClosePresent) {
                headers.addValue(Constants.CONNECTION).setString(
                        Constants.CLOSE);
            }
        } else if (!http11 && !getErrorState().isError()) {
            headers.addValue(Constants.CONNECTION).setString(Constants.KEEPALIVE);
        }

        // Add server header
        if (server == null) {
            if (serverRemoveAppProvidedValues) {
                headers.removeHeader("server");
            }
        } else {
            // server总是覆盖应用程序可能设置的任何内容
            headers.setValue("Server").setString(server);
        }

        // 构建响应 header
        try {
            outputBuffer.sendStatus();

            int size = headers.size();
            for (int i = 0; i < size; i++) {
                outputBuffer.sendHeader(headers.getName(i), headers.getValue(i));
            }
            outputBuffer.endHeaders();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // 如果出错了, 重置 header 缓冲区, 因此可能替换为错误响应.
            outputBuffer.resetHeaderBuffer();
            throw t;
        }

        outputBuffer.commit();
    }

    private static boolean isConnectionClose(MimeHeaders headers) {
        MessageBytes connection = headers.getValue(Constants.CONNECTION);
        if (connection == null) {
            return false;
        }
        return connection.equals(Constants.CLOSE);
    }

    private void prepareSendfile(OutputFilter[] outputFilters) {
        String fileName = (String) request.getAttribute(
                org.apache.coyote.Constants.SENDFILE_FILENAME_ATTR);
        if (fileName == null) {
            sendfileData = null;
        } else {
            // No entity body sent here
            outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
            long pos = ((Long) request.getAttribute(
                    org.apache.coyote.Constants.SENDFILE_FILE_START_ATTR)).longValue();
            long end = ((Long) request.getAttribute(
                    org.apache.coyote.Constants.SENDFILE_FILE_END_ATTR)).longValue();
            sendfileData = socketWrapper.createSendfileData(fileName, pos, end - pos);
        }
    }

    /**
     * Parse host.
     */
    private void parseHost(MessageBytes valueMB) {

        if (valueMB == null || valueMB.isNull()) {
            // HTTP/1.0
            // 如果没有 header, 从端点使用端口信息
            // host将延迟的从socket获取, 如果需要使用 ActionCode#REQ_LOCAL_NAME_ATTRIBUTE
            request.setServerPort(endpoint.getPort());
            return;
        }

        ByteChunk valueBC = valueMB.getByteChunk();
        byte[] valueB = valueBC.getBytes();
        int valueL = valueBC.getLength();
        int valueS = valueBC.getStart();
        int colonPos = -1;
        if (hostNameC.length < valueL) {
            hostNameC = new char[valueL];
        }

        boolean ipv6 = (valueB[valueS] == '[');
        boolean bracketClosed = false;
        for (int i = 0; i < valueL; i++) {
            char b = (char) valueB[i + valueS];
            hostNameC[i] = b;
            if (b == ']') {
                bracketClosed = true;
            } else if (b == ':') {
                if (!ipv6 || bracketClosed) {
                    colonPos = i;
                    break;
                }
            }
        }

        if (colonPos < 0) {
            request.serverName().setChars(hostNameC, 0, valueL);
        } else {
            request.serverName().setChars(hostNameC, 0, colonPos);

            int port = 0;
            int mult = 1;
            for (int i = valueL - 1; i > colonPos; i--) {
                int charValue = HexUtils.getDec(valueB[i + valueS]);
                if (charValue == -1 || charValue > 9) {
                    // Invalid character
                    // 400 - Bad request
                    response.setStatus(400);
                    setErrorState(ErrorState.CLOSE_CLEAN, null);
                    break;
                }
                port = port + (charValue * mult);
                mult = 10 * mult;
            }
            request.setServerPort(port);
        }

    }


    @Override
    protected boolean flushBufferedWrite() throws IOException {
        if (outputBuffer.hasDataToWrite()) {
            if (outputBuffer.flushBuffer(false)) {
                // 缓冲区没有完全刷新，因此重新注册socket进行写入.
                // 注意，这不会通过Response ，因为该级别的写入注册状态应该保持不变.
                // 一旦缓冲区被清空, 下面的代码将调用 Adaptor.asyncDispatch(), 将启用这个事件对应的 Response.
                outputBuffer.registerWriteInterest();
                return true;
            }
        }
        return false;
    }


    @Override
    protected SocketState dispatchEndRequest() {
        if (!keepAlive) {
            return SocketState.CLOSED;
        } else {
            endRequest();
            inputBuffer.nextRequest();
            outputBuffer.nextRequest();
            if (socketWrapper.isReadPending()) {
                return SocketState.LONG;
            } else {
                return SocketState.OPEN;
            }
        }
    }


    @Override
    protected Log getLog() {
        return log;
    }


    /*
     * 没有更多的输入传递给应用程序. 根据错误和期望状态，剩余的输入将被忽略或连接下降.
     */
    private void endRequest() {
        if (getErrorState().isError()) {
            // 如果正在关闭连接, 不要排干输入. 如果servlet拒绝它，上传100GB的文件的方法不会将线程捆绑在一起.
            inputBuffer.setSwallowInput(false);
        } else {
            // 需要在这里再次检查这一点，以防在需要关闭连接的错误发生之前提交响应.
            checkExpectationAndResponseStatus();
        }

        // 完成请求的处理
        if (getErrorState().isIoAllowed()) {
            try {
                inputBuffer.endRequest();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // 500 - Internal Server Error
                // 无法向访问日志添加500, 因为在 Adapter.service 方法中已经写入.
                response.setStatus(500);
                setErrorState(ErrorState.CLOSE_NOW, t);
                log.error(sm.getString("http11processor.request.finish"), t);
            }
        }
        if (getErrorState().isIoAllowed()) {
            try {
                action(ActionCode.COMMIT, null);
                outputBuffer.finishResponse();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                setErrorState(ErrorState.CLOSE_NOW, t);
                log.error(sm.getString("http11processor.response.finish"), t);
            }
        }
    }


    @Override
    protected final void finishResponse() throws IOException {
        outputBuffer.finishResponse();
    }


    @Override
    protected final void ack() {
        // 确认请求
        // 发送一个 100 状态返回, 如果它是有意义的 (尚未提交响应, 而且客户端指定了100个继续的期望)
        if (!response.isCommitted() && request.hasExpectation()) {
            inputBuffer.setSwallowInput(true);
            try {
                outputBuffer.sendAck();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
            }
        }
    }


    @Override
    protected final void flush() throws IOException {
        outputBuffer.flush();
    }


    @Override
    protected final int available(boolean doRead) {
        return inputBuffer.available(doRead);
    }


    @Override
    protected final void setRequestBody(ByteChunk body) {
        InputFilter savedBody = new SavedRequestInputFilter(body);
        savedBody.setRequest(request);

        Http11InputBuffer internalBuffer = (Http11InputBuffer) request.getInputBuffer();
        internalBuffer.addActiveFilter(savedBody);
    }


    @Override
    protected final void setSwallowResponse() {
        outputBuffer.responseFinished = true;
    }


    @Override
    protected final void disableSwallowRequest() {
        inputBuffer.setSwallowInput(false);
    }


    @Override
    protected final void sslReHandShake() throws IOException {
        if (sslSupport != null) {
            // Consume and buffer the request body, so that it does not
            // interfere with the client's handshake messages
            InputFilter[] inputFilters = inputBuffer.getFilters();
            ((BufferedInputFilter) inputFilters[Constants.BUFFERED_FILTER]).setLimit(
                    maxSavePostSize);
            inputBuffer.addActiveFilter(inputFilters[Constants.BUFFERED_FILTER]);

            /*
             * 没有外层的 try/catch, 因为希望在重新协商过程中引发的I/O错误抛出给调用者, 因为他们是致命的连接.
             */
            socketWrapper.doClientAuth(sslSupport);
            try {
                /*
                 * 处理cert 链的错误不会影响客户端连接，因此它们可以在这里记录并忽略.
                 */
                Object sslO = sslSupport.getPeerCertificateChain();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.CERTIFICATE_KEY, sslO);
                }
            } catch (IOException ioe) {
                log.warn(sm.getString("http11processor.socket.ssl"), ioe);
            }
        }
    }


    @Override
    protected final boolean isRequestBodyFullyRead() {
        return inputBuffer.isFinished();
    }


    @Override
    protected final void registerReadInterest() {
        socketWrapper.registerReadInterest();
    }


    @Override
    protected final boolean isReady() {
        return outputBuffer.isReady();
    }


    @Override
    public UpgradeToken getUpgradeToken() {
        return upgradeToken;
    }


    @Override
    protected final void doHttpUpgrade(UpgradeToken upgradeToken) {
        this.upgradeToken = upgradeToken;
        // 停止进一步HTTP输出
        outputBuffer.responseFinished = true;
    }


    @Override
    public ByteBuffer getLeftoverInput() {
        return inputBuffer.getLeftover();
    }


    @Override
    public boolean isUpgrade() {
        return upgradeToken != null;
    }


    /**
     * 触发sendfile 处理.
     *
     * @return 发送文件处理的状态
     */
    private SendfileState processSendfile(SocketWrapperBase<?> socketWrapper) {
        openSocket = keepAlive;
        // Done 等同于未使用 sendfile
        SendfileState result = SendfileState.DONE;
        // Do sendfile as needed: add socket to sendfile and end
        if (sendfileData != null && !getErrorState().isError()) {
            if (keepAlive) {
                if (available(false) == 0) {
                    sendfileData.keepAliveState = SendfileKeepAliveState.OPEN;
                } else {
                    sendfileData.keepAliveState = SendfileKeepAliveState.PIPELINED;
                }
            } else {
                sendfileData.keepAliveState = SendfileKeepAliveState.NONE;
            }
            result = socketWrapper.processSendfile(sendfileData);
            switch (result) {
            case ERROR:
                // Write failed
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("http11processor.sendfile.error"));
                }
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, null);
                //$FALL-THROUGH$
            default:
                sendfileData = null;
            }
        }
        return result;
    }


    @Override
    public final void recycle() {
        getAdapter().checkRecycled(request, response);
        super.recycle();
        inputBuffer.recycle();
        outputBuffer.recycle();
        upgradeToken = null;
        socketWrapper = null;
        sendfileData = null;
    }


    @Override
    public void pause() {
        // NOOP for HTTP
    }
}
