package org.apache.catalina.servlets;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.regex.Pattern;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.catalina.util.IOTools;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 *  Web应用的CGI-invoking servlet, 用于执行符合公共网关接口（CGI）规范的脚本，并在调用此servlet的路径信息中指定.
 *
 * <p>
 * <i>Note: 此代码编译，甚至适用于简单的CGI案例. 没有进行详尽的测试. 请考虑它的质量. 感谢作者的反馈(见下文).</i>
 * </p>
 * <p>
 *
 * <b>Example</b>:<br>
 * 如果这个servlet实例被映射为(使用<code>&lt;web-app&gt;/WEB-INF/web.xml</code>) :
 * </p>
 * <p>
 * <code>
 * &lt;web-app&gt;/cgi-bin/*
 * </code>
 * </p>
 * <p>
 * 然后，以下请求:
 * </p>
 * <p>
 * <code>
 * http://localhost:8080/&lt;web-app&gt;/cgi-bin/dir1/script/pathinfo1
 * </code>
 * </p>
 * <p>
 * 将执行脚本
 * </p>
 * <p>
 * <code>
 * &lt;web-app-root&gt;/WEB-INF/cgi/dir1/script
 * </code>
 * </p>
 * <p>
 * 并将脚本的<code>PATH_INFO</code>设置为<code>/pathinfo1</code>.
 * </p>
 * <p>
 * 推荐:  你所有的CGI脚本都放在<code>&lt;webapp&gt;/WEB-INF/cgi</code>下面.
 * 这将确保您不会意外地将CGI脚本代码暴露出去，你的CGI将干净安置在WEB-INF文件夹中.
 * </p>
 * <p>
 * 上面提到的默认CGI位置. 可以灵活地把CGI放到任何你想的地方，但是:
 * </p>
 * <p>
 *   CGI搜索路径将开始
 *   webAppRootDir + File.separator + cgiPathPrefix
 *   (或者webAppRootDir，如果cgiPathPrefix是null).
 * </p>
 * <p>
 *   cgiPathPrefix 通过设置cgiPathPrefix初始化参数定义
 * </p>
 *
 * <p>
 *
 * <B>CGI 规范</B>:<br> 来自
 * <a href="http://cgi-spec.golux.com">http://cgi-spec.golux.com</a>.
 * A work-in-progress & expired Internet Draft. 
 * 目前不存在描述CGI规范的RFC. 此servlet的行为与上面引用的规范不同, 这里有文件记录, 一个bug,
 * 或规范从Best Community Practice (BCP)引用不同的实例.
 * </p>
 * <p>
 *
 * <b>Canonical metavariables</b>:<br>
 * CGI规范定义了以下元变量:
 * <br>
 * [excerpt from CGI specification]
 * <PRE>
 *  AUTH_TYPE
 *  CONTENT_LENGTH
 *  CONTENT_TYPE
 *  GATEWAY_INTERFACE
 *  PATH_INFO
 *  PATH_TRANSLATED
 *  QUERY_STRING
 *  REMOTE_ADDR
 *  REMOTE_HOST
 *  REMOTE_IDENT
 *  REMOTE_USER
 *  REQUEST_METHOD
 *  SCRIPT_NAME
 *  SERVER_NAME
 *  SERVER_PORT
 *  SERVER_PROTOCOL
 *  SERVER_SOFTWARE
 * </PRE>
 * <p>
 * 以协议名称开始的元变量名称(<EM>e.g.</EM>, "HTTP_ACCEPT")在请求标头字段的描述中也是规范的. 
 * 这些字段的数量和含义可能与本规范无关.(参见第 6.1.5 [CGI 规范].)
 * </p>
 * [end excerpt]
 *
 * <h2>实现注意事项</h2>
 * <p>
 *
 * <b>标准的输入处理</b>: 如果脚本接受标准输入,
 * 然后客户端必须在一定的超时时间内开始发送输入, 否则servlet将假定没有输入，并继续运行脚本.
 * 脚本的标准输入将被关闭，客户端的任何其他输入的处理都是未定义的. 很有可能会被忽略. 
 * 如果这种行为变得不受欢迎, 然后这个servlet需要增强处理催生了进程的stdin，stdout和stderr线程(不应该太难).
 * <br>
 * 如果你发现你的CGI脚本正在超时接收输入, 可以设置init参数<code></code> 你的webapps的CGI处理servlet是.
 * </p>
 * <p>
 *
 * <b>元变量值</b>: 根据CGI, 实现类可以选择以特定实现类的方式来表示空值或丢失值，但必须定义这种方式.
 * 这个实现总是选择所需的元变量定义, 但是设置值为"" 为所有的元变量的值是null或undefined.
 * PATH_TRANSLATED 是这条规则的唯一例外, 按照CGI规范.
 * </p>
 * <p>
 *
 * <b>NPH -- 非解析报头实现</b>:  这种实现不支持CGI NPH的概念, 其中服务器确保提供给脚本的数据是由客户端提供，而不是服务器.
 * </p>
 * <p>
 * servlet容器（包括Tomcat）的功能是专门用来解析和更改CGI特定变量的, 这样使NPH功能难以支撑.
 * </p>
 * <p>
 * CGI规范规定，兼容的服务器可以支持NPH输出.
 * 它没有规定服务器必须支持NPH输出是完全兼容的. 因此，此实现类保持无条件遵守规范,虽然NPH支持是不存在的.
 * </p>
 * <p>
 * </p>
 * <h3>TODO:</h3>
 * <ul>
 * <li> Support for setting headers (for example, Location headers don't work)
 * <li> Support for collapsing multiple header lines (per RFC 2616)
 * <li> Ensure handling of POST method does not interfere with 2.3 Filters
 * <li> Refactor some debug code out of core
 * <li> Ensure header handling preserves encoding
 * <li> Possibly rewrite CGIRunner.run()?
 * <li> Possibly refactor CGIRunner and CGIEnvironment as non-inner classes?
 * <li> Document handling of cgi stdin when there is no stdin
 * <li> Revisit IOException handling in CGIRunner.run()
 * <li> Better documentation
 * <li> Confirm use of ServletInputStream.available() in CGIRunner.run() is
 *      not needed
 * <li> [add more to this TODO list]
 * </ul>
 */
public final class CGIServlet extends HttpServlet {

    private static final Log log = LogFactory.getLog(CGIServlet.class);
    private static final StringManager sm = StringManager.getManager(CGIServlet.class);

    /* some vars below copied from Craig R. McClanahan's InvokerServlet */

    private static final long serialVersionUID = 1L;

    /**
     *  CGI搜索路径：
     *    webAppRootDir + File.separator + cgiPathPrefix
     *    (或者只有webAppRootDir，如果cgiPathPrefix是null)
     */
    private String cgiPathPrefix = null;

