package org.apache.coyote;

/**
 * 用于标记容器所分配的线程来处理来自传入连接的数据. 应用程序创建的线程不是容器线程，也不是容器线程池中提取的线程， 来执行 AsyncContext.start(Runnable).
 */
public class ContainerThreadMarker {

    private static final ThreadLocal<Boolean> marker = new ThreadLocal<>();

    public static boolean isContainerThread() {
        Boolean flag = marker.get();
        if (flag == null) {
            return false;
        } else {
            return flag.booleanValue();
        }
    }

    public static void set() {
        marker.set(Boolean.TRUE);
    }

    public static void clear() {
        marker.set(Boolean.FALSE);
    }
}
