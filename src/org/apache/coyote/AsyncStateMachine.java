package org.apache.coyote;

import java.security.AccessController;
import java.security.PrivilegedAction;

import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.PrivilegedGetTccl;
import org.apache.tomcat.util.security.PrivilegedSetTccl;

/**
 * 管理异步请求的状态转换.
 *
 * <pre>
 * 所使用的内部状态是:
 * DISPATCHED       - 标准请求. 非异步模式.
 * STARTING         - ServletRequest.startAsync() 已经被调用, 但发出该调用的请求尚未完成处理.
 * STARTED          - ServletRequest.startAsync() 已经被调用, 但发出该调用的请求已经完成处理.
 * READ_WRITE_OP    - 执行异步读写.
 * MUST_COMPLETE    - ServletRequest.startAsync() 随后的 complete() 已经被调用, 在单个 Servlet.service() 方法中.
 *                    一旦请求完成, 将处理complete().
 * COMPLETE_PENDING - ServletRequest.startAsync() 已经被调用, 在发出该调用的请求已经完成处理之前,
 *                    非容器线程将调用complete(). 请求完成时将处理 complete(). 这个和 MUST_COMPLETE 不一样, 因为在错误处理过程中避免竞争条件所需的差异.
 * COMPLETING       - 一旦请求处于STARTED 状态, 将调用 complete() . 容器线程可能触发, 也可能不触发 - 取决于是否使用 start(Runnable).
 * TIMING_OUT       - 异步请求已超时并正在等待调用 complete(). 如果不是这样, 将进入错误状态.
 * MUST_DISPATCH    - ServletRequest.startAsync() 随后的 dispatch() 已经被调用, 在单个 Servlet.service() 方法中.
 *                    一旦请求完成, 将处理dispatch().
 * DISPATCH_PENDING - ServletRequest.startAsync() 已经被调用, 在发出该调用的请求已经完成处理之前,
 *                    非容器线程将调用dispatch(). 请求完成时将处理 dispatch().  这个和 MUST_DISPATCH 不一样, 由于在错误处理过程中需要避免竞争条件的差异.
 * DISPATCHING      - 分配完成处理.
 * MUST_ERROR       - ServletRequest.startAsync() 已经被调用, 随后在一个非容器线程上发生 I/O 错误.
 *                    此状态的主要目的是防止在一个非容器线程上的额外的异步动作 (complete(), dispatch() etc.).
 *                    容器将执行必要的错误处理, 包括确保调用 AsyncLister.onError() 方法.
 * ERROR            - 一些事情出错了.
 *
 *                           |-----«-------------------------------«------------------------------|
 *                           |                                                                    |
 *                           |      error()                                                       |
 * |-----------------»---|   |  |--«--------MUST_ERROR---------------«------------------------|   |
 * |                    \|/ \|/\|/                                                            |   |
 * |   |----------«-----E R R O R--«-----------------------«-------------------------------|  |   |
 * |   |      complete() /|\/|\\ \-«--------------------------------«-------|              |  |   |
 * |   |                  |  |  \                                           |              |  |   |
 * |   |    |-----»-------|  |   \-----------»----------|                   |              |  |   |
 * |   |    |                |                          |dispatch()         |              |  ^   |
 * |   |    |                |                         \|/                  ^              |  |   |
 * |   |    |                |          |--|timeout()   |                   |              |  |   |
 * |   |    |     post()     |          | \|/           |     post()        |              |  |   |
 * |   |    |    |---------- | --»DISPATCHED«---------- | --------------COMPLETING«-----|  |  |   |
 * |   |    |    |           |   /|\/|\ |               |                | /|\ /|\      |  |  |   |
 * |   |    |    |    |---»- | ---|  |  |startAsync()   |       timeout()|--|   |       |  |  |   |
 * |   |    ^    ^    |      |       |  |               |                       |       |  ^  |   |
 * |   |    |    |    |   |-- \ -----|  |   complete()  |                       |post() |  |  |   |
 * |   |    |    |    |   |    \        |     /--»----- | ---COMPLETE_PENDING-»-|       ^  |  |   |
 * |   |    |    |    |   |     \       |    /          |                               |  |  |   |
 * |   |    |    |    |   ^      \      |   /           |                    complete() |  |  |   |
 * |  \|/   |    |    |   |       \    \|/ /   post()   |                     /---»-----|  |  ^   |
 * | MUST_COMPLETE-«- | - | --«----STARTING--»--------- | ------------|      /             |  |   |
 * |  /|\    /|\      |   |  complete()  | \            |             |     /   error()    |  |   ^
 * |   |      |       |   |              |  \           |             |    //---»----------|  |   |
 * |   |      |       ^   |    dispatch()|   \          |    post()   |   //                  |   |
 * |   |      |       |   |              |    \         |    |-----|  |  //   nct-io-error    |   |
 * |   |      |       |   |              |     \        |    |     |  | ///---»---------------|   |
 * |   |      |       |   |             \|/     \       |    |    \|/\| |||                       |
 * |   |      |       |   |--«--MUST_DISPATCH-----«-----|    |--«--STARTED«---------«---------|   |
 * |   |      |       | dispatched() /|\   |      \               / |   |        post()       |   |
 * |   |      |       |               |    |       \             /  |   |                     |   |
 * |   |      |       |               |    |        \           /   |   |                     |   |
 * |   |      |       |               |    |post()  |           |   |   |                     ^   |
 * ^   |      ^       |               |    |       \|/          |   |   |asyncOperation()     |   |
 * |   |      |       ^               |    |  DISPATCH_PENDING  |   |   |                     |   |
 * |   |      |       |               |    |  |post()           |   |   |                     |   |
 * |   |      |       |               |    |  |      |----------|   |   |»-READ_WRITE_OP--»---|   |
 * |   |      |       |               |    |  |      |  dispatch()  |            |  |  |          |
 * |   |      |       |               |    |  |      |              |            |  |  |          |
 * |   |      |       |post()         |    |  |      |     timeout()|            |  |  |   error()|
 * |   |      |       |dispatched()   |   \|/\|/    \|/             |  dispatch()|  |  |-»--------|
 * |   |      |       |---«---------- | ---DISPATCHING«-----«------ | ------«----|  |
 * |   |      |                       |     |    ^                  |               |
 * |   |      |                       |     |----|                  |               |
 * |   |      |                       |    timeout()                |               |
 * |   |      |                       |                             |               |
 * |   |      |                       |       dispatch()           \|/              |
 * |   |      |                       |-----------«-----------TIMING_OUT            |
 * |   |      |                                                 |   |               |
 * |   |      |-------«----------------------------------«------|   |               |
 * |   |                          complete()                        |               |
 * |   |                                                            |               |
 * |«- | ----«-------------------«-------------------------------«--|               |
 *     |                           error()                                          |
 *     |                                                  complete()                |
 *     |----------------------------------------------------------------------------|
 * </pre>
 */
