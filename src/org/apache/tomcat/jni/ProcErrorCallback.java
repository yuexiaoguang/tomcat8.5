package org.apache.tomcat.jni;

/** ProcErrorCallback Interface
 */
public interface ProcErrorCallback {

    /**
     * 如果APR在运行指定程序之前遇到子级错误，则在子进程中调用.
     * 
     * @param pool 与apr_proc_t关联的池. 如果子错误功能需要用户数据, 将它与此池关联.
     * @param err 描述错误的APR错误代码
     * @param description 失败的进程类型的文本描述
     */
    public void callback(long pool, int err, String description);
}
