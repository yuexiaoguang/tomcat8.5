package org.apache.tomcat.jni;

import java.nio.ByteBuffer;

/** File
 */
public class File {

    /** 打开文件进行读取 */
    public static final int APR_FOPEN_READ       = 0x00001;
    /** 打开文件进行写入 */
    public static final int APR_FOPEN_WRITE      = 0x00002;
    /** 如果不存在则创建文件 */
    public static final int APR_FOPEN_CREATE     = 0x00004;
    /** 附加到文件末尾 */
    public static final int APR_FOPEN_APPEND     = 0x00008;
    /** 打开文件并截断为0长度 */
    public static final int APR_FOPEN_TRUNCATE   = 0x00010;
    /** 以二进制模式打开文件 */
    public static final int APR_FOPEN_BINARY     = 0x00020;
    /** 如果APR_CREATE和文件存在，则打开应该失败. */
    public static final int APR_FOPEN_EXCL       = 0x00040;
    /** 打开文件进行缓冲I/O */
    public static final int APR_FOPEN_BUFFERED   = 0x00080;
    /** 关闭后删除文件 */
    public static final int APR_FOPEN_DELONCLOSE = 0x00100;
    /** 
     * 平台相关标记，用于打开文件以跨多个线程使用
     */
    public static final int APR_FOPEN_XTHREAD     = 0x00200;
    /** 
     * 平台相关支持, 用于更高级别的锁定读/写访问, 以支持跨进程/机器的写入
     */
    public static final int APR_FOPEN_SHARELOCK   = 0x00400;
    /** 打开文件时不要注册清理 */
    public static final int APR_FOPEN_NOCLEANUP   = 0x00800;
    /** 
     * 咨询标志, 该文件应支持apr_socket_sendfile操作
     */
    public static final int APR_FOPEN_SENDFILE_ENABLED = 0x01000;
    /** 
     * 平台相关标志, 以启用大文件支持;
     * <br><b>Warning :</b> APR_LARGEFILE标志仅对某些 sizeof(apr_off_t) == 4 的平台有效.
     * 在实现的情况下，它允许打开和写入超过apr_off_t (2 千兆字节)的大小的文件.
     * 当文件大小超过2Gb时, apr_file_info_get() 将失败, 表现为描述符出错, 就像 apr_stat()/apr_lstat() 将在文件名上失败一样.
     * 根据特定的 APR_FINFO_* 标志, apr_dir_read()将在大文件的目录条目上使用 APR_INCOMPLETE 失败.
     * 通常，不建议使用此标志.
     */
    public static final int APR_FOPEN_LARGEFILE      = 0x04000;

    /** 设置文件位置 */
    public static final int APR_SET = 0;
    /** 当前位置 */
    public static final int APR_CUR = 1;
    /** 到文件末尾 */
    public static final int APR_END = 2;

    /* flags for apr_file_attrs_set */

    /** 文件是只读的 */
    public static final int APR_FILE_ATTR_READONLY   = 0x01;
    /** 文件是可执行的 */
    public static final int APR_FILE_ATTR_EXECUTABLE = 0x02;
    /** 文件被隐藏 */
    public static final int APR_FILE_ATTR_HIDDEN     = 0x04;


    /* File lock types/flags */

    /** 
     * 共享锁. 多个进程或线程可以在任何给定时间持有共享锁. 实质上, 这是一个 "读锁", 防止写入建立独占锁.
     */
    public static final int APR_FLOCK_SHARED    = 1;

    /** 
     * 独占锁. 在任何给定时间, 只有一个进程可以拥有独占锁. 类似于“写锁”.
     */
    public static final int APR_FLOCK_EXCLUSIVE = 2;
    /** 提取锁类型的掩码 */
    public static final int APR_FLOCK_TYPEMASK  = 0x000F;
    /** 获取文件锁时不要阻塞 */
    public static final int APR_FLOCK_NONBLOCK  = 0x0010;

    /* 
     * apr_file_info_t结构的filetype成员的apr_filetype_e值
     * <br><b>Warning :</b>: 并非所有下面的文件类型都可以确定.
     * 例如，如果在该平台上未明确标识该类型，则给定平台可能无法正确地将套接字描述符报告为APR_SOCK.
     * 在存在文件类型但无法通过下面的已识别标志描述的情况下，文件类型将为APR_UNKFILE. 如果未确定filetype成员，则类型将为APR_NOFILE.
     */