    /** 与脚本一起使用的可执行文件 */
    private String cgiExecutable = "perl";

    /** 可执行文件的附加参数 */
    private List<String> cgiExecutableArgs = null;

    /** 用于参数的编码 */
    private String parameterEncoding = System.getProperty("file.encoding", "UTF-8");

    /**
     * 用于完成stderr的读取等待的时间(in milliseconds), 在终止CGI过程之前.
     */
    private long stderrTimeout = 2000;

    /**
     * 用于将HTTP报头作为环境变量传递给CGI进程的正则表达式. 环境变量的名称将是HTTP header 转换器字母大写的名称,
     * 前缀是<code>HTTP_</code>和所有的<code>-</code>字符转换为<code>_</code>.
     */
    private Pattern envHttpHeadersPattern = Pattern.compile(
            "ACCEPT[-0-9A-Z]*|CACHE-CONTROL|COOKIE|HOST|IF-[-0-9A-Z]*|REFERER|USER-AGENT");

    /** 用于确保多个线程不尝试扩展同一文件的对象 */
    private static final Object expandFileLock = new Object();

    /** 要传递给CGI脚本的shell环境变量 */
    private final Hashtable<String,String> shellEnv = new Hashtable<>();

    /**
     * 启用来自查询字符串的脚本命令行参数的创建.
     */
    private boolean enableCmdLineArguments = true;

    /**
     * 设置实例变量.
     *
     * @param config  包含servlet配置和初始化参数的<code>ServletConfig</code>
     * @exception ServletException   如果发生了异常，干扰了servlet的正常操作
     */
    @Override
    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        // 从初始化参数设置属性
        cgiPathPrefix = getServletConfig().getInitParameter("cgiPathPrefix");
        boolean passShellEnvironment =
            Boolean.parseBoolean(getServletConfig().getInitParameter("passShellEnvironment"));

        if (passShellEnvironment) {
            shellEnv.putAll(System.getenv());
        }

        Enumeration<String> e = config.getInitParameterNames();
        while(e.hasMoreElements()) {
            String initParamName = e.nextElement();
            if (initParamName.startsWith("environment-variable-")) {
                if (initParamName.length() == 21) {
                    throw new ServletException(sm.getString("cgiServlet.emptyEnvVarName"));
                }
                shellEnv.put(initParamName.substring(21), config.getInitParameter(initParamName));
            }
        }

        if (getServletConfig().getInitParameter("executable") != null) {
            cgiExecutable = getServletConfig().getInitParameter("executable");
        }

        if (getServletConfig().getInitParameter("executable-arg-1") != null) {
            List<String> args = new ArrayList<>();
            for (int i = 1;; i++) {
                String arg = getServletConfig().getInitParameter(
                        "executable-arg-" + i);
                if (arg == null) {
                    break;
                }
                args.add(arg);
            }
            cgiExecutableArgs = args;
        }

        if (getServletConfig().getInitParameter("parameterEncoding") != null) {
            parameterEncoding = getServletConfig().getInitParameter("parameterEncoding");
        }

        if (getServletConfig().getInitParameter("stderrTimeout") != null) {
            stderrTimeout = Long.parseLong(getServletConfig().getInitParameter(
                    "stderrTimeout"));
        }

        if (getServletConfig().getInitParameter("envHttpHeaders") != null) {
            envHttpHeadersPattern =
                    Pattern.compile(getServletConfig().getInitParameter("envHttpHeaders"));
        }

