package org.apache.catalina.servlets;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.connector.ResponseFacade;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.URLEncoder;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;
import org.apache.tomcat.util.security.PrivilegedGetTccl;
import org.apache.tomcat.util.security.PrivilegedSetTccl;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;


/**
 * <p>大多数Web应用程序的默认资源服务servlet, 用于服务静态资源，如HTML页面和图像.
 * </p>
 * <p>
 * 这个servlet要映射到<em>/</em> e.g.:
 * </p>
 * <pre>
 *   &lt;servlet-mapping&gt;
 *       &lt;servlet-name&gt;default&lt;/servlet-name&gt;
 *       &lt;url-pattern&gt;/&lt;/url-pattern&gt;
 *   &lt;/servlet-mapping&gt;
 * </pre>
 * <p>它可以映射到子路径, 然而，在所有情况下，使用Web应用程序上下文根的完整路径从Web应用程序资源根目录提供资源.
 * <br>例如，给定Web应用程序结构:
 *</p>
 * <pre>
 * /context
 *   /images
 *     tomcat2.jpg
 *   /static
 *     /images
 *       tomcat.jpg
 * </pre>
 * <p>
 * ... 而且一个servlet只映射<code>/static/*</code>到默认servlet:
 * </p>
 * <pre>
 *   &lt;servlet-mapping&gt;
 *       &lt;servlet-name&gt;default&lt;/servlet-name&gt;
 *       &lt;url-pattern&gt;/static/*&lt;/url-pattern&gt;
 *   &lt;/servlet-mapping&gt;
 * </pre>
 * <p>
 * 那么请求<code>/context/static/images/tomcat.jpg</code>将成功, 如果请求<code>/context/images/tomcat2.jpg</code>失败.
 * </p>
 */
