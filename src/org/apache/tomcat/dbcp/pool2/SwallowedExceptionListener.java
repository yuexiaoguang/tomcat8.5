package org.apache.tomcat.dbcp.pool2;

/**
 * 不可避免地忽略异常的池可以配置此监听器的实例, 以便用户可以收到有关何时发生这种情况的通知.
 * 监听器在调用时不应抛出异常, 但调用监听器的池应该保护自己免受异常的影响.
 */
public interface SwallowedExceptionListener {

    /**
     * 每次实现不可避免地忽略异常时都会调用此方法.
     *
     * @param e 被忽略的异常
     */
    void onSwallowException(Exception e);
}
