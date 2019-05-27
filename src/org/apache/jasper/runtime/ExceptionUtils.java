package org.apache.jasper.runtime;

import java.lang.reflect.InvocationTargetException;


/**
 * 用于处理Throwable和Exception的工具类.
 */
public class ExceptionUtils {

    /**
     * 检查提供的Throwable是否需要重新抛出并吞下所有其他Throwable.
     * @param t 要检查的Throwable
     */
    public static void handleThrowable(Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof StackOverflowError) {
            // Swallow silently - it should be recoverable
            return;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // All other instances of Throwable will be silently swallowed
    }

    /**
     * 检查提供的Throwable是否是 <code>InvocationTargetException</code>实例并返回由它包装的throwable.
     *
     * @param t 要检查的Throwable
     * @return <code>t</code> or <code>t.getCause()</code>
     */
    public static Throwable unwrapInvocationTargetException(Throwable t) {
        if (t instanceof InvocationTargetException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }
}
