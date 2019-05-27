package org.apache.tomcat.jni;

/** Status
 */
public class Status {

    /**
     * APR_OS_START_ERROR是APR特定错误值开始的位置.
     */
     public static final int APR_OS_START_ERROR   = 20000;
    /**
     * APR_OS_ERRSPACE_SIZE是您可以适应以下错误/状态范围之一的最大错误数 -- 排除APR_OS_START_USERERR.
     */
     public static final int APR_OS_ERRSPACE_SIZE = 50000;
    /**
     * APR_OS_START_STATUS是APR特定状态码的起始位置.
     */
     public static final int APR_OS_START_STATUS  = (APR_OS_START_ERROR + APR_OS_ERRSPACE_SIZE);

    /**
     * APR_OS_START_USERERR为使用APR的应用程序保留, APR将自己的错误码与APR一起分层.
     * 注意，紧跟在此之后的错误被设置为比平时更远十倍, 这样apr的用户就有很大的空间可以声明自定义错误码.
     */
    public static final int APR_OS_START_USERERR  = (APR_OS_START_STATUS + APR_OS_ERRSPACE_SIZE);
    /**
     * APR_OS_START_USEERR已过时, 仅为兼容性而定义.
     * 请改用APR_OS_START_USERERR.
     */
    public static final int APR_OS_START_USEERR    = APR_OS_START_USERERR;
    /**
     * APR_OS_START_CANONERR是在没有相应错误的系统上定义了errno值的APR版本的地方.
     */
    public static final int APR_OS_START_CANONERR  = (APR_OS_START_USERERR + (APR_OS_ERRSPACE_SIZE * 10));

    /**
     * APR_OS_START_EAIERR将getaddrinfo()中的EAI_错误代码折叠为apr_status_t值.
     */
    public static final int APR_OS_START_EAIERR  = (APR_OS_START_CANONERR + APR_OS_ERRSPACE_SIZE);
    /**
     * APR_OS_START_SYSERR将特定于平台的系统错误值折叠为apr_status_t值.
     */
    public static final int APR_OS_START_SYSERR  = (APR_OS_START_EAIERR + APR_OS_ERRSPACE_SIZE);

    /** 没有错误. */
    public static final int APR_SUCCESS = 0;

