package org.apache.tomcat.websocket;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.websocket.SendHandler;
import javax.websocket.SendResult;

import org.apache.tomcat.util.res.StringManager;


/**
 * 将Future转换为SendHandler.
 */
class FutureToSendHandler implements Future<Void>, SendHandler {

    private static final StringManager sm = StringManager.getManager(FutureToSendHandler.class);

    private final CountDownLatch latch = new CountDownLatch(1);
    private final WsSession wsSession;
    private volatile AtomicReference<SendResult> result = new AtomicReference<>(null);

    public FutureToSendHandler(WsSession wsSession) {
        this.wsSession = wsSession;
    }


    // --------------------------------------------------------- SendHandler

    @Override
    public void onResult(SendResult result) {
        this.result.compareAndSet(null, result);
        latch.countDown();
    }


    // -------------------------------------------------------------- Future

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 不支持取消任务
        return false;
    }

    @Override
    public boolean isCancelled() {
        // 不支持取消任务
        return false;
    }

    @Override
    public boolean isDone() {
        return latch.getCount() == 0;
    }

    @Override
    public Void get() throws InterruptedException,
            ExecutionException {
        try {
            wsSession.registerFuture(this);
            latch.await();
        } finally {
            wsSession.unregisterFuture(this);
        }
        if (result.get().getException() != null) {
            throw new ExecutionException(result.get().getException());
        }
        return null;
    }

    @Override
    public Void get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException,
            TimeoutException {
        boolean retval = false;
        try {
            wsSession.registerFuture(this);
            retval = latch.await(timeout, unit);
        } finally {
            wsSession.unregisterFuture(this);

        }
        if (retval == false) {
            throw new TimeoutException(sm.getString("futureToSendHandler.timeout",
                    Long.valueOf(timeout), unit.toString().toLowerCase()));
        }
        if (result.get().getException() != null) {
            throw new ExecutionException(result.get().getException());
        }
        return null;
    }
}
