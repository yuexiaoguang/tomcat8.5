package org.apache.coyote.http2;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * 用于管理优先级.
 */
abstract class AbstractStream {

    private static final Log log = LogFactory.getLog(AbstractStream.class);
    private static final StringManager sm = StringManager.getManager(AbstractStream.class);

    private final Integer identifier;

    private volatile AbstractStream parentStream = null;
    private final Set<Stream> childStreams =
            Collections.newSetFromMap(new ConcurrentHashMap<Stream,Boolean>());
    private long windowSize = ConnectionSettingsBase.DEFAULT_INITIAL_WINDOW_SIZE;

    public Integer getIdentifier() {
        return identifier;
    }


    public AbstractStream(Integer identifier) {
        this.identifier = identifier;
    }


    void detachFromParent() {
        if (parentStream != null) {
            parentStream.getChildStreams().remove(this);
            parentStream = null;
        }
    }


    final void addChild(Stream child) {
        child.setParentStream(this);
        childStreams.add(child);
    }


    boolean isDescendant(AbstractStream stream) {
        if (childStreams.contains(stream)) {
            return true;
        }
        for (AbstractStream child : childStreams) {
            if (child.isDescendant(stream)) {
                return true;
            }
        }
        return false;
    }


    AbstractStream getParentStream() {
        return parentStream;
    }


    void setParentStream(AbstractStream parentStream) {
        this.parentStream = parentStream;
    }


    final Set<Stream> getChildStreams() {
        return childStreams;
    }


    protected synchronized void setWindowSize(long windowSize) {
        this.windowSize = windowSize;
    }


    protected synchronized long getWindowSize() {
        return windowSize;
    }


    /**
     * 增加窗口大小.
     * 
     * @param increment 增加的数量
     * @throws Http2Exception 如果窗口大小现在高于允许的最大值
     */
    protected synchronized void incrementWindowSize(int increment) throws Http2Exception {
        // 这里不需要溢出保护.
        // 增量不能大于 Integer.MAX_VALUE, 而且一旦 windowSize 大于 2^31-1, 将触发一个错误.
        windowSize += increment;

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("abstractStream.windowSizeInc", getConnectionId(),
                    getIdentifier(), Integer.toString(increment), Long.toString(windowSize)));
        }

        if (windowSize > ConnectionSettingsBase.MAX_WINDOW_SIZE) {
            String msg = sm.getString("abstractStream.windowSizeTooBig", getConnectionId(), identifier,
                    Integer.toString(increment), Long.toString(windowSize));
            if (identifier.intValue() == 0) {
                throw new ConnectionException(msg, Http2Error.FLOW_CONTROL_ERROR);
            } else {
                throw new StreamException(
                        msg, Http2Error.FLOW_CONTROL_ERROR, identifier.intValue());
            }
        }
    }


    protected synchronized void decrementWindowSize(int decrement) {
        // 这里不需要溢出保护.
        // 减量不能大于 Integer.MAX_VALUE, 而且一旦 windowSize小于零, 不允许再减量
        windowSize -= decrement;
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("abstractStream.windowSizeDec", getConnectionId(),
                    getIdentifier(), Integer.toString(decrement), Long.toString(windowSize)));
        }
    }


    protected abstract String getConnectionId();

    protected abstract int getWeight();

    protected abstract void doNotifyAll();
}