public class DefaultServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    private static final DocumentBuilderFactory factory;

    private static final SecureEntityResolver secureEntityResolver;

    /**
     * Full range marker.
     */
    protected static final ArrayList<Range> FULL = new ArrayList<>();

    /**
     * MIME多段分离字符串
     */
    protected static final String mimeSeparation = "CATALINA_MIME_BOUNDARY";

    /**
     * JNDI资源名称.
     * @deprecated Unused. Will be removed in Tomcat 9.
     */
    @Deprecated
    protected static final String RESOURCES_JNDI_NAME = "java:/comp/Resources";

    /**
     * 文件传输缓冲区的大小, 字节.
     */
    protected static final int BUFFER_SIZE = 4096;


    // ----------------------------------------------------- Static Initializer

    static {
        if (Globals.IS_SECURITY_ENABLED) {
            factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            secureEntityResolver = new SecureEntityResolver();
        } else {
            factory = null;
            secureEntityResolver = null;
        }
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 调试等级.
     */
    protected int debug = 0;

    /**
     * 服务资源时要使用的输入缓冲区大小.
     */
    protected int input = 2048;

    /**
     * 当没有欢迎文件时，我们是否应该生成目录列表？
     */
    protected boolean listings = false;

    /**
     * 只读标记. 默认是true.
     */
    protected boolean readOnly = true;

    /**
     * 服务的压缩格式列表及其顺序.
     */
    protected CompressionFormat[] compressionFormats;

    /**
     * 服务资源时要使用的输出缓冲区大小.
     */
    protected int output = 2048;

    /**
     * 允许每个目录定制目录列表.
     */
    protected String localXsltFile = null;

    /**
     * 允许每个上下文自定义目录列表.
     */
    protected String contextXsltFile = null;

    /**
     * 允许每个实例定制目录列表.
     */
    protected String globalXsltFile = null;

    /**
     * 允许包含自述文件.
     */
    protected String readmeFile = null;

    /**
     * Web应用资源的完整集合
     */
    protected transient WebResourceRoot resources = null;

    /**
     * 读取静态文件时使用的文件编码. 如果没有指定，则使用平台默认值.
     */
    protected String fileEncoding = null;

    /**
     * sendfile使用的最小大小, in bytes.
     */
    protected int sendfileSize = 48 * 1024;

    /**
     * 是否使用静态资源发送 Accept-Ranges: bytes header?
     */
    protected boolean useAcceptRanges = true;

    /**
     * 确定服务器信息是否存在.
     */
    protected boolean showServerInfo = true;


    // --------------------------------------------------------- Public Methods

    /**
     * 销毁这个servlet.
     */
    @Override
    public void destroy() {
        // NOOP
    }


    /**
     * 初始化这个servlet.
     */
    @Override
    public void init() throws ServletException {

        if (getServletConfig().getInitParameter("debug") != null)
            debug = Integer.parseInt(getServletConfig().getInitParameter("debug"));

        if (getServletConfig().getInitParameter("input") != null)
            input = Integer.parseInt(getServletConfig().getInitParameter("input"));

        if (getServletConfig().getInitParameter("output") != null)
            output = Integer.parseInt(getServletConfig().getInitParameter("output"));

        listings = Boolean.parseBoolean(getServletConfig().getInitParameter("listings"));

        if (getServletConfig().getInitParameter("readonly") != null)
            readOnly = Boolean.parseBoolean(getServletConfig().getInitParameter("readonly"));

        compressionFormats = parseCompressionFormats(
                getServletConfig().getInitParameter("precompressed"),
                getServletConfig().getInitParameter("gzip"));

        if (getServletConfig().getInitParameter("sendfileSize") != null)
            sendfileSize =
                Integer.parseInt(getServletConfig().getInitParameter("sendfileSize")) * 1024;

        fileEncoding = getServletConfig().getInitParameter("fileEncoding");

        globalXsltFile = getServletConfig().getInitParameter("globalXsltFile");
        contextXsltFile = getServletConfig().getInitParameter("contextXsltFile");
        localXsltFile = getServletConfig().getInitParameter("localXsltFile");
        readmeFile = getServletConfig().getInitParameter("readmeFile");

        if (getServletConfig().getInitParameter("useAcceptRanges") != null)
            useAcceptRanges = Boolean.parseBoolean(getServletConfig().getInitParameter("useAcceptRanges"));

        // 指定缓冲区大小的健全性检查
        if (input < 256)
            input = 256;
        if (output < 256)
            output = 256;

        if (debug > 0) {
            log("DefaultServlet.init:  input buffer size=" + input +
                ", output buffer size=" + output);
        }

        // Load the web resources
        resources = (WebResourceRoot) getServletContext().getAttribute(
                Globals.RESOURCES_ATTR);

        if (resources == null) {
            throw new UnavailableException("No resources");
        }

        if (getServletConfig().getInitParameter("showServerInfo") != null) {
            showServerInfo = Boolean.parseBoolean(getServletConfig().getInitParameter("showServerInfo"));
        }
    }

    private CompressionFormat[] parseCompressionFormats(String precompressed, String gzip) {
        List<CompressionFormat> ret = new ArrayList<>();
        if (precompressed != null && precompressed.indexOf('=') > 0) {
            for (String pair : precompressed.split(",")) {
                String[] setting = pair.split("=");
                String encoding = setting[0];
                String extension = setting[1];
                ret.add(new CompressionFormat(extension, encoding));
            }
        } else if (precompressed != null) {
            if (Boolean.parseBoolean(precompressed)) {
                ret.add(new CompressionFormat(".br", "br"));
                ret.add(new CompressionFormat(".gz", "gzip"));
            }
        } else if (Boolean.parseBoolean(gzip)) {
            // gzip 处理是与Tomcat 8.x的向后兼容性
            ret.add(new CompressionFormat(".gz", "gzip"));
        }
        return ret.toArray(new CompressionFormat[ret.size()]);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 返回相对路径.
     *
     * @param request The servlet request we are processing
     * @return the relative path
     */
    protected String getRelativePath(HttpServletRequest request) {
        return getRelativePath(request, false);
    }

    protected String getRelativePath(HttpServletRequest request, boolean allowEmptyPath) {
        // IMPORTANT: DefaultServlet可以映射到 '/' 或 '/path/*', 但总是使用上下文根路径从Web应用根服务资源.
        // 即，它不能用于在子路径下安装Web应用程序根目录
        // 这种方法必须构建一个完整的上下文根路径, 虽然子类可以改变这种行为.

        String servletPath;
        String pathInfo;

        if (request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) != null) {
            // For includes, 从属性获取信息
            pathInfo = (String) request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
            servletPath = (String) request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
        } else {
            pathInfo = request.getPathInfo();
            servletPath = request.getServletPath();
        }

        StringBuilder result = new StringBuilder();
        if (servletPath.length() > 0) {
            result.append(servletPath);
        }
        if (pathInfo != null) {
            result.append(pathInfo);
        }
        if (result.length() == 0 && !allowEmptyPath) {
            result.append('/');
        }

        return result.toString();
    }


    /**
     * 在生成目录列表时，确定用于预留资源的适当路径.
     * 根据{@link #getRelativePath(HttpServletRequest)}的行为，这个将改变.
     * 
     * @param request 确定路径的请求
     * @return 应用于列表中所有资源的前缀.
     */
    protected String getPathPrefix(final HttpServletRequest request) {
        return request.getContextPath();
    }


    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        if (req.getDispatcherType() == DispatcherType.ERROR) {
            doGet(req, resp);
        } else {
            super.service(req, resp);
        }
    }


    /**
     * 处理指定资源的GET请求.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
        throws IOException, ServletException {

        // Serve the requested resource, including the data content
        serveResource(request, response, true, fileEncoding);

    }


    /**
     * 处理指定资源的HEAD请求.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        // 服务请求的资源, 不包含数据内容, 除非被included, 因为在这种情况下，需要提供内容, 因此，包含资源的正确内容长度被报告
        boolean serveContent = DispatcherType.INCLUDE.equals(request.getDispatcherType());
        serveResource(request, response, serveContent, fileEncoding);
    }


    /**
     * 重写默认实现类, 确保正确处理TRACE.
     *
     * @param req   包含servlet客户端的请求的{@link HttpServletRequest}对象
     * @param resp  包含返回给servlet客户端的响应的{@link HttpServletResponse}对象
     *
     * @exception IOException   如果发生输入或输出错误，在servlet处理OPTIONS请求时
     * @exception ServletException  如果不能处理OPTIONS请求
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        StringBuilder allow = new StringBuilder();
        // There is a doGet method
        allow.append("GET, HEAD");
        // There is a doPost
        allow.append(", POST");
        // There is a doPut
        allow.append(", PUT");
        // There is a doDelete
        allow.append(", DELETE");
        // Trace - 除非能证明是无效的
        if (req instanceof RequestFacade &&
                ((RequestFacade) req).getAllowTrace()) {
            allow.append(", TRACE");
        }
        // Always allow options
        allow.append(", OPTIONS");

        resp.setHeader("Allow", allow.toString());
    }


    /**
     * 处理指定资源的POST请求.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
        throws IOException, ServletException {
        doGet(request, response);
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

        if (readOnly) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String path = getRelativePath(req);

        WebResource resource = resources.getResource(path);

        Range range = parseContentRange(req, resp);

        InputStream resourceInputStream = null;

        try {
            // 将范围中指定的数据追加到该资源的现有内容中 - create a temp. 在本地文件系统上执行此操作的文件
            // 假设现在只指定一个范围
            if (range != null) {
                File contentFile = executePartialPut(req, range, path);
                resourceInputStream = new FileInputStream(contentFile);
            } else {
                resourceInputStream = req.getInputStream();
            }

            if (resources.write(path, resourceInputStream, true)) {
                if (resource.exists()) {
                    resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                } else {
                    resp.setStatus(HttpServletResponse.SC_CREATED);
                }
            } else {
                resp.sendError(HttpServletResponse.SC_CONFLICT);
            }
        } finally {
            if (resourceInputStream != null) {
                try {
                    resourceInputStream.close();
                } catch (IOException ioe) {
                    // Ignore
                }
            }
        }
    }


    /**
     * Handle a partial PUT. 请求中指定的新内容添加到oldRevisionContent的现有内容. 
     * 此代码不支持对同一资源的同步部分更新.
     * 
     * @param req Servlet请求
     * @param range 将写入的范围
     * @param path 路径
     * 
     * @return the associated file object
     * @throws IOException an IO error occurred
     */
    protected File executePartialPut(HttpServletRequest req, Range range,
                                     String path)
        throws IOException {

        // 将范围中指定的数据追加到该资源的现有内容中 - create a temp. 在本地文件系统上执行此操作的文件
        File tempDir = (File) getServletContext().getAttribute
            (ServletContext.TEMPDIR);
        // Convert all '/' characters to '.' in resourcePath
        String convertedResourcePath = path.replace('/', '.');
        File contentFile = new File(tempDir, convertedResourcePath);
        if (contentFile.createNewFile()) {
            // Clean up contentFile when Tomcat is terminated
            contentFile.deleteOnExit();
        }

        try (RandomAccessFile randAccessContentFile =
            new RandomAccessFile(contentFile, "rw");) {

            WebResource oldResource = resources.getResource(path);

            // Copy data in oldRevisionContent to contentFile
            if (oldResource.isFile()) {
                try (BufferedInputStream bufOldRevStream =
                    new BufferedInputStream(oldResource.getInputStream(),
                            BUFFER_SIZE);) {

                    int numBytesRead;
                    byte[] copyBuffer = new byte[BUFFER_SIZE];
                    while ((numBytesRead = bufOldRevStream.read(copyBuffer)) != -1) {
                        randAccessContentFile.write(copyBuffer, 0, numBytesRead);
                    }

                }
            }

            randAccessContentFile.setLength(range.length);

            // Append data in request input stream to contentFile
            randAccessContentFile.seek(range.start);
            int numBytesRead;
            byte[] transferBuffer = new byte[BUFFER_SIZE];
            try (BufferedInputStream requestBufInStream =
                new BufferedInputStream(req.getInputStream(), BUFFER_SIZE);) {
                while ((numBytesRead = requestBufInStream.read(transferBuffer)) != -1) {
                    randAccessContentFile.write(transferBuffer, 0, numBytesRead);
                }
            }
        }

        return contentFile;
    }


    /**
     * 处理指定资源的DELETE请求.
     *
     * @param req The servlet request we are processing
     * @param resp The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String path = getRelativePath(req);

        WebResource resource = resources.getResource(path);

        if (resource.exists()) {
            if (resource.delete()) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }

    }


    /**
     * 检查可选的IF标头中指定的条件是否满足.
     *
     * @param request   The servlet request we are processing
     * @param response  The servlet response we are creating
     * @param resource  The resource
     * 
     * @return <code>true</code> 如果资源满足所有指定条件,
     * <code>false</code>如果任何条件不满意, 在这种情况下请求处理停止
     * @throws IOException an IO error occurred
     */
    protected boolean checkIfHeaders(HttpServletRequest request,
                                     HttpServletResponse response,
                                     WebResource resource)
        throws IOException {

        return checkIfMatch(request, response, resource)
            && checkIfModifiedSince(request, response, resource)
            && checkIfNoneMatch(request, response, resource)
            && checkIfUnmodifiedSince(request, response, resource);

    }


    /**
     * URL rewriter.
     *
     * @param path 必须重写的路径
     * @return the rewritten path
     */
    protected String rewriteUrl(String path) {
        return URLEncoder.DEFAULT.encode(path, StandardCharsets.UTF_8);
    }


    /**
     * 服务指定资源, 可选地包含数据内容.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @param content  Should the content be included?
     * @param encoding 如果需要访问作为字符而不是字节的源，需要使用的该编码
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void serveResource(HttpServletRequest request,
                                 HttpServletResponse response,
                                 boolean content,
                                 String encoding)
        throws IOException, ServletException {

        boolean serveContent = content;

        // 标识请求的资源路径
        String path = getRelativePath(request, true);

        if (debug > 0) {
            if (serveContent)
                log("DefaultServlet.serveResource:  Serving resource '" +
                    path + "' headers and data");
            else
                log("DefaultServlet.serveResource:  Serving resource '" +
                    path + "' headers only");
        }

        if (path.length() == 0) {
            // Context root redirect
            doDirectoryRedirect(request, response);
            return;
        }

        WebResource resource = resources.getResource(path);
        boolean isError = DispatcherType.ERROR == request.getDispatcherType();

        if (!resource.exists()) {
            // Check if we're included, 因此可以在错误中返回适当的缺失资源名称
            String requestUri = (String) request.getAttribute(
                    RequestDispatcher.INCLUDE_REQUEST_URI);
            if (requestUri == null) {
                requestUri = request.getRequestURI();
            } else {
                // We're included
                // SRV.9.3 says we must throw a FNFE
                throw new FileNotFoundException(sm.getString(
                        "defaultServlet.missingResource", requestUri));
            }

            if (isError) {
                response.sendError(((Integer) request.getAttribute(
                        RequestDispatcher.ERROR_STATUS_CODE)).intValue());
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, requestUri);
            }
            return;
        }

        if (!resource.canRead()) {
            // Check if we're included, 因此可以在错误中返回适当的缺失资源名称
            String requestUri = (String) request.getAttribute(
                    RequestDispatcher.INCLUDE_REQUEST_URI);
            if (requestUri == null) {
                requestUri = request.getRequestURI();
            } else {
                // We're included
                // 规范没有说明在这种情况下该怎么做，但FNFE似乎是合理的
                throw new FileNotFoundException(sm.getString(
                        "defaultServlet.missingResource", requestUri));
            }

            if (isError) {
                response.sendError(((Integer) request.getAttribute(
                        RequestDispatcher.ERROR_STATUS_CODE)).intValue());
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, requestUri);
            }
            return;
        }

        boolean included = false;
        // 检查可选的IF报头中指定的条件是否满足.
        if (resource.isFile()) {
            // Checking If headers
            included = (request.getAttribute(
                    RequestDispatcher.INCLUDE_CONTEXT_PATH) != null);
            if (!included && !isError && !checkIfHeaders(request, response, resource)) {
                return;
            }
        }

        // Find content type.
        String contentType = resource.getMimeType();
        if (contentType == null) {
            contentType = getServletContext().getMimeType(resource.getName());
            resource.setMimeType(contentType);
        }

        // 这些需要反射原始资源, not the potentially
        // precompressed version of the resource so get them now if they are going to
        // be needed later
        String eTag = null;
        String lastModifiedHttp = null;
        if (resource.isFile() && !isError) {
            eTag = resource.getETag();
            lastModifiedHttp = resource.getLastModifiedHttp();
        }


        // Serve a precompressed version of the file if present
        boolean usingPrecompressedVersion = false;
        if (compressionFormats.length > 0 && !included && resource.isFile() &&
                !pathEndsWithCompressedExtension(path)) {
            List<PrecompressedResource> precompressedResources =
                    getAvailablePrecompressedResources(path);
            if (!precompressedResources.isEmpty()) {
                Collection<String> varyHeaders = response.getHeaders("Vary");
                boolean addRequired = true;
                for (String varyHeader : varyHeaders) {
                    if ("*".equals(varyHeader) ||
                            "accept-encoding".equalsIgnoreCase(varyHeader)) {
                        addRequired = false;
                        break;
                    }
                }
                if (addRequired) {
                    response.addHeader("Vary", "accept-encoding");
                }
                PrecompressedResource bestResource =
                        getBestPrecompressedResource(request, precompressedResources);
                if (bestResource != null) {
                    response.addHeader("Content-Encoding", bestResource.format.encoding);
                    resource = bestResource.resource;
                    usingPrecompressedVersion = true;
                }
            }
        }

        ArrayList<Range> ranges = null;
        long contentLength = -1L;

        if (resource.isDirectory()) {
            if (!path.endsWith("/")) {
                doDirectoryRedirect(request, response);
                return;
            }

            // Skip directory listings if we have been configured to
            // suppress them
            if (!listings) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                   request.getRequestURI());
                return;
            }
            contentType = "text/html;charset=UTF-8";
        } else {
            if (!isError) {
                if (useAcceptRanges) {
                    // Accept ranges header
                    response.setHeader("Accept-Ranges", "bytes");
                }

                // Parse range specifier
                ranges = parseRange(request, response, resource);

                // ETag header
                response.setHeader("ETag", eTag);

                // Last-Modified header
                response.setHeader("Last-Modified", lastModifiedHttp);
            }

            // Get content length
            contentLength = resource.getContentLength();
            // Special case for zero length files, which would cause a
            // (silent) ISE when setting the output buffer size
            if (contentLength == 0L) {
                serveContent = false;
            }
        }

        ServletOutputStream ostream = null;
        PrintWriter writer = null;

        if (serveContent) {
            // Trying to retrieve the servlet output stream
            try {
                ostream = response.getOutputStream();
            } catch (IllegalStateException e) {
                // If it fails, we try to get a Writer instead if we're
                // trying to serve a text file
                if (!usingPrecompressedVersion &&
                        ((contentType == null) ||
                                (contentType.startsWith("text")) ||
                                (contentType.endsWith("xml")) ||
                                (contentType.contains("/javascript")))
                        ) {
                    writer = response.getWriter();
                    // Cannot reliably serve partial content with a Writer
                    ranges = FULL;
                } else {
                    throw e;
                }
            }
        }

        // Check to see if a Filter, Valve of wrapper has written some content.
        // If it has, disable range requests and setting of a content length
        // since neither can be done reliably.
        ServletResponse r = response;
        long contentWritten = 0;
        while (r instanceof ServletResponseWrapper) {
            r = ((ServletResponseWrapper) r).getResponse();
        }
        if (r instanceof ResponseFacade) {
            contentWritten = ((ResponseFacade) r).getContentWritten();
        }
        if (contentWritten > 0) {
            ranges = FULL;
        }

        if (resource.isDirectory() ||
                isError ||
                ( (ranges == null || ranges.isEmpty())
                        && request.getHeader("Range") == null ) ||
                ranges == FULL ) {

            // Set the appropriate output headers
            if (contentType != null) {
                if (debug > 0)
                    log("DefaultServlet.serveFile:  contentType='" +
                        contentType + "'");
                response.setContentType(contentType);
            }
            if (resource.isFile() && contentLength >= 0 &&
                    (!serveContent || ostream != null)) {
                if (debug > 0)
                    log("DefaultServlet.serveFile:  contentLength=" +
                        contentLength);
                // Don't set a content length if something else has already
                // written to the response.
                if (contentWritten == 0) {
                    response.setContentLengthLong(contentLength);
                }
            }

            if (serveContent) {
                try {
                    response.setBufferSize(output);
                } catch (IllegalStateException e) {
                    // Silent catch
                }
                InputStream renderResult = null;
                if (ostream == null) {
                    // Output via a writer so can't use sendfile or write
                    // content directly.
                    if (resource.isDirectory()) {
                        renderResult = render(getPathPrefix(request), resource, encoding);
                    } else {
                        renderResult = resource.getInputStream();
                    }
                    copy(renderResult, writer, encoding);
                } else {
                    // Output is via an InputStream
                    if (resource.isDirectory()) {
                        renderResult = render(getPathPrefix(request), resource, encoding);
                    } else {
                        // Output is content of resource
                        if (!checkSendfile(request, response, resource,
                                contentLength, null)) {
                            // sendfile not possible so check if resource
                            // content is available directly
                            byte[] resourceBody = resource.getContent();
                            if (resourceBody == null) {
                                // Resource content not available, use
                                // inputstream
                                renderResult = resource.getInputStream();
                            } else {
                                // Use the resource content directly
                                ostream.write(resourceBody);
                            }
                        }
                    }
                    // If a stream was configured, it needs to be copied to
                    // the output (this method closes the stream)
                    if (renderResult != null) {
                        copy(renderResult, ostream);
                    }
                }
            }

        } else {

            if ((ranges == null) || (ranges.isEmpty()))
                return;

            // Partial content response.

            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);

            if (ranges.size() == 1) {

                Range range = ranges.get(0);
                response.addHeader("Content-Range", "bytes "
                                   + range.start
                                   + "-" + range.end + "/"
                                   + range.length);
                long length = range.end - range.start + 1;
                response.setContentLengthLong(length);

                if (contentType != null) {
                    if (debug > 0)
                        log("DefaultServlet.serveFile:  contentType='" +
                            contentType + "'");
                    response.setContentType(contentType);
                }

                if (serveContent) {
                    try {
                        response.setBufferSize(output);
                    } catch (IllegalStateException e) {
                        // Silent catch
                    }
                    if (ostream != null) {
                        if (!checkSendfile(request, response, resource,
                                range.end - range.start + 1, range))
                            copy(resource, ostream, range);
                    } else {
                        // we should not get here
                        throw new IllegalStateException();
                    }
                }
            } else {
                response.setContentType("multipart/byteranges; boundary="
                                        + mimeSeparation);
                if (serveContent) {
                    try {
                        response.setBufferSize(output);
                    } catch (IllegalStateException e) {
                        // Silent catch
                    }
                    if (ostream != null) {
                        copy(resource, ostream, ranges.iterator(), contentType);
                    } else {
                        // we should not get here
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    private boolean pathEndsWithCompressedExtension(String path) {
        for (CompressionFormat format : compressionFormats) {
            if (path.endsWith(format.extension)) {
                return true;
            }
        }
        return false;
    }

    private List<PrecompressedResource> getAvailablePrecompressedResources(String path) {
        List<PrecompressedResource> ret = new ArrayList<>(compressionFormats.length);
        for (CompressionFormat format : compressionFormats) {
            WebResource precompressedResource = resources.getResource(path + format.extension);
            if (precompressedResource.exists() && precompressedResource.isFile()) {
                ret.add(new PrecompressedResource(precompressedResource, format));
            }
        }
        return ret;
    }

    /**
     * 将客户端首选编码格式与可用预压缩资源匹配.
     *
     * @param request   The servlet request we are processing
     * @param precompressedResources   可用预压缩资源列表.
     * 
     * @return 最佳匹配预压缩资源, 或 null.
     */
    private PrecompressedResource getBestPrecompressedResource(HttpServletRequest request,
            List<PrecompressedResource> precompressedResources) {
        Enumeration<String> headers = request.getHeaders("Accept-Encoding");
        PrecompressedResource bestResource = null;
        double bestResourceQuality = 0;
        int bestResourcePreference = Integer.MAX_VALUE;
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            for (String preference : header.split(",")) {
                double quality = 1;
                int qualityIdx = preference.indexOf(';');
                if (qualityIdx > 0) {
                    int equalsIdx = preference.indexOf('=', qualityIdx + 1);
                    if (equalsIdx == -1) {
                        continue;
                    }
                    quality = Double.parseDouble(preference.substring(equalsIdx + 1).trim());
                }
                if (quality >= bestResourceQuality) {
                    String encoding = preference;
                    if (qualityIdx > 0) {
                        encoding = encoding.substring(0, qualityIdx);
                    }
                    encoding = encoding.trim();
                    if ("identity".equals(encoding)) {
                        bestResource = null;
                        bestResourceQuality = quality;
                        bestResourcePreference = Integer.MAX_VALUE;
                        continue;
                    }
                    if ("*".equals(encoding)) {
                        bestResource = precompressedResources.get(0);
                        bestResourceQuality = quality;
                        bestResourcePreference = 0;
                        continue;
                    }
                    for (int i = 0; i < precompressedResources.size(); ++i) {
                        PrecompressedResource resource = precompressedResources.get(i);
                        if (encoding.equals(resource.format.encoding)) {
                            if (quality > bestResourceQuality || i < bestResourcePreference) {
                                bestResource = resource;
                                bestResourceQuality = quality;
                                bestResourcePreference = i;
                            }
                            break;
                        }
                    }
                }
            }
        }
        return bestResource;
    }

    private void doDirectoryRedirect(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        StringBuilder location = new StringBuilder(request.getRequestURI());
        location.append('/');
        if (request.getQueryString() != null) {
            location.append('?');
            location.append(request.getQueryString());
        }
        response.sendRedirect(response.encodeRedirectURL(location.toString()));
    }

    /**
     * 解析content-range标头.
     *
     * @param request The servlet request we a)re processing
     * @param response The servlet response we are creating
     * @return the range object
     * @throws IOException an IO error occurred
     */
    protected Range parseContentRange(HttpServletRequest request,
                                      HttpServletResponse response)
        throws IOException {

        // Retrieving the content-range header (if any is specified
        String rangeHeader = request.getHeader("Content-Range");

        if (rangeHeader == null)
            return null;

        // bytes is the only range unit supported
        if (!rangeHeader.startsWith("bytes")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        rangeHeader = rangeHeader.substring(6).trim();

        int dashPos = rangeHeader.indexOf('-');
        int slashPos = rangeHeader.indexOf('/');

        if (dashPos == -1) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        if (slashPos == -1) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        Range range = new Range();

        try {
            range.start = Long.parseLong(rangeHeader.substring(0, dashPos));
            range.end =
                Long.parseLong(rangeHeader.substring(dashPos + 1, slashPos));
            range.length = Long.parseLong
                (rangeHeader.substring(slashPos + 1, rangeHeader.length()));
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        if (!range.validate()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        return range;

    }


    /**
     * 解析range标头.
     *
     * @param request   The servlet request we are processing
     * @param response  The servlet response we are creating
     * @param resource  The resource
     * 
     * @return a list of ranges
     * @throws IOException an IO error occurred
     */
    protected ArrayList<Range> parseRange(HttpServletRequest request,
            HttpServletResponse response,
            WebResource resource) throws IOException {

        // Checking If-Range
        String headerValue = request.getHeader("If-Range");

        if (headerValue != null) {

            long headerValueTime = (-1L);
            try {
                headerValueTime = request.getDateHeader("If-Range");
            } catch (IllegalArgumentException e) {
                // Ignore
            }

            String eTag = resource.getETag();
            long lastModified = resource.getLastModified();

            if (headerValueTime == (-1L)) {

                // If the ETag the client gave does not match the entity
                // etag, then the entire entity is returned.
                if (!eTag.equals(headerValue.trim()))
                    return FULL;

            } else {

                // 如果客户端获取的实体的时间戳比实体的最后一个修改日期早, 返回整个实体.
                if (lastModified > (headerValueTime + 1000))
                    return FULL;

            }

        }

        long fileLength = resource.getContentLength();

        if (fileLength == 0)
            return null;

        // Retrieving the range header (if any is specified
        String rangeHeader = request.getHeader("Range");

        if (rangeHeader == null)
            return null;
        // bytes is the only range unit supported (and I don't see the point
        // of adding new ones).
        if (!rangeHeader.startsWith("bytes")) {
            response.addHeader("Content-Range", "bytes */" + fileLength);
            response.sendError
                (HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            return null;
        }

        rangeHeader = rangeHeader.substring(6);

        // Vector which will contain all the ranges which are successfully
        // parsed.
        ArrayList<Range> result = new ArrayList<>();
        StringTokenizer commaTokenizer = new StringTokenizer(rangeHeader, ",");

        // Parsing the range list
        while (commaTokenizer.hasMoreTokens()) {
            String rangeDefinition = commaTokenizer.nextToken().trim();

            Range currentRange = new Range();
            currentRange.length = fileLength;

            int dashPos = rangeDefinition.indexOf('-');

            if (dashPos == -1) {
                response.addHeader("Content-Range", "bytes */" + fileLength);
                response.sendError
                    (HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return null;
            }

            if (dashPos == 0) {

                try {
                    long offset = Long.parseLong(rangeDefinition);
                    currentRange.start = fileLength + offset;
                    currentRange.end = fileLength - 1;
                } catch (NumberFormatException e) {
                    response.addHeader("Content-Range",
                                       "bytes */" + fileLength);
                    response.sendError
                        (HttpServletResponse
                         .SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    return null;
                }

            } else {

                try {
                    currentRange.start = Long.parseLong
                        (rangeDefinition.substring(0, dashPos));
                    if (dashPos < rangeDefinition.length() - 1)
                        currentRange.end = Long.parseLong
                            (rangeDefinition.substring
                             (dashPos + 1, rangeDefinition.length()));
                    else
                        currentRange.end = fileLength - 1;
                } catch (NumberFormatException e) {
                    response.addHeader("Content-Range",
                                       "bytes */" + fileLength);
                    response.sendError
                        (HttpServletResponse
                         .SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    return null;
                }

            }

            if (!currentRange.validate()) {
                response.addHeader("Content-Range", "bytes */" + fileLength);
                response.sendError
                    (HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return null;
            }

            result.add(currentRange);
        }

        return result;
    }


    /**
     * Decide which way to render. HTML or XML.
     *
     * @param contextPath The path
     * @param resource The resource
     *
     * @return the input stream with the rendered output
     *
     * @throws IOException an IO error occurred
     * @throws ServletException rendering error
     *
     * @deprecated Unused. Will be removed in Tomcat 9
     */
    @Deprecated
    protected InputStream render(String contextPath, WebResource resource)
        throws IOException, ServletException {
        return render(contextPath, resource, null);
    }

    protected InputStream render(String contextPath, WebResource resource, String encoding)
        throws IOException, ServletException {

        Source xsltSource = findXsltSource(resource);

        if (xsltSource == null) {
            return renderHtml(contextPath, resource, encoding);
        }
        return renderXml(contextPath, resource, xsltSource, encoding);
    }


    /**
     * 返回一个InputStream到这个目录的内容的XML表示.
     *
     * @param contextPath 内部路径是相对的上下文路径
     * @param resource The associated resource
     * @param xsltSource The XSL stylesheet
     *
     * @return the XML data
     *
     * @throws IOException an IO error occurred
     * @throws ServletException rendering error
     *
     * @deprecated Unused. Will be removed in Tomcat 9
     */
    @Deprecated
    protected InputStream renderXml(String contextPath, WebResource resource, Source xsltSource)
        throws IOException, ServletException {
        return renderXml(contextPath, resource, xsltSource, null);
    }

    protected InputStream renderXml(String contextPath, WebResource resource, Source xsltSource,
            String encoding)
        throws IOException, ServletException {

        StringBuilder sb = new StringBuilder();

        sb.append("<?xml version=\"1.0\"?>");
        sb.append("<listing ");
        sb.append(" contextPath='");
        sb.append(contextPath);
        sb.append("'");
        sb.append(" directory='");
        sb.append(resource.getName());
        sb.append("' ");
        sb.append(" hasParent='").append(!resource.getName().equals("/"));
        sb.append("'>");

        sb.append("<entries>");

        String[] entries = resources.list(resource.getWebappPath());

        // rewriteUrl(contextPath) is expensive. cache result for later reuse
        String rewrittenContextPath =  rewriteUrl(contextPath);
        String directoryWebappPath = resource.getWebappPath();

        for (String entry : entries) {

            if (entry.equalsIgnoreCase("WEB-INF") ||
                    entry.equalsIgnoreCase("META-INF") ||
                    entry.equalsIgnoreCase(localXsltFile))
                continue;

            if ((directoryWebappPath + entry).equals(contextXsltFile))
                continue;

            WebResource childResource =
                    resources.getResource(directoryWebappPath + entry);
            if (!childResource.exists()) {
                continue;
            }

            sb.append("<entry");
            sb.append(" type='")
              .append(childResource.isDirectory()?"dir":"file")
              .append("'");
            sb.append(" urlPath='")
              .append(rewrittenContextPath)
              .append(rewriteUrl(directoryWebappPath + entry))
              .append(childResource.isDirectory()?"/":"")
              .append("'");
            if (childResource.isFile()) {
                sb.append(" size='")
                  .append(renderSize(childResource.getContentLength()))
                  .append("'");
            }
            sb.append(" date='")
              .append(childResource.getLastModifiedHttp())
              .append("'");

            sb.append(">");
            sb.append(Escape.htmlElementContent(entry));
            if (childResource.isDirectory())
                sb.append("/");
            sb.append("</entry>");
        }
        sb.append("</entries>");

        String readme = getReadme(resource, encoding);

        if (readme!=null) {
            sb.append("<readme><![CDATA[");
            sb.append(readme);
            sb.append("]]></readme>");
        }

        sb.append("</listing>");

        // 防止可能的内存泄漏. 确保Transformer 和 TransformerFactory 未从Web应用程序加载.
        ClassLoader original;
        if (Globals.IS_SECURITY_ENABLED) {
            PrivilegedGetTccl pa = new PrivilegedGetTccl();
            original = AccessController.doPrivileged(pa);
        } else {
            original = Thread.currentThread().getContextClassLoader();
        }
        try {
            if (Globals.IS_SECURITY_ENABLED) {
                PrivilegedSetTccl pa =
                        new PrivilegedSetTccl(DefaultServlet.class.getClassLoader());
                AccessController.doPrivileged(pa);
            } else {
                Thread.currentThread().setContextClassLoader(
                        DefaultServlet.class.getClassLoader());
            }

            TransformerFactory tFactory = TransformerFactory.newInstance();
            Source xmlSource = new StreamSource(new StringReader(sb.toString()));
            Transformer transformer = tFactory.newTransformer(xsltSource);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            OutputStreamWriter osWriter = new OutputStreamWriter(stream, "UTF8");
            StreamResult out = new StreamResult(osWriter);
            transformer.transform(xmlSource, out);
            osWriter.flush();
            return (new ByteArrayInputStream(stream.toByteArray()));
        } catch (TransformerException e) {
            throw new ServletException("XSL transformer error", e);
        } finally {
            if (Globals.IS_SECURITY_ENABLED) {
                PrivilegedSetTccl pa = new PrivilegedSetTccl(original);
                AccessController.doPrivileged(pa);
            } else {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    /**
     * 返回InputStream 到该目录内容的HTML表示形式..
     *
     * @param contextPath 内部路径是相对的上下文路径
     * @param resource    The associated resource
     *
     * @return the HTML data
     *
     * @throws IOException an IO error occurred
     *
     * @deprecated Unused. Will be removed in Tomcat 9
     */
    @Deprecated
    protected InputStream renderHtml(String contextPath, WebResource resource)
        throws IOException {
        return renderHtml(contextPath, resource, null);
    }

    protected InputStream renderHtml(String contextPath, WebResource resource, String encoding)
        throws IOException {

        // Prepare a writer to a buffered area
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        OutputStreamWriter osWriter = new OutputStreamWriter(stream, "UTF8");
        PrintWriter writer = new PrintWriter(osWriter);

        StringBuilder sb = new StringBuilder();

        String[] entries = resources.list(resource.getWebappPath());

        // rewriteUrl(contextPath) is expensive. cache result for later reuse
        String rewrittenContextPath =  rewriteUrl(contextPath);
        String directoryWebappPath = resource.getWebappPath();

        // Render the page header
        sb.append("<html>\r\n");
        sb.append("<head>\r\n");
        sb.append("<title>");
        sb.append(sm.getString("directory.title", directoryWebappPath));
        sb.append("</title>\r\n");
        sb.append("<STYLE><!--");
        sb.append(org.apache.catalina.util.TomcatCSS.TOMCAT_CSS);
        sb.append("--></STYLE> ");
        sb.append("</head>\r\n");
        sb.append("<body>");
        sb.append("<h1>");
        sb.append(sm.getString("directory.title", directoryWebappPath));

        // Render the link to our parent (if required)
        String parentDirectory = directoryWebappPath;
        if (parentDirectory.endsWith("/")) {
            parentDirectory =
                parentDirectory.substring(0, parentDirectory.length() - 1);
        }
        int slash = parentDirectory.lastIndexOf('/');
        if (slash >= 0) {
            String parent = directoryWebappPath.substring(0, slash);
            sb.append(" - <a href=\"");
            sb.append(rewrittenContextPath);
            if (parent.equals(""))
                parent = "/";
            sb.append(rewriteUrl(parent));
            if (!parent.endsWith("/"))
                sb.append("/");
            sb.append("\">");
            sb.append("<b>");
            sb.append(sm.getString("directory.parent", parent));
            sb.append("</b>");
            sb.append("</a>");
        }

        sb.append("</h1>");
        sb.append("<HR size=\"1\" noshade=\"noshade\">");

        sb.append("<table width=\"100%\" cellspacing=\"0\"" +
                     " cellpadding=\"5\" align=\"center\">\r\n");

        // Render the column headings
        sb.append("<tr>\r\n");
        sb.append("<td align=\"left\"><font size=\"+1\"><strong>");
        sb.append(sm.getString("directory.filename"));
        sb.append("</strong></font></td>\r\n");
        sb.append("<td align=\"center\"><font size=\"+1\"><strong>");
        sb.append(sm.getString("directory.size"));
        sb.append("</strong></font></td>\r\n");
        sb.append("<td align=\"right\"><font size=\"+1\"><strong>");
        sb.append(sm.getString("directory.lastModified"));
        sb.append("</strong></font></td>\r\n");
        sb.append("</tr>");

        boolean shade = false;
        for (String entry : entries) {
            if (entry.equalsIgnoreCase("WEB-INF") ||
                entry.equalsIgnoreCase("META-INF"))
                continue;

            WebResource childResource =
                    resources.getResource(directoryWebappPath + entry);
            if (!childResource.exists()) {
                continue;
            }

            sb.append("<tr");
            if (shade)
                sb.append(" bgcolor=\"#eeeeee\"");
            sb.append(">\r\n");
            shade = !shade;

            sb.append("<td align=\"left\">&nbsp;&nbsp;\r\n");
            sb.append("<a href=\"");
            sb.append(rewrittenContextPath);
            sb.append(rewriteUrl(directoryWebappPath + entry));
            if (childResource.isDirectory())
                sb.append("/");
            sb.append("\"><tt>");
            sb.append(Escape.htmlElementContent(entry));
            if (childResource.isDirectory())
                sb.append("/");
            sb.append("</tt></a></td>\r\n");

            sb.append("<td align=\"right\"><tt>");
            if (childResource.isDirectory())
                sb.append("&nbsp;");
            else
                sb.append(renderSize(childResource.getContentLength()));
            sb.append("</tt></td>\r\n");

            sb.append("<td align=\"right\"><tt>");
            sb.append(childResource.getLastModifiedHttp());
            sb.append("</tt></td>\r\n");

            sb.append("</tr>\r\n");
        }

        // Render the page footer
        sb.append("</table>\r\n");

        sb.append("<HR size=\"1\" noshade=\"noshade\">");

        String readme = getReadme(resource, encoding);
        if (readme!=null) {
            sb.append(readme);
            sb.append("<HR size=\"1\" noshade=\"noshade\">");
        }

        if (showServerInfo) {
            sb.append("<h3>").append(ServerInfo.getServerInfo()).append("</h3>");
        }
        sb.append("</body>\r\n");
        sb.append("</html>\r\n");

        // Return an input stream to the underlying bytes
        writer.write(sb.toString());
        writer.flush();
        return (new ByteArrayInputStream(stream.toByteArray()));

    }


    /**
     * 呈现指定的文件大小 (in bytes).
     *
     * @param size File size (in bytes)
     * @return the formatted size
     */
    protected String renderSize(long size) {

        long leftSide = size / 1024;
        long rightSide = (size % 1024) / 103;   // Makes 1 digit
        if ((leftSide == 0) && (rightSide == 0) && (size > 0))
            rightSide = 1;

        return ("" + leftSide + "." + rightSide + " kb");

    }


    /**
     * Get the readme file as a string.
     * @param directory The directory to search
     * @return the readme for the specified directory
     *
     * @deprecated Unused. Will be removed in Tomcat 9
     */
    @Deprecated
    protected String getReadme(WebResource directory) {
        return getReadme(directory, null);
    }

    /**
     * Get the readme file as a string.
     * 
     * @param directory The directory to search
     * @param encoding The readme encoding
     * @return the readme for the specified directory
     */
    protected String getReadme(WebResource directory, String encoding) {

        if (readmeFile != null) {
            WebResource resource = resources.getResource(
                    directory.getWebappPath() + readmeFile);
            if (resource.isFile()) {
                StringWriter buffer = new StringWriter();
                InputStreamReader reader = null;
                try (InputStream is = resource.getInputStream();){
                    if (encoding != null) {
                        reader = new InputStreamReader(is, encoding);
                    } else {
                        reader = new InputStreamReader(is);
                    }
                    copyRange(reader, new PrintWriter(buffer));
                } catch (IOException e) {
                    log("Failure to close reader", e);
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                        }
                    }
                }
                return buffer.toString();
            } else {
                if (debug > 10)
                    log("readme '" + readmeFile + "' not found");

                return null;
            }
        }

        return null;
    }


    /**
     * 返回xsl模板的 Source.
     * 
     * @param directory The directory to search
     * @return the source for the specified directory
     * @throws IOException an IO error occurred
     */
    protected Source findXsltSource(WebResource directory)
        throws IOException {

        if (localXsltFile != null) {
            WebResource resource = resources.getResource(
                    directory.getWebappPath() + localXsltFile);
            if (resource.isFile()) {
                InputStream is = resource.getInputStream();
                if (is != null) {
                    if (Globals.IS_SECURITY_ENABLED) {
                        return secureXslt(is);
                    } else {
                        return new StreamSource(is);
                    }
                }
            }
            if (debug > 10) {
                log("localXsltFile '" + localXsltFile + "' not found");
            }
        }

        if (contextXsltFile != null) {
            InputStream is =
                getServletContext().getResourceAsStream(contextXsltFile);
            if (is != null) {
                if (Globals.IS_SECURITY_ENABLED) {
                    return secureXslt(is);
                } else {
                    return new StreamSource(is);
                }
            }

            if (debug > 10)
                log("contextXsltFile '" + contextXsltFile + "' not found");
        }

        /*  Open and read in file in one fell swoop to reduce chance
         *  chance of leaving handle open.
         */
        if (globalXsltFile != null) {
            File f = validateGlobalXsltFile();
            if (f != null){
                try (FileInputStream fis = new FileInputStream(f)){
                    byte b[] = new byte[(int)f.length()]; /* danger! */
                    fis.read(b);
                    return new StreamSource(new ByteArrayInputStream(b));
                }
            }
        }

        return null;
    }


    private File validateGlobalXsltFile() {
        Context context = resources.getContext();

        File baseConf = new File(context.getCatalinaBase(), "conf");
        File result = validateGlobalXsltFile(baseConf);
        if (result == null) {
            File homeConf = new File(context.getCatalinaHome(), "conf");
            if (!baseConf.equals(homeConf)) {
                result = validateGlobalXsltFile(homeConf);
            }
        }

        return result;
    }


    private File validateGlobalXsltFile(File base) {
        File candidate = new File(globalXsltFile);
        if (!candidate.isAbsolute()) {
            candidate = new File(base, globalXsltFile);
        }

        if (!candidate.isFile()) {
            return null;
        }

        // First check that the resulting path is under the provided base
        try {
            if (!candidate.getCanonicalPath().startsWith(base.getCanonicalPath())) {
                return null;
            }
        } catch (IOException ioe) {
            return null;
        }

        // Next check that an .xsl or .xslt file has been specified
        String nameLower = candidate.getName().toLowerCase(Locale.ENGLISH);
        if (!nameLower.endsWith(".xslt") && !nameLower.endsWith(".xsl")) {
            return null;
        }

        return candidate;
    }


    private Source secureXslt(InputStream is) {
        // Need to filter out any external entities
        Source result = null;
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver(secureEntityResolver);
            Document document = builder.parse(is);
            result = new DOMSource(document);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            if (debug > 0) {
                log(e.getMessage(), e);
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        return result;
    }


    // -------------------------------------------------------- protected Methods

    /**
     * 检查是否可以使用sendfile.
     * 
     * @param request The Servlet request
     * @param response The Servlet response
     * @param resource The resource
     * @param length 将被写入的长度(只有当range 是 null 时)
     * @param range 将被写入的range
     * 
     * @return <code>true</code>如果应该使用sendfile (然后将写入委托到端点)
     */
    protected boolean checkSendfile(HttpServletRequest request,
                                  HttpServletResponse response,
                                  WebResource resource,
                                  long length, Range range) {
        String canonicalPath;
        if (sendfileSize > 0
            && length > sendfileSize
            && (Boolean.TRUE.equals(request.getAttribute(Globals.SENDFILE_SUPPORTED_ATTR)))
            && (request.getClass().getName().equals("org.apache.catalina.connector.RequestFacade"))
            && (response.getClass().getName().equals("org.apache.catalina.connector.ResponseFacade"))
            && resource.isFile()
            && ((canonicalPath = resource.getCanonicalPath()) != null)
            ) {
            request.setAttribute(Globals.SENDFILE_FILENAME_ATTR, canonicalPath);
            if (range == null) {
                request.setAttribute(Globals.SENDFILE_FILE_START_ATTR, Long.valueOf(0L));
                request.setAttribute(Globals.SENDFILE_FILE_END_ATTR, Long.valueOf(length));
            } else {
                request.setAttribute(Globals.SENDFILE_FILE_START_ATTR, Long.valueOf(range.start));
                request.setAttribute(Globals.SENDFILE_FILE_END_ATTR, Long.valueOf(range.end + 1));
            }
            return true;
        }
        return false;
    }


    /**
     * 检查是否满足If-Match条件.
     *
     * @param request   The servlet request we are processing
     * @param response  The servlet response we are creating
     * @param resource  The resource
     * @return <code>true</code>如果资源满足指定条件;
     *  <code>false</code>如果条件不满足，在这种情况下请求处理停止
     * @throws IOException an IO error occurred
     */
    protected boolean checkIfMatch(HttpServletRequest request,
            HttpServletResponse response, WebResource resource)
            throws IOException {

        String eTag = resource.getETag();
        String headerValue = request.getHeader("If-Match");
        if (headerValue != null) {
            if (headerValue.indexOf('*') == -1) {

                StringTokenizer commaTokenizer = new StringTokenizer
                    (headerValue, ",");
                boolean conditionSatisfied = false;

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

                // If none of the given ETags match, 412 Precondition failed is
                // sent back
                if (!conditionSatisfied) {
                    response.sendError
                        (HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * 检查是否满足if-modified-since条件.
     *
     * @param request   The servlet request we are processing
     * @param response  The servlet response we are creating
     * @param resource  The resource
     * @return boolean true: 如果资源满足指定条件;
     * false : 如果条件不满足，在这种情况下请求处理停止
     */
    protected boolean checkIfModifiedSince(HttpServletRequest request,
            HttpServletResponse response, WebResource resource) {
        try {
            long headerValue = request.getDateHeader("If-Modified-Since");
            long lastModified = resource.getLastModified();
            if (headerValue != -1) {

                // If an If-None-Match header has been specified, if modified since
                // is ignored.
                if ((request.getHeader("If-None-Match") == null)
                    && (lastModified < headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader("ETag", resource.getETag());

                    return false;
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;
    }


    /**
     * 检查是否满足if-none-match条件.
     *
     * @param request   The servlet request we are processing
     * @param response  The servlet response we are creating
     * @param resource  The resource
     * @return boolean true: 如果资源满足指定条件;
     * false : 如果条件不满足，在这种情况下请求处理停止
     * @throws IOException an IO error occurred
     */
    protected boolean checkIfNoneMatch(HttpServletRequest request,
            HttpServletResponse response, WebResource resource)
            throws IOException {

        String eTag = resource.getETag();
        String headerValue = request.getHeader("If-None-Match");
        if (headerValue != null) {

            boolean conditionSatisfied = false;

            if (!headerValue.equals("*")) {

                StringTokenizer commaTokenizer =
                    new StringTokenizer(headerValue, ",");

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

            } else {
                conditionSatisfied = true;
            }

            if (conditionSatisfied) {

                // For GET and HEAD, we should respond with
                // 304 Not Modified.
                // For every other method, 412 Precondition Failed is sent
                // back.
                if ( ("GET".equals(request.getMethod()))
                     || ("HEAD".equals(request.getMethod())) ) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader("ETag", eTag);

                    return false;
                }
                response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                return false;
            }
        }
        return true;
    }

    /**
     * 检查是否满足if-unmodified-since条件.
     *
     * @param request   The servlet request we are processing
     * @param response  The servlet response we are creating
     * @param resource  The resource
     * 
     * @return boolean true: 如果资源满足指定条件;
     * false : 如果条件不满足，在这种情况下请求处理停止
     * @throws IOException an IO error occurred
     */
    protected boolean checkIfUnmodifiedSince(HttpServletRequest request,
            HttpServletResponse response, WebResource resource)
            throws IOException {
        try {
            long lastModified = resource.getLastModified();
            long headerValue = request.getDateHeader("If-Unmodified-Since");
            if (headerValue != -1) {
                if ( lastModified >= (headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        } catch(IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;
    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流(即使面对一个异常).
     *
     * @param resource  源资源
     * @param is        读取源资源的输入流
     * @param ostream   要写入的输出流
     *
     * @exception IOException if an input/output error occurs
     *
     * @deprecated Unused. This will be removed in Tomcat 9.
     *             Use {@link #copy(InputStream, ServletOutputStream)}
     */
    @Deprecated
    protected void copy(WebResource resource, InputStream is,
                      ServletOutputStream ostream)
        throws IOException {
        copy(is, ostream);
    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流(即使面对一个异常).
     *
     * @param is        读取源资源的输入流
     * @param ostream   要写入的输出流
     *
     * @exception IOException if an input/output error occurs
     */
    protected void copy(InputStream is, ServletOutputStream ostream) throws IOException {

        IOException exception = null;
        InputStream istream = new BufferedInputStream(is, input);

        // Copy the input stream to the output stream
        exception = copyRange(istream, ostream);

        // Clean up the input stream
        istream.close();

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;
    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流(即使面对一个异常).
     *
     * @param resource  The source resource
     * @param is        读取源资源的输入流
     * @param writer    要写入的writer
     * @param encoding  读取源输入流时使用的编码
     *
     * @exception IOException if an input/output error occurs
     *
     * @deprecated Unused. This will be removed in Tomcat 9.
     *             Use {@link #copy(InputStream, PrintWriter, String)}
     */
    @Deprecated
    protected void copy(WebResource resource, InputStream is, PrintWriter writer,
            String encoding) throws IOException {
        copy(is, writer,encoding);
    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流(即使面对一个异常).
     *
     * @param is        读取源资源的输入流
     * @param writer    要写入的writer
     * @param encoding  读取源输入流时使用的编码
     *
     * @exception IOException if an input/output error occurs
     */
    protected void copy(InputStream is, PrintWriter writer, String encoding) throws IOException {
        IOException exception = null;

        Reader reader;
        if (encoding == null) {
            reader = new InputStreamReader(is);
        } else {
            reader = new InputStreamReader(is, encoding);
        }

        // Copy the input stream to the output stream
        exception = copyRange(reader, writer);

        // Clean up the reader
        reader.close();

        // Rethrow any exception that has occurred
        if (exception != null) {
            throw exception;
        }
    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流(即使面对一个异常).
     *
     * @param resource  The source resource
     * @param ostream   要写入的输出流
     * @param range     客户端要检索的Range
     * 
     * @exception IOException if an input/output error occurs
     */
    protected void copy(WebResource resource, ServletOutputStream ostream,
                      Range range)
        throws IOException {

        IOException exception = null;

        InputStream resourceInputStream = resource.getInputStream();
        InputStream istream =
            new BufferedInputStream(resourceInputStream, input);
        exception = copyRange(istream, ostream, range.start, range.end);

        // Clean up the input stream
        istream.close();

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流(即使面对一个异常).
     *
     * @param resource      The source resource
     * @param ostream       要写入的输出流
     * @param ranges        客户端希望检索的范围的枚举
     * @param contentType   资源的内容类型
     * 
     * @exception IOException if an input/output error occurs
     */
    protected void copy(WebResource resource, ServletOutputStream ostream,
                      Iterator<Range> ranges, String contentType)
        throws IOException {

        IOException exception = null;

        while ( (exception == null) && (ranges.hasNext()) ) {

            InputStream resourceInputStream = resource.getInputStream();
            try (InputStream istream = new BufferedInputStream(resourceInputStream, input)) {

                Range currentRange = ranges.next();

                // Writing MIME header.
                ostream.println();
                ostream.println("--" + mimeSeparation);
                if (contentType != null)
                    ostream.println("Content-Type: " + contentType);
                ostream.println("Content-Range: bytes " + currentRange.start
                               + "-" + currentRange.end + "/"
                               + currentRange.length);
                ostream.println();

                // Printing content
                exception = copyRange(istream, ostream, currentRange.start,
                                      currentRange.end);
            }
        }

        ostream.println();
        ostream.print("--" + mimeSeparation + "--");

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流(即使面对一个异常).
     *
     * @param istream 要读取的输入流
     * @param ostream 要写入的输出流
     * 
     * @return Exception which occurred during processing
     */
    protected IOException copyRange(InputStream istream,
                                  ServletOutputStream ostream) {

        // Copy the input stream to the output stream
        IOException exception = null;
        byte buffer[] = new byte[input];
        int len = buffer.length;
        while (true) {
            try {
                len = istream.read(buffer);
                if (len == -1)
                    break;
                ostream.write(buffer, 0, len);
            } catch (IOException e) {
                exception = e;
                len = -1;
                break;
            }
        }
        return exception;

    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流(即使面对一个异常).
     *
     * @param reader 要读取的 reader
     * @param writer 要写入的writer
     * 
     * @return Exception which occurred during processing
     */
    protected IOException copyRange(Reader reader, PrintWriter writer) {

        // Copy the input stream to the output stream
        IOException exception = null;
        char buffer[] = new char[input];
        int len = buffer.length;
        while (true) {
            try {
                len = reader.read(buffer);
                if (len == -1)
                    break;
                writer.write(buffer, 0, len);
            } catch (IOException e) {
                exception = e;
                len = -1;
                break;
            }
        }
        return exception;
    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流(即使面对一个异常).
     *
     * @param istream 要读取的输入流
     * @param ostream 要写入的输出流
     * @param start 将被复制的范围的开始
     * @param end 将被复制的范围的结束
     * 
     * @return Exception which occurred during processing
     */
    protected IOException copyRange(InputStream istream,
                                  ServletOutputStream ostream,
                                  long start, long end) {

        if (debug > 10)
            log("Serving bytes:" + start + "-" + end);

        long skipped = 0;
        try {
            skipped = istream.skip(start);
        } catch (IOException e) {
            return e;
        }
        if (skipped < start) {
            return new IOException(sm.getString("defaultservlet.skipfail",
                    Long.valueOf(skipped), Long.valueOf(start)));
        }

        IOException exception = null;
        long bytesToRead = end - start + 1;

        byte buffer[] = new byte[input];
        int len = buffer.length;
        while ( (bytesToRead > 0) && (len >= buffer.length)) {
            try {
                len = istream.read(buffer);
                if (bytesToRead >= len) {
                    ostream.write(buffer, 0, len);
                    bytesToRead -= len;
                } else {
                    ostream.write(buffer, 0, (int) bytesToRead);
                    bytesToRead = 0;
                }
            } catch (IOException e) {
                exception = e;
                len = -1;
            }
            if (len < buffer.length)
                break;
        }

        return exception;

    }


    protected static class Range {

        public long start;
        public long end;
        public long length;

        /**
         * 验证范围.
         *
         * @return true 如果范围是有效的, 否则false
         */
        public boolean validate() {
            if (end >= length)
                end = length - 1;
            return (start >= 0) && (end >= 0) && (start <= end) && (length > 0);
        }
    }

    protected static class CompressionFormat implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String extension;
        public final String encoding;

        public CompressionFormat(String extension, String encoding) {
            this.extension = extension;
            this.encoding = encoding;
        }
    }

    private static class PrecompressedResource {
        public final WebResource resource;
        public final CompressionFormat format;

        private PrecompressedResource(WebResource resource, CompressionFormat format) {
            this.resource = resource;
            this.format = format;
        }
    }

    /**
     * 这是安全的，因为任何使用外部实体的尝试都会触发异常.
     */
    private static class SecureEntityResolver implements EntityResolver2  {

        @Override
        public InputSource resolveEntity(String publicId, String systemId)
                throws SAXException, IOException {
            throw new SAXException(sm.getString("defaultServlet.blockExternalEntity",
                    publicId, systemId));
        }

        @Override
        public InputSource getExternalSubset(String name, String baseURI)
                throws SAXException, IOException {
            throw new SAXException(sm.getString("defaultServlet.blockExternalSubset",
                    name, baseURI));
        }

        @Override
        public InputSource resolveEntity(String name, String publicId,
                String baseURI, String systemId) throws SAXException,
                IOException {
            throw new SAXException(sm.getString("defaultServlet.blockExternalEntity2",
                    name, publicId, baseURI, systemId));
        }
    }
}
