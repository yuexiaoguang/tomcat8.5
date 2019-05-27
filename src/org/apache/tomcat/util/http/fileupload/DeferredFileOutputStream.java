package org.apache.tomcat.util.http.fileupload;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;


/**
 * 一个输出流，它将数据保留在内存中，直到达到指定的阈值，然后才将其提交到磁盘.
 * 如果在达到阈值之前关闭流, 根本不会将数据写入磁盘.
 * <p>
 * 此类源自FileUpload处理. 在这个用例中, 事先并不知道正在上传的文件的大小.
 * 如果文件很小, 希望将其存储在内存中 (为了速度); 但如果文件很大, 您希望将其存储到文件中 (避免内存问题).
 */
public class DeferredFileOutputStream
    extends ThresholdingOutputStream
{

    // ----------------------------------------------------------- Data members


    /**
     * 在到达theshold之前将数据写入的输出流.
     */
    private ByteArrayOutputStream memoryOutputStream;


    /**
     * 在任何给定时间将数据写入的输出流.
     * 这将始终是<code>memoryOutputStream</code>或<code>diskOutputStream</code>之一.
     */
    private OutputStream currentOutputStream;


    /**
     * 超出阈值时, 将向其输出的文件.
     */
    private File outputFile;

    /**
     * 临时文件前缀.
     */
    private final String prefix;

    /**
     * 临时文件后缀.
     */
    private final String suffix;

    /**
     * 用于临时文件的目录.
     */
    private final File directory;

    // ----------------------------------------------------------- Constructors


    /**
     * 在指定的阈值触发事件, 并将数据保存到该点之外的文件中.
     *
     * @param threshold  触发事件的字节数.
     * @param outputFile 保存超出阈值的数据的文件.
     */
    public DeferredFileOutputStream(int threshold, File outputFile)
    {
        this(threshold,  outputFile, null, null, null);
    }


    /**
     * 在指定的阈值触发事件, 并将数据保存到该点之外的文件中.
     *
     * @param threshold  触发事件的字节数.
     * @param outputFile 保存超出阈值的数据的文件.
     * @param prefix 用于临时文件的前缀.
     * @param suffix 用于临时文件的后缀.
     * @param directory 临时文件目录.
     */
    private DeferredFileOutputStream(int threshold, File outputFile, String prefix, String suffix, File directory) {
        super(threshold);
        this.outputFile = outputFile;

        memoryOutputStream = new ByteArrayOutputStream();
        currentOutputStream = memoryOutputStream;
        this.prefix = prefix;
        this.suffix = suffix;
        this.directory = directory;
    }


    // --------------------------------------- ThresholdingOutputStream methods


    /**
     * 返回当前输出流. 这可以是基于内存的或基于磁盘的, 取决于关于阈值的当前状态.
     *
     * @return 底层输出流.
     *
     * @exception IOException 如果发生错误.
     */
    @Override
    protected OutputStream getStream() throws IOException
    {
        return currentOutputStream;
    }


    /**
     * 将基础输出流从基于内存的流切换到由磁盘支持的流.
     * 这就是我们意识到太多要写入的数据保留在内存中的点, 所以我们选择切换到基于磁盘的存储.
     *
     * @exception IOException 如果发生错误.
     */
    @Override
    protected void thresholdReached() throws IOException
    {
        if (prefix != null) {
            outputFile = File.createTempFile(prefix, suffix, directory);
        }
        FileOutputStream fos = new FileOutputStream(outputFile);
        memoryOutputStream.writeTo(fos);
        currentOutputStream = fos;
        memoryOutputStream = null;
    }


    // --------------------------------------------------------- Public methods


    /**
     * 确定此输出流的数据是否已保留在内存中.
     *
     * @return {@code true} 如果数据在内存中可用; 否则{@code false}.
     */
    public boolean isInMemory()
    {
        return !isThresholdExceeded();
    }


    /**
     * 以字节数组的形式返回此输出流的数据, 假设数据已保留在内存中.
     * 如果数据写入磁盘, 这个方法返回 {@code null}.
     *
     * @return 此输出流的数据, 或 {@code null} 如果没有这样的数据.
     */
    public byte[] getData()
    {
        if (memoryOutputStream != null)
        {
            return memoryOutputStream.toByteArray();
        }
        return null;
    }


    /**
     * 返回构造函数中指定的输出文件或创建的临时文件或null.
     * <p>
     * 如果使用指定文件的构造函数, 则它返回相同的输出文件, 即使没有达到阈值.
     * <p>
     * 如果使用指定临时文件前缀/后缀的构造函数, 然后返回达到阈值时创建的临时文件.  如果未达到阈值，则返回{@code null}.
     *
     * @return 此输出流的文件, 或 {@code null} 如果没有这样的文件.
     */
    public File getFile()
    {
        return outputFile;
    }


    /**
     * 关闭底层输出流, 并标记其为关闭
     *
     * @exception IOException 如果发生错误.
     */
    @Override
    public void close() throws IOException
    {
        super.close();
    }
}
