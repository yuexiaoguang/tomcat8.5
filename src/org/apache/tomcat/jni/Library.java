package org.apache.tomcat.jni;

import java.io.File;

/** Library
 */
public final class Library {

    /* 默认库名称 */
    private static final String [] NAMES = {"tcnative-1", "libtcnative-1"};
    /*
     * 唯一库单例实例的句柄.
     */
    private static Library _instance = null;

    private Library() throws Exception {
        boolean loaded = false;
        String path = System.getProperty("java.library.path");
        String [] paths = path.split(File.pathSeparator);
        StringBuilder err = new StringBuilder();
        for (int i = 0; i < NAMES.length; i++) {
            try {
                System.loadLibrary(NAMES[i]);
                loaded = true;
            } catch (ThreadDeath t) {
                throw t;
            } catch (VirtualMachineError t) {
                // 不要使用Java 7多重异常捕获，这样就可以保持在 Tomcat 6/7/8/9 中JNI代码相同
                throw t;
            } catch (Throwable t) {
                String name = System.mapLibraryName(NAMES[i]);
                for (int j = 0; j < paths.length; j++) {
                    java.io.File fd = new java.io.File(paths[j] , name);
                    if (fd.exists()) {
                        // 文件存在但加载失败
                        throw t;
                    }
                }
                if (i > 0) {
                    err.append(", ");
                }
                err.append(t.getMessage());
            }
            if (loaded) {
                break;
            }
        }
        if (!loaded) {
            StringBuilder names = new StringBuilder();
            for (String name : NAMES) {
                names.append(name);
                names.append(", ");
            }
            throw new LibraryNotFoundError(names.substring(0, names.length() -2), err.toString());
        }
    }

    private Library(String libraryName)
    {
        System.loadLibrary(libraryName);
    }

    /* 创建全局 TCN 的 APR 池
     * 这必须是对TCN库的第一次调用.
     */
    private static native boolean initialize();
    /* 销毁全局 TCN 的 APR 池
     * 这必须是对TCN库的最后一次调用.
     */
    public static native void terminate();
    /* 用于加载APR功能的内部功能 */
    private static native boolean has(int what);
    /* 用于加载APR功能的内部功能 */
    private static native int version(int what);
    /* 用于加载APR大小的内部功能 */
    private static native int size(int what);

    /* TCN_MAJOR_VERSION */
    public static int TCN_MAJOR_VERSION  = 0;
    /* TCN_MINOR_VERSION */
    public static int TCN_MINOR_VERSION  = 0;
    /* TCN_PATCH_VERSION */
    public static int TCN_PATCH_VERSION  = 0;
    /* TCN_IS_DEV_VERSION */
    public static int TCN_IS_DEV_VERSION = 0;
    /* APR_MAJOR_VERSION */
    public static int APR_MAJOR_VERSION  = 0;
    /* APR_MINOR_VERSION */
    public static int APR_MINOR_VERSION  = 0;
    /* APR_PATCH_VERSION */
    public static int APR_PATCH_VERSION  = 0;
    /* APR_IS_DEV_VERSION */
    public static int APR_IS_DEV_VERSION = 0;

    /* TCN_VERSION_STRING */
    public static native String versionString();
    /* APR_VERSION_STRING */
    public static native String aprVersionString();

    /*  APR功能宏 */
    public static boolean APR_HAVE_IPV6           = false;
    public static boolean APR_HAS_SHARED_MEMORY   = false;
    public static boolean APR_HAS_THREADS         = false;
    public static boolean APR_HAS_SENDFILE        = false;
    public static boolean APR_HAS_MMAP            = false;
    public static boolean APR_HAS_FORK            = false;
    public static boolean APR_HAS_RANDOM          = false;
    public static boolean APR_HAS_OTHER_CHILD     = false;
    public static boolean APR_HAS_DSO             = false;
    public static boolean APR_HAS_SO_ACCEPTFILTER = false;
    public static boolean APR_HAS_UNICODE_FS      = false;
    public static boolean APR_HAS_PROC_INVOKED    = false;
    public static boolean APR_HAS_USER            = false;
    public static boolean APR_HAS_LARGE_FILES     = false;
    public static boolean APR_HAS_XTHREAD_FILES   = false;
    public static boolean APR_HAS_OS_UUID         = false;
    /* 是大端吗? */
    public static boolean APR_IS_BIGENDIAN        = false;
    /* 
     * APR在可以在文件/管道上轮询的系统上将APR_FILES_AS_SOCKETS设置为1.
     */
    public static boolean APR_FILES_AS_SOCKETS    = false;
    /* 
     * 此宏指示EBCDIC是否为本地字符集.
     */
    public static boolean APR_CHARSET_EBCDIC      = false;
    /* 
     * TCP_NODELAY套接字选项是否从监听套接字继承?
     */
    public static boolean APR_TCP_NODELAY_INHERITED = false;
    /* 
     * O_NONBLOCK标志是否从监听套接字继承?
     */
    public static boolean APR_O_NONBLOCK_INHERITED  = false;


