package org.apache.tomcat.jni;

/** User
 */
public class User {

    /**
     * 获取调用进程的userid (和groupid).
     * 仅当定义了APR_HAS_USER时，此功能才可用.
     * 
     * @param p 从中分配工作空间的池
     * @return 返回用户 id
     * @throws Error 如果发生错误
     */
     public static native long uidCurrent(long p)
        throws Error;

    /**
     * 获取调用进程的groupid.
     * 仅当定义了APR_HAS_USER时，此功能才可用.
     * 
     * @param p 从中分配工作空间的池
     * @return 返回组 id
     * @throws Error 如果发生错误
     */
     public static native long gidCurrent(long p)
        throws Error;


    /**
     * 获取指定用户名的用户id.
     * 仅当定义了APR_HAS_USER时，此功能才可用.
     * 
     * @param username 要查找的用户名
     * @param p 从中分配工作空间的池
     * 
     * @return 返回用户 id
     * @throws Error 如果发生错误
     */
     public static native long uid(String username, long p)
        throws Error;

    /**
     * 获取指定用户名的groupid.
     * 仅当定义了APR_HAS_USER时，此功能才可用.
     * 
     * @param username 要查找的用户名
     * @param p 从中分配工作空间的池
     * 
     * @return 返回用户的组 id
     * @throws Error 如果发生错误
     */
     public static native long usergid(String username, long p)
        throws Error;

    /**
     * 获取指定组名的groupid.
     * 仅当定义了APR_HAS_USER时，此功能才可用.
     * 
     * @param groupname 要查找的组名称
     * @param p 从中分配工作空间的池
     * 
     * @return 返回用户的组 id
     * @throws Error 如果发生错误
     */
     public static native long gid(String groupname, long p)
        throws Error;

    /**
     * 获取指定用户 id 的用户名.
     * 仅当定义了APR_HAS_USER时，此功能才可用.
     * 
     * @param userid 用户 id
     * @param p 从中分配字符串的池
     * 
     * @return 包含用户名的新字符串
     * @throws Error 如果发生错误
     */
     public static native String username(long userid, long p)
        throws Error;

    /**
     * 获取指定groupid的组名称.
     * 仅当定义了APR_HAS_USER时，此功能才可用.
     * 
     * @param groupid
     * @param p 从中分配字符串的池
     * 
     * @return 包含组名的新字符串
     * @throws Error 如果发生错误
     */
     public static native String groupname(long groupid, long p)
        throws Error;

    /**
     * 比较两个用户标识符是否相等.
     * 仅当定义了APR_HAS_USER时，此功能才可用.
     * 
     * @param left
     * @param right
     * 
     * @return APR_SUCCESS 如果apr_uid_t结构标识的同一个用户,
     * APR_EMISMATCH 如果不是, APR_BADARG 如果一个 apr_uid_t 无效.
     */
     public static native int uidcompare(long left, long right);

    /**
     * 比较两个组标识符是否相等.
     * 仅当定义了APR_HAS_USER时，此功能才可用.
     * 
     * @param left
     * @param right
     * 
     * @return APR_SUCCESS 如果apr_gid_t结构标识同一个组,
     * APR_EMISMATCH 如果不是, APR_BADARG 如果一个 apr_uid_t 无效.
     */
     public static native int gidcompare(long left, long right);

    /**
     * 获取指定用户的主目录.
     * 仅当定义了APR_HAS_USER时，此功能才可用.
     * 
     * @param username 指定的用户
     * @param p 从中分配字符串的池
     * 
     * @return 包含目录名称的新字符串
     * @throws Error 如果发生错误
     */
     public static native String homepath(String username, long p)
        throws Error;

}
