package org.apache.catalina.valves;


import java.io.CharArrayWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.TLSUtil;
import org.apache.coyote.ActionCode;
import org.apache.coyote.RequestInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.collections.SynchronizedStack;


/**
 * <p><b>Valve</b>接口的抽象实现类, 生成具有匹配可配置模式的详细行内容的Web服务器访问日志.
 * 可用模式的语法类似于<a href="http://httpd.apache.org/">Apache HTTP Server</a>支持的<code>mod_log_config</code> 模块.</p>
 *
 * <p>日志消息的模式可以包括常量文本或以下任何替换字符串, 其中，来自指定响应的相应信息被替换:</p>
 * <ul>
 * <li><b>%a</b> - 远程IP地址
 * <li><b>%A</b> - 本地IP地址
 * <li><b>%b</b> - 发送的字节, 不包括 HTTP header, 或 '-' 如果没有发送字节
 * <li><b>%B</b> - 发送的字节, 不包括 HTTP header
 * <li><b>%h</b> - 远程主机名 (或 IP 地址, 如果连接器的<code>enableLookups</code>是 false)
 * <li><b>%H</b> - 请求协议
 * <li><b>%l</b> - 来自identd的远程逻辑用户名  (总是返回 '-')
 * <li><b>%m</b> - 请求方法
 * <li><b>%p</b> - 本地端口
 * <li><b>%q</b> - 查询字符串 (预先准备的 '?' 如果它存在, 否则是一个空字符串
 * <li><b>%r</b> - 请求的第一行
 * <li><b>%s</b> - 响应的HTTP状态代码
 * <li><b>%S</b> - 用户会话ID
 * <li><b>%t</b> - 日期和时间, 通用日志格式
 * <li><b>%u</b> - 经过认证的远程用户
 * <li><b>%U</b> - 请求的URL路径
 * <li><b>%v</b> - 本地服务器名称
 * <li><b>%D</b> - 处理请求所花费的时间, 毫秒
 * <li><b>%T</b> - 处理请求所花费的时间, 秒
 * <li><b>%F</b> - 作出反应的时间, 毫秒
 * <li><b>%I</b> - 当前请求线程名 (可与堆栈跟踪进行比较)
 * <li><b>%X</b> - 响应完成时的连接状态:
 *   <ul>
 *   <li><code>X</code> = 连接在响应完成之前中止.</li>
 *   <li><code>+</code> = 在发送响应后, 连接可以保持活跃.</li>
 *   <li><code>-</code> = 响应发送后, 连接将被关闭.</li>
 *   </ul>
 * </ul>
 * <p>此外, 调用方可以为常用的模式指定下列别名中的一个:</p>
 * <ul>
 * <li><b>common</b> - <code>%h %l %u %t "%r" %s %b</code>
 * <li><b>combined</b> - <code>%h %l %u %t "%r" %s %b "%{Referer}i" "%{User-Agent}i"</code>
 * </ul>
 *
 * <p>
 * 也支持从cookie, 进入的header, Session, ServletRequest中的其它一些东西写入信息.<br>
 * 它是仿照<a href="http://httpd.apache.org/">Apache HTTP Server</a>日志配置语法:</p>
 * <ul>
 * <li><code>%{xxx}i</code> 为进入的 header
 * <li><code>%{xxx}o</code> 为输出的响应 header
 * <li><code>%{xxx}c</code> 为指定的 cookie
 * <li><code>%{xxx}r</code> xxx 是 ServletRequest中的属性
 * <li><code>%{xxx}s</code> xxx 是 HttpSession中的属性
 * <li><code>%{xxx}t</code> xxx 是一个增强的 SimpleDateFormat 格式 (有关支持的时间模式的详细信息，请参阅配置参考文档)
 * </ul>
 *
 * <p>
 * 还支持条件日志. 可以使用<code>conditionUnless</code> 和 <code>conditionIf</code> 属性配置.
 * 如果ServletRequest.getAttribute(conditionUnless)返回的值产生非空值, 将跳过日志.
 * 如果ServletRequest.getAttribute(conditionIf)返回的值产生空值, 将跳过日志.
 * <code>condition</code>属性是<code>conditionUnless</code>的同义词, 并提供向后兼容性.
 * </p>
 *
 * <p>
 * 对于调用 getAttribute() 的扩展属性, 需要确保没有新行或控制字符.
 * </p>
 */
public abstract class AbstractAccessLogValve extends ValveBase implements AccessLog {

    private static final Log log = LogFactory.getLog(AbstractAccessLogValve.class);

    /**
     * 时间格式类型.
     */
    private static enum FormatType {
        CLF, SEC, MSEC, MSEC_FRAC, SDF
    }

    /**
     * 端口类型.
     */
    private static enum PortType {
        LOCAL, REMOTE
    }

