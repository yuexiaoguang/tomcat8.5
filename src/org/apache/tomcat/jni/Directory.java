package org.apache.tomcat.jni;

/** Directory
 */
public class Directory {

    /**
     * 在文件系统上创建一个新目录.
     * 
     * @param path 要创建的目录的路径. (所有系统上使用 / )
     * @param perm 新目录的权限.
     * @param pool 要使用的池.
     * @return 操作结果
     */
    public static native int make(String path, int perm, long pool);

    /**
     * 在文件系统上创建一个新目录, 但表现得像 'mkdir -p'. 根据需要创建中间目录. 如果PATH已存在，则不会报告错误.
     * 
     * @param path 要创建的目录的路径. (所有系统上使用 / )
     * @param perm 新目录的权限.
     * @param pool 要使用的池.
     * 
     * @return 操作结果
     */
    public static native int makeRecursive(String path, int perm, long pool);

    /**
     * 从文件系统中删除目录.
     * 
     * @param path 要删除的目录的路径. (所有系统上使用 / )
     * @param pool 要使用的池.
     * 
     * @return 操作结果
     */
    public static native int remove(String path, long pool);

    /**
     * 查找适合作为临时存储位置的现有目录.
     * 
     * @param pool 用于任何必要分配的池.
     * @return 临时目录.
     *
     * 此函数使用算法搜索应用程序可用于临时存储的目录. 一旦找到这样的目录, 该位置由库缓存. 因此，如果有一次成功，调用只需支付一次该算法的费用.
     */
    public static native String tempGet(long pool);

    /**
     * 打开指定的目录.
     * 
     * @param dirname 目录的完整路径 (所有系统上使用 / )
     * @param pool 要使用的池.
     * 
     * @return 打开的目录描述符.
     * @throws Error 发生错误
     */
    public static native long open(String dirname, long pool)
        throws Error;

    /**
     * 关闭指定的目录.
     * 
     * @param thedir 要关闭的目录描述符.
     * 
     * @return 操作的结果
     */
    public static native int close(long thedir);

    /**
     * 将目录回滚到第一个条目.
     * 
     * @param thedir 要回放的目录描述符.
     * 
     * @return 操作的结果
     */
    public static native int rewind(long thedir);


    /**
     * 从指定目录中读取下一个条目.
     * 
     * @param finfo 文件信息结构，由apr_dir_read填写
     * @param wanted 所需的apr_finfo_t字段，作为APR_FINFO_值的位标志
     * @param thedir 从apr_dir_open返回的目录描述符. 对于读取的条目，不保证顺序
     * 
     * @return 操作的结果
     */
    public static native int read(FileInfo finfo, int wanted, long thedir);

}