    /**
     * APR 错误值
     * <PRE>
     * <b>APR ERROR VALUES</b>
     * APR_ENOSTAT      APR无法对该文件执行统计
     * APR_ENOPOOL      没有为APR提供用于分配内存的池
     * APR_EBADDATE     APR被指定无效日期
     * APR_EINVALSOCK   APR被赋予了无效的套接字
     * APR_ENOPROC      APR没有给出流程结构
     * APR_ENOTIME      APR没有给出时间结构
     * APR_ENODIR       APR没有给出目录结构
     * APR_ENOLOCK      APR未获得锁结构
     * APR_ENOPOLL      APR未获得轮询结构
     * APR_ENOSOCKET    APR未获得套接字
     * APR_ENOTHREAD    APR未获得线程结构
     * APR_ENOTHDKEY    APR未获得线程Key结构
     * APR_ENOSHMAVAIL  没有更多共享内存可用
     * APR_EDSOOPEN     APR无法打开dso对象. 有关更多信息，请调用 apr_dso_error().
     * APR_EGENERAL     一般失败 (没有具体信息)
     * APR_EBADIP       指定的IP地址无效
     * APR_EBADMASK     指定的网络掩码无效
     * APR_ESYMNOTFOUND 找不到请求的符号
     * </PRE>
     *
     */
    public static final int APR_ENOSTAT       = (APR_OS_START_ERROR + 1);
    public static final int APR_ENOPOOL       = (APR_OS_START_ERROR + 2);
    public static final int APR_EBADDATE      = (APR_OS_START_ERROR + 4);
    public static final int APR_EINVALSOCK    = (APR_OS_START_ERROR + 5);
    public static final int APR_ENOPROC       = (APR_OS_START_ERROR + 6);
    public static final int APR_ENOTIME       = (APR_OS_START_ERROR + 7);
    public static final int APR_ENODIR        = (APR_OS_START_ERROR + 8);
    public static final int APR_ENOLOCK       = (APR_OS_START_ERROR + 9);
    public static final int APR_ENOPOLL       = (APR_OS_START_ERROR + 10);
    public static final int APR_ENOSOCKET     = (APR_OS_START_ERROR + 11);
    public static final int APR_ENOTHREAD     = (APR_OS_START_ERROR + 12);
    public static final int APR_ENOTHDKEY     = (APR_OS_START_ERROR + 13);
    public static final int APR_EGENERAL      = (APR_OS_START_ERROR + 14);
    public static final int APR_ENOSHMAVAIL   = (APR_OS_START_ERROR + 15);
    public static final int APR_EBADIP        = (APR_OS_START_ERROR + 16);
    public static final int APR_EBADMASK      = (APR_OS_START_ERROR + 17);
    public static final int APR_EDSOOPEN      = (APR_OS_START_ERROR + 19);
    public static final int APR_EABSOLUTE     = (APR_OS_START_ERROR + 20);
    public static final int APR_ERELATIVE     = (APR_OS_START_ERROR + 21);
    public static final int APR_EINCOMPLETE   = (APR_OS_START_ERROR + 22);
    public static final int APR_EABOVEROOT    = (APR_OS_START_ERROR + 23);
    public static final int APR_EBADPATH      = (APR_OS_START_ERROR + 24);
    public static final int APR_EPATHWILD     = (APR_OS_START_ERROR + 25);
    public static final int APR_ESYMNOTFOUND  = (APR_OS_START_ERROR + 26);
    public static final int APR_EPROC_UNKNOWN = (APR_OS_START_ERROR + 27);
    public static final int APR_ENOTENOUGHENTROPY = (APR_OS_START_ERROR + 28);

    /** APR 状态值
     * <PRE>
     * <b>APR STATUS VALUES</b>
     * APR_INCHILD        程序当前在子级中执行
     * APR_INPARENT       程序当前在父级中执行
     * APR_DETACH         线程已分离
     * APR_NOTDETACH      线程没有分离
     * APR_CHILD_DONE     子级已经完成执行
     * APR_CHILD_NOTDONE  子级未完成执行
     * APR_TIMEUP         操作超时
     * APR_INCOMPLETE     虽然进行了一些处理并且结果部分有效，但操作不完整
     * APR_BADCH          Getopt在选项字符串中找不到选项
     * APR_BADARG         Getopt找到了一个缺少参数的选项，并在选项字符串中指定了参数
     * APR_EOF            APR遇到了文件的结尾
     * APR_NOTFOUND       APR无法在轮询结构中找到套接字
     * APR_ANONYMOUS      APR正在使用匿名共享内存
     * APR_FILEBASED      APR使用文件名作为共享内存的Key
     * APR_KEYBASED       APR使用共享Key作为共享内存的Key
     * APR_EINIT          初始化值. 如果没有找到选项, 但状态变量需要一个值, 应该使用它
     * APR_ENOTIMPL       APR功能尚未在此平台上实现, 要么是因为还没有人接受它, 或在此平台上无法使用该功能.
     * APR_EMISMATCH      两个密码不匹配.
     * APR_EBUSY          给定的锁很忙.
     * </PRE>
     *
     */
    public static final int APR_INCHILD       = (APR_OS_START_STATUS + 1);
    public static final int APR_INPARENT      = (APR_OS_START_STATUS + 2);
    public static final int APR_DETACH        = (APR_OS_START_STATUS + 3);
    public static final int APR_NOTDETACH     = (APR_OS_START_STATUS + 4);
    public static final int APR_CHILD_DONE    = (APR_OS_START_STATUS + 5);
    public static final int APR_CHILD_NOTDONE = (APR_OS_START_STATUS + 6);
    public static final int APR_TIMEUP        = (APR_OS_START_STATUS + 7);
    public static final int APR_INCOMPLETE    = (APR_OS_START_STATUS + 8);
    public static final int APR_BADCH         = (APR_OS_START_STATUS + 12);
    public static final int APR_BADARG        = (APR_OS_START_STATUS + 13);
    public static final int APR_EOF           = (APR_OS_START_STATUS + 14);
    public static final int APR_NOTFOUND      = (APR_OS_START_STATUS + 15);
    public static final int APR_ANONYMOUS     = (APR_OS_START_STATUS + 19);
    public static final int APR_FILEBASED     = (APR_OS_START_STATUS + 20);
    public static final int APR_KEYBASED      = (APR_OS_START_STATUS + 21);
    public static final int APR_EINIT         = (APR_OS_START_STATUS + 22);
    public static final int APR_ENOTIMPL      = (APR_OS_START_STATUS + 23);
    public static final int APR_EMISMATCH     = (APR_OS_START_STATUS + 24);
    public static final int APR_EBUSY         = (APR_OS_START_STATUS + 25);

