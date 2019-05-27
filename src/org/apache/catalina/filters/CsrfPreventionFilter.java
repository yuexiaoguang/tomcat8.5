package org.apache.catalina.filters;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;

/**
 * 为Web应用程序提供基本的CSRF保护. 过滤器假设:
 * <ul>
 * <li>过滤器映射为 /*</li>
 * <li>{@link HttpServletResponse#encodeRedirectURL(String)}和
 * {@link HttpServletResponse#encodeURL(String)}用于对返回到客户端的所有URL进行编码
 * </ul>
 */
public class CsrfPreventionFilter extends CsrfPreventionFilterBase {

    private final Set<String> entryPoints = new HashSet<>();

    private int nonceCacheSize = 5;

    /**
     * 入口点是不存在有效的随机数的URL.
     * 它们被用来提供一种导航到被保护的应用程序的方法. 入口点将限制于HTTP GET请求，并且不应触发任何安全敏感的操作.
     *
     * @param entryPoints   将被配置为入口点的URL逗号分隔列表.
     */
    public void setEntryPoints(String entryPoints) {
        String values[] = entryPoints.split(",");
        for (String value : values) {
            this.entryPoints.add(value.trim());
        }
    }

    /**
     * 设置先前发布的数量，将在LRU基础上缓存以支持并行请求, 在浏览器中使用刷新和返回以及类似的行为，可能会导致提交以前的而不是当前的.
     * 如果未设置, 默认使用 5.
     *
     * @param nonceCacheSize    缓存的数量
     */
    public void setNonceCacheSize(int nonceCacheSize) {
        this.nonceCacheSize = nonceCacheSize;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        ServletResponse wResponse = null;

        if (request instanceof HttpServletRequest &&
                response instanceof HttpServletResponse) {

            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;

            boolean skipNonceCheck = false;

            if (Constants.METHOD_GET.equals(req.getMethod())
                    && entryPoints.contains(getRequestedPath(req))) {
                skipNonceCheck = true;
            }

            HttpSession session = req.getSession(false);

            @SuppressWarnings("unchecked")
            LruCache<String> nonceCache = (session == null) ? null
                    : (LruCache<String>) session.getAttribute(
                            Constants.CSRF_NONCE_SESSION_ATTR_NAME);

            if (!skipNonceCheck) {
                String previousNonce =
                    req.getParameter(Constants.CSRF_NONCE_REQUEST_PARAM);

                if (nonceCache == null || previousNonce == null ||
                        !nonceCache.contains(previousNonce)) {
                    res.sendError(getDenyStatus());
                    return;
                }
            }

            if (nonceCache == null) {
                nonceCache = new LruCache<>(nonceCacheSize);
                if (session == null) {
                    session = req.getSession(true);
                }
                session.setAttribute(
                        Constants.CSRF_NONCE_SESSION_ATTR_NAME, nonceCache);
            }

            String newNonce = generateNonce();

            nonceCache.add(newNonce);

            wResponse = new CsrfResponseWrapper(res, newNonce);
        } else {
            wResponse = response;
        }

        chain.doFilter(request, wResponse);
    }


    protected static class CsrfResponseWrapper
            extends HttpServletResponseWrapper {

        private final String nonce;

        public CsrfResponseWrapper(HttpServletResponse response, String nonce) {
            super(response);
            this.nonce = nonce;
        }

        @Override
        @Deprecated
        public String encodeRedirectUrl(String url) {
            return encodeRedirectURL(url);
        }

        @Override
        public String encodeRedirectURL(String url) {
            return addNonce(super.encodeRedirectURL(url));
        }

        @Override
        @Deprecated
        public String encodeUrl(String url) {
            return encodeURL(url);
        }

        @Override
        public String encodeURL(String url) {
            return addNonce(super.encodeURL(url));
        }

        /**
         * 返回添加到查询字符串的指定的URL.
         *
         * @param url 要编辑的URL
         */
        private String addNonce(String url) {

            if ((url == null) || (nonce == null)) {
                return (url);
            }

            String path = url;
            String query = "";
            String anchor = "";
            int pound = path.indexOf('#');
            if (pound >= 0) {
                anchor = path.substring(pound);
                path = path.substring(0, pound);
            }
            int question = path.indexOf('?');
            if (question >= 0) {
                query = path.substring(question);
                path = path.substring(0, question);
            }
            StringBuilder sb = new StringBuilder(path);
            if (query.length() >0) {
                sb.append(query);
                sb.append('&');
            } else {
                sb.append('?');
            }
            sb.append(Constants.CSRF_NONCE_REQUEST_PARAM);
            sb.append('=');
            sb.append(nonce);
            sb.append(anchor);
            return (sb.toString());
        }
    }

    protected static class LruCache<T> implements Serializable {

        private static final long serialVersionUID = 1L;

        // 虽然内部实现使用Map, 此缓存实现仅与密钥有关.
        private final Map<T,T> cache;

        public LruCache(final int cacheSize) {
            cache = new LinkedHashMap<T,T>() {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(Map.Entry<T,T> eldest) {
                    if (size() > cacheSize) {
                        return true;
                    }
                    return false;
                }
            };
        }

        public void add(T key) {
            synchronized (cache) {
                cache.put(key, null);
            }
        }

        public boolean contains(T key) {
            synchronized (cache) {
                return cache.containsKey(key);
            }
        }
    }
}
