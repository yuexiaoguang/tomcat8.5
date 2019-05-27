package org.apache.catalina.connector;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.naming.NamingException;
import javax.security.auth.Subject;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.MultipartConfigElement;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletResponse;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Session;
import org.apache.catalina.TomcatPrincipal;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.ApplicationFilterChain;
import org.apache.catalina.core.ApplicationMapping;
import org.apache.catalina.core.ApplicationPart;
import org.apache.catalina.core.ApplicationPushBuilder;
import org.apache.catalina.core.ApplicationSessionCookieConfig;
import org.apache.catalina.core.AsyncContextImpl;
import org.apache.catalina.mapper.MappingData;
import org.apache.catalina.servlet4preview.http.PushBuilder;
import org.apache.catalina.servlet4preview.http.ServletMapping;
import org.apache.catalina.util.ParameterMap;
import org.apache.catalina.util.TLSUtil;
import org.apache.catalina.util.URLEncoder;
import org.apache.coyote.ActionCode;
import org.apache.coyote.UpgradeToken;
import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.http.CookieProcessor;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.Parameters;
import org.apache.tomcat.util.http.Parameters.FailReason;
import org.apache.tomcat.util.http.ServerCookie;
import org.apache.tomcat.util.http.ServerCookies;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileUploadBase;
import org.apache.tomcat.util.http.fileupload.FileUploadBase.InvalidContentTypeException;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.apache.tomcat.util.http.fileupload.servlet.ServletRequestContext;
import org.apache.tomcat.util.http.parser.AcceptLanguage;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.res.StringManager;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;

/**
 * Coyote request的包装器对象.
 */
public class Request implements org.apache.catalina.servlet4preview.http.HttpServletRequest {

    private static final Log log = LogFactory.getLog(Request.class);

    // ----------------------------------------------------------- Constructors


    public Request() {
        formats = new SimpleDateFormat[formatsTemplate.length];
        for(int i = 0; i < formats.length; i++) {
            formats[i] = (SimpleDateFormat) formatsTemplate[i].clone();
        }
    }


    // ------------------------------------------------------------- Properties


    protected org.apache.coyote.Request coyoteRequest;

    public void setCoyoteRequest(org.apache.coyote.Request coyoteRequest) {
        this.coyoteRequest = coyoteRequest;
        inputBuffer.setRequest(coyoteRequest);
    }

    public org.apache.coyote.Request getCoyoteRequest() {
        return (this.coyoteRequest);
    }


    // ----------------------------------------------------- Variables