    //------------------------------------------------------ Constructor
    public AbstractAccessLogValve() {
        super(true);
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * 启用此组件
     */
    protected boolean enabled = true;

    /**
     * 用于格式化访问日志行的模式.
     */
    protected String pattern = null;

    /**
     * 全局日期格式缓存的大小
     */
    private static final int globalCacheSize = 300;

    /**
     * 线程本地数据格式缓存的大小
     */
    private static final int localCacheSize = 60;

    /**
     * <p>基于秒的格式化时间戳的缓存结构.</p>
     *
     * <p>缓存由连续的秒范围的条目组成. 范围的长度是可配置的. 它是基于循环缓冲器来实现的. 新条目改变范围.</p>
     *
     * <p>CLF格式有一个缓存 (访问日志的标准格式), 以及SimpleDateFormat使用的额外的格式的缓存的一个 HashMap.</p>
     *
     * <p>虽然缓存在检索格式化的时间戳时支持指定区域设置, 首次使用格式时，每个格式都将使用给定的区域设置. 新的区域只能用于新的格式.
     * CLF 格式将始终使用<code>en_US</code>区域格式化.</p>
     *
     * <p>缓存不是线程安全的. 它可以通过线程本地实例使用, 而不需要同步, 或同步作为全局缓存.</p>
     *
     * <p>可以用父级缓存创建缓存, 以构建缓存层次结构. 对父级缓存的访问是线程安全的.</p>
     *
     * <p>该类使用小线程本地第一级缓存和更大同步的全局第二级缓存.</p>
     */
    protected static class DateFormatCache {

        protected class Cache {

            /* CLF log format */
            private static final String cLFFormat = "dd/MMM/yyyy:HH:mm:ss Z";

            /* 第二个用于在最近调用中检索CLF格式 */
            private long previousSeconds = Long.MIN_VALUE;
            /* 最近调用中检索的CLF格式的值 */
            private String previousFormat = "";

            /* 缓存中包含的第一秒 */
            private long first = Long.MIN_VALUE;
            /* 缓存中包含的最后一秒 */
            private long last = Long.MIN_VALUE;
            /* 在循环缓存中"first"的索引 */
            private int offset = 0;
            /* 能够调用 SimpleDateFormat.format()的帮助类. */
            private final Date currentDate = new Date();

            protected final String cache[];
            private SimpleDateFormat formatter;
            private boolean isCLF = false;

            private Cache parent = null;

            private Cache(Cache parent) {
                this(null, parent);
            }

            private Cache(String format, Cache parent) {
                this(format, null, parent);
            }

            private Cache(String format, Locale loc, Cache parent) {
                cache = new String[cacheSize];
                for (int i = 0; i < cacheSize; i++) {
                    cache[i] = null;
                }
                if (loc == null) {
                    loc = cacheDefaultLocale;
                }
                if (format == null) {
                    isCLF = true;
                    format = cLFFormat;
                    formatter = new SimpleDateFormat(format, Locale.US);
                } else {
                    formatter = new SimpleDateFormat(format, loc);
                }
                formatter.setTimeZone(TimeZone.getDefault());
                this.parent = parent;
            }

            private String getFormatInternal(long time) {

                long seconds = time / 1000;

                /* First step: 如果在前一次调用中我们看到了这个时间戳, 那么需要 CLF, 返回之前的值. */
                if (seconds == previousSeconds) {
                    return previousFormat;
                }

                /* Second step: 尝试在缓存中定位 */
                previousSeconds = seconds;
                int index = (offset + (int)(seconds - first)) % cacheSize;
                if (index < 0) {
                    index += cacheSize;
                }
                if (seconds >= first && seconds <= last) {
                    if (cache[index] != null) {
                        /* 已找到, 所以记住下一个调用和返回.*/
                        previousFormat = cache[index];
                        return previousFormat;
                    }

                /* Third step: 在缓存中找不到, 调整缓存并添加项 */
                } else if (seconds >= last + cacheSize || seconds <= first - cacheSize) {
                    first = seconds;
                    last = first + cacheSize - 1;
                    index = 0;
                    offset = 0;
                    for (int i = 1; i < cacheSize; i++) {
                        cache[i] = null;
                    }
                } else if (seconds > last) {
                    for (int i = 1; i < seconds - last; i++) {
                        cache[(index + cacheSize - i) % cacheSize] = null;
                    }
                    first = seconds - (cacheSize - 1);
                    last = seconds;
                    offset = (index + 1) % cacheSize;
                } else if (seconds < first) {
                    for (int i = 1; i < first - seconds; i++) {
                        cache[(index + i) % cacheSize] = null;
                    }
                    first = seconds;
                    last = seconds + (cacheSize - 1);
                    offset = index;
                }

                /* Last step: 使用父缓存或本地的格式化新的时间戳. */
                if (parent != null) {
                    synchronized(parent) {
                        previousFormat = parent.getFormatInternal(time);
                    }
                } else {
                    currentDate.setTime(time);
                    previousFormat = formatter.format(currentDate);
                    if (isCLF) {
                        StringBuilder current = new StringBuilder(32);
                        current.append('[');
                        current.append(previousFormat);
                        current.append(']');
                        previousFormat = current.toString();
                    }
                }
                cache[index] = previousFormat;
                return previousFormat;
            }
        }

        /* 缓存条目的数量 */
        private int cacheSize = 0;

        private final Locale cacheDefaultLocale;
        private final DateFormatCache parent;
        protected final Cache cLFCache;
        private final HashMap<String, Cache> formatCache = new HashMap<>();

        protected DateFormatCache(int size, Locale loc, DateFormatCache parent) {
            cacheSize = size;
            cacheDefaultLocale = loc;
            this.parent = parent;
            Cache parentCache = null;
            if (parent != null) {
                synchronized(parent) {
                    parentCache = parent.getCache(null, null);
                }
            }
            cLFCache = new Cache(parentCache);
        }

        private Cache getCache(String format, Locale loc) {
            Cache cache;
            if (format == null) {
                cache = cLFCache;
            } else {
                cache = formatCache.get(format);
                if (cache == null) {
                    Cache parentCache = null;
                    if (parent != null) {
                        synchronized(parent) {
                            parentCache = parent.getCache(format, loc);
                        }
                    }
                    cache = new Cache(format, loc, parentCache);
                    formatCache.put(format, cache);
                }
            }
            return cache;
        }

        public String getFormat(long time) {
            return cLFCache.getFormatInternal(time);
        }

        public String getFormat(String format, Locale loc, long time) {
            return getCache(format, loc).getFormatInternal(time);
        }
    }

    /**
     * 全局日期格式缓存.
     */
    private static final DateFormatCache globalDateCache =
            new DateFormatCache(globalCacheSize, Locale.getDefault(), null);

    /**
     * 线程本地数据格式缓存.
     */
    private static final ThreadLocal<DateFormatCache> localDateCache =
            new ThreadLocal<DateFormatCache>() {
        @Override
        protected DateFormatCache initialValue() {
            return new DateFormatCache(localCacheSize, Locale.getDefault(), globalDateCache);
        }
    };


    /**
     * 这个valve用于日志行的上次更新的时间.
     */
    private static final ThreadLocal<Date> localDate =
            new ThreadLocal<Date>() {
        @Override
        protected Date initialValue() {
            return new Date();
        }
    };

    /**
     * 是否正在做条件日志. 默认 null.
     * 它是 <code>conditionUnless</code> 属性的值.
     */
    protected String condition = null;

    /**
     * 是否正在做条件日志. 默认 null.
     * 它是 <code>conditionIf</code> 属性的值.
     */
    protected String conditionIf = null;

    /**
     * 在日志项和日志文件名后缀中格式化时间戳的区域名称.
     */
    protected String localeName = Locale.getDefault().toString();


    /**
     * 在日志项和日志文件名后缀中格式化时间戳的区域.
     */
    protected Locale locale = Locale.getDefault();

    /**
     * 用于制作日志消息.
     */
    protected AccessLogElement[] logElements = null;

    /**
     * 这个valve是否设置请求属性 IP 地址, 主机名, 协议, 端口号.
     * 默认是<code>false</code>.
     */
    protected boolean requestAttributesEnabled = false;

    /**
     * 用于日志消息生成的缓冲池. 用于减少垃圾产生的池.
     */
    private SynchronizedStack<CharArrayWriter> charArrayWriters =
            new SynchronizedStack<>();

    /**
     * 日志消息缓冲区通常被回收和重新使用. 防止过度使用内存, 如果缓冲区增长超过这个大小，它将被丢弃.
     * 默认是 256 字符. 这应该设置为大于典型的访问日志消息大小.
     */
    private int maxLogMessageBufferSize = 256;

    /**
     * 配置的日志模式是否包含已知的TLS属性?
     */
    private boolean tlsAttributeRequired = false;


    // ------------------------------------------------------------- Properties

    /**
     * {@inheritDoc}
     * 默认是 <code>false</code>.
     */
    @Override
    public void setRequestAttributesEnabled(boolean requestAttributesEnabled) {
        this.requestAttributesEnabled = requestAttributesEnabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getRequestAttributesEnabled() {
        return requestAttributesEnabled;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPattern() {
        return (this.pattern);
    }


    /**
     * 设置格式化模式, 首先翻译任何识别的别名.
     *
     * @param pattern 新模式
     */
    public void setPattern(String pattern) {
        if (pattern == null) {
            this.pattern = "";
        } else if (pattern.equals(Constants.AccessLog.COMMON_ALIAS)) {
            this.pattern = Constants.AccessLog.COMMON_PATTERN;
        } else if (pattern.equals(Constants.AccessLog.COMBINED_ALIAS)) {
            this.pattern = Constants.AccessLog.COMBINED_PATTERN;
        } else {
            this.pattern = pattern;
        }
        logElements = createLogElements();
    }

    /**
     * 返回执行条件日志时要查找的属性名称. 如果是 null, 记录所有请求.
     */
    public String getCondition() {
        return condition;
    }


    /**
     * 设置 ServletRequest.attribute 查找执行条件日志. 设置为 null 记录所有.
     *
     * @param condition Set to null to log everything
     */
    public void setCondition(String condition) {
        this.condition = condition;
    }


    /**
     * 返回执行条件日志时要查找的属性名称. 如果是 null, 记录所有请求.
     */
    public String getConditionUnless() {
        return getCondition();
    }


    /**
     * 设置 ServletRequest.attribute 查找执行条件日志. 设置为 null 记录所有.
     *
     * @param condition Set to null to log everything
     */
    public void setConditionUnless(String condition) {
        setCondition(condition);
    }

    /**
     * 返回执行条件日志时要查找的属性名称. 如果是 null, 记录所有请求.
     */
    public String getConditionIf() {
        return conditionIf;
    }


    /**
     * 设置 ServletRequest.attribute 查找执行条件日志. 设置为 null 记录所有.
     *
     * @param condition Set to null to log everything
     */
    public void setConditionIf(String condition) {
        this.conditionIf = condition;
    }

    /**
     * 返回用于在日志条目和日志文件名后缀中用于格式化时间戳的区域设置.
     * @return the locale
     */
    public String getLocale() {
        return localeName;
    }


    /**
     * 设置用于在日志条目和日志文件名后缀中用于格式化时间戳的区域设置.
     * 仅当AccessLogValve没有记录任何内容时才支持更改区域设置. 稍后更改区域会导致格式不一致.
     *
     * @param localeName 使用的区域.
     */
    public void setLocale(String localeName) {
        this.localeName = localeName;
        locale = findLocale(localeName, locale);
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 记录一个概括指定请求和响应的消息, 根据<code>pattern</code> 属性指定的格式.
     *
     * @param request Request being processed
     * @param response Response being processed
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果发生servlet错误
     */
    @Override
    public void invoke(Request request, Response response) throws IOException,
            ServletException {
        if (tlsAttributeRequired) {
            // 日志模式使用TLS属性. 确保这些请求在处理请求之前被填充，因为在访问日志请求TLS信息之前，可以使用NIO2来关闭连接（TLS信息丢失）.
            // 现在请求它，导致它被缓存在请求中.
            request.getAttribute(Globals.CERTIFICATES_ATTR);
        }
        getNext().invoke(request, response);
    }


    @Override
    public void log(Request request, Response response, long time) {
        if (!getState().isAvailable() || !getEnabled() || logElements == null
                || condition != null
                && null != request.getRequest().getAttribute(condition)
                || conditionIf != null
                && null == request.getRequest().getAttribute(conditionIf)) {
            return;
        }

        /**
         * XXX 这有点傻, 但是我们希望启动和停止的时间和持续时间一致. 最好在请求和/或响应对象中保持启动和停止，并从接口中删除时间（持续时间）.
         */
        long start = request.getCoyoteRequest().getStartTime();
        Date date = getDate(start + time);

        CharArrayWriter result = charArrayWriters.pop();
        if (result == null) {
            result = new CharArrayWriter(128);
        }

        for (int i = 0; i < logElements.length; i++) {
            logElements[i].addElement(result, date, request, response, time);
        }

        log(result);

        if (result.size() <= maxLogMessageBufferSize) {
            result.reset();
            charArrayWriters.push(result);
        }
    }

    // -------------------------------------------------------- Protected Methods

    /**
     * 记录指定的消息.
     *
     * @param message 要记录的消息. 该对象将通过调用方法循环使用.
     */
    protected abstract void log(CharArrayWriter message);

    // -------------------------------------------------------- Private Methods

    /**
     * 此方法返回一个精确到1秒的日期对象.
     * 如果线程调用此方法以获取日期, 则创建新日期后其长度小于1秒, 此方法简单地再次给出相同的Date，使得系统不必花费时间创建不必要的Date对象.
     * @param systime The time
     * @return the date object
     */
    private static Date getDate(long systime) {
        Date date = localDate.get();
        date.setTime(systime);
        return date;
    }


    /**
     * 按名称查找区域.
     * @param name 区域名称
     * @param fallback 如果找不到名称，则回退区域设置
     * @return the locale object
     */
    protected static Locale findLocale(String name, Locale fallback) {
        if (name == null || name.isEmpty()) {
            return Locale.getDefault();
        } else {
            for (Locale l: Locale.getAvailableLocales()) {
                if (name.equals(l.toString())) {
                    return(l);
                }
            }
        }
        log.error(sm.getString("accessLogValve.invalidLocale", name));
        return fallback;
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        setState(LifecycleState.STARTING);
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);
    }

    /**
     * 将部分消息写入缓冲区.
     */
    protected interface AccessLogElement {
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time);

    }

    /**
     * 写入线程名 - %I
     */
    protected static class ThreadNameElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            RequestInfo info = request.getCoyoteRequest().getRequestProcessor();
            if(info != null) {
                buf.append(info.getWorkerThreadName());
            } else {
                buf.append("-");
            }
        }
    }

