package org.apache.catalina.valves;

import java.io.IOException;
import java.util.concurrent.Semaphore;

import javax.servlet.ServletException;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;


/**
 * <p>Valve的实现类限制了并发.</p>
 *
 * <p>这个Valve 可能附加到任何 Container, 取决于希望执行的并发控制的粒度.</p>
 */
public class SemaphoreValve extends ValveBase {

    //------------------------------------------------------ Constructor
    public SemaphoreValve() {
        super(true);
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * Semaphore.
     */
    protected Semaphore semaphore = null;


    // ------------------------------------------------------------- Properties


    /**
     * 信号量的并发级别.
     */
    protected int concurrency = 10;
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }


    /**
     * 信号量的公平性.
     */
    protected boolean fairness = false;
    public boolean getFairness() { return fairness; }
    public void setFairness(boolean fairness) { this.fairness = fairness; }


    /**
     * 阻塞直到许可证可用.
     */
    protected boolean block = true;
    public boolean getBlock() { return block; }
    public void setBlock(boolean block) { this.block = block; }


    /**
     * 中断块, 直到许可证可用.
     */
    protected boolean interruptible = false;
    public boolean getInterruptible() { return interruptible; }
    public void setInterruptible(boolean interruptible) { this.interruptible = interruptible; }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        semaphore = new Semaphore(concurrency, fairness);

        setState(LifecycleState.STARTING);
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);

        semaphore = null;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 使用信号量对请求执行并发控制.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    @Override
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        if (controlConcurrency(request, response)) {
            boolean shouldRelease = true;
            try {
                if (block) {
                    if (interruptible) {
                        try {
                            semaphore.acquire();
                        } catch (InterruptedException e) {
                            shouldRelease = false;
                            permitDenied(request, response);
                            return;
                        }
                    } else {
                        semaphore.acquireUninterruptibly();
                    }
                } else {
                    if (!semaphore.tryAcquire()) {
                        shouldRelease = false;
                        permitDenied(request, response);
                        return;
                    }
                }
                getNext().invoke(request, response);
            } finally {
                if (shouldRelease) {
                    semaphore.release();
                }
            }
        } else {
            getNext().invoke(request, response);
        }

    }


    /**
     * 子类友好的添加条件方法.
     * 
     * @param request The Servlet request
     * @param response The Servlet response
     * @return <code>true</code>如果在该请求上发生并发控制
     */
    public boolean controlConcurrency(Request request, Response response) {
        return true;
    }


    /**
     * 子类友好方法在未授予许可证时添加错误处理.
     * 
     * @param request The Servlet request
     * @param response The Servlet response
     * @throws IOException 写入输出错误
     * @throws ServletException Other error
     */
    public void permitDenied(Request request, Response response)
        throws IOException, ServletException {
        // NO-OP by default
    }
}