public class AsyncStateMachine {

    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(AsyncStateMachine.class);

    private static enum AsyncState {
        DISPATCHED      (false, false, false, false),
        STARTING        (true,  true,  false, false),
        STARTED         (true,  true,  false, false),
        MUST_COMPLETE   (true,  true,  true,  false),
        COMPLETE_PENDING(true,  true,  false, false),
        COMPLETING      (true,  false, true,  false),
        TIMING_OUT      (true,  true,  false, false),
        MUST_DISPATCH   (true,  true,  false, true),
        DISPATCH_PENDING(true,  true,  false, false),
        DISPATCHING     (true,  false, false, true),
        READ_WRITE_OP   (true,  true,  false, false),
        MUST_ERROR      (true,  true,  false, false),
        ERROR           (true,  true,  false, false);

        private final boolean isAsync;
        private final boolean isStarted;
        private final boolean isCompleting;
        private final boolean isDispatching;

        private AsyncState(boolean isAsync, boolean isStarted, boolean isCompleting,
                boolean isDispatching) {
            this.isAsync = isAsync;
            this.isStarted = isStarted;
            this.isCompleting = isCompleting;
            this.isDispatching = isDispatching;
        }

        public boolean isAsync() {
            return isAsync;
        }

        public boolean isStarted() {
            return isStarted;
        }

        public boolean isDispatching() {
            return isDispatching;
        }

        public boolean isCompleting() {
            return isCompleting;
        }
    }