    public static final int TIMEUP            = (APR_OS_START_USERERR + 1);
    public static final int EAGAIN            = (APR_OS_START_USERERR + 2);
    public static final int EINTR             = (APR_OS_START_USERERR + 3);
    public static final int EINPROGRESS       = (APR_OS_START_USERERR + 4);
    public static final int ETIMEDOUT         = (APR_OS_START_USERERR + 5);

    private static native boolean is(int err, int idx);
    /*
     * APR_STATUS_IS Status Value Tests
     * <br><b>Warning :</b> 对于任何特定的错误条件, 这些测试中可能不止一个匹配.
     * 		这是因为特定于平台的错误码可能并不总是与POSIX代码的语义相匹配, 因此这些测试（以及相应的APR错误码）以其命名.
     * 		一个值得注意的例子是Win32平台上的APR_STATUS_IS_ENOENT和APR_STATUS_IS_ENOTDIR测试. 程序员应该始终意识到这一点并相应地调整测试的顺序.
     *
     */
    public static final boolean APR_STATUS_IS_ENOSTAT(int s)    { return is(s, 1); }
    public static final boolean APR_STATUS_IS_ENOPOOL(int s)    { return is(s, 2); }
    /* empty slot: +3 */
    public static final boolean APR_STATUS_IS_EBADDATE(int s)   { return is(s, 4); }
    public static final boolean APR_STATUS_IS_EINVALSOCK(int s) { return is(s, 5); }
    public static final boolean APR_STATUS_IS_ENOPROC(int s)    { return is(s, 6); }
    public static final boolean APR_STATUS_IS_ENOTIME(int s)    { return is(s, 7); }
    public static final boolean APR_STATUS_IS_ENODIR(int s)     { return is(s, 8); }
    public static final boolean APR_STATUS_IS_ENOLOCK(int s)    { return is(s, 9); }
    public static final boolean APR_STATUS_IS_ENOPOLL(int s)    { return is(s, 10); }
    public static final boolean APR_STATUS_IS_ENOSOCKET(int s)  { return is(s, 11); }
    public static final boolean APR_STATUS_IS_ENOTHREAD(int s)  { return is(s, 12); }
    public static final boolean APR_STATUS_IS_ENOTHDKEY(int s)  { return is(s, 13); }
    public static final boolean APR_STATUS_IS_EGENERAL(int s)   { return is(s, 14); }
    public static final boolean APR_STATUS_IS_ENOSHMAVAIL(int s){ return is(s, 15); }
    public static final boolean APR_STATUS_IS_EBADIP(int s)     { return is(s, 16); }
    public static final boolean APR_STATUS_IS_EBADMASK(int s)   { return is(s, 17); }
    /* empty slot: +18 */
    public static final boolean APR_STATUS_IS_EDSOPEN(int s)    { return is(s, 19); }
    public static final boolean APR_STATUS_IS_EABSOLUTE(int s)  { return is(s, 20); }
    public static final boolean APR_STATUS_IS_ERELATIVE(int s)  { return is(s, 21); }
    public static final boolean APR_STATUS_IS_EINCOMPLETE(int s){ return is(s, 22); }
    public static final boolean APR_STATUS_IS_EABOVEROOT(int s) { return is(s, 23); }
    public static final boolean APR_STATUS_IS_EBADPATH(int s)   { return is(s, 24); }
    public static final boolean APR_STATUS_IS_EPATHWILD(int s)  { return is(s, 25); }
    public static final boolean APR_STATUS_IS_ESYMNOTFOUND(int s)      { return is(s, 26); }
    public static final boolean APR_STATUS_IS_EPROC_UNKNOWN(int s)     { return is(s, 27); }
    public static final boolean APR_STATUS_IS_ENOTENOUGHENTROPY(int s) { return is(s, 28); }

