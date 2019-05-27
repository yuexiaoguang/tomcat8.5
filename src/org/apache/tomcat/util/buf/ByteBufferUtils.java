package org.apache.tomcat.util.buf;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.res.StringManager;

public class ByteBufferUtils {

    private static final StringManager sm =
            StringManager.getManager(Constants.Package);
    private static final Log log = LogFactory.getLog(ByteBufferUtils.class);

    private static final Object unsafe;
    private static final Method cleanerMethod;
    private static final Method cleanMethod;
    private static final Method invokeCleanerMethod;

    static {
        ByteBuffer tempBuffer = ByteBuffer.allocateDirect(0);
        Method cleanerMethodLocal = null;
        Method cleanMethodLocal = null;
        Object unsafeLocal = null;
        Method invokeCleanerMethodLocal = null;
        if (JreCompat.isJre9Available()) {
            try {
                Class<?> clazz = Class.forName("sun.misc.Unsafe");
                Field theUnsafe = clazz.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                unsafeLocal = theUnsafe.get(null);
                invokeCleanerMethodLocal = clazz.getMethod("invokeCleaner", ByteBuffer.class);
                invokeCleanerMethodLocal.invoke(unsafeLocal, tempBuffer);
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException
                    | ClassNotFoundException | NoSuchFieldException e) {
                log.warn(sm.getString("byteBufferUtils.cleaner"), e);
                unsafeLocal = null;
                invokeCleanerMethodLocal = null;
            }
        } else {
            try {
                cleanerMethodLocal = tempBuffer.getClass().getMethod("cleaner");
                cleanerMethodLocal.setAccessible(true);
                Object cleanerObject = cleanerMethodLocal.invoke(tempBuffer);
                cleanMethodLocal = cleanerObject.getClass().getMethod("clean");
                cleanMethodLocal.invoke(cleanerObject);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException |
                    IllegalArgumentException | InvocationTargetException e) {
                log.warn(sm.getString("byteBufferUtils.cleaner"), e);
                cleanerMethodLocal = null;
                cleanMethodLocal = null;
            }
        }
        cleanerMethod = cleanerMethodLocal;
        cleanMethod = cleanMethodLocal;
        unsafe = unsafeLocal;
        invokeCleanerMethod = invokeCleanerMethodLocal;
    }

    private ByteBufferUtils() {
        // Hide the default constructor since this is a utility class.
    }


    /**
     * 将缓冲区扩展到给定大小，除非它已经更大了.
     * 假设缓冲区处于“写入”模式，因为在“读取”模式下不需要扩展缓冲区.
     *
     * @param in        要扩展的缓冲区
     * @param newSize   缓冲区应该扩展的大小
     * 
     * @return          将来自输入缓冲区的任何数据复制到扩展缓冲区; 如果不需要扩展，则使用原始缓冲区
     */
    public static ByteBuffer expand(ByteBuffer in, int newSize) {
        if (in.capacity() >= newSize) {
            return in;
        }

        ByteBuffer out;
        boolean direct = false;
        if (in.isDirect()) {
            out = ByteBuffer.allocateDirect(newSize);
            direct = true;
        } else {
            out = ByteBuffer.allocate(newSize);
        }

        // Copy data
        in.flip();
        out.put(in);

        if (direct) {
            cleanDirectBuffer(in);
        }

        return out;
    }

    public static void cleanDirectBuffer(ByteBuffer buf) {
        if (cleanMethod != null) {
            try {
                cleanMethod.invoke(cleanerMethod.invoke(buf));
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | SecurityException e) {
                // Ignore
            }
        } else if (invokeCleanerMethod != null) {
            try {
                invokeCleanerMethod.invoke(unsafe, buf);
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | SecurityException e) {
                // Ignore
                e.printStackTrace();
            }
        }
    }

}
