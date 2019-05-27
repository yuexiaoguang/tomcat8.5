package javax.servlet;

import java.io.IOException;

/**
 * 在使用非阻塞IO时接收写入事件通知.
 */
public interface WriteListener extends java.util.EventListener{

    /**
     * 当它可以不阻塞地写数据时调用. 一旦数据可以写入，容器将第一次调用该方法.
     * 后续调用将只会在{@link ServletOutputStream#isReady()}返回false时发生，此后就可以编写数据了.
     *
     * @throws IOException 如果在处理此事件时发生I/O错误
     */
    public void onWritePossible() throws IOException;

    /**
     * 在编写响应时发生错误时调用.
     *
     * @param throwable 发生的错误
     */
    public void onError(java.lang.Throwable throwable);
}