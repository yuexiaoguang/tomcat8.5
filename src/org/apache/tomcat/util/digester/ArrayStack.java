package org.apache.tomcat.util.digester;

import java.util.ArrayList;
import java.util.EmptyStackException;

/**
 * <p>来自Commons Collections的<code>ArrayStack</code>类的导入副本，这是Digester唯一的直接依赖.</p>
 *
 * <p><strong>WARNNG</strong> - 此类是公开的, 仅允许从<code>org.apache.commons.digester</code>的子包中使用它.
 * 它不应被视为Commons Digester公共API的一部分. 如果你想自己使用这样的类, 你应该直接使用Commons Collections中的那个.</p>
 *
 * <p>{@link java.util.Stack} API的实现, 它基于<code>ArrayList</code>而不是<code>Vector</code>, 所以它不同步以防止多线程访问.
 * 因此，不需要担心多线程竞争的环境，实现操作会更快.</p>
 *
 * <p>不像<code>Stack</code>, <code>ArrayStack</code> 接受 null 条目.
 * </p>
 *
 * @param <E> 此堆栈中的对象类型
 */
public class ArrayStack<E> extends ArrayList<E> {

    /** 确保序列化兼容性 */
    private static final long serialVersionUID = 2130079159931574599L;

    /**
     * 初始大小由<code>ArrayList</code>控制, 目前为10.
     */
    public ArrayStack() {
        super();
    }

    /**
     * @param initialSize  要使用的初始大小
     * @throws IllegalArgumentException  如果指定的初始大小为负数
     */
    public ArrayStack(int initialSize) {
        super(initialSize);
    }

    /**
     * 如果此堆栈当前为空，则返回<code>true</code>.
     * <p>
     * 方法与<code>java.util.Stack</code>兼容. 此类的新用户应使用<code>isEmpty</code>.
     *
     * @return true 如果堆栈当前为空
     */
    public boolean empty() {
        return isEmpty();
    }

    /**
     * 返回此堆栈的顶部项而不删除它.
     *
     * @return 堆栈顶部的项目
     * @throws EmptyStackException  如果堆栈是空的
     */
    public E peek() throws EmptyStackException {
        int n = size();
        if (n <= 0) {
            throw new EmptyStackException();
        } else {
            return get(n - 1);
        }
    }

    /**
     * 从该堆栈的顶部向下返回第n个项目（零相对）而不删除它.
     *
     * @param n  向下的数量
     * @return 堆栈上的第n项, 零相对
     * @throws EmptyStackException  如果堆栈上没有足够的项目来满足此请求
     */
    public E peek(int n) throws EmptyStackException {
        int m = (size() - n) - 1;
        if (m < 0) {
            throw new EmptyStackException();
        } else {
            return get(m);
        }
    }

    /**
     * 将顶部项弹出此堆栈并将其返回.
     *
     * @return 堆栈顶部的项
     * @throws EmptyStackException  如果堆栈是空的
     */
    public E pop() throws EmptyStackException {
        int n = size();
        if (n <= 0) {
            throw new EmptyStackException();
        } else {
            return remove(n - 1);
        }
    }

    /**
     * 将新项推送到此堆栈的顶部. 推送的项也会被返回. 等效于 <code>add</code>.
     *
     * @param item  要添加的项
     * @return 刚刚推送的项
     */
    public E push(E item) {
        add(item);
        return item;
    }
}
