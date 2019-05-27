package org.apache.catalina.filters;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class ExpiresFilter extends FilterBase {

    /**
     * {@link #amount}和{@link #unit}
     */
    protected static class Duration {

        protected final int amount;

        protected final DurationUnit unit;

        public Duration(int amount, DurationUnit unit) {
            super();
            this.amount = amount;
            this.unit = unit;
        }

        public int getAmount() {
            return amount;
        }

        public DurationUnit getUnit() {
            return unit;
        }

        @Override
        public String toString() {
            return amount + " " + unit;
        }
    }

    /**
     * Duration unit
     */
    protected enum DurationUnit {
        DAY(Calendar.DAY_OF_YEAR), HOUR(Calendar.HOUR), MINUTE(Calendar.MINUTE), MONTH(
                Calendar.MONTH), SECOND(Calendar.SECOND), WEEK(
                Calendar.WEEK_OF_YEAR), YEAR(Calendar.YEAR);
        private final int calendarField;

        private DurationUnit(int calendarField) {
            this.calendarField = calendarField;
        }

        public int getCalendardField() {
            return calendarField;
        }

    }

    /**
     * <p>
     * 过滤器的主要配置.
     * </p>
     * <p>
     * 例如'{@code access plus 1 month 15 days 2 hours}'.
     * </p>
     */
    protected static class ExpiresConfiguration {
        /**
         * List of duration elements.
         */
        private final List<Duration> durations;

        /**
         * 响应中设置的起始点.
         */
        private final StartingPoint startingPoint;

        public ExpiresConfiguration(StartingPoint startingPoint,
                List<Duration> durations) {
            super();
            this.startingPoint = startingPoint;
            this.durations = durations;
        }

        public List<Duration> getDurations() {
            return durations;
        }

        public StartingPoint getStartingPoint() {
            return startingPoint;
        }

        @Override
        public String toString() {
            return "ExpiresConfiguration[startingPoint=" + startingPoint +
                    ", duration=" + durations + "]";
        }
    }

    /**
     * 到期配置起始点.
     * 无论是html-page/servlet-response 是 served ({@link StartingPoint#ACCESS_TIME})
     * 或是html-page/servlet-response 是 modified ({@link StartingPoint#LAST_MODIFICATION_TIME}).
     */
    protected enum StartingPoint {
        ACCESS_TIME, LAST_MODIFICATION_TIME
    }

    public class XHttpServletResponse extends HttpServletResponseWrapper {

        /**
         * {@code Cache-Control} http响应header的值.
         */
        private String cacheControlHeader;

        /**
         * {@code Last-Modified}http响应header的值.
         */
        private long lastModifiedHeader;

        private boolean lastModifiedHeaderSet;

        private PrintWriter printWriter;

        private final HttpServletRequest request;

        private ServletOutputStream servletOutputStream;

        /**
         * 是否调用响应主体的写入方法({@code write(...)}, {@code print(...)}, etc).
         */
        private boolean writeResponseBodyStarted;

        public XHttpServletResponse(HttpServletRequest request,
                HttpServletResponse response) {
            super(response);
            this.request = request;
        }

        @Override
        public void addDateHeader(String name, long date) {
            super.addDateHeader(name, date);
            if (!lastModifiedHeaderSet) {
                this.lastModifiedHeader = date;
                this.lastModifiedHeaderSet = true;
            }
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, value);
            if (HEADER_CACHE_CONTROL.equalsIgnoreCase(name) &&
                    cacheControlHeader == null) {
                cacheControlHeader = value;
            }
        }

        public String getCacheControlHeader() {
            return cacheControlHeader;
        }

        public long getLastModifiedHeader() {
            return lastModifiedHeader;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (servletOutputStream == null) {
                servletOutputStream = new XServletOutputStream(
                        super.getOutputStream(), request, this);
            }
            return servletOutputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (printWriter == null) {
                printWriter = new XPrintWriter(super.getWriter(), request, this);
            }
            return printWriter;
        }

        public boolean isLastModifiedHeaderSet() {
            return lastModifiedHeaderSet;
        }

        public boolean isWriteResponseBodyStarted() {
            return writeResponseBodyStarted;
        }

        @Override
        public void reset() {
            super.reset();
            this.lastModifiedHeader = 0;
            this.lastModifiedHeaderSet = false;
            this.cacheControlHeader = null;
        }

        @Override
        public void setDateHeader(String name, long date) {
            super.setDateHeader(name, date);
            if (HEADER_LAST_MODIFIED.equalsIgnoreCase(name)) {
                this.lastModifiedHeader = date;
                this.lastModifiedHeaderSet = true;
            }
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, value);
            if (HEADER_CACHE_CONTROL.equalsIgnoreCase(name)) {
                this.cacheControlHeader = value;
            }
        }

        public void setWriteResponseBodyStarted(boolean writeResponseBodyStarted) {
            this.writeResponseBodyStarted = writeResponseBodyStarted;
        }
    }

    public class XPrintWriter extends PrintWriter {
        private final PrintWriter out;

        private final HttpServletRequest request;

        private final XHttpServletResponse response;

        public XPrintWriter(PrintWriter out, HttpServletRequest request,
                XHttpServletResponse response) {
            super(out);
            this.out = out;
            this.request = request;
            this.response = response;
        }

        @Override
        public PrintWriter append(char c) {
            fireBeforeWriteResponseBodyEvent();
            return out.append(c);
        }

        @Override
        public PrintWriter append(CharSequence csq) {
            fireBeforeWriteResponseBodyEvent();
            return out.append(csq);
        }

        @Override
        public PrintWriter append(CharSequence csq, int start, int end) {
            fireBeforeWriteResponseBodyEvent();
            return out.append(csq, start, end);
        }

        @Override
        public void close() {
            fireBeforeWriteResponseBodyEvent();
            out.close();
        }

        private void fireBeforeWriteResponseBodyEvent() {
            if (!this.response.isWriteResponseBodyStarted()) {
                this.response.setWriteResponseBodyStarted(true);
                onBeforeWriteResponseBody(request, response);
            }
        }

        @Override
        public void flush() {
            fireBeforeWriteResponseBodyEvent();
            out.flush();
        }

        @Override
        public void print(boolean b) {
            fireBeforeWriteResponseBodyEvent();
            out.print(b);
        }

        @Override
        public void print(char c) {
            fireBeforeWriteResponseBodyEvent();
            out.print(c);
        }

        @Override
        public void print(char[] s) {
            fireBeforeWriteResponseBodyEvent();
            out.print(s);
        }

        @Override
        public void print(double d) {
            fireBeforeWriteResponseBodyEvent();
            out.print(d);
        }

        @Override
        public void print(float f) {
            fireBeforeWriteResponseBodyEvent();
            out.print(f);
        }

        @Override
        public void print(int i) {
            fireBeforeWriteResponseBodyEvent();
            out.print(i);
        }

        @Override
        public void print(long l) {
            fireBeforeWriteResponseBodyEvent();
            out.print(l);
        }

        @Override
        public void print(Object obj) {
            fireBeforeWriteResponseBodyEvent();
            out.print(obj);
        }

        @Override
        public void print(String s) {
            fireBeforeWriteResponseBodyEvent();
            out.print(s);
        }

        @Override
        public PrintWriter printf(Locale l, String format, Object... args) {
            fireBeforeWriteResponseBodyEvent();
            return out.printf(l, format, args);
        }

        @Override
        public PrintWriter printf(String format, Object... args) {
            fireBeforeWriteResponseBodyEvent();
            return out.printf(format, args);
        }

        @Override
        public void println() {
            fireBeforeWriteResponseBodyEvent();
            out.println();
        }

        @Override
        public void println(boolean x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(char x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(char[] x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(double x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(float x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(int x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(long x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(Object x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void println(String x) {
            fireBeforeWriteResponseBodyEvent();
            out.println(x);
        }

        @Override
        public void write(char[] buf) {
            fireBeforeWriteResponseBodyEvent();
            out.write(buf);
        }

        @Override
        public void write(char[] buf, int off, int len) {
            fireBeforeWriteResponseBodyEvent();
            out.write(buf, off, len);
        }

        @Override
        public void write(int c) {
            fireBeforeWriteResponseBodyEvent();
            out.write(c);
        }

        @Override
        public void write(String s) {
            fireBeforeWriteResponseBodyEvent();
            out.write(s);
        }

        @Override
        public void write(String s, int off, int len) {
            fireBeforeWriteResponseBodyEvent();
            out.write(s, off, len);
        }

    }

    public class XServletOutputStream extends ServletOutputStream {

        private final HttpServletRequest request;

        private final XHttpServletResponse response;

        private final ServletOutputStream servletOutputStream;

        public XServletOutputStream(ServletOutputStream servletOutputStream,
                HttpServletRequest request, XHttpServletResponse response) {
            super();
            this.servletOutputStream = servletOutputStream;
            this.response = response;
            this.request = request;
        }

        @Override
        public void close() throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.close();
        }

        private void fireOnBeforeWriteResponseBodyEvent() {
            if (!this.response.isWriteResponseBodyStarted()) {
                this.response.setWriteResponseBodyStarted(true);
                onBeforeWriteResponseBody(request, response);
            }
        }

        @Override
        public void flush() throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.flush();
        }

        @Override
        public void print(boolean b) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(b);
        }

        @Override
        public void print(char c) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(c);
        }

        @Override
        public void print(double d) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(d);
        }

        @Override
        public void print(float f) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(f);
        }

        @Override
        public void print(int i) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(i);
        }

        @Override
        public void print(long l) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(l);
        }

        @Override
        public void print(String s) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.print(s);
        }

        @Override
        public void println() throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println();
        }

        @Override
        public void println(boolean b) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(b);
        }

        @Override
        public void println(char c) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(c);
        }

        @Override
        public void println(double d) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(d);
        }

        @Override
        public void println(float f) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(f);
        }

        @Override
        public void println(int i) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(i);
        }

        @Override
        public void println(long l) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(l);
        }

        @Override
        public void println(String s) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.println(s);
        }

        @Override
        public void write(byte[] b) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.write(b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            fireOnBeforeWriteResponseBodyEvent();
            servletOutputStream.write(b);
        }

        /**
         * TODO SERVLET 3.1
         */
        @Override
        public boolean isReady() {
            // TODO Auto-generated method stub
            return false;
        }

        /**
         * TODO SERVLET 3.1
         */
        @Override
        public void setWriteListener(WriteListener listener) {
            // TODO Auto-generated method stub
        }
    }

    /**
     * 支持空格字符的逗号分隔字符串
     */
    private static final Pattern commaSeparatedValuesPattern = Pattern.compile("\\s*,\\s*");

    private static final String HEADER_CACHE_CONTROL = "Cache-Control";

    private static final String HEADER_EXPIRES = "Expires";

    private static final String HEADER_LAST_MODIFIED = "Last-Modified";

    private static final Log log = LogFactory.getLog(ExpiresFilter.class);

    private static final String PARAMETER_EXPIRES_BY_TYPE = "ExpiresByType";

    private static final String PARAMETER_EXPIRES_DEFAULT = "ExpiresDefault";

    private static final String PARAMETER_EXPIRES_EXCLUDED_RESPONSE_STATUS_CODES = "ExpiresExcludedResponseStatusCodes";

    /**
     * 将逗号分隔的数字列表转换为 {@code int[]}.
     *
     * @param commaDelimitedInts 可以是{@code null}
     * @return never {@code null} array
     */
    protected static int[] commaDelimitedListToIntArray(
            String commaDelimitedInts) {
        String[] intsAsStrings = commaDelimitedListToStringArray(commaDelimitedInts);
        int[] ints = new int[intsAsStrings.length];
        for (int i = 0; i < intsAsStrings.length; i++) {
            String intAsString = intsAsStrings[i];
            try {
                ints[i] = Integer.parseInt(intAsString);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Exception parsing number '" + i +
                        "' (zero based) of comma delimited list '" +
                        commaDelimitedInts + "'");
            }
        }
        return ints;
    }

    /**
     * 将给定逗号分隔的字符串列表转换为字符串数组
     *
     * @param commaDelimitedStrings 要分隔的字符串
     * @return array of patterns (non {@code null})
     */
    protected static String[] commaDelimitedListToStringArray(String commaDelimitedStrings) {
        return (commaDelimitedStrings == null || commaDelimitedStrings.length() == 0) ? new String[0]
                : commaSeparatedValuesPattern.split(commaDelimitedStrings);
    }

    /**
     * @return {@code true} 如果给定的{@code str}包含给定的{@code searchStr}.
     * 
     * @param str 将被搜索的字符串
     * @param searchStr 将被搜索的子字符串
     */
    protected static boolean contains(String str, String searchStr) {
        if (str == null || searchStr == null) {
            return false;
        }
        return str.indexOf(searchStr) >= 0;
    }

    /**
     * 将int数组转换为逗号分隔字符串
     * 
     * @param ints The int array
     * @return a comma separated string
     */
    protected static String intsToCommaDelimitedString(int[] ints) {
        if (ints == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < ints.length; i++) {
            result.append(ints[i]);
            if (i < (ints.length - 1)) {
                result.append(", ");
            }
        }
        return result.toString();
    }

    /**
     * @param str 要检查的字符串
     * @return {@code true}如果给定的{@code str}是{@code null}或具有零字符长度.
     */
    protected static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    /**
     * @param str 要检查的字符串
     * @return {@code true} 如果给定的{@code str}至少有一个字符(可以是空格).
     */
    protected static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * @return {@code true} 如果给定的{@code string}以{@code prefix}开头, 忽略大小写.
     *
     * @param string 可以是 {@code null}
     * @param prefix 可以是 {@code null}
     */
    protected static boolean startsWithIgnoreCase(String string, String prefix) {
        if (string == null || prefix == null) {
            return string == null && prefix == null;
        }
        if (prefix.length() > string.length()) {
            return false;
        }

        return string.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /**
     * @return 给定的{@code str}子集，在第一个给定{@code separator}出现的地方之前.
     * 返回{@code null}，如果给定的{@code str}或给定的{@code separator}是null.
     * 返回空字符串，如果{@code separator}是空的.
     *
     * @param str 可以是{@code null}
     * @param separator 可以是{@code null}
     */
    protected static String substringBefore(String str, String separator) {
        if (str == null || str.isEmpty() || separator == null) {
            return null;
        }

        if (separator.isEmpty()) {
            return "";
        }

        int separatorIndex = str.indexOf(separator);
        if (separatorIndex == -1) {
            return str;
        }
        return str.substring(0, separatorIndex);
    }

    /**
     * 默认过期配置.
     */
    private ExpiresConfiguration defaultExpiresConfiguration;

    /**
     * 响应状态码列表，{@link ExpiresFilter}不会生成过期header.
     */
    private int[] excludedResponseStatusCodes = new int[] { HttpServletResponse.SC_NOT_MODIFIED };

    /**
     * 按内容类型过期配置. 测试可见.
     */
    private Map<String, ExpiresConfiguration> expiresConfigurationByContentType = new LinkedHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest &&
                response instanceof HttpServletResponse) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            if (response.isCommitted()) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString(
                            "expiresFilter.responseAlreadyCommited",
                            httpRequest.getRequestURL()));
                }
                chain.doFilter(request, response);
            } else {
                XHttpServletResponse xResponse = new XHttpServletResponse(
                        httpRequest, httpResponse);
                chain.doFilter(request, xResponse);
                if (!xResponse.isWriteResponseBodyStarted()) {
                    // Empty response, manually trigger
                    // onBeforeWriteResponseBody()
                    onBeforeWriteResponseBody(httpRequest, xResponse);
                }
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    public ExpiresConfiguration getDefaultExpiresConfiguration() {
        return defaultExpiresConfiguration;
    }

    public String getExcludedResponseStatusCodes() {
        return intsToCommaDelimitedString(excludedResponseStatusCodes);
    }

    public int[] getExcludedResponseStatusCodesAsInts() {
        return excludedResponseStatusCodes;
    }

    /**
     * <p>
     * 返回给定{@link XHttpServletResponse}或{@code null}的到期日期, 如果没有为声明的内容类型配置到期日期.
     * </p>
     *
     * @param response Servlet响应
     * @return 过期日期
     */
    protected Date getExpirationDate(XHttpServletResponse response) {
        String contentType = response.getContentType();
        if (contentType != null) {
            contentType = contentType.toLowerCase(Locale.ENGLISH);
        }

        // 查找精确地content-type 匹配 (即 "text/html; charset=iso-8859-1")
        ExpiresConfiguration configuration = expiresConfigurationByContentType.get(contentType);
        if (configuration != null) {
            Date result = getExpirationDate(configuration, response);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString(
                        "expiresFilter.useMatchingConfiguration",
                        configuration, contentType, contentType, result));
            }
            return result;
        }

        if (contains(contentType, ";")) {
            // 查找content-type , 不包括字符集 (e.g. "text/html")
            String contentTypeWithoutCharset = substringBefore(contentType, ";").trim();
            configuration = expiresConfigurationByContentType.get(contentTypeWithoutCharset);

            if (configuration != null) {
                Date result = getExpirationDate(configuration, response);
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString(
                            "expiresFilter.useMatchingConfiguration",
                            configuration, contentTypeWithoutCharset,
                            contentType, result));
                }
                return result;
            }
        }

        if (contains(contentType, "/")) {
            // 查找主要类型匹配 (e.g. "text")
            String majorType = substringBefore(contentType, "/");
            configuration = expiresConfigurationByContentType.get(majorType);
            if (configuration != null) {
                Date result = getExpirationDate(configuration, response);
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString(
                            "expiresFilter.useMatchingConfiguration",
                            configuration, majorType, contentType, result));
                }
                return result;
            }
        }

        if (defaultExpiresConfiguration != null) {
            Date result = getExpirationDate(defaultExpiresConfiguration,
                    response);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("expiresFilter.useDefaultConfiguration",
                        defaultExpiresConfiguration, contentType, result));
            }
            return result;
        }

        if (log.isDebugEnabled()) {
            log.debug(sm.getString(
                    "expiresFilter.noExpirationConfiguredForContentType",
                    contentType));
        }
        return null;
    }

    /**
     * <p>
     * 返回给定的{@link ExpiresConfiguration}, {@link HttpServletRequest}, {@link XHttpServletResponse}的到期日期.
     * </p>
     * 
     * @param configuration 解析的到期日
     * @param response Servlet响应
     * @return 过期日期
     */
    protected Date getExpirationDate(ExpiresConfiguration configuration,
            XHttpServletResponse response) {
        Calendar calendar;
        switch (configuration.getStartingPoint()) {
        case ACCESS_TIME:
            calendar = Calendar.getInstance();
            break;
        case LAST_MODIFICATION_TIME:
            if (response.isLastModifiedHeaderSet()) {
                try {
                    long lastModified = response.getLastModifiedHeader();
                    calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(lastModified);
                } catch (NumberFormatException e) {
                    // default to now
                    calendar = Calendar.getInstance();
                }
            } else {
                // Last-Modified header not found, use now
                calendar = Calendar.getInstance();
            }
            break;
        default:
            throw new IllegalStateException(sm.getString(
                    "expiresFilter.unsupportedStartingPoint",
                    configuration.getStartingPoint()));
        }
        for (Duration duration : configuration.getDurations()) {
            calendar.add(duration.getUnit().getCalendardField(),
                    duration.getAmount());
        }

        return calendar.getTime();
    }

    public Map<String, ExpiresConfiguration> getExpiresConfigurationByContentType() {
        return expiresConfigurationByContentType;
    }

    @Override
    protected Log getLogger() {
        return log;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        for (Enumeration<String> names = filterConfig.getInitParameterNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            String value = filterConfig.getInitParameter(name);

            try {
                if (name.startsWith(PARAMETER_EXPIRES_BY_TYPE)) {
                    String contentType = name.substring(
                            PARAMETER_EXPIRES_BY_TYPE.length()).trim().toLowerCase(Locale.ENGLISH);
                    ExpiresConfiguration expiresConfiguration = parseExpiresConfiguration(value);
                    this.expiresConfigurationByContentType.put(contentType,
                            expiresConfiguration);
                } else if (name.equalsIgnoreCase(PARAMETER_EXPIRES_DEFAULT)) {
                    ExpiresConfiguration expiresConfiguration = parseExpiresConfiguration(value);
                    this.defaultExpiresConfiguration = expiresConfiguration;
                } else if (name.equalsIgnoreCase(PARAMETER_EXPIRES_EXCLUDED_RESPONSE_STATUS_CODES)) {
                    this.excludedResponseStatusCodes = commaDelimitedListToIntArray(value);
                } else {
                    log.warn(sm.getString(
                            "expiresFilter.unknownParameterIgnored", name,
                            value));
                }
            } catch (RuntimeException e) {
                throw new ServletException(sm.getString(
                        "expiresFilter.exceptionProcessingParameter", name,
                        value), e);
            }
        }

        log.debug(sm.getString("expiresFilter.filterInitialized",
                this.toString()));
    }

    /**
     * @param request Servlet请求
     * @param response Servlet响应
     * 
     * @return <code>true</code>如果可以添加过期header
     */
    protected boolean isEligibleToExpirationHeaderGeneration(
            HttpServletRequest request, XHttpServletResponse response) {
        boolean expirationHeaderHasBeenSet = response.containsHeader(HEADER_EXPIRES) ||
                contains(response.getCacheControlHeader(), "max-age");
        if (expirationHeaderHasBeenSet) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString(
                        "expiresFilter.expirationHeaderAlreadyDefined",
                        request.getRequestURI(),
                        Integer.valueOf(response.getStatus()),
                        response.getContentType()));
            }
            return false;
        }

        for (int skippedStatusCode : this.excludedResponseStatusCodes) {
            if (response.getStatus() == skippedStatusCode) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("expiresFilter.skippedStatusCode",
                            request.getRequestURI(),
                            Integer.valueOf(response.getStatus()),
                            response.getContentType()));
                }
                return false;
            }
        }

        return true;
    }

    /**
     * <p>
     * 如果servlet没有设置过期header，而且过期已经在{@link ExpiresFilter}配置中定义,
     * 设置'{@code Expires}' header和'{@code Cache-Control}' header的 '{@code max-age}'属性.
     * </p>
     * <p>
     * 必须在"Start Write Response Body"事件上调用.
     * </p>
     * @param request The Servlet request
     * @param response The Servlet response
     */
    public void onBeforeWriteResponseBody(HttpServletRequest request,
            XHttpServletResponse response) {

        if (!isEligibleToExpirationHeaderGeneration(request, response)) {
            return;
        }

        Date expirationDate = getExpirationDate(response);
        if (expirationDate == null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("expiresFilter.noExpirationConfigured",
                        request.getRequestURI(),
                        Integer.valueOf(response.getStatus()),
                        response.getContentType()));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("expiresFilter.setExpirationDate",
                        request.getRequestURI(),
                        Integer.valueOf(response.getStatus()),
                        response.getContentType(), expirationDate));
            }

            String maxAgeDirective = "max-age=" +
                    ((expirationDate.getTime() - System.currentTimeMillis()) / 1000);

            String cacheControlHeader = response.getCacheControlHeader();
            String newCacheControlHeader = (cacheControlHeader == null) ? maxAgeDirective
                    : cacheControlHeader + ", " + maxAgeDirective;
            response.setHeader(HEADER_CACHE_CONTROL, newCacheControlHeader);
            response.setDateHeader(HEADER_EXPIRES, expirationDate.getTime());
        }

    }

    /**
     * 解析配置行，例如'{@code access plus 1 month 15 days 2 hours}'或'{@code modification 1 day 2 hours 5 seconds}'
     *
     * @param inputLine 输入
     * @return 解析的过期
     */
    protected ExpiresConfiguration parseExpiresConfiguration(String inputLine) {
        String line = inputLine.trim();

        StringTokenizer tokenizer = new StringTokenizer(line, " ");

        String currentToken;

        try {
            currentToken = tokenizer.nextToken();
        } catch (NoSuchElementException e) {
            throw new IllegalStateException(sm.getString(
                    "expiresFilter.startingPointNotFound", line));
        }

        StartingPoint startingPoint;
        if ("access".equalsIgnoreCase(currentToken) ||
                "now".equalsIgnoreCase(currentToken)) {
            startingPoint = StartingPoint.ACCESS_TIME;
        } else if ("modification".equalsIgnoreCase(currentToken)) {
            startingPoint = StartingPoint.LAST_MODIFICATION_TIME;
        } else if (!tokenizer.hasMoreTokens() &&
                startsWithIgnoreCase(currentToken, "a")) {
            startingPoint = StartingPoint.ACCESS_TIME;
            // trick : 将持续时间配置从旧模式转换为新模式
            tokenizer = new StringTokenizer(currentToken.substring(1) +
                    " seconds", " ");
        } else if (!tokenizer.hasMoreTokens() &&
                startsWithIgnoreCase(currentToken, "m")) {
            startingPoint = StartingPoint.LAST_MODIFICATION_TIME;
            // trick : 将持续时间配置从旧模式转换为新模式
            tokenizer = new StringTokenizer(currentToken.substring(1) +
                    " seconds", " ");
        } else {
            throw new IllegalStateException(sm.getString(
                    "expiresFilter.startingPointInvalid", currentToken, line));
        }

        try {
            currentToken = tokenizer.nextToken();
        } catch (NoSuchElementException e) {
            throw new IllegalStateException(sm.getString(
                    "expiresFilter.noDurationFound", line));
        }

        if ("plus".equalsIgnoreCase(currentToken)) {
            // skip
            try {
                currentToken = tokenizer.nextToken();
            } catch (NoSuchElementException e) {
                throw new IllegalStateException(sm.getString(
                        "expiresFilter.noDurationFound", line));
            }
        }

        List<Duration> durations = new ArrayList<>();

        while (currentToken != null) {
            int amount;
            try {
                amount = Integer.parseInt(currentToken);
            } catch (NumberFormatException e) {
                throw new IllegalStateException(sm.getString(
                        "expiresFilter.invalidDurationNumber",
                        currentToken, line));
            }

            try {
                currentToken = tokenizer.nextToken();
            } catch (NoSuchElementException e) {
                throw new IllegalStateException(
                        sm.getString(
                                "expiresFilter.noDurationUnitAfterAmount",
                                Integer.valueOf(amount), line));
            }
            DurationUnit durationUnit;
            if ("year".equalsIgnoreCase(currentToken) ||
                    "years".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.YEAR;
            } else if ("month".equalsIgnoreCase(currentToken) ||
                    "months".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.MONTH;
            } else if ("week".equalsIgnoreCase(currentToken) ||
                    "weeks".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.WEEK;
            } else if ("day".equalsIgnoreCase(currentToken) ||
                    "days".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.DAY;
            } else if ("hour".equalsIgnoreCase(currentToken) ||
                    "hours".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.HOUR;
            } else if ("minute".equalsIgnoreCase(currentToken) ||
                    "minutes".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.MINUTE;
            } else if ("second".equalsIgnoreCase(currentToken) ||
                    "seconds".equalsIgnoreCase(currentToken)) {
                durationUnit = DurationUnit.SECOND;
            } else {
                throw new IllegalStateException(
                        sm.getString(
                                "expiresFilter.invalidDurationUnit",
                                currentToken, line));
            }

            Duration duration = new Duration(amount, durationUnit);
            durations.add(duration);

            if (tokenizer.hasMoreTokens()) {
                currentToken = tokenizer.nextToken();
            } else {
                currentToken = null;
            }
        }

        return new ExpiresConfiguration(startingPoint, durations);
    }

    public void setDefaultExpiresConfiguration(
            ExpiresConfiguration defaultExpiresConfiguration) {
        this.defaultExpiresConfiguration = defaultExpiresConfiguration;
    }

    public void setExcludedResponseStatusCodes(int[] excludedResponseStatusCodes) {
        this.excludedResponseStatusCodes = excludedResponseStatusCodes;
    }

    public void setExpiresConfigurationByContentType(
            Map<String, ExpiresConfiguration> expiresConfigurationByContentType) {
        this.expiresConfigurationByContentType = expiresConfigurationByContentType;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[excludedResponseStatusCode=[" +
                intsToCommaDelimitedString(this.excludedResponseStatusCodes) +
                "], default=" + this.defaultExpiresConfiguration + ", byType=" +
                this.expiresConfigurationByContentType + "]";
    }
}