    public static int APR_SIZEOF_VOIDP;
    public static int APR_PATH_MAX;
    public static int APRMAXHOSTLEN;
    public static int APR_MAX_IOVEC_SIZE;
    public static int APR_MAX_SECS_TO_LINGER;
    public static int APR_MMAP_THRESHOLD;
    public static int APR_MMAP_LIMIT;

    /* 返回全局TCN的APR池 */
    public static native long globalPool();

    /**
     * 设置任何APR内部数据结构. 这必须是任何APR库调用的第一个函数.
     * 
     * @param libraryName 要加载的库的名称
     *
     * @return {@code true} 如果本地代码已成功初始化
     *         否则 {@code false}
     *
     * @throws Exception 如果初始化期间出现问题
     */
    public static synchronized boolean initialize(String libraryName) throws Exception {
        if (_instance == null) {
            if (libraryName == null)
                _instance = new Library();
            else
                _instance = new Library(libraryName);
            TCN_MAJOR_VERSION  = version(0x01);
            TCN_MINOR_VERSION  = version(0x02);
            TCN_PATCH_VERSION  = version(0x03);
            TCN_IS_DEV_VERSION = version(0x04);
            APR_MAJOR_VERSION  = version(0x11);
            APR_MINOR_VERSION  = version(0x12);
            APR_PATCH_VERSION  = version(0x13);
            APR_IS_DEV_VERSION = version(0x14);

            APR_SIZEOF_VOIDP        = size(1);
            APR_PATH_MAX            = size(2);
            APRMAXHOSTLEN           = size(3);
            APR_MAX_IOVEC_SIZE      = size(4);
            APR_MAX_SECS_TO_LINGER  = size(5);
            APR_MMAP_THRESHOLD      = size(6);
            APR_MMAP_LIMIT          = size(7);

            APR_HAVE_IPV6           = has(0);
            APR_HAS_SHARED_MEMORY   = has(1);
            APR_HAS_THREADS         = has(2);
            APR_HAS_SENDFILE        = has(3);
            APR_HAS_MMAP            = has(4);
            APR_HAS_FORK            = has(5);
            APR_HAS_RANDOM          = has(6);
            APR_HAS_OTHER_CHILD     = has(7);
            APR_HAS_DSO             = has(8);
            APR_HAS_SO_ACCEPTFILTER = has(9);
            APR_HAS_UNICODE_FS      = has(10);
            APR_HAS_PROC_INVOKED    = has(11);
            APR_HAS_USER            = has(12);
            APR_HAS_LARGE_FILES     = has(13);
            APR_HAS_XTHREAD_FILES   = has(14);
            APR_HAS_OS_UUID         = has(15);
            APR_IS_BIGENDIAN        = has(16);
            APR_FILES_AS_SOCKETS    = has(17);
            APR_CHARSET_EBCDIC      = has(18);
            APR_TCP_NODELAY_INHERITED = has(19);
            APR_O_NONBLOCK_INHERITED  = has(20);
            if (APR_MAJOR_VERSION < 1) {
                throw new UnsatisfiedLinkError("Unsupported APR Version (" +
                                               aprVersionString() + ")");
            }
            if (!APR_HAS_THREADS) {
                throw new UnsatisfiedLinkError("Missing APR_HAS_THREADS");
            }
        }
        return initialize();
    }
}