    protected static final TimeZone GMT_ZONE = TimeZone.getTimeZone("GMT");


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Request.class);


    /**
     * 关联的cookies.
     */
    protected Cookie[] cookies = null;


    /**
     * 在getDateHeader()中使用.
     *
     * 注意，因为SimpleDateFormat不是线程安全的, 不能声明formats[] 为一个静态变量.
     */
    protected final SimpleDateFormat formats[];

    private static final SimpleDateFormat formatsTemplate[] = {
        new SimpleDateFormat(FastHttpDateFormat.RFC1123_DATE, Locale.US),
        new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
    };


    /**
     * 默认区域设置.
     */
    protected static final Locale defaultLocale = Locale.getDefault();


    /**
     * 与此请求相关联的属性, 使用属性名作为key.
     */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();


    /**
     * 是否已解析SSL属性以提高应用程序的性能(通常是框架)，尤其是在多次调用{@link Request#getAttributeNames()}时.
     */
    protected boolean sslAttributesParsed = false;


    /**
     * 请求关联的首选区域.
     */
    protected final ArrayList<Locale> locales = new ArrayList<>();


    /**
     * 请求关联的内部注释, 通过Catalina组件和事件监听器.
     */
    private final transient HashMap<String, Object> notes = new HashMap<>();


    /**
     * 身份验证类型.
     */
    protected String authType = null;


    /**
     * 当前调度器类型.
     */
    protected DispatcherType internalDispatcherType = null;


    /**
     * 关联的输入缓冲区.
     */
    protected final InputBuffer inputBuffer = new InputBuffer();


    /**
     * ServletInputStream.
     */
    protected CoyoteInputStream inputStream = new CoyoteInputStream(inputBuffer);


    /**
     * Reader.
     */
    protected CoyoteReader reader = new CoyoteReader(inputBuffer);


    /**
     * 是否使用流.
     */
    protected boolean usingInputStream = false;


    /**
     * Using writer flag.
     */
    protected boolean usingReader = false;


    /**
     * User 主体.
     */
    protected Principal userPrincipal = null;


    /**
     * 请求参数是否解析.
     */
    protected boolean parametersParsed = false;


    /**
     * cookie header是否已经解析为ServerCookies.
     */
    protected boolean cookiesParsed = false;


    /**
     * Cookie是否解析. ServerCookies是否已转换成面向用户的Cookie对象.
     */
    protected boolean cookiesConverted = false;


    /**
     * Secure flag.
     */
    protected boolean secure = false;


    /**
     * 当前AccessControllerContext关联的Subject
     */
    protected transient Subject subject = null;


    /**
     * Post 数据缓冲区.
     */
    protected static final int CACHED_POST_LEN = 8192;
    protected byte[] postData = null;


    /**
     * 在getParametersMap 方法中使用的Map.
     */
    protected ParameterMap<String, String[]> parameterMap = new ParameterMap<>();


    /**
     * 用这个请求上传的部分.
     */
    protected Collection<Part> parts = null;


    /**
     * 抛出的异常, 解析部分的时候.
     */
    protected Exception partsParseException = null;


    /**
     * 此请求的当前活动会话.
     */
    protected Session session = null;


    /**
     * 当前请求调度器路径.
     */
    protected Object requestDispatcherPath = null;


    /**
     * 是在cookie中接收到请求的会话id吗?
     */
    protected boolean requestedSessionCookie = false;


    /**
     * 该请求的会话 ID.
     */
    protected String requestedSessionId = null;


    /**
     * 在URL中接收到请求的会话id吗?
     */
    protected boolean requestedSessionURL = false;


    /**
     * 是否是从SSL会话获得的请求会话ID?
     */
    protected boolean requestedSessionSSL = false;


    /**
     * 解析区域.
     */
    protected boolean localesParsed = false;


    /**
     * 本地端口
     */
    protected int localPort = -1;

    /**
     * 远程地址.
     */
    protected String remoteAddr = null;


    /**
     * 远程主机.
     */
    protected String remoteHost = null;


    /**
     * 远程端口
     */
    protected int remotePort = -1;

    /**
     * 本地地址
     */
    protected String localAddr = null;


    /**
     * 本地地址
     */
    protected String localName = null;

    /**
     * AsyncContext
     */
    private volatile AsyncContextImpl asyncContext = null;

    protected Boolean asyncSupported = null;

    private HttpServletRequest applicationRequest = null;


    // --------------------------------------------------------- Public Methods

    protected void addPathParameter(String name, String value) {
        coyoteRequest.addPathParameter(name, value);
    }

    protected String getPathParameter(String name) {
        return coyoteRequest.getPathParameter(name);
    }

    public void setAsyncSupported(boolean asyncSupported) {
        this.asyncSupported = Boolean.valueOf(asyncSupported);
    }

    /**
     * 释放所有对象引用, 初始化实例变量, 准备重用这个对象.
     */
    public void recycle() {

        internalDispatcherType = null;
        requestDispatcherPath = null;

        authType = null;
        inputBuffer.recycle();
        usingInputStream = false;
        usingReader = false;
        userPrincipal = null;
        subject = null;
        parametersParsed = false;
        if (parts != null) {
            for (Part part: parts) {
                try {
                    part.delete();
                } catch (IOException ignored) {
                    // ApplicationPart.delete() never throws an IOEx
                }
            }
            parts = null;
        }
        partsParseException = null;
        locales.clear();
        localesParsed = false;
        secure = false;
        remoteAddr = null;
        remoteHost = null;
        remotePort = -1;
        localPort = -1;
        localAddr = null;
        localName = null;

        attributes.clear();
        sslAttributesParsed = false;
        notes.clear();

        recycleSessionInfo();
        recycleCookieInfo(false);

        if (Globals.IS_SECURITY_ENABLED || Connector.RECYCLE_FACADES) {
            parameterMap = new ParameterMap<>();
        } else {
            parameterMap.setLocked(false);
            parameterMap.clear();
        }

        mappingData.recycle();
        applicationMapping.recycle();

        applicationRequest = null;
        if (Globals.IS_SECURITY_ENABLED || Connector.RECYCLE_FACADES) {
            if (facade != null) {
                facade.clear();
                facade = null;
            }
            if (inputStream != null) {
                inputStream.clear();
                inputStream = null;
            }
            if (reader != null) {
                reader.clear();
                reader = null;
            }
        }

        asyncSupported = null;
        if (asyncContext!=null) {
            asyncContext.recycle();
        }
        asyncContext = null;
    }


    protected void recycleSessionInfo() {
        if (session != null) {
            try {
                session.endAccess();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.warn(sm.getString("coyoteRequest.sessionEndAccessFail"), t);
            }
        }
        session = null;
        requestedSessionCookie = false;
        requestedSessionId = null;
        requestedSessionURL = false;
        requestedSessionSSL = false;
    }


    protected void recycleCookieInfo(boolean recycleCoyote) {
        cookiesParsed = false;
        cookiesConverted = false;
        cookies = null;
        if (recycleCoyote) {
            getCoyoteRequest().getCookies().recycle();
        }
    }


    // -------------------------------------------------------- Request Methods

    /**
     * 关联的Catalina 连接.
     */
    protected Connector connector;

    /**
     * 返回接收此请求的连接器.
     */
    public Connector getConnector() {
        return this.connector;
    }

    /**
     * 设置接收此请求的连接器.
     *
     * @param connector The new connector
     */
    public void setConnector(Connector connector) {
        this.connector = connector;
    }


    /**
     * 返回正在处理此请求的上下文.
     * <p>
     * 只要适当的上下文被识别，就可以使用.
     * 注意上下文允许<code>getContextPath()</code>返回值, 从而实现对请求URI的解析.
     */
    public Context getContext() {
        return mappingData.context;
    }

    /**
     * @param context 新关联的Context
     * @deprecated Use setters on {@link #getMappingData() MappingData} object.
     * Depending on use case, you may need to update other
     * <code>MappingData</code> fields as well, such as
     * <code>contextSlashCount</code> and <code>host</code>.
     */
    @Deprecated
    public void setContext(Context context) {
        mappingData.context = context;
    }


    /**
     * 与请求相关联的过滤器链.
     */
    protected FilterChain filterChain = null;

    /**
     * 获取与请求相关联的过滤器链.
     */
    public FilterChain getFilterChain() {
        return this.filterChain;
    }

    /**
     * 设置与请求相关联的过滤器链.
     *
     * @param filterChain new filter chain
     */
    public void setFilterChain(FilterChain filterChain) {
        this.filterChain = filterChain;
    }


    /**
     * 返回正在处理此请求的主机.
     */
    public Host getHost() {
        return mappingData.host;
    }


    /**
     * Mapping data.
     */
    protected final MappingData mappingData = new MappingData();
    private final ApplicationMapping applicationMapping = new ApplicationMapping(mappingData);

    /**
     * @return mapping data.
     */
    public MappingData getMappingData() {
        return mappingData;
    }


    /**
     * 请求的外观
     */
    protected RequestFacade facade = null;


    /**
     * 返回原始<code>ServletRequest</code>. 此方法必须由子类实现.
     */
    public HttpServletRequest getRequest() {
        if (facade == null) {
            facade = new RequestFacade(this);
        }
        if (applicationRequest == null) {
            applicationRequest = facade;
        }
        return applicationRequest;
    }


    /**
     * 设置一个包装的HttpServletRequest 传递给应用.
     *
     * @param applicationRequest 传递到应用程序的封装的请求
     */
    public void setRequest(HttpServletRequest applicationRequest) {
        // Check the wrapper wraps this request
        ServletRequest r = applicationRequest;
        while (r instanceof HttpServletRequestWrapper) {
            r = ((HttpServletRequestWrapper) r).getRequest();
        }
        if (r != facade) {
            throw new IllegalArgumentException(sm.getString("request.illegalWrap"));
        }
        this.applicationRequest = applicationRequest;
    }


    /**
     * 这个请求关联的响应.
     */
    protected org.apache.catalina.connector.Response response = null;

    /**
     * 返回这个请求关联的响应.
     */
    public org.apache.catalina.connector.Response getResponse() {
        return this.response;
    }

    /**
     * 设置这个请求关联的响应.
     *
     * @param response The new associated response
     */
    public void setResponse(org.apache.catalina.connector.Response response) {
        this.response = response;
    }

    /**
     * 返回与此请求关联的输入流..
     */
    public InputStream getStream() {
        if (inputStream == null) {
            inputStream = new CoyoteInputStream(inputBuffer);
        }
        return inputStream;
    }

    /**
     * URI 字节到char转换器.
     */
    protected B2CConverter URIConverter = null;

    /**
     * @return URI 转换器.
     */
    protected B2CConverter getURIConverter() {
        return URIConverter;
    }

    /**
     * 设置URI 转换器.
     *
     * @param URIConverter URI转换器
     */
    protected void setURIConverter(B2CConverter URIConverter) {
        this.URIConverter = URIConverter;
    }


    /**
     * 返回处理这个请求的 Wrapper.
     */
    public Wrapper getWrapper() {
        return mappingData.wrapper;
    }

    /**
     * @param wrapper 关联的Wrapper
     * @deprecated Use setters on {@link #getMappingData() MappingData} object.
     * Depending on use case, you may need to update other
     * <code>MappingData</code> fields as well, such as <code>context</code>
     * and <code>contextSlashCount</code>.
     */
    @Deprecated
    public void setWrapper(Wrapper wrapper) {
        mappingData.wrapper = wrapper;
    }


    // ------------------------------------------------- Request Public Methods


    /**
     * 创建并返回一个 ServletInputStream 读取与此请求相关联的内容.
     *
     * @exception IOException 如果发生输入/输出错误
     */
    public ServletInputStream createInputStream()
        throws IOException {
        if (inputStream == null) {
            inputStream = new CoyoteInputStream(inputBuffer);
        }
        return inputStream;
    }


    /**
     * 执行刷新和关闭输入流或读取器所需的任何操作, 在一个操作中.
     *
     * @exception IOException 如果发生输入/输出错误
     */
    public void finishRequest() throws IOException {
        if (response.getStatus() == HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE) {
            checkSwallowInput();
        }
    }


    /**
     * @return 指定名称的绑定到该请求的内部注释的对象, 或者<code>null</code>如果没有这样的绑定.
     *
     * @param name 注释的名称
     */
    public Object getNote(String name) {
        return notes.get(name);
    }


    /**
     * 移除指定名称的注释绑定的所有对象.
     *
     * @param name 注释的名称
     */
    public void removeNote(String name) {
        notes.remove(name);
    }


    /**
     * 设置服务器的端口号来处理此请求.
     *
     * @param port 服务器的端口号
     */
    public void setLocalPort(int port) {
        localPort = port;
    }

    /**
     * 将对象绑定到与此请求相关联的内部注释中的指定名称, 替换此名称的任何现有绑定.
     *
     * @param name 名称
     * @param value 对象
     */
    public void setNote(String name, Object value) {
        notes.put(name, value);
    }


    /**
     * 设置与此请求相关联的远程客户端的IP地址.
     *
     * @param remoteAddr 远程IP 地址
     */
    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }


    /**
     * 设置与此请求关联的远程客户端的完全限定名.
     *
     * @param remoteHost 远程主机名
     */
    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }


    /**
     * 设置<code>isSecure()</code>返回的值.
     *
     * @param secure The new isSecure value
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
    }


    /**
     * 设置服务器的端口号来处理此请求.
     *
     * @param port 服务器端口号
     */
    public void setServerPort(int port) {
        coyoteRequest.setServerPort(port);
    }


    // ------------------------------------------------- ServletRequest Methods



    /**
     * 返回指定的请求属性，如果它存在; 否则返回<code>null</code>.
     *
     * @param name 请求属性的名称
     */
    @Override
    public Object getAttribute(String name) {
        // Special attributes
        SpecialAttributeAdapter adapter = specialAttributes.get(name);
        if (adapter != null) {
            return adapter.get(this, name);
        }

        Object attr = attributes.get(name);

        if (attr != null) {
            return(attr);
        }

        attr = coyoteRequest.getAttribute(name);
        if (attr != null) {
            return attr;
        }
        if (TLSUtil.isTLSRequestAttribute(name)) {
            coyoteRequest.action(ActionCode.REQ_SSL_ATTRIBUTE, coyoteRequest);
            attr = coyoteRequest.getAttribute(Globals.CERTIFICATES_ATTR);
            if (attr != null) {
                attributes.put(Globals.CERTIFICATES_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.CIPHER_SUITE_ATTR);
            if (attr != null) {
                attributes.put(Globals.CIPHER_SUITE_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.KEY_SIZE_ATTR);
            if (attr != null) {
                attributes.put(Globals.KEY_SIZE_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.SSL_SESSION_ID_ATTR);
            if (attr != null) {
                attributes.put(Globals.SSL_SESSION_ID_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.SSL_SESSION_MGR_ATTR);
            if (attr != null) {
                attributes.put(Globals.SSL_SESSION_MGR_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(SSLSupport.PROTOCOL_VERSION_KEY);
            if (attr != null) {
                attributes.put(SSLSupport.PROTOCOL_VERSION_KEY, attr);
            }
            attr = attributes.get(name);
            sslAttributesParsed = true;
        }
        return attr;
    }


    @Override
    public long getContentLengthLong() {
        return coyoteRequest.getContentLengthLong();
    }


    /**
     * 返回此请求的所有请求属性的名称, 或一个空的<code>Enumeration</code>.
     * 注意，返回的属性名称仅为可以通过{@link #setAttribute(String, Object)}设置的属性名.
     * Tomcat内部属性将不包括在内, 它们是通过{@link #getAttribute(String)}获取的.
     * Tomcat的内部属性包括:
     * <ul>
     * <li>{@link Globals#DISPATCHER_TYPE_ATTR}</li>
     * <li>{@link Globals#DISPATCHER_REQUEST_PATH_ATTR}</li>
     * <li>{@link Globals#ASYNC_SUPPORTED_ATTR}</li>
     * <li>{@link Globals#CERTIFICATES_ATTR} (SSL connections only)</li>
     * <li>{@link Globals#CIPHER_SUITE_ATTR} (SSL connections only)</li>
     * <li>{@link Globals#KEY_SIZE_ATTR} (SSL connections only)</li>
     * <li>{@link Globals#SSL_SESSION_ID_ATTR} (SSL connections only)</li>
     * <li>{@link Globals#SSL_SESSION_MGR_ATTR} (SSL connections only)</li>
     * <li>{@link Globals#PARAMETER_PARSE_FAILED_ATTR}</li>
     * </ul>
     * 底层连接器还可以公开请求属性. 也包括"org.apache.tomcat"开头的名称:
     * <ul>
     * <li>{@link Globals#SENDFILE_SUPPORTED_ATTR}</li>
     * </ul>
     * 连接器实现可能返回一些, 这些属性也可以支持附加属性.
     *
     * @return 属性名称枚举
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        if (isSecure() && !sslAttributesParsed) {
            getAttribute(Globals.CERTIFICATES_ATTR);
        }
        // 复制防止 ConcurrentModificationExceptions, 如果用于移除属性
        Set<String> names = new HashSet<>();
        names.addAll(attributes.keySet());
        return Collections.enumeration(names);
    }


    /**
     * @return 此请求的字符编码.
     */
    @Override
    public String getCharacterEncoding() {
        String characterEncoding = coyoteRequest.getCharacterEncoding();
        if (characterEncoding != null) {
            return characterEncoding;
        }

        Context context = getContext();
        if (context != null) {
            return context.getRequestCharacterEncoding();
        }

        return null;
    }


    private Charset getCharset() {
        Charset charset = null;
        try {
            charset = coyoteRequest.getCharset();
        } catch (UnsupportedEncodingException e) {
            // Ignore
        }
        if (charset != null) {
            return charset;
        }

        Context context = getContext();
        if (context != null) {
            String encoding = context.getRequestCharacterEncoding();
            if (encoding != null) {
                try {
                    return B2CConverter.getCharset(encoding);
                } catch (UnsupportedEncodingException e) {
                    // Ignore
                }
            }
        }

        return org.apache.coyote.Constants.DEFAULT_BODY_CHARSET;
    }


    /**
     * @return 此请求的内容长度.
     */
    @Override
    public int getContentLength() {
        return coyoteRequest.getContentLength();
    }


    /**
     * @return 此请求的内容类型.
     */
    @Override
    public String getContentType() {
        return coyoteRequest.getContentType();
    }


    /**
     * 设置此请求的内容类型.
     *
     * @param contentType 内容类型
     */
    public void setContentType(String contentType) {
        coyoteRequest.setContentType(contentType);
    }


    /**
     * 返回此请求的servlet输入流. 默认实现返回<code>createInputStream()</code>创建的输入流.
     *
     * @exception IllegalStateException 如果<code>getReader()</code>已经调用
     * @exception IOException 如果发生输入/输出错误
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {

        if (usingReader) {
            throw new IllegalStateException
                (sm.getString("coyoteRequest.getInputStream.ise"));
        }

        usingInputStream = true;
        if (inputStream == null) {
            inputStream = new CoyoteInputStream(inputBuffer);
        }
        return inputStream;

    }


    /**
     * 返回客户端将接受内容的首选区域, 基于第一个<code>Accept-Language</code> header的值.
     * 如果请求没有指定首选语言, 返回服务器的默认区域设置.
     */
    @Override
    public Locale getLocale() {

        if (!localesParsed) {
            parseLocales();
        }

        if (locales.size() > 0) {
            return locales.get(0);
        }

        return defaultLocale;
    }


    /**
     * 返回客户端接受内容的首选区域集, 基于所有的<code>Accept-Language</code>header.
     * 如果请求没有指定首选语言, 返回服务器的默认区域设置.
     */
    @Override
    public Enumeration<Locale> getLocales() {

        if (!localesParsed) {
            parseLocales();
        }

        if (locales.size() > 0) {
            return Collections.enumeration(locales);
        }
        ArrayList<Locale> results = new ArrayList<>();
        results.add(defaultLocale);
        return Collections.enumeration(results);
    }


    /**
     * 返回指定请求参数的值; 否则返回<code>null</code>. 如果定义了多个值, 返回第一个.
     *
     * @param name 期望的请求参数的名称
     */
    @Override
    public String getParameter(String name) {

        if (!parametersParsed) {
            parseParameters();
        }
        return coyoteRequest.getParameters().getParameter(name);
    }



    /**
     * 返回请求参数的<code>Map</code>.
     * 请求参数是请求发送的额外信息. 对于HTTP servlets, 参数包含在查询字符串或已发布表单数据中.
     *
     * @return 参数名称作为key，参数值作为值的<code>Map</code>.
     */
    @Override
    public Map<String, String[]> getParameterMap() {

        if (parameterMap.isLocked()) {
            return parameterMap;
        }

        Enumeration<String> enumeration = getParameterNames();
        while (enumeration.hasMoreElements()) {
            String name = enumeration.nextElement();
            String[] values = getParameterValues(name);
            parameterMap.put(name, values);
        }

        parameterMap.setLocked(true);
        return parameterMap;
    }


    /**
     * @return 此请求的所有请求参数的名称.
     */
    @Override
    public Enumeration<String> getParameterNames() {

        if (!parametersParsed) {
            parseParameters();
        }
        return coyoteRequest.getParameters().getParameterNames();
    }


    /**
     * 返回指定的请求参数的值; 否则返回<code>null</code>.
     *
     * @param name 请求参数的名称
     */
    @Override
    public String[] getParameterValues(String name) {

        if (!parametersParsed) {
            parseParameters();
        }
        return coyoteRequest.getParameters().getParameterValues(name);
    }


    /**
     * @return 用于此请求的协议和版本.
     */
    @Override
    public String getProtocol() {
        return coyoteRequest.protocol().toString();
    }


    /**
     * 包装了该请求输入流的读取器.
     * 默认实现包装了<code>BufferedReader</code>环绕<code>createInputStream()</code>返回的servlet输入流.
     *
     * @exception IllegalStateException 如果<code>getInputStream()</code>已经被调用
     * @exception IOException 如果发生输入/输出错误
     */
    @Override
    public BufferedReader getReader() throws IOException {

        if (usingInputStream) {
            throw new IllegalStateException
                (sm.getString("coyoteRequest.getReader.ise"));
        }

        usingReader = true;
        inputBuffer.checkConverter();
        if (reader == null) {
            reader = new CoyoteReader(inputBuffer);
        }
        return reader;
    }


    /**
     * 返回指定虚拟路径的实际路径.
     *
     * @param path 要翻译的路径
     *
     * @deprecated As of version 2.1 of the Java Servlet API, use
     *  <code>ServletContext.getRealPath()</code>.
     */
    @Override
    @Deprecated
    public String getRealPath(String path) {

        Context context = getContext();
        if (context == null) {
            return null;
        }
        ServletContext servletContext = context.getServletContext();
        if (servletContext == null) {
            return null;
        }

        try {
            return (servletContext.getRealPath(path));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    /**
     * 返回此请求的远程IP地址.
     */
    @Override
    public String getRemoteAddr() {
        if (remoteAddr == null) {
            coyoteRequest.action
                (ActionCode.REQ_HOST_ADDR_ATTRIBUTE, coyoteRequest);
            remoteAddr = coyoteRequest.remoteAddr().toString();
        }
        return remoteAddr;
    }


    /**
     * 返回此请求的远程主机名.
     */
    @Override
    public String getRemoteHost() {
        if (remoteHost == null) {
            if (!connector.getEnableLookups()) {
                remoteHost = getRemoteAddr();
            } else {
                coyoteRequest.action
                    (ActionCode.REQ_HOST_ATTRIBUTE, coyoteRequest);
                remoteHost = coyoteRequest.remoteHost().toString();
            }
        }
        return remoteHost;
    }

    /**
     * 返回客户端的Internet协议（IP）资源端口或发送请求的最后一个代理.
     */
    @Override
    public int getRemotePort(){
        if (remotePort == -1) {
            coyoteRequest.action
                (ActionCode.REQ_REMOTEPORT_ATTRIBUTE, coyoteRequest);
            remotePort = coyoteRequest.getRemotePort();
        }
        return remotePort;
    }

    /**
     * 返回收到请求的Internet协议（IP）接口的主机名.
     */
    @Override
    public String getLocalName(){
        if (localName == null) {
            coyoteRequest.action
                (ActionCode.REQ_LOCAL_NAME_ATTRIBUTE, coyoteRequest);
            localName = coyoteRequest.localName().toString();
        }
        return localName;
    }

    /**
     * 返回接收请求的接口的Internet协议（IP）地址.
     */
    @Override
    public String getLocalAddr(){
        if (localAddr == null) {
            coyoteRequest.action
                (ActionCode.REQ_LOCAL_ADDR_ATTRIBUTE, coyoteRequest);
            localAddr = coyoteRequest.localAddr().toString();
        }
        return localAddr;
    }


    /**
     * 返回收到请求的接口的Internet协议（IP）端口号.
     */
    @Override
    public int getLocalPort(){
        if (localPort == -1){
            coyoteRequest.action
                (ActionCode.REQ_LOCALPORT_ATTRIBUTE, coyoteRequest);
            localPort = coyoteRequest.getLocalPort();
        }
        return localPort;
    }

    /**
     * 返回将资源包装在指定的路径上的RequestDispatcher, 它可以被解释为相对于当前请求路径.
     *
     * @param path 要包装的资源的路径
     */
    @Override
    public RequestDispatcher getRequestDispatcher(String path) {

        Context context = getContext();
        if (context == null) {
            return null;
        }

        // 如果路径已经是上下文相对的, 传递它
        if (path == null) {
            return null;
        } else if (path.startsWith("/")) {
            return (context.getServletContext().getRequestDispatcher(path));
        }

        // 将请求相对路径转换为上下文相对路径
        String servletPath = (String) getAttribute(
                RequestDispatcher.INCLUDE_SERVLET_PATH);
        if (servletPath == null) {
            servletPath = getServletPath();
        }

        // 添加路径信息, 如果有
        String pathInfo = getPathInfo();
        String requestPath = null;

        if (pathInfo == null) {
            requestPath = servletPath;
        } else {
            requestPath = servletPath + pathInfo;
        }

        int pos = requestPath.lastIndexOf('/');
        String relative = null;
        if (context.getDispatchersUseEncodedPaths()) {
            if (pos >= 0) {
                relative = URLEncoder.DEFAULT.encode(
                        requestPath.substring(0, pos + 1), StandardCharsets.UTF_8) + path;
            } else {
                relative = URLEncoder.DEFAULT.encode(requestPath, StandardCharsets.UTF_8) + path;
            }
        } else {
            if (pos >= 0) {
                relative = requestPath.substring(0, pos + 1) + path;
            } else {
                relative = requestPath + path;
            }
        }
        return context.getServletContext().getRequestDispatcher(relative);
    }


    /**
     * 返回用于生成此请求的协议.
     */
    @Override
    public String getScheme() {
        return coyoteRequest.scheme().toString();
    }


    /**
     * 返回响应此请求的服务器名称.
     */
    @Override
    public String getServerName() {
        return coyoteRequest.serverName().toString();
    }


    /**
     * @return 响应此请求的服务器端口.
     */
    @Override
    public int getServerPort() {
        return coyoteRequest.getServerPort();
    }


    /**
     * @return <code>true</code>这个请求是在安全连接上接收的
     */
    @Override
    public boolean isSecure() {
        return secure;
    }


    /**
     * 删除指定的请求属性.
     *
     * @param name 属性名
     */
    @Override
    public void removeAttribute(String name) {
        // 删除指定的属性
        // 向本地层传递特殊属性
        if (name.startsWith("org.apache.tomcat.")) {
            coyoteRequest.getAttributes().remove(name);
        }

        boolean found = attributes.containsKey(name);
        if (found) {
            Object value = attributes.get(name);
            attributes.remove(name);

            // 通知相关应用程序事件监听器
            notifyAttributeRemoved(name, value);
        } else {
            return;
        }
    }


    /**
     * 将指定的请求属性设置为指定的值.
     *
     * @param name 属性的名称
     * @param value 关联的值
     */
    @Override
    public void setAttribute(String name, Object value) {

        // Name cannot be null
        if (name == null) {
            throw new IllegalArgumentException
                (sm.getString("coyoteRequest.setAttribute.namenull"));
        }

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        // Special attributes
        SpecialAttributeAdapter adapter = specialAttributes.get(name);
        if (adapter != null) {
            adapter.set(this, name, value);
            return;
        }

        // 添加或替换指定的属性
        // 在进行任何更新之前进行安全检查
        if (Globals.IS_SECURITY_ENABLED &&
                name.equals(Globals.SENDFILE_FILENAME_ATTR)) {
            // 使用规范文件名避免任何可能的链接和相对路径问题
            String canonicalPath;
            try {
                canonicalPath = new File(value.toString()).getCanonicalPath();
            } catch (IOException e) {
                throw new SecurityException(sm.getString(
                        "coyoteRequest.sendfileNotCanonical", value), e);
            }
            // Sendfile在Tomcat的安全上下文中执行，所以需要检查在Web应用程序的安全上下文中是否仍然允许访问该文件
            System.getSecurityManager().checkRead(canonicalPath);
            // Update the value so the canonical path is used
            value = canonicalPath;
        }

        Object oldValue = attributes.put(name, value);

        // Pass special attributes to the native layer
        if (name.startsWith("org.apache.tomcat.")) {
            coyoteRequest.setAttribute(name, value);
        }

        // 通知相关事件监听器
        notifyAttributeAssigned(name, value, oldValue);
    }


    /**
     * 通知相关事件监听器, 属性已赋值.
     *
     * @param name 属性名
     * @param value 新属性值
     * @param oldValue 旧属性值
     */
    private void notifyAttributeAssigned(String name, Object value,
            Object oldValue) {
        Context context = getContext();
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0)) {
            return;
        }
        boolean replaced = (oldValue != null);
        ServletRequestAttributeEvent event = null;
        if (replaced) {
            event = new ServletRequestAttributeEvent(
                    context.getServletContext(), getRequest(), name, oldValue);
        } else {
            event = new ServletRequestAttributeEvent(
                    context.getServletContext(), getRequest(), name, value);
        }

        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof ServletRequestAttributeListener)) {
                continue;
            }
            ServletRequestAttributeListener listener =
                (ServletRequestAttributeListener) listeners[i];
            try {
                if (replaced) {
                    listener.attributeReplaced(event);
                } else {
                    listener.attributeAdded(event);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // 错误阀门将选中此异常并将其显示给用户
                attributes.put(RequestDispatcher.ERROR_EXCEPTION, t);
                context.getLogger().error(sm.getString("coyoteRequest.attributeEvent"), t);
            }
        }
    }


    /**
     * 通知相关事件监听器, 属性已删除.
     *
     * @param name 属性名
     * @param value 属性值
     */
    private void notifyAttributeRemoved(String name, Object value) {
        Context context = getContext();
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0)) {
            return;
        }
        ServletRequestAttributeEvent event =
          new ServletRequestAttributeEvent(context.getServletContext(),
                                           getRequest(), name, value);
        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof ServletRequestAttributeListener)) {
                continue;
            }
            ServletRequestAttributeListener listener =
                (ServletRequestAttributeListener) listeners[i];
            try {
                listener.attributeRemoved(event);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // 错误阀门将选中此异常并将其显示给用户
                attributes.put(RequestDispatcher.ERROR_EXCEPTION, t);
                context.getLogger().error(sm.getString("coyoteRequest.attributeEvent"), t);
            }
        }
    }


    /**
     * 重写此请求正文中使用的字符编码的名称. 
     * 在读取请求参数或读取输入之前必须调用此方法.
     *
     * @param enc 要使用的字符编码
     *
     * @exception UnsupportedEncodingException 如果不支持指定的编码
     *
     * @since Servlet 2.3
     */
    @Override
    public void setCharacterEncoding(String enc) throws UnsupportedEncodingException {

        if (usingReader) {
            return;
        }

        // 确认编码名有效
        Charset charset = B2CConverter.getCharset(enc);

        // 保存已验证的编码
        coyoteRequest.setCharset(charset);
    }


    @Override
    public ServletContext getServletContext() {
        return getContext().getServletContext();
     }

    @Override
    public AsyncContext startAsync() {
        return startAsync(getRequest(),response.getResponse());
    }

    @Override
    public AsyncContext startAsync(ServletRequest request,
            ServletResponse response) {
        if (!isAsyncSupported()) {
            IllegalStateException ise =
                    new IllegalStateException(sm.getString("request.asyncNotSupported"));
            log.warn(sm.getString("coyoteRequest.noAsync",
                    StringUtils.join(getNonAsyncClassNames())), ise);
            throw ise;
        }

        if (asyncContext == null) {
            asyncContext = new AsyncContextImpl(this);
        }

        asyncContext.setStarted(getContext(), request, response,
                request==getRequest() && response==getResponse().getResponse());
        asyncContext.setTimeout(getConnector().getAsyncTimeout());

        return asyncContext;
    }


    private Set<String> getNonAsyncClassNames() {
        Set<String> result = new HashSet<>();

        Wrapper wrapper = getWrapper();
        if (!wrapper.isAsyncSupported()) {
            result.add(wrapper.getServletClass());
        }

        FilterChain filterChain = getFilterChain();
        if (filterChain instanceof ApplicationFilterChain) {
            ((ApplicationFilterChain) filterChain).findNonAsyncFilters(result);
        } else {
            result.add(sm.getString("coyoteRequest.filterAsyncSupportUnknown"));
        }

        Container c = wrapper;
        while (c != null) {
            c.getPipeline().findNonAsyncValves(result);
            c = c.getParent();
        }

        return result;
    }

    @Override
    public boolean isAsyncStarted() {
        if (asyncContext == null) {
            return false;
        }

        return asyncContext.isStarted();
    }

    public boolean isAsyncDispatching() {
        if (asyncContext == null) {
            return false;
        }

        AtomicBoolean result = new AtomicBoolean(false);
        coyoteRequest.action(ActionCode.ASYNC_IS_DISPATCHING, result);
        return result.get();
    }

    public boolean isAsyncCompleting() {
        if (asyncContext == null) {
            return false;
        }

        AtomicBoolean result = new AtomicBoolean(false);
        coyoteRequest.action(ActionCode.ASYNC_IS_COMPLETING, result);
        return result.get();
    }

    public boolean isAsync() {
        if (asyncContext == null) {
            return false;
        }

        AtomicBoolean result = new AtomicBoolean(false);
        coyoteRequest.action(ActionCode.ASYNC_IS_ASYNC, result);
        return result.get();
    }

    @Override
    public boolean isAsyncSupported() {
        if (this.asyncSupported == null) {
            return true;
        }

        return asyncSupported.booleanValue();
    }

    @Override
    public AsyncContext getAsyncContext() {
        if (!isAsyncStarted()) {
            throw new IllegalStateException(sm.getString("request.notAsync"));
        }
        return asyncContext;
    }

    public AsyncContextImpl getAsyncContextInternal() {
        return asyncContext;
    }

    @Override
    public DispatcherType getDispatcherType() {
        if (internalDispatcherType == null) {
            return DispatcherType.REQUEST;
        }

        return this.internalDispatcherType;
    }

    // ---------------------------------------------------- HttpRequest Methods


    /**
     * 将cookie添加到与此请求相关联的cookie集合中.
     *
     * @param cookie The new cookie
     */
    public void addCookie(Cookie cookie) {

        if (!cookiesConverted) {
            convertCookies();
        }

        int size = 0;
        if (cookies != null) {
            size = cookies.length;
        }

        Cookie[] newCookies = new Cookie[size + 1];
        for (int i = 0; i < size; i++) {
            newCookies[i] = cookies[i];
        }
        newCookies[size] = cookie;

        cookies = newCookies;
    }


    /**
     * 为该请求添加一组首选区域的区域设置. 第一个添加的Locale 将会是 getLocales()第一个返回的.
     *
     * @param locale The new preferred Locale
     */
    public void addLocale(Locale locale) {
        locales.add(locale);
    }


    /**
     * 清空与此请求相关联的cookie集合.
     */
    public void clearCookies() {
        cookiesParsed = true;
        cookiesConverted = true;
        cookies = null;
    }


    /**
     * 清空与此请求相关联的Locale集合.
     */
    public void clearLocales() {
        locales.clear();
    }


    /**
     * 设置此请求所使用的身份验证类型; 否则设置类型为<code>null</code>.
     * 常用值为 "BASIC", "DIGEST", or "SSL".
     *
     * @param type 使用的认证类型
     */
    public void setAuthType(String type) {
        this.authType = type;
    }


    /**
     * 设置此请求的路径信息. 当关联上下文将请求映射到特定包装器时，通常会调用它.
     *
     * @param path 路径信息
     */
    public void setPathInfo(String path) {
        mappingData.pathInfo.setString(path);
    }


    /**
     * 该请求的请求会话ID是否通过cookie进入.  这通常由HTTP连接器调用, 当它解析请求头时.
     *
     * @param flag The new flag
     */
    public void setRequestedSessionCookie(boolean flag) {
        this.requestedSessionCookie = flag;
    }


    /**
     * 为这个请求设置请求的会话ID.  这通常由HTTP连接器调用, 当它解析请求头时.
     *
     * @param id The new session id
     */
    public void setRequestedSessionId(String id) {
        this.requestedSessionId = id;
    }


    /**
     * 该请求的请求会话ID是否通过URL进入. 这通常由HTTP连接器调用, 当它解析请求头时.
     *
     * @param flag The new flag
     */
    public void setRequestedSessionURL(boolean flag) {
        this.requestedSessionURL = flag;
    }


    /**
     * 此请求的请求会话ID是否通过SSL传入. 这通常由HTTP连接器调用, 当它解析请求头时.
     *
     * @param flag The new flag
     */
    public void setRequestedSessionSSL(boolean flag) {
        this.requestedSessionSSL = flag;
    }


    /**
     * 获取已解码的请求URI.
     */
    public String getDecodedRequestURI() {
        return coyoteRequest.decodedURI().toString();
    }


    /**
     * 获取已解码的请求URI.
     */
    public MessageBytes getDecodedRequestURIMB() {
        return coyoteRequest.decodedURI();
    }


    /**
     * 设置已为此请求进行身份验证的Principal.  此值还用于计算<code>getRemoteUser()</code>方法返回的值.
     *
     * @param principal The user Principal
     */
    public void setUserPrincipal(final Principal principal) {
        if (Globals.IS_SECURITY_ENABLED) {
            if (subject == null) {
                final HttpSession session = getSession(false);
                if (session == null) {
                    // Cache the subject in the request
                    subject = newSubject(principal);
                } else {
                    // Cache the subject in the request and the session
                    subject = (Subject) session.getAttribute(Globals.SUBJECT_ATTR);
                    if (subject == null) {
                        subject = newSubject(principal);
                        session.setAttribute(Globals.SUBJECT_ATTR, subject);
                    } else {
                        subject.getPrincipals().add(principal);
                    }
                }
            } else {
                subject.getPrincipals().add(principal);
            }
        }
        userPrincipal = principal;
    }


    private Subject newSubject(final Principal principal) {
        final Subject result = new Subject();
        result.getPrincipals().add(principal);
        return result;
    }


    // --------------------------------------------- HttpServletRequest Methods

    /**
     * Pulled forward from Servlet 4.0. The method signature may be modified,
     * removed or replaced at any time until Servlet 4.0 becomes final.
     *
     * @return A builder to use to construct the push request
     */
    @Override
    public PushBuilder newPushBuilder() {
        return newPushBuilder(this);
    }


    public PushBuilder newPushBuilder(HttpServletRequest request) {
        AtomicBoolean result = new AtomicBoolean();
        coyoteRequest.action(ActionCode.IS_PUSH_SUPPORTED, result);
        if (result.get()) {
            return new ApplicationPushBuilder(this, request);
        } else {
            return null;
        }
    }


    /**
     * {@inheritDoc}
     *
     * @since Servlet 3.1
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends HttpUpgradeHandler> T upgrade(
            Class<T> httpUpgradeHandlerClass) throws java.io.IOException, ServletException {
        T handler;
        InstanceManager instanceManager = null;
        try {
            // 不要为内部Tomcat类通过实例管理器，因为它们不需要注入
            if (InternalHttpUpgradeHandler.class.isAssignableFrom(httpUpgradeHandlerClass)) {
                handler = httpUpgradeHandlerClass.getConstructor().newInstance();
            } else {
                instanceManager = getContext().getInstanceManager();
                handler = (T) instanceManager.newInstance(httpUpgradeHandlerClass);
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                NamingException | IllegalArgumentException | NoSuchMethodException |
                SecurityException e) {
            throw new ServletException(e);
        }
        UpgradeToken upgradeToken = new UpgradeToken(handler,
                getContext(), instanceManager);

        coyoteRequest.action(ActionCode.UPGRADE, upgradeToken);

        // RFC2616所需的输出. 协议特定的报头应该已经被设置了.
        response.setStatus(HttpServletResponse.SC_SWITCHING_PROTOCOLS);

        return handler;
    }

    /**
     * 返回用于此请求的身份验证类型.
     */
    @Override
    public String getAuthType() {
        return authType;
    }

    /**
     * 返回用于选择请求上下文的请求URI的一部分. 返回的值不会被解码，这也意味着它不规范化.
     */
    @Override
    public String getContextPath() {
        String canonicalContextPath = getServletContext().getContextPath();
        String uri = getRequestURI();
        char[] uriChars = uri.toCharArray();
        int lastSlash = mappingData.contextSlashCount;
        // 根上下文的特殊情况处理
        if (lastSlash == 0) {
            return "";
        }
        int pos = 0;
        // 至少需要上下文路径中的斜杠数量
        while (lastSlash > 0) {
            pos = nextSlash(uriChars, pos + 1);
            if (pos == -1) {
                break;
            }
            lastSlash--;
        }
        // 现在允许路径参数、规范化和/或编码.
        // 基本上, 将候选路径扩展到下一个斜杠，直到解码和规范化的候选路径（路径参数被移除）与规范路径相同.
        String candidate;
        if (pos == -1) {
            candidate = uri;
        } else {
            candidate = uri.substring(0, pos);
        }
        candidate = removePathParameters(candidate);
        candidate = UDecoder.URLDecode(candidate, connector.getURICharset());
        candidate = org.apache.tomcat.util.http.RequestUtil.normalize(candidate);
        boolean match = canonicalContextPath.equals(candidate);
        while (!match && pos != -1) {
            pos = nextSlash(uriChars, pos + 1);
            if (pos == -1) {
                candidate = uri;
            } else {
                candidate = uri.substring(0, pos);
            }
            candidate = removePathParameters(candidate);
            candidate = UDecoder.URLDecode(candidate, connector.getURICharset());
            candidate = org.apache.tomcat.util.http.RequestUtil.normalize(candidate);
            match = canonicalContextPath.equals(candidate);
        }
        if (match) {
            if (pos == -1) {
                return uri;
            } else {
                return uri.substring(0, pos);
            }
        } else {
            // Should never happen
            throw new IllegalStateException(sm.getString(
                    "coyoteRequest.getContextPath.ise", canonicalContextPath, uri));
        }
    }


    private String removePathParameters(String input) {
        int nextSemiColon = input.indexOf(';');
        // Shortcut
        if (nextSemiColon == -1) {
            return input;
        }
        StringBuilder result = new StringBuilder(input.length());
        result.append(input.substring(0, nextSemiColon));
        while (true) {
            int nextSlash = input.indexOf('/', nextSemiColon);
            if (nextSlash == -1) {
                break;
            }
            nextSemiColon = input.indexOf(';', nextSlash);
            if (nextSemiColon == -1) {
                result.append(input.substring(nextSlash));
                break;
            } else {
                result.append(input.substring(nextSlash, nextSemiColon));
            }
        }

        return result.toString();
    }


    private int nextSlash(char[] uri, int startPos) {
        int len = uri.length;
        int pos = startPos;
        while (pos < len) {
            if (uri[pos] == '/') {
                return pos;
            } else if (UDecoder.ALLOW_ENCODED_SLASH && uri[pos] == '%' && pos + 2 < len &&
                    uri[pos+1] == '2' && (uri[pos + 2] == 'f' || uri[pos + 2] == 'F')) {
                return pos;
            }
            pos++;
        }
        return -1;
    }


    /**
     * 返回这个请求接收到的一组Cookie. 触发对Cookie HTTP头的解析，然后转换为Cookie对象.
     */
    @Override
    public Cookie[] getCookies() {
        if (!cookiesConverted) {
            convertCookies();
        }
        return cookies;
    }


    /**
     * 返回与此请求关联的Cookie的服务器表示形式. 如果尚未解析标头，则触发对Cookie HTTP头（但不转换为Cookie对象）的解析.
     */
    public ServerCookies getServerCookies() {
        parseCookies();
        return coyoteRequest.getCookies();
    }


    /**
     * 返回指定日期标头的值; 否则返回 -1.
     *
     * @param name 请求的数据头的名称
     *
     * @exception IllegalArgumentException 如果指定的标头值不能转换为日期
     */
    @Override
    public long getDateHeader(String name) {

        String value = getHeader(name);
        if (value == null) {
            return (-1L);
        }

        // 尝试以多种格式转换日期标头
        long result = FastHttpDateFormat.parseDate(value, formats);
        if (result != (-1L)) {
            return result;
        }
        throw new IllegalArgumentException(value);

    }


    /**
     * 返回指定标头的第一个值; 否则返回<code>null</code>
     *
     * @param name 请求标头的名称
     */
    @Override
    public String getHeader(String name) {
        return coyoteRequest.getHeader(name);
    }


    /**
     * 返回指定标头的所有值; 否则返回一个空枚举.
     *
     * @param name 请求标头的名称
     */
    @Override
    public Enumeration<String> getHeaders(String name) {
        return coyoteRequest.getMimeHeaders().values(name);
    }


    /**
     * @return 使用此请求接收的所有标头的名称.
     */
    @Override
    public Enumeration<String> getHeaderNames() {
        return coyoteRequest.getMimeHeaders().names();
    }


    /**
     * 将指定标头的值作为integer返回, 或 -1 如果此请求没有这样的标头.
     *
     * @param name 请求标头的名称
     *
     * @exception IllegalArgumentException 如果指定的标头值不能转换为整数
     */
    @Override
    public int getIntHeader(String name) {

        String value = getHeader(name);
        if (value == null) {
            return (-1);
        }

        return Integer.parseInt(value);
    }


    @Override
    public ServletMapping getServletMapping() {
        return applicationMapping.getServletMapping();
    }


    /**
     * @return 此请求中使用的HTTP请求方法.
     */
    @Override
    public String getMethod() {
        return coyoteRequest.method().toString();
    }


    /**
     * @return 与此请求关联的路径信息.
     */
    @Override
    public String getPathInfo() {
        return mappingData.pathInfo.toString();
    }


    /**
     * @return 此请求的额外路径信息, 翻译成真实的路径.
     */
    @Override
    public String getPathTranslated() {

        Context context = getContext();
        if (context == null) {
            return null;
        }

        if (getPathInfo() == null) {
            return null;
        }

        return context.getServletContext().getRealPath(getPathInfo());
    }


    /**
     * @return 与此请求关联的查询字符串.
     */
    @Override
    public String getQueryString() {
        return coyoteRequest.queryString().toString();
    }


    /**
     * @return 已对此请求进行身份验证的远程用户的名称.
     */
    @Override
    public String getRemoteUser() {

        if (userPrincipal == null) {
            return null;
        }

        return userPrincipal.getName();
    }


    /**
     * 获取请求路径.
     */
    public MessageBytes getRequestPathMB() {
        return mappingData.requestPath;
    }


    /**
     * @return 此请求中包含的会话标识符.
     */
    @Override
    public String getRequestedSessionId() {
        return requestedSessionId;
    }


    /**
     * @return 此请求的请求URI.
     */
    @Override
    public String getRequestURI() {
        return coyoteRequest.requestURI().toString();
    }


    /**
     * 重建用于发送请求的客户端的URL.
     * 返回的URL包含一个协议, 服务器名称, 端口号, 和服务器路径, 但它不包含查询字符串参数.
     * <p>
     * 因为这个方法返回一个<code>StringBuffer</code>, 不是一个<code>String</code>, 您可以轻松地修改URL,
     * 例如, 追加查询参数.
     * <p>
     * 此方法对于创建重定向消息和报告错误非常有用.
     *
     * @return 包含重新构造的URL的<code>StringBuffer</code>对象
     */
    @Override
    public StringBuffer getRequestURL() {

        StringBuffer url = new StringBuffer();
        String scheme = getScheme();
        int port = getServerPort();
        if (port < 0)
         {
            port = 80; // Work around java.net.URL bug
        }

        url.append(scheme);
        url.append("://");
        url.append(getServerName());
        if ((scheme.equals("http") && (port != 80))
            || (scheme.equals("https") && (port != 443))) {
            url.append(':');
            url.append(port);
        }
        url.append(getRequestURI());

        return url;
    }


    /**
     * @return 用于选择将处理此请求的servlet的请求URI的一部分.
     */
    @Override
    public String getServletPath() {
        return (mappingData.wrapperPath.toString());
    }


    /**
     * @return 与此请求关联的会话, 必要时创建一个.
     */
    @Override
    public HttpSession getSession() {
        Session session = doGetSession(true);
        if (session == null) {
            return null;
        }

        return session.getSession();
    }


    /**
     * @return 与此请求关联的会话, 必要时创建一个.
     *
     * @param create 如果不存在，是否创建新会话
     */
    @Override
    public HttpSession getSession(boolean create) {
        Session session = doGetSession(create);
        if (session == null) {
            return null;
        }

        return session.getSession();
    }


    /**
     * @return <code>true</code> 如果包含在这个请求中的会话标识符来自cookie.
     */
    @Override
    public boolean isRequestedSessionIdFromCookie() {

        if (requestedSessionId == null) {
            return false;
        }

        return requestedSessionCookie;
    }


    /**
     * @return <code>true</code> 如果这个请求中包含的会话标识符来自请求URI.
     */
    @Override
    public boolean isRequestedSessionIdFromURL() {

        if (requestedSessionId == null) {
            return false;
        }

        return requestedSessionURL;
    }


    /**
     * @return <code>true</code> 如果这个请求中包含的会话标识符来自请求URI.
     *
     * @deprecated As of Version 2.1 of the Java Servlet API, use
     *  <code>isRequestedSessionIdFromURL()</code> instead.
     */
    @Override
    @Deprecated
    public boolean isRequestedSessionIdFromUrl() {
        return (isRequestedSessionIdFromURL());
    }


    /**
     * @return <code>true</code>如果此请求中包含的会话标识符标识会话有效.
     */
    @Override
    public boolean isRequestedSessionIdValid() {

        if (requestedSessionId == null) {
            return false;
        }

        Context context = getContext();
        if (context == null) {
            return false;
        }

        Manager manager = context.getManager();
        if (manager == null) {
            return false;
        }

        Session session = null;
        try {
            session = manager.findSession(requestedSessionId);
        } catch (IOException e) {
            // Can't find the session
        }

        if ((session == null) || !session.isValid()) {
            // 检查并行部署上下文
            if (getMappingData().contexts == null) {
                return false;
            } else {
                for (int i = (getMappingData().contexts.length); i > 0; i--) {
                    Context ctxt = getMappingData().contexts[i - 1];
                    try {
                        if (ctxt.getManager().findSession(requestedSessionId) !=
                                null) {
                            return true;
                        }
                    } catch (IOException e) {
                        // Ignore
                    }
                }
                return false;
            }
        }
        return true;
    }


    /**
     * @return <code>true</code> 如果经过验证的用户主体拥有指定的角色名.
     *
     * @param role 要验证的角色名
     */
    @Override
    public boolean isUserInRole(String role) {

        // 已验证的主体?
        if (userPrincipal == null) {
            return false;
        }

        // 将检查角色配置的Realm
        Context context = getContext();
        if (context == null) {
            return false;
        }

        // 如果角色是 "*" ，则返回值必须是 false
        // Servlet 31, section 13.3
        if ("*".equals(role)) {
            return false;
        }

        // 如果角色是 "**" , 除非应用程序定义具有该名称的角色, 只检查用户是否已被认证
        if ("**".equals(role) && !context.findSecurityRole("**")) {
            return userPrincipal != null;
        }

        Realm realm = context.getRealm();
        if (realm == null) {
            return false;
        }

        // 检查直接定义的角色， <security-role>
        return (realm.hasRole(getWrapper(), userPrincipal, role));
    }


    /**
     * @return 为此请求已被认证的主体.
     */
    public Principal getPrincipal() {
        return userPrincipal;
    }


    /**
     * @return 为此请求已被认证的主体.
     */
    @Override
    public Principal getUserPrincipal() {
        if (userPrincipal instanceof TomcatPrincipal) {
            GSSCredential gssCredential =
                    ((TomcatPrincipal) userPrincipal).getGssCredential();
            if (gssCredential != null) {
                int left = -1;
                try {
                    left = gssCredential.getRemainingLifetime();
                } catch (GSSException e) {
                    log.warn(sm.getString("coyoteRequest.gssLifetimeFail",
                            userPrincipal.getName()), e);
                }
                if (left == 0) {
                    // GSS 凭据已经到期. 需要重新认证.
                    try {
                        logout();
                    } catch (ServletException e) {
                        // 永远不会发生(no code called by logout()
                        // throws a ServletException
                    }
                    return null;
                }
            }
            return ((TomcatPrincipal) userPrincipal).getUserPrincipal();
        }

        return userPrincipal;
    }


    /**
     * @return 与此请求关联的会话, 必要时创建一个.
     */
    public Session getSessionInternal() {
        return doGetSession(true);
    }


    /**
     * 更改此请求关联的会话的ID. 有一些事情可能会触发ID更改.
     * 这些包括在集群中的节点之间移动和在认证过程中防止会话固定.
     *
     * @param newSessionId   更改会话ID的会话
     */
    public void changeSessionId(String newSessionId) {
        // 如果有一个旧的会话ID，必须仔细检查，才能调用
        if (requestedSessionId != null && requestedSessionId.length() > 0) {
            requestedSessionId = newSessionId;
        }

        Context context = getContext();
        if (context != null
                && !context.getServletContext()
                        .getEffectiveSessionTrackingModes()
                        .contains(SessionTrackingMode.COOKIE)) {
            return;
        }

        if (response != null) {
            Cookie newCookie =
                ApplicationSessionCookieConfig.createSessionCookie(context,
                        newSessionId, isSecure());
            response.addSessionCookieInternal(newCookie);
        }
    }

    /**
     * 更改与此请求关联的会话ID.
     *
     * @return 更改之前的旧会话ID
     * @since Servlet 3.1
     */
    @Override
    public String changeSessionId() {

        Session session = this.getSessionInternal(false);
        if (session == null) {
            throw new IllegalStateException(
                sm.getString("coyoteRequest.changeSessionId"));
        }

        Manager manager = this.getContext().getManager();
        manager.changeSessionId(session);

        String newSessionId = session.getId();
        this.changeSessionId(newSessionId);

        return newSessionId;
    }

    /**
     * @return 与此请求关联的会话, 必要时创建一个.
     *
     * @param create 如果不存在，是否创建新会话
     */
    public Session getSessionInternal(boolean create) {
        return doGetSession(create);
    }


    /**
     * @return <code>true</code>如果有解析的参数
     */
    public boolean isParametersParsed() {
        return parametersParsed;
    }


    /**
     * @return <code>true</code>如果已尝试读取请求主体，而且读取了所有请求正文.
     */
    public boolean isFinished() {
        return coyoteRequest.isFinished();
    }


    /**
     * 检查中止上传的配置，如果这样配置, 禁用任何剩余输入的吞咽，并在写入响应后关闭连接.
     */
    protected void checkSwallowInput() {
        Context context = getContext();
        if (context != null && !context.getSwallowAbortedUploads()) {
            coyoteRequest.action(ActionCode.DISABLE_SWALLOW_INPUT, null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean authenticate(HttpServletResponse response)
    throws IOException, ServletException {
        if (response.isCommitted()) {
            throw new IllegalStateException(
                    sm.getString("coyoteRequest.authenticate.ise"));
        }

        return getContext().getAuthenticator().authenticate(this, response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void login(String username, String password)
    throws ServletException {
        if (getAuthType() != null || getRemoteUser() != null ||
                getUserPrincipal() != null) {
            throw new ServletException(
                    sm.getString("coyoteRequest.alreadyAuthenticated"));
        }

        Context context = getContext();
        if (context.getAuthenticator() == null) {
            throw new ServletException("no authenticator");
        }

        context.getAuthenticator().login(username, password, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logout() throws ServletException {
        getContext().getAuthenticator().logout(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Part> getParts() throws IOException, IllegalStateException,
            ServletException {

        parseParts(true);

        if (partsParseException != null) {
            if (partsParseException instanceof IOException) {
                throw (IOException) partsParseException;
            } else if (partsParseException instanceof IllegalStateException) {
                throw (IllegalStateException) partsParseException;
            } else if (partsParseException instanceof ServletException) {
                throw (ServletException) partsParseException;
            }
        }

        return parts;
    }

    private void parseParts(boolean explicit) {

        // 如果部分已被解析，则立即返回
        if (parts != null || partsParseException != null) {
            return;
        }

        Context context = getContext();
        MultipartConfigElement mce = getWrapper().getMultipartConfigElement();

        if (mce == null) {
            if(context.getAllowCasualMultipartParsing()) {
                mce = new MultipartConfigElement(null,
                                                 connector.getMaxPostSize(),
                                                 connector.getMaxPostSize(),
                                                 connector.getMaxPostSize());
            } else {
                if (explicit) {
                    partsParseException = new IllegalStateException(
                            sm.getString("coyoteRequest.noMultipartConfig"));
                    return;
                } else {
                    parts = Collections.emptyList();
                    return;
                }
            }
        }

        Parameters parameters = coyoteRequest.getParameters();
        parameters.setLimit(getConnector().getMaxParameterCount());

        boolean success = false;
        try {
            File location;
            String locationStr = mce.getLocation();
            if (locationStr == null || locationStr.length() == 0) {
                location = ((File) context.getServletContext().getAttribute(
                        ServletContext.TEMPDIR));
            } else {
                // 如果是相对的, 相对于 TEMPDIR
                location = new File(locationStr);
                if (!location.isAbsolute()) {
                    location = new File(
                            (File) context.getServletContext().getAttribute(
                                        ServletContext.TEMPDIR),
                                        locationStr).getAbsoluteFile();
                }
            }

            if (!location.isDirectory()) {
                parameters.setParseFailedReason(FailReason.MULTIPART_CONFIG_INVALID);
                partsParseException = new IOException(
                        sm.getString("coyoteRequest.uploadLocationInvalid",
                                location));
                return;
            }


            // 创建一个新文件上传处理程序
            DiskFileItemFactory factory = new DiskFileItemFactory();
            try {
                factory.setRepository(location.getCanonicalFile());
            } catch (IOException ioe) {
                parameters.setParseFailedReason(FailReason.IO_ERROR);
                partsParseException = ioe;
                return;
            }
            factory.setSizeThreshold(mce.getFileSizeThreshold());

            ServletFileUpload upload = new ServletFileUpload();
            upload.setFileItemFactory(factory);
            upload.setFileSizeMax(mce.getMaxFileSize());
            upload.setSizeMax(mce.getMaxRequestSize());

            parts = new ArrayList<>();
            try {
                List<FileItem> items =
                        upload.parseRequest(new ServletRequestContext(this));
                int maxPostSize = getConnector().getMaxPostSize();
                int postSize = 0;
                Charset charset = getCharset();
                for (FileItem item : items) {
                    ApplicationPart part = new ApplicationPart(item, location);
                    parts.add(part);
                    if (part.getSubmittedFileName() == null) {
                        String name = part.getName();
                        String value = null;
                        try {
                            value = part.getString(charset.name());
                        } catch (UnsupportedEncodingException uee) {
                            // Not possible
                        }
                        if (maxPostSize >= 0) {
                            // 必须计算大小. 不完全准确但足够接近.
                            postSize += name.getBytes(charset).length;
                            if (value != null) {
                                // Equals sign
                                postSize++;
                                // Value length
                                postSize += part.getSize();
                            }
                            // Value separator
                            postSize++;
                            if (postSize > maxPostSize) {
                                parameters.setParseFailedReason(FailReason.POST_TOO_LARGE);
                                throw new IllegalStateException(sm.getString(
                                        "coyoteRequest.maxPostSizeExceeded"));
                            }
                        }
                        parameters.addParameter(name, value);
                    }
                }

                success = true;
            } catch (InvalidContentTypeException e) {
                parameters.setParseFailedReason(FailReason.INVALID_CONTENT_TYPE);
                partsParseException = new ServletException(e);
            } catch (FileUploadBase.SizeException e) {
                parameters.setParseFailedReason(FailReason.POST_TOO_LARGE);
                checkSwallowInput();
                partsParseException = new IllegalStateException(e);
            } catch (FileUploadException e) {
                parameters.setParseFailedReason(FailReason.IO_ERROR);
                partsParseException = new IOException(e);
            } catch (IllegalStateException e) {
                // addParameters() will set parseFailedReason
                checkSwallowInput();
                partsParseException = e;
            }
        } finally {
            // 这看起来很奇怪，但却是正确的. setParseFailedReason() 只设置失败原因，如果当前没有设置.
            if (partsParseException != null || !success) {
                parameters.setParseFailedReason(FailReason.UNKNOWN);
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Part getPart(String name) throws IOException, IllegalStateException,
            ServletException {
        Collection<Part> c = getParts();
        Iterator<Part> iterator = c.iterator();
        while (iterator.hasNext()) {
            Part part = iterator.next();
            if (name.equals(part.getName())) {
                return part;
            }
        }
        return null;
    }


    // ------------------------------------------------------ Protected Methods

    protected Session doGetSession(boolean create) {

        // 如果没有指定上下文，则不能是一个会话
        Context context = getContext();
        if (context == null) {
            return (null);
        }

        // 如果存在，返回当前会话并有效
        if ((session != null) && !session.isValid()) {
            session = null;
        }
        if (session != null) {
            return (session);
        }

        // 返回请求的会话，如果它存在并且是有效的
        Manager manager = context.getManager();
        if (manager == null) {
            return (null);      // Sessions are not supported
        }
        if (requestedSessionId != null) {
            try {
                session = manager.findSession(requestedSessionId);
            } catch (IOException e) {
                session = null;
            }
            if ((session != null) && !session.isValid()) {
                session = null;
            }
            if (session != null) {
                session.access();
                return (session);
            }
        }

        // 如果请求，则创建新会话，并且不提交响应
        if (!create) {
            return (null);
        }
        if (response != null
                && context.getServletContext()
                        .getEffectiveSessionTrackingModes()
                        .contains(SessionTrackingMode.COOKIE)
                && response.getResponse().isCommitted()) {
            throw new IllegalStateException(
                    sm.getString("coyoteRequest.sessionCreateCommitted"));
        }

        // 在非常有限的情况下重复使用客户端提供的会话ID.
        String sessionId = getRequestedSessionId();
        if (requestedSessionSSL) {
            // 如果从SSL握手获得会话ID，则使用它.
        } else if (("/".equals(context.getSessionCookiePath())
                && isRequestedSessionIdFromCookie())) {
            /* 这是常见的（ISH）用例: 在同一主机上多个Web应用程序之间使用同一会话ID. 典型地，这是Portlet实现使用的.
             * 只有通过cookies跟踪会话才有效. cookie 必须有"/"路径，否则将不提供对所有Web应用程序的请求.
             *
             * 客户端提供的任何会话ID都应该是已经存在于主机上某个位置的会话. 检查上下文是否被配置.
             */
            if (context.getValidateClientProvidedNewSessionId()) {
                boolean found = false;
                for (Container container : getHost().findChildren()) {
                    Manager m = ((Context) container).getManager();
                    if (m != null) {
                        try {
                            if (m.findSession(sessionId) != null) {
                                found = true;
                                break;
                            }
                        } catch (IOException e) {
                            // Ignore. 这个管理器的问题将在其他地方处理.
                        }
                    }
                }
                if (!found) {
                    sessionId = null;
                }
            }
        } else {
            sessionId = null;
        }
        session = manager.createSession(sessionId);

        // 基于该会话创建新会话cookie
        if (session != null
                && context.getServletContext()
                        .getEffectiveSessionTrackingModes()
                        .contains(SessionTrackingMode.COOKIE)) {
            Cookie cookie =
                ApplicationSessionCookieConfig.createSessionCookie(
                        context, session.getIdInternal(), isSecure());

            response.addSessionCookieInternal(cookie);
        }

        if (session == null) {
            return null;
        }

        session.access();
        return session;
    }

    protected String unescape(String s) {
        if (s==null) {
            return null;
        }
        if (s.indexOf('\\') == -1) {
            return s;
        }
        StringBuilder buf = new StringBuilder();
        for (int i=0; i<s.length(); i++) {
            char c = s.charAt(i);
            if (c!='\\') {
                buf.append(c);
            } else {
                if (++i >= s.length())
                 {
                    throw new IllegalArgumentException();//invalid escape, hence invalid cookie
                }
                c = s.charAt(i);
                buf.append(c);
            }
        }
        return buf.toString();
    }

    /**
     * 解析cookies. 这只将cookie解析为内存中高效的ServerCookies结构. 它不填充Cookie 对象.
     */
    protected void parseCookies() {
        if (cookiesParsed) {
            return;
        }

        cookiesParsed = true;

        ServerCookies serverCookies = coyoteRequest.getCookies();
        serverCookies.setLimit(connector.getMaxCookieCount());
        CookieProcessor cookieProcessor = getContext().getCookieProcessor();
        cookieProcessor.parseCookieHeader(coyoteRequest.getMimeHeaders(), serverCookies);
    }

    /**
     * 转换已分析的Cookie (如果没有解析cookie头，则首先解析cookie头).
     */
    protected void convertCookies() {
        if (cookiesConverted) {
            return;
        }

        cookiesConverted = true;

        if (getContext() == null) {
            return;
        }

        parseCookies();

        ServerCookies serverCookies = coyoteRequest.getCookies();
        CookieProcessor cookieProcessor = getContext().getCookieProcessor();

        int count = serverCookies.getCookieCount();
        if (count <= 0) {
            return;
        }

        cookies = new Cookie[count];

        int idx=0;
        for (int i = 0; i < count; i++) {
            ServerCookie scookie = serverCookies.getCookie(i);
            try {
                /*
                we must unescape the '\\' escape character
                */
                Cookie cookie = new Cookie(scookie.getName().toString(),null);
                int version = scookie.getVersion();
                cookie.setVersion(version);
                scookie.getValue().getByteChunk().setCharset(cookieProcessor.getCharset());
                cookie.setValue(unescape(scookie.getValue().toString()));
                cookie.setPath(unescape(scookie.getPath().toString()));
                String domain = scookie.getDomain().toString();
                if (domain!=null)
                 {
                    cookie.setDomain(unescape(domain));//avoid NPE
                }
                String comment = scookie.getComment().toString();
                cookie.setComment(version==1?unescape(comment):null);
                cookies[idx++] = cookie;
            } catch(IllegalArgumentException e) {
                // Ignore bad cookie
            }
        }
        if( idx < count ) {
            Cookie [] ncookies = new Cookie[idx];
            System.arraycopy(cookies, 0, ncookies, 0, idx);
            cookies = ncookies;
        }
    }


    /**
     * Parse request parameters.
     */
    protected void parseParameters() {

        parametersParsed = true;

        Parameters parameters = coyoteRequest.getParameters();
        boolean success = false;
        try {
            // 每次通过JMX改变限制
            parameters.setLimit(getConnector().getMaxParameterCount());

            // getCharacterEncoding() 可能已被重写，以搜索包含请求编码的隐藏表单字段
            Charset charset = getCharset();

            boolean useBodyEncodingForURI = connector.getUseBodyEncodingForURI();
            parameters.setCharset(charset);
            if (useBodyEncodingForURI) {
                parameters.setQueryStringCharset(charset);
            }
            // Note: 如果 !useBodyEncodingForURI, the query string encoding is
            //       that set towards the start of CoyoyeAdapter.service()

            parameters.handleQueryParameters();

            if (usingInputStream || usingReader) {
                success = true;
                return;
            }

            if( !getConnector().isParseBodyMethod(getMethod()) ) {
                success = true;
                return;
            }

            String contentType = getContentType();
            if (contentType == null) {
                contentType = "";
            }
            int semicolon = contentType.indexOf(';');
            if (semicolon >= 0) {
                contentType = contentType.substring(0, semicolon).trim();
            } else {
                contentType = contentType.trim();
            }

            if ("multipart/form-data".equals(contentType)) {
                parseParts(false);
                success = true;
                return;
            }

            if (!("application/x-www-form-urlencoded".equals(contentType))) {
                success = true;
                return;
            }

            int len = getContentLength();

            if (len > 0) {
                int maxPostSize = connector.getMaxPostSize();
                if ((maxPostSize >= 0) && (len > maxPostSize)) {
                    Context context = getContext();
                    if (context != null && context.getLogger().isDebugEnabled()) {
                        context.getLogger().debug(
                                sm.getString("coyoteRequest.postTooLarge"));
                    }
                    checkSwallowInput();
                    parameters.setParseFailedReason(FailReason.POST_TOO_LARGE);
                    return;
                }
                byte[] formData = null;
                if (len < CACHED_POST_LEN) {
                    if (postData == null) {
                        postData = new byte[CACHED_POST_LEN];
                    }
                    formData = postData;
                } else {
                    formData = new byte[len];
                }
                try {
                    if (readPostBody(formData, len) != len) {
                        parameters.setParseFailedReason(FailReason.REQUEST_BODY_INCOMPLETE);
                        return;
                    }
                } catch (IOException e) {
                    // Client disconnect
                    Context context = getContext();
                    if (context != null && context.getLogger().isDebugEnabled()) {
                        context.getLogger().debug(
                                sm.getString("coyoteRequest.parseParameters"),
                                e);
                    }
                    parameters.setParseFailedReason(FailReason.CLIENT_DISCONNECT);
                    return;
                }
                parameters.processParameters(formData, 0, len);
            } else if ("chunked".equalsIgnoreCase(
                    coyoteRequest.getHeader("transfer-encoding"))) {
                byte[] formData = null;
                try {
                    formData = readChunkedPostBody();
                } catch (IllegalStateException ise) {
                    // chunkedPostTooLarge error
                    parameters.setParseFailedReason(FailReason.POST_TOO_LARGE);
                    Context context = getContext();
                    if (context != null && context.getLogger().isDebugEnabled()) {
                        context.getLogger().debug(
                                sm.getString("coyoteRequest.parseParameters"),
                                ise);
                    }
                    return;
                } catch (IOException e) {
                    // Client disconnect
                    parameters.setParseFailedReason(FailReason.CLIENT_DISCONNECT);
                    Context context = getContext();
                    if (context != null && context.getLogger().isDebugEnabled()) {
                        context.getLogger().debug(
                                sm.getString("coyoteRequest.parseParameters"),
                                e);
                    }
                    return;
                }
                if (formData != null) {
                    parameters.processParameters(formData, 0, formData.length);
                }
            }
            success = true;
        } finally {
            if (!success) {
                parameters.setParseFailedReason(FailReason.UNKNOWN);
            }
        }

    }


    /**
     * 读取数组中的POST正文.
     *
     * @param body 将读取主体的字节数组
     * @param len 主体长度
     * @return 已读取的字节计数
     * @throws IOException 如果发生了IO异常
     */
    protected int readPostBody(byte[] body, int len)
        throws IOException {

        int offset = 0;
        do {
            int inputLen = getStream().read(body, offset, len - offset);
            if (inputLen <= 0) {
                return offset;
            }
            offset += inputLen;
        } while ((len - offset) > 0);
        return len;

    }


    /**
     * 读取分块的post主体.
     *
     * @return 作为字节数组的post主体
     * @throws IOException 如果发生了IO异常
     */
    protected byte[] readChunkedPostBody() throws IOException {
        ByteChunk body = new ByteChunk();

        byte[] buffer = new byte[CACHED_POST_LEN];

        int len = 0;
        while (len > -1) {
            len = getStream().read(buffer, 0, CACHED_POST_LEN);
            if (connector.getMaxPostSize() >= 0 &&
                    (body.getLength() + len) > connector.getMaxPostSize()) {
                // Too much data
                checkSwallowInput();
                throw new IllegalStateException(
                        sm.getString("coyoteRequest.chunkedPostTooLarge"));
            }
            if (len > 0) {
                body.append(buffer, 0, len);
            }
        }
        if (body.getLength() == 0) {
            return null;
        }
        if (body.getLength() < body.getBuffer().length) {
            int length = body.getLength();
            byte[] result = new byte[length];
            System.arraycopy(body.getBuffer(), 0, result, 0, length);
            return result;
        }

        return body.getBuffer();
    }


    /**
     * 解析请求区域.
     */
    protected void parseLocales() {

        localesParsed = true;

        // 存储已在本地集合中请求的累积语言, 按质量值排序 (所以可以按降序添加区域). 
        // ArrayLists 包含添加的相应区域
        TreeMap<Double, ArrayList<Locale>> locales = new TreeMap<>();

        Enumeration<String> values = getHeaders("accept-language");

        while (values.hasMoreElements()) {
            String value = values.nextElement();
            parseLocalesHeader(value, locales);
        }

        // 以最高-最低的顺序处理质量值(由于在创建密钥时否定了Double 值)
        for (ArrayList<Locale> list : locales.values()) {
            for (Locale locale : list) {
                addLocale(locale);
            }
        }
    }


    /**
     * 解析 accept-language header值.
     *
     * @param value the header value
     * @param locales 将保存结果的Map
     */
    protected void parseLocalesHeader(String value, TreeMap<Double, ArrayList<Locale>> locales) {

        List<AcceptLanguage> acceptLanguages;
        try {
            acceptLanguages = AcceptLanguage.parse(new StringReader(value));
        } catch (IOException e) {
            // 未形成的 header被忽略. 在不可能发生IOException异常的情况下，做同样的事情.
            return;
        }

        for (AcceptLanguage acceptLanguage : acceptLanguages) {
            // 在该质量级别的区域列表中添加一个新的区域设置
            Double key = Double.valueOf(-acceptLanguage.getQuality());  // 颠倒顺序
            ArrayList<Locale> values = locales.get(key);
            if (values == null) {
                values = new ArrayList<>();
                locales.put(key, values);
            }
            values.add(acceptLanguage.getLocale());
        }
    }


    // ----------------------------------------------------- Special attributes handling

    private static interface SpecialAttributeAdapter {
        Object get(Request request, String name);

        void set(Request request, String name, Object value);

        // 没有特殊属性支持移除
        // void remove(Request request, String name);
    }

    private static final Map<String, SpecialAttributeAdapter> specialAttributes
        = new HashMap<>();

    static {
        specialAttributes.put(Globals.DISPATCHER_TYPE_ATTR,
                new SpecialAttributeAdapter() {
                    @Override
                    public Object get(Request request, String name) {
                        return (request.internalDispatcherType == null) ? DispatcherType.REQUEST
                                : request.internalDispatcherType;
                    }

                    @Override
                    public void set(Request request, String name, Object value) {
                        request.internalDispatcherType = (DispatcherType) value;
                    }
                });
        specialAttributes.put(Globals.DISPATCHER_REQUEST_PATH_ATTR,
                new SpecialAttributeAdapter() {
                    @Override
                    public Object get(Request request, String name) {
                        return (request.requestDispatcherPath == null) ? request
                                .getRequestPathMB().toString()
                                : request.requestDispatcherPath.toString();
                    }

                    @Override
                    public void set(Request request, String name, Object value) {
                        request.requestDispatcherPath = value;
                    }
                });
        specialAttributes.put(Globals.ASYNC_SUPPORTED_ATTR,
                new SpecialAttributeAdapter() {
                    @Override
                    public Object get(Request request, String name) {
                        return request.asyncSupported;
                    }

                    @Override
                    public void set(Request request, String name, Object value) {
                        Boolean oldValue = request.asyncSupported;
                        request.asyncSupported = (Boolean)value;
                        request.notifyAttributeAssigned(name, value, oldValue);
                    }
                });
        specialAttributes.put(Globals.GSS_CREDENTIAL_ATTR,
                new SpecialAttributeAdapter() {
                    @Override
                    public Object get(Request request, String name) {
                        if (request.userPrincipal instanceof TomcatPrincipal) {
                            return ((TomcatPrincipal) request.userPrincipal)
                                    .getGssCredential();
                        }
                        return null;
                    }

                    @Override
                    public void set(Request request, String name, Object value) {
                        // NO-OP
                    }
                });
        specialAttributes.put(Globals.PARAMETER_PARSE_FAILED_ATTR,
                new SpecialAttributeAdapter() {
                    @Override
                    public Object get(Request request, String name) {
                        if (request.getCoyoteRequest().getParameters()
                                .isParseFailed()) {
                            return Boolean.TRUE;
                        }
                        return null;
                    }

                    @Override
                    public void set(Request request, String name, Object value) {
                        // NO-OP
                    }
                });
        specialAttributes.put(Globals.PARAMETER_PARSE_FAILED_REASON_ATTR,
                new SpecialAttributeAdapter() {
                    @Override
                    public Object get(Request request, String name) {
                        return request.getCoyoteRequest().getParameters().getParseFailedReason();
                    }

                    @Override
                    public void set(Request request, String name, Object value) {
                        // NO-OP
                    }
                });
        specialAttributes.put(Globals.SENDFILE_SUPPORTED_ATTR,
                new SpecialAttributeAdapter() {
                    @Override
                    public Object get(Request request, String name) {
                        return Boolean.valueOf(
                                request.getConnector().getProtocolHandler(
                                        ).isSendfileSupported() && request.getCoyoteRequest().getSendfile());
                    }
                    @Override
                    public void set(Request request, String name, Object value) {
                        // NO-OP
                    }
                });

        for (SimpleDateFormat sdf : formatsTemplate) {
            sdf.setTimeZone(GMT_ZONE);
        }
    }
}
