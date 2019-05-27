package javax.servlet;

import java.io.IOException;

/**
 * 在使用非阻塞IO时接收读取事件通知.
 */
public interface ReadListener extends java.util.EventListener{

    /**
     * 当数据可读取时调用. 一旦有数据读取，容器将第一次调用该方法.
     * 后续调用只有在{@link ServletInputStream#isReady()}返回false 而且数据随后可读的时候才会发生.
     *
     * @throws IOException 如果在处理事件时发生I/O错误
     */
    public abstract void onDataAvailable() throws IOException;

    /**
     * 当请求主体已被完全读取时调用.
     *
     * @throws IOException 如果在处理事件时发生I/O错误
     */
    public abstract void onAllDataRead() throws IOException;

    /**
     * 在读取请求主体发生错误时调用.
     *
     * @param throwable 发生的异常
     */
    public abstract void onError(java.lang.Throwable throwable);
}