    /** 确定文件类型 */
    public static final int APR_NOFILE  = 0;
    /** 一个普通的文件 */
    public static final int APR_REG     = 1;
    /** 一个目录 */
    public static final int APR_DIR     = 2;
    /** 一个字符设备 */
    public static final int APR_CHR     = 3;
    /** 块设备 */
    public static final int APR_BLK     = 4;
    /** 一个 FIFO / 管道 */
    public static final int APR_PIPE    = 5;
    /** 一个符号链接 */
    public static final int APR_LNK     = 6;
    /** 一个[unix域名]套接字 */
    public static final int APR_SOCK    = 7;
    /** 一些其他未知类型的文件 */
    public static final int APR_UNKFILE = 127;


    /*
     * apr_file_permissions File Permissions flags
     */

    public static final int APR_FPROT_USETID     = 0x8000; /** Set user id */
    public static final int APR_FPROT_UREAD      = 0x0400; /** Read by user */
    public static final int APR_FPROT_UWRITE     = 0x0200; /** Write by user */
    public static final int APR_FPROT_UEXECUTE   = 0x0100; /** Execute by user */

    public static final int APR_FPROT_GSETID     = 0x4000; /** Set group id */
    public static final int APR_FPROT_GREAD      = 0x0040; /** Read by group */
    public static final int APR_FPROT_GWRITE     = 0x0020; /** Write by group */
    public static final int APR_FPROT_GEXECUTE   = 0x0010; /** Execute by group */

    public static final int APR_FPROT_WSTICKY    = 0x2000; /** Sticky bit */
    public static final int APR_FPROT_WREAD      = 0x0004; /** Read by others */
    public static final int APR_FPROT_WWRITE     = 0x0002; /** Write by others */
    public static final int APR_FPROT_WEXECUTE   = 0x0001; /** Execute by others */
    public static final int APR_FPROT_OS_DEFAULT = 0x0FFF; /** use OS's default permissions */


    public static final int APR_FINFO_LINK   = 0x00000001; /** Stat the link not the file itself if it is a link */
    public static final int APR_FINFO_MTIME  = 0x00000010; /** Modification Time */
    public static final int APR_FINFO_CTIME  = 0x00000020; /** Creation or inode-changed time */
    public static final int APR_FINFO_ATIME  = 0x00000040; /** Access Time */
    public static final int APR_FINFO_SIZE   = 0x00000100; /** Size of the file */
    public static final int APR_FINFO_CSIZE  = 0x00000200; /** Storage size consumed by the file */
    public static final int APR_FINFO_DEV    = 0x00001000; /** Device */
    public static final int APR_FINFO_INODE  = 0x00002000; /** Inode */
    public static final int APR_FINFO_NLINK  = 0x00004000; /** Number of links */
    public static final int APR_FINFO_TYPE   = 0x00008000; /** Type */
    public static final int APR_FINFO_USER   = 0x00010000; /** User */
    public static final int APR_FINFO_GROUP  = 0x00020000; /** Group */
    public static final int APR_FINFO_UPROT  = 0x00100000; /** User protection bits */
    public static final int APR_FINFO_GPROT  = 0x00200000; /** Group protection bits */
    public static final int APR_FINFO_WPROT  = 0x00400000; /** World protection bits */
    public static final int APR_FINFO_ICASE  = 0x01000000; /** if dev is case insensitive */
    public static final int APR_FINFO_NAME   = 0x02000000; /** -&gt;name in proper case */

    public static final int APR_FINFO_MIN    = 0x00008170; /** type, mtime, ctime, atime, size */
    public static final int APR_FINFO_IDENT  = 0x00003000; /** dev and inode */
    public static final int APR_FINFO_OWNER  = 0x00030000; /** user and group */
    public static final int APR_FINFO_PROT   = 0x00700000; /**  all protections */
    public static final int APR_FINFO_NORM   = 0x0073b170; /**  an atomic unix apr_stat() */
    public static final int APR_FINFO_DIRENT = 0x02000000; /**  an atomic unix apr_dir_read() */



