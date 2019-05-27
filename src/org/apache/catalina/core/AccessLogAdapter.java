package org.apache.catalina.core;

import java.util.Arrays;
import java.util.Objects;

import org.apache.catalina.AccessLog;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

/**
 * 包装了多个AccessLog 实例.
 */
public class AccessLogAdapter implements AccessLog {

    private AccessLog[] logs;

    public AccessLogAdapter(AccessLog log) {
        Objects.requireNonNull(log);
        logs = new AccessLog[] { log };
    }

    public void add(AccessLog log) {
        Objects.requireNonNull(log);
        AccessLog newArray[] = Arrays.copyOf(logs, logs.length + 1);
        newArray[newArray.length - 1] = log;
        logs = newArray;
    }

    @Override
    public void log(Request request, Response response, long time) {
        for (AccessLog log: logs) {
            log.log(request, response, time);
        }
    }

    @Override
    public void setRequestAttributesEnabled(boolean requestAttributesEnabled) {
        // NOOP
    }

    @Override
    public boolean getRequestAttributesEnabled() {
        // NOOP. Could return logs[0].getRequestAttributesEnabled(), but I do
        // not see a use case for that.
        return false;
    }

}
