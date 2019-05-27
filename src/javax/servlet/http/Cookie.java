package javax.servlet.http;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.BitSet;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * 创建一个cookie, servlet向Web浏览器发送的少量信息, 由浏览器保存, 然后再送回服务器. 
 * 一个cookie的值可以唯一地标识客户机, 因此cookie通常用于会话管理.
 * <p>
 * cookie有一个名称、一个值和可选属性，如注释、路径和域限定符、最大年龄和版本号.
 * 有些Web浏览器在处理可选属性时有bug, 所以要提高你的servlet的互操作性.
 * <p>
 * servlet使用{@link HttpServletResponse#addCookie}方法将cookie发送到浏览器, 它将字段添加到HTTP响应头，以便将cookie发送到浏览器，每次一个.
 * 浏览器将为每个Web服务器支持20个cookie，总共300个cookie，并可能将cookie大小限制为4 KB.
 * <p>
 * 通过向HTTP请求头添加字段，浏览器将cookie返回到servlet. 可以从请求中检索cookie，使用{@link HttpServletRequest#getCookies}方法.
 * 几个cookie可能具有相同的名称，但路径属性不同.
 * <p>
 * Cookie影响使用它们的Web页面的缓存. HTTP 1.0不缓存使用此类创建的cookie的页面. 此类不支持HTTP 1.1定义的缓存控制.
 * <p>
 * 这个类支持 RFC 2109和RFC 6265规范. 默认情况下, 使用RFC 6265创建Cookie.
 */
public class Cookie implements Cloneable, Serializable {

    private static final CookieNameValidator validation;
    static {
        boolean strictNaming;
        String prop = System.getProperty("org.apache.tomcat.util.http.ServerCookie.STRICT_NAMING");
        if (prop != null) {
            strictNaming = Boolean.parseBoolean(prop);
        } else {
            strictNaming = Boolean.getBoolean("org.apache.catalina.STRICT_SERVLET_COMPLIANCE");
        }

        if (strictNaming) {
            validation = new RFC2109Validator();
        }
        else {
            validation = new RFC6265Validator();
        }
    }

    private static final long serialVersionUID = 1L;

    private final String name;
    private String value;

    private int version = 0; // ;Version=1 ... means RFC 2109 style

    //
    // 标题cookie字段中编码的属性.
    //
    private String comment; // ;Comment=VALUE ... 描述cookie的用法
    private String domain; // ;Domain=VALUE ... 查看cookie的域名
    private int maxAge = -1; // ;Max-Age=VALUE ... cookies auto-expire
    private String path; // ;Path=VALUE ... 查看cookie的URL
    private boolean secure; // ;Secure ... e.g. 使用SSL
    private boolean httpOnly; // 不在cookie规范中，但受浏览器支持

    /**
     * <p>
     * 名称必须符合RFC 2109. 它只能包含ASCII 字母数字字符，不能包含逗号, 分号, 或空格，或以$ 字符开头.
     * cookie的名称在创建后不能更改.
     * <p>
     * 该值可以是服务器选择发送的任何值. 它的值可能只对服务器感兴趣. cookie的值可能在<code>setValue</code>方法之后修改.
     * <p>
     * 默认情况下，cookie是根据Netscape cookie规范创建的. 可以使用<code>setVersion</code>方法修改版本.
     *
     * @param name cookie的名称
     * @param value cookie的值
     * @throws IllegalArgumentException 如果cookie名称包含非法字符（例如，逗号、空格或分号），或者它是为cookie协议保留的令牌之一
     */
    public Cookie(String name, String value) {
        validation.validate(name);
        this.name = name;
        this.value = value;
    }

    /**
     * 指定描述cookie用途的注释. 如果浏览器向用户展示cookie，则该注释非常有用. Netscape版本0 cookie不支持注释.
     *
     * @param purpose  要显示给用户的注释
     */
    public void setComment(String purpose) {
        comment = purpose;
    }

    /**
     * 返回描述此cookie用途的注释, 或者<code>null</code>.
     *
     * @return 注释, 或者<code>null</code>
     */
    public String getComment() {
        return comment;
    }

    /**
     * 指定应该提供此cookie的域.
     * <p>
     * 域名的格式是通过RFC 2109指定的.
     * 域名以一个点开始(<code>.foo.com</code>) 意味着cookie在指定的域名系统（DNS）区域的服务器中是可见的
     * (例如, <code>www.foo.com</code>, 但<code>a.b.foo.com</code>不是). 默认情况下，cookie只返回发送它们的服务器.
     *
     * @param pattern 此cookie可见的域名; 格式参照 RFC 2109
     */
    public void setDomain(String pattern) {
        domain = pattern.toLowerCase(Locale.ENGLISH); // IE 据说需要这个
    }

    /**
     * 返回此cookie的域名. 域名的格式是通过RFC 2109指定的.
     *
     * @return 域名
     */
    public String getDomain() {
        return domain;
    }

    /**
     * 设置cookie的最大年龄为秒.
     * <p>
     * 正值表明cookie在许多秒过去后将过期. 注意值是<i>maximum</i>年龄，当cookie过期时, 不是cookie的当前年龄.
     * <p>
     * 负值意味着cookie不会持久存储，当Web浏览器退出时将被删除. 零值会导致cookie被删除.
     *
     * @param expiry  cookie的最大年龄，单位为秒; 负值意味着cookie不会持久存储; 零值会导致cookie被删除.
     */
    public void setMaxAge(int expiry) {
        maxAge = expiry;
    }

    /**
     * 返回cookie的最大年龄为秒, 单位为秒, 默认情况下, <code>-1</code>意味着cookie不会持久存储.
     */
    public int getMaxAge() {
        return maxAge;
    }

    /**
     * 指定客户端返回cookie的路径.
     * <p>
     * cookie可以在您指定的目录中的所有页面中可见, 包括这个目录的子目录的所有页面.
     * cookie的路径必须包含设置cookie的servlet, 例如, <i>/catalog</i>, 在服务器<i>/catalog</i>目录下的所有目录都可见cookie.
     * <p>
     * 咨询RFC 2109查看关于设置cookie的路径名称的更多信息.
     *
     * @param uri 路径
     */
    public void setPath(String uri) {
        path = uri;
    }

    /**
     * 返回浏览器返回的cookie的服务器上的路径. Cookie在服务器上对所有子路径可见.
     *
     * @return 包含servlet名称的路径, 例如, <i>/catalog</i>
     */
    public String getPath() {
        return path;
    }

    /**
     * 指示浏览器是否只使用安全协议发送cookie, 例如HTTPS 或 SSL.
     * <p>
     * 默认为<code>false</code>.
     *
     * @param flag 如果是<code>true</code>, 只有在使用安全协议时才将cookie从浏览器发送到服务器;
     * 				如果是<code>false</code>, 在任何协议下发送
     */
    public void setSecure(boolean flag) {
        secure = flag;
    }

    /**
     * 返回<code>true</code>，如果浏览器只在安全协议下发送cookie, 或者<code>false</code>如果浏览器可以使用任何协议发送cookie.
     *
     * @return <code>true</code>如果浏览器使用安全协议; 否则返回<code>true</code>
     */
    public boolean getSecure() {
        return secure;
    }

    /**
     * 返回cookie的名称. 创建后无法更改名称.
     *
     * @return 指定的cookie的名称
     */
    public String getName() {
        return name;
    }

    /**
     * 在cookie创建后将一个新值赋给cookie. 如果使用二进制值, 可以使用BASE64 编码.
     * <p>
     * 在Version 0 cookie中, 值不能包含空格，括号，括号、等号、逗号、双引号、斜杠，问号，冒号和分号.
     * 在所有浏览器上，空值可能行为方式都不一样.
     *
     * @param newValue 新值
     */
    public void setValue(String newValue) {
        value = newValue;
    }

    /**
     * 返回cookie的值.
     *
     * @return cookie的值
     */
    public String getValue() {
        return value;
    }

    /**
     * 返回此cookie遵守的协议的版本. Version 1遵守RFC 2109, version 0 遵从Netscape草拟的原始cookie规范.
     * 浏览器提供的cookie使用并识别浏览器的cookie版本.
     *
     * @return 0 如果cookie符合最初的Netscape规范;
     *         1 如果cookie符合RFC 2109
     */
    public int getVersion() {
        return version;
    }

    /**
     * 设置cookie符合的cookie协议的版本.
     * Version 0 符合最初的Netscape规范.
     * Version 1 符合 RFC 2109.
     * <p>
     * RFC 2109还是有些新的, 将版本1视为实验性; 不要在生产环境使用它.
     *
     * @param v 0 如果cookie符合最初的Netscape规范;
     *          1 如果cookie符合RFC 2109
     */
    public void setVersion(int v) {
        version = v;
    }

    /**
     * 覆盖标准的<code>java.lang.Object.clone</code>方法来返回这个cookie的备份.
     */
    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 设置该cookie是否隐藏在客户端脚本上的标志.
     *
     * @param httpOnly  标志的新值
     */
    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
    }

    /**
     * 获取该cookie是否会从客户端脚本中隐藏的标志.
     *
     * @return  <code>true</code>如果cookie隐藏在脚本中, 否则<code>false</code>
     */
    public boolean isHttpOnly() {
        return httpOnly;
    }
}


class CookieNameValidator {
    private static final String LSTRING_FILE = "javax.servlet.http.LocalStrings";
    protected static final ResourceBundle lStrings = ResourceBundle.getBundle(LSTRING_FILE);

    protected final BitSet allowed;

    protected CookieNameValidator(String separators) {
        allowed = new BitSet(128);
        allowed.set(0x20, 0x7f); // any CHAR except CTLs or separators
        for (int i = 0; i < separators.length(); i++) {
            char ch = separators.charAt(i);
            allowed.clear(ch);
        }
    }

    void validate(String name) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException(lStrings.getString("err.cookie_name_blank"));
        }
        if (!isToken(name)) {
            String errMsg = lStrings.getString("err.cookie_name_is_token");
            throw new IllegalArgumentException(MessageFormat.format(errMsg, name));
        }
    }

    private boolean isToken(String possibleToken) {
        int len = possibleToken.length();

        for (int i = 0; i < len; i++) {
            char c = possibleToken.charAt(i);
            if (!allowed.get(c)) {
                return false;
            }
        }
        return true;
    }
}

class RFC6265Validator extends CookieNameValidator {
    private static final String RFC2616_SEPARATORS = "()<>@,;:\\\"/[]?={} \t";

    RFC6265Validator() {
        super(RFC2616_SEPARATORS);
    }
}

class RFC2109Validator extends RFC6265Validator {
    RFC2109Validator() {
        // special treatment to allow for FWD_SLASH_IS_SEPARATOR property
        boolean allowSlash;
        String prop = System.getProperty("org.apache.tomcat.util.http.ServerCookie.FWD_SLASH_IS_SEPARATOR");
        if (prop != null) {
            allowSlash = !Boolean.parseBoolean(prop);
        } else {
            allowSlash = !Boolean.getBoolean("org.apache.catalina.STRICT_SERVLET_COMPLIANCE");
        }
        if (allowSlash) {
            allowed.set('/');
        }
    }

    @Override
    void validate(String name) {
        super.validate(name);
        if (name.charAt(0) == '$') {
            String errMsg = lStrings.getString("err.cookie_name_is_token");
            throw new IllegalArgumentException(MessageFormat.format(errMsg, name));
        }
    }
}
