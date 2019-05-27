package org.apache.tomcat.util;

import java.lang.reflect.InvocationTargetException;


/**
 * 异常工具类.
 */
public class ExceptionUtils {

    /**
     * 检查提供的Throwable是否需要重新抛出, 并忽略所有其他的Throwable.
     * 
     * @param t the Throwable to check
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
     * 检查提供的Throwable是否是<code>InvocationTargetException</code>的实例，并返回由它包装的throwable，如果有的话.
     *
     * @param t 要检查的 Throwable
     * 
     * @return <code>t</code> or <code>t.getCause()</code>
     */
    public static Throwable unwrapInvocationTargetException(Throwable t) {
        if (t instanceof InvocationTargetException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }


    /**
     * 提供NO-OP方法以实现此类的简单预加载.
     * 由于该类在错误处理中被广泛使用, 谨慎地预加载它, 以避免在错误处理期间加载此类, 从而掩盖真正的问题.
     */
    public static void preload() {
        // NO-OP
    }
}
