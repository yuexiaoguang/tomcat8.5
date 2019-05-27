package org.apache.catalina.ssi;


import java.io.PrintWriter;
/**
 * 所有SSI 命令( SSIEcho, SSIInclude, ...)必须实现
 */
public interface SSICommand {
    /**
     * 将命令的输出写入writer.
     * 
     * @param ssiMediator SSI的中介
     * @param commandName 实际命令的名称 ( ie. echo )
     * @param paramNames 参数名称
     * @param paramValues 参数值
     * @param writer the writer to output to
     * 
     * @return 来自SSI命令的最后修改日期
     * @throws SSIStopProcessingException 如果应中止SSI处理
     */
    public long process(SSIMediator ssiMediator, String commandName,
            String[] paramNames, String[] paramValues, PrintWriter writer)
            throws SSIStopProcessingException;
}