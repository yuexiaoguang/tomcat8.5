package org.apache.tomcat.jni;

/** Proc
 */
public class Proc {

    /*
     * apr_cmdtype_e enum
     */
    public static final int APR_SHELLCM      = 0; /** 使用shell来调用程序 */
    public static final int APR_PROGRAM      = 1; /** 直接调用程序, 没有复制的环境 */
    public static final int APR_PROGRAM_ENV  = 2; /** 调用程序，复制我们的环境 */
    public static final int APR_PROGRAM_PATH = 3; /** 在PATH上找到程序，使用我们的环境 */
    public static final int APR_SHELLCMD_ENV = 4; /** 使用shell来调用程序，复制我们的环境*/

    /*
     * apr_wait_how_e enum
     */
    public static final int APR_WAIT   = 0; /** 等待指定的进程完成 */
    public static final int APR_NOWAIT = 1; /** 不要等 -- 看看它是否已经完成 */

    /*
     * apr_exit_why_e enum
     */
    public static final int APR_PROC_EXIT        = 1; /** 进程正常退出 */
    public static final int APR_PROC_SIGNAL      = 2; /** 进程由于信号而退出 */
    public static final int APR_PROC_SIGNAL_CORE = 4; /** 进程退出并转储核心文件 */

    public static final int APR_NO_PIPE       = 0;
    public static final int APR_FULL_BLOCK    = 1;
    public static final int APR_FULL_NONBLOCK = 2;
    public static final int APR_PARENT_BLOCK  = 3;
    public static final int APR_CHILD_BLOCK   = 4;

    public static final int APR_LIMIT_CPU     = 0;
    public static final int APR_LIMIT_MEM     = 1;
    public static final int APR_LIMIT_NPROC   = 2;
    public static final int APR_LIMIT_NOFILE  = 3;


    /** 子级已经死了, 调用者必须调用注销 */
    public static final int APR_OC_REASON_DEATH      = 0;
    /** write_fd 不可写 */
    public static final int APR_OC_REASON_UNWRITABLE = 1;
    /** 
     * 正在重新启动，执行任何必要的清理 (包括向子级发送特殊信号)
     */
    public static final int APR_OC_REASON_RESTART    = 2;
    /** 
     * 取消注册已被调用，做任何必要的事情 (包括杀死子级)
     */
    public static final int APR_OC_REASON_UNREGISTER = 3;
    /** 不知为何，子级离开了我们 ... buggy os? */
    public static final int APR_OC_REASON_LOST       = 4;
    /** 
     * 正在进行健康检查, 对于大多数维护功能, 这是一个无操作.
     */
    public static final int APR_OC_REASON_RUNNING    = 5;

    /* apr_kill_conditions_e enumeration */
    /** 进程永远不会发送任何信号 */
    public static final int APR_KILL_NEVER         = 0;
    /** 进程在apr_pool_t清理时发送SIGKILL */
    public static final int APR_KILL_ALWAYS        = 1;
    /** SIGTERM, 等待3秒, SIGKILL */
    public static final int APR_KILL_AFTER_TIMEOUT = 2;
    /** 一直等待这个进程完成 */
    public static final int APR_JUST_WAIT          = 3;
    /** 发送SIGTERM然后等待 */
    public static final int APR_KILL_ONLY_ONCE     = 4;

    public static final int APR_PROC_DETACH_FOREGROUND = 0; /** 不要分离 */
    public static final int APR_PROC_DETACH_DAEMONIZE  = 1; /** 分离 */

    /* 创建进程调用的最大参数数量 */
    public static final int MAX_ARGS_SIZE          = 1024;
    /* 创建进程调用的最大环境变量数量 */
    public static final int MAX_ENV_SIZE           = 1024;

    /**
     * 从池中分配apr_proc_t结构.
     * 这不是apr功能.
     * 
     * @param cont 要使用的池.
     * @return 指针
     */
    public static native long alloc(long cont);

