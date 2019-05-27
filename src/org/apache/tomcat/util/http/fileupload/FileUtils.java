package org.apache.tomcat.util.http.fileupload;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;


/**
 * 常规文件操作.
 * <p>
 * 提供以下操作:
 * <ul>
 * <li>写入文件
 * <li>从文件中读取
 * <li>制作包含父目录的目录
 * <li>复制文件和目录
 * <li>删除文件和目录
 * <li>转换为URL和从URL转换
 * <li>按过滤器和扩展名列出文件和目录
 * <li>比较文件内容
 * <li>文件上次更改日期
 * <li>计算校验和
 * </ul>
 * <p>
 * 代码的起源: Excalibur, Alexandria, Commons-Utils
 */
public class FileUtils {

    public FileUtils() {
        super();
    }

    //-----------------------------------------------------------------------
    /**
     * 以递归方式删除目录.
     *
     * @param directory  要删除的目录
     * @throws IOException 如果删除不成功
     */
    public static void deleteDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }

        if (!isSymlink(directory)) {
            cleanDirectory(directory);
        }

        if (!directory.delete()) {
            String message =
                "Unable to delete directory " + directory + ".";
            throw new IOException(message);
        }
    }

    /**
     * 清除目录而不删除它.
     *
     * @param directory 要清理的目录
     * @throws IOException 如果清理不成功
     */
    public static void cleanDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            String message = directory + " does not exist";
            throw new IllegalArgumentException(message);
        }

        if (!directory.isDirectory()) {
            String message = directory + " is not a directory";
            throw new IllegalArgumentException(message);
        }

        File[] files = directory.listFiles();
        if (files == null) {  // null if security restricted
            throw new IOException("Failed to list contents of " + directory);
        }

        IOException exception = null;
        for (File file : files) {
            try {
                forceDelete(file);
            } catch (IOException ioe) {
                exception = ioe;
            }
        }

        if (null != exception) {
            throw exception;
        }
    }

    //-----------------------------------------------------------------------
    /**
     * 删除文件. 如果file是目录, 删除它和所有子目录.
     * <p>
     * File.delete()和此方法之间的区别是:
     * <ul>
     * <li>要删除的目录不必为空.</li>
     * <li>无法删除文件或目录时会出现异常. (java.io.File 方法返回 boolean)</li>
     * </ul>
     *
     * @param file  要删除的文件或目录, 不能是 {@code null}
     * 
     * @throws NullPointerException 如果目录是 {@code null}
     * @throws FileNotFoundException 如果找不到该文件
     * @throws IOException 如果删除不成功
     */
    public static void forceDelete(File file) throws IOException {
        if (file.isDirectory()) {
            deleteDirectory(file);
        } else {
            boolean filePresent = file.exists();
            if (!file.delete()) {
                if (!filePresent){
                    throw new FileNotFoundException("File does not exist: " + file);
                }
                String message =
                    "Unable to delete file: " + file;
                throw new IOException(message);
            }
        }
    }

    /**
     * 在JVM退出时计划要删除的文件.
     * 如果文件是目录，则删除它和所有子目录.
     *
     * @param file  要删除的文件或目录, 不能是 {@code null}
     * 
     * @throws NullPointerException 如果文件是 {@code null}
     * @throws IOException 如果删除不成功
     */
    public static void forceDeleteOnExit(File file) throws IOException {
        if (file.isDirectory()) {
            deleteDirectoryOnExit(file);
        } else {
            file.deleteOnExit();
        }
    }

    /**
     * 在JVM退出时递归删除目录.
     *
     * @param directory  要删除的目录, 不能是 {@code null}
     * 
     * @throws NullPointerException 如果目录是 {@code null}
     * @throws IOException 如果删除不成功
     */
    private static void deleteDirectoryOnExit(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }

        directory.deleteOnExit();
        if (!isSymlink(directory)) {
            cleanDirectoryOnExit(directory);
        }
    }

    /**
     * 清理目录而不删除它.
     *
     * @param directory  要清理的目录, 不能是 {@code null}
     * 
     * @throws NullPointerException 如果目录是 {@code null}
     * @throws IOException 如果清理不成功
     */
    private static void cleanDirectoryOnExit(File directory) throws IOException {
        if (!directory.exists()) {
            String message = directory + " does not exist";
            throw new IllegalArgumentException(message);
        }

        if (!directory.isDirectory()) {
            String message = directory + " is not a directory";
            throw new IllegalArgumentException(message);
        }

        File[] files = directory.listFiles();
        if (files == null) {  // null if security restricted
            throw new IOException("Failed to list contents of " + directory);
        }

        IOException exception = null;
        for (File file : files) {
            try {
                forceDeleteOnExit(file);
            } catch (IOException ioe) {
                exception = ioe;
            }
        }

        if (null != exception) {
            throw exception;
        }
    }


    /**
     * 确定指定的文件是否是符号链接而不是实际文件.
     * <p>
     * 如果路径中的任何位置存在符号链接，则不会返回true, 仅当指定文件是时.
     * <p>
     * <b>Note:</b> 当前的实现总是返回 {@code false}, 如果系统被检测为Windows使用
     * {@link File#separatorChar} == '\\'
     *
     * @param file 要检查的文件
     * @return true 如果文件是符号链接
     * @throws IOException 如果在检查文件时发生IO错误
     * @since 2.0
     */
    public static boolean isSymlink(File file) throws IOException {
        if (file == null) {
            throw new NullPointerException("File must not be null");
        }
        //FilenameUtils.isSystemWindows()
        if (File.separatorChar == '\\') {
            return false;
        }
        File fileInCanonicalDir = null;
        if (file.getParent() == null) {
            fileInCanonicalDir = file;
        } else {
            File canonicalDir = file.getParentFile().getCanonicalFile();
            fileInCanonicalDir = new File(canonicalDir, file.getName());
        }

        if (fileInCanonicalDir.getCanonicalFile().equals(fileInCanonicalDir.getAbsoluteFile())) {
            return false;
        } else {
            return true;
        }
    }
}
