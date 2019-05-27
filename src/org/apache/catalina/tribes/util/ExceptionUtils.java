package org.apache.catalina.tribes.util;

/**
 * 用于处理Throwables 和 Exceptions.
 */
public class ExceptionUtils {

    /**
     * 检查指定的 Throwable 是需要重新抛出还是忽略.
     * 
     * @param t 要检查的Throwable
     */
    public static void handleThrowable(Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof StackOverflowError) {
            // 忽略 - 它应该是可恢复的
            return;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // 所有其它的 Throwable实例将被忽略
    }
}
