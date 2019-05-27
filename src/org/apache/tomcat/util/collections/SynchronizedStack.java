package org.apache.tomcat.util.collections;

/**
 * 当需要创建一个可重用的对象池而不需要缩小池时，这是一个（大多数）无GC的{@link java.util.concurrent.ConcurrentLinkedQueue}的替代方案.
 * 目的是尽可能快地提供最少的所需功能，同时尽可能减少垃圾.
 *
 * @param <T> 此堆栈管理的对象类型
 */
public class SynchronizedStack<T> {

    public static final int DEFAULT_SIZE = 128;
    private static final int DEFAULT_LIMIT = -1;

    private int size;
    private final int limit;

    /*
     * 指向堆栈中的下一个可用对象
     */
    private int index = -1;

    private Object[] stack;


    public SynchronizedStack() {
        this(DEFAULT_SIZE, DEFAULT_LIMIT);
    }

    public SynchronizedStack(int size, int limit) {
        if (limit > -1 && size > limit) {
            this.size = limit;
        } else {
            this.size = size;
        }
        this.limit = limit;
        stack = new Object[size];
    }


    public synchronized boolean push(T obj) {
        index++;
        if (index == size) {
            if (limit == -1 || size < limit) {
                expand();
            } else {
                index--;
                return false;
            }
        }
        stack[index] = obj;
        return true;
    }

    @SuppressWarnings("unchecked")
    public synchronized T pop() {
        if (index == -1) {
            return null;
        }
        T result = (T) stack[index];
        stack[index--] = null;
        return result;
    }

    public synchronized void clear() {
        if (index > -1) {
            for (int i = 0; i < index + 1; i++) {
                stack[i] = null;
            }
        }
        index = -1;
    }

    private void expand() {
        int newSize = size * 2;
        if (limit != -1 && newSize > limit) {
            newSize = limit;
        }
        Object[] newStack = new Object[newSize];
        System.arraycopy(stack, 0, newStack, 0, size);
        // 这是通过丢弃旧数组创建垃圾的唯一的点. 请注意，只有数组而不是内容才会变成垃圾.
        stack = newStack;
        size = newSize;
    }
}
