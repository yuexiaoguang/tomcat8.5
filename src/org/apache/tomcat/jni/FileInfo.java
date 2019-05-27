package org.apache.tomcat.jni;

/** Fileinfo
 */
public class FileInfo {

    /** 分配内存并关闭指定池中的延迟句柄 */
    public long pool;
    /** 描述此apr_finfo_t结构的有效字段的位掩码，包括所有可用的“想要的”字段以及可能更多 */
    public int valid;
    /** 该文件的访问权限. 模仿Unix访问权限. */
    public int protection;
    /** 文件的类型. APR_REG, APR_DIR, APR_CHR, APR_BLK, APR_PIPE, APR_LNK, APR_SOCK 其中之一.
     * 如果类型未确定, 则为 APR_NOFILE. 如果无法确定类型, 则为 APR_UNKFILE.
     */
    public int filetype;
    /** 拥有该文件的用户 id */
    public int user;
    /** 拥有该文件的组ID */
    public int group;
    /** 文件的inode. */
    public int inode;
    /** 文件所在设备的ID. */
    public int device;
    /** 文件的硬链接数. */
    public int nlink;
    /** 文件的大小 */
    public long size;
    /** 文件占用的存储大小 */
    public long csize;
    /** 上次访问文件的时间 */
    public long atime;
    /** 上次修改文件的时间 */
    public long mtime;
    /** 文件创建的时间, 或者inode最后更改了 */
    public long ctime;
    /** 文件的路径名 (可能是无根的) */
    public String fname;
    /** 文件系统案例中的文件名（无路径） */
    public String name;
    /** 文件的句柄, 如果访问 (可以提交给apr_duphandle) */
    public long filehand;

}