    /**
     * 打开指定的文件.
     * 
     * @param fname 文件的完整路径 (在所有系统上使用 / )
     * @param flag 以下值:
     * <PRE>
     * APR_FOPEN_READ              打开读取
     * APR_FOPEN_WRITE             打开写入
     * APR_FOPEN_CREATE            如果不存在则创建文件
     * APR_FOPEN_APPEND            文件 ptr设置为在所有写入之前结束
     * APR_FOPEN_TRUNCATE          如果文件存在，则将长度设置为零
     * APR_FOPEN_BINARY            不是文本文件 (在UNIX上忽略此标志，因为它没有任何意义)
     * APR_FOPEN_BUFFERED          缓冲数据. 默认没有缓冲
     * APR_FOPEN_EXCL              如果APR_CREATE和文件存在则返回错误
     * APR_FOPEN_DELONCLOSE        关闭后删除文件.
     * APR_FOPEN_XTHREAD           平台相关标记，用于打开文件以跨多个线程使用
     * APR_FOPEN_SHARELOCK         平台相关支持, 用于更高级别的锁定读/写访问，以支持跨进程/机器的写入
     * APR_FOPEN_NOCLEANUP         不要在<em>pool</em>参数中传入的池中注册清理 (see below).
     *                             当池被销毁时，apr_file_t中的apr_os_file_t句柄不会被关闭.
     * APR_FOPEN_SENDFILE_ENABLED  使用适当的平台语义打开sendfile操作. 仅提供建议，apr_socket_sendfile不检查此标志.
     * </PRE>
     * 
     * @param perm 文件的访问权限.
     * @param pool 要使用的池.
     * 如果perm是APR_OS_DEFAULT并且正在创建文件, 将使用适当的默认权限.
     * 
     * @return 打开的文件描述符.
     * @throws Error 发生错误
     */
    public static native long open(String fname, int flag, int perm, long pool)
        throws Error;

    /**
     * 关闭指定的文件.
     * 
     * @param file 要关闭的文件描述符.
     * 
     * @return 操作状态
     */
    public static native int close(long file);

    /**
     * 刷新文件的缓冲区.
     * 
     * @param thefile 要刷新的文件描述符
     * 
     * @return 操作状态
     */
    public static native int flush(long thefile);

    /**
     * 打开一个临时文件
     * 
     * @param templ 创建临时文件时使用的模板.
     * @param flags 用于打开文件的标志. 如果是零, 使用APR_CREATE | APR_READ | APR_WRITE | APR_EXCL | APR_DELONCLOSE 打开文件
     * @param pool 用于分配文件的池.
     * 
     * @return 用作临时文件的apr文件. 此函数从模板生成唯一的临时文件名.
     * 模板的最后六个字符必须是XXXXXX, 并且这些字符将替换为使文件名唯一的字符串. 因为它会被修改, 模板不能是字符串常量, 但应声明为字符数组.
     * 
     * @throws Error 发生错误
     */
    public static native long mktemp(String templ, int flags, long pool)
        throws Error;

    /**
     * 删除指定的文件.
     * 
     * @param path 文件的完整路径 (在所有系统上使用 / )
     * @param pool 要使用的池. 如果文件是打开的, 在关闭所有实例之前不会删除它.
     * 
     * @return 操作状态
     */
    public static native int remove(String path, long pool);

    /**
     * 重命名指定的文件.
     * <br><b>Warning :</b> 如果新位置存在文件, 则会覆盖该文件. 可能无法跨设备移动文件或目录.
     * 
     * @param fromPath 原始文件的完整路径 (在所有系统上使用 / )
     * @param toPath 新文件的完整路径 (在所有系统上使用 / )
     * @param pool 要使用的池.
     * 
     * @return 操作状态
     */
    public static native int rename(String fromPath, String toPath, long pool);

    /**
     * 将指定的文件复制到另一个文件. 新文件不需要存在, 它将被创建.
     * <br><b>Warning :</b> 如果新文件已存在, 其内容将被覆盖.
     * 
     * @param fromPath 原始文件的完整路径 (在所有系统上使用 / )
     * @param toPath 新文件的完整路径 (在所有系统上使用 / )
     * @param perms 如果创建了新文件，其访问权限.
     *     代替通常或组合的文件权限, 可以给出值APR_FILE_SOURCE_PERMS, 在这种情况下，复制源文件的权限.
     * @param pool 要使用的池.
     * 
     * @return 操作状态
     */
    public static native int copy(String fromPath, String toPath, int perms, long pool);

