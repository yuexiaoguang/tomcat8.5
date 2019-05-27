package org.apache.juli;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * 更紧凑的格式化程序.
 *
 * 等价的log4j配置:
 *   <pre>
 *  log4j.rootCategory=WARN, A1
 *  log4j.appender.A1=org.apache.log4j.ConsoleAppender
 *  log4j.appender.A1.layout=org.apache.log4j.PatternLayout
 *  log4j.appender.A1.Target=System.err
 *  log4j.appender.A1.layout.ConversionPattern=%r %-15.15c{2} %-1.1p %m %n
 *  </pre>
 *
 * Example:
 *  1130122891846 Http11BaseProtocol I Initializing Coyote HTTP/1.1 on http-8800
 */
public class JdkLoggerFormatter extends Formatter {

    // 来自JDK Level的值
    public static final int LOG_LEVEL_TRACE  = 400;
    public static final int LOG_LEVEL_DEBUG  = 500;
    public static final int LOG_LEVEL_INFO   = 800;
    public static final int LOG_LEVEL_WARN   = 900;
    public static final int LOG_LEVEL_ERROR  = 1000;
    public static final int LOG_LEVEL_FATAL  = 1000;

    @Override
    public String format(LogRecord record) {
        Throwable t=record.getThrown();
        int level=record.getLevel().intValue();
        String name=record.getLoggerName();
        long time=record.getMillis();
        String message=formatMessage(record);


        if( name.indexOf('.') >= 0 )
            name = name.substring(name.lastIndexOf('.') + 1);

        // 使用字符串缓冲区可获得更好的性能
        StringBuilder buf = new StringBuilder();

        buf.append(time);

        // 填充到8以使其更具可读性
        for( int i=0; i<8-buf.length(); i++ ) { buf.append(" "); }

        // 增加日志级别的可读表示.
        switch(level) {
         case LOG_LEVEL_TRACE: buf.append(" T "); break;
         case LOG_LEVEL_DEBUG: buf.append(" D "); break;
         case LOG_LEVEL_INFO:  buf.append(" I ");  break;
         case LOG_LEVEL_WARN:  buf.append(" W ");  break;
         case LOG_LEVEL_ERROR: buf.append(" E "); break;
         //case : buf.append(" F "); break;
         default: buf.append("   ");
         }


        // 如果已配置，则增加日志实例的名称
        buf.append(name);
        buf.append(" ");

        // 垫到20个字符
        for( int i=0; i<8-buf.length(); i++ ) { buf.append(" "); }

        // 消息
        buf.append(message);

        // 如果不为null，则追加堆栈跟踪
        if(t != null) {
            buf.append(System.lineSeparator());

            java.io.StringWriter sw= new java.io.StringWriter(1024);
            java.io.PrintWriter pw= new java.io.PrintWriter(sw);
            t.printStackTrace(pw);
            pw.close();
            buf.append(sw.toString());
        }

        buf.append(System.lineSeparator());
        // 打印到适当的目的地
        return buf.toString();
    }
}
