package org.apache.tomcat.util.http.fileupload.servlet;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileItemFactory;
import org.apache.tomcat.util.http.fileupload.FileItemIterator;
import org.apache.tomcat.util.http.fileupload.FileUpload;
import org.apache.tomcat.util.http.fileupload.FileUploadBase;
import org.apache.tomcat.util.http.fileupload.FileUploadException;


/**
 * <p>用于处理文件上传的高级API.</p>
 *
 * <p>如何存储单个部分的数据由用于创建它们的工厂决定;给定的部分可能在内存中，磁盘上或其他地方.</p>
 */
public class ServletFileUpload extends FileUpload {

    /**
     * HTTP POST方法的常量.
     */
    private static final String POST_METHOD = "POST";

    // ---------------------------------------------------------- Class methods

    /**
     * 确定请求是否包含多部分内容.
     *
     * @param request 要评估的servlet请求. 必须 non-null.
     *
     * @return <code>true</code>如果请求是多部分的;
     *         <code>false</code> 否则.
     */
    public static final boolean isMultipartContent(
            HttpServletRequest request) {
        if (!POST_METHOD.equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return FileUploadBase.isMultipartContent(new ServletRequestContext(request));
    }

    // ----------------------------------------------------------- Constructors

    /**
     * 必须使用<code>setFileItemFactory()</code>配置工厂, 在尝试解析请求之前.
     */
    public ServletFileUpload() {
        super();
    }

    /**
     * @param fileItemFactory 用于创建文件项的工厂.
     */
    public ServletFileUpload(FileItemFactory fileItemFactory) {
        super(fileItemFactory);
    }

    // --------------------------------------------------------- Public methods

    /**
     * 处理符合<a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>的<code>multipart/form-data</code>流.
     *
     * @param request 要解析的servlet请求.
     *
     * @return 从请求中解析的<code>FileItem</code>实例的Map.
     *
     * @throws FileUploadException 如果在读取/解析请求或存储文件时出现问题.
     *
     * @since 1.3
     */
    public Map<String, List<FileItem>> parseParameterMap(HttpServletRequest request)
            throws FileUploadException {
        return parseParameterMap(new ServletRequestContext(request));
    }

    /**
     * 处理符合<a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>的<code>multipart/form-data</code>流.
     *
     * @param request 要解析的servlet请求.
     *
     * @return 从请求中解析的<code>FileItemStream</code>实例的迭代器, 按照它们传输的顺序.
     *
     * @throws FileUploadException 如果在读取/解析请求或存储文件时出现问题.
     * @throws IOException 发生I/O错误. 可能是在与客户端通信时网络错误, 或存储上传内容时的问题.
     */
    public FileItemIterator getItemIterator(HttpServletRequest request)
    throws FileUploadException, IOException {
        return super.getItemIterator(new ServletRequestContext(request));
    }

}
