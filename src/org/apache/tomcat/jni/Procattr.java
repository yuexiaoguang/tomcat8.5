package org.apache.tomcat.jni;

/** Procattr
 */
public class Procattr {

    /**
     * 创建并初始化一个新的procattr变量
     * 
     * @param cont 要使用的池
     * 
     * @return 新创建的procattr.
     * @throws Error 发生错误
     */
    public static native long create(long cont)
        throws Error;

    /**
     * 确定在启动子进程时是否应将stdin，stdout或stderr中的任何一个链接到管道.
     * 
     * @param attr 关心的procattr.
     * @param in stdin是否链接到父级的管道?
     * @param out stdout是否链接到父级的管道?
     * @param err stderr是否链接到父级的管道?
     * 
     * @return 操作状态
     */
    public static native int ioSet(long attr, int in, int out, int err);

    /**
     * 将child_in和parent_in值设置为现有的apr_file_t值.
     * <br>
     * 这不是必需的初始化函数. 如果您已经打开了要使用的管道（或多个文件）, 这将非常有用, 也许持久地跨多个进程调用 - 例如一个日志文件.
     * 您可以通过不创建自己的管道来保存一些额外的函数调用，因为这会在进程空间中为您创建一个.
     * 
     * @param attr 关心的procattr.
     * @param in apr_file_t用作child_in的值. 必须是有效的文件.
     * @param parent apr_file_t用作parent_in的值. 必须是有效的文件.
     * 
     * @return 操作状态
     */
    public static native int childInSet(long attr, long in, long parent);

    /**
     * 将child_out和parent_out值设置为现有的apr_file_t值.
     * <br>
     * 这不是必需的初始化函数. 如果您已经打开了要使用的管道（或多个文件）, 这将非常有用, 也许持久地跨多个进程调用 - 例如一个日志文件.
     * 
     * @param attr 关心的procattr.
     * @param out apr_file_t用作child_out的值. 必须是有效的文件.
     * @param parent apr_file_t用作parent_out的值. 必须是有效的文件.
     * 
     * @return 操作状态
     */
    public static native int childOutSet(long attr, long out, long parent);

    /**
     * 将child_err和parent_err值设置为现有的apr_file_t值.
     * <br>
     * 这不是必需的初始化函数. 如果您已经打开了要使用的管道（或多个文件）, 这将非常有用, 也许持久地跨多个进程调用 - 例如一个日志文件.
     * 
     * @param attr 关心的procattr.
     * @param err  apr_file_t用作child_err的值. 必须是有效的文件.
     * @param parent apr_file_t用作parent_err的值. 必须是有效的文件.
     * 
     * @return 操作状态
     */
    public static native int childErrSet(long attr, long err, long parent);

    /**
     * 设置子进程应该从哪个目录开始执行.
     * 
     * @param attr 关心的procattr.
     * @param dir 哪个目录开始. 默认情况下, 这与创建createprocess调用时, 父级当前所在的目录相同.
     *            
     * @return 操作状态
     */
    public static native int dirSet(long attr, String dir);

    /**
     * 设置子进程将调用的命令类型.
     * 
     * @param attr 关心的procattr.
     * @param cmd 命令的类型. 以下其中之一:
     * <PRE>
     * APR_SHELLCMD     --  shell可以处理的任何东西
     * APR_PROGRAM      --  可执行程序   (default)
     * APR_PROGRAM_ENV  --  可执行程序, 复制环境
     * APR_PROGRAM_PATH --  PATH上的可执行程序, 复制环境
     * </PRE>
     * @return 操作状态
     */
    public static native int cmdtypeSet(long attr, int cmd);

    /**
     * 确定子进程是否应该处于分离状态.
     * 
     * @param attr 关心的procattr.
     * @param detach 子进程开始处于分离状态?  默认不是.
     * 
     * @return 操作状态
     */
    public static native int detachSet(long attr, int detach);

    /**
     * 指定apr_proc_create()应该尽一切可能向apr_proc_create()的调用者报告失败, 而不是在子进程上找到.
     * 
     * @param attr 描述要创建的子进程的procattr.
     * @param chk 指示是否应该进行额外的工作以尝试向调用者报告失败.
     * <br>
     * 此标志仅影响使用fork()的平台上的apr_proc_create(). 这导致调用过程中的额外开销, 但这可能有助于应用程序更优雅地处理此类错误.
     * 
     * @return 操作状态
     */
    public static native int errorCheckSet(long attr, int chk);

    /**
     * 确定子项是应该从自己的地址空间开始, 还是使用父项中的当前地址空间
     * 
     * @param attr 关心的procattr.
     * @param addrspace 子项应该从自己的地址空间开始?  在NetWare上默认为no，在其他平台上为yes.
     * 
     * @return 操作状态
     */
    public static native int addrspaceSet(long attr, int addrspace);

    /**
     * 如果APR在运行指定程序之前遇到错误, 请指定要在子进程中调用的错误函数.
     * 
     * @param attr 描述要创建的子进程的procattr.
     * @param pool 要使用的池.
     * @param o 要在子进程中调用的Object.
     * <br>
     * 目前，它只会在使用fork()的平台上从apr_proc_create()调用. 
     * 永远不会在其他平台上调用它, 在这些平台上，apr_proc_create()将在父进程中返回错误，而不是在now-forked子进程中调用回调.
     */
    public static native void errfnSet(long attr, long pool, Object o);

    /**
     * 设置用于运行进程的用户名
     * 
     * @param attr 关心的procattr.
     * @param username 使用的用户名
     * @param password 用户密码. WIN32或任何其他具有APR_PROCATTR_USER_SET_REQUIRES_PASSWORD设置的平台需要密码.
     *                 
     * @return 操作状态
     */
    public static native int userSet(long attr, String username, String password);

    /**
     * 设置用于运行进程的组
     * 
     * @param attr 关心的procattr.
     * @param groupname 使用的组名称
     * 
     * @return 操作状态
     */
    public static native int groupSet(long attr, String groupname);

}
