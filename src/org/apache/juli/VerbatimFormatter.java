package org.apache.juli;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * 仅输出日志消息而不添加其他元素. 不记录堆栈跟踪. 日志消息是由 <code>System.lineSeparator()</code> 分隔的.
 * 这适用于需要完全控制输出格式的访问日志等.
 */
public class VerbatimFormatter extends Formatter {

    @Override
    public String format(LogRecord record) {
        // Timestamp
        StringBuilder sb = new StringBuilder(record.getMessage());

        // 下一条记录的新行
        sb.append(System.lineSeparator());

        return sb.toString();
    }

}
