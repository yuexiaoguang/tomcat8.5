package org.apache.tomcat.util.http.fileupload.disk;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tomcat.util.http.fileupload.DeferredFileOutputStream;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileItemHeaders;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.apache.tomcat.util.http.fileupload.ParameterParser;
import org.apache.tomcat.util.http.fileupload.util.Streams;

/**
 * <p>{@link org.apache.tomcat.util.http.fileupload.FileItem FileItem}接口的默认实现.
 *
 * <p>从{@link org.apache.tomcat.util.http.fileupload.FileUpload FileUpload}实例检索此类的实例后
 * (see {@link org.apache.tomcat.util.http.fileupload.FileUpload
 * #parseRequest(org.apache.tomcat.util.http.fileupload.RequestContext)}),
 * 您可以使用{@link #get()}一次请求文件的所有内容, 或者使用{@link #getInputStream()}请求{@link java.io.InputStream InputStream}, 
 * 并处理该文件而不尝试加载它 进入内存, 这对于大文件来说可能很方便.
 *
 * <p>为文件项创建的临时文件, 应该稍后删除.</p>
 */
public class DiskFileItem
    implements FileItem {

    // ----------------------------------------------------- Manifest constants

    /**
     * 发送者未提供显式charset参数时, 要使用的默认内容字符集.
     * “text”类型的媒体子类型通过HTTP接收时的默认字符集值为“ISO-8859-1”.
     */
    public static final String DEFAULT_CHARSET = "ISO-8859-1";

    // ----------------------------------------------------------- Data members

    /**
     * 用于生成唯一文件名的UID.
     */
    private static final String UID =
            UUID.randomUUID().toString().replace('-', '_');

    /**
     * 用于唯一标识符生成的计数器.
     */
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    /**
     * 浏览器提供的表单字段的名称.
     */
    private String fieldName;

    /**
     * 浏览器传递的内容类型, 或<code>null</code>.
     */
    private final String contentType;

    /**
     * 此项目是否是简单的表单字段.
     */
    private boolean isFormField;

    /**
     * 用户文件系统中的原始文件名.
     */
    private final String fileName;

    /**
     * 项目的大小, 以字节为单位. 用于在文件项从其原始位置移动时缓存的大小.
     */
    private long size = -1;


    /**
     * 将存储在磁盘上的上传的阈值.
     */
    private final int sizeThreshold;

    /**
     * 将存储上传文件的目录, 如果存储在磁盘上.
     */
    private final File repository;

    /**
     * 缓存的文件内容.
     */
    private byte[] cachedContent;

    /**
     * 此项目的输出流.
     */
    private transient DeferredFileOutputStream dfos;

    /**
     * 要使用的临时文件.
     */
    private transient File tempFile;

    /**
     * 文件项 header.
     */
    private FileItemHeaders headers;

    // ----------------------------------------------------------- Constructors

    /**
     * @param fieldName     表单字段的名称.
     * @param contentType   浏览器传递的内容类型; 如果未指定，则为<code>null</code>.
     * @param isFormField   此项是否是普通表单字段，而不是文件上传.
     * @param fileName      用户文件系统中的原始文件名, 如果未指定，则为<code>null</code>.
     * @param sizeThreshold 阈值, 以字节为单位, 阈值以下的项目将保存在内存中, 阈值以上的项目将保存在文件中.
     * @param repository    将在其中创建文件的目录, 当项目大小超过阈值时.
     */
    public DiskFileItem(String fieldName,
            String contentType, boolean isFormField, String fileName,
            int sizeThreshold, File repository) {
        this.fieldName = fieldName;
        this.contentType = contentType;
        this.isFormField = isFormField;
        this.fileName = fileName;
        this.sizeThreshold = sizeThreshold;
        this.repository = repository;
    }

    // ------------------------------- Methods from javax.activation.DataSource

    /**
     * 返回可用于检索文件内容的{@link java.io.InputStream InputStream}.
     *
     * @throws IOException 如果发生错误.
     */
    @Override
    public InputStream getInputStream()
        throws IOException {
        if (!isInMemory()) {
            return new FileInputStream(dfos.getFile());
        }

        if (cachedContent == null) {
            cachedContent = dfos.getData();
        }
        return new ByteArrayInputStream(cachedContent);
    }

    /**
     * 返回代理传递的内容类型; 如果未定义，则返回<code>null</code>.
     */
    @Override
    public String getContentType() {
        return contentType;
    }

    /**
     * 返回代理传递的内容字符集; 如果未定义，则返回<code>null</code>.
     */
    public String getCharSet() {
        ParameterParser parser = new ParameterParser();
        parser.setLowerCaseNames(true);
        // Parameter parser can handle null input
        Map<String,String> params = parser.parse(getContentType(), ';');
        return params.get("charset");
    }

    /**
     * 返回客户端文件系统中的原始文件名.
     *
     * @throws org.apache.tomcat.util.http.fileupload.InvalidFileNameException
     *   文件名包含NUL字符，可能是安全攻击的指示符. 如果仍然使用文件名, 捕获异常并使用{@link
     *   org.apache.tomcat.util.http.fileupload.InvalidFileNameException#getName()}.
     */
    @Override
    public String getName() {
        return Streams.checkFileName(fileName);
    }

    // ------------------------------------------------------- FileItem methods

    /**
     * 提供有关是否从内存中读取文件内容的提示.
     *
     * @return <code>true</code>如果文件内容将从内存中读取; 否则<code>false</code>.
     */
    @Override
    public boolean isInMemory() {
        if (cachedContent != null) {
            return true;
        }
        return dfos.isInMemory();
    }

    /**
     * 返回文件的大小, 以字节为单位.
     */
    @Override
    public long getSize() {
        if (size >= 0) {
            return size;
        } else if (cachedContent != null) {
            return cachedContent.length;
        } else if (dfos.isInMemory()) {
            return dfos.getData().length;
        } else {
            return dfos.getFile().length();
        }
    }

    /**
     * 以字节数组的形式返回文件的内容.  如果文件的内容尚未缓存在内存中, 它们将从磁盘存储加载并缓存.
     *
     * @return 文件的内容为字节数组, 或{@code null}如果无法读取数据
     */
    @Override
    public byte[] get() {
        if (isInMemory()) {
            if (cachedContent == null && dfos != null) {
                cachedContent = dfos.getData();
            }
            return cachedContent;
        }

        byte[] fileData = new byte[(int) getSize()];
        InputStream fis = null;

        try {
            fis = new FileInputStream(dfos.getFile());
            IOUtils.readFully(fis, fileData);
        } catch (IOException e) {
            fileData = null;
        } finally {
            IOUtils.closeQuietly(fis);
        }

        return fileData;
    }

    /**
     * 使用指定的编码以String形式返回文件的内容. 此方法使用{@link #get()}来检索文件的内容.
     *
     * @param charset 要使用的字符集.
     *
     * @return 文件的内容.
     *
     * @throws UnsupportedEncodingException 如果请求的字符编码不可用.
     */
    @Override
    public String getString(final String charset)
        throws UnsupportedEncodingException {
        return new String(get(), charset);
    }

    /**
     * 以String形式返回文件的内容, 使用默认字符编码. 此方法使用{@link #get()}来检索文件的内容.
     *
     * <b>TODO</b> 考虑使此方法抛出UnsupportedEncodingException.
     *
     * @return 文件的内容.
     */
    @Override
    public String getString() {
        byte[] rawdata = get();
        String charset = getCharSet();
        if (charset == null) {
            charset = DEFAULT_CHARSET;
        }
        try {
            return new String(rawdata, charset);
        } catch (UnsupportedEncodingException e) {
            return new String(rawdata);
        }
    }

    /**
     * 将上传的项目写入磁盘.
     * 客户端代码不关心项目是否存储在内存中, 或在磁盘上的临时位置. 只想将上传的项目写入文件.
     * <p>
     * 此实现首先尝试将上传的项重命名为指定的目标文件, 如果项目最初写入磁盘.
     * 否则，数据将被复制到指定的文件.
     * <p>
     * 此方法仅保证运行一次, 第一次为特定项目调用它. 这是因为，如果该方法重命名临时文件，该文件将不可再用于以后再次复制或重命名.
     *
     * @param file 应存储上传项目的<code>File</code>.
     *
     * @throws Exception 如果发生错误.
     */
    @Override
    public void write(File file) throws Exception {
        if (isInMemory()) {
            FileOutputStream fout = null;
            try {
                fout = new FileOutputStream(file);
                fout.write(get());
                fout.close();
            } finally {
                IOUtils.closeQuietly(fout);
            }
        } else {
            File outputFile = getStoreLocation();
            if (outputFile != null) {
                // Save the length of the file
                size = outputFile.length();
                /*
                 * 上载的文件存储在磁盘的临时位置上，因此将其移动到所需的文件.
                 */
                if (!outputFile.renameTo(file)) {
                    BufferedInputStream in = null;
                    BufferedOutputStream out = null;
                    try {
                        in = new BufferedInputStream(
                            new FileInputStream(outputFile));
                        out = new BufferedOutputStream(
                                new FileOutputStream(file));
                        IOUtils.copy(in, out);
                        out.close();
                    } finally {
                        IOUtils.closeQuietly(in);
                        IOUtils.closeQuietly(out);
                    }
                }
            } else {
                /*
                 * 无论出于何种原因，都无法将文件写入磁盘.
                 */
                throw new FileUploadException(
                    "Cannot write uploaded file to disk!");
            }
        }
    }

    /**
     * 删除文件项的底层存储, 包括删除任何关联的临时磁盘文件.
     * 虽然在<code>FileItem</code>实例被垃圾回收时会自动删除此存储, 此方法可用于确保在较早时间完成此操作, 从而保留系统资源.
     */
    @Override
    public void delete() {
        cachedContent = null;
        File outputFile = getStoreLocation();
        if (outputFile != null && outputFile.exists()) {
            outputFile.delete();
        }
    }

    /**
     * 返回与此文件项对应的multipart表单中的字段名称.
     */
    @Override
    public String getFieldName() {
        return fieldName;
    }

    /**
     * 设置与此文件项对应的multipart表单中的字段名称.
     *
     * @param fieldName 表单字段的名称.
     */
    @Override
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    /**
     * 确定<code>FileItem</code>实例是否表示简单的表单字段.
     *
     * @return <code>true</code>如果实例表示一个简单的表单字段; <code>false</code>如果它表示上传的文件.
     */
    @Override
    public boolean isFormField() {
        return isFormField;
    }

    /**
     * 指定<code>FileItem</code>实例是否表示简单的表单字段.
     *
     * @param state <code>true</code>如果实例表示一个简单的表单字段; <code>false</code>如果它表示上传的文件.
     */
    @Override
    public void setFormField(boolean state) {
        isFormField = state;
    }

    /**
     * 返回可用于存储文件内容的{@link java.io.OutputStream OutputStream}.
     *
     * @throws IOException 如果发生错误.
     */
    @Override
    public OutputStream getOutputStream()
        throws IOException {
        if (dfos == null) {
            File outputFile = getTempFile();
            dfos = new DeferredFileOutputStream(sizeThreshold, outputFile);
        }
        return dfos;
    }

    // --------------------------------------------------------- Public methods

    /**
     * 返回<code>FileItem</code>的数据在磁盘上的临时位置的{@link java.io.File}对象.
     * 请注意，对于将其数据存储在内存中的<code> FileItem </ code>, 此方法将返回<code>null</code>.
     * 处理大文件时, 可以使用{@link java.io.File#renameTo(java.io.File)}将文件移动到新位置而无需复制数据,
     * 如果源和目标位置位于同一逻辑卷中.
     *
     * @return 数据文件, 或<code>null</code>如果数据存储在内存中.
     */
    public File getStoreLocation() {
        if (dfos == null) {
            return null;
        }
        if (isInMemory()) {
            return null;
        }
        return dfos.getFile();
    }

    // ------------------------------------------------------ Protected methods

    /**
     * 从临时存储中删除文件内容.
     */
    @Override
    protected void finalize() {
        if (dfos == null) {
            return;
        }
        File outputFile = dfos.getFile();

        if (outputFile != null && outputFile.exists()) {
            outputFile.delete();
        }
    }

    /**
     * 创建并返回一个 {@link java.io.File File}, 表示配置的存储库路径中唯一命名的临时文件.
     * 文件的生命周期与<code>FileItem</code>实例的生命周期相关联; 实例被垃圾回收时，文件将被删除.
     * <p>
     * <b>Note: 重写此方法的子类必须确保它们每次都返回相同的文件.</b>
     *
     * @return 用于临时存储的{@link java.io.File File}.
     */
    protected File getTempFile() {
        if (tempFile == null) {
            File tempDir = repository;
            if (tempDir == null) {
                tempDir = new File(System.getProperty("java.io.tmpdir"));
            }

            String tempFileName =
                    String.format("upload_%s_%s.tmp", UID, getUniqueId());

            tempFile = new File(tempDir, tempFileName);
        }
        return tempFile;
    }

    // -------------------------------------------------------- Private methods

    /**
     * 返回在用于加载此类的类加载器中唯一的标识符, 但没有随意的外观.
     *
     * @return 具有非随机查找实例标识符的String.
     */
    private static String getUniqueId() {
        final int limit = 100000000;
        int current = COUNTER.getAndIncrement();
        String id = Integer.toString(current);

        // 如果你设法获得超过1亿的ID, 你将开始获得超过8个字符的ID.
        if (current < limit) {
            id = ("00000000" + id).substring(id.length());
        }
        return id;
    }

    @Override
    public String toString() {
        return String.format("name=%s, StoreLocation=%s, size=%s bytes, isFormField=%s, FieldName=%s",
                      getName(), getStoreLocation(), Long.valueOf(getSize()),
                      Boolean.valueOf(isFormField()), getFieldName());
    }

    /**
     * 返回文件项 header.
     * 
     * @return 文件项 header.
     */
    @Override
    public FileItemHeaders getHeaders() {
        return headers;
    }

    /**
     * 设置文件项 header.
     * 
     * @param pHeaders 文件项 header.
     */
    @Override
    public void setHeaders(FileItemHeaders pHeaders) {
        headers = pHeaders;
    }

}