    /**
     * 将指定的文件附加到另一个文件.
     * 新文件不需要存在，如果需要，将创建它.
     * 
     * @param fromPath 源文件的完整路径 (在所有系统上使用 / )
     * @param toPath 目标文件的完整路径 (在所有系统上使用 / )
     * @param perms 如果创建了新文件，其访问权限.
     *     代替通常或组合的文件权限, 可以给出值APR_FILE_SOURCE_PERMS, 在这种情况下，复制源文件的权限.
     * @param pool 要使用的池.
     * 
     * @return 操作状态
     */
    public static native int append(String fromPath, String toPath, int perms, long pool);

    /**
     * 将字符串写入指定的文件.
     * 
     * @param str 要写入的字符串. 必须以NULL结尾!
     * @param thefile 要写入的文件描述符
     * 
     * @return 操作状态
     */
    public static native int puts(byte [] str, long thefile);

    /**
     * 将读/写文件偏移量移动到文件中的指定字节.
     * 
     * @param thefile 文件描述符
     * @param where 如何移动指针, 以下其中之一:
     * <PRE>
     * APR_SET  --  设置 offset 到 offset
     * APR_CUR  --  添加 offset 到当前位置
     * APR_END  --  添加 offset 到当前文件位置
     * </PRE>
     * @param offset 将指针移动到的偏移量.
     * 
     * @return 指针实际上已移动到的偏移量.
     * @throws Error 如果读取文件时发生错误
     */
    public static native long seek(long thefile, int where, long offset)
        throws Error;

    /**
     * 将字符写入指定的文件.
     * 
     * @param ch 要写入的字符.
     * @param thefile 要写入的文件描述符
     * 
     * @return 操作状态
     */
    public static native int putc(byte ch, long thefile);

    /**
     * 将一个字符放回指定的流.
     * 
     * @param ch 要写入的字符.
     * @param thefile 要写入的文件描述符
     * 
     * @return 操作状态
     */
    public static native int ungetc(byte ch, long thefile);

    /**
     * 将数据写入指定的文件.
     *
     * 将写入指定的字节数，但不会更多. 如果操作系统无法写入那么多字节，它将尽可能多地写入. 修改第三个参数以反映*写入的字节数.
     *
     * 可以写入两个字节并返回错误. 永远不会返回APR_EINTR.
     * 
     * @param thefile 要写入的文件描述符.
     * @param buf 包含数据的缓冲区.
     * @param offset 在buf中开始偏移
     * @param nbytes 要写入的字节数; (-1) 表示全部数组.
     * 
     * @return 写入的字节数.
     */
    public static native int write(long thefile, byte[] buf, int offset, int nbytes);

    /**
     * 将数据写入指定的文件.
     *
     * 将写入指定的字节数，但不会更多. 如果操作系统无法写入那么多字节，它将尽可能多地写入. 修改第三个参数以反映*写入的字节数.
     *
     * 可以写入两个字节并返回错误. 永远不会返回APR_EINTR.
     * 
     * @param thefile 要写入的文件描述符.
     * @param buf 包含数据的直接字节缓冲区.
     * @param offset 在buf中开始偏移
     * @param nbytes 要写入的字节数
     * 
     * @return 写入的字节数.
     */
    public static native int writeb(long thefile, ByteBuffer buf, int offset, int nbytes);

    /**
     * 将数据写入指定的文件, 确保在返回之前写入所有数据.
     *
     * 将写入指定的字节数，但不会更多. 如果操作系统无法写入那么多字节, 进程/线程将阻塞, 直到它们可以被写入.
     * 诸如“空间不足”或“管道关闭”之类的异常错误将以错误终止.
     *
     * 可以写入两个字节并返回错误. 而且如果 *bytes_written 小于 nbytes, 伴随的错误是返回_always_.
     *
     * 永远不会返回APR_EINTR.
     * 
     * @param thefile 要写入的文件描述符.
     * @param buf 包含数据的缓冲区.
     * @param offset 在buf中的开始偏移量
     * @param nbytes 要写入的字节数; (-1) 表示全部数组.
     * 
     * @return 写入的字节数.
     */
    public static native int writeFull(long thefile, byte[] buf, int offset, int nbytes);

