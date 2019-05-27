package javax.servlet.jsp.tagext;

/**
 * Tag的辅助接口, 需要管理资源的附加钩子的IterationTag 或 BodyTag 标记处理程序.
 *
 * <p>此接口提供两种新方法: doCatch(Throwable)和 doFinally().
 * 典型的调用如下所示:
 *
 * <pre>
 * h = get a Tag();  // 获取标记处理程序，可能来自池
 *
 * h.setPageContext(pc);  // 初始化所需的
 * h.setParent(null);
 * h.setFoo("foo");
 *
 * // 标签调用协议; see Tag.java
 * try {
 *   doStartTag()...
 *   ....
 *   doEndTag()...
 * } catch (Throwable t) {
 *   // 对异常情况作出反应
 *   h.doCatch(t);
 * } finally {
 *   // 恢复数据变量并释放每个调用资源
 *   h.doFinally();
 * }
 *
 * ... other invocations perhaps with some new setters
 * ...
 * h.release();  // release long-term resources
 * </pre>
 */
public interface TryCatchFinally {

    /**
     * 发生Throwable时调用，在标记内或以下任何一种方法中计算主体:
     * Tag.doStartTag(), Tag.doEndTag(), IterationTag.doAfterBody(), BodyTag.doInitBody().
     *
     * <p>这个方法不会在setter方法之一发生Throwable时调用.
     *
     * <p>此方法可能引发异常(同一个还是新的) 这将进一步传播到链. 如果抛出一个异常, doFinally()将被调用.
     *
     * <p>此方法用于响应异常情况.
     *
     * @param t
     * @throws Throwable 如果异常在链中重新抛出.
     */
    void doCatch(Throwable t) throws Throwable;

    /**
     * 在doEndTag()之后调用. 
     * 即使在标记主体中出现异常，也会调用此方法, 或下列任何一个方法:
     * Tag.doStartTag(), Tag.doEndTag(), IterationTag.doAfterBody(), BodyTag.doInitBody().
     *
     * <p>这个方法不会在setter方法之一发生Throwable时调用.
     *
     * <p>此方法不应抛出Exception.
     *
     * <p>此方法旨在维护每次调用数据完整性和资源管理操作.
     */
    void doFinally();
}