    /**
     * 写入本地IP地址 - %A
     */
    protected static class LocalAddrElement implements AccessLogElement {

        private static final String LOCAL_ADDR_VALUE;

        static {
            String init;
            try {
                init = InetAddress.getLocalHost().getHostAddress();
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                init = "127.0.0.1";
            }
            LOCAL_ADDR_VALUE = init;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            buf.append(LOCAL_ADDR_VALUE);
        }
    }

    /**
     * 写入远程IP地址 - %a
     */
    protected class RemoteAddrElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (requestAttributesEnabled) {
                Object addr = request.getAttribute(REMOTE_ADDR_ATTRIBUTE);
                if (addr == null) {
                    buf.append(request.getRemoteAddr());
                } else {
                    buf.append(addr.toString());
                }
            } else {
                buf.append(request.getRemoteAddr());
            }
        }
    }

    /**
     * 写入远程主机名 - %h
     */
    protected class HostElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            String value = null;
            if (requestAttributesEnabled) {
                Object host = request.getAttribute(REMOTE_HOST_ATTRIBUTE);
                if (host != null) {
                    value = host.toString();
                }
            }
            if (value == null || value.length() == 0) {
                value = request.getRemoteHost();
            }
            if (value == null || value.length() == 0) {
                value = "-";
            }
            buf.append(value);
        }
    }

    /**
     * 从 identd 写入远程逻辑用户名(总是返回 '-') - %l
     */
    protected static class LogicalUserNameElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            buf.append('-');
        }
    }

    /**
     * 写入请求协议 - %H
     */
    protected class ProtocolElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (requestAttributesEnabled) {
                Object proto = request.getAttribute(PROTOCOL_ATTRIBUTE);
                if (proto == null) {
                    buf.append(request.getProtocol());
                } else {
                    buf.append(proto.toString());
                }
            } else {
                buf.append(request.getProtocol());
            }
        }
    }

    /**
     * 写入经过认证的远程用户, 否则 '-' - %u
     */
    protected static class UserElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (request != null) {
                String value = request.getRemoteUser();
                if (value != null) {
                    buf.append(value);
                } else {
                    buf.append('-');
                }
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * 写入日期和时间, 使用配置的格式 (默认 CLF) - %t or %{format}t
     */
    protected class DateAndTimeElement implements AccessLogElement {

        /**
         * 指定请求开始时间的格式前缀
         */
        private static final String requestStartPrefix = "begin";

        /**
         * 指定响应结束时间的格式前缀
         */
        private static final String responseEndPrefix = "end";

        /**
         * 可选前缀和格式余数之间的分隔符
         */
        private static final String prefixSeparator = ":";

        /**
         * 时代以来的特殊格式
         */
        private static final String secFormat = "sec";

        /**
         * 时代以来的毫秒专用格式
         */
        private static final String msecFormat = "msec";

        /**
         * 时间戳毫秒部分的特殊格式
         */
        private static final String msecFractionFormat = "msec_frac";

        /**
         * 用于替换SimpleDateFormat的 "S" 和 "SSS" 毫秒格式的格式, 通过我们自己的处理
         */
        private static final String msecPattern = "{#}";
        private static final String trippleMsecPattern =
            msecPattern + msecPattern + msecPattern;

        /* 格式化的描述字符串, null if CLF */
        private final String format;
        /* 是否使用请求的开始或响应的结束作为时间戳 */
        private final boolean usesBegin;
        /* 格式化类型 */
        private final FormatType type;
        /* 是否需要添加毫秒后处理 */
        private boolean usesMsecs = false;

        protected DateAndTimeElement() {
            this(null);
        }

        /**
         * 替换毫秒格式字符 'S', 通过一些假的字符, 以便使生成的格式化时间戳缓存.
         * 稍后用实际的毫秒替换虚拟字符，因为这相当容易.
         */
        private String tidyFormat(String format) {
            boolean escape = false;
            StringBuilder result = new StringBuilder();
            int len = format.length();
            char x;
            for (int i = 0; i < len; i++) {
                x = format.charAt(i);
                if (escape || x != 'S') {
                    result.append(x);
                } else {
                    result.append(msecPattern);
                    usesMsecs = true;
                }
                if (x == '\'') {
                    escape = !escape;
                }
            }
            return result.toString();
        }

        protected DateAndTimeElement(String header) {
            String format = header;
            boolean usesBegin = false;
            FormatType type = FormatType.CLF;

            if (format != null) {
                if (format.equals(requestStartPrefix)) {
                    usesBegin = true;
                    format = "";
                } else if (format.startsWith(requestStartPrefix + prefixSeparator)) {
                    usesBegin = true;
                    format = format.substring(6);
                } else if (format.equals(responseEndPrefix)) {
                    usesBegin = false;
                    format = "";
                } else if (format.startsWith(responseEndPrefix + prefixSeparator)) {
                    usesBegin = false;
                    format = format.substring(4);
                }
                if (format.length() == 0) {
                    type = FormatType.CLF;
                } else if (format.equals(secFormat)) {
                    type = FormatType.SEC;
                } else if (format.equals(msecFormat)) {
                    type = FormatType.MSEC;
                } else if (format.equals(msecFractionFormat)) {
                    type = FormatType.MSEC_FRAC;
                } else {
                    type = FormatType.SDF;
                    format = tidyFormat(format);
                }
            }
            this.format = format;
            this.usesBegin = usesBegin;
            this.type = type;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            long timestamp = date.getTime();
            long frac;
            if (usesBegin) {
                timestamp -= time;
            }
            switch (type) {
            case CLF:
                buf.append(localDateCache.get().getFormat(timestamp));
                break;
            case SEC:
                buf.append(Long.toString(timestamp / 1000));
                break;
            case MSEC:
                buf.append(Long.toString(timestamp));
                break;
            case MSEC_FRAC:
                frac = timestamp % 1000;
                if (frac < 100) {
                    if (frac < 10) {
                        buf.append('0');
                        buf.append('0');
                    } else {
                        buf.append('0');
                    }
                }
                buf.append(Long.toString(frac));
                break;
            case SDF:
                String temp = localDateCache.get().getFormat(format, locale, timestamp);
                if (usesMsecs) {
                    frac = timestamp % 1000;
                    StringBuilder trippleMsec = new StringBuilder(4);
                    if (frac < 100) {
                        if (frac < 10) {
                            trippleMsec.append('0');
                            trippleMsec.append('0');
                        } else {
                            trippleMsec.append('0');
                        }
                    }
                    trippleMsec.append(frac);
                    temp = temp.replace(trippleMsecPattern, trippleMsec);
                    temp = temp.replace(msecPattern, Long.toString(frac));
                }
                buf.append(temp);
                break;
            }
        }
    }

    /**
     * 写入请求的第一行 (方法和请求 URI) - %r
     */
    protected static class RequestElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (request != null) {
                String method = request.getMethod();
                if (method == null) {
                    // 没有方法意味着没有请求行
                    buf.append('-');
                } else {
                    buf.append(request.getMethod());
                    buf.append(' ');
                    buf.append(request.getRequestURI());
                    if (request.getQueryString() != null) {
                        buf.append('?');
                        buf.append(request.getQueryString());
                    }
                    buf.append(' ');
                    buf.append(request.getProtocol());
                }
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * 写入响应的HTTP状态代码 - %s
     */
    protected static class HttpStatusCodeElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (response != null) {
                // 这种方法用于减少从字符串转换的GC
                int status = response.getStatus();
                if (100 <= status && status < 1000) {
                    buf.append((char) ('0' + (status / 100)))
                            .append((char) ('0' + ((status / 10) % 10)))
                            .append((char) ('0' + (status % 10)));
                } else {
                   buf.append(Integer.toString(status));
                }
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * 为请求连接写入本地或远程端口 - %p and %{xxx}p
     */
    protected class PortElement implements AccessLogElement {

        /**
         * 要记录的端口类型
         */
        private static final String localPort = "local";
        private static final String remotePort = "remote";

        private final PortType portType;

        public PortElement() {
            portType = PortType.LOCAL;
        }

        public PortElement(String type) {
            switch (type) {
            case remotePort:
                portType = PortType.REMOTE;
                break;
            case localPort:
                portType = PortType.LOCAL;
                break;
            default:
                log.error(sm.getString("accessLogValve.invalidPortType", type));
                portType = PortType.LOCAL;
                break;
            }
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (requestAttributesEnabled && portType == PortType.LOCAL) {
                Object port = request.getAttribute(SERVER_PORT_ATTRIBUTE);
                if (port == null) {
                    buf.append(Integer.toString(request.getServerPort()));
                } else {
                    buf.append(port.toString());
                }
            } else {
                if (portType == PortType.LOCAL) {
                    buf.append(Integer.toString(request.getServerPort()));
                } else {
                    buf.append(Integer.toString(request.getRemotePort()));
                }
            }
        }
    }

    /**
     * 写入发送的字节, 排除 HTTP header - %b, %B
     */
    protected static class ByteSentElement implements AccessLogElement {
        private final boolean conversion;

        /**
         * @param conversion <code>true</code>表示写入 '-' 替换 0 - %b.
         */
        public ByteSentElement(boolean conversion) {
            this.conversion = conversion;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            // 不需要刷新，因为响应提交后会触发日志消息
            long length = response.getBytesWritten(false);
            if (length <= 0) {
                // 保护空值和意外的类型，因为这些值可能由不可信的应用程序设置
                Object start = request.getAttribute(
                        Globals.SENDFILE_FILE_START_ATTR);
                if (start instanceof Long) {
                    Object end = request.getAttribute(
                            Globals.SENDFILE_FILE_END_ATTR);
                    if (end instanceof Long) {
                        length = ((Long) end).longValue() -
                                ((Long) start).longValue();
                    }
                }
            }
            if (length <= 0 && conversion) {
                buf.append('-');
            } else {
                buf.append(Long.toString(length));
            }
        }
    }

    /**
     * 写入请求方法 (GET, POST, etc.) - %m
     */
    protected static class MethodElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (request != null) {
                buf.append(request.getMethod());
            }
        }
    }

    /**
     * 处理请求所需的写入时间 - %D, %T
     */
    protected static class ElapsedTimeElement implements AccessLogElement {
        private final boolean millis;

        /**
         * @param millis <code>true</code>, 写入时间, 毫秒 - %D,
         * 如果是<code>false</code>, 写入时间, 秒 - %T
         */
        public ElapsedTimeElement(boolean millis) {
            this.millis = millis;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (millis) {
                buf.append(Long.toString(time));
            } else {
                // second
                buf.append(Long.toString(time / 1000));
                buf.append('.');
                int remains = (int) (time % 1000);
                buf.append(Long.toString(remains / 100));
                remains = remains % 100;
                buf.append(Long.toString(remains / 10));
                buf.append(Long.toString(remains % 10));
            }
        }
    }

    /**
     * 写入时间直到写入第一个字节为止(提交时间), 毫秒 - %F
     */
    protected static class FirstByteTimeElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            long commitTime = response.getCoyoteResponse().getCommitTime();
            if (commitTime == -1) {
                buf.append('-');
            } else {
                long delta = commitTime - request.getCoyoteRequest().getStartTime();
                buf.append(Long.toString(delta));
            }
        }
    }

    /**
     * 写入查询字符串 (预先准备一个 '?', 如果它存在) - %q
     */
    protected static class QueryElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            String query = null;
            if (request != null) {
                query = request.getQueryString();
            }
            if (query != null) {
                buf.append('?');
                buf.append(query);
            }
        }
    }

    /**
     * 写入用户会话ID - %S
     */
    protected static class SessionIdElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (request == null) {
                buf.append('-');
            } else {
                Session session = request.getSessionInternal(false);
                if (session == null) {
                    buf.append('-');
                } else {
                    buf.append(session.getIdInternal());
                }
            }
        }
    }

    /**
     * 写入请求的URL路径 - %U
     */
    protected static class RequestURIElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (request != null) {
                buf.append(request.getRequestURI());
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * 写入本地服务器名称 - %v
     */
    protected static class LocalServerNameElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            buf.append(request.getServerName());
        }
    }

    /**
     * 写入字符串
     */
    protected static class StringElement implements AccessLogElement {
        private final String str;

        public StringElement(String str) {
            this.str = str;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            buf.append(str);
        }
    }

    /**
     * 写入输入的 header - %{xxx}i
     */
    protected static class HeaderElement implements AccessLogElement {
        private final String header;

        public HeaderElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            Enumeration<String> iter = request.getHeaders(header);
            if (iter.hasMoreElements()) {
                buf.append(iter.nextElement());
                while (iter.hasMoreElements()) {
                    buf.append(',').append(iter.nextElement());
                }
                return;
            }
            buf.append('-');
        }
    }

    /**
     * 写入一个特定的 cookie - %{xxx}c
     */
    protected static class CookieElement implements AccessLogElement {
        private final String header;

        public CookieElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            String value = "-";
            Cookie[] c = request.getCookies();
            if (c != null) {
                for (int i = 0; i < c.length; i++) {
                    if (header.equals(c[i].getName())) {
                        value = c[i].getValue();
                        break;
                    }
                }
            }
            buf.append(value);
        }
    }

    /**
     * 写入一个指定的响应 header - %{xxx}o
     */
    protected static class ResponseHeaderElement implements AccessLogElement {
        private final String header;

        public ResponseHeaderElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (null != response) {
                Iterator<String> iter = response.getHeaders(header).iterator();
                if (iter.hasNext()) {
                    buf.append(iter.next());
                    while (iter.hasNext()) {
                        buf.append(',').append(iter.next());
                    }
                    return;
                }
            }
            buf.append('-');
        }
    }

    /**
     * 写入 ServletRequest中的属性 - %{xxx}r
     */
    protected static class RequestAttributeElement implements AccessLogElement {
        private final String header;

        public RequestAttributeElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            Object value = null;
            if (request != null) {
                value = request.getAttribute(header);
            } else {
                value = "??";
            }
            if (value != null) {
                if (value instanceof String) {
                    buf.append((String) value);
                } else {
                    buf.append(value.toString());
                }
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * 写入 HttpSession中的属性 - %{xxx}s
     */
    protected static class SessionAttributeElement implements AccessLogElement {
        private final String header;

        public SessionAttributeElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            Object value = null;
            if (null != request) {
                HttpSession sess = request.getSession(false);
                if (null != sess) {
                    value = sess.getAttribute(header);
                }
            } else {
                value = "??";
            }
            if (value != null) {
                if (value instanceof String) {
                    buf.append((String) value);
                } else {
                    buf.append(value.toString());
                }
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * 完成响应时写入连接状态 - %X
     */
    protected static class ConnectionStatusElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            if (response != null && request != null) {
                boolean statusFound = false;

                // 检查连接IO是否是处于 "not allowed" 状态
                AtomicBoolean isIoAllowed = new AtomicBoolean(false);
                request.getCoyoteRequest().action(ActionCode.IS_IO_ALLOWED, isIoAllowed);
                if (!isIoAllowed.get()) {
                    buf.append('X');
                    statusFound = true;
                } else {
                    // 检查连接中止的 cond
                    if (response.isError()) {
                        Throwable ex = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
                        if (ex instanceof ClientAbortException) {
                            buf.append('X');
                            statusFound = true;
                        }
                    }
                }

                // 如果尚未找到状态, 检查连接是否是 keep-alive 或关闭
                if (!statusFound) {
                    String connStatus = response.getHeader(org.apache.coyote.http11.Constants.CONNECTION);
                    if (org.apache.coyote.http11.Constants.CLOSE.equalsIgnoreCase(connStatus)) {
                        buf.append('-');
                    } else {
                        buf.append('+');
                    }
                }
            } else {
                // 未知的连接状态
                buf.append('?');
            }
        }
    }

    /**
     * 解析模式字符串, 并创建 AccessLogElement数组.
     */
    protected AccessLogElement[] createLogElements() {
        List<AccessLogElement> list = new ArrayList<>();
        boolean replace = false;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (replace) {
                /*
                 * 处理 { 的代码, 行为将会是 ... 如果未找到闭合的 } - 那么忽略 {
                 */
                if ('{' == ch) {
                    StringBuilder name = new StringBuilder();
                    int j = i + 1;
                    for (; j < pattern.length() && '}' != pattern.charAt(j); j++) {
                        name.append(pattern.charAt(j));
                    }
                    if (j + 1 < pattern.length()) {
                        /* +1 是为了说明 } , 现在递增 */
                        j++;
                        list.add(createAccessLogElement(name.toString(),
                                pattern.charAt(j)));
                        i = j; /* 因为我们走了不止一个字符 */
                    } else {
                        // D'oh - 字符串结束 - 假装从来没有这样做，并处理“老办法”
                        list.add(createAccessLogElement(ch));
                    }
                } else {
                    list.add(createAccessLogElement(ch));
                }
                replace = false;
            } else if (ch == '%') {
                replace = true;
                list.add(new StringElement(buf.toString()));
                buf = new StringBuilder();
            } else {
                buf.append(ch);
            }
        }
        if (buf.length() > 0) {
            list.add(new StringElement(buf.toString()));
        }
        return list.toArray(new AccessLogElement[0]);
    }

    /**
     * 创建一个需要元素名称的 AccessLogElement 实现类.
     * @param name Header name
     * @param pattern 日志模式中的字符
     * @return 日志元素
     */
    protected AccessLogElement createAccessLogElement(String name, char pattern) {
        switch (pattern) {
        case 'i':
            return new HeaderElement(name);
        case 'c':
            return new CookieElement(name);
        case 'o':
            return new ResponseHeaderElement(name);
        case 'p':
            return new PortElement(name);
        case 'r':
            if (TLSUtil.isTLSRequestAttribute(name)) {
                tlsAttributeRequired = true;
            }
            return new RequestAttributeElement(name);
        case 's':
            return new SessionAttributeElement(name);
        case 't':
            return new DateAndTimeElement(name);
        default:
            return new StringElement("???");
        }
    }

    /**
     * 创建一个 AccessLogElement 实现.
     * @param pattern 日志模式中的字符
     * @return the log element
     */
    protected AccessLogElement createAccessLogElement(char pattern) {
        switch (pattern) {
        case 'a':
            return new RemoteAddrElement();
        case 'A':
            return new LocalAddrElement();
        case 'b':
            return new ByteSentElement(true);
        case 'B':
            return new ByteSentElement(false);
        case 'D':
            return new ElapsedTimeElement(true);
        case 'F':
            return new FirstByteTimeElement();
        case 'h':
            return new HostElement();
        case 'H':
            return new ProtocolElement();
        case 'l':
            return new LogicalUserNameElement();
        case 'm':
            return new MethodElement();
        case 'p':
            return new PortElement();
        case 'q':
            return new QueryElement();
        case 'r':
            return new RequestElement();
        case 's':
            return new HttpStatusCodeElement();
        case 'S':
            return new SessionIdElement();
        case 't':
            return new DateAndTimeElement();
        case 'T':
            return new ElapsedTimeElement(false);
        case 'u':
            return new UserElement();
        case 'U':
            return new RequestURIElement();
        case 'v':
            return new LocalServerNameElement();
        case 'I':
            return new ThreadNameElement();
        case 'X':
            return new ConnectionStatusElement();
        default:
            return new StringElement("???" + pattern + "???");
        }
    }
}