    /**
     * 这是目前APR中唯一的非便携式调用. 这将执行标准的unix fork.
     * 
     * @param proc 由此产生的进程句柄.
     * @param cont 要使用的池.
     * 
     * @return APR_INCHILD 对应子级, 和 APR_INPARENT 对应父级或错误.
     */
    public static native int fork(long [] proc, long cont);

    /**
     * 创建一个新流程并在该流程中执行新程序.
     * 此函数返回而不等待新进程终止; 使用apr_proc_wait.
     * 
     * @param proc 进程句柄
     * @param progname 要运行的程序
     * @param args 传递给新程序的参数. 第一个应该是程序名称.
     * @param env 新进程的新环境表. 这应该是以NULL结尾的字符串列表. 对于APR_PROGRAM_ENV，APR_PROGRAM_PATH和APR_SHELLCMD_ENV类型的命令，将忽略此参数.
     * @param attr 应该使用procattr来确定如何创建新进程
     * @param pool 要使用的池.
     * 
     * @return 产生的进程句柄.
     */
    public static native int create(long proc, String progname,
                                    String [] args, String [] env,
                                    long attr, long pool);

    /**
     * 等待子进程死亡
     * 
     * @param proc 与所需子进程对应的进程句柄
     * @param exit exit[0] 如果子进程终止, 则返回子进程的退出状态, 或导致子进程死亡的信号.
     *                在不支持获取此信息的平台上, status参数将作为APR_ENOTIMPL返回.
     * exit[1] 为什么子进程死了, 以下其中之一:
     * <PRE>
     * APR_PROC_EXIT         -- 进程正常终止
     * APR_PROC_SIGNAL       -- 进程被一个信号杀死
     * APR_PROC_SIGNAL_CORE  -- 进程被一个信号杀死, 并生成了核心转储.
     * </PRE>
     * @param waithow 该怎么等待. 其中之一:
     * <PRE>
     * APR_WAIT   -- 阻塞, 直到子进程死亡.
     * APR_NOWAIT -- 无论子级是否死亡，都会立即返回.
     * </PRE>
     * @return childs状态位于此进程的返回代码中. 以下其中之一:
     * <PRE>
     * APR_CHILD_DONE     -- 子级不再运行.
     * APR_CHILD_NOTDONE  -- 子级仍然在运行.
     * </PRE>
     */
    public static native int wait(long proc, int [] exit, int waithow);

    /**
     * 等待任何当前子进程死亡并返回有关该子进程的信息.
     * 
     * @param proc 输入时指向NULL的指针将填充子进程信息
     * @param exit exit[0] 如果子进程终止, 则返回子进程的退出状态, 或导致子进程死亡的信号.
     *                在不支持获取此信息的平台上, status参数将作为APR_ENOTIMPL返回.
     * exit[1] 为什么子进程死了, 以下其中之一:
     * <PRE>
     * APR_PROC_EXIT         -- 进程正常终止
     * APR_PROC_SIGNAL       -- 进程被一个信号杀死
     * APR_PROC_SIGNAL_CORE  -- 进程被一个信号杀死, 并生成了核心转储.
     * </PRE>
     * @param waithow 该怎么等待. 其中之一:
     * <PRE>
     * APR_WAIT   -- 阻塞, 直到子进程死亡.
     * APR_NOWAIT -- 无论子级是否死亡，都会立即返回.
     * </PRE>
     * @param pool 用于分配子进程信息的池.
     * 
     * @return 操作状态
     */
    public static native int waitAllProcs(long proc, int [] exit, int waithow, long pool);

     /**
     * 从控制终端分离进程.
     * 
     * @param daemonize 如果进程应该守护并成为后台进程, 则设置为非零; 否则它会留在前台.
     *                  
     * @return 操作状态
     */
    public static native int detach(int daemonize);

    /**
     * 终止进程.
     * 
     * @param proc 要终止的进程.
     * @param sig 如何杀死进程.
     * 
     * @return 操作状态
     */
    public static native int kill(long proc, int sig);

}
