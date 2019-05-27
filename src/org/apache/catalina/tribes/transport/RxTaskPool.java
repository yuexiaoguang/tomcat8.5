package org.apache.catalina.tribes.transport;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * 简单的线程池类. 池的大小在构造方法设置, 并且固定大小. 线程通过FIFO空闲队列循环.
 */
public class RxTaskPool {

    final List<AbstractRxTask> idle = new LinkedList<>();
    final List<AbstractRxTask> used = new LinkedList<>();

    final Object mutex = new Object();
    boolean running = true;

    private int maxTasks;
    private int minTasks;

    private final TaskCreator creator;


    public RxTaskPool (int maxTasks, int minTasks, TaskCreator creator) throws Exception {
        // 用工作线程填充池
        this.maxTasks = maxTasks;
        this.minTasks = minTasks;
        this.creator = creator;
    }

    protected void configureTask(AbstractRxTask task) {
        synchronized (task) {
            task.setTaskPool(this);
//            task.setName(task.getClass().getName() + "[" + inc() + "]");
//            task.setDaemon(true);
//            task.setPriority(Thread.MAX_PRIORITY);
//            task.start();
        }
    }

    /**
     * 查找空闲工作线程. 可以返回 null.
     * @return a worker
     */
    public AbstractRxTask getRxTask()
    {
        AbstractRxTask worker = null;
        synchronized (mutex) {
            while ( worker == null && running ) {
                if (idle.size() > 0) {
                    try {
                        worker = idle.remove(0);
                    } catch (java.util.NoSuchElementException x) {
                        // 意味着没有可用的工作者
                        worker = null;
                    }
                } else if ( used.size() < this.maxTasks && creator != null) {
                    worker = creator.createRxTask();
                    configureTask(worker);
                } else {
                    try {
                        mutex.wait();
                    } catch (java.lang.InterruptedException x) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            if ( worker != null ) used.add(worker);
        }
        return (worker);
    }

    public int available() {
        return idle.size();
    }

    /**
     * 由工作线程调用以返回空闲池.
     * @param worker The worker
     */
    public void returnWorker (AbstractRxTask worker) {
        if ( running ) {
            synchronized (mutex) {
                used.remove(worker);
                //if ( idle.size() < minThreads && !idle.contains(worker)) idle.add(worker);
                if ( idle.size() < maxTasks && !idle.contains(worker)) idle.add(worker); //let max be the upper limit
                else {
                    worker.setDoRun(false);
                    synchronized (worker){worker.notify();}
                }
                mutex.notify();
            }
        }else {
            worker.setDoRun(false);
            synchronized (worker){worker.notify();}
        }
    }

    public int getMaxThreads() {
        return maxTasks;
    }

    public int getMinThreads() {
        return minTasks;
    }

    public void stop() {
        running = false;
        synchronized (mutex) {
            Iterator<AbstractRxTask> i = idle.iterator();
            while ( i.hasNext() ) {
                AbstractRxTask worker = i.next();
                returnWorker(worker);
                i.remove();
            }
        }
    }

    public void setMaxTasks(int maxThreads) {
        this.maxTasks = maxThreads;
    }

    public void setMinTasks(int minThreads) {
        this.minTasks = minThreads;
    }

    public TaskCreator getTaskCreator() {
        return this.creator;
    }

    public static interface TaskCreator  {
        public AbstractRxTask createRxTask();
    }
}
