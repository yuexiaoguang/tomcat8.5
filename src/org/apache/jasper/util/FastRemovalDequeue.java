package org.apache.jasper.util;

/**
 * 支持条目的常量时间删除的队列.
 * 实现完全是线程安全的.
 *
 * FastRemovalDequeue通常用法是作为一个排序的条目的列表, 对象的排序的位置只在第一个或最后一个.
 *
 * 无论何时排序的位置需要修改, 消费者可以删除这个对象并重新插入它，在常量时间的前面或后面.
 * 因此保持列表有序很简单.
 */
public class FastRemovalDequeue<T> {

    /** 队列的最大值 */
    private final int maxSize;
    /** 队列的第一个元素. */
    protected Entry first;
    /** 队列的最后一个元素. */
    protected Entry last;
    /** 队列的大小 */
    private int size;

    /**
     * 初始化空队列.
     *
     * @param maxSize 队列的最大大小
     */
    public FastRemovalDequeue(int maxSize) {
        if (maxSize <=1 ) {
            maxSize = 2;
        }
        this.maxSize = maxSize;
        first = null;
        last = null;
        size = 0;
    }

    /**
     * 搜索列表的大小.
     * 此方法还需要进行外部同步，以确保正确地发布更改.
     *
     * @return 列表的大小.
     * */
    public synchronized int getSize() {
        return size;
    }

    /**
     * 将对象添加到列表的开始，并返回为该对象创建的条目. 这个 entry稍后可以重用来删除.
     *
     * @param object 要添加到列表开始的对象.
     * @return 删除对象时要使用的 entry.
     * */
    public synchronized Entry push(final T object) {
        Entry entry = new Entry(object);
        if (size >= maxSize) {
            entry.setReplaced(pop());
        }
        if (first == null) {
            first = last = entry;
        } else {
            first.setPrevious(entry);
            entry.setNext(first);
            first = entry;
        }
        size++;

        return entry;
    }

    /**
     * 添加一个对象到列表末尾，并返回创建的 entry. 这个 entry稍后可以重用来删除.
     *
     * @param object 要添加到列表开始的对象.
     * @return 删除对象时要使用的 entry.
     * */
    public synchronized Entry unpop(final T object) {
        Entry entry = new Entry(object);
        if (size >= maxSize) {
            entry.setReplaced(unpush());
        }
        if (first == null) {
            first = last = entry;
        } else {
            last.setNext(entry);
            entry.setPrevious(last);
            last = entry;
        }
        size++;

        return entry;
    }

    /**
     * 删除第一个元素，并返回它.
     **/
    public synchronized T unpush() {
        T content = null;
        if (first != null) {
            Entry element = first;
            first = first.getNext();
            content = element.getContent();
            if (first == null) {
                last =null;
            } else {
                first.setPrevious(null);
            }
            size--;
            element.invalidate();
        }
        return content;
    }

    /**
     * 删除列表的最后一个元素并返回它.
     **/
    public synchronized T pop() {
        T content = null;
        if (last != null) {
            Entry element = last;
            last = last.getPrevious();
            content = element.getContent();
            if (last == null) {
                first = null;
            } else {
                last.setNext(null);
            }
            size--;
            element.invalidate();
        }
        return content;
    }

    /**
     * 删除列表的元素并返回它.
     *
     * @param element The element to remove
     */
    public synchronized void remove(final Entry element) {
        if (element == null || !element.getValid()) {
            return;
        }
        Entry next = element.getNext();
        Entry prev = element.getPrevious();
        if (next != null) {
            next.setPrevious(prev);
        } else {
            last = prev;
        }
        if (prev != null) {
            prev.setNext(next);
        } else {
            first = next;
        }
        size--;
        element.invalidate();
    }

    /**
     * 移动元素到前面.
     *
     * 也可以由remove() 和 push()实现, 但是显式编码可能更快.
     *
     * @param element 之前的要删除的条目.
     * */
    public synchronized void moveFirst(final Entry element) {
        if (element.getValid() &&
            element.getPrevious() != null) {
            Entry prev = element.getPrevious();
            Entry next = element.getNext();
            prev.setNext(next);
            if (next != null) {
                next.setPrevious(prev);
            } else {
                last = prev;
            }
            first.setPrevious(element);
            element.setNext(first);
            element.setPrevious(null);
            first = element;
        }
    }

    /**
     * 移动元素到后面.
     *
     * 也可以由remove() 和 unpop(), 但是显式编码可能更快.
     *
     * @param element 要移动到后面的元素.
     * */
    public synchronized void moveLast(final Entry element) {
        if (element.getValid() &&
            element.getNext() != null) {
            Entry next = element.getNext();
            Entry prev = element.getPrevious();
            next.setPrevious(prev);
            if (prev != null) {
                prev.setNext(next);
            } else {
                first = next;
            }
            last.setNext(element);
            element.setPrevious(last);
            element.setNext(null);
            last = element;
        }
    }

    /**
     * 双链表条目的实现.
     * 对于上述集合的消费者, 这只是简单输入, 输出.
     */
    public class Entry {

        /** 是否仍然有效? */
        private boolean valid = true;
        /** 条目的数据内容. */
        private final T content;
        /** 由该项替换的可选内容 */
        private T replaced = null;
        /** 下个元素的指针. */
        private Entry next = null;
        /** 上个元素的指针. */
        private Entry previous = null;

        private Entry(T object) {
            content = object;
        }

        private final boolean getValid() {
            return valid;
        }

        private final void invalidate() {
            this.valid = false;
            this.previous = null;
            this.next = null;
        }

        public final T getContent() {
            return content;
        }

        public final T getReplaced() {
            return replaced;
        }

        private final void setReplaced(final T replaced) {
            this.replaced = replaced;
        }

        public final void clearReplaced() {
            this.replaced = null;
        }

        private final Entry getNext() {
            return next;
        }

        private final void setNext(final Entry next) {
            this.next = next;
        }

        private final Entry getPrevious() {
            return previous;
        }

        private final void setPrevious(final Entry previous) {
            this.previous = previous;
        }

        @Override
        public String toString() {
            return "Entry-" + content.toString();
        }
    }
}
