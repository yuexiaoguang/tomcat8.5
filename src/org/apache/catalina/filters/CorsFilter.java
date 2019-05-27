package org.apache.catalina.filters;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>
 * {@link Filter}通过实现W3C的CORS资源规范来启用客户端跨源请求(<b>C</b>ross-<b>O</b>rigin <b>R</b>esource<b>S</b>haring).
 * 每个{@link HttpServletRequest}请求按规范检验, 而且对应的响应 header被添加到{@link HttpServletResponse}.
 * </p>
 *
 * <p>
 * 默认情况下, 它还设置以下请求属性, 这有助于确定下游请求的性质.
 * </p>
 * <ul>
 * <li><b>cors.isCorsRequest:</b> 确定请求是否是CORS请求. 设置为<code>true</code>如果是CORS请求; 否则<code>false</code>.</li>
 * <li><b>cors.request.origin:</b> The Origin URL, 即来自请求的页面的URL.</li>
 * <li>
 * <b>cors.request.type:</b> Type of request. Possible values:
 * <ul>
 * <li>SIMPLE: A request which is not preceded by a pre-flight request.</li>
 * <li>ACTUAL: A request which is preceded by a pre-flight request.</li>
 * <li>PRE_FLIGHT: A pre-flight request.</li>
 * <li>NOT_CORS: 正常同源请求.</li>
 * <li>INVALID_CORS: 无效的交叉原点请求.</li>
 * </ul>
 * </li>
 * <li><b>cors.request.headers:</b> Request headers sent as
 * 'Access-Control-Request-Headers' header, for pre-flight request.</li>
 * </ul>
 */
public class CorsFilter implements Filter {

    private static final Log log = LogFactory.getLog(CorsFilter.class);
    private static final StringManager sm = StringManager.getManager(CorsFilter.class);


    /**
     * 允许访问资源的来源.
     */
    private final Collection<String> allowedOrigins = new HashSet<>();

    /**
     * 确定是否允许任何源请求.
     */
    private boolean anyOriginAllowed;

    /**
     * 资源支持的零或多个HTTP方法.
     */
    private final Collection<String> allowedHttpMethods = new HashSet<>();

    /**
     * 由资源支持的零或多个Header字段名称.
     */
    private final Collection<String> allowedHttpHeaders = new HashSet<>();

    /**
     * 除了资源可能使用并可被暴露的简单响应header之外的header字段名称.
     */
    private final Collection<String> exposedHeaders = new HashSet<>();

    /**
     * 资源是否支持请求中的用户凭据.
     */
    private boolean supportsCredentials;

    /**
     * pre-flight请求可以缓存的时间(in seconds).
     */
    private long preflightMaxAge;

    /**
     * 确定请求是否应该被修饰.
     */
    private boolean decorateRequest;