        if (getServletConfig().getInitParameter("enableCmdLineArguments") != null) {
            enableCmdLineArguments =
                    Boolean.parseBoolean(config.getInitParameter("enableCmdLineArguments"));
        }
    }


    /**
     * L打印出重要的servlet API和容器信息.
     *
     * @param  req    用作信息源的HttpServletRequest对象
     *
     * @exception  IOException  如果出现写操作异常
     */
    private void printServletEnvironment(HttpServletRequest req) throws IOException {

        // Document the properties from ServletRequest
        log.trace("ServletRequest Properties");
        Enumeration<String> attrs = req.getAttributeNames();
        while (attrs.hasMoreElements()) {
            String attr = attrs.nextElement();
            log.trace("Request Attribute: " + attr + ": [ " + req.getAttribute(attr) +"]");
        }
        log.trace("Character Encoding: [" + req.getCharacterEncoding() + "]");
        log.trace("Content Length: [" + req.getContentLengthLong() + "]");
        log.trace("Content Type: [" + req.getContentType() + "]");
        Enumeration<Locale> locales = req.getLocales();
        while (locales.hasMoreElements()) {
            Locale locale = locales.nextElement();
            log.trace("Locale: [" +locale + "]");
        }
        Enumeration<String> params = req.getParameterNames();
        while (params.hasMoreElements()) {
            String param = params.nextElement();
            for (String value : req.getParameterValues(param)) {
                log.trace("Request Parameter: " + param + ":  [" + value + "]");
            }
        }
        log.trace("Protocol: [" + req.getProtocol() + "]");
        log.trace("Remote Address: [" + req.getRemoteAddr() + "]");
        log.trace("Remote Host: [" + req.getRemoteHost() + "]");
        log.trace("Scheme: [" + req.getScheme() + "]");
        log.trace("Secure: [" + req.isSecure() + "]");
        log.trace("Server Name: [" + req.getServerName() + "]");
        log.trace("Server Port: [" + req.getServerPort() + "]");

        // Document the properties from HttpServletRequest
        log.trace("HttpServletRequest Properties");
        log.trace("Auth Type: [" + req.getAuthType() + "]");
        log.trace("Context Path: [" + req.getContextPath() + "]");
        Cookie cookies[] = req.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                log.trace("Cookie: " + cookie.getName() + ": [" + cookie.getValue() + "]");
            }
        }
        Enumeration<String> headers = req.getHeaderNames();
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            log.trace("HTTP Header: " + header + ": [" + req.getHeader(header) + "]");
        }
        log.trace("Method: [" + req.getMethod() + "]");
        log.trace("Path Info: [" + req.getPathInfo() + "]");
        log.trace("Path Translated: [" + req.getPathTranslated() + "]");
        log.trace("Query String: [" + req.getQueryString() + "]");
        log.trace("Remote User: [" + req.getRemoteUser() + "]");
        log.trace("Requested Session ID: [" + req.getRequestedSessionId() + "]");
        log.trace("Requested Session ID From Cookie: [" +
                req.isRequestedSessionIdFromCookie() + "]");
        log.trace("Requested Session ID From URL: [" + req.isRequestedSessionIdFromURL() + "]");
        log.trace("Requested Session ID Valid: [" + req.isRequestedSessionIdValid() + "]");
        log.trace("Request URI: [" + req.getRequestURI() + "]");
        log.trace("Servlet Path: [" + req.getServletPath() + "]");
        log.trace("User Principal: [" + req.getUserPrincipal() + "]");

        // Process the current session (if there is one)
        HttpSession session = req.getSession(false);
        if (session != null) {

            // Document the session properties
            log.trace("HttpSession Properties");
            log.trace("ID: [" + session.getId() + "]");
            log.trace("Creation Time: [" + new Date(session.getCreationTime()) + "]");
            log.trace("Last Accessed Time: [" + new Date(session.getLastAccessedTime()) + "]");
            log.trace("Max Inactive Interval: [" + session.getMaxInactiveInterval() + "]");

            // Document the session attributes
            attrs = session.getAttributeNames();
            while (attrs.hasMoreElements()) {
                String attr = attrs.nextElement();
                log.trace("Session Attribute: " + attr + ": [" + session.getAttribute(attr) + "]");
            }
        }

        // Document the servlet configuration properties
        log.trace("ServletConfig Properties");
        log.trace("Servlet Name: [" + getServletConfig().getServletName() + "]");

        // Document the servlet configuration initialization parameters
        params = getServletConfig().getInitParameterNames();
        while (params.hasMoreElements()) {
            String param = params.nextElement();
            String value = getServletConfig().getInitParameter(param);
            log.trace("Servlet Init Param: " + param + ": [" + value + "]");
        }

        // Document the servlet context properties
        log.trace("ServletContext Properties");
        log.trace("Major Version: [" + getServletContext().getMajorVersion() + "]");
        log.trace("Minor Version: [" + getServletContext().getMinorVersion() + "]");
        log.trace("Real Path for '/': [" + getServletContext().getRealPath("/") + "]");
        log.trace("Server Info: [" + getServletContext().getServerInfo() + "]");

        // Document the servlet context initialization parameters
        log.trace("ServletContext Initialization Parameters");
        params = getServletContext().getInitParameterNames();
        while (params.hasMoreElements()) {
            String param = params.nextElement();
            String value = getServletContext().getInitParameter(param);
            log.trace("Servlet Context Init Param: " + param + ": [" + value + "]");
        }

        // Document the servlet context attributes
        log.trace("ServletContext Attributes");
        attrs = getServletContext().getAttributeNames();
        while (attrs.hasMoreElements()) {
            String attr = attrs.nextElement();
            log.trace("Servlet Context Attribute: " + attr +
                    ": [" + getServletContext().getAttribute(attr) + "]");
        }
    }


    /**
     * 提供CGI网关服务 -- 委托给{@link #doGet(HttpServletRequest, HttpServletResponse)}.
     *
     * @param  req   HttpServletRequest passed in by servlet container
     * @param  res   HttpServletResponse passed in by servlet container
     *
     * @exception  ServletException  if a servlet-specific exception occurs
     * @exception  IOException  if a read/write exception occurs
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {
        doGet(req, res);
    }


    /**
     * 提供CGI网关服务.
     *
     * @param  req   HttpServletRequest passed in by servlet container
     * @param  res   HttpServletResponse passed in by servlet container
     *
     * @exception  ServletException  if a servlet-specific exception occurs
     * @exception  IOException  if a read/write exception occurs
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        CGIEnvironment cgiEnv = new CGIEnvironment(req, getServletContext());

        if (cgiEnv.isValid()) {
            CGIRunner cgi = new CGIRunner(cgiEnv.getCommand(),
                                          cgiEnv.getEnvironment(),
                                          cgiEnv.getWorkingDirectory(),
                                          cgiEnv.getParameters());

            if ("POST".equals(req.getMethod())) {
                cgi.setInput(req.getInputStream());
            }
            cgi.setResponse(res);
            cgi.run();
        } else {
            res.sendError(404);
        }

        if (log.isTraceEnabled()) {
            String[] cgiEnvLines = cgiEnv.toString().split(System.lineSeparator());
            for (String cgiEnvLine : cgiEnvLines) {
                log.trace(cgiEnvLine);
            }

            printServletEnvironment(req);
        }
    }


    /**
     * 取决于状态码的行为.
     *
     * Status < 400  - 调用 setStatus. 返回 false. CGI servlet将提供响应主体.
     * Status >= 400 - 调用 sendError(status), 返回 true. 标准错误页面机制将提供响应主体.
     */
    private boolean setStatus(HttpServletResponse response, int status) throws IOException {
        if (status >= HttpServletResponse.SC_BAD_REQUEST) {
            response.sendError(status);
            return true;
        } else {
            response.setStatus(status);
            return false;
        }
    }


    /**
     * 封装CGI环境和规则，以从servlet容器和请求信息中派生出该环境.
     */
    protected class CGIEnvironment {


        /** 封闭servlet的上下文 */
        private ServletContext context = null;

        /** 封闭servlet的上下文路径 */
        private String contextPath = null;

        /** 封闭servlet的servlet URI */
        private String servletPath = null;

        /** 当前请求的路径 */
        private String pathInfo = null;

        /** 封闭servlet Web应用程序的真正文件系统目录 */
        private String webAppRootDir = null;

        /** 上下文临时文件夹 - 用于扩展war中的脚本 */
        private File tmpDir = null;

        /** 衍生的CGI环境 */
        private Hashtable<String, String> env = null;

        /** 要调用的CGI命令 */
        private String command = null;

        /** CGI命令所需的工作目录 */
        private final File workingDirectory;

        /** CGI命令的命令行参数 */
        private final ArrayList<String> cmdLineParameters = new ArrayList<>();

        /** 这个对象是否有效 */
        private final boolean valid;


        /**
         * 创建一个CGIEnvironment 并派生出必要的环境, 查询参数, 工作目录, CGI命令, etc.
         *
         * @param  req       HttpServletRequest for information provided by
         *                   the Servlet API
         * @param  context   ServletContext for information provided by the
         *                   Servlet API
         * @throws IOException an IO error occurred
         */
        protected CGIEnvironment(HttpServletRequest req,
                                 ServletContext context) throws IOException {
            setupFromContext(context);
            setupFromRequest(req);

            this.valid = setCGIEnvironment(req);

            if (this.valid) {
                workingDirectory = new File(command.substring(0,
                      command.lastIndexOf(File.separator)));
            } else {
                workingDirectory = null;
            }
        }


        /**
         * 使用ServletContext设置一些CGI变量
         *
         * @param  context   ServletContext for information provided by the
         *                   Servlet API
         */
        protected void setupFromContext(ServletContext context) {
            this.context = context;
            this.webAppRootDir = context.getRealPath("/");
            this.tmpDir = (File) context.getAttribute(ServletContext.TEMPDIR);
        }


        /**
         * 使用HttpServletRequest设置大多数CGI变量
         *
         * @param  req   HttpServletRequest for information provided by
         *               the Servlet API
         * @throws UnsupportedEncodingException Unknown encoding
         */
        protected void setupFromRequest(HttpServletRequest req)
                throws UnsupportedEncodingException {

            boolean isIncluded = false;

            // Look to see if this request is an include
            if (req.getAttribute(
                    RequestDispatcher.INCLUDE_REQUEST_URI) != null) {
                isIncluded = true;
            }
            if (isIncluded) {
                this.contextPath = (String) req.getAttribute(
                        RequestDispatcher.INCLUDE_CONTEXT_PATH);
                this.servletPath = (String) req.getAttribute(
                        RequestDispatcher.INCLUDE_SERVLET_PATH);
                this.pathInfo = (String) req.getAttribute(
                        RequestDispatcher.INCLUDE_PATH_INFO);
            } else {
                this.contextPath = req.getContextPath();
                this.servletPath = req.getServletPath();
                this.pathInfo = req.getPathInfo();
            }
            // If getPathInfo() returns null, must be using extension mapping
            // In this case, pathInfo should be same as servletPath
            if (this.pathInfo == null) {
                this.pathInfo = this.servletPath;
            }

            // If the request method is GET, POST or HEAD and the query string
            // does not contain an unencoded "=" this is an indexed query.
            // The parsed query string becomes the command line parameters
            // for the cgi command.
            if (enableCmdLineArguments && (req.getMethod().equals("GET")
                || req.getMethod().equals("POST") || req.getMethod().equals("HEAD"))) {
                String qs;
                if (isIncluded) {
                    qs = (String) req.getAttribute(
                            RequestDispatcher.INCLUDE_QUERY_STRING);
                } else {
                    qs = req.getQueryString();
                }
                if (qs != null && qs.indexOf('=') == -1) {
                    StringTokenizer qsTokens = new StringTokenizer(qs, "+");
                    while ( qsTokens.hasMoreTokens() ) {
                        cmdLineParameters.add(URLDecoder.decode(qsTokens.nextToken(),
                                              parameterEncoding));
                    }
                }
            }
        }


        /**
         * 解析有关CGI脚本的核心信息.
         *
         * <p>
         * Example URI:
         * </p>
         * <PRE> /servlet/cgigateway/dir1/realCGIscript/pathinfo1 </PRE>
         * <ul>
         * <LI><b>path</b> = $CATALINA_HOME/mywebapp/dir1/realCGIscript
         * <LI><b>scriptName</b> = /servlet/cgigateway/dir1/realCGIscript
         * <LI><b>cgiName</b> = /dir1/realCGIscript
         * <LI><b>name</b> = realCGIscript
         * </ul>
         * <p>
         * CGI的搜索算法: 搜索下面的真实路径
         *    &lt;my-webapp-root&gt; 并查找getPathTranslated("/")中的第一个非目录, 读取/搜索从左至右.
         *</p>
         *<p>
         *   CGI搜索路径：
         *   webAppRootDir + File.separator + cgiPathPrefix
         *   (只使用webAppRootDir，如果cgiPathPrefix是null).
         *</p>
         *<p>
         *   cgiPathPrefix是通过设置这个servlet的cgiPathPrefix 初始化参数定义的
         *</p>
         *
         * @param pathInfo       String from HttpServletRequest.getPathInfo()
         * @param webAppRootDir  String from context.getRealPath("/")
         * @param contextPath    String as from
         *                       HttpServletRequest.getContextPath()
         * @param servletPath    String as from
         *                       HttpServletRequest.getServletPath()
         * @param cgiPathPrefix  webAppRootDir下面的子目录，Web应用的CGI可以存储; 可以是 null.
         *                       CGI搜索路径：
         *   						webAppRootDir + File.separator + cgiPathPrefix
         *   						(只使用webAppRootDir，如果cgiPathPrefix是null).
         *   						cgiPathPrefix 通过设置servlet的 cgiPathPrefix 初始化参数指定.
         *
         *
         * @return
         * <ul>
         * <li>
         * <code>path</code> -    有效的CGI脚本的完整的文件系统路径,或者null
         * <li>
         * <code>scriptName</code> - CGI变量SCRIPT_NAME; 有效CGI脚本的完整URL路径，或 null
         * <li>
         * <code>cgiName</code> - servlet路径信息片段对应于CGI脚本本身, 或null
         * <li>
         * <code>name</code> -    CGI脚本的简单名称（没有目录）, 或null
         * </ul>
         */
        protected String[] findCGI(String pathInfo, String webAppRootDir,
                                   String contextPath, String servletPath,
                                   String cgiPathPrefix) {
            String path = null;
            String name = null;
            String scriptname = null;

            if (webAppRootDir != null &&
                    webAppRootDir.lastIndexOf(File.separator) == (webAppRootDir.length() - 1)) {
                //strip the trailing "/" from the webAppRootDir
                webAppRootDir = webAppRootDir.substring(0, (webAppRootDir.length() - 1));
            }

            if (cgiPathPrefix != null) {
                webAppRootDir = webAppRootDir + File.separator + cgiPathPrefix;
            }

            if (log.isDebugEnabled()) {
                log.debug(sm.getString("cgiServlet.find.path", pathInfo, webAppRootDir));
            }

            File currentLocation = new File(webAppRootDir);
            StringTokenizer dirWalker = new StringTokenizer(pathInfo, "/");
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("cgiServlet.find.location",
                        currentLocation.getAbsolutePath()));
            }
            StringBuilder cginameBuilder = new StringBuilder();
            while (!currentLocation.isFile() && dirWalker.hasMoreElements()) {
                String nextElement = (String) dirWalker.nextElement();
                currentLocation = new File(currentLocation, nextElement);
                cginameBuilder.append('/').append(nextElement);
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("cgiServlet.find.location",
                            currentLocation.getAbsolutePath()));
                }
            }
            String cginame = cginameBuilder.toString();
            if (!currentLocation.isFile()) {
                return new String[] { null, null, null, null };
            }

            path = currentLocation.getAbsolutePath();
            name = currentLocation.getName();

            if (servletPath.startsWith(cginame)) {
                scriptname = contextPath + cginame;
            } else {
                scriptname = contextPath + servletPath + cginame;
            }

            if (log.isDebugEnabled()) {
                log.debug(sm.getString("cgiServlet.find.found", name, path, scriptname, cginame));
            }
            return new String[] { path, scriptname, cginame, name };
        }

        /**
         * 构建提供给CGI脚本的CGI环境; 依赖Servlet API方法和findCGI
         *
         * @param    req request associated with the CGI
         *           Invocation
         *
         * @return   true if environment was set OK, false if there
         *           was a problem and no environment was set
         * @throws IOException an IO error occurred
         */
        protected boolean setCGIEnvironment(HttpServletRequest req) throws IOException {

            /*
             * This method is slightly ugly; c'est la vie.
             * "You cannot stop [ugliness], you can only hope to contain [it]"
             * (apologies to Marv Albert regarding MJ)
             */

            Hashtable<String,String> envp = new Hashtable<>();

            // Add the shell environment variables (if any)
            envp.putAll(shellEnv);

            // Add the CGI environment variables
            String sPathInfoOrig = null;
            String sPathInfoCGI = null;
            String sPathTranslatedCGI = null;
            String sCGIFullPath = null;
            String sCGIScriptName = null;
            String sCGIFullName = null;
            String sCGIName = null;
            String[] sCGINames;


            sPathInfoOrig = this.pathInfo;
            sPathInfoOrig = sPathInfoOrig == null ? "" : sPathInfoOrig;

            if (webAppRootDir == null ) {
                // The app has not been deployed in exploded form
                webAppRootDir = tmpDir.toString();
                expandCGIScript();
            }

            sCGINames = findCGI(sPathInfoOrig,
                                webAppRootDir,
                                contextPath,
                                servletPath,
                                cgiPathPrefix);

            sCGIFullPath = sCGINames[0];
            sCGIScriptName = sCGINames[1];
            sCGIFullName = sCGINames[2];
            sCGIName = sCGINames[3];

            if (sCGIFullPath == null
                || sCGIScriptName == null
                || sCGIFullName == null
                || sCGIName == null) {
                return false;
            }

            envp.put("SERVER_SOFTWARE", "TOMCAT");

            envp.put("SERVER_NAME", nullsToBlanks(req.getServerName()));

            envp.put("GATEWAY_INTERFACE", "CGI/1.1");

            envp.put("SERVER_PROTOCOL", nullsToBlanks(req.getProtocol()));

            int port = req.getServerPort();
            Integer iPort =
                (port == 0 ? Integer.valueOf(-1) : Integer.valueOf(port));
            envp.put("SERVER_PORT", iPort.toString());

            envp.put("REQUEST_METHOD", nullsToBlanks(req.getMethod()));

            envp.put("REQUEST_URI", nullsToBlanks(req.getRequestURI()));


            /*-
             * PATH_INFO should be determined by using sCGIFullName:
             * 1) Let sCGIFullName not end in a "/" (see method findCGI)
             * 2) Let sCGIFullName equal the pathInfo fragment which
             *    corresponds to the actual cgi script.
             * 3) Thus, PATH_INFO = request.getPathInfo().substring(
             *                      sCGIFullName.length())
             *
             * (see method findCGI, where the real work is done)
             *
             */
            if (pathInfo == null
                || (pathInfo.substring(sCGIFullName.length()).length() <= 0)) {
                sPathInfoCGI = "";
            } else {
                sPathInfoCGI = pathInfo.substring(sCGIFullName.length());
            }
            envp.put("PATH_INFO", sPathInfoCGI);


            /*-
             * PATH_TRANSLATED must be determined after PATH_INFO (and the
             * implied real cgi-script) has been taken into account.
             *
             * The following example demonstrates:
             *
             * servlet info   = /servlet/cgigw/dir1/dir2/cgi1/trans1/trans2
             * cgifullpath    = /servlet/cgigw/dir1/dir2/cgi1
             * path_info      = /trans1/trans2
             * webAppRootDir  = servletContext.getRealPath("/")
             *
             * path_translated = servletContext.getRealPath("/trans1/trans2")
             *
             * That is, PATH_TRANSLATED = webAppRootDir + sPathInfoCGI
             * (unless sPathInfoCGI is null or blank, then the CGI
             * specification dictates that the PATH_TRANSLATED metavariable
             * SHOULD NOT be defined.
             *
             */
            if (!("".equals(sPathInfoCGI))) {
                sPathTranslatedCGI = context.getRealPath(sPathInfoCGI);
            }
            if (sPathTranslatedCGI == null || "".equals(sPathTranslatedCGI)) {
                //NOOP
            } else {
                envp.put("PATH_TRANSLATED", nullsToBlanks(sPathTranslatedCGI));
            }


            envp.put("SCRIPT_NAME", nullsToBlanks(sCGIScriptName));

            envp.put("QUERY_STRING", nullsToBlanks(req.getQueryString()));

            envp.put("REMOTE_HOST", nullsToBlanks(req.getRemoteHost()));

            envp.put("REMOTE_ADDR", nullsToBlanks(req.getRemoteAddr()));

            envp.put("AUTH_TYPE", nullsToBlanks(req.getAuthType()));

            envp.put("REMOTE_USER", nullsToBlanks(req.getRemoteUser()));

            envp.put("REMOTE_IDENT", ""); //not necessary for full compliance

            envp.put("CONTENT_TYPE", nullsToBlanks(req.getContentType()));


            /* Note CGI spec says CONTENT_LENGTH must be NULL ("") or undefined
             * if there is no content, so we cannot put 0 or -1 in as per the
             * Servlet API spec.
             */
            long contentLength = req.getContentLengthLong();
            String sContentLength = (contentLength <= 0 ? "" :
                Long.toString(contentLength));
            envp.put("CONTENT_LENGTH", sContentLength);


            Enumeration<String> headers = req.getHeaderNames();
            String header = null;
            while (headers.hasMoreElements()) {
                header = null;
                header = headers.nextElement().toUpperCase(Locale.ENGLISH);
                //REMIND: rewrite multiple headers as if received as single
                //REMIND: change character set
                //REMIND: I forgot what the previous REMIND means
                if (envHttpHeadersPattern.matcher(header).matches()) {
                    envp.put("HTTP_" + header.replace('-', '_'), req.getHeader(header));
                }
            }

            File fCGIFullPath = new File(sCGIFullPath);
            command = fCGIFullPath.getCanonicalPath();

            envp.put("X_TOMCAT_SCRIPT_PATH", command);  //for kicks

            envp.put("SCRIPT_FILENAME", command);  //for PHP

            this.env = envp;

            return true;

        }

        /**
         * 将请求的资源从Web应用程序存档提取到上下文工作目录，以便执行CGI脚本.
         */
        protected void expandCGIScript() {
            StringBuilder srcPath = new StringBuilder();
            StringBuilder destPath = new StringBuilder();
            InputStream is = null;

            // paths depend on mapping
            if (cgiPathPrefix == null ) {
                srcPath.append(pathInfo);
                is = context.getResourceAsStream(srcPath.toString());
                destPath.append(tmpDir);
                destPath.append(pathInfo);
            } else {
                // essentially same search algorithm as findCGI()
                srcPath.append(cgiPathPrefix);
                StringTokenizer pathWalker =
                        new StringTokenizer (pathInfo, "/");
                // start with first element
                while (pathWalker.hasMoreElements() && (is == null)) {
                    srcPath.append("/");
                    srcPath.append(pathWalker.nextElement());
                    is = context.getResourceAsStream(srcPath.toString());
                }
                destPath.append(tmpDir);
                destPath.append("/");
                destPath.append(srcPath);
            }

            if (is == null) {
                // didn't find anything, give up now
                log.warn(sm.getString("cgiServlet.expandNotFound", srcPath));
                return;
            }

            File f = new File(destPath.toString());
            if (f.exists()) {
                try {
                    is.close();
                } catch (IOException e) {
                    log.warn(sm.getString("cgiServlet.expandCloseFail", srcPath), e);
                }
                // Don't need to expand if it already exists
                return;
            }

            // create directories
            File dir = f.getParentFile();
            if (!dir.mkdirs() && !dir.isDirectory()) {
                log.warn(sm.getString("cgiServlet.expandCreateDirFail", dir.getAbsolutePath()));
                return;
            }

            try {
                synchronized (expandFileLock) {
                    // make sure file doesn't exist
                    if (f.exists()) {
                        return;
                    }

                    // create file
                    if (!f.createNewFile()) {
                        return;
                    }

                    try {
                        Files.copy(is, f.toPath());
                    } finally {
                        is.close();
                    }

                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("cgiServlet.expandOk", srcPath, destPath));
                    }
                }
            } catch (IOException ioe) {
                log.warn(sm.getString("cgiServlet.expandFail", srcPath, destPath), ioe);
                // delete in case file is corrupted
                if (f.exists()) {
                    if (!f.delete()) {
                        log.warn(sm.getString("cgiServlet.expandDeleteFail", f.getAbsolutePath()));
                    }
                }
            }
        }


        /**
         * Returns important CGI environment information in a multi-line text
         * format.
         *
         * @return CGI environment info
         */
        @Override
        public String toString() {

            StringBuilder sb = new StringBuilder();

            sb.append("CGIEnvironment Info:");
            sb.append(System.lineSeparator());

            if (isValid()) {
                sb.append("Validity: [true]");
                sb.append(System.lineSeparator());

                sb.append("Environment values:");
                sb.append(System.lineSeparator());
                for (Entry<String,String> entry : env.entrySet()) {
                    sb.append("  ");
                    sb.append(entry.getKey());
                    sb.append(": [");
                    sb.append(blanksToString(entry.getValue(), "will be set to blank"));
                    sb.append("]");
                    sb.append(System.lineSeparator());
                }

                sb.append("Derived Command :[");
                sb.append(nullsToBlanks(command));
                sb.append("]");
                sb.append(System.lineSeparator());


                sb.append("Working Directory: [");
                if (workingDirectory != null) {
                    sb.append(workingDirectory.toString());
                }
                sb.append("]");
                sb.append(System.lineSeparator());

                sb.append("Command Line Params:");
                sb.append(System.lineSeparator());
                for (String param : cmdLineParameters) {
                    sb.append("  [");
                    sb.append(param);
                    sb.append("]");
                    sb.append(System.lineSeparator());
                }
            } else {
                sb.append("Validity: [false]");
                sb.append(System.lineSeparator());
                sb.append("CGI script not found or not specified.");
                sb.append(System.lineSeparator());
                sb.append("Check the HttpServletRequest pathInfo property to see if it is what ");
                sb.append(System.lineSeparator());
                sb.append("you meant it to be. You must specify an existant and executable file ");
                sb.append(System.lineSeparator());
                sb.append("as part of the path-info.");
                sb.append(System.lineSeparator());
            }

            return sb.toString();
        }


        /**
         * 获取派生命令字符串
         */
        protected String getCommand() {
            return command;
        }


        /**
         * 获取派生的CGI工作目录
         */
        protected File getWorkingDirectory() {
            return workingDirectory;
        }


        /**
         * 获取派生的CGI环境
         */
        protected Hashtable<String,String> getEnvironment() {
            return env;
        }


        /**
         * 获取派生的CGI查询参数
         */
        protected ArrayList<String> getParameters() {
            return cmdLineParameters;
        }


        /**
         * 获取有效状态
         *
         * @return   true 环境有效, 否则false
         */
        protected boolean isValid() {
            return valid;
        }


        /**
         * 将null转换为空字符串("")
         *
         * @param    s string to be converted if necessary
         * @return   a non-null string, either the original or the empty string
         *           ("") if the original was <code>null</code>
         */
        protected String nullsToBlanks(String s) {
            return nullsToString(s, "");
        }


        /**
         * 将null转换为另一个字符串
         *
         * @param    couldBeNull string to be converted if necessary
         * @param    subForNulls string to return instead of a null string
         * @return   a non-null string, either the original or the substitute
         *           string if the original was <code>null</code>
         */
        protected String nullsToString(String couldBeNull,
                                       String subForNulls) {
            return (couldBeNull == null ? subForNulls : couldBeNull);
        }


        /**
         * 将空白字符串转换为另一个字符串
         *
         * @param    couldBeBlank string to be converted if necessary
         * @param    subForBlanks string to return instead of a blank string
         * @return   a non-null string, either the original or the substitute
         *           string if the original was <code>null</code> or empty ("")
         */
        protected String blanksToString(String couldBeBlank,
                                      String subForBlanks) {
            return (("".equals(couldBeBlank) || couldBeBlank == null)
                    ? subForBlanks
                    : couldBeBlank);
        }


    } //class CGIEnvironment


    /**
     * 封装了如何运行CGI脚本的知识, 给定脚本所需的环境和（可选）输入/输出流
     *
     * <p>
     * 暴露<code>run</code>方法用于实际调用CGI
     * </p>
     * <p>
     * CGI环境和设置传递给构造器.
     * </p>
     * <p>
     * 输入输出流可以通过<code>setInput</code>和<code>setResponse</code>方法设置, 分别地.
     * </p>
     */
    protected class CGIRunner {

        /** 要执行的脚本/命令 */
        private final String command;

        /** 调用CGI脚本时使用的环境  */
        private final Hashtable<String,String> env;

        /** 执行CGI脚本时使用的工作目录 */
        private final File wd;

        /** 要传递给被调用脚本的查询参数 */
        private final ArrayList<String> params;

        /** 输入要传递给CGI脚本 */
        private InputStream stdin = null;

        /** 用于设置标头和获取输出流的响应对象 */
        private HttpServletResponse response = null;

        /** 该对象是否有足够的信息来run() */
        private boolean readyToRun = false;


        /**
         *  创建一个CGIRunner 并初始化它的环境, 工作目录, 和查询参数.
         *  <BR>
         *  使用<code>setInput</code>和<code>setResponse</code>方法设置输入输出流.
         *
         * @param  command  要执行的命令的字符串完整路径
         * @param  env      Hashtable 所需的脚本环境
         * @param  wd       使用脚本所需的工作目录
         * @param  params   Hashtable使用脚本的查询参数
         */
        protected CGIRunner(String command, Hashtable<String,String> env,
                            File wd, ArrayList<String> params) {
            this.command = command;
            this.env = env;
            this.wd = wd;
            this.params = params;
            updateReadyStatus();
        }


        /**
         * Checks and sets ready status
         */
        protected void updateReadyStatus() {
            if (command != null
                && env != null
                && wd != null
                && params != null
                && response != null) {
                readyToRun = true;
            } else {
                readyToRun = false;
            }
        }


        /**
         * Gets ready status
         *
         * @return   false if not ready (<code>run</code> will throw
         *           an exception), true if ready
         */
        protected boolean isReady() {
            return readyToRun;
        }


        /**
         * 设置HttpServletResponse对象用于设置header和发送输出
         *
         * @param  response   HttpServletResponse to be used
         */
        protected void setResponse(HttpServletResponse response) {
            this.response = response;
            updateReadyStatus();
        }


        /**
         * 设置要传递给调用的CGI脚本的标准输入
         *
         * @param  stdin   InputStream to be used
         */
        protected void setInput(InputStream stdin) {
            this.stdin = stdin;
            updateReadyStatus();
        }


        /**
         * 转换Hashtable成String数组，通过转换每个键值对成一个String,格式为"key=value" (hashkey + "=" + hash.get(hashkey).toString())
         *
         * @param  h   Hashtable to convert
         *
         * @return     converted string array
         *
         * @exception  NullPointerException   if a hash key has a null value
         *
         */
        protected String[] hashToStringArray(Hashtable<String,?> h)
            throws NullPointerException {
            Vector<String> v = new Vector<>();
            Enumeration<String> e = h.keys();
            while (e.hasMoreElements()) {
                String k = e.nextElement();
                v.add(k + "=" + h.get(k).toString());
            }
            String[] strArr = new String[v.size()];
            v.copyInto(strArr);
            return strArr;
        }


        /**
         * 使用所需环境、当前工作目录和输入/输出流执行CGI脚本
         *
         * <p>
         * 实现了以下CGI规范的建议:
         * <UL>
         * <LI> 服务器应该将脚本URI的“查询”组件作为脚本的命令行参数提供给脚本, 如果它不包含任何非编码“=”字符和命令行参数，可以生成一个明确的方式.
         * <LI> See <code>getCGIEnvironment</code> method.
         * <LI> 在适用的情况下，服务器应该在调用脚本之前将当前工作目录设置为脚本所在的目录.
         * <LI> 服务器实现应该为下列情况定义其行为:
         *     <ul>
         *     <LI> <u>允许的字符是</u>:  此实现不允许ASCII NUL或任何字符不能URL编码根据互联网标准;
         *     <LI> <u>路径段中允许的字符</u>: 此实现不允许路径中的非终结符空段 -- IOExceptions may be thrown;
         *     <LI> <u>"<code>.</code>" and "<code>..</code>" 路径</u>:
         *             此实现不允许"<code>.</code>" 和
         *             "<code>..</code>" 包含在路径中, 这样字符会通过IOException异常被抛出;
         *     <LI> <u>实现限制</u>: 除了上述记录外，此实现没有任何限制. 此实现可能受到用于保存此实现的servlet容器的限制.
         *             特别是，所有主要CGI变量值都是直接或间接从容器的servlet API方法的实现派生的.
         *     </ul>
         * </UL>
         * </p>
         *
         * @exception IOException if problems during reading/writing occur
         */
        protected void run() throws IOException {

            /*
             * REMIND:  this method feels too big; should it be re-written?
             */

            if (!isReady()) {
                throw new IOException(this.getClass().getName() + ": not ready to run.");
            }

            if (log.isDebugEnabled()) {
                log.debug("envp: [" + env + "], command: [" + command + "]");
            }

            if ((command.indexOf(File.separator + "." + File.separator) >= 0)
                || (command.indexOf(File.separator + "..") >= 0)
                || (command.indexOf(".." + File.separator) >= 0)) {
                throw new IOException(this.getClass().getName()
                                      + "Illegal Character in CGI command "
                                      + "path ('.' or '..') detected.  Not "
                                      + "running CGI [" + command + "].");
            }

            /* original content/structure of this section taken from
             * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4216884
             * with major modifications by Martin Dengler
             */
            Runtime rt = null;
            BufferedReader cgiHeaderReader = null;
            InputStream cgiOutput = null;
            BufferedReader commandsStdErr = null;
            Thread errReaderThread = null;
            BufferedOutputStream commandsStdIn = null;
            Process proc = null;
            int bufRead = -1;

            List<String> cmdAndArgs = new ArrayList<>();
            if (cgiExecutable.length() != 0) {
                cmdAndArgs.add(cgiExecutable);
            }
            if (cgiExecutableArgs != null) {
                cmdAndArgs.addAll(cgiExecutableArgs);
            }
            cmdAndArgs.add(command);
            cmdAndArgs.addAll(params);

            try {
                rt = Runtime.getRuntime();
                proc = rt.exec(
                        cmdAndArgs.toArray(new String[cmdAndArgs.size()]),
                        hashToStringArray(env), wd);

                String sContentLength = env.get("CONTENT_LENGTH");

                if(!"".equals(sContentLength)) {
                    commandsStdIn = new BufferedOutputStream(proc.getOutputStream());
                    IOTools.flow(stdin, commandsStdIn);
                    commandsStdIn.flush();
                    commandsStdIn.close();
                }

                /* 我们要等待进程退出,  Process.waitFor()是无效的; see
                 * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4223650
                 */

                boolean isRunning = true;
                commandsStdErr = new BufferedReader
                    (new InputStreamReader(proc.getErrorStream()));
                final BufferedReader stdErrRdr = commandsStdErr ;

                errReaderThread = new Thread() {
                    @Override
                    public void run () {
                        sendToLog(stdErrRdr);
                    }
                };
                errReaderThread.start();

                InputStream cgiHeaderStream =
                    new HTTPHeaderInputStream(proc.getInputStream());
                cgiHeaderReader =
                    new BufferedReader(new InputStreamReader(cgiHeaderStream));

                // Need to be careful here. If sendError() is called the
                // response body should be provided by the standard error page
                // process. But, if the output of the CGI process isn't read
                // then that process can hang.
                boolean skipBody = false;

                while (isRunning) {
                    try {
                        //set headers
                        String line = null;
                        while (((line = cgiHeaderReader.readLine()) != null) && !("".equals(line))) {
                            if (log.isTraceEnabled()) {
                                log.trace("addHeader(\"" + line + "\")");
                            }
                            if (line.startsWith("HTTP")) {
                                skipBody = setStatus(response, getSCFromHttpStatusLine(line));
                            } else if (line.indexOf(':') >= 0) {
                                String header =
                                    line.substring(0, line.indexOf(':')).trim();
                                String value =
                                    line.substring(line.indexOf(':') + 1).trim();
                                if (header.equalsIgnoreCase("status")) {
                                    skipBody = setStatus(response, getSCFromCGIStatusHeader(value));
                                } else {
                                    response.addHeader(header , value);
                                }
                            } else {
                                log.info(sm.getString("cgiServlet.runBadHeader", line));
                            }
                        }

                        //write output
                        byte[] bBuf = new byte[2048];

                        OutputStream out = response.getOutputStream();
                        cgiOutput = proc.getInputStream();

                        try {
                            while (!skipBody && (bufRead = cgiOutput.read(bBuf)) != -1) {
                                if (log.isTraceEnabled()) {
                                    log.trace("output " + bufRead + " bytes of data");
                                }
                                out.write(bBuf, 0, bufRead);
                            }
                        } finally {
                            // Attempt to consume any leftover byte if something bad happens,
                            // such as a socket disconnect on the servlet side; otherwise, the
                            // external process could hang
                            if (bufRead != -1) {
                                while ((bufRead = cgiOutput.read(bBuf)) != -1) {
                                    // NOOP - just read the data
                                }
                            }
                        }

                        proc.exitValue(); // Throws exception if alive

                        isRunning = false;

                    } catch (IllegalThreadStateException e) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ignored) {
                            // Ignore
                        }
                    }
                } //replacement for Process.waitFor()

            } catch (IOException e){
                log.warn(sm.getString("cgiServlet.runFail"), e);
                throw e;
            } finally {
                // Close the header reader
                if (cgiHeaderReader != null) {
                    try {
                        cgiHeaderReader.close();
                    } catch (IOException ioe) {
                        log.warn(sm.getString("cgiServlet.runHeaderReaderFail"), ioe);
                    }
                }
                // Close the output stream if used
                if (cgiOutput != null) {
                    try {
                        cgiOutput.close();
                    } catch (IOException ioe) {
                        log.warn(sm.getString("cgiServlet.runOutputStreamFail"), ioe);
                    }
                }
                // Make sure the error stream reader has finished
                if (errReaderThread != null) {
                    try {
                        errReaderThread.join(stderrTimeout);
                    } catch (InterruptedException e) {
                        log.warn(sm.getString("cgiServlet.runReaderInterrupt"));                    }
                }
                if (proc != null){
                    proc.destroy();
                    proc = null;
                }
            }
        }

        /**
         * 解析Status-Line并提取状态码.
         *
         * @param line HTTP Status-Line (RFC2616, section 6.1)
         * @return 如果不能提取有效状态码，则提取状态码或表示内部错误的代码.
         */
        private int getSCFromHttpStatusLine(String line) {
            int statusStart = line.indexOf(' ') + 1;

            if (statusStart < 1 || line.length() < statusStart + 3) {
                // Not a valid HTTP Status-Line
                log.warn(sm.getString("cgiServlet.runInvalidStatus", line));
                return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            }

            String status = line.substring(statusStart, statusStart + 3);

            int statusCode;
            try {
                statusCode = Integer.parseInt(status);
            } catch (NumberFormatException nfe) {
                // Not a valid status code
                log.warn(sm.getString("cgiServlet.runInvalidStatus", status));
                return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            }

            return statusCode;
        }

        /**
         * 解析CGI Status Header值并提取状态码.
         *
         * @param value 表单的 CGI Status值<code>digit digit digit SP reason-phrase</code>
         * 
         * @return 提取的状态码或错误码.
         */
        private int getSCFromCGIStatusHeader(String value) {
            if (value.length() < 3) {
                // Not a valid status value
                log.warn(sm.getString("cgiServlet.runInvalidStatus", value));
                return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            }

            String status = value.substring(0, 3);

            int statusCode;
            try {
                statusCode = Integer.parseInt(status);
            } catch (NumberFormatException nfe) {
                // Not a valid status code
                log.warn(sm.getString("cgiServlet.runInvalidStatus", status));
                return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            }

            return statusCode;
        }

        private void sendToLog(BufferedReader rdr) {
            String line = null;
            int lineCount = 0 ;
            try {
                while ((line = rdr.readLine()) != null) {
                    log.warn(sm.getString("cgiServlet.runStdErr", line));
                    lineCount++ ;
                }
            } catch (IOException e) {
                log.warn(sm.getString("cgiServlet.runStdErrFail"), e);
            } finally {
                try {
                    rdr.close();
                } catch (IOException e) {
                    log.warn(sm.getString("cgiServlet.runStdErrFail"), e);
                }
            }
            if (lineCount > 0) {
                log.warn(sm.getString("cgiServlet.runStdErrCount", Integer.valueOf(lineCount)));
            }
        }
    } //class CGIRunner

    /**
     * 这是一个专门用于读取HTTP头的输入流. 它读取最多，包括两个终止header的空行. 它允许使用字节或字符作为适当的内容读取.
     */
    protected static class HTTPHeaderInputStream extends InputStream {
        private static final int STATE_CHARACTER = 0;
        private static final int STATE_FIRST_CR = 1;
        private static final int STATE_FIRST_LF = 2;
        private static final int STATE_SECOND_CR = 3;
        private static final int STATE_HEADER_END = 4;

        private final InputStream input;
        private int state;

        HTTPHeaderInputStream(InputStream theInput) {
            input = theInput;
            state = STATE_CHARACTER;
        }

        @Override
        public int read() throws IOException {
            if (state == STATE_HEADER_END) {
                return -1;
            }

            int i = input.read();

            // Update the state
            // State machine looks like this
            //
            //    -------->--------
            //   |      (CR)       |
            //   |                 |
            //  CR1--->---         |
            //   |        |        |
            //   ^(CR)    |(LF)    |
            //   |        |        |
            // CHAR--->--LF1--->--EOH
            //      (LF)  |  (LF)  |
            //            |(CR)    ^(LF)
            //            |        |
            //          (CR2)-->---

            if (i == 10) {
                // LF
                switch(state) {
                    case STATE_CHARACTER:
                        state = STATE_FIRST_LF;
                        break;
                    case STATE_FIRST_CR:
                        state = STATE_FIRST_LF;
                        break;
                    case STATE_FIRST_LF:
                    case STATE_SECOND_CR:
                        state = STATE_HEADER_END;
                        break;
                }

            } else if (i == 13) {
                // CR
                switch(state) {
                    case STATE_CHARACTER:
                        state = STATE_FIRST_CR;
                        break;
                    case STATE_FIRST_CR:
                        state = STATE_HEADER_END;
                        break;
                    case STATE_FIRST_LF:
                        state = STATE_SECOND_CR;
                        break;
                }

            } else {
                state = STATE_CHARACTER;
            }

            return i;
        }
    }

}
