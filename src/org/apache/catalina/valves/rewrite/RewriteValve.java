package org.apache.catalina.valves.rewrite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Pipeline;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.URLEncoder;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.http.RequestUtil;

public class RewriteValve extends ValveBase {

    /**
     * valve 将使用的重写规则.
     */
    protected RewriteRule[] rules = null;


    /**
     * 如果发生重写, 整个请求将被再次处理.
     */
    protected ThreadLocal<Boolean> invoked = new ThreadLocal<>();


    /**
     * 配置文件的相对路径.
     * Note: 如果valve的容器是上下文, 它将相对于 /WEB-INF/.
     */
    protected String resourcePath = "rewrite.config";


    /**
     * 将设置为 true, 如果 valve和一个 context关联.
     */
    protected boolean context = false;


    /**
     * 启用这个组件
     */
    protected boolean enabled = true;

    /**
     * 规则使用的Map.
     */
    protected Map<String, RewriteMap> maps = new Hashtable<>();


    public RewriteValve() {
        super(true);
    }


    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        containerLog = LogFactory.getLog(getContainer().getLogName() + ".rewrite");
    }


    @Override
    protected synchronized void startInternal() throws LifecycleException {

        super.startInternal();

        InputStream is = null;

        // 处理这个 valve的配置文件
        if (getContainer() instanceof Context) {
            context = true;
            is = ((Context) getContainer()).getServletContext()
                .getResourceAsStream("/WEB-INF/" + resourcePath);
            if (containerLog.isDebugEnabled()) {
                if (is == null) {
                    containerLog.debug("No configuration resource found: /WEB-INF/" + resourcePath);
                } else {
                    containerLog.debug("Read configuration from: /WEB-INF/" + resourcePath);
                }
            }
        } else if (getContainer() instanceof Host) {
            String resourceName = getHostConfigPath(resourcePath);
            File file = new File(getConfigBase(), resourceName);
            try {
                if (!file.exists()) {
                    // Use getResource and getResourceAsStream
                    is = getClass().getClassLoader()
                        .getResourceAsStream(resourceName);
                    if (is != null && containerLog.isDebugEnabled()) {
                        containerLog.debug("Read configuration from CL at " + resourceName);
                    }
                } else {
                    if (containerLog.isDebugEnabled()) {
                        containerLog.debug("Read configuration from " + file.getAbsolutePath());
                    }
                    is = new FileInputStream(file);
                }
                if ((is == null) && (containerLog.isDebugEnabled())) {
                    containerLog.debug("No configuration resource found: " + resourceName +
                            " in " + getConfigBase() + " or in the classloader");
                }
            } catch (Exception e) {
                containerLog.error("Error opening configuration", e);
            }
        }

        if (is == null) {
            // 将使用管理操作动态配置 valve
            return;
        }

        try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isr)) {
            parse(reader);
        } catch (IOException ioe) {
            containerLog.error("Error closing configuration", ioe);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                containerLog.error("Error closing configuration", e);
            }
        }

    }

    public void setConfiguration(String configuration)
        throws Exception {
        if (containerLog == null) {
            containerLog = LogFactory.getLog(getContainer().getLogName() + ".rewrite");
        }
        maps.clear();
        parse(new BufferedReader(new StringReader(configuration)));
    }

    public String getConfiguration() {
        StringBuffer buffer = new StringBuffer();
        // FIXME: Output maps if possible
        for (int i = 0; i < rules.length; i++) {
            for (int j = 0; j < rules[i].getConditions().length; j++) {
                buffer.append(rules[i].getConditions()[j].toString()).append("\r\n");
            }
            buffer.append(rules[i].toString()).append("\r\n").append("\r\n");
        }
        return buffer.toString();
    }

    protected void parse(BufferedReader reader) throws LifecycleException {
        ArrayList<RewriteRule> rules = new ArrayList<>();
        ArrayList<RewriteCond> conditions = new ArrayList<>();
        while (true) {
            try {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                Object result = parse(line);
                if (result instanceof RewriteRule) {
                    RewriteRule rule = (RewriteRule) result;
                    if (containerLog.isDebugEnabled()) {
                        containerLog.debug("Add rule with pattern " + rule.getPatternString()
                                + " and substitution " + rule.getSubstitutionString());
                    }
                    for (int i = (conditions.size() - 1); i > 0; i--) {
                        if (conditions.get(i - 1).isOrnext()) {
                            conditions.get(i).setOrnext(true);
                        }
                    }
                    for (int i = 0; i < conditions.size(); i++) {
                        if (containerLog.isDebugEnabled()) {
                            RewriteCond cond = conditions.get(i);
                            containerLog.debug("Add condition " + cond.getCondPattern()
                                    + " test " + cond.getTestString() + " to rule with pattern "
                                    + rule.getPatternString() + " and substitution "
                                    + rule.getSubstitutionString() + (cond.isOrnext() ? " [OR]" : "")
                                    + (cond.isNocase() ? " [NC]" : ""));
                        }
                        rule.addCondition(conditions.get(i));
                    }
                    conditions.clear();
                    rules.add(rule);
                } else if (result instanceof RewriteCond) {
                    conditions.add((RewriteCond) result);
                } else if (result instanceof Object[]) {
                    String mapName = (String) ((Object[]) result)[0];
                    RewriteMap map = (RewriteMap) ((Object[]) result)[1];
                    maps.put(mapName, map);
                    if (map instanceof Lifecycle) {
                        ((Lifecycle) map).start();
                    }
                }
            } catch (IOException e) {
                containerLog.error("Error reading configuration", e);
            }
        }
        this.rules = rules.toArray(new RewriteRule[0]);

        // 解析规则完成
        for (int i = 0; i < this.rules.length; i++) {
            this.rules[i].parse(maps);
        }
    }

    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        super.stopInternal();
        Iterator<RewriteMap> values = maps.values().iterator();
        while (values.hasNext()) {
            RewriteMap map = values.next();
            if (map instanceof Lifecycle) {
                ((Lifecycle) map).stop();
            }
        }
        maps.clear();
        rules = null;
    }


    @Override
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        if (!getEnabled() || rules == null || rules.length == 0) {
            getNext().invoke(request, response);
            return;
        }

        if (Boolean.TRUE.equals(invoked.get())) {
            try {
                getNext().invoke(request, response);
            } finally {
                invoked.set(null);
            }
            return;
        }

        try {

            Resolver resolver = new ResolverImpl(request);

            invoked.set(Boolean.TRUE);

            // 只要MB不是一个字符序列或附属的, 必须将其转换为字符串
            Charset uriCharset = request.getConnector().getURICharset();
            String originalQueryStringEncoded = request.getQueryString();
            MessageBytes urlMB =
                    context ? request.getRequestPathMB() : request.getDecodedRequestURIMB();
            urlMB.toChars();
            CharSequence urlDecoded = urlMB.getCharChunk();
            CharSequence host = request.getServerName();
            boolean rewritten = false;
            boolean done = false;
            boolean qsa = false;
            for (int i = 0; i < rules.length; i++) {
                RewriteRule rule = rules[i];
                CharSequence test = (rule.isHost()) ? host : urlDecoded;
                CharSequence newtest = rule.evaluate(test, resolver);
                if (newtest != null && !test.equals(newtest.toString())) {
                    if (containerLog.isDebugEnabled()) {
                        containerLog.debug("Rewrote " + test + " as " + newtest
                                + " with rule pattern " + rule.getPatternString());
                    }
                    if (rule.isHost()) {
                        host = newtest;
                    } else {
                        urlDecoded = newtest;
                    }
                    rewritten = true;
                }

                // 在最后回复之前检查 QSA
                if (!qsa && newtest != null && rule.isQsappend()) {
                    // TODO: 如果添加QSD支持，这个逻辑将需要一些调整
                    qsa = true;
                }

                // Final reply

                // - forbidden
                if (rule.isForbidden() && newtest != null) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    done = true;
                    break;
                }
                // - gone
                if (rule.isGone() && newtest != null) {
                    response.sendError(HttpServletResponse.SC_GONE);
                    done = true;
                    break;
                }

                // - redirect (code)
                if (rule.isRedirect() && newtest != null) {
                    // 如果有一个查询字符串，则将其追加到URL中，并且没有重写
                    String urlStringDecoded = urlDecoded.toString();
                    int index = urlStringDecoded.indexOf("?");
                    String rewrittenQueryStringDecoded;
                    if (index == -1) {
                        rewrittenQueryStringDecoded = null;
                    } else {
                        rewrittenQueryStringDecoded = urlStringDecoded.substring(index + 1);
                        urlStringDecoded = urlStringDecoded.substring(0, index);
                    }

                    StringBuffer urlStringEncoded =
                            new StringBuffer(URLEncoder.DEFAULT.encode(urlStringDecoded, uriCharset));
                    if (originalQueryStringEncoded != null &&
                            originalQueryStringEncoded.length() > 0) {
                        if (rewrittenQueryStringDecoded == null) {
                            urlStringEncoded.append('?');
                            urlStringEncoded.append(originalQueryStringEncoded);
                        } else {
                            if (qsa) {
                                // 如果指定了QSA，则追加查询
                                urlStringEncoded.append('?');
                                urlStringEncoded.append(URLEncoder.QUERY.encode(
                                        rewrittenQueryStringDecoded, uriCharset));
                                urlStringEncoded.append('&');
                                urlStringEncoded.append(originalQueryStringEncoded);
                            } else if (index == urlStringEncoded.length() - 1) {
                                // 如果 ? 是最后一个字符串, 删除它, 它的唯一目的是防止重写模块追加查询字符串
                                urlStringEncoded.deleteCharAt(index);
                            } else {
                                urlStringEncoded.append('?');
                                urlStringEncoded.append(URLEncoder.QUERY.encode(
                                        rewrittenQueryStringDecoded, uriCharset));
                            }
                        }
                    } else if (rewrittenQueryStringDecoded != null) {
                        urlStringEncoded.append('?');
                        urlStringEncoded.append(
                                URLEncoder.QUERY.encode(rewrittenQueryStringDecoded, uriCharset));
                    }

                    // 插入上下文, 如果
                    // 1. 这个 valve 和上下文关联
                    // 2. URL以斜杠开始
                    // 3. URL不是绝对的
                    if (context && urlStringEncoded.charAt(0) == '/' &&
                            !UriUtil.hasScheme(urlStringEncoded)) {
                        urlStringEncoded.insert(0, request.getContext().getEncodedPath());
                    }
                    if (rule.isNoescape()) {
                        response.sendRedirect(
                                UDecoder.URLDecode(urlStringEncoded.toString(), uriCharset));
                    } else {
                        response.sendRedirect(urlStringEncoded.toString());
                    }
                    response.setStatus(rule.getRedirectCode());
                    done = true;
                    break;
                }

                // 回复修改

                // - cookie
                if (rule.isCookie() && newtest != null) {
                    Cookie cookie = new Cookie(rule.getCookieName(),
                            rule.getCookieResult());
                    cookie.setDomain(rule.getCookieDomain());
                    cookie.setMaxAge(rule.getCookieLifetime());
                    cookie.setPath(rule.getCookiePath());
                    cookie.setSecure(rule.isCookieSecure());
                    cookie.setHttpOnly(rule.isCookieHttpOnly());
                    response.addCookie(cookie);
                }
                // - env (note: 设置请求属性)
                if (rule.isEnv() && newtest != null) {
                    for (int j = 0; j < rule.getEnvSize(); j++) {
                        request.setAttribute(rule.getEnvName(j), rule.getEnvResult(j));
                    }
                }
                // - content type (note: 这不会强制内容类型, 使用过滤器来完成)
                if (rule.isType() && newtest != null) {
                    request.setContentType(rule.getTypeValue());
                }

                // 控制流处理

                // - chain (如果这一个不匹配，则跳过剩余链接规则)
                if (rule.isChain() && newtest == null) {
                    for (int j = i; j < rules.length; j++) {
                        if (!rules[j].isChain()) {
                            i = j;
                            break;
                        }
                    }
                    continue;
                }
                // - last (停止重写)
                if (rule.isLast() && newtest != null) {
                    break;
                }
                // - next (再次重做)
                if (rule.isNext() && newtest != null) {
                    i = 0;
                    continue;
                }
                // - skip (n rules)
                if (newtest != null) {
                    i += rule.getSkip();
                }

            }

            if (rewritten) {
                if (!done) {
                    // 查看是否需要替换查询字符串
                    String urlStringDecoded = urlDecoded.toString();
                    String queryStringDecoded = null;
                    int queryIndex = urlStringDecoded.indexOf('?');
                    if (queryIndex != -1) {
                        queryStringDecoded = urlStringDecoded.substring(queryIndex+1);
                        urlStringDecoded = urlStringDecoded.substring(0, queryIndex);
                    }
                    // 在重写开始之前保存当前上下文路径
                    String contextPath = null;
                    if (context) {
                        contextPath = request.getContextPath();
                    }
                    // 填充编码的 (i.e. 未解码的) requestURI
                    request.getCoyoteRequest().requestURI().setString(null);
                    CharChunk chunk = request.getCoyoteRequest().requestURI().getCharChunk();
                    chunk.recycle();
                    if (context) {
                        // 这既不解码也不规范化
                        chunk.append(contextPath);
                    }
                    chunk.append(URLEncoder.DEFAULT.encode(urlStringDecoded, uriCharset));
                    request.getCoyoteRequest().requestURI().toChars();
                    // 解码的和规范化的 URI
                    // 重写可能已经对URL进行了非正规化
                    urlStringDecoded = RequestUtil.normalize(urlStringDecoded);
                    request.getCoyoteRequest().decodedURI().setString(null);
                    chunk = request.getCoyoteRequest().decodedURI().getCharChunk();
                    chunk.recycle();
                    if (context) {
                        // 这是解码的和规范化的
                        chunk.append(request.getServletContext().getContextPath());
                    }
                    chunk.append(urlStringDecoded);
                    request.getCoyoteRequest().decodedURI().toChars();
                    // 设置新查询
                    if (queryStringDecoded != null) {
                        request.getCoyoteRequest().queryString().setString(null);
                        chunk = request.getCoyoteRequest().queryString().getCharChunk();
                        chunk.recycle();
                        chunk.append(URLEncoder.QUERY.encode(queryStringDecoded, uriCharset));
                        if (qsa && originalQueryStringEncoded != null &&
                                originalQueryStringEncoded.length() > 0) {
                            chunk.append('&');
                            chunk.append(originalQueryStringEncoded);
                        }
                        if (!chunk.isNull()) {
                            request.getCoyoteRequest().queryString().toChars();
                        }
                    }
                    // 设置新主机
                    if (!host.equals(request.getServerName())) {
                        request.getCoyoteRequest().serverName().setString(null);
                        chunk = request.getCoyoteRequest().serverName().getCharChunk();
                        chunk.recycle();
                        chunk.append(host.toString());
                        request.getCoyoteRequest().serverName().toChars();
                    }
                    request.getMappingData().recycle();
                    // 递归调用整个请求
                    try {
                        Connector connector = request.getConnector();
                        if (!connector.getProtocolHandler().getAdapter().prepare(
                                request.getCoyoteRequest(), response.getCoyoteResponse())) {
                            return;
                        }
                        Pipeline pipeline = connector.getService().getContainer().getPipeline();
                        request.setAsyncSupported(pipeline.isAsyncSupported());
                        pipeline.getFirst().invoke(request, response);
                    } catch (Exception e) {
                        // 这在Calalina适配器实现中并没有发生
                    }
                }
            } else {
                getNext().invoke(request, response);
            }

        } finally {
            invoked.set(null);
        }

    }


    /**
     * @return config base.
     */
    protected File getConfigBase() {
        File configBase =
            new File(System.getProperty("catalina.base"), "conf");
        if (!configBase.exists()) {
            return null;
        } else {
            return configBase;
        }
    }


    /**
     * 查找将存储重写的配置文件的配置路径.
     * 
     * @param resourceName 重写配置文件名
     * @return 完全重写配置路径
     */
    protected String getHostConfigPath(String resourceName) {
        StringBuffer result = new StringBuffer();
        Container container = getContainer();
        Container host = null;
        Container engine = null;
        while (container != null) {
            if (container instanceof Host)
                host = container;
            if (container instanceof Engine)
                engine = container;
            container = container.getParent();
        }
        if (engine != null) {
            result.append(engine.getName()).append('/');
        }
        if (host != null) {
            result.append(host.getName()).append('/');
        }
        result.append(resourceName);
        return result.toString();
    }


    /**
     * 这个工厂方法将解析一个类似的行:
     *
     * Example:
     *  RewriteCond %{REMOTE_HOST}  ^host1.*  [OR]
     *
     * @param line 重写配置中的行
     * @return 条件, 规则或解析的行的映射结果
     */
    public static Object parse(String line) {
        StringTokenizer tokenizer = new StringTokenizer(line);
        if (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (token.equals("RewriteCond")) {
                // RewriteCond TestString CondPattern [Flags]
                RewriteCond condition = new RewriteCond();
                if (tokenizer.countTokens() < 2) {
                    throw new IllegalArgumentException("Invalid line: " + line);
                }
                condition.setTestString(tokenizer.nextToken());
                condition.setCondPattern(tokenizer.nextToken());
                if (tokenizer.hasMoreTokens()) {
                    String flags = tokenizer.nextToken();
                    if (flags.startsWith("[") && flags.endsWith("]")) {
                        flags = flags.substring(1, flags.length() - 1);
                    }
                    StringTokenizer flagsTokenizer = new StringTokenizer(flags, ",");
                    while (flagsTokenizer.hasMoreElements()) {
                        parseCondFlag(line, condition, flagsTokenizer.nextToken());
                    }
                }
                return condition;
            } else if (token.equals("RewriteRule")) {
                // RewriteRule Pattern Substitution [Flags]
                RewriteRule rule = new RewriteRule();
                if (tokenizer.countTokens() < 2) {
                    throw new IllegalArgumentException("Invalid line: " + line);
                }
                rule.setPatternString(tokenizer.nextToken());
                rule.setSubstitutionString(tokenizer.nextToken());
                if (tokenizer.hasMoreTokens()) {
                    String flags = tokenizer.nextToken();
                    if (flags.startsWith("[") && flags.endsWith("]")) {
                        flags = flags.substring(1, flags.length() - 1);
                    }
                    StringTokenizer flagsTokenizer = new StringTokenizer(flags, ",");
                    while (flagsTokenizer.hasMoreElements()) {
                        parseRuleFlag(line, rule, flagsTokenizer.nextToken());
                    }
                }
                return rule;
            } else if (token.equals("RewriteMap")) {
                // RewriteMap name rewriteMapClassName whateverOptionalParameterInWhateverFormat
                if (tokenizer.countTokens() < 2) {
                    throw new IllegalArgumentException("Invalid line: " + line);
                }
                String name = tokenizer.nextToken();
                String rewriteMapClassName = tokenizer.nextToken();
                RewriteMap map = null;
                try {
                    map = (RewriteMap) (Class.forName(
                            rewriteMapClassName).getConstructor().newInstance());
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid map className: " + line);
                }
                if (tokenizer.hasMoreTokens()) {
                    map.setParameters(tokenizer.nextToken());
                }
                Object[] result = new Object[2];
                result[0] = name;
                result[1] = map;
                return result;
            } else if (token.startsWith("#")) {
                // it's a comment, ignore it
            } else {
                throw new IllegalArgumentException("Invalid line: " + line);
            }
        }
        return null;
    }


    /**
     * RewriteCond 标志的解析器.
     * 
     * @param line 正在解析的配置行
     * @param condition 当前条件
     * @param flag The flag
     */
    protected static void parseCondFlag(String line, RewriteCond condition, String flag) {
        if (flag.equals("NC") || flag.equals("nocase")) {
            condition.setNocase(true);
        } else if (flag.equals("OR") || flag.equals("ornext")) {
            condition.setOrnext(true);
        } else {
            throw new IllegalArgumentException("Invalid flag in: " + line + " flags: " + flag);
        }
    }


    /**
     * RewriteRule 标志的解析器.
     * @param line 正在解析的配置行
     * @param rule 当前规则
     * @param flag The flag
     */
    protected static void parseRuleFlag(String line, RewriteRule rule, String flag) {
        if (flag.equals("B")) {
            rule.setEscapeBackReferences(true);
        } else if (flag.equals("chain") || flag.equals("C")) {
            rule.setChain(true);
        } else if (flag.startsWith("cookie=") || flag.startsWith("CO=")) {
            rule.setCookie(true);
            if (flag.startsWith("cookie")) {
                flag = flag.substring("cookie=".length());
            } else if (flag.startsWith("CO=")) {
                flag = flag.substring("CO=".length());
            }
            StringTokenizer tokenizer = new StringTokenizer(flag, ":");
            if (tokenizer.countTokens() < 2) {
                throw new IllegalArgumentException("Invalid flag in: " + line);
            }
            rule.setCookieName(tokenizer.nextToken());
            rule.setCookieValue(tokenizer.nextToken());
            if (tokenizer.hasMoreTokens()) {
                rule.setCookieDomain(tokenizer.nextToken());
            }
            if (tokenizer.hasMoreTokens()) {
                try {
                    rule.setCookieLifetime(Integer.parseInt(tokenizer.nextToken()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid flag in: " + line, e);
                }
            }
            if (tokenizer.hasMoreTokens()) {
                rule.setCookiePath(tokenizer.nextToken());
            }
            if (tokenizer.hasMoreTokens()) {
                rule.setCookieSecure(Boolean.parseBoolean(tokenizer.nextToken()));
            }
            if (tokenizer.hasMoreTokens()) {
                rule.setCookieHttpOnly(Boolean.parseBoolean(tokenizer.nextToken()));
            }
        } else if (flag.startsWith("env=") || flag.startsWith("E=")) {
            rule.setEnv(true);
            if (flag.startsWith("env=")) {
                flag = flag.substring("env=".length());
            } else if (flag.startsWith("E=")) {
                flag = flag.substring("E=".length());
            }
            int pos = flag.indexOf(':');
            if (pos == -1 || (pos + 1) == flag.length()) {
                throw new IllegalArgumentException("Invalid flag in: " + line);
            }
            rule.addEnvName(flag.substring(0, pos));
            rule.addEnvValue(flag.substring(pos + 1));
        } else if (flag.startsWith("forbidden") || flag.startsWith("F")) {
            rule.setForbidden(true);
        } else if (flag.startsWith("gone") || flag.startsWith("G")) {
            rule.setGone(true);
        } else if (flag.startsWith("host") || flag.startsWith("H")) {
            rule.setHost(true);
        } else if (flag.startsWith("last") || flag.startsWith("L")) {
            rule.setLast(true);
        } else if (flag.startsWith("nocase") || flag.startsWith("NC")) {
            rule.setNocase(true);
        } else if (flag.startsWith("noescape") || flag.startsWith("NE")) {
            rule.setNoescape(true);
        } else if (flag.startsWith("next") || flag.startsWith("N")) {
            rule.setNext(true);
        // Note: 因为Tomcat没有代理能力, 所以不支持代理
        } else if (flag.startsWith("qsappend") || flag.startsWith("QSA")) {
            rule.setQsappend(true);
        } else if (flag.startsWith("redirect") || flag.startsWith("R")) {
            rule.setRedirect(true);
            int redirectCode = HttpServletResponse.SC_FOUND;
            if (flag.startsWith("redirect=") || flag.startsWith("R=")) {
                if (flag.startsWith("redirect=")) {
                    flag = flag.substring("redirect=".length());
                } else if (flag.startsWith("R=")) {
                    flag = flag.substring("R=".length());
                }
                switch(flag) {
                    case "temp":
                        redirectCode = HttpServletResponse.SC_FOUND;
                        break;
                    case "permanent":
                        redirectCode = HttpServletResponse.SC_MOVED_PERMANENTLY;
                        break;
                    case "seeother":
                        redirectCode = HttpServletResponse.SC_SEE_OTHER;
                        break;
                    default:
                        redirectCode = Integer.parseInt(flag);
                        break;
                }
            }
            rule.setRedirectCode(redirectCode);
        } else if (flag.startsWith("skip") || flag.startsWith("S")) {
            if (flag.startsWith("skip=")) {
                flag = flag.substring("skip=".length());
            } else if (flag.startsWith("S=")) {
                flag = flag.substring("S=".length());
            }
            rule.setSkip(Integer.parseInt(flag));
        } else if (flag.startsWith("type") || flag.startsWith("T")) {
            if (flag.startsWith("type=")) {
                flag = flag.substring("type=".length());
            } else if (flag.startsWith("T=")) {
                flag = flag.substring("T=".length());
            }
            rule.setType(true);
            rule.setTypeValue(flag);
        } else {
            throw new IllegalArgumentException("Invalid flag in: " + line + " flag: " + flag);
        }
    }
}
