package org.apache.tomcat.jni;

/** PoolCallback Interface
 */
public interface PoolCallback {

    /**
     * 在池被销毁或清除时调用
     * 
     * @return 必须返回 APR_SUCCESS
     */
    public int callback();
}