    private volatile AsyncState state = AsyncState.DISPATCHED;
    private volatile long lastAsyncStart = 0;
    // 需要这个触发监听器
    private AsyncContextCallback asyncCtxt = null;
    private final AbstractProcessor processor;


    public AsyncStateMachine(AbstractProcessor processor) {
        this.processor = processor;
    }


    public boolean isAsync() {
        return state.isAsync();
    }

    public boolean isAsyncDispatching() {
        return state.isDispatching();
    }

    public boolean isAsyncStarted() {
        return state.isStarted();
    }

    public boolean isAsyncTimingOut() {
        return state == AsyncState.TIMING_OUT;
    }

    public boolean isAsyncError() {
        return state == AsyncState.ERROR;
    }

    public boolean isCompleting() {
        return state.isCompleting();
    }

    /**
     * 获取此连接最后转换为异步处理的时间.
     *
     * @return 此连接最后转换为异步的时间 ({@link System#currentTimeMillis()})
     */
    public long getLastAsyncStart() {
        return lastAsyncStart;
    }

    public synchronized void asyncStart(AsyncContextCallback asyncCtxt) {
        if (state == AsyncState.DISPATCHED) {
            state = AsyncState.STARTING;
            this.asyncCtxt = asyncCtxt;
            lastAsyncStart = System.currentTimeMillis();
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncStart()", state));
        }
    }

    public synchronized void asyncOperation() {
        if (state==AsyncState.STARTED) {
            state = AsyncState.READ_WRITE_OP;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncOperation()", state));
        }
    }

    /*
     * 异步处理. 是否进入长轮询取决于当前状态. 例如, 每个 SRV.2.3.3.3 现在可以处理调用 complete() 或 dispatch().
     */
    public synchronized SocketState asyncPostProcess() {
        if (state == AsyncState.COMPLETE_PENDING) {
            doComplete();
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.DISPATCH_PENDING) {
            doDispatch();
            return SocketState.ASYNC_END;
        } else  if (state == AsyncState.STARTING || state == AsyncState.READ_WRITE_OP) {
            state = AsyncState.STARTED;
            return SocketState.LONG;
        } else if (state == AsyncState.MUST_COMPLETE || state == AsyncState.COMPLETING) {
            asyncCtxt.fireOnComplete();
            state = AsyncState.DISPATCHED;
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.MUST_DISPATCH) {
            state = AsyncState.DISPATCHING;
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.DISPATCHING) {
            state = AsyncState.DISPATCHED;
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.STARTED) {
            // 如果异步监听器对异步servlet进行分配，则可能发生这种情况，在 onTimeout 期间
            return SocketState.LONG;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncPostProcess()", state));
        }
    }


    public synchronized boolean asyncComplete() {
        if (!ContainerThreadMarker.isContainerThread() && state == AsyncState.STARTING) {
            state = AsyncState.COMPLETE_PENDING;
            return false;
        } else {
            return doComplete();
        }
    }


    private synchronized boolean doComplete() {
        clearNonBlockingListeners();
        boolean doComplete = false;
        if (state == AsyncState.STARTING || state == AsyncState.TIMING_OUT ||
                state == AsyncState.ERROR || state == AsyncState.READ_WRITE_OP) {
            state = AsyncState.MUST_COMPLETE;
        } else if (state == AsyncState.STARTED || state == AsyncState.COMPLETE_PENDING) {
            state = AsyncState.COMPLETING;
            doComplete = true;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncComplete()", state));
        }
        return doComplete;
    }


    public synchronized boolean asyncTimeout() {
        if (state == AsyncState.STARTED) {
            state = AsyncState.TIMING_OUT;
            return true;
        } else if (state == AsyncState.COMPLETING ||
                state == AsyncState.DISPATCHING ||
                state == AsyncState.DISPATCHED) {
            // NOOP - App 调用 complete() 或 dispatch() 在触发超时和执行之间达到这一点
            return false;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncTimeout()", state));
        }
    }


    public synchronized boolean asyncDispatch() {
        if (!ContainerThreadMarker.isContainerThread() && state == AsyncState.STARTING) {
            state = AsyncState.DISPATCH_PENDING;
            return false;
        } else {
            return doDispatch();
        }
    }


