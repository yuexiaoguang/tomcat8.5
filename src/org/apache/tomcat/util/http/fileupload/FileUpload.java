package org.apache.tomcat.util.http.fileupload;

/**
 * <p>用于处理文件上传的高级API.</p>
 *
 * <p>此类处理每个HTML片段的多个文件, 使用<code>multipart/mixed</code>编码类型发送,
 * 就像<a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>指定的那样.
 * 使用{@link #parseRequest(RequestContext)}获取与给定HTML片段关联的{@link org.apache.tomcat.util.http.fileupload.FileItem FileItems}列表.</p>
 *
 * <p>如何存储单个部分的数据由用于创建它们的工厂决定; 给定的部分可能在内存中，磁盘上或其他地方.</p>
 */
public class FileUpload
    extends FileUploadBase {

    // ----------------------------------------------------------- Data members

    /**
     * 用于创建新的表单项的工厂.
     */
    private FileItemFactory fileItemFactory;

    // ----------------------------------------------------------- Constructors

    /**
     * 必须配置工厂, 使用 <code>setFileItemFactory()</code>, 在尝试解析请求之前.
     */
    public FileUpload() {
        super();
    }

    /**
     * @param fileItemFactory 用于创建文件项的工厂.
     */
    public FileUpload(FileItemFactory fileItemFactory) {
        super();
        this.fileItemFactory = fileItemFactory;
    }

    // ----------------------------------------------------- Property accessors

    /**
     * 返回创建文件项时使用的工厂类.
     */
    @Override
    public FileItemFactory getFileItemFactory() {
        return fileItemFactory;
    }

    /**
     * 设置创建文件项时使用的工厂类.
     *
     * @param factory 新文件项的工厂类.
     */
    @Override
    public void setFileItemFactory(FileItemFactory factory) {
        this.fileItemFactory = factory;
    }

}
