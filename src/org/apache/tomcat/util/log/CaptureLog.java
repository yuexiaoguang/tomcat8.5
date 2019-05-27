package org.apache.tomcat.util.log;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * 每个线程System.err和System.out日志捕获数据.
 */
class CaptureLog {

    protected CaptureLog() {
        baos = new ByteArrayOutputStream();
        ps = new PrintStream(baos);
    }

    private final ByteArrayOutputStream baos;
    private final PrintStream ps;

    protected PrintStream getStream() {
        return ps;
    }

    protected void reset() {
        baos.reset();
    }

    protected String getCapture() {
        return baos.toString();
    }
}
