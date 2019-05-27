package org.apache.catalina.valves.rewrite;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RewriteRule {

    protected RewriteCond[] conditions = new RewriteCond[0];

    protected ThreadLocal<Pattern> pattern = new ThreadLocal<>();
    protected Substitution substitution = null;

    protected String patternString = null;
    protected String substitutionString = null;

    public void parse(Map<String, RewriteMap> maps) {
        // Parse the substitution
        if (!"-".equals(substitutionString)) {
            substitution = new Substitution();
            substitution.setSub(substitutionString);
            substitution.parse(maps);
            substitution.setEscapeBackReferences(isEscapeBackReferences());
        }
        // Parse the pattern
        int flags = 0;
        if (isNocase()) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        Pattern.compile(patternString, flags);
        // Parse conditions
        for (int i = 0; i < conditions.length; i++) {
            conditions[i].parse(maps);
        }
        // 具有替换值的解析标志
        if (isEnv()) {
            for (int i = 0; i < envValue.size(); i++) {
                Substitution newEnvSubstitution = new Substitution();
                newEnvSubstitution.setSub(envValue.get(i));
                newEnvSubstitution.parse(maps);
                envSubstitution.add(newEnvSubstitution);
                envResult.add(new ThreadLocal<String>());
            }
        }
        if (isCookie()) {
            cookieSubstitution = new Substitution();
            cookieSubstitution.setSub(cookieValue);
            cookieSubstitution.parse(maps);
        }
    }

    public void addCondition(RewriteCond condition) {
        RewriteCond[] conditions = new RewriteCond[this.conditions.length + 1];
        for (int i = 0; i < this.conditions.length; i++) {
            conditions[i] = this.conditions[i];
        }
        conditions[this.conditions.length] = condition;
        this.conditions = conditions;
    }

    /**
     * 基于上下文的规则评估
     * @param url The char sequence
     * @param resolver 属性分析器
     * @return <code>null</code>如果没有重写发生
     */
    public CharSequence evaluate(CharSequence url, Resolver resolver) {
        Pattern pattern = this.pattern.get();
        if (pattern == null) {
            // 解析模式
            int flags = 0;
            if (isNocase()) {
                flags |= Pattern.CASE_INSENSITIVE;
            }
            pattern = Pattern.compile(patternString, flags);
            this.pattern.set(pattern);
        }
        Matcher matcher = pattern.matcher(url);
        if (!matcher.matches()) {
            // 评估完成
            return null;
        }
        // 评估条件
        boolean done = false;
        boolean rewrite = true;
        Matcher lastMatcher = null;
        int pos = 0;
        while (!done) {
            if (pos < conditions.length) {
                rewrite = conditions[pos].evaluate(matcher, lastMatcher, resolver);
                if (rewrite) {
                    Matcher lastMatcher2 = conditions[pos].getMatcher();
                    if (lastMatcher2 != null) {
                        lastMatcher = lastMatcher2;
                    }
                    while (pos < conditions.length && conditions[pos].isOrnext()) {
                        pos++;
                    }
                } else if (!conditions[pos].isOrnext()) {
                    done = true;
                }
                pos++;
            } else {
                done = true;
            }
        }
        // 使用替换来重写URL
        if (rewrite) {
            if (isEnv()) {
                for (int i = 0; i < envSubstitution.size(); i++) {
                    envResult.get(i).set(envSubstitution.get(i).evaluate(matcher, lastMatcher, resolver));
                }
            }
            if (isCookie()) {
                cookieResult.set(cookieSubstitution.evaluate(matcher, lastMatcher, resolver));
            }
            if (substitution != null) {
                return substitution.evaluate(matcher, lastMatcher, resolver);
            } else {
                return url;
            }
        } else {
            return null;
        }
    }


    @Override
    public String toString() {
        // FIXME: Add flags if possible
        return "RewriteRule " + patternString + " " + substitutionString;
    }


    private boolean escapeBackReferences = false;

    /**
     *  是否将下一个规则和当前规则链接起来 (它本身可以用下列规则链接, etc.).
     *  这有以下作用: 如果规则匹配, 然后处理继续像往常一样, i.e., 这个标志不起作用. 如果规则不匹配, 然后跳过所有链接的规则.
     *  例如, 使用它删除每个目录规则集内的一部分 ``.www'', 当您允许外部重定向发生时 ( ``.www'' 不应该出现的地方!).
     */
    protected boolean chain = false;

    /**
     *  在客户端浏览器上设置cookie. cookie的名称由NAME指定, 值是 VAL.
     *  domain字段是cookie的域名, 例如 '.apache.org',可选的 lifetime是cookie的有效期(分钟), 可选的 path 是cookie的路径
     */
    protected boolean cookie = false;
    protected String cookieName = null;
    protected String cookieValue = null;
    protected String cookieDomain = null;
    protected int cookieLifetime = -1;
    protected String cookiePath = null;
    protected boolean cookieSecure = false;
    protected boolean cookieHttpOnly = false;
    protected Substitution cookieSubstitution = null;
    protected ThreadLocal<String> cookieResult = new ThreadLocal<>();

    /**
     *  强制请求属性 VAR 设置为 VAL, VAL 可以包含返回引用的正则表达式 $N 和 %N, 它们将扩大. 允许多个 env 标志.
     */
    protected boolean env = false;
    protected ArrayList<String> envName = new ArrayList<>();
    protected ArrayList<String> envValue = new ArrayList<>();
    protected ArrayList<Substitution> envSubstitution = new ArrayList<>();
    protected ArrayList<ThreadLocal<String>> envResult = new ArrayList<>();

    /**
     *  强迫当前的URL被禁止, i.e., 它立即返回403的HTTP响应(FORBIDDEN).
     *  使用这个标志连接适当的 RewriteCond, 有条件地阻塞一些URL.
     */
    protected boolean forbidden = false;

    /**
     *  强迫当前的URL消失, i.e., 它立即返回410的HTTP响应 (GONE). 使用此标志来标记不再存在的页面.
     */
    protected boolean gone = false;

    /**
     * Host. 意味着这个规则及其相关的条件将适用于主机, 允许主机重写 (ex: 重定向内部的 *.foo.com 到 bar.foo.com).
     */
    protected boolean host = false;

    /**
     *  停止这里的重写过程，不要再应用任何重写规则. 这个对应于 Perl语言的 last命令或 C语言的 break 命令.
     *  使用此标志防止当前重写的URL进一步被后面的规则重写. 例如, 使用它重写根路径URL ('/') 到 '/e/www/'.
     */
    protected boolean last = false;

    /**
     *  重新运行重写过程 (以第一次的重写规则重新开始).
     *  这里匹配的URL不再是原来的URL，而是最后一个重写规则的URL. 这个对应于 Perl语言的 next命令或 C语言的 continue 命令.
     *  使用此标志重新启动重写过程, i.e., 立即进入循环的顶部. 但是小心不要创建一个无限循环!
     */
    protected boolean next = false;

    /**
     *  当模式与当前URL匹配时, 不区分大小写.
     */
    protected boolean nocase = false;

    /**
     *  保存从通常的URI转义规则到重写的结果的 mod_rewrite.
     *  通常, 特殊字符 (例如 '%', '$', ';', 等等) 将被转义到它们的六进制码 ('%25', '%24', and '%3B', 分别地);
     *  此标志防止此操作完成. 这允许在输出中出现百分比符号, 因为在  RewriteRule /foo/(.*) /bar?arg=P1\%3d$1 [R,NE] 中, 
     *  将会把 '/foo/zed' 转换为安全请求 '/bar?arg=P1=zed'.
     */
    protected boolean noescape = false;

    /**
     *  如果当前请求是内部子请求, 则此标志强制重写引擎跳过重写规则.
     *  例如, 在Apache内部发生子请求, 当 mod_include 试图查找有关可能的目录默认文件(index.xxx)的信息时.
     *  在子请求上, 它并不总是有用的, 甚至有时会导致失败, 如果应用了完整的规则集. 使用此标志排除某些规则.
     *  为你的决定使用下面的规则: 每当用CGI脚本给一些URL加上前缀, 以强制它们由CGI脚本处理时, 遇到子请求问题的可能性很高.
     *  在这些情况下, 使用这个标志.
     */
    protected boolean nosubreq = false;

    /**
     *  Note: 不代理
     */

    /**
     * Note: No passthrough
     */

    /**
     *  此标志强制重写引擎将替换字符串中的查询字符串部分追加到现有字符串中，而不是替换它.
     *  当希望通过重写规则向查询字符串中添加更多数据时使用.
     */
    protected boolean qsappend = false;

    /**
     *  使用http://thishost[:thisport]/ (这使得新URL成为URI)替换前缀强制外部重定向.
     *  如果没有给出代码, 将使用302 HTTP响应 (FOUND, previously MOVED TEMPORARILY).
     *  如果希望在300～399范围内使用其他响应代码，只需将它们指定为数字或使用下列符号名称中的一个:
     *  temp (default), permanent, seeother.
     *  使用它作为规则, 它应该规范化URL并将其返回给客户端, e.g., 翻译 ``/~'' 为 ``/u/'', 或总是追加一个斜杠 /u/user, 等.
     *  Note: 使用此标志时, 确保替换字段是一个有效的URL! 如果不是, 将重定向到无效位置!
     *  并且这个标志只使用 http://thishost[:thisport]/ 给URL添加前缀, 继续重写.
     *  通常，还希望立即停止重写, 并立即重定向. 停止重写, 还需要提供 'L' 标志.
     */
    protected boolean redirect = false;
    protected int redirectCode = 0;

    /**
     *  当前规则匹配时，此标志强制重写引擎按顺序跳过下一个num规则.
     *  使用这个让 pseudo if-then-else 构建: then分支的最后一个规则变为skip=N, N 是else 分支的规则的数量.
     *  (和 'chain|C' 标志不一样!)
     */
    protected int skip = 0;

    /**
     *  将目标文件的MIME类型强制为MIME类型.
     *  例如, 这可以用于根据某些条件设置内容类型.
     *  例如, 下面的代码段允许 .php 文件通过mod_php显示, 如果它们是 .phps 后缀名:
     *  RewriteRule ^(.+\.php)s$ $1 [T=application/x-httpd-php-source]
     */
    protected boolean type = false;
    protected String typeValue = null;

    public boolean isEscapeBackReferences() {
        return escapeBackReferences;
    }
    public void setEscapeBackReferences(boolean escapeBackReferences) {
        this.escapeBackReferences = escapeBackReferences;
    }
    public boolean isChain() {
        return chain;
    }
    public void setChain(boolean chain) {
        this.chain = chain;
    }
    public RewriteCond[] getConditions() {
        return conditions;
    }
    public void setConditions(RewriteCond[] conditions) {
        this.conditions = conditions;
    }
    public boolean isCookie() {
        return cookie;
    }
    public void setCookie(boolean cookie) {
        this.cookie = cookie;
    }
    public String getCookieName() {
        return cookieName;
    }
    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }
    public String getCookieValue() {
        return cookieValue;
    }
    public void setCookieValue(String cookieValue) {
        this.cookieValue = cookieValue;
    }
    public String getCookieResult() {
        return cookieResult.get();
    }
    public boolean isEnv() {
        return env;
    }
    public int getEnvSize() {
        return envName.size();
    }
    public void setEnv(boolean env) {
        this.env = env;
    }
    public String getEnvName(int i) {
        return envName.get(i);
    }
    public void addEnvName(String envName) {
        this.envName.add(envName);
    }
    public String getEnvValue(int i) {
        return envValue.get(i);
    }
    public void addEnvValue(String envValue) {
        this.envValue.add(envValue);
    }
    public String getEnvResult(int i) {
        return envResult.get(i).get();
    }
    public boolean isForbidden() {
        return forbidden;
    }
    public void setForbidden(boolean forbidden) {
        this.forbidden = forbidden;
    }
    public boolean isGone() {
        return gone;
    }
    public void setGone(boolean gone) {
        this.gone = gone;
    }
    public boolean isLast() {
        return last;
    }
    public void setLast(boolean last) {
        this.last = last;
    }
    public boolean isNext() {
        return next;
    }
    public void setNext(boolean next) {
        this.next = next;
    }
    public boolean isNocase() {
        return nocase;
    }
    public void setNocase(boolean nocase) {
        this.nocase = nocase;
    }
    public boolean isNoescape() {
        return noescape;
    }
    public void setNoescape(boolean noescape) {
        this.noescape = noescape;
    }
    public boolean isNosubreq() {
        return nosubreq;
    }
    public void setNosubreq(boolean nosubreq) {
        this.nosubreq = nosubreq;
    }
    public boolean isQsappend() {
        return qsappend;
    }
    public void setQsappend(boolean qsappend) {
        this.qsappend = qsappend;
    }
    public boolean isRedirect() {
        return redirect;
    }
    public void setRedirect(boolean redirect) {
        this.redirect = redirect;
    }
    public int getRedirectCode() {
        return redirectCode;
    }
    public void setRedirectCode(int redirectCode) {
        this.redirectCode = redirectCode;
    }
    public int getSkip() {
        return skip;
    }
    public void setSkip(int skip) {
        this.skip = skip;
    }
    public Substitution getSubstitution() {
        return substitution;
    }
    public void setSubstitution(Substitution substitution) {
        this.substitution = substitution;
    }
    public boolean isType() {
        return type;
    }
    public void setType(boolean type) {
        this.type = type;
    }
    public String getTypeValue() {
        return typeValue;
    }
    public void setTypeValue(String typeValue) {
        this.typeValue = typeValue;
    }

    public String getPatternString() {
        return patternString;
    }

    public void setPatternString(String patternString) {
        this.patternString = patternString;
    }

    public String getSubstitutionString() {
        return substitutionString;
    }

    public void setSubstitutionString(String substitutionString) {
        this.substitutionString = substitutionString;
    }

    public boolean isHost() {
        return host;
    }

    public void setHost(boolean host) {
        this.host = host;
    }

    public String getCookieDomain() {
        return cookieDomain;
    }

    public void setCookieDomain(String cookieDomain) {
        this.cookieDomain = cookieDomain;
    }

    public int getCookieLifetime() {
        return cookieLifetime;
    }

    public void setCookieLifetime(int cookieLifetime) {
        this.cookieLifetime = cookieLifetime;
    }

    public String getCookiePath() {
        return cookiePath;
    }

    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public boolean isCookieHttpOnly() {
        return cookieHttpOnly;
    }

    public void setCookieHttpOnly(boolean cookieHttpOnly) {
        this.cookieHttpOnly = cookieHttpOnly;
    }
}
