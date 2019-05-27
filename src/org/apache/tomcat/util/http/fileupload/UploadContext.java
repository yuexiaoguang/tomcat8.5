package org.apache.tomcat.util.http.fileupload;

/**
 * 增强了对文件上传所需的请求信息的访问, 修复了{@link RequestContext}中的内容长度数据访问.
 *
 * 引入这个新接口的原因只是为了向后兼容，对于重构的2.x版本，它可能会消失，再次将新方法移动到RequestContext中.
 */
public interface UploadContext extends RequestContext {

    /**
     * 检索请求的内容长度.
     *
     * @return 请求的内容长度.
     * @since 1.3
     */
    long contentLength();

}
