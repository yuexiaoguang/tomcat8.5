package org.apache.catalina.authenticator;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.Realm;
import org.apache.catalina.connector.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.http.parser.Authorization;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;
import org.apache.tomcat.util.security.MD5Encoder;


/**
 * <b>Authenticator</b>和<b>Valve</b>的HTTP DIGEST认证的实现类(see RFC 2069).
 */
public class DigestAuthenticator extends AuthenticatorBase {

    private static final Log log = LogFactory.getLog(DigestAuthenticator.class);


    // -------------------------------------------------------------- Constants

    /**
     * Tomcat的 DIGEST实现类只支持auth的保护.
     */
    protected static final String QOP = "auth";


    // ----------------------------------------------------------- Constructors

    public DigestAuthenticator() {
        super();
        setCache(false);
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 当前正在跟踪服务器随机值的列表
     */
    protected Map<String,NonceInfo> nonces;


    /**
     * 用于生成随机数的最后一个时间戳. 每个随机数都应该得到一个唯一的时间戳.
     */
    protected long lastTimestamp = 0;
    protected final Object lastTimestampLock = new Object();


    /**
     * 缓存中保存的服务器未登录的最大数目. 如果未指定, 默认值为1000.
     */
    protected int nonceCacheSize = 1000;


    /**
     * 用于跟踪给定随机数的可见的临时计数值的窗口大小. 如果未指定, 默认值为100.
     */
    protected int nonceCountWindowSize = 100;

    /**
     * Private key.
     */
    protected String key = null;


    /**
     * 服务器未登录多长时间有效, 单位毫秒. 默认为5分钟.
     */
    protected long nonceValidity = 5 * 60 * 1000;


    /**
     * Opaque string.
     */
    protected String opaque;


    /**
     * 是否应按RCF 2617的要求验证URI? 可以在代理修改URI的反向代理中禁用.
     */
    protected boolean validateUri = true;

    // ------------------------------------------------------------- Properties

    public int getNonceCountWindowSize() {
        return nonceCountWindowSize;
    }


    public void setNonceCountWindowSize(int nonceCountWindowSize) {
        this.nonceCountWindowSize = nonceCountWindowSize;
    }


    public int getNonceCacheSize() {
        return nonceCacheSize;
    }


    public void setNonceCacheSize(int nonceCacheSize) {
        this.nonceCacheSize = nonceCacheSize;
    }


    public String getKey() {
        return key;
    }


    public void setKey(String key) {
        this.key = key;
    }


    public long getNonceValidity() {
        return nonceValidity;
    }


    public void setNonceValidity(long nonceValidity) {
        this.nonceValidity = nonceValidity;
    }


    public String getOpaque() {
        return opaque;
    }


    public void setOpaque(String opaque) {
        this.opaque = opaque;
    }


    public boolean isValidateUri() {
        return validateUri;
    }


    public void setValidateUri(boolean validateUri) {
        this.validateUri = validateUri;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 根据指定的登录配置对作出此请求的用户进行身份验证. 
     * 如果满足指定的约束，返回<code>true</code> , 
     * 否则返回<code>false</code>.
     *
     * @param request Request we are processing
     * @param response Response we are creating
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    protected boolean doAuthenticate(Request request, HttpServletResponse response)
            throws IOException {

        // NOTE: 不要试图使用任何现有的SSO会话重新验证,
        // 因为只有原始身份验证是BASIC 或 FORM才能生效, 这比这个应用指定的DIGEST 验证类型更不安全
        //
        // 更改为true，以允许先前的FORM或BASIC身份验证为该WebApp验证用户
        // TODO 使此成为可配置属性(in SingleSignOn??)
        if (checkForCachedAuthentication(request, response, false)) {
            return true;
        }

        // 验证已经包含在此请求中的所有凭据
        Principal principal = null;
        String authorization = request.getHeader("authorization");
        DigestInfo digestInfo = new DigestInfo(getOpaque(), getNonceValidity(),
                getKey(), nonces, isValidateUri());
        if (authorization != null) {
            if (digestInfo.parse(request, authorization)) {
                if (digestInfo.validate(request)) {
                    principal = digestInfo.authenticate(context.getRealm());
                }

                if (principal != null && !digestInfo.isNonceStale()) {
                    register(request, response, principal,
                            HttpServletRequest.DIGEST_AUTH,
                            digestInfo.getUsername(), null);
                    return true;
                }
            }
        }

        // 发送一个"unauthorized"响应和适当的质疑

        // 接下来，生成一个临时令牌(唯一的令牌).
        String nonce = generateNonce(request);

        setAuthenticateHeader(request, response, nonce,
                principal != null && digestInfo.isNonceStale());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }


    @Override
    protected String getAuthMethod() {
        return HttpServletRequest.DIGEST_AUTH;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 删除字符串上的引号. RFC2617 状态引用对于除域以外的所有参数都是可选的.
     *
     * @param quotedString 带引号的字符串
     * @param quotesRequired <code>true</code>如果需要引号
     * 
     * @return 没有引号的字符串
     */
    protected static String removeQuotes(String quotedString,
                                         boolean quotesRequired) {
        //support both quoted and non-quoted
        if (quotedString.length() > 0 && quotedString.charAt(0) != '"' &&
                !quotesRequired) {
            return quotedString;
        } else if (quotedString.length() > 2) {
            return quotedString.substring(1, quotedString.length() - 1);
        } else {
            return "";
        }
    }

    /**
     * 删除字符串上的引号.
     *
     * @param quotedString 带引号的字符串
     * 
     * @return 没有引号的字符串
     */
    protected static String removeQuotes(String quotedString) {
        return removeQuotes(quotedString, false);
    }

    /**
     * 生成唯一令牌。令牌是根据以下模式生成的. 
     * NOnceToken = Base64 ( MD5 ( client-IP ":" time-stamp ":" private-key ) ).
     *
     * @param request HTTP Servlet request
     * 
     * @return 生成的令牌
     */
    protected String generateNonce(Request request) {

        long currentTime = System.currentTimeMillis();

        synchronized (lastTimestampLock) {
            if (currentTime > lastTimestamp) {
                lastTimestamp = currentTime;
            } else {
                currentTime = ++lastTimestamp;
            }
        }

        String ipTimeKey =
            request.getRemoteAddr() + ":" + currentTime + ":" + getKey();

        byte[] buffer = ConcurrentMessageDigest.digestMD5(
                ipTimeKey.getBytes(StandardCharsets.ISO_8859_1));
        String nonce = currentTime + ":" + MD5Encoder.encode(buffer);

        NonceInfo info = new NonceInfo(currentTime, getNonceCountWindowSize());
        synchronized (nonces) {
            nonces.put(nonce, info);
        }

        return nonce;
    }


    /**
     * 生成WWW-Authenticate header.
     * <p>
     * header 必须遵循此模板 :
     * <pre>
     *      WWW-Authenticate    = "WWW-Authenticate" ":" "Digest"
     *                            digest-challenge
     *
     *      digest-challenge    = 1#( realm | [ domain ] | nonce |
     *                  [ digest-opaque ] |[ stale ] | [ algorithm ] )
     *
     *      realm               = "realm" "=" realm-value
     *      realm-value         = quoted-string
     *      domain              = "domain" "=" &lt;"&gt; 1#URI &lt;"&gt;
     *      nonce               = "nonce" "=" nonce-value
     *      nonce-value         = quoted-string
     *      opaque              = "opaque" "=" quoted-string
     *      stale               = "stale" "=" ( "true" | "false" )
     *      algorithm           = "algorithm" "=" ( "MD5" | token )
     * </pre>
     *
     * @param request HTTP Servlet request
     * @param response HTTP Servlet response
     * @param nonce 临时令牌
     * @param isNonceStale <code>true</code>添加过时参数
     */
    protected void setAuthenticateHeader(HttpServletRequest request,
                                         HttpServletResponse response,
                                         String nonce,
                                         boolean isNonceStale) {

        String realmName = getRealmName(context);

        String authenticateHeader;
        if (isNonceStale) {
            authenticateHeader = "Digest realm=\"" + realmName + "\", " +
            "qop=\"" + QOP + "\", nonce=\"" + nonce + "\", " + "opaque=\"" +
            getOpaque() + "\", stale=true";
        } else {
            authenticateHeader = "Digest realm=\"" + realmName + "\", " +
            "qop=\"" + QOP + "\", nonce=\"" + nonce + "\", " + "opaque=\"" +
            getOpaque() + "\"";
        }

        response.setHeader(AUTH_HEADER_NAME, authenticateHeader);

    }


    // ------------------------------------------------------- Lifecycle Methods

    @Override
    protected synchronized void startInternal() throws LifecycleException {
        super.startInternal();

        // 生成随机密钥
        if (getKey() == null) {
            setKey(sessionIdGenerator.generateSessionId());
        }

        // Generate the opaque string the same way
        if (getOpaque() == null) {
            setOpaque(sessionIdGenerator.generateSessionId());
        }

        nonces = new LinkedHashMap<String, DigestAuthenticator.NonceInfo>() {

            private static final long serialVersionUID = 1L;
            private static final long LOG_SUPPRESS_TIME = 5 * 60 * 1000;

            private long lastLog = 0;

            @Override
            protected boolean removeEldestEntry(
                    Map.Entry<String,NonceInfo> eldest) {
                // 这是从同步调用，所以保持简单
                long currentTime = System.currentTimeMillis();
                if (size() > getNonceCacheSize()) {
                    if (lastLog < currentTime &&
                            currentTime - eldest.getValue().getTimestamp() <
                            getNonceValidity()) {
                        // Replay attack is possible
                        log.warn(sm.getString(
                                "digestAuthenticator.cacheRemove"));
                        lastLog = currentTime + LOG_SUPPRESS_TIME;
                    }
                    return true;
                }
                return false;
            }
        };
    }

    public static class DigestInfo {

        private final String opaque;
        private final long nonceValidity;
        private final String key;
        private final Map<String,NonceInfo> nonces;
        private boolean validateUri = true;

        private String userName = null;
        private String method = null;
        private String uri = null;
        private String response = null;
        private String nonce = null;
        private String nc = null;
        private String cnonce = null;
        private String realmName = null;
        private String qop = null;
        private String opaqueReceived = null;

        private boolean nonceStale = false;


        public DigestInfo(String opaque, long nonceValidity, String key,
                Map<String,NonceInfo> nonces, boolean validateUri) {
            this.opaque = opaque;
            this.nonceValidity = nonceValidity;
            this.key = key;
            this.nonces = nonces;
            this.validateUri = validateUri;
        }


        public String getUsername() {
            return userName;
        }


        public boolean parse(Request request, String authorization) {
            // 验证授权凭证格式
            if (authorization == null) {
                return false;
            }

            Map<String,String> directives;
            try {
                directives = Authorization.parseAuthorizationDigest(
                        new StringReader(authorization));
            } catch (IOException e) {
                return false;
            }

            if (directives == null) {
                return false;
            }

            method = request.getMethod();
            userName = directives.get("username");
            realmName = directives.get("realm");
            nonce = directives.get("nonce");
            nc = directives.get("nc");
            cnonce = directives.get("cnonce");
            qop = directives.get("qop");
            uri = directives.get("uri");
            response = directives.get("response");
            opaqueReceived = directives.get("opaque");

            return true;
        }

        public boolean validate(Request request) {
            if ( (userName == null) || (realmName == null) || (nonce == null)
                 || (uri == null) || (response == null) ) {
                return false;
            }

            // 验证 URI - 应匹配客户端发送的请求行
            if (validateUri) {
                String uriQuery;
                String query = request.getQueryString();
                if (query == null) {
                    uriQuery = request.getRequestURI();
                } else {
                    uriQuery = request.getRequestURI() + "?" + query;
                }
                if (!uri.equals(uriQuery)) {
                    // 有些客户端（旧Android）使用绝对的URI来DIGEST，但在请求行中使用相对URI.
                    // request. 2.3.5 < fixed Android version <= 4.0.3
                    String host = request.getHeader("host");
                    String scheme = request.getScheme();
                    if (host != null && !uriQuery.startsWith(scheme)) {
                        StringBuilder absolute = new StringBuilder();
                        absolute.append(scheme);
                        absolute.append("://");
                        absolute.append(host);
                        absolute.append(uriQuery);
                        if (!uri.equals(absolute.toString())) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }

            // Validate the Realm name
            String lcRealm = getRealmName(request.getContext());
            if (!lcRealm.equals(realmName)) {
                return false;
            }

            // Validate the opaque string
            if (!opaque.equals(opaqueReceived)) {
                return false;
            }

            // Validate nonce
            int i = nonce.indexOf(':');
            if (i < 0 || (i + 1) == nonce.length()) {
                return false;
            }
            long nonceTime;
            try {
                nonceTime = Long.parseLong(nonce.substring(0, i));
            } catch (NumberFormatException nfe) {
                return false;
            }
            String md5clientIpTimeKey = nonce.substring(i + 1);
            long currentTime = System.currentTimeMillis();
            if ((currentTime - nonceTime) > nonceValidity) {
                nonceStale = true;
                synchronized (nonces) {
                    nonces.remove(nonce);
                }
            }
            String serverIpTimeKey =
                request.getRemoteAddr() + ":" + nonceTime + ":" + key;
            byte[] buffer = ConcurrentMessageDigest.digestMD5(
                    serverIpTimeKey.getBytes(StandardCharsets.ISO_8859_1));
            String md5ServerIpTimeKey = MD5Encoder.encode(buffer);
            if (!md5ServerIpTimeKey.equals(md5clientIpTimeKey)) {
                return false;
            }

            // Validate qop
            if (qop != null && !QOP.equals(qop)) {
                return false;
            }

            // Validate cnonce and nc
            // Check if presence of nc and Cnonce is consistent with presence of qop
            if (qop == null) {
                if (cnonce != null || nc != null) {
                    return false;
                }
            } else {
                if (cnonce == null || nc == null) {
                    return false;
                }
                // RFC 2617 says nc must be 8 digits long. Older Android clients
                // use 6. 2.3.5 < fixed Android version <= 4.0.3
                if (nc.length() < 6 || nc.length() > 8) {
                    return false;
                }
                long count;
                try {
                    count = Long.parseLong(nc, 16);
                } catch (NumberFormatException nfe) {
                    return false;
                }
                NonceInfo info;
                synchronized (nonces) {
                    info = nonces.get(nonce);
                }
                if (info == null) {
                    // Nonce有效但不在缓存中. 一定是从缓存中掉了出来 - 强制重新验证
                    nonceStale = true;
                } else {
                    if (!info.nonceCountValid(count)) {
                        return false;
                    }
                }
            }
            return true;
        }

        public boolean isNonceStale() {
            return nonceStale;
        }

        public Principal authenticate(Realm realm) {
            // Second MD5 digest used to calculate the digest :
            // MD5(Method + ":" + uri)
            String a2 = method + ":" + uri;

            byte[] buffer = ConcurrentMessageDigest.digestMD5(
                    a2.getBytes(StandardCharsets.ISO_8859_1));
            String md5a2 = MD5Encoder.encode(buffer);

            return realm.authenticate(userName, response, nonce, nc, cnonce,
                    qop, realmName, md5a2);
        }

    }

    public static class NonceInfo {
        private final long timestamp;
        private final boolean seen[];
        private final int offset;
        private int count = 0;

        public NonceInfo(long currentTime, int seenWindowSize) {
            this.timestamp = currentTime;
            seen = new boolean[seenWindowSize];
            offset = seenWindowSize / 2;
        }

        public synchronized boolean nonceCountValid(long nonceCount) {
            if ((count - offset) >= nonceCount ||
                    (nonceCount > count - offset + seen.length)) {
                return false;
            }
            int checkIndex = (int) ((nonceCount + offset) % seen.length);
            if (seen[checkIndex]) {
                return false;
            } else {
                seen[checkIndex] = true;
                seen[count % seen.length] = false;
                count++;
                return true;
            }
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
