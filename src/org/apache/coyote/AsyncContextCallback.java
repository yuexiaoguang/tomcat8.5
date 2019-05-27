package org.apache.coyote;

/**
 * 为Coyote连接器提供一种机制, 向 {@link javax.servlet.AsyncContext}实现类发出信号, 例如触发事件监听器.
 * 它以这种方式实现, 因此 org.apache.coyote 包不依赖 org.apache.catalina 包.
 */
public interface AsyncContextCallback {
    public void fireOnComplete();
}
