package org.apache.catalina.filters;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 为REST API提供基本的CSRF保护. 过滤器假定客户端适合临时的传输 ，通过'X-CSRF-Token' header.
 *
 * <pre>
 * Positive scenario:
 *           Client                            Server
 *              |                                 |
 *              | GET Fetch Request              \| JSESSIONID
 *              |---------------------------------| X-CSRF-Token
 *              |                                /| pair generation
 *              |/Response to Fetch Request       |
 *              |---------------------------------|
 * JSESSIONID   |\                                |
 * X-CSRF-Token |                                 |
 * pair cached  | POST Request with valid nonce  \| JSESSIONID
 *              |---------------------------------| X-CSRF-Token
 *              |                                /| pair validation
 *              |/ Response to POST Request       |
 *              |---------------------------------|
 *              |\                                |
 *
 * Negative scenario:
 *           Client                            Server
 *              |                                 |
 *              | POST Request without nonce     \| JSESSIONID
 *              |---------------------------------| X-CSRF-Token
 *              |                                /| pair validation
 *              |/Request is rejected             |
 *              |---------------------------------|
 *              |\                                |
 *
 *           Client                            Server
 *              |                                 |
 *              | POST Request with invalid nonce\| JSESSIONID
 *              |---------------------------------| X-CSRF-Token
 *              |                                /| pair validation
 *              |/Request is rejected             |
 *              |---------------------------------|
 *              |\                                |
 * </pre>
 */
public class RestCsrfPreventionFilter extends CsrfPreventionFilterBase {
    private static enum MethodType {
        NON_MODIFYING_METHOD, MODIFYING_METHOD
    }

    private static final Pattern NON_MODIFYING_METHODS_PATTERN = Pattern
            .compile("GET|HEAD|OPTIONS");

    private Set<String> pathsAcceptingParams = new HashSet<>();

    private String pathsDelimiter = ",";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            MethodType mType = MethodType.MODIFYING_METHOD;
            String method = ((HttpServletRequest) request).getMethod();
            if (method != null && NON_MODIFYING_METHODS_PATTERN.matcher(method).matches()) {
                mType = MethodType.NON_MODIFYING_METHOD;
            }

            RestCsrfPreventionStrategy strategy;
            switch (mType) {
            case NON_MODIFYING_METHOD:
                strategy = new FetchRequest();
                break;
            default:
                strategy = new StateChangingRequest();
                break;
            }

            if (!strategy.apply((HttpServletRequest) request, (HttpServletResponse) response)) {
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private abstract static class RestCsrfPreventionStrategy {

        abstract boolean apply(HttpServletRequest request, HttpServletResponse response)
                throws IOException;

        protected String extractNonceFromRequestHeader(HttpServletRequest request, String key) {
            return request.getHeader(key);
        }

        protected String[] extractNonceFromRequestParams(HttpServletRequest request, String key) {
            return request.getParameterValues(key);
        }

        protected void storeNonceToResponse(HttpServletResponse response, String key, String value) {
            response.setHeader(key, value);
        }

        protected String extractNonceFromSession(HttpSession session, String key) {
            return session == null ? null : (String) session.getAttribute(key);
        }

        protected void storeNonceToSession(HttpSession session, String key, Object value) {
            session.setAttribute(key, value);
        }
    }

    private class StateChangingRequest extends RestCsrfPreventionStrategy {

        @Override
        public boolean apply(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            if (isValidStateChangingRequest(
                    extractNonceFromRequest(request),
                    extractNonceFromSession(request.getSession(false),
                            Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME))) {
                return true;
            }

            storeNonceToResponse(response, Constants.CSRF_REST_NONCE_HEADER_NAME,
                    Constants.CSRF_REST_NONCE_HEADER_REQUIRED_VALUE);
            response.sendError(getDenyStatus(),
                    sm.getString("restCsrfPreventionFilter.invalidNonce"));
            return false;
        }

        private boolean isValidStateChangingRequest(String reqNonce, String sessionNonce) {
            return reqNonce != null && sessionNonce != null
                    && Objects.equals(reqNonce, sessionNonce);
        }

        private String extractNonceFromRequest(HttpServletRequest request) {
            String nonceFromRequest = extractNonceFromRequestHeader(request,
                    Constants.CSRF_REST_NONCE_HEADER_NAME);
            if ((nonceFromRequest == null || Objects.equals("", nonceFromRequest))
                    && !getPathsAcceptingParams().isEmpty()
                    && getPathsAcceptingParams().contains(getRequestedPath(request))) {
                nonceFromRequest = extractNonceFromRequestParams(request);
            }
            return nonceFromRequest;
        }

        private String extractNonceFromRequestParams(HttpServletRequest request) {
            String[] params = extractNonceFromRequestParams(request,
                    Constants.CSRF_REST_NONCE_HEADER_NAME);
            if (params != null && params.length > 0) {
                String nonce = params[0];
                for (String param : params) {
                    if (!Objects.equals(param, nonce)) {
                        return null;
                    }
                }
                return nonce;
            }
            return null;
        }
    }

    private class FetchRequest extends RestCsrfPreventionStrategy {

        @Override
        public boolean apply(HttpServletRequest request, HttpServletResponse response) {
            if (Constants.CSRF_REST_NONCE_HEADER_FETCH_VALUE
                    .equalsIgnoreCase(extractNonceFromRequestHeader(request,
                            Constants.CSRF_REST_NONCE_HEADER_NAME))) {
                String nonceFromSessionStr = extractNonceFromSession(request.getSession(false),
                        Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME);
                if (nonceFromSessionStr == null) {
                    nonceFromSessionStr = generateNonce();
                    storeNonceToSession(Objects.requireNonNull(request.getSession(true)),
                            Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME, nonceFromSessionStr);
                }
                storeNonceToResponse(response, Constants.CSRF_REST_NONCE_HEADER_NAME,
                        nonceFromSessionStr);
            }
            return true;
        }

    }

    /**
     * 一个逗号分隔的URL列表, 可以通过请求参数 'X-CSRF-Token'接受.
     * 对于无法通过header提供临时信息的用例, 可以通过请求参数提供. 如果是一个X-CSRF-Token header, 它将优先于请求中具有相同名称的任何参数.
     * 请求参数不能用于获取新的临时的 header.
     *
     * @param pathsList 将被配置为接受带有临时信息的请求参数的路径的逗号分隔的URL列表.
     */
    public void setPathsAcceptingParams(String pathsList) {
        if (pathsList != null) {
            for (String element : pathsList.split(pathsDelimiter)) {
                    pathsAcceptingParams.add(element.trim());
            }
        }
    }

    public Set<String> getPathsAcceptingParams() {
        return pathsAcceptingParams;
    }
}
