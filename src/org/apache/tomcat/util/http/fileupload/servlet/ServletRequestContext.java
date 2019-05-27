package org.apache.tomcat.util.http.fileupload.servlet;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;

import org.apache.tomcat.util.http.fileupload.FileUploadBase;
import org.apache.tomcat.util.http.fileupload.UploadContext;


/**
 * <p>提供对HTTP servlet请求所需的请求信息的访问.</p>
 */
public class ServletRequestContext implements UploadContext {

    // ----------------------------------------------------- Instance Variables

    /**
     * 提供上下文的请求.
     */
    private final HttpServletRequest request;

    // ----------------------------------------------------------- Constructors

    /**
     * @param request 此上下文适用的请求.
     */
    public ServletRequestContext(HttpServletRequest request) {
        this.request = request;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 检索请求的字符编码.
     */
    @Override
    public String getCharacterEncoding() {
        return request.getCharacterEncoding();
    }

    /**
     * 检索请求的内容类型.
     */
    @Override
    public String getContentType() {
        return request.getContentType();
    }

    /**
     * 检索请求的内容长度.
     */
    @Override
    public long contentLength() {
        long size;
        try {
            size = Long.parseLong(request.getHeader(FileUploadBase.CONTENT_LENGTH));
        } catch (NumberFormatException e) {
            size = request.getContentLength();
        }
        return size;
    }

    /**
     * 检索请求的输入流.
     * 
     * @throws IOException 如果出现问题.
     */
    @Override
    public InputStream getInputStream() throws IOException {
        return request.getInputStream();
    }

    @Override
    public String toString() {
        return String.format("ContentLength=%s, ContentType=%s",
                      Long.valueOf(this.contentLength()),
                      this.getContentType());
    }

}