    /**
     * 将数据写入指定的文件, 确保在返回之前写入所有数据.
     *
     * 将写入指定的字节数，但不会更多. 如果操作系统无法写入那么多字节, 进程/线程将阻塞, 直到它们可以被写入.
     * 诸如“空间不足”或“管道关闭”之类的异常错误将以错误终止.
     *
     * 可以写入两个字节并返回错误. 而且如果 *bytes_written 小于 nbytes, 伴随的错误是返回_always_.
     *
     * 永远不会返回APR_EINTR.
     * 
     * @param thefile 要写入的文件描述符.
     * @param buf 包含数据的直接字节缓冲区.
     * @param offset 在buf中的开始偏移量
     * @param nbytes 要写入的字节数
     * 
     * @return 写入的字节数.
     */
    public static native int writeFullb(long thefile, ByteBuffer buf, int offset, int nbytes);

    /**
     * 将数组字节数组中的数据写入指定文件.
     *
     * 可以写入两个字节并返回错误. 永远不会返回APR_EINTR.
     *
     * 即使底层操作系统不提供writev(), apr_file_writev也可用.
     * 
     * @param thefile 要写入的文件描述符.
     * @param vec 从中获取数据以写入文件的数组.
     * 
     * @return 写入的字节数.
     */
    public static native int writev(long thefile, byte[][] vec);

    /**
     * 将数组字节数组中的数据写入指定文件, 确保在返回之前写入所有数据.
     *
     * 即使底层操作系统不提供writev(), writevFull也可用.
     * 
     * @param thefile 要写入的文件描述符.
     * @param vec 从中获取数据以写入文件的数组.
     * 
     * @return 写入的字节数.
     */
    public static native int writevFull(long thefile, byte[][] vec);

    /**
     * 从指定文件中读取数据.
     *
     * apr_file_read将读取指定的字节数，但不会更多. 如果没有足够的数据来填充该字节数，则会读取所有可用数据.
     * 修改第三个参数以反映读取的字节数. 如果通过ungetc将char放回流中，它将是返回的第一个字符.
     *
     * 不能读取两个字节并返回APR_EOF或其他错误. 永远不会返回APR_EINTR.
     * 
     * @param thefile 要读取的文件描述符.
     * @param buf 用于存储数据的缓冲区.
     * @param offset 在buf中的开始偏移量
     * @param nbytes 要读取的字节数; (-1) 表示全部数组.
     * 
     * @return 读取的字节数.
     */
    public static native int read(long thefile, byte[] buf,  int offset, int nbytes);

    /**
     * 从指定文件中读取数据.
     *
     * apr_file_read将读取指定的字节数，但不会更多. 如果没有足够的数据来填充该字节数，则会读取所有可用数据.
     * 修改第三个参数以反映读取的字节数. 如果通过ungetc将char放回流中，它将是返回的第一个字符.
     *
     * 不能读取两个字节并返回APR_EOF或其他错误. 永远不会返回APR_EINTR.
     * 
     * @param thefile 要读取的文件描述符.
     * @param buf 用于存储数据的直接字节缓冲区.
     * @param offset 在buf中的开始偏移量
     * @param nbytes 要读取的字节数.
     * 
     * @return 读取的字节数.
     */
    public static native int readb(long thefile, ByteBuffer buf,  int offset, int nbytes);

    /**
     * 从指定文件中读取数据, 确保在返回之前填充缓冲区.
     *
     * 将读取指定的字节数，但不会更多. 如果没有足够的数据来填充该字节数, 然后进程/线程将阻塞, 直到它可用或到达EOF.
     * 如果通过ungetc将char放回流中，它将是返回的第一个字符.
     *
     * 可以读取两个字节并返回错误. 如果bytes_read小于nbytes，则总是返回附带的错误.
     *
     * 永远不会返回APR_EINTR.
     * 
     * @param thefile 要读取的文件描述符.
     * @param buf 用于存储数据的缓冲区.
     * @param offset 在buf中的开始偏移量
     * @param nbytes 要读取的字节数; (-1) 表示全部数组.
     * 
     * @return 读取的字节数.
     */
    public static native int readFull(long thefile, byte[] buf,  int offset, int nbytes);