    /*
     * APR_Error
     */
    public static final boolean APR_STATUS_IS_INCHILD(int s)    { return is(s, 51); }
    public static final boolean APR_STATUS_IS_INPARENT(int s)   { return is(s, 52); }
    public static final boolean APR_STATUS_IS_DETACH(int s)     { return is(s, 53); }
    public static final boolean APR_STATUS_IS_NOTDETACH(int s)  { return is(s, 54); }
    public static final boolean APR_STATUS_IS_CHILD_DONE(int s) { return is(s, 55); }
    public static final boolean APR_STATUS_IS_CHILD_NOTDONE(int s)  { return is(s, 56); }
    public static final boolean APR_STATUS_IS_TIMEUP(int s)     { return is(s, 57); }
    public static final boolean APR_STATUS_IS_INCOMPLETE(int s) { return is(s, 58); }
    /* empty slot: +9 */
    /* empty slot: +10 */
    /* empty slot: +11 */
    public static final boolean APR_STATUS_IS_BADCH(int s)      { return is(s, 62); }
    public static final boolean APR_STATUS_IS_BADARG(int s)     { return is(s, 63); }
    public static final boolean APR_STATUS_IS_EOF(int s)        { return is(s, 64); }
    public static final boolean APR_STATUS_IS_NOTFOUND(int s)   { return is(s, 65); }
    /* empty slot: +16 */
    /* empty slot: +17 */
    /* empty slot: +18 */
    public static final boolean APR_STATUS_IS_ANONYMOUS(int s)  { return is(s, 69); }
    public static final boolean APR_STATUS_IS_FILEBASED(int s)  { return is(s, 70); }
    public static final boolean APR_STATUS_IS_KEYBASED(int s)   { return is(s, 71); }
    public static final boolean APR_STATUS_IS_EINIT(int s)      { return is(s, 72); }
    public static final boolean APR_STATUS_IS_ENOTIMPL(int s)   { return is(s, 73); }
    public static final boolean APR_STATUS_IS_EMISMATCH(int s)  { return is(s, 74); }
    public static final boolean APR_STATUS_IS_EBUSY(int s)      { return is(s, 75); }

    /* Socket errors */
    public static final boolean APR_STATUS_IS_EAGAIN(int s)     { return is(s, 90); }
    public static final boolean APR_STATUS_IS_ETIMEDOUT(int s)  { return is(s, 91); }
    public static final boolean APR_STATUS_IS_ECONNABORTED(int s) { return is(s, 92); }
    public static final boolean APR_STATUS_IS_ECONNRESET(int s)   { return is(s, 93); }
    public static final boolean APR_STATUS_IS_EINPROGRESS(int s)  { return is(s, 94); }
    public static final boolean APR_STATUS_IS_EINTR(int s)      { return is(s, 95); }
    public static final boolean APR_STATUS_IS_ENOTSOCK(int s)   { return is(s, 96); }
    public static final boolean APR_STATUS_IS_EINVAL(int s)     { return is(s, 97); }

}
