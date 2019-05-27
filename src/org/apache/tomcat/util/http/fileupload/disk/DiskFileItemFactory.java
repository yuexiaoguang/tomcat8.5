package org.apache.tomcat.util.http.fileupload.disk;

import java.io.File;

import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileItemFactory;

/**
 * <p>{@link org.apache.tomcat.util.http.fileupload.FileItemFactory}的默认实现.
 * 此实现创建{@link org.apache.tomcat.util.http.fileupload.FileItem}实例，这些实例将其内容保存在内存中，用于较小的项目; 
 * 或保存在磁盘上的临时文件中，用于较大的项目.
 * 内容将存储在磁盘上的大小阈值是可配置的，就像创建临时文件的目录一样.</p>
 *
 * <p>如果没有其他配置, 默认配置值如下:</p>
 * <ul>
 *   <li>大小阈值是 10KB.</li>
 *   <li>存储库是系统默认的临时目录, 由<code>System.getProperty("java.io.tmpdir")</code>返回.</li>
 * </ul>
 * <p>
 * <b>NOTE</b>: 文件在系统默认临时目录中创建，具有可预测的名称. 这意味着对该目录具有写访问权限的本地攻击者可以执行TOUTOC攻击，以使用攻击者选择的文件替换任何上载的文件.
 * 这意味着将取决于上传文件的使用方式，但可能很重要. 在本地不受信任的用户的环境中使用此实现时，必须使用{@link #setRepository(File)}来配置不可公开写入的存储库位置.
 * 在Servlet容器中，可以使用ServletContext属性<code>javax.servlet.context.tempdir</code>标识的位置.
 * </p>
 *
 * <p>为文件项创建的临时文件, 应该稍后删除.</p>
 *
 * @since FileUpload 1.1
 */
public class DiskFileItemFactory implements FileItemFactory {

    // ----------------------------------------------------- Manifest constants

    /**
     * 将存储在磁盘上的上传的默认阈值.
     */
    public static final int DEFAULT_SIZE_THRESHOLD = 10240;

    // ----------------------------------------------------- Instance Variables

    /**
     * 将存储上传文件的目录, 如果存储在磁盘上.
     */
    private File repository;

    /**
     * 将存储在磁盘上的上传的默认阈值.
     */
    private int sizeThreshold = DEFAULT_SIZE_THRESHOLD;

    // ----------------------------------------------------------- Constructors

    public DiskFileItemFactory() {
        this(DEFAULT_SIZE_THRESHOLD, null);
    }

    /**
     * @param sizeThreshold 阈值, 以字节为单位, 阈值以下的项目将保存在内存中, 阈值以上的项目将保存为一个文件.
     * @param repository    项目大小超过阈值的数据存储库（即创建文件的目录）.
     */
    public DiskFileItemFactory(int sizeThreshold, File repository) {
        this.sizeThreshold = sizeThreshold;
        this.repository = repository;
    }

    // ------------------------------------------------------------- Properties

    /**
     * 返回用于临时存储大于配置的大小阈值的文件的目录.
     */
    public File getRepository() {
        return repository;
    }

    /**
     * 设置用于临时存储大于配置的大小阈值的文件的目录.
     *
     * @param repository 临时文件的目录.
     */
    public void setRepository(File repository) {
        this.repository = repository;
    }

    /**
     * 返回大小阈值，超过该阈值将文件直接写入磁盘. 默认大小是 10240 bytes.
     */
    public int getSizeThreshold() {
        return sizeThreshold;
    }

    /**
     * 设置大小阈值，超过该阈值将文件直接写入磁盘.
     *
     * @param sizeThreshold 大小阈值, 以字节为单位.
     */
    public void setSizeThreshold(int sizeThreshold) {
        this.sizeThreshold = sizeThreshold;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 从提供的参数和本地工厂配置创建新的{@link DiskFileItem}实例.
     *
     * @param fieldName   表单字段的名称.
     * @param contentType 表单字段的内容类型.
     * @param isFormField <code>true</code>如果这是一个普通的表单字段;
     *                    <code>false</code>否则.
     * @param fileName    上传文件的名称, 由浏览器或其他客户提供.
     *
     * @return 新创建的文件项.
     */
    @Override
    public FileItem createItem(String fieldName, String contentType,
            boolean isFormField, String fileName) {
        return new DiskFileItem(fieldName, contentType,
                isFormField, fileName, sizeThreshold, repository);
    }
}
