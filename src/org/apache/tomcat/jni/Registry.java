package org.apache.tomcat.jni;

/** Windows注册表支持
 */
public class Registry {

    /* Registry Enums */
    public static final int HKEY_CLASSES_ROOT       = 1;
    public static final int HKEY_CURRENT_CONFIG     = 2;
    public static final int HKEY_CURRENT_USER       = 3;
    public static final int HKEY_LOCAL_MACHINE      = 4;
    public static final int HKEY_USERS              = 5;

    public static final int KEY_ALL_ACCESS          = 0x0001;
    public static final int KEY_CREATE_LINK         = 0x0002;
    public static final int KEY_CREATE_SUB_KEY      = 0x0004;
    public static final int KEY_ENUMERATE_SUB_KEYS  = 0x0008;
    public static final int KEY_EXECUTE             = 0x0010;
    public static final int KEY_NOTIFY              = 0x0020;
    public static final int KEY_QUERY_VALUE         = 0x0040;
    public static final int KEY_READ                = 0x0080;
    public static final int KEY_SET_VALUE           = 0x0100;
    public static final int KEY_WOW64_64KEY         = 0x0200;
    public static final int KEY_WOW64_32KEY         = 0x0400;
    public static final int KEY_WRITE               = 0x0800;

    public static final int REG_BINARY              = 1;
    public static final int REG_DWORD               = 2;
    public static final int REG_EXPAND_SZ           = 3;
    public static final int REG_MULTI_SZ            = 4;
    public static final int REG_QWORD               = 5;
    public static final int REG_SZ                  = 6;

     /**
     * 创建或打开注册表项.
     * 
     * @param name 要打开的注册表子项
     * @param root 根项, HKEY_* 其中之一
     * @param sam 访问掩码，指定注册表项的访问权限.
     * @param pool 用于本地内存分配的池
     * 
     * @return 打开的注册表项
     * @throws Error 发生错误
     */
    public static native long create(int root, String name, int sam, long pool)
        throws Error;

     /**
     * 打开指定的注册表项.
     * 
     * @param name 要打开的注册表子项
     * @param root 根项, HKEY_* 其中之一
     * @param sam 访问掩码，指定注册表项的访问权限.
     * @param pool 用于本地内存分配的池
     * 
     * @return 打开的注册表项
     * @throws Error 发生错误
     */
    public static native long open(int root, String name, int sam, long pool)
        throws Error;

    /**
     * 关闭指定的注册表项.
     * 
     * @param key 要关闭的注册表项描述符.
     * 
     * @return 操作状态
     */
    public static native int close(long key);

    /**
     * 获取注册表项类型.
     * 
     * @param key 要使用的注册表项描述符.
     * @param name 要查询的值的名称
     * 
     * @return 值类型或负数错误值
     */
    public static native int getType(long key, String name);

    /**
     * 获取REG_DWORD的注册表值
     * 
     * @param key 要使用的注册表项描述符.
     * @param name 要查询的值的名称
     * 
     * @return 注册表项值
     * @throws Error 发生错误
     */
    public static native int getValueI(long key, String name)
        throws Error;

    /**
     * 获取REG_QWORD或REG_DWORD的注册表值
     * 
     * @param key 要使用的注册表项描述符.
     * @param name 要查询的值的名称
     * 
     * @return 注册表项值
     * @throws Error 发生错误
     */
    public static native long getValueJ(long key, String name)
        throws Error;

    /**
     * 获取注册表项长度.
     * 
     * @param key 要使用的注册表项描述符.
     * @param name 要查询的值的名称
     * 
     * @return 值大小或负数错误值
     */
    public static native int getSize(long key, String name);

    /**
     * 获取REG_SZ或REG_EXPAND_SZ的注册表值
     * 
     * @param key 要使用的注册表项描述符.
     * @param name 要查询的值的名称
     * 
     * @return 注册表项值
     * @throws Error 发生错误
     */
    public static native String getValueS(long key, String name)
        throws Error;

    /**
     * 获取REG_MULTI_SZ的注册表值
     * 
     * @param key 要使用的注册表项描述符.
     * @param name 要查询的值的名称
     * 
     * @return 注册表项值
     * @throws Error 发生错误
     */
    public static native String[] getValueA(long key, String name)
        throws Error;

    /**
     * 获取REG_BINARY的注册表值
     * 
     * @param key 要使用的注册表项描述符.
     * @param name 要查询的值的名称
     * 
     * @return 注册表项值
     * @throws Error 发生错误
     */
    public static native byte[] getValueB(long key, String name)
        throws Error;


    /**
     * 设置REG_DWORD的注册表值
     * 
     * @param key 要使用的注册表项描述符.
     * @param name 要设置的值的名称
     * @param val 要设置的值
     * 
     * @return 如果成功, 返回值是 0
     */
    public static native int setValueI(long key, String name, int val);

    /**
     * 设置REG_QWORD的注册表值
     * 
     * @param key 要使用的注册表项描述符.
     * @param name 要设置的值的名称
     * @param val 要设置的值
     * 
     * @return 如果成功, 返回值是 0
     */
    public static native int setValueJ(long key, String name, long val);

    /**
     * 设置REG_SZ的注册表值
     * 
     * @param key 要使用的注册表项描述符.
     * @param name 要设置的值的名称
     * @param val 要设置的值
     * 
     * @return 如果成功, 返回值是 0
     */
    public static native int setValueS(long key, String name, String val);

    /**
     * 设置REG_EXPAND_SZ的注册表值
     * 
     * @param key 要使用的注册表项描述符.
     * @param name 要设置的值的名称
     * @param val 要设置的值
     * 
     * @return 如果成功, 返回值是 0
     */
    public static native int setValueE(long key, String name, String val);

     /**
     * 设置REG_MULTI_SZ的注册表值
     * 
     * @param key 要使用的注册表项描述符.
     * @param name 要设置的值的名称
     * @param val 要设置的值
     * 
     * @return 如果成功, 返回值是 0
     */
    public static native int setValueA(long key, String name, String[] val);

     /**
     * 设置REG_BINARY的注册表值
     * 
     * @param key 要使用的注册表项描述符.
     * @param name 要设置的值的名称
     * @param val 要设置的值
     * 
     * @return 如果成功, 返回值是 0
     */
    public static native int setValueB(long key, String name, byte[] val);

    /**
     * 枚举注册表子项
     * 
     * @param key 要使用的注册表项描述符.
     * 
     * @return 所有子项名称的数组
     * @throws Error 发生错误
     */
    public static native String[] enumKeys(long key)
        throws Error;

    /**
     * 枚举注册表值
     * 
     * @param key 要使用的注册表项描述符.
     * 
     * @return 所有值名称的数组
     * @throws Error 发生错误
     */
    public static native String[] enumValues(long key)
        throws Error;

     /**
     * 删除注册表值
     * 
     * @param key 要使用的注册表项描述符.
     * @param name 要删除的值的名称
     * 
     * @return 如果成功, 返回值是 0
     */
    public static native int deleteValue(long key, String name);

     /**
     * 删除注册表子项
     * 
     * @param root 根项, HKEY_* 其中之一
     * @param name 要删除的子项
     * @param onlyIfEmpty 如果为true，而且包含任何子项或值，则不会删除该项
     *                    
     * @return 如果成功, 返回值是 0
     */
    public static native int deleteKey(int root, String name,
                                       boolean onlyIfEmpty);


}