    /**
     * 从指定文件中读取数据, 确保在返回之前填充缓冲区.
     *
     * 将读取指定的字节数，但不会更多. 如果没有足够的数据来填充该字节数, 然后进程/线程将阻塞, 直到它可用或到达EOF.
     * 如果通过ungetc将char放回流中，它将是返回的第一个字符.
     *
     * 可以读取两个字节并返回错误. 如果bytes_read小于nbytes，则总是返回附带的错误.
     *
     * 永远不会返回APR_EINTR.
     * 
     * @param thefile 要读取的文件描述符.
     * @param buf 用于存储数据的直接字节缓冲区.
     * @param offset 在buf中的开始偏移量
     * @param nbytes 要读取的字节数
     * 
     * @return 读取的字节数.
     */
    public static native int readFullb(long thefile, ByteBuffer buf,  int offset, int nbytes);

    /**
     * 从指定的文件中读取一个字符串. 如果存储任何字符, 缓冲区将以NULL结尾.
     * 
     * @param buf 用于存储字符串的缓冲区.
     * @param offset 在buf中的开始偏移量
     * @param thefile 要读取的文件描述符
     * 
     * @return 读取的字节数.
     */
    public static native int gets(byte[] buf,  int offset, long thefile);


    /**
     * 从指定的文件中读取一个字符.
     * 
     * @param thefile 要读取的文件描述符
     * 
     * @return 读取的字符
     * @throws Error 如果读取文件时发生错误
     */
    public static native int getc(long thefile)
        throws Error;

    /**
     * 是否在文件的末尾
     * 
     * @param fptr 正在测试的apr文件.
     * 
     * @return 如果在文件的末尾, 则返回APR_EOF; 否则 APR_SUCCESS.
     */
    public static native int eof(long fptr);

    /**
     * 返回当前文件的文件名.
     * 
     * @param thefile 当前打开的文件.
     * 
     * @return 文件名
     */
    public static native String nameGet(long thefile);

    /**
     * 设置指定文件的权限位.
     * <br><b>Warning :</b> 某些平台可能无法应用所有可用的权限位; 如果指定了某些无法设置的权限，则将返回APR_INCOMPLETE.
     * <br><b>Warning :</b> 未实现此功能的平台将返回APR_ENOTIMPL.
     * 
     * @param fname 要应用权限的文件（名称）.
     * @param perms 要应用于文件的权限位.
     * 
     * @return 操作状态
     */
    public static native int permsSet(String fname, int perms);

    /**
     * 设置指定文件的属性.
     * 应优先使用此函数, 而不是显式操作文件权限, 因为提供这些属性的操作是特定于平台的, 并且可能不仅仅涉及设置权限位.
     * <br><b>Warning :</b> 未实现此功能的平台将返回APR_ENOTIMPL.
     *      
     * @param fname 文件的完整路径 (在所有系统上使用 / )
     * @param attributes 以下组合
     * <PRE>
     *            APR_FILE_ATTR_READONLY   - 使文件只读
     *            APR_FILE_ATTR_EXECUTABLE - 使文件可执行
     *            APR_FILE_ATTR_HIDDEN     - 使文件隐藏
     * </PRE>
     * @param mask 属性中有效位的掩码.
     * @param pool 要使用的池.
     * 
     * @return 操作状态
     */
    public static native int  attrsSet(String fname, int attributes, int mask, long pool);

    /**
     * 设置指定文件的mtime.
     * <br><b>Warning :</b> 未实现此功能的平台将返回APR_ENOTIMPL.
     *      
     * @param fname 文件的完整路径 (在所有系统上使用 / )
     * @param mtime 应用于文件的mtime, 以微秒为单位
     * @param pool 要使用的池.
     * 
     * @return 操作状态
     */
    public static native int  mtimeSet(String fname, long mtime, long pool);

    /**
     * 在指定的打开文件上建立锁. 锁可以是建议性的或强制性的，由平台决定.
     * 锁作为整体应用于文件, 而不是特定的范围. 锁是基于每个线程/进程建立的; 同一个线程的第二个锁不会阻塞.
     * 
     * @param thefile 要锁定的文件.
     * @param type 要在文件上建立的锁的类型.
     * 
     * @return 操作状态
     */
    public static native int lock(long thefile, int type);

