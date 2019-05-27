package org.apache.tomcat.util.http.fileupload;

import java.io.IOException;
import java.io.InputStream;

/**
 * <p>访问文件上传所需的请求信息. 应该为FileUpload可以处理的每种类型的请求实现此接口, 例如servlet和portlet.</p>
 */
public interface RequestContext {

    /**
     * 检索请求的字符编码.
     *
     * @return 请求的字符编码.
     */
    String getCharacterEncoding();

    /**
     * 检索请求的内容类型.
     *
     * @return 请求的内容类型.
     */
    String getContentType();

    /**
     * 检索请求的输入流.
     *
     * @return 请求的输入流.
     *
     * @throws IOException 如果出现问题.
     */
    InputStream getInputStream() throws IOException;

}