    private synchronized boolean doDispatch() {
        boolean doDispatch = false;
        if (state == AsyncState.STARTING ||
                state == AsyncState.TIMING_OUT ||
                state == AsyncState.ERROR) {
            // 在这三种情况下，处理是在容器线程上进行的，因此，不需要将处理转移到新的容器线程
            state = AsyncState.MUST_DISPATCH;
        } else if (state == AsyncState.STARTED || state == AsyncState.DISPATCH_PENDING) {
            state = AsyncState.DISPATCHING;
            // 总是需要一个调度.
            // 如果在非容器线程上, 需要返回到容器线程来完成处理.
            // 如果在容器线程上，当前请求/响应不是与AsyncContext相关联的请求/响应，所以需要一个新的容器线程来处理不同的请求/响应.
            doDispatch = true;
        } else if (state == AsyncState.READ_WRITE_OP) {
            state = AsyncState.DISPATCHING;
            // 如果在容器线程上，则将socket 添加到查询器中， 当线程存在于 AbstractConnectionHandler.process() 方法, 所以不要在这里做一个调度，第二次把它添加到轮询器中.
            if (!ContainerThreadMarker.isContainerThread()) {
                doDispatch = true;
            }
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncDispatch()", state));
        }
        return doDispatch;
    }


    public synchronized void asyncDispatched() {
        if (state == AsyncState.DISPATCHING ||
                state == AsyncState.MUST_DISPATCH) {
            state = AsyncState.DISPATCHED;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncDispatched()", state));
        }
    }


    public synchronized void asyncMustError() {
        if (state == AsyncState.STARTED) {
            clearNonBlockingListeners();
            state = AsyncState.MUST_ERROR;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncMustError()", state));
        }
    }


    public synchronized void asyncError() {
        if (state == AsyncState.STARTING ||
                state == AsyncState.STARTED ||
                state == AsyncState.DISPATCHED ||
                state == AsyncState.TIMING_OUT ||
                state == AsyncState.MUST_COMPLETE ||
                state == AsyncState.READ_WRITE_OP ||
                state == AsyncState.COMPLETING ||
                state == AsyncState.MUST_ERROR) {
            clearNonBlockingListeners();
            state = AsyncState.ERROR;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncError()", state));
        }
    }

    public synchronized void asyncRun(Runnable runnable) {
        if (state == AsyncState.STARTING || state ==  AsyncState.STARTED ||
                state == AsyncState.READ_WRITE_OP) {
            // 从Connector的线程池中使用容器线程执行 runnable. 使用包装器防止内存泄漏
            ClassLoader oldCL;
            if (Constants.IS_SECURITY_ENABLED) {
                PrivilegedAction<ClassLoader> pa = new PrivilegedGetTccl();
                oldCL = AccessController.doPrivileged(pa);
            } else {
                oldCL = Thread.currentThread().getContextClassLoader();
            }
            try {
                if (Constants.IS_SECURITY_ENABLED) {
                    PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                            this.getClass().getClassLoader());
                    AccessController.doPrivileged(pa);
                } else {
                    Thread.currentThread().setContextClassLoader(
                            this.getClass().getClassLoader());
                }

                processor.getExecutor().execute(runnable);
            } finally {
                if (Constants.IS_SECURITY_ENABLED) {
                    PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                            oldCL);
                    AccessController.doPrivileged(pa);
                } else {
                    Thread.currentThread().setContextClassLoader(oldCL);
                }
            }
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncRun()", state));
        }

    }


    public synchronized void recycle() {
        // 使用 lastAsyncStart 确定此实例是否已被使用, 自从上次回收以来. 如果还没有，就不需要再回收，这就节省了对notifyAll()的相对昂贵的调用
        if (lastAsyncStart == 0) {
            return;
        }
        // 确保出现错误时，暂停的任何非容器线程都不暂停.
        notifyAll();
        asyncCtxt = null;
        state = AsyncState.DISPATCHED;
        lastAsyncStart = 0;
    }


    private void clearNonBlockingListeners() {
        processor.getRequest().listener = null;
        processor.getRequest().getResponse().listener = null;
    }
}