    @Override
    public void doFilter(final ServletRequest servletRequest,
            final ServletResponse servletResponse, final FilterChain filterChain)
            throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest) ||
                !(servletResponse instanceof HttpServletResponse)) {
            throw new ServletException(sm.getString("corsFilter.onlyHttp"));
        }

        // Safe to downcast at this point.
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Determines the CORS request type.
        CorsFilter.CORSRequestType requestType = checkRequestType(request);

        // Adds CORS specific attributes to request.
        if (decorateRequest) {
            CorsFilter.decorateCORSProperties(request, requestType);
        }
        switch (requestType) {
        case SIMPLE:
            // Handles a Simple CORS request.
            this.handleSimpleCORS(request, response, filterChain);
            break;
        case ACTUAL:
            // Handles an Actual CORS request.
            this.handleSimpleCORS(request, response, filterChain);
            break;
        case PRE_FLIGHT:
            // Handles a Pre-flight CORS request.
            this.handlePreflightCORS(request, response, filterChain);
            break;
        case NOT_CORS:
            // Handles a Normal request that is not a cross-origin request.
            this.handleNonCORS(request, response, filterChain);
            break;
        default:
            // Handles a CORS request that violates specification.
            this.handleInvalidCORS(request, response, filterChain);
            break;
        }
    }


    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        parseAndStore(
                getInitParameter(filterConfig,
                        PARAM_CORS_ALLOWED_ORIGINS,     DEFAULT_ALLOWED_ORIGINS),
                getInitParameter(filterConfig,
                        PARAM_CORS_ALLOWED_METHODS,     DEFAULT_ALLOWED_HTTP_METHODS),
                getInitParameter(filterConfig,
                        PARAM_CORS_ALLOWED_HEADERS,     DEFAULT_ALLOWED_HTTP_HEADERS),
                getInitParameter(filterConfig,
                        PARAM_CORS_EXPOSED_HEADERS,     DEFAULT_EXPOSED_HEADERS),
                getInitParameter(filterConfig,
                        PARAM_CORS_SUPPORT_CREDENTIALS, DEFAULT_SUPPORTS_CREDENTIALS),
                getInitParameter(filterConfig,
                        PARAM_CORS_PREFLIGHT_MAXAGE,    DEFAULT_PREFLIGHT_MAXAGE),
                getInitParameter(filterConfig,
                        PARAM_CORS_REQUEST_DECORATE,    DEFAULT_DECORATE_REQUEST)
        );
    }


    /**
     * 返回参数的值, 或defaultValue.
     *
     * @param filterConfig  过滤器的配置
     * @param name          参数名
     * @param defaultValue  如果参数不存在，则返回默认值
     *
     * @return 参数的值或默认值
     */
    private String getInitParameter(FilterConfig filterConfig, String name, String defaultValue) {

        if (filterConfig == null) {
            return defaultValue;
        }

        String value = filterConfig.getInitParameter(name);
        if (value != null) {
            return value;
        }

        return defaultValue;
    }


    /**
     * 处理{@link CORSRequestType}.SIMPLE类型的CORS请求.
     *
     * @param request The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @param filterChain The {@link FilterChain} object.
     * 
     * @throws IOException IO错误
     * @throws ServletException Servlet error propagation
     */
    protected void handleSimpleCORS(final HttpServletRequest request,
            final HttpServletResponse response, final FilterChain filterChain)
            throws IOException, ServletException {

        CorsFilter.CORSRequestType requestType = checkRequestType(request);
        if (!(requestType == CorsFilter.CORSRequestType.SIMPLE ||
                requestType == CorsFilter.CORSRequestType.ACTUAL)) {
            throw new IllegalArgumentException(
                    sm.getString("corsFilter.wrongType2",
                            CorsFilter.CORSRequestType.SIMPLE,
                            CorsFilter.CORSRequestType.ACTUAL));
        }

        final String origin = request
                .getHeader(CorsFilter.REQUEST_HEADER_ORIGIN);
        final String method = request.getMethod();

        // Section 6.1.2
        if (!isOriginAllowed(origin)) {
            handleInvalidCORS(request, response, filterChain);
            return;
        }

        if (!allowedHttpMethods.contains(method)) {
            handleInvalidCORS(request, response, filterChain);
            return;
        }

        // Section 6.1.3
        // Add a single Access-Control-Allow-Origin header.
        if (anyOriginAllowed && !supportsCredentials) {
            // 如果资源不支持凭据，如果允许任何来源
            // 让 CORS 请求, 返回'*' header.
            response.addHeader(
                    CorsFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN,
                    "*");
        } else {
            // 如果资源支持凭证添加一个 Access-Control-Allow-Origin header, 使用Origin header作为值.
            response.addHeader(
                    CorsFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN,
                    origin);
        }

        // Section 6.1.3
        // 如果资源支持凭证, 添加一个Access-Control-Allow-Credentials header, 使用区分大小写的"true"作为值.
        if (supportsCredentials) {
            response.addHeader(
                    CorsFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS,
                    "true");
        }

        // Section 6.1.4
        // 如果暴露的header列表不是空的, 添加一个或多个Access-Control-Expose-Headers header, 使用暴露的header中的header字段名作为值.
        if ((exposedHeaders != null) && (exposedHeaders.size() > 0)) {
            String exposedHeadersString = join(exposedHeaders, ",");
            response.addHeader(
                    CorsFilter.RESPONSE_HEADER_ACCESS_CONTROL_EXPOSE_HEADERS,
                    exposedHeadersString);
        }

        // 指示响应取决于原点
        response.addHeader(CorsFilter.REQUEST_HEADER_VARY,
                CorsFilter.REQUEST_HEADER_ORIGIN);

        // Forward the request down the filter chain.
        filterChain.doFilter(request, response);
    }


    /**
     * 处理CORS pre-flight request.
     *
     * @param request The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @param filterChain The {@link FilterChain} object.
     * 
     * @throws IOException an IO error occurred
     * @throws ServletException Servlet error propagation
     */
    protected void handlePreflightCORS(final HttpServletRequest request,
            final HttpServletResponse response, final FilterChain filterChain)
            throws IOException, ServletException {

        CORSRequestType requestType = checkRequestType(request);
        if (requestType != CORSRequestType.PRE_FLIGHT) {
            throw new IllegalArgumentException(sm.getString("corsFilter.wrongType1",
                    CORSRequestType.PRE_FLIGHT.name().toLowerCase(Locale.ENGLISH)));
        }

        final String origin = request
                .getHeader(CorsFilter.REQUEST_HEADER_ORIGIN);

        // Section 6.2.2
        if (!isOriginAllowed(origin)) {
            handleInvalidCORS(request, response, filterChain);
            return;
        }

        // Section 6.2.3
        String accessControlRequestMethod = request.getHeader(
                CorsFilter.REQUEST_HEADER_ACCESS_CONTROL_REQUEST_METHOD);
        if (accessControlRequestMethod == null) {
            handleInvalidCORS(request, response, filterChain);
            return;
        } else {
            accessControlRequestMethod = accessControlRequestMethod.trim();
        }

        // Section 6.2.4
        String accessControlRequestHeadersHeader = request.getHeader(
                CorsFilter.REQUEST_HEADER_ACCESS_CONTROL_REQUEST_HEADERS);
        List<String> accessControlRequestHeaders = new LinkedList<>();
        if (accessControlRequestHeadersHeader != null &&
                !accessControlRequestHeadersHeader.trim().isEmpty()) {
            String[] headers = accessControlRequestHeadersHeader.trim().split(
                    ",");
            for (String header : headers) {
                accessControlRequestHeaders.add(header.trim().toLowerCase(Locale.ENGLISH));
            }
        }

        // Section 6.2.5
        if (!allowedHttpMethods.contains(accessControlRequestMethod)) {
            handleInvalidCORS(request, response, filterChain);
            return;
        }

        // Section 6.2.6
        if (!accessControlRequestHeaders.isEmpty()) {
            for (String header : accessControlRequestHeaders) {
                if (!allowedHttpHeaders.contains(header)) {
                    handleInvalidCORS(request, response, filterChain);
                    return;
                }
            }
        }

        // Section 6.2.7
        if (supportsCredentials) {
            response.addHeader(
                    CorsFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN,
                    origin);
            response.addHeader(
                    CorsFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS,
                    "true");
        } else {
            if (anyOriginAllowed) {
                response.addHeader(
                        CorsFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN,
                        "*");
            } else {
                response.addHeader(
                        CorsFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN,
                        origin);
            }
        }

        // Section 6.2.8
        if (preflightMaxAge > 0) {
            response.addHeader(
                    CorsFilter.RESPONSE_HEADER_ACCESS_CONTROL_MAX_AGE,
                    String.valueOf(preflightMaxAge));
        }

        // Section 6.2.9
        response.addHeader(
                CorsFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_METHODS,
                accessControlRequestMethod);

        // Section 6.2.10
        if ((allowedHttpHeaders != null) && (!allowedHttpHeaders.isEmpty())) {
            response.addHeader(
                    CorsFilter.RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_HEADERS,
                    join(allowedHttpHeaders, ","));
        }

        // Do not forward the request down the filter chain.
    }


    /**
     * 处理请求, 不是CORS 请求, 但是一个有效的请求.
     * 它不是交叉原点请求. 这个实现, 只是向下转发请求.
     *
     * @param request The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @param filterChain The {@link FilterChain} object.
     * 
     * @throws IOException an IO error occurred
     * @throws ServletException Servlet error propagation
     */
    private void handleNonCORS(final HttpServletRequest request,
            final HttpServletResponse response, final FilterChain filterChain)
            throws IOException, ServletException {
        // Let request pass.
        filterChain.doFilter(request, response);
    }


    /**
     * 处理违反规范的CORS请求.
     *
     * @param request The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @param filterChain The {@link FilterChain} object.
     */
    private void handleInvalidCORS(final HttpServletRequest request,
            final HttpServletResponse response, final FilterChain filterChain) {
        String origin = request.getHeader(CorsFilter.REQUEST_HEADER_ORIGIN);
        String method = request.getMethod();
        String accessControlRequestHeaders = request.getHeader(
                REQUEST_HEADER_ACCESS_CONTROL_REQUEST_HEADERS);

        response.setContentType("text/plain");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.resetBuffer();

        if (log.isDebugEnabled()) {
            // Debug so no need for i18n
            StringBuilder message =
                    new StringBuilder("Invalid CORS request; Origin=");
            message.append(origin);
            message.append(";Method=");
            message.append(method);
            if (accessControlRequestHeaders != null) {
                message.append(";Access-Control-Request-Headers=");
                message.append(accessControlRequestHeaders);
            }
            log.debug(message.toString());
        }
    }


    @Override
    public void destroy() {
        // NOOP
    }


    /**
     * 装饰{@link HttpServletRequest}, 使用 CORS 属性.
     * <ul>
     * <li><b>cors.isCorsRequest:</b>确定是否是 CORS请求. 设置为<code>true</code>如果是 CORS 请求; 否则<code>false</code>.</li>
     * <li><b>cors.request.origin:</b> The Origin URL.</li>
     * <li><b>cors.request.type:</b> Type of request. Values:
     * <code>simple</code>或<code>preflight</code>或<code>not_cors</code>或<code>invalid_cors</code></li>
     * <li><b>cors.request.headers:</b> Request headers sent as
     * 'Access-Control-Request-Headers' header, for pre-flight request.</li>
     * </ul>
     *
     * @param request The {@link HttpServletRequest} object.
     * @param corsRequestType The {@link CORSRequestType} object.
     */
    protected static void decorateCORSProperties(
            final HttpServletRequest request,
            final CORSRequestType corsRequestType) {
        if (request == null) {
            throw new IllegalArgumentException(
                    sm.getString("corsFilter.nullRequest"));
        }

        if (corsRequestType == null) {
            throw new IllegalArgumentException(
                    sm.getString("corsFilter.nullRequestType"));
        }

        switch (corsRequestType) {
        case SIMPLE:
            request.setAttribute(
                    CorsFilter.HTTP_REQUEST_ATTRIBUTE_IS_CORS_REQUEST,
                    Boolean.TRUE);
            request.setAttribute(CorsFilter.HTTP_REQUEST_ATTRIBUTE_ORIGIN,
                    request.getHeader(CorsFilter.REQUEST_HEADER_ORIGIN));
            request.setAttribute(
                    CorsFilter.HTTP_REQUEST_ATTRIBUTE_REQUEST_TYPE,
                    corsRequestType.name().toLowerCase(Locale.ENGLISH));
            break;
        case ACTUAL:
            request.setAttribute(
                    CorsFilter.HTTP_REQUEST_ATTRIBUTE_IS_CORS_REQUEST,
                    Boolean.TRUE);
            request.setAttribute(CorsFilter.HTTP_REQUEST_ATTRIBUTE_ORIGIN,
                    request.getHeader(CorsFilter.REQUEST_HEADER_ORIGIN));
            request.setAttribute(
                    CorsFilter.HTTP_REQUEST_ATTRIBUTE_REQUEST_TYPE,
                    corsRequestType.name().toLowerCase(Locale.ENGLISH));
            break;
        case PRE_FLIGHT:
            request.setAttribute(
                    CorsFilter.HTTP_REQUEST_ATTRIBUTE_IS_CORS_REQUEST,
                    Boolean.TRUE);
            request.setAttribute(CorsFilter.HTTP_REQUEST_ATTRIBUTE_ORIGIN,
                    request.getHeader(CorsFilter.REQUEST_HEADER_ORIGIN));
            request.setAttribute(
                    CorsFilter.HTTP_REQUEST_ATTRIBUTE_REQUEST_TYPE,
                    corsRequestType.name().toLowerCase(Locale.ENGLISH));
            String headers = request.getHeader(
                    REQUEST_HEADER_ACCESS_CONTROL_REQUEST_HEADERS);
            if (headers == null) {
                headers = "";
            }
            request.setAttribute(
                    CorsFilter.HTTP_REQUEST_ATTRIBUTE_REQUEST_HEADERS, headers);
            break;
        case NOT_CORS:
            request.setAttribute(
                    CorsFilter.HTTP_REQUEST_ATTRIBUTE_IS_CORS_REQUEST,
                    Boolean.FALSE);
            break;
        default:
            // Don't set any attributes
            break;
        }
    }


    /**
     * 转换{@link Set}为字符串, 每个元素由提供的分隔符分隔.
     *
     * @param elements 包含元素的{@link Set}.
     * @param joinSeparator 分隔符.
     * 
     * @return 添加的{@link String}; <code>null</code>如果{@link Set}是 null.
     */
    protected static String join(final Collection<String> elements, final String joinSeparator) {
        String separator = ",";
        if (elements == null) {
            return null;
        }
        if (joinSeparator != null) {
            separator = joinSeparator;
        }
        StringBuilder buffer = new StringBuilder();
        boolean isFirst = true;
        for (String element : elements) {
            if (!isFirst) {
                buffer.append(separator);
            } else {
                isFirst = false;
            }

            if (element != null) {
                buffer.append(element);
            }
        }

        return buffer.toString();
    }


    /**
     * 确定请求类型.
     *
     * @param request HTTP Servlet请求
     * @return the CORS type
     */
    protected CORSRequestType checkRequestType(final HttpServletRequest request) {
        CORSRequestType requestType = CORSRequestType.INVALID_CORS;
        if (request == null) {
            throw new IllegalArgumentException(
                    sm.getString("corsFilter.nullRequest"));
        }
        String originHeader = request.getHeader(REQUEST_HEADER_ORIGIN);
        // Section 6.1.1 and Section 6.2.1
        if (originHeader != null) {
            if (originHeader.isEmpty()) {
                requestType = CORSRequestType.INVALID_CORS;
            } else if (!isValidOrigin(originHeader)) {
                requestType = CORSRequestType.INVALID_CORS;
            } else if (isLocalOrigin(request, originHeader)) {
                return CORSRequestType.NOT_CORS;
            } else {
                String method = request.getMethod();
                if (method != null) {
                    if ("OPTIONS".equals(method)) {
                        String accessControlRequestMethodHeader =
                                request.getHeader(
                                        REQUEST_HEADER_ACCESS_CONTROL_REQUEST_METHOD);
                        if (accessControlRequestMethodHeader != null &&
                                !accessControlRequestMethodHeader.isEmpty()) {
                            requestType = CORSRequestType.PRE_FLIGHT;
                        } else if (accessControlRequestMethodHeader != null &&
                                accessControlRequestMethodHeader.isEmpty()) {
                            requestType = CORSRequestType.INVALID_CORS;
                        } else {
                            requestType = CORSRequestType.ACTUAL;
                        }
                    } else if ("GET".equals(method) || "HEAD".equals(method)) {
                        requestType = CORSRequestType.SIMPLE;
                    } else if ("POST".equals(method)) {
                        String mediaType = getMediaType(request.getContentType());
                        if (mediaType != null) {
                            if (SIMPLE_HTTP_REQUEST_CONTENT_TYPE_VALUES
                                    .contains(mediaType)) {
                                requestType = CORSRequestType.SIMPLE;
                            } else {
                                requestType = CORSRequestType.ACTUAL;
                            }
                        }
                    } else {
                        requestType = CORSRequestType.ACTUAL;
                    }
                }
            }
        } else {
            requestType = CORSRequestType.NOT_CORS;
        }

        return requestType;
    }


    private boolean isLocalOrigin(HttpServletRequest request, String origin) {

        // Build scheme://host:port from request
        StringBuilder target = new StringBuilder();
        String scheme = request.getScheme();
        if (scheme == null) {
            return false;
        } else {
            scheme = scheme.toLowerCase(Locale.ENGLISH);
        }
        target.append(scheme);
        target.append("://");

        String host = request.getServerName();
        if (host == null) {
            return false;
        }
        target.append(host);

        int port = request.getServerPort();
        if ("http".equals(scheme) && port != 80 ||
                "https".equals(scheme) && port != 443) {
            target.append(':');
            target.append(port);
        }

        return origin.equalsIgnoreCase(target.toString());
    }


    /**
     * 返回小写字母, 内容类型的media类型.
     */
    private String getMediaType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String result = contentType.toLowerCase(Locale.ENGLISH);
        int firstSemiColonIndex = result.indexOf(';');
        if (firstSemiColonIndex > -1) {
            result = result.substring(0, firstSemiColonIndex);
        }
        result = result.trim();
        return result;
    }

    /**
     * Checks if the Origin is allowed to make a CORS request.
     *
     * @param origin The Origin.
     * @return <code>true</code> if origin is allowed; <code>false</code>
     *         otherwise.
     */
    private boolean isOriginAllowed(final String origin) {
        if (anyOriginAllowed) {
            return true;
        }

        // If 'Origin' header is a case-sensitive match of any of allowed
        // origins, then return true, else return false.
        return allowedOrigins.contains(origin);
    }


    /**
     * 解析每个参数值并填充配置变量. 如果提供参数, 重写默认的.
     *
     * @param allowedOrigins 逗号分隔的来源.
     * @param allowedHttpMethods 逗号分隔的 HTTP方法.
     * @param allowedHttpHeaders 逗号分隔的HTTP header.
     * @param exposedHeaders 逗号分隔的需要暴露的header
     * @param supportsCredentials "true"如果需要启用支持凭据.
     * @param preflightMaxAge 允许用户代理缓存pre-flight请求结果的秒数.
     * 
     * @throws ServletException
     */
    private void parseAndStore(final String allowedOrigins,
            final String allowedHttpMethods, final String allowedHttpHeaders,
            final String exposedHeaders, final String supportsCredentials,
            final String preflightMaxAge, final String decorateRequest)
                    throws ServletException {

        if (allowedOrigins.trim().equals("*")) {
            this.anyOriginAllowed = true;
        } else {
            this.anyOriginAllowed = false;
            Set<String> setAllowedOrigins = parseStringToSet(allowedOrigins);
            this.allowedOrigins.clear();
            this.allowedOrigins.addAll(setAllowedOrigins);
        }

        Set<String> setAllowedHttpMethods = parseStringToSet(allowedHttpMethods);
        this.allowedHttpMethods.clear();
        this.allowedHttpMethods.addAll(setAllowedHttpMethods);

        Set<String> setAllowedHttpHeaders = parseStringToSet(allowedHttpHeaders);
        Set<String> lowerCaseHeaders = new HashSet<>();
        for (String header : setAllowedHttpHeaders) {
            String lowerCase = header.toLowerCase(Locale.ENGLISH);
            lowerCaseHeaders.add(lowerCase);
        }
        this.allowedHttpHeaders.clear();
        this.allowedHttpHeaders.addAll(lowerCaseHeaders);

        Set<String> setExposedHeaders = parseStringToSet(exposedHeaders);
        this.exposedHeaders.clear();
        this.exposedHeaders.addAll(setExposedHeaders);

        // For any value other then 'true' this will be false.
        this.supportsCredentials = Boolean.parseBoolean(supportsCredentials);

        try {
            if (!preflightMaxAge.isEmpty()) {
                this.preflightMaxAge = Long.parseLong(preflightMaxAge);
            } else {
                this.preflightMaxAge = 0L;
            }
        } catch (NumberFormatException e) {
            throw new ServletException(
                    sm.getString("corsFilter.invalidPreflightMaxAge"), e);
        }

        // For any value other then 'true' this will be false.
        this.decorateRequest = Boolean.parseBoolean(decorateRequest);
    }

    /**
     * 逗号分隔字符串.
     *
     * @param data 要分隔的字符串.
     * 
     * @return Set<String>
     */
    private Set<String> parseStringToSet(final String data) {
        String[] splits;

        if (data != null && data.length() > 0) {
            splits = data.split(",");
        } else {
            splits = new String[] {};
        }

        Set<String> set = new HashSet<>();
        if (splits.length > 0) {
            for (String split : splits) {
                set.add(split.trim());
            }
        }

        return set;
    }


    /**
     * 检查给定的来源是否有效. Criteria:
     * <ul>
     * <li>如果编码字符存在于来源, 这是无效的.</li>
     * <li>如果来源是 "null", 这是有效的.</li>
     * <li>来源应该是有效的{@link URI}</li>
     * </ul>
     *
     * @param origin 来源URI
     * 
     * @return <code>true</code>如果来源是有效的
     */
    protected static boolean isValidOrigin(String origin) {
        // 编码字符检查. 有助于防止CRLF注入.
        if (origin.contains("%")) {
            return false;
        }

        // "null"是有效的来源
        if ("null".equals(origin)) {
            return true;
        }

        // RFC6454, section 4. "如果 uri-scheme是文件, 实现可以返回实现定义的值.".
        // 在该值上没有限制，因此将所有文件URI视为有效来源.
        if (origin.startsWith("file://")) {
            return true;
        }

        URI originURI;
        try {
            originURI = new URI(origin);
        } catch (URISyntaxException e) {
            return false;
        }
        // 如果URI方案为 null, 返回 false. Return true otherwise.
        return originURI.getScheme() != null;

    }


    /**
     * 确定CORS 请求是否允许任何来源.
     *
     * @return <code>true</code>如果允许; 否则false.
     */
    public boolean isAnyOriginAllowed() {
        return anyOriginAllowed;
    }


    /**
     * 获取暴露的header.
     *
     * @return 要暴露给浏览器的header.
     */
    public Collection<String> getExposedHeaders() {
        return exposedHeaders;
    }


    /**
     * 是否启用支持凭据.
     *
     * @return <code>true</code>如果支持凭据;
     *         否则<code>false</code>
     */
    public boolean isSupportsCredentials() {
        return supportsCredentials;
    }


    /**
     * 返回preflight响应缓存时间，in seconds.
     */
    public long getPreflightMaxAge() {
        return preflightMaxAge;
    }


    /**
     * 允许访问资源的来源.
     */
    public Collection<String> getAllowedOrigins() {
        return allowedOrigins;
    }


    /**
     * 允许请求的HTTP方法.
     *
     * @return {@link Set}
     */
    public Collection<String> getAllowedHttpMethods() {
        return allowedHttpMethods;
    }


    /**
     * 返回资源支持的header.
     */
    public Collection<String> getAllowedHttpHeaders() {
        return allowedHttpHeaders;
    }


    // -------------------------------------------------- CORS Response Headers
    /**
     * 通过返回响应中的原始请求头的值，指示是否可以共享资源.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN =
            "Access-Control-Allow-Origin";

    /**
     * 指示当OMIT凭据标志未设置时，是否可以响应请求.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS =
            "Access-Control-Allow-Credentials";

    /**
     * 指示向CORS API规范的API暴露哪些header是安全的
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_EXPOSE_HEADERS =
            "Access-Control-Expose-Headers";

    /**
     * 指示preflight请求的结果能缓存的时间.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_MAX_AGE =
            "Access-Control-Max-Age";

    /**
     * 真实请求可以使用的方法.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_METHODS =
            "Access-Control-Allow-Methods";

    /**
     * 真实请求可以使用的 header字段名称.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_HEADERS =
            "Access-Control-Allow-Headers";

    // -------------------------------------------------- CORS Request Headers

    /**
     * Vary header， 指示是否允许禁用代理缓存，通过指示响应取决于来源.
     */
    public static final String REQUEST_HEADER_VARY = "Vary";

    /**
     * Origin header，指示cross-origin请求或preflight 请求的来源.
     */
    public static final String REQUEST_HEADER_ORIGIN = "Origin";

    /**
     * Access-Control-Request-Method header， 指示作为preflight请求一部分的真实请求使用的方法.
     */
    public static final String REQUEST_HEADER_ACCESS_CONTROL_REQUEST_METHOD =
            "Access-Control-Request-Method";

    /**
     * Access-Control-Request-Headers header， 指示作为preflight请求一部分的真实请求使用的header.
     */
    public static final String REQUEST_HEADER_ACCESS_CONTROL_REQUEST_HEADERS =
            "Access-Control-Request-Headers";

    // ----------------------------------------------------- Request attributes
    /**
     * CORS请求属性的前缀.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_PREFIX = "cors.";

    /**
     * 包含请求来源的属性.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_ORIGIN =
            HTTP_REQUEST_ATTRIBUTE_PREFIX + "request.origin";

    /**
     * Boolean值, 建议请求是否是 CORS请求.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_IS_CORS_REQUEST =
            HTTP_REQUEST_ATTRIBUTE_PREFIX + "isCorsRequest";

    /**
     * CORS请求的类型, {@link CORSRequestType}类型.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_REQUEST_TYPE =
            HTTP_REQUEST_ATTRIBUTE_PREFIX + "request.type";

    /**
     * 请求header，作为'Access-Control-Request-Headers' header, 对于pre-flight 请求.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_REQUEST_HEADERS =
            HTTP_REQUEST_ATTRIBUTE_PREFIX + "request.headers";

    // -------------------------------------------------------------- Constants
    /**
     * CORS 请求的类型. 此外，还提供实用方法来确定请求类型.
     */
    protected static enum CORSRequestType {
        /**
         * 简单的HTTP 请求, 即不应该是pre-flighted.
         */
        SIMPLE,
        /**
         * pre-flighted HTTP请求.
         */
        ACTUAL,
        /**
         * pre-flight CORS请求, 获取元信息, 在发送non-simple HTTP请求之前.
         */
        PRE_FLIGHT,
        /**
         * 不是CORS 请求, 不是普通请求.
         */
        NOT_CORS,
        /**
         * 无效的CORS 请求, 即一个CORS 请求, 但不是有效的.
         */
        INVALID_CORS
    }

    /**
     * Content-Type header的media类型值, 将被视为'simple'. 注意， media-type值比较忽略参数和不区分大小写的方式.
     */
    public static final Collection<String> SIMPLE_HTTP_REQUEST_CONTENT_TYPE_VALUES =
            new HashSet<>(Arrays.asList("application/x-www-form-urlencoded",
                    "multipart/form-data", "text/plain"));

    // ------------------------------------------------ Configuration Defaults
    /**
     * 默认情况下, 允许所有来源.
     */
    public static final String DEFAULT_ALLOWED_ORIGINS = "*";

    /**
     * 默认情况下, 支持下列方法: GET, POST, HEAD, OPTIONS.
     */
    public static final String DEFAULT_ALLOWED_HTTP_METHODS = "GET,POST,HEAD,OPTIONS";

    /**
     * 默认情况下, 缓存pre-flight 响应的持续时间是 30 mins.
     */
    public static final String DEFAULT_PREFLIGHT_MAXAGE = "1800";

    /**
     * 默认情况下, 支持凭据.
     */
    public static final String DEFAULT_SUPPORTS_CREDENTIALS = "true";

    /**
     * 默认情况下, 支持下列header:
     * Origin,Accept,X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers.
     */
    public static final String DEFAULT_ALLOWED_HTTP_HEADERS =
            "Origin,Accept,X-Requested-With,Content-Type," +
            "Access-Control-Request-Method,Access-Control-Request-Headers";

    /**
     * 默认情况下, 响应中没有暴露header.
     */
    public static final String DEFAULT_EXPOSED_HEADERS = "";

    /**
     * 默认情况下, 请求是否使用 CORS 属性支持.
     */
    public static final String DEFAULT_DECORATE_REQUEST = "true";

    // ----------------------------------------Filter Config Init param-name(s)
    /**
     * 从{@link javax.servlet.FilterConfig}检索允许的来源的Key.
     */
    public static final String PARAM_CORS_ALLOWED_ORIGINS =
            "cors.allowed.origins";

    /**
     * 从{@link javax.servlet.FilterConfig}检索支持的凭证的Key.
     */
    public static final String PARAM_CORS_SUPPORT_CREDENTIALS =
            "cors.support.credentials";

    /**
     * 从{@link javax.servlet.FilterConfig}检索暴露的header的Key.
     */
    public static final String PARAM_CORS_EXPOSED_HEADERS =
            "cors.exposed.headers";

    /**
     * 从{@link javax.servlet.FilterConfig}检索允许的header的Key.
     */
    public static final String PARAM_CORS_ALLOWED_HEADERS =
            "cors.allowed.headers";

    /**
     * 从{@link javax.servlet.FilterConfig}检索允许的方法的Key.
     */
    public static final String PARAM_CORS_ALLOWED_METHODS =
            "cors.allowed.methods";

    /**
     * 从{@link javax.servlet.FilterConfig}检索preflight最大时间.
     */
    public static final String PARAM_CORS_PREFLIGHT_MAXAGE =
            "cors.preflight.maxage";

    /**
     * 确定是否应装饰请求的Key.
     */
    public static final String PARAM_CORS_REQUEST_DECORATE =
            "cors.request.decorate";
}
