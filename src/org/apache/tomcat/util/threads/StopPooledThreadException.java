package org.apache.tomcat.util.threads;


/**
 * 通过{@link ThreadPoolExecutor}抛出的自定义 {@link RuntimeException}, 表示线程应该被处理.
 */
public class StopPooledThreadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StopPooledThreadException(String msg) {
        super(msg);
    }
}