    /**
     * 删除文件上任何未完成的锁.
     * 
     * @param thefile 要解锁的文件.
     * 
     * @return 操作状态
     */
    public static native int unlock(long thefile);

    /**
     * 检索文件打开时传递给 apr_file_open() 的标志.
     * 
     * @param file 要检索标志的文件.
     * 
     * @return 标志
     */
    public static native int flagsGet(long file);

    /**
     * 将文件的长度截断为指定的偏移量
     * 
     * @param fp 要截断的文件
     * @param offset 截断到的偏移量.
     * 
     * @return 操作状态
     */
    public static native int trunc(long fp, long offset);

    /**
     * 创建一个匿名管道.
     * 
     * @param io io[0] 用作管道输入的文件描述符.
     *           io[1] 用作管道输出的文件描述符.
     * @param pool 操作的池.
     * 
     * @return 操作状态
     */
    public static native int pipeCreate(long [] io, long pool);

    /**
     * 获取管道的超时值或操纵阻塞状态.
     * 
     * @param thepipe 正在获取其超时时间的管道.
     * 
     * @return 当前超时值，以微秒为单位.
     * @throws Error 发生错误
     */
    public static native long pipeTimeoutGet(long thepipe)
        throws Error;

    /**
     * 设置管道的超时值或操纵阻塞状态.
     * 
     * @param thepipe 正在设置其超时时间的管道.
     * @param timeout 超时值，以微秒为单位.  值 &lt; 0 表示一直等待, 0 表示不要等待.
     *        
     * @return 操作状态
     */
    public static native int pipeTimeoutSet(long thepipe, long timeout);

    /**
     * 复制指定的文件描述符.
     * 
     * @param newFile 要复制的文件. newFile 必须指向有效的 apr_file_t, 或指向 NULL.
     * @param oldFile 要复制的文件.
     * @param pool 用于新文件的池.
     * 
     * @return 重复的文件结构.
     * @throws Error 如果读取文件描述符时发生错误
     */
    public static native long dup(long newFile, long oldFile, long pool)
        throws Error;

    /**
     * 复制指定的文件描述符并关闭原始文件.
     * 
     * @param newFile 要关闭并重用的旧文件. newFile 必须指向有效的 apr_file_t. 它不能为NULL.
     * @param oldFile 要复制的文件.
     * @param pool 用于新文件的池.
     * 
     * @return 操作状态
     */
    public static native int dup2(long newFile, long oldFile, long pool);

    /**
     * 获取指定文件的统计信息. 该文件由filename指定，而不是使用预先打开的文件.
     * 
     * @param finfo 存储有关文件的信息的位置，如果调用失败，则永远不会触及该信息.
     * @param fname 要统计的文件名.
     * @param wanted 所需的apr_finfo_t字段, 作为APR_FINFO_值的一个标志
     * @param pool 用于分配新文件的池.
     * 
     * @return 操作状态
     */
    public static native int stat(FileInfo finfo, String fname, int wanted, long pool);

    /**
     * 获取指定文件的统计信息. 该文件由filename指定，而不是使用预先打开的文件.
     * 
     * @param fname 要统计的文件名.
     * @param wanted 所需的apr_finfo_t字段, 作为APR_FINFO_值的一个标志
     * @param pool 用于分配新文件的池.
     * 
     * @return FileInfo object.
     */
    public static native FileInfo getStat(String fname, int wanted, long pool);

    /**
     * 获取指定文件的统计信息.
     * 
     * @param finfo 存储有关文件的信息的位置.
     * @param wanted 所需的apr_finfo_t字段, 作为APR_FINFO_值的一个标志
     * @param thefile 获取有关信息的文件.
     * 
     * @return 操作状态
     */
    public static native int infoGet(FileInfo finfo, int wanted, long thefile);


    /**
     * 获取指定文件的统计信息.
     * 
     * @param wanted 所需的apr_finfo_t字段, 作为APR_FINFO_值的一个标志
     * @param thefile 获取有关信息的文件.
     * 
     * @return FileInfo object.
     */
    public static native FileInfo getInfo(int wanted, long thefile);

}
