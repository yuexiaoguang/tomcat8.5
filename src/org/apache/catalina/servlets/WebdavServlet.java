package org.apache.catalina.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Stack;
import java.util.TimeZone;
import java.util.Vector;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.catalina.WebResource;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.util.ConcurrentDateFormat;
import org.apache.catalina.util.DOMWriter;
import org.apache.catalina.util.URLEncoder;
import org.apache.catalina.util.XMLWriter;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.RequestUtil;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;
import org.apache.tomcat.util.security.MD5Encoder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * 增加了2级支持WebDAV. 
 * DefaultServlet处理的所有的基础HTTP请求. WebDAVServlet不能作为默认的servlet (即映射到 '/'), 因为在这个配置中它不能工作.
 * <p>
 * 匹配一个子路径 (即<code>/webdav/*</code>到这个servlet具有在该子路径下重新安装整个Web应用程序的效果, 使用 WebDAV
 * 访问所有资源. <code>WEB-INF</code>和<code>META-INF</code>目录在重新安装的资源树中受保护.
 * <p>
 * 启用上下文的WebDAV，添加下列内容到 web.xml:
 * <pre>
 * &lt;servlet&gt;
 *  &lt;servlet-name&gt;webdav&lt;/servlet-name&gt;
 *  &lt;servlet-class&gt;org.apache.catalina.servlets.WebdavServlet&lt;/servlet-class&gt;
 *    &lt;init-param&gt;
 *      &lt;param-name&gt;debug&lt;/param-name&gt;
 *      &lt;param-value&gt;0&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *      &lt;param-name&gt;listings&lt;/param-name&gt;
 *      &lt;param-value&gt;false&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *  &lt;/servlet&gt;
 *  &lt;servlet-mapping&gt;
 *    &lt;servlet-name&gt;webdav&lt;/servlet-name&gt;
 *    &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 *  &lt;/servlet-mapping&gt;
 * </pre>
 * 这将使只读访问成为可能. 允许读写访问添加:
 * <pre>
 *  &lt;init-param&gt;
 *    &lt;param-name&gt;readonly&lt;/param-name&gt;
 *    &lt;param-value&gt;false&lt;/param-value&gt;
 *  &lt;/init-param&gt;
 * </pre>
 * 通过不同的URL使内容可编辑, 使用以下映射:
 * <pre>
 *  &lt;servlet-mapping&gt;
 *    &lt;servlet-name&gt;webdav&lt;/servlet-name&gt;
 *    &lt;url-pattern&gt;/webdavedit/*&lt;/url-pattern&gt;
 *  &lt;/servlet-mapping&gt;
 * </pre>
 * 通过WebDAV默认访问 /WEB-INF 和 META-INF无效. 启用对这些URL的访问, 使用添加:
 * <pre>
 *  &lt;init-param&gt;
 *    &lt;param-name&gt;allowSpecialPaths&lt;/param-name&gt;
 *    &lt;param-value&gt;true&lt;/param-value&gt;
 *  &lt;/init-param&gt;
 * </pre>
 * 不要忘记适当地保护对编辑URL的访问, 尤其是使用allowSpecialPaths 时. 使用上面的映射配置, 上下文将像以前一样可供普通用户使用.
 * 具有必要访问权限的用户将能够编辑可用的内容, 通过 http://host:port/context/content 使用
 * http://host:port/context/webdavedit/content
 */
public class WebdavServlet extends DefaultServlet {

    private static final long serialVersionUID = 1L;


    // -------------------------------------------------------------- Constants

    private static final URLEncoder URL_ENCODER_XML;
    static {
        URL_ENCODER_XML = (URLEncoder) URLEncoder.DEFAULT.clone();
        // 从安全字符集删除 '&', 因为虽然它允许在URI路径中, 它不允许在XML和编码中，这是解决这个问题的简单方法.
        URL_ENCODER_XML.removeSafeCharacter('&');
    }

    private static final String METHOD_PROPFIND = "PROPFIND";
    private static final String METHOD_PROPPATCH = "PROPPATCH";
    private static final String METHOD_MKCOL = "MKCOL";
    private static final String METHOD_COPY = "COPY";
    private static final String METHOD_MOVE = "MOVE";
    private static final String METHOD_LOCK = "LOCK";
    private static final String METHOD_UNLOCK = "UNLOCK";


    /**
     * PROPFIND - 指定属性掩码.
     */
    private static final int FIND_BY_PROPERTY = 0;


    /**
     * PROPFIND - 显示所有属性.
     */
    private static final int FIND_ALL_PROP = 1;


    /**
     * PROPFIND - 返回属性名称.
     */
    private static final int FIND_PROPERTY_NAMES = 2;


    /**
     * 创建一个新锁.
     */
    private static final int LOCK_CREATION = 0;


    /**
     * 刷新锁.
     */
    private static final int LOCK_REFRESH = 1;


    /**
     * 默认锁定超时值.
     */
    private static final int DEFAULT_TIMEOUT = 3600;


    /**
     * 最大锁定超时.
     */
    private static final int MAX_TIMEOUT = 604800;


    /**
     * 默认命名空间.
     */
    protected static final String DEFAULT_NAMESPACE = "DAV:";


    /**
     * 创建日期ISO表示的简单日期格式 (部分).
     */
    protected static final ConcurrentDateFormat creationDateFormat =
        new ConcurrentDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US,
                TimeZone.getTimeZone("GMT"));


    // ----------------------------------------------------- Instance Variables

    /**
     * 锁资源库放在单个资源上.
     * <p>
     * Key : path <br>
     * Value : LockInfo
     */
    private final Hashtable<String,LockInfo> resourceLocks = new Hashtable<>();


    /**
     * 锁空资源库.
     * <p>
     * Key : 包含空锁资源的集合的路径<br>
     * Value : 作为集合成员的空锁资源的向量. vector的每个元素都是与空锁资源相关联的路径.
     */
    private final Hashtable<String,Vector<String>> lockNullResources =
        new Hashtable<>();


    /**
     * 遗传锁的Vector.
     * <p>
     * Key : path <br>
     * Value : LockInfo
     */
    private final Vector<LockInfo> collectionLocks = new Vector<>();


    /**
     * 用于生成合理安全锁定ID的机密信息.
     */
    private String secret = "catalina";


    /**
     * spec中的默认深度是无限的. 默认情况下将深度限制为3，因为无限深度会使操作非常昂贵.
     */
    private int maxDepth = 3;


    /**
     * 是否允许通过WebDAV访问特殊路径(/WEB-INF 和 /META-INF)?
     */
    private boolean allowSpecialPaths = false;


    // --------------------------------------------------------- Public Methods


    /**
     * 初始化这个servlet.
     */
    @Override
    public void init()
        throws ServletException {

        super.init();

        if (getServletConfig().getInitParameter("secret") != null)
            secret = getServletConfig().getInitParameter("secret");

        if (getServletConfig().getInitParameter("maxDepth") != null)
            maxDepth = Integer.parseInt(
                    getServletConfig().getInitParameter("maxDepth"));

        if (getServletConfig().getInitParameter("allowSpecialPaths") != null)
            allowSpecialPaths = Boolean.parseBoolean(
                    getServletConfig().getInitParameter("allowSpecialPaths"));
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 返回JAXP文档生成器实例.
     * 
     * @return the document builder
     * @throws ServletException 文档生成器创建失败 (包装的<code>ParserConfigurationException</code>异常)
     */
    protected DocumentBuilder getDocumentBuilder()
        throws ServletException {
        DocumentBuilder documentBuilder = null;
        DocumentBuilderFactory documentBuilderFactory = null;
        try {
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            documentBuilderFactory.setExpandEntityReferences(false);
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
            documentBuilder.setEntityResolver(
                    new WebdavResolver(this.getServletContext()));
        } catch(ParserConfigurationException e) {
            throw new ServletException
                (sm.getString("webdavservlet.jaxpfailed"));
        }
        return documentBuilder;
    }


    /**
     * 处理特殊WebDAV方法.
     */
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        final String path = getRelativePath(req);

        // 错误页面检查需要在特殊路径检查之前进行，因为自定义错误页面通常位于WEB-INF下，因此它们不能直接访问.
        if (req.getDispatcherType() == DispatcherType.ERROR) {
            doGet(req, resp);
            return;
        }

        // 阻止访问特殊的子目录.
        // DefaultServlet假定它从Web应用程序的根目录提供服务资源，并且不添加任何特殊路径保护
        // WebdavServlet 在新的路径下重新安装webapp, 所以这个检查对所有方法都是必要的(包括 GET).
        if (isSpecialPath(path)) {
            resp.sendError(WebdavStatus.SC_NOT_FOUND);
            return;
        }

        final String method = req.getMethod();

        if (debug > 0) {
            log("[" + method + "] " + path);
        }

        if (method.equals(METHOD_PROPFIND)) {
            doPropfind(req, resp);
        } else if (method.equals(METHOD_PROPPATCH)) {
            doProppatch(req, resp);
        } else if (method.equals(METHOD_MKCOL)) {
            doMkcol(req, resp);
        } else if (method.equals(METHOD_COPY)) {
            doCopy(req, resp);
        } else if (method.equals(METHOD_MOVE)) {
            doMove(req, resp);
        } else if (method.equals(METHOD_LOCK)) {
            doLock(req, resp);
        } else if (method.equals(METHOD_UNLOCK)) {
            doUnlock(req, resp);
        } else {
            // DefaultServlet processing
            super.service(req, resp);
        }

    }


    /**
     * 检查给定的路径是否指向<code>WEB-INF</code>或<code>META-INF</code>目录下的资源.
     * 
     * @param path 正在访问资源的完整路径
     * @return <code>true</code>如果指定的资源位于特殊路径下
     */
    private final boolean isSpecialPath(final String path) {
        return !allowSpecialPaths && (
                path.toUpperCase(Locale.ENGLISH).startsWith("/WEB-INF") ||
                path.toUpperCase(Locale.ENGLISH).startsWith("/META-INF"));
    }


    @Override
    protected boolean checkIfHeaders(HttpServletRequest request,
                                     HttpServletResponse response,
                                     WebResource resource)
        throws IOException {

        if (!super.checkIfHeaders(request, response, resource))
            return false;

        // TODO : Checking the WebDAV If header
        return true;
    }


    /**
     * URL rewriter.
     *
     * @param path 必须重写的路径
     * @return the rewritten path
     */
    @Override
    protected String rewriteUrl(String path) {
        return URL_ENCODER_XML.encode(path, StandardCharsets.UTF_8);
    }


    /**
     * 重写DefaultServlet实现类并只使用 PathInfo.
     * 如果ServletPath non-null, 因为 WebDAV servlet已经映射到一个 url而不是 /*, 在不同于普通视图的url上配置编辑.
     *
     * @param request 正在处理的servlet请求
     */
    @Override
    protected String getRelativePath(HttpServletRequest request) {
        return getRelativePath(request, false);
    }

    @Override
    protected String getRelativePath(HttpServletRequest request, boolean allowEmptyPath) {
        String pathInfo;

        if (request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) != null) {
            // For includes, get the info from the attributes
            pathInfo = (String) request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
        } else {
            pathInfo = request.getPathInfo();
        }

        StringBuilder result = new StringBuilder();
        if (pathInfo != null) {
            result.append(pathInfo);
        }
        if (result.length() == 0) {
            result.append('/');
        }

        return result.toString();
    }


    /**
     * 确定标准目录GET列表的前缀.
     */
    @Override
    protected String getPathPrefix(final HttpServletRequest request) {
        // 重复列表路径中servlet路径(e.g. /webdav/)
        String contextPath = request.getContextPath();
        if (request.getServletPath() !=  null) {
            contextPath = contextPath + request.getServletPath();
        }
        return contextPath;
    }


    /**
     * OPTIONS Method.
     *
     * @param req The Servlet request
     * @param resp The Servlet response
     * 
     * @throws ServletException 如果发生错误
     * @throws IOException 如果发生IO错误
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        resp.addHeader("DAV", "1,2");

        StringBuilder methodsAllowed = determineMethodsAllowed(req);

        resp.addHeader("Allow", methodsAllowed.toString());
        resp.addHeader("MS-Author-Via", "DAV");
    }


    /**
     * PROPFIND Method.
     * 
     * @param req The Servlet request
     * @param resp The Servlet response
     * 
     * @throws ServletException 如果发生错误
     * @throws IOException 如果发生IO错误
     */
    protected void doPropfind(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (!listings) {
            // Get allowed methods
            StringBuilder methodsAllowed = determineMethodsAllowed(req);

            resp.addHeader("Allow", methodsAllowed.toString());
            resp.sendError(WebdavStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        String path = getRelativePath(req);
        if (path.length() > 1 && path.endsWith("/"))
            path = path.substring(0, path.length() - 1);

        // 要显示的属性.
        Vector<String> properties = null;
        // Propfind depth
        int depth = maxDepth;
        // Propfind type
        int type = FIND_ALL_PROP;

        String depthStr = req.getHeader("Depth");

        if (depthStr == null) {
            depth = maxDepth;
        } else {
            if (depthStr.equals("0")) {
                depth = 0;
            } else if (depthStr.equals("1")) {
                depth = 1;
            } else if (depthStr.equals("infinity")) {
                depth = maxDepth;
            }
        }

        Node propNode = null;

        if (req.getContentLengthLong() > 0) {
            DocumentBuilder documentBuilder = getDocumentBuilder();

            try {
                Document document = documentBuilder.parse
                    (new InputSource(req.getInputStream()));

                // Get the root element of the document
                Element rootElement = document.getDocumentElement();
                NodeList childList = rootElement.getChildNodes();

                for (int i=0; i < childList.getLength(); i++) {
                    Node currentNode = childList.item(i);
                    switch (currentNode.getNodeType()) {
                    case Node.TEXT_NODE:
                        break;
                    case Node.ELEMENT_NODE:
                        if (currentNode.getNodeName().endsWith("prop")) {
                            type = FIND_BY_PROPERTY;
                            propNode = currentNode;
                        }
                        if (currentNode.getNodeName().endsWith("propname")) {
                            type = FIND_PROPERTY_NAMES;
                        }
                        if (currentNode.getNodeName().endsWith("allprop")) {
                            type = FIND_ALL_PROP;
                        }
                        break;
                    }
                }
            } catch (SAXException e) {
                // Something went wrong - bad request
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                return;
            } catch (IOException e) {
                // Something went wrong - bad request
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                return;
            }
        }

        if (type == FIND_BY_PROPERTY) {
            properties = new Vector<>();
            // propNode must be non-null if type == FIND_BY_PROPERTY
            @SuppressWarnings("null")
            NodeList childList = propNode.getChildNodes();

            for (int i=0; i < childList.getLength(); i++) {
                Node currentNode = childList.item(i);
                switch (currentNode.getNodeType()) {
                case Node.TEXT_NODE:
                    break;
                case Node.ELEMENT_NODE:
                    String nodeName = currentNode.getNodeName();
                    String propertyName = null;
                    if (nodeName.indexOf(':') != -1) {
                        propertyName = nodeName.substring
                            (nodeName.indexOf(':') + 1);
                    } else {
                        propertyName = nodeName;
                    }
                    // href is a live property which is handled differently
                    properties.addElement(propertyName);
                    break;
                }
            }

        }

        WebResource resource = resources.getResource(path);

        if (!resource.exists()) {
            int slash = path.lastIndexOf('/');
            if (slash != -1) {
                String parentPath = path.substring(0, slash);
                Vector<String> currentLockNullResources =
                    lockNullResources.get(parentPath);
                if (currentLockNullResources != null) {
                    Enumeration<String> lockNullResourcesList =
                        currentLockNullResources.elements();
                    while (lockNullResourcesList.hasMoreElements()) {
                        String lockNullPath =
                            lockNullResourcesList.nextElement();
                        if (lockNullPath.equals(path)) {
                            resp.setStatus(WebdavStatus.SC_MULTI_STATUS);
                            resp.setContentType("text/xml; charset=UTF-8");
                            // Create multistatus object
                            XMLWriter generatedXML =
                                new XMLWriter(resp.getWriter());
                            generatedXML.writeXMLHeader();
                            generatedXML.writeElement("D", DEFAULT_NAMESPACE,
                                    "multistatus", XMLWriter.OPENING);
                            parseLockNullProperties
                                (req, generatedXML, lockNullPath, type,
                                 properties);
                            generatedXML.writeElement("D", "multistatus",
                                    XMLWriter.CLOSING);
                            generatedXML.sendData();
                            return;
                        }
                    }
                }
            }
        }

        if (!resource.exists()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, path);
            return;
        }

        resp.setStatus(WebdavStatus.SC_MULTI_STATUS);

        resp.setContentType("text/xml; charset=UTF-8");

        // Create multistatus object
        XMLWriter generatedXML = new XMLWriter(resp.getWriter());
        generatedXML.writeXMLHeader();

        generatedXML.writeElement("D", DEFAULT_NAMESPACE, "multistatus",
                XMLWriter.OPENING);

        if (depth == 0) {
            parseProperties(req, generatedXML, path, type,
                            properties);
        } else {
            // 堆栈始终包含当前级别的对象
            Stack<String> stack = new Stack<>();
            stack.push(path);

            // 一层以下的对象的堆栈
            Stack<String> stackBelow = new Stack<>();

            while ((!stack.isEmpty()) && (depth >= 0)) {

                String currentPath = stack.pop();
                parseProperties(req, generatedXML, currentPath,
                                type, properties);

                resource = resources.getResource(currentPath);

                if (resource.isDirectory() && (depth > 0)) {

                    String[] entries = resources.list(currentPath);
                    for (String entry : entries) {
                        String newPath = currentPath;
                        if (!(newPath.endsWith("/")))
                                newPath += "/";
                        newPath += entry;
                        stackBelow.push(newPath);
                    }

                    // 显示该集合中存在的lock-null资源
                    String lockPath = currentPath;
                    if (lockPath.endsWith("/"))
                        lockPath =
                            lockPath.substring(0, lockPath.length() - 1);
                    Vector<String> currentLockNullResources =
                        lockNullResources.get(lockPath);
                    if (currentLockNullResources != null) {
                        Enumeration<String> lockNullResourcesList =
                            currentLockNullResources.elements();
                        while (lockNullResourcesList.hasMoreElements()) {
                            String lockNullPath =
                                lockNullResourcesList.nextElement();
                            parseLockNullProperties
                                (req, generatedXML, lockNullPath, type,
                                 properties);
                        }
                    }

                }

                if (stack.isEmpty()) {
                    depth--;
                    stack = stackBelow;
                    stackBelow = new Stack<>();
                }

                generatedXML.sendData();
            }
        }
        generatedXML.writeElement("D", "multistatus", XMLWriter.CLOSING);

        generatedXML.sendData();
    }


    /**
     * PROPPATCH Method.
     * 
     * @param req The Servlet request
     * @param resp The Servlet response
     * 
     * @throws IOException 如果发生IO错误
     */
    protected void doProppatch(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);

    }


    /**
     * MKCOL Method.
     * 
     * @param req The Servlet request
     * @param resp The Servlet response
     * 
     * @throws ServletException 如果发生错误
     * @throws IOException 如果发生IO错误
     */
    protected void doMkcol(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        String path = getRelativePath(req);

        WebResource resource = resources.getResource(path);

        // Can't create a collection if a resource already exists at the given
        // path
        if (resource.exists()) {
            // Get allowed methods
            StringBuilder methodsAllowed = determineMethodsAllowed(req);

            resp.addHeader("Allow", methodsAllowed.toString());

            resp.sendError(WebdavStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (req.getContentLengthLong() > 0) {
            DocumentBuilder documentBuilder = getDocumentBuilder();
            try {
                // Document document =
                documentBuilder.parse(new InputSource(req.getInputStream()));
                // TODO : Process this request body
                resp.sendError(WebdavStatus.SC_NOT_IMPLEMENTED);
                return;

            } catch(SAXException saxe) {
                // Parse error - assume invalid content
                resp.sendError(WebdavStatus.SC_UNSUPPORTED_MEDIA_TYPE);
                return;
            }
        }

        if (resources.mkdir(path)) {
            resp.setStatus(WebdavStatus.SC_CREATED);
            // Removing any lock-null resource which would be present
            lockNullResources.remove(path);
        } else {
            resp.sendError(WebdavStatus.SC_CONFLICT,
                           WebdavStatus.getStatusText
                           (WebdavStatus.SC_CONFLICT));
        }
    }


    /**
     * DELETE Method.
     * @param req The Servlet request
     * @param resp The Servlet response
     * @throws ServletException If an error occurs
     * @throws IOException If an IO error occurs
     */
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }
        deleteResource(req, resp);
    }


    /**
     * 处理指定资源的PUT请求.
     *
     * @param req The servlet request we are processing
     * @param resp The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        super.doPut(req, resp);

        String path = getRelativePath(req);

        // Removing any lock-null resource which would be present
        lockNullResources.remove(path);
    }

    /**
     * COPY Method.
     * @param req The Servlet request
     * @param resp The Servlet response
     * @throws IOException If an IO error occurs
     */
    protected void doCopy(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        copyResource(req, resp);

    }


    /**
     * MOVE Method.
     * @param req The Servlet request
     * @param resp The Servlet response
     * @throws IOException If an IO error occurs
     */
    protected void doMove(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        String path = getRelativePath(req);

        if (copyResource(req, resp)) {
            deleteResource(path, req, resp, false);
        }

    }


    /**
     * LOCK Method.
     * @param req The Servlet request
     * @param resp The Servlet response
     * @throws ServletException If an error occurs
     * @throws IOException If an IO error occurs
     */
    protected void doLock(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        LockInfo lock = new LockInfo();

        // Parsing lock request

        // Parsing depth header

        String depthStr = req.getHeader("Depth");

        if (depthStr == null) {
            lock.depth = maxDepth;
        } else {
            if (depthStr.equals("0")) {
                lock.depth = 0;
            } else {
                lock.depth = maxDepth;
            }
        }

        // Parsing timeout header

        int lockDuration = DEFAULT_TIMEOUT;
        String lockDurationStr = req.getHeader("Timeout");
        if (lockDurationStr == null) {
            lockDuration = DEFAULT_TIMEOUT;
        } else {
            int commaPos = lockDurationStr.indexOf(',');
            // If multiple timeouts, just use the first
            if (commaPos != -1) {
                lockDurationStr = lockDurationStr.substring(0,commaPos);
            }
            if (lockDurationStr.startsWith("Second-")) {
                lockDuration = Integer.parseInt(lockDurationStr.substring(7));
            } else {
                if (lockDurationStr.equalsIgnoreCase("infinity")) {
                    lockDuration = MAX_TIMEOUT;
                } else {
                    try {
                        lockDuration = Integer.parseInt(lockDurationStr);
                    } catch (NumberFormatException e) {
                        lockDuration = MAX_TIMEOUT;
                    }
                }
            }
            if (lockDuration == 0) {
                lockDuration = DEFAULT_TIMEOUT;
            }
            if (lockDuration > MAX_TIMEOUT) {
                lockDuration = MAX_TIMEOUT;
            }
        }
        lock.expiresAt = System.currentTimeMillis() + (lockDuration * 1000);

        int lockRequestType = LOCK_CREATION;

        Node lockInfoNode = null;

        DocumentBuilder documentBuilder = getDocumentBuilder();

        try {
            Document document = documentBuilder.parse(new InputSource
                (req.getInputStream()));

            // Get the root element of the document
            Element rootElement = document.getDocumentElement();
            lockInfoNode = rootElement;
        } catch (IOException e) {
            lockRequestType = LOCK_REFRESH;
        } catch (SAXException e) {
            lockRequestType = LOCK_REFRESH;
        }

        if (lockInfoNode != null) {

            // Reading lock information

            NodeList childList = lockInfoNode.getChildNodes();
            StringWriter strWriter = null;
            DOMWriter domWriter = null;

            Node lockScopeNode = null;
            Node lockTypeNode = null;
            Node lockOwnerNode = null;

            for (int i=0; i < childList.getLength(); i++) {
                Node currentNode = childList.item(i);
                switch (currentNode.getNodeType()) {
                case Node.TEXT_NODE:
                    break;
                case Node.ELEMENT_NODE:
                    String nodeName = currentNode.getNodeName();
                    if (nodeName.endsWith("lockscope")) {
                        lockScopeNode = currentNode;
                    }
                    if (nodeName.endsWith("locktype")) {
                        lockTypeNode = currentNode;
                    }
                    if (nodeName.endsWith("owner")) {
                        lockOwnerNode = currentNode;
                    }
                    break;
                }
            }

            if (lockScopeNode != null) {

                childList = lockScopeNode.getChildNodes();
                for (int i=0; i < childList.getLength(); i++) {
                    Node currentNode = childList.item(i);
                    switch (currentNode.getNodeType()) {
                    case Node.TEXT_NODE:
                        break;
                    case Node.ELEMENT_NODE:
                        String tempScope = currentNode.getNodeName();
                        if (tempScope.indexOf(':') != -1) {
                            lock.scope = tempScope.substring
                                (tempScope.indexOf(':') + 1);
                        } else {
                            lock.scope = tempScope;
                        }
                        break;
                    }
                }

                if (lock.scope == null) {
                    // Bad request
                    resp.setStatus(WebdavStatus.SC_BAD_REQUEST);
                }

            } else {
                // Bad request
                resp.setStatus(WebdavStatus.SC_BAD_REQUEST);
            }

            if (lockTypeNode != null) {

                childList = lockTypeNode.getChildNodes();
                for (int i=0; i < childList.getLength(); i++) {
                    Node currentNode = childList.item(i);
                    switch (currentNode.getNodeType()) {
                    case Node.TEXT_NODE:
                        break;
                    case Node.ELEMENT_NODE:
                        String tempType = currentNode.getNodeName();
                        if (tempType.indexOf(':') != -1) {
                            lock.type =
                                tempType.substring(tempType.indexOf(':') + 1);
                        } else {
                            lock.type = tempType;
                        }
                        break;
                    }
                }

                if (lock.type == null) {
                    // Bad request
                    resp.setStatus(WebdavStatus.SC_BAD_REQUEST);
                }

            } else {
                // Bad request
                resp.setStatus(WebdavStatus.SC_BAD_REQUEST);
            }

            if (lockOwnerNode != null) {

                childList = lockOwnerNode.getChildNodes();
                for (int i=0; i < childList.getLength(); i++) {
                    Node currentNode = childList.item(i);
                    switch (currentNode.getNodeType()) {
                    case Node.TEXT_NODE:
                        lock.owner += currentNode.getNodeValue();
                        break;
                    case Node.ELEMENT_NODE:
                        strWriter = new StringWriter();
                        domWriter = new DOMWriter(strWriter);
                        domWriter.print(currentNode);
                        lock.owner += strWriter.toString();
                        break;
                    }
                }

                if (lock.owner == null) {
                    // Bad request
                    resp.setStatus(WebdavStatus.SC_BAD_REQUEST);
                }

            } else {
                lock.owner = "";
            }

        }

        String path = getRelativePath(req);

        lock.path = path;

        WebResource resource = resources.getResource(path);

        Enumeration<LockInfo> locksList = null;

        if (lockRequestType == LOCK_CREATION) {

            // Generating lock id
            String lockTokenStr = req.getServletPath() + "-" + lock.type + "-"
                + lock.scope + "-" + req.getUserPrincipal() + "-"
                + lock.depth + "-" + lock.owner + "-" + lock.tokens + "-"
                + lock.expiresAt + "-" + System.currentTimeMillis() + "-"
                + secret;
            String lockToken = MD5Encoder.encode(ConcurrentMessageDigest.digestMD5(
                    lockTokenStr.getBytes(StandardCharsets.ISO_8859_1)));

            if (resource.isDirectory() && lock.depth == maxDepth) {

                // Locking a collection (and all its member resources)

                // 检查此集合的子资源是否已被锁定
                Vector<String> lockPaths = new Vector<>();
                locksList = collectionLocks.elements();
                while (locksList.hasMoreElements()) {
                    LockInfo currentLock = locksList.nextElement();
                    if (currentLock.hasExpired()) {
                        resourceLocks.remove(currentLock.path);
                        continue;
                    }
                    if ( (currentLock.path.startsWith(lock.path)) &&
                         ((currentLock.isExclusive()) ||
                          (lock.isExclusive())) ) {
                        // A child collection of this collection is locked
                        lockPaths.addElement(currentLock.path);
                    }
                }
                locksList = resourceLocks.elements();
                while (locksList.hasMoreElements()) {
                    LockInfo currentLock = locksList.nextElement();
                    if (currentLock.hasExpired()) {
                        resourceLocks.remove(currentLock.path);
                        continue;
                    }
                    if ( (currentLock.path.startsWith(lock.path)) &&
                         ((currentLock.isExclusive()) ||
                          (lock.isExclusive())) ) {
                        // A child resource of this collection is locked
                        lockPaths.addElement(currentLock.path);
                    }
                }

                if (!lockPaths.isEmpty()) {

                    // One of the child paths was locked
                    // 生成一个多态错误报告

                    Enumeration<String> lockPathsList = lockPaths.elements();

                    resp.setStatus(WebdavStatus.SC_CONFLICT);

                    XMLWriter generatedXML = new XMLWriter();
                    generatedXML.writeXMLHeader();

                    generatedXML.writeElement("D", DEFAULT_NAMESPACE,
                            "multistatus", XMLWriter.OPENING);

                    while (lockPathsList.hasMoreElements()) {
                        generatedXML.writeElement("D", "response",
                                XMLWriter.OPENING);
                        generatedXML.writeElement("D", "href",
                                XMLWriter.OPENING);
                        generatedXML.writeText(lockPathsList.nextElement());
                        generatedXML.writeElement("D", "href",
                                XMLWriter.CLOSING);
                        generatedXML.writeElement("D", "status",
                                XMLWriter.OPENING);
                        generatedXML
                            .writeText("HTTP/1.1 " + WebdavStatus.SC_LOCKED
                                       + " " + WebdavStatus
                                       .getStatusText(WebdavStatus.SC_LOCKED));
                        generatedXML.writeElement("D", "status",
                                XMLWriter.CLOSING);

                        generatedXML.writeElement("D", "response",
                                XMLWriter.CLOSING);
                    }

                    generatedXML.writeElement("D", "multistatus",
                            XMLWriter.CLOSING);

                    Writer writer = resp.getWriter();
                    writer.write(generatedXML.toString());
                    writer.close();

                    return;

                }

                boolean addLock = true;

                // 检查此路径上是否已有共享锁
                locksList = collectionLocks.elements();
                while (locksList.hasMoreElements()) {

                    LockInfo currentLock = locksList.nextElement();
                    if (currentLock.path.equals(lock.path)) {

                        if (currentLock.isExclusive()) {
                            resp.sendError(WebdavStatus.SC_LOCKED);
                            return;
                        } else {
                            if (lock.isExclusive()) {
                                resp.sendError(WebdavStatus.SC_LOCKED);
                                return;
                            }
                        }

                        currentLock.tokens.addElement(lockToken);
                        lock = currentLock;
                        addLock = false;

                    }

                }

                if (addLock) {
                    lock.tokens.addElement(lockToken);
                    collectionLocks.addElement(lock);
                }

            } else {

                // 锁定一个资源

                // 检索该资源上已有的锁
                LockInfo presentLock = resourceLocks.get(lock.path);
                if (presentLock != null) {

                    if ((presentLock.isExclusive()) || (lock.isExclusive())) {
                        // If either lock is exclusive, the lock can't be
                        // granted
                        resp.sendError(WebdavStatus.SC_PRECONDITION_FAILED);
                        return;
                    } else {
                        presentLock.tokens.addElement(lockToken);
                        lock = presentLock;
                    }

                } else {

                    lock.tokens.addElement(lockToken);
                    resourceLocks.put(lock.path, lock);

                    // Checking if a resource exists at this path
                    if (!resource.exists()) {

                        // "Creating" a lock-null resource
                        int slash = lock.path.lastIndexOf('/');
                        String parentPath = lock.path.substring(0, slash);

                        Vector<String> lockNulls =
                            lockNullResources.get(parentPath);
                        if (lockNulls == null) {
                            lockNulls = new Vector<>();
                            lockNullResources.put(parentPath, lockNulls);
                        }

                        lockNulls.addElement(lock.path);

                    }
                    // Add the Lock-Token header as by RFC 2518 8.10.1
                    // - only do this for newly created locks
                    resp.addHeader("Lock-Token", "<opaquelocktoken:"
                                   + lockToken + ">");
                }

            }

        }

        if (lockRequestType == LOCK_REFRESH) {

            String ifHeader = req.getHeader("If");
            if (ifHeader == null)
                ifHeader = "";

            // Checking resource locks

            LockInfo toRenew = resourceLocks.get(path);
            Enumeration<String> tokenList = null;

            if (toRenew != null) {
                // At least one of the tokens of the locks must have been given
                tokenList = toRenew.tokens.elements();
                while (tokenList.hasMoreElements()) {
                    String token = tokenList.nextElement();
                    if (ifHeader.indexOf(token) != -1) {
                        toRenew.expiresAt = lock.expiresAt;
                        lock = toRenew;
                    }
                }
            }

            // Checking inheritable collection locks

            Enumeration<LockInfo> collectionLocksList =
                collectionLocks.elements();
            while (collectionLocksList.hasMoreElements()) {
                toRenew = collectionLocksList.nextElement();
                if (path.equals(toRenew.path)) {

                    tokenList = toRenew.tokens.elements();
                    while (tokenList.hasMoreElements()) {
                        String token = tokenList.nextElement();
                        if (ifHeader.indexOf(token) != -1) {
                            toRenew.expiresAt = lock.expiresAt;
                            lock = toRenew;
                        }
                    }

                }
            }

        }

        // 设置状态, 然后生成包含锁定信息的XML响应
        XMLWriter generatedXML = new XMLWriter();
        generatedXML.writeXMLHeader();
        generatedXML.writeElement("D", DEFAULT_NAMESPACE, "prop",
                XMLWriter.OPENING);

        generatedXML.writeElement("D", "lockdiscovery", XMLWriter.OPENING);

        lock.toXML(generatedXML);

        generatedXML.writeElement("D", "lockdiscovery", XMLWriter.CLOSING);

        generatedXML.writeElement("D", "prop", XMLWriter.CLOSING);

        resp.setStatus(WebdavStatus.SC_OK);
        resp.setContentType("text/xml; charset=UTF-8");
        Writer writer = resp.getWriter();
        writer.write(generatedXML.toString());
        writer.close();
    }


    /**
     * UNLOCK Method.
     * @param req The Servlet request
     * @param resp The Servlet response
     * @throws IOException If an IO error occurs
     */
    protected void doUnlock(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        String path = getRelativePath(req);

        String lockTokenHeader = req.getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        // Checking resource locks

        LockInfo lock = resourceLocks.get(path);
        Enumeration<String> tokenList = null;
        if (lock != null) {

            // 至少必须给出一个锁的令牌

            tokenList = lock.tokens.elements();
            while (tokenList.hasMoreElements()) {
                String token = tokenList.nextElement();
                if (lockTokenHeader.indexOf(token) != -1) {
                    lock.tokens.removeElement(token);
                }
            }

            if (lock.tokens.isEmpty()) {
                resourceLocks.remove(path);
                // Removing any lock-null resource which would be present
                lockNullResources.remove(path);
            }

        }

        // Checking inheritable collection locks

        Enumeration<LockInfo> collectionLocksList = collectionLocks.elements();
        while (collectionLocksList.hasMoreElements()) {
            lock = collectionLocksList.nextElement();
            if (path.equals(lock.path)) {

                tokenList = lock.tokens.elements();
                while (tokenList.hasMoreElements()) {
                    String token = tokenList.nextElement();
                    if (lockTokenHeader.indexOf(token) != -1) {
                        lock.tokens.removeElement(token);
                        break;
                    }
                }

                if (lock.tokens.isEmpty()) {
                    collectionLocks.removeElement(lock);
                    // Removing any lock-null resource which would be present
                    lockNullResources.remove(path);
                }

            }
        }

        resp.setStatus(WebdavStatus.SC_NO_CONTENT);

    }

    // -------------------------------------------------------- Private Methods

    /**
     * 检查当前资源是否已写入锁定状态. 该方法将查看"If"标头，以确保客户端给出了适当的锁定令牌.
     *
     * @param req Servlet request
     * @return <code>true</code> if the resource is locked (在资源上存在的至少一个非共享锁中没有找到适当的锁定令牌).
     */
    private boolean isLocked(HttpServletRequest req) {

        String path = getRelativePath(req);

        String ifHeader = req.getHeader("If");
        if (ifHeader == null)
            ifHeader = "";

        String lockTokenHeader = req.getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        return isLocked(path, ifHeader + lockTokenHeader);
    }


    /**
     * 检查当前资源状态是否已经写锁定.
     *
     * @param path Path of the resource
     * @param ifHeader "If" HTTP header which was included in the request
     * @return <code>true</code> if the resource is locked (在资源上存在的至少一个非共享锁中没有找到适当的锁定令牌).
     */
    private boolean isLocked(String path, String ifHeader) {

        // Checking resource locks

        LockInfo lock = resourceLocks.get(path);
        Enumeration<String> tokenList = null;
        if ((lock != null) && (lock.hasExpired())) {
            resourceLocks.remove(path);
        } else if (lock != null) {

            // At least one of the tokens of the locks must have been given

            tokenList = lock.tokens.elements();
            boolean tokenMatch = false;
            while (tokenList.hasMoreElements()) {
                String token = tokenList.nextElement();
                if (ifHeader.indexOf(token) != -1) {
                    tokenMatch = true;
                    break;
                }
            }
            if (!tokenMatch)
                return true;

        }

        // Checking inheritable collection locks

        Enumeration<LockInfo> collectionLocksList = collectionLocks.elements();
        while (collectionLocksList.hasMoreElements()) {
            lock = collectionLocksList.nextElement();
            if (lock.hasExpired()) {
                collectionLocks.removeElement(lock);
            } else if (path.startsWith(lock.path)) {

                tokenList = lock.tokens.elements();
                boolean tokenMatch = false;
                while (tokenList.hasMoreElements()) {
                    String token = tokenList.nextElement();
                    if (ifHeader.indexOf(token) != -1) {
                        tokenMatch = true;
                        break;
                    }
                }
                if (!tokenMatch)
                    return true;

            }
        }
        return false;
    }


    /**
     * 复制资源.
     *
     * @param req Servlet request
     * @param resp Servlet response
     * @return boolean true if the copy is successful
     * @throws IOException If an IO error occurs
     */
    private boolean copyResource(HttpServletRequest req,
                                 HttpServletResponse resp)
            throws IOException {

        // Parsing destination header

        String destinationPath = req.getHeader("Destination");

        if (destinationPath == null) {
            resp.sendError(WebdavStatus.SC_BAD_REQUEST);
            return false;
        }

        // Remove url encoding from destination
        destinationPath = UDecoder.URLDecode(destinationPath, StandardCharsets.UTF_8);

        int protocolIndex = destinationPath.indexOf("://");
        if (protocolIndex >= 0) {
            // if the Destination URL contains the protocol, we can safely
            // trim everything upto the first "/" character after "://"
            int firstSeparator =
                destinationPath.indexOf('/', protocolIndex + 4);
            if (firstSeparator < 0) {
                destinationPath = "/";
            } else {
                destinationPath = destinationPath.substring(firstSeparator);
            }
        } else {
            String hostName = req.getServerName();
            if ((hostName != null) && (destinationPath.startsWith(hostName))) {
                destinationPath = destinationPath.substring(hostName.length());
            }

            int portIndex = destinationPath.indexOf(':');
            if (portIndex >= 0) {
                destinationPath = destinationPath.substring(portIndex);
            }

            if (destinationPath.startsWith(":")) {
                int firstSeparator = destinationPath.indexOf('/');
                if (firstSeparator < 0) {
                    destinationPath = "/";
                } else {
                    destinationPath =
                        destinationPath.substring(firstSeparator);
                }
            }
        }

        // Normalise destination path (remove '.' and '..')
        destinationPath = RequestUtil.normalize(destinationPath);

        String contextPath = req.getContextPath();
        if ((contextPath != null) &&
            (destinationPath.startsWith(contextPath))) {
            destinationPath = destinationPath.substring(contextPath.length());
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo != null) {
            String servletPath = req.getServletPath();
            if ((servletPath != null) &&
                (destinationPath.startsWith(servletPath))) {
                destinationPath = destinationPath
                    .substring(servletPath.length());
            }
        }

        if (debug > 0)
            log("Dest path :" + destinationPath);

        // Check destination path to protect special subdirectories
        if (isSpecialPath(destinationPath)) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return false;
        }

        String path = getRelativePath(req);

        if (destinationPath.equals(path)) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return false;
        }

        // Parsing overwrite header

        boolean overwrite = true;
        String overwriteHeader = req.getHeader("Overwrite");

        if (overwriteHeader != null) {
            if (overwriteHeader.equalsIgnoreCase("T")) {
                overwrite = true;
            } else {
                overwrite = false;
            }
        }

        // Overwriting the destination

        WebResource destination = resources.getResource(destinationPath);

        if (overwrite) {
            // Delete destination resource, if it exists
            if (destination.exists()) {
                if (!deleteResource(destinationPath, req, resp, true)) {
                    return false;
                }
            } else {
                resp.setStatus(WebdavStatus.SC_CREATED);
            }
        } else {
            // If the destination exists, then it's a conflict
            if (destination.exists()) {
                resp.sendError(WebdavStatus.SC_PRECONDITION_FAILED);
                return false;
            }
        }

        // Copying source to destination

        Hashtable<String,Integer> errorList = new Hashtable<>();

        boolean result = copyResource(errorList, path, destinationPath);

        if ((!result) || (!errorList.isEmpty())) {
            if (errorList.size() == 1) {
                resp.sendError(errorList.elements().nextElement().intValue());
            } else {
                sendReport(req, resp, errorList);
            }
            return false;
        }

        // Copy was successful
        if (destination.exists()) {
            resp.setStatus(WebdavStatus.SC_NO_CONTENT);
        } else {
            resp.setStatus(WebdavStatus.SC_CREATED);
        }

        // Removing any lock-null resource which would be present at
        // the destination path
        lockNullResources.remove(destinationPath);

        return true;
    }


    /**
     * 复制一个集合.
     *
     * @param errorList 复制过程中的错误信息列表Hashtable
     * @param source 要复制的资源的路径
     * @param dest 目的地路径
     * 
     * @return <code>true</code> if the copy was successful
     */
    private boolean copyResource(Hashtable<String,Integer> errorList,
            String source, String dest) {

        if (debug > 1)
            log("Copy: " + source + " To: " + dest);

        WebResource sourceResource = resources.getResource(source);

        if (sourceResource.isDirectory()) {
            if (!resources.mkdir(dest)) {
                WebResource destResource = resources.getResource(dest);
                if (!destResource.isDirectory()) {
                    errorList.put(dest, Integer.valueOf(WebdavStatus.SC_CONFLICT));
                    return false;
                }
            }

            String[] entries = resources.list(source);
            for (String entry : entries) {
                String childDest = dest;
                if (!childDest.equals("/")) {
                    childDest += "/";
                }
                childDest += entry;
                String childSrc = source;
                if (!childSrc.equals("/")) {
                    childSrc += "/";
                }
                childSrc += entry;
                copyResource(errorList, childSrc, childDest);
            }
        } else if (sourceResource.isFile()) {
            WebResource destResource = resources.getResource(dest);
            if (!destResource.exists() && !destResource.getWebappPath().endsWith("/")) {
                int lastSlash = destResource.getWebappPath().lastIndexOf('/');
                if (lastSlash > 0) {
                    String parent = destResource.getWebappPath().substring(0, lastSlash);
                    WebResource parentResource = resources.getResource(parent);
                    if (!parentResource.isDirectory()) {
                        errorList.put(source, Integer.valueOf(WebdavStatus.SC_CONFLICT));
                        return false;
                    }
                }
            }
            try (InputStream is = sourceResource.getInputStream()) {
                if (!resources.write(dest, is,
                        false)) {
                    errorList.put(source,
                            Integer.valueOf(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
                    return false;
                }
            } catch (IOException e) {
                log(sm.getString("webdavservlet.inputstreamclosefail", source), e);
            }
        } else {
            errorList.put(source,
                    Integer.valueOf(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
            return false;
        }
        return true;
    }


    /**
     * Delete a resource.
     *
     * @param req Servlet request
     * @param resp Servlet response
     * @return <code>true</code> if the delete is successful
     * @throws IOException If an IO error occurs
     */
    private boolean deleteResource(HttpServletRequest req,
                                   HttpServletResponse resp)
            throws IOException {

        String path = getRelativePath(req);

        return deleteResource(path, req, resp, true);
    }


    /**
     * Delete a resource.
     *
     * @param path 要删除的资源的路径
     * @param req Servlet request
     * @param resp Servlet response
     * @param setStatus 应该在成功完成后设置响应状态吗
     * 
     * @return <code>true</code> if the delete is successful
     * @throws IOException If an IO error occurs
     */
    private boolean deleteResource(String path, HttpServletRequest req,
                                   HttpServletResponse resp, boolean setStatus)
            throws IOException {

        String ifHeader = req.getHeader("If");
        if (ifHeader == null)
            ifHeader = "";

        String lockTokenHeader = req.getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        if (isLocked(path, ifHeader + lockTokenHeader)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return false;
        }

        WebResource resource = resources.getResource(path);

        if (!resource.exists()) {
            resp.sendError(WebdavStatus.SC_NOT_FOUND);
            return false;
        }

        if (!resource.isDirectory()) {
            if (!resource.delete()) {
                resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
                return false;
            }
        } else {

            Hashtable<String,Integer> errorList = new Hashtable<>();

            deleteCollection(req, path, errorList);
            if (!resource.delete()) {
                errorList.put(path, Integer.valueOf
                    (WebdavStatus.SC_INTERNAL_SERVER_ERROR));
            }

            if (!errorList.isEmpty()) {
                sendReport(req, resp, errorList);
                return false;
            }
        }
        if (setStatus) {
            resp.setStatus(WebdavStatus.SC_NO_CONTENT);
        }
        return true;
    }


    /**
     * 删除集合.
     * @param req The Servlet request
     * @param path 要删除的集合的路径
     * @param errorList 包含发生的错误的列表
     */
    private void deleteCollection(HttpServletRequest req,
                                  String path,
                                  Hashtable<String,Integer> errorList) {

        if (debug > 1)
            log("Delete:" + path);

        // 防止删除特殊子目录
        if (isSpecialPath(path)) {
            errorList.put(path, Integer.valueOf(WebdavStatus.SC_FORBIDDEN));
            return;
        }

        String ifHeader = req.getHeader("If");
        if (ifHeader == null)
            ifHeader = "";

        String lockTokenHeader = req.getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        String[] entries = resources.list(path);

        for (String entry : entries) {
            String childName = path;
            if (!childName.equals("/"))
                childName += "/";
            childName += entry;

            if (isLocked(childName, ifHeader + lockTokenHeader)) {

                errorList.put(childName, Integer.valueOf(WebdavStatus.SC_LOCKED));

            } else {
                WebResource childResource = resources.getResource(childName);
                if (childResource.isDirectory()) {
                    deleteCollection(req, childName, errorList);
                }

                if (!childResource.delete()) {
                    if (!childResource.isDirectory()) {
                        // If it's not a collection, then it's an unknown
                        // error
                        errorList.put(childName, Integer.valueOf(
                                WebdavStatus.SC_INTERNAL_SERVER_ERROR));
                    }
                }
            }
        }
    }


    /**
     * 发送multistatus元素包含一个完整的错误报告给客户.
     *
     * @param req Servlet request
     * @param resp Servlet response
     * @param errorList 要显示的错误列表
     * @throws IOException If an IO error occurs
     */
    private void sendReport(HttpServletRequest req, HttpServletResponse resp,
                            Hashtable<String,Integer> errorList)
            throws IOException {

        resp.setStatus(WebdavStatus.SC_MULTI_STATUS);

        String absoluteUri = req.getRequestURI();
        String relativePath = getRelativePath(req);

        XMLWriter generatedXML = new XMLWriter();
        generatedXML.writeXMLHeader();

        generatedXML.writeElement("D", DEFAULT_NAMESPACE, "multistatus",
                XMLWriter.OPENING);

        Enumeration<String> pathList = errorList.keys();
        while (pathList.hasMoreElements()) {

            String errorPath = pathList.nextElement();
            int errorCode = errorList.get(errorPath).intValue();

            generatedXML.writeElement("D", "response", XMLWriter.OPENING);

            generatedXML.writeElement("D", "href", XMLWriter.OPENING);
            String toAppend = errorPath.substring(relativePath.length());
            if (!toAppend.startsWith("/"))
                toAppend = "/" + toAppend;
            generatedXML.writeText(absoluteUri + toAppend);
            generatedXML.writeElement("D", "href", XMLWriter.CLOSING);
            generatedXML.writeElement("D", "status", XMLWriter.OPENING);
            generatedXML.writeText("HTTP/1.1 " + errorCode + " "
                    + WebdavStatus.getStatusText(errorCode));
            generatedXML.writeElement("D", "status", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "response", XMLWriter.CLOSING);

        }

        generatedXML.writeElement("D", "multistatus", XMLWriter.CLOSING);

        Writer writer = resp.getWriter();
        writer.write(generatedXML.toString());
        writer.close();

    }


    /**
     * Propfind辅助方法.
     *
     * @param req The servlet request
     * @param resources 与此上下文关联的资源对象
     * @param generatedXML XML response to the Propfind request
     * @param path 当前资源的路径
     * @param type Propfind type
     * @param propertiesVector 如果propfind类型是通过名称查找属性, 然后这个Vector包含这些属性
     */
    private void parseProperties(HttpServletRequest req,
                                 XMLWriter generatedXML,
                                 String path, int type,
                                 Vector<String> propertiesVector) {

        // Exclude any resource in the /WEB-INF and /META-INF subdirectories
        if (isSpecialPath(path))
            return;

        WebResource resource = resources.getResource(path);
        if (!resource.exists()) {
            // File is in directory listing but doesn't appear to exist
            // Broken symlink or odd permission settings?
            return;
        }

        String href = req.getContextPath() + req.getServletPath();
        if ((href.endsWith("/")) && (path.startsWith("/")))
            href += path.substring(1);
        else
            href += path;
        if (resource.isDirectory() && (!href.endsWith("/")))
            href += "/";

        String rewrittenUrl = rewriteUrl(href);

        generatePropFindResponse(generatedXML, rewrittenUrl, path, type, propertiesVector,
                resource.isFile(), false, resource.getCreation(), resource.getLastModified(),
                resource.getContentLength(), getServletContext().getMimeType(resource.getName()),
                resource.getETag());
    }


    /**
     * Propfind 辅助方法. 可以显示一个空锁资源的属性.
     *
     * @param resources 与此上下文关联的资源对象
     * @param generatedXML XML response to the Propfind request
     * @param path Path of the current resource
     * @param type Propfind type
     * @param propertiesVector 如果propfind类型是通过名称查找属性, 然后这个Vector包含这些属性
     */
    private void parseLockNullProperties(HttpServletRequest req,
                                         XMLWriter generatedXML,
                                         String path, int type,
                                         Vector<String> propertiesVector) {

        // Exclude any resource in the /WEB-INF and /META-INF subdirectories
        if (isSpecialPath(path))
            return;

        // Retrieving the lock associated with the lock-null resource
        LockInfo lock = resourceLocks.get(path);

        if (lock == null)
            return;

        String absoluteUri = req.getRequestURI();
        String relativePath = getRelativePath(req);
        String toAppend = path.substring(relativePath.length());
        if (!toAppend.startsWith("/"))
            toAppend = "/" + toAppend;

        String rewrittenUrl = rewriteUrl(RequestUtil.normalize(
                absoluteUri + toAppend));

        generatePropFindResponse(generatedXML, rewrittenUrl, path, type, propertiesVector,
                true, true, lock.creationDate.getTime(), lock.creationDate.getTime(),
                0, "", "");
    }


    private void generatePropFindResponse(XMLWriter generatedXML, String rewrittenUrl,
            String path, int propFindType, Vector<String> propertiesVector, boolean isFile,
            boolean isLockNull, long created, long lastModified, long contentLength,
            String contentType, String eTag) {

        generatedXML.writeElement("D", "response", XMLWriter.OPENING);
        String status = "HTTP/1.1 " + WebdavStatus.SC_OK + " " +
                WebdavStatus.getStatusText(WebdavStatus.SC_OK);

        // Generating href element
        generatedXML.writeElement("D", "href", XMLWriter.OPENING);
        generatedXML.writeText(rewrittenUrl);
        generatedXML.writeElement("D", "href", XMLWriter.CLOSING);

        String resourceName = path;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1)
            resourceName = resourceName.substring(lastSlash + 1);

        switch (propFindType) {

        case FIND_ALL_PROP :

            generatedXML.writeElement("D", "propstat", XMLWriter.OPENING);
            generatedXML.writeElement("D", "prop", XMLWriter.OPENING);

            generatedXML.writeProperty("D", "creationdate", getISOCreationDate(created));
            generatedXML.writeElement("D", "displayname", XMLWriter.OPENING);
            generatedXML.writeData(resourceName);
            generatedXML.writeElement("D", "displayname", XMLWriter.CLOSING);
            if (isFile) {
                generatedXML.writeProperty("D", "getlastmodified",
                        FastHttpDateFormat.formatDate(lastModified, null));
                generatedXML.writeProperty("D", "getcontentlength", Long.toString(contentLength));
                if (contentType != null) {
                    generatedXML.writeProperty("D", "getcontenttype", contentType);
                }
                generatedXML.writeProperty("D", "getetag", eTag);
                if (isLockNull) {
                    generatedXML.writeElement("D", "resourcetype", XMLWriter.OPENING);
                    generatedXML.writeElement("D", "lock-null", XMLWriter.NO_CONTENT);
                    generatedXML.writeElement("D", "resourcetype", XMLWriter.CLOSING);
                } else {
                    generatedXML.writeElement("D", "resourcetype", XMLWriter.NO_CONTENT);
                }
            } else {
                generatedXML.writeElement("D", "resourcetype", XMLWriter.OPENING);
                generatedXML.writeElement("D", "collection", XMLWriter.NO_CONTENT);
                generatedXML.writeElement("D", "resourcetype", XMLWriter.CLOSING);
            }

            generatedXML.writeProperty("D", "source", "");

            String supportedLocks = "<D:lockentry>"
                + "<D:lockscope><D:exclusive/></D:lockscope>"
                + "<D:locktype><D:write/></D:locktype>"
                + "</D:lockentry>" + "<D:lockentry>"
                + "<D:lockscope><D:shared/></D:lockscope>"
                + "<D:locktype><D:write/></D:locktype>"
                + "</D:lockentry>";
            generatedXML.writeElement("D", "supportedlock", XMLWriter.OPENING);
            generatedXML.writeText(supportedLocks);
            generatedXML.writeElement("D", "supportedlock", XMLWriter.CLOSING);

            generateLockDiscovery(path, generatedXML);

            generatedXML.writeElement("D", "prop", XMLWriter.CLOSING);
            generatedXML.writeElement("D", "status", XMLWriter.OPENING);
            generatedXML.writeText(status);
            generatedXML.writeElement("D", "status", XMLWriter.CLOSING);
            generatedXML.writeElement("D", "propstat", XMLWriter.CLOSING);

            break;

        case FIND_PROPERTY_NAMES :

            generatedXML.writeElement("D", "propstat", XMLWriter.OPENING);
            generatedXML.writeElement("D", "prop", XMLWriter.OPENING);

            generatedXML.writeElement("D", "creationdate", XMLWriter.NO_CONTENT);
            generatedXML.writeElement("D", "displayname", XMLWriter.NO_CONTENT);
            if (isFile) {
                generatedXML.writeElement("D", "getcontentlanguage", XMLWriter.NO_CONTENT);
                generatedXML.writeElement("D", "getcontentlength", XMLWriter.NO_CONTENT);
                generatedXML.writeElement("D", "getcontenttype", XMLWriter.NO_CONTENT);
                generatedXML.writeElement("D", "getetag", XMLWriter.NO_CONTENT);
                generatedXML.writeElement("D", "getlastmodified", XMLWriter.NO_CONTENT);
            }
            generatedXML.writeElement("D", "resourcetype", XMLWriter.NO_CONTENT);
            generatedXML.writeElement("D", "source", XMLWriter.NO_CONTENT);
            generatedXML.writeElement("D", "lockdiscovery", XMLWriter.NO_CONTENT);

            generatedXML.writeElement("D", "prop", XMLWriter.CLOSING);
            generatedXML.writeElement("D", "status", XMLWriter.OPENING);
            generatedXML.writeText(status);
            generatedXML.writeElement("D", "status", XMLWriter.CLOSING);
            generatedXML.writeElement("D", "propstat", XMLWriter.CLOSING);

            break;

        case FIND_BY_PROPERTY :

            Vector<String> propertiesNotFound = new Vector<>();

            // Parse the list of properties

            generatedXML.writeElement("D", "propstat", XMLWriter.OPENING);
            generatedXML.writeElement("D", "prop", XMLWriter.OPENING);

            Enumeration<String> properties = propertiesVector.elements();

            while (properties.hasMoreElements()) {

                String property = properties.nextElement();

                if (property.equals("creationdate")) {
                    generatedXML.writeProperty ("D", "creationdate", getISOCreationDate(created));
                } else if (property.equals("displayname")) {
                    generatedXML.writeElement("D", "displayname", XMLWriter.OPENING);
                    generatedXML.writeData(resourceName);
                    generatedXML.writeElement("D", "displayname", XMLWriter.CLOSING);
                } else if (property.equals("getcontentlanguage")) {
                    if (isFile) {
                        generatedXML.writeElement("D", "getcontentlanguage",
                                XMLWriter.NO_CONTENT);
                    } else {
                        propertiesNotFound.addElement(property);
                    }
                } else if (property.equals("getcontentlength")) {
                    if (isFile) {
                        generatedXML.writeProperty("D", "getcontentlength",
                                Long.toString(contentLength));
                    } else {
                        propertiesNotFound.addElement(property);
                    }
                } else if (property.equals("getcontenttype")) {
                    if (isFile) {
                        generatedXML.writeProperty("D", "getcontenttype", contentType);
                    } else {
                        propertiesNotFound.addElement(property);
                    }
                } else if (property.equals("getetag")) {
                    if (isFile) {
                        generatedXML.writeProperty("D", "getetag", eTag);
                    } else {
                        propertiesNotFound.addElement(property);
                    }
                } else if (property.equals("getlastmodified")) {
                    if (isFile) {
                        generatedXML.writeProperty("D", "getlastmodified",
                                FastHttpDateFormat.formatDate(lastModified, null));
                    } else {
                        propertiesNotFound.addElement(property);
                    }
                } else if (property.equals("resourcetype")) {
                    if (isFile) {
                        if(isLockNull) {
                            generatedXML.writeElement("D", "resourcetype", XMLWriter.OPENING);
                            generatedXML.writeElement("D", "lock-null", XMLWriter.NO_CONTENT);
                            generatedXML.writeElement("D", "resourcetype", XMLWriter.CLOSING);
                        } else {
                            generatedXML.writeElement("D", "resourcetype", XMLWriter.NO_CONTENT);
                        }
                    } else {
                        generatedXML.writeElement("D", "resourcetype", XMLWriter.OPENING);
                        generatedXML.writeElement("D", "collection", XMLWriter.NO_CONTENT);
                        generatedXML.writeElement("D", "resourcetype",XMLWriter.CLOSING);
                    }
                } else if (property.equals("source")) {
                    generatedXML.writeProperty("D", "source", "");
                } else if (property.equals("supportedlock")) {
                    supportedLocks = "<D:lockentry>"
                        + "<D:lockscope><D:exclusive/></D:lockscope>"
                        + "<D:locktype><D:write/></D:locktype>"
                        + "</D:lockentry>" + "<D:lockentry>"
                        + "<D:lockscope><D:shared/></D:lockscope>"
                        + "<D:locktype><D:write/></D:locktype>"
                        + "</D:lockentry>";
                    generatedXML.writeElement("D", "supportedlock", XMLWriter.OPENING);
                    generatedXML.writeText(supportedLocks);
                    generatedXML.writeElement("D", "supportedlock", XMLWriter.CLOSING);
                } else if (property.equals("lockdiscovery")) {
                    if (!generateLockDiscovery(path, generatedXML))
                        propertiesNotFound.addElement(property);
                } else {
                    propertiesNotFound.addElement(property);
                }

            }

            generatedXML.writeElement("D", "prop", XMLWriter.CLOSING);
            generatedXML.writeElement("D", "status", XMLWriter.OPENING);
            generatedXML.writeText(status);
            generatedXML.writeElement("D", "status", XMLWriter.CLOSING);
            generatedXML.writeElement("D", "propstat", XMLWriter.CLOSING);

            Enumeration<String> propertiesNotFoundList = propertiesNotFound.elements();

            if (propertiesNotFoundList.hasMoreElements()) {

                status = "HTTP/1.1 " + WebdavStatus.SC_NOT_FOUND + " " +
                        WebdavStatus.getStatusText(WebdavStatus.SC_NOT_FOUND);

                generatedXML.writeElement("D", "propstat", XMLWriter.OPENING);
                generatedXML.writeElement("D", "prop", XMLWriter.OPENING);

                while (propertiesNotFoundList.hasMoreElements()) {
                    generatedXML.writeElement("D", propertiesNotFoundList.nextElement(),
                            XMLWriter.NO_CONTENT);
                }

                generatedXML.writeElement("D", "prop", XMLWriter.CLOSING);
                generatedXML.writeElement("D", "status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement("D", "status", XMLWriter.CLOSING);
                generatedXML.writeElement("D", "propstat", XMLWriter.CLOSING);

            }
            break;
        }

        generatedXML.writeElement("D", "response", XMLWriter.CLOSING);
    }


    /**
     * 打印与路径相关联的锁发现信息
     *
     * @param path Path
     * @param generatedXML 将添加锁信息的XML数据
     * @return true 如果至少显示了一个锁
     */
    private boolean generateLockDiscovery(String path, XMLWriter generatedXML) {

        LockInfo resourceLock = resourceLocks.get(path);
        Enumeration<LockInfo> collectionLocksList = collectionLocks.elements();

        boolean wroteStart = false;

        if (resourceLock != null) {
            wroteStart = true;
            generatedXML.writeElement("D", "lockdiscovery", XMLWriter.OPENING);
            resourceLock.toXML(generatedXML);
        }

        while (collectionLocksList.hasMoreElements()) {
            LockInfo currentLock = collectionLocksList.nextElement();
            if (path.startsWith(currentLock.path)) {
                if (!wroteStart) {
                    wroteStart = true;
                    generatedXML.writeElement("D", "lockdiscovery",
                            XMLWriter.OPENING);
                }
                currentLock.toXML(generatedXML);
            }
        }

        if (wroteStart) {
            generatedXML.writeElement("D", "lockdiscovery", XMLWriter.CLOSING);
        } else {
            return false;
        }

        return true;

    }


    /**
     * 获取ISO格式的创建日期.
     * @return 格式化的创建日期
     */
    private String getISOCreationDate(long creationDate) {
        return creationDateFormat.format(new Date(creationDate));
    }

    /**
     * 确定资源通常允许的方法.
     * @param req The Servlet request
     * @return a string builder with the allowed HTTP methods
     */
    private StringBuilder determineMethodsAllowed(HttpServletRequest req) {

        StringBuilder methodsAllowed = new StringBuilder();

        WebResource resource = resources.getResource(getRelativePath(req));

        if (!resource.exists()) {
            methodsAllowed.append("OPTIONS, MKCOL, PUT, LOCK");
            return methodsAllowed;
        }

        methodsAllowed.append("OPTIONS, GET, HEAD, POST, DELETE");
        // Trace - assume disabled unless we can prove otherwise
        if (req instanceof RequestFacade &&
                ((RequestFacade) req).getAllowTrace()) {
            methodsAllowed.append(", TRACE");
        }
        methodsAllowed.append(", PROPPATCH, COPY, MOVE, LOCK, UNLOCK");

        if (listings) {
            methodsAllowed.append(", PROPFIND");
        }

        if (resource.isFile()) {
            methodsAllowed.append(", PUT");
        }

        return methodsAllowed;
    }

    // --------------------------------------------------  LockInfo Inner Class


    /**
     * 持有锁定信息.
     */
    private class LockInfo {

        // ------------------------------------------------- Instance Variables

        String path = "/";
        String type = "write";
        String scope = "exclusive";
        int depth = 0;
        String owner = "";
        Vector<String> tokens = new Vector<>();
        long expiresAt = 0;
        Date creationDate = new Date();

        // ----------------------------------------------------- Public Methods

        @Override
        public String toString() {

            StringBuilder result =  new StringBuilder("Type:");
            result.append(type);
            result.append("\nScope:");
            result.append(scope);
            result.append("\nDepth:");
            result.append(depth);
            result.append("\nOwner:");
            result.append(owner);
            result.append("\nExpiration:");
            result.append(FastHttpDateFormat.formatDate(expiresAt, null));
            Enumeration<String> tokensList = tokens.elements();
            while (tokensList.hasMoreElements()) {
                result.append("\nToken:");
                result.append(tokensList.nextElement());
            }
            result.append("\n");
            return result.toString();
        }


        /**
         * @return true 如果锁过期了.
         */
        public boolean hasExpired() {
            return System.currentTimeMillis() > expiresAt;
        }


        /**
         * @return true 如果锁是独占的.
         */
        public boolean isExclusive() {
            return scope.equals("exclusive");
        }


        /**
         * 获取此锁令牌的XML表示形式.
         *
         * @param generatedXML 将写入片段的XML写入器
         */
        public void toXML(XMLWriter generatedXML) {

            generatedXML.writeElement("D", "activelock", XMLWriter.OPENING);

            generatedXML.writeElement("D", "locktype", XMLWriter.OPENING);
            generatedXML.writeElement("D", type, XMLWriter.NO_CONTENT);
            generatedXML.writeElement("D", "locktype", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "lockscope", XMLWriter.OPENING);
            generatedXML.writeElement("D", scope, XMLWriter.NO_CONTENT);
            generatedXML.writeElement("D", "lockscope", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "depth", XMLWriter.OPENING);
            if (depth == maxDepth) {
                generatedXML.writeText("Infinity");
            } else {
                generatedXML.writeText("0");
            }
            generatedXML.writeElement("D", "depth", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "owner", XMLWriter.OPENING);
            generatedXML.writeText(owner);
            generatedXML.writeElement("D", "owner", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "timeout", XMLWriter.OPENING);
            long timeout = (expiresAt - System.currentTimeMillis()) / 1000;
            generatedXML.writeText("Second-" + timeout);
            generatedXML.writeElement("D", "timeout", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "locktoken", XMLWriter.OPENING);
            Enumeration<String> tokensList = tokens.elements();
            while (tokensList.hasMoreElements()) {
                generatedXML.writeElement("D", "href", XMLWriter.OPENING);
                generatedXML.writeText("opaquelocktoken:"
                                       + tokensList.nextElement());
                generatedXML.writeElement("D", "href", XMLWriter.CLOSING);
            }
            generatedXML.writeElement("D", "locktoken", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "activelock", XMLWriter.CLOSING);
        }
    }


    // --------------------------------------------- WebdavResolver Inner Class
    /**
     * 为XML解析器工作, 不完全希望{@link DocumentBuilderFactory#setExpandEntityReferences(boolean)}, 当参数为<code>false</code>.
     * 出于安全原因，过滤外部引用. See CVE-2007-5461.
     */
    private static class WebdavResolver implements EntityResolver {
        private ServletContext context;

        public WebdavResolver(ServletContext theContext) {
            context = theContext;
        }

        @Override
        public InputSource resolveEntity (String publicId, String systemId) {
            context.log(sm.getString("webdavservlet.enternalEntityIgnored",
                    publicId, systemId));
            return new InputSource(
                    new StringReader("Ignored external entity"));
        }
    }
}


// --------------------------------------------------------  WebdavStatus Class


/**
 * 包装HttpServletResponse类成抽象的，指定协议使用的. 
 * 为了支持其他协议，我们只需要修改这个类和WebDavRetCode 类.
 */
class WebdavStatus {


    // ----------------------------------------------------- Instance Variables


    /**
     * 这个Hashtable 包含HTTP状态码和WebDAV的描述性文本映射.
     */
    private static final Hashtable<Integer,String> mapStatusCodes =
            new Hashtable<>();


    // ------------------------------------------------------ HTTP Status Codes


    /**
     * 状态码(200) 指示请求成功正常.
     */
    public static final int SC_OK = HttpServletResponse.SC_OK;


    /**
     * 状态码(201) 指示请求成功并在服务器上创建新资源.
     */
    public static final int SC_CREATED = HttpServletResponse.SC_CREATED;


    /**
     * 状态码(202) 指示请求接受处理，但未完成.
     */
    public static final int SC_ACCEPTED = HttpServletResponse.SC_ACCEPTED;


    /**
     * 状态码(204) 指示请求成功，但没有返回新信息.
     */
    public static final int SC_NO_CONTENT = HttpServletResponse.SC_NO_CONTENT;


    /**
     * 状态码(301) 指示资源已永久移动到新位置, 未来的引用应该使用一个新的URI和它们的请求.
     */
    public static final int SC_MOVED_PERMANENTLY =
        HttpServletResponse.SC_MOVED_PERMANENTLY;


    /**
     * 状态码(302) 指示资源已临时移到另一位置, 但是将来的引用仍然应该使用原始URI来访问资源.
     */
    public static final int SC_MOVED_TEMPORARILY =
        HttpServletResponse.SC_MOVED_TEMPORARILY;


    /**
     * 状态码(304) 指示条件GET 操作发现资源可用且未修改.
     */
    public static final int SC_NOT_MODIFIED =
        HttpServletResponse.SC_NOT_MODIFIED;


    /**
     * 状态码(400) 指示客户端发送的请求有语法错误.
     */
    public static final int SC_BAD_REQUEST =
        HttpServletResponse.SC_BAD_REQUEST;


    /**
     * 状态码(401) 指示请求需要HTTP身份验证.
     */
    public static final int SC_UNAUTHORIZED =
        HttpServletResponse.SC_UNAUTHORIZED;


    /**
     * 状态码(403) 指示服务器理解请求，但拒绝执行请求.
     */
    public static final int SC_FORBIDDEN = HttpServletResponse.SC_FORBIDDEN;


    /**
     * 状态码(404) 指示所请求的资源不可用.
     */
    public static final int SC_NOT_FOUND = HttpServletResponse.SC_NOT_FOUND;


    /**
     * 状态码(500) 指示HTTP服务内部的错误，从而阻止它执行请求.
     */
    public static final int SC_INTERNAL_SERVER_ERROR =
        HttpServletResponse.SC_INTERNAL_SERVER_ERROR;


    /**
     * 状态码(501) 指示HTTP服务不支持实现请求所需的功能.
     */
    public static final int SC_NOT_IMPLEMENTED =
        HttpServletResponse.SC_NOT_IMPLEMENTED;


    /**
     * 状态码(502) 指示HTTP服务器在代理或网关时从服务器处接收到无效响应.
     */
    public static final int SC_BAD_GATEWAY =
        HttpServletResponse.SC_BAD_GATEWAY;


    /**
     * 状态码(503) 指示HTTP服务暂时超载, 无法处理请求.
     */
    public static final int SC_SERVICE_UNAVAILABLE =
        HttpServletResponse.SC_SERVICE_UNAVAILABLE;


    /**
     * 状态码(100) 指示客户端可以继续其请求. 
     * 此临时响应用于通知客户端已收到请求的初始部分，尚未被服务器拒绝.
     */
    public static final int SC_CONTINUE = 100;


    /**
     * 状态码(405) 指示资源不允许指定方法.
     */
    public static final int SC_METHOD_NOT_ALLOWED = 405;


    /**
     * 状态码(409) 指示由于资源的当前状态发生冲突，无法完成请求.
     */
    public static final int SC_CONFLICT = 409;


    /**
     * 状态码(412) 指示在服务器上测试的请求报头字段中的一个或多个给定的前提条件，该字段被评估为false.
     */
    public static final int SC_PRECONDITION_FAILED = 412;


    /**
     * 状态码(413) 指示服务器拒绝处理请求，因为请求实体大于服务器愿意或能够处理的请求.
     */
    public static final int SC_REQUEST_TOO_LONG = 413;


    /**
     * 状态码(415) 指示服务器拒绝服务请求，因为请求的实体是所请求的资源不支持所请求方法的格式.
     */
    public static final int SC_UNSUPPORTED_MEDIA_TYPE = 415;


    // -------------------------------------------- Extended WebDav status code


    /**
     * 状态码(207) 指示响应需要为多个独立操作提供状态.
     */
    public static final int SC_MULTI_STATUS = 207;
    // This one collides with HTTP 1.1
    // "207 Partial Update OK"


    /**
     * 状态码(418) 指示使用PATCH方法提交的实体不被资源理解.
     */
    public static final int SC_UNPROCESSABLE_ENTITY = 418;
    // This one collides with HTTP 1.1
    // "418 Reauthentication Required"


    /**
     * 状态码(419) 指示资源在执行该方法后没有足够的空间来记录资源的状态.
     */
    public static final int SC_INSUFFICIENT_SPACE_ON_RESOURCE = 419;
    // This one collides with HTTP 1.1
    // "419 Proxy Reauthentication Required"


    /**
     * 状态码(420) 指示该方法未在其范围内的特定资源上执行，因为该方法执行的某些部分失败导致整个方法被中止.
     */
    public static final int SC_METHOD_FAILURE = 420;


    /**
     * 状态码(423) 指示方法的目标资源被锁定, 而且请求中没有一个有效的锁信息头, 或者锁信息头标识另一个主体持有的锁.
     */
    public static final int SC_LOCKED = 423;


    // ------------------------------------------------------------ Initializer


    static {
        // HTTP 1.0 status Code
        addStatusCodeMap(SC_OK, "OK");
        addStatusCodeMap(SC_CREATED, "Created");
        addStatusCodeMap(SC_ACCEPTED, "Accepted");
        addStatusCodeMap(SC_NO_CONTENT, "No Content");
        addStatusCodeMap(SC_MOVED_PERMANENTLY, "Moved Permanently");
        addStatusCodeMap(SC_MOVED_TEMPORARILY, "Moved Temporarily");
        addStatusCodeMap(SC_NOT_MODIFIED, "Not Modified");
        addStatusCodeMap(SC_BAD_REQUEST, "Bad Request");
        addStatusCodeMap(SC_UNAUTHORIZED, "Unauthorized");
        addStatusCodeMap(SC_FORBIDDEN, "Forbidden");
        addStatusCodeMap(SC_NOT_FOUND, "Not Found");
        addStatusCodeMap(SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
        addStatusCodeMap(SC_NOT_IMPLEMENTED, "Not Implemented");
        addStatusCodeMap(SC_BAD_GATEWAY, "Bad Gateway");
        addStatusCodeMap(SC_SERVICE_UNAVAILABLE, "Service Unavailable");
        addStatusCodeMap(SC_CONTINUE, "Continue");
        addStatusCodeMap(SC_METHOD_NOT_ALLOWED, "Method Not Allowed");
        addStatusCodeMap(SC_CONFLICT, "Conflict");
        addStatusCodeMap(SC_PRECONDITION_FAILED, "Precondition Failed");
        addStatusCodeMap(SC_REQUEST_TOO_LONG, "Request Too Long");
        addStatusCodeMap(SC_UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type");
        // WebDav Status Codes
        addStatusCodeMap(SC_MULTI_STATUS, "Multi-Status");
        addStatusCodeMap(SC_UNPROCESSABLE_ENTITY, "Unprocessable Entity");
        addStatusCodeMap(SC_INSUFFICIENT_SPACE_ON_RESOURCE,
                         "Insufficient Space On Resource");
        addStatusCodeMap(SC_METHOD_FAILURE, "Method Failure");
        addStatusCodeMap(SC_LOCKED, "Locked");
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 返回HTTP状态文本的HTTP或WebDAV状态码，通过查找指定的静态映射.
     *
     * @param   nHttpStatusCode [IN] HTTP or WebDAV status code
     * @return  带有HTTP状态码的简短描述短语的字符串 (e.g., "OK").
     */
    public static String getStatusText(int nHttpStatusCode) {
        Integer intKey = Integer.valueOf(nHttpStatusCode);

        if (!mapStatusCodes.containsKey(intKey)) {
            return "";
        } else {
            return mapStatusCodes.get(intKey);
        }
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 添加新的状态码 -> 状态文本映射. 这是静态方法，因为映射是静态变量.
     *
     * @param   nKey    [IN] HTTP或WebDAV状态码
     * @param   strVal  [IN] HTTP状态文本
     */
    private static void addStatusCodeMap(int nKey, String strVal) {
        mapStatusCodes.put(Integer.valueOf(nKey), strVal);
    }
}
