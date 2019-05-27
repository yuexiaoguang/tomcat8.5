package org.apache.tomcat.util.http.fileupload;

/**
 * <p>用于创建{@link FileItem}实例的工厂接口. 工厂可以提供自己的自定义配置，超出默认文件上传实现提供的配置.</p>
 */
public interface FileItemFactory {

    /**
     * @param fieldName   表单字段的名称.
     * @param contentType 表单字段的内容类型.
     * @param isFormField <code>true</code> 如果这是一个普通的表单字段;
     *                    <code>false</code>否则.
     * @param fileName    上传文件的名称, 由浏览器或其他客户提供.
     *
     * @return 新创建的文件项.
     */
    FileItem createItem(
            String fieldName,
            String contentType,
            boolean isFormField,
            String fileName
            );

}
