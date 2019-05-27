package org.apache.juli.logging;

/**
 * <p>一个简单的日志接口，抽象日志API.</p>
 *
 * <p><code>Log</code>使用的六个日志等级是 (顺序):</p>
 * <ol>
 * <li>trace (the least serious)</li>
 * <li>debug</li>
 * <li>info</li>
 * <li>warn</li>
 * <li>error</li>
 * <li>fatal (the most serious)</li>
 * </ol>
 * <p>这些日志级别与底层日志记录系统使用的概念的映射, 是依赖于实现的.</p>
 *
 * <p>性能通常是日志记录问题. 通过检查适当的属性, 组件可以避免昂贵的操作 (生成要记录的信息).</p>
 *
 * <p> 例如,
 * <code>
 *    if (log.isDebugEnabled()) {
 *        ... do something expensive ...
 *        log.debug(theResult);
 *    }
 * </code>
 * </p>
 *
 * <p>底层日志记录系统的配置，通常通过该系统支持的机制，在Logging API外部完成.</p>
 */
public interface Log {


    // ----------------------------------------------------- Logging Properties


    /**
     * <p>是否启用了 debug 日志记录? </p>
     *
     * <p>调用此方法可防止执行昂贵的操作 (例如, <code>String</code>连接), 当日志级别大于 debug 时. </p>
     *
     * @return <code>true</code>如果启用了 debug 级别日志记录, 否则<code>false</code>
     */
    public boolean isDebugEnabled();


    /**
     * <p>当前是否启用了 error 记录? </p>
     *
     * <p> 调用此方法可防止执行昂贵的操作 (例如, <code>String</code>连接), 当日志级别大于 error 时. </p>
     *
     * @return <code>true</code>如果启用了 error 级别日志记录, 否则 <code>false</code>
     */
    public boolean isErrorEnabled();


    /**
     * <p>当前是否启用了 fatal 记录? </p>
     *
     * <p> 调用此方法可防止执行昂贵的操作 (例如, <code>String</code>连接), 当日志级别大于 fatal 时. </p>
     *
     * @return <code>true</code>如果启用了 fatal 级别日志记录, 否则 <code>false</code>
     */
    public boolean isFatalEnabled();


    /**
     * <p>当前是否启用了 info 记录? </p>
     *
     * <p> 调用此方法可防止执行昂贵的操作 (例如, <code>String</code>连接), 当日志级别大于 info 时. </p>
     *
     * @return <code>true</code>如果启用了 info 级别日志记录, 否则 <code>false</code>
     */
    public boolean isInfoEnabled();


    /**
     * <p>当前是否启用了 trace 记录? </p>
     *
     * <p> 调用此方法可防止执行昂贵的操作 (例如, <code>String</code>连接), 当日志级别大于 trace 时. </p>
     *
     * @return <code>true</code>如果启用了 trace 级别日志记录, 否则 <code>false</code>
     */
    public boolean isTraceEnabled();


    /**
     * <p>当前是否启用了 warn 记录? </p>
     *
     * <p> 调用此方法可防止执行昂贵的操作 (例如, <code>String</code>连接), 当日志级别大于 warn 时. </p>
     *
     * @return <code>true</code>如果启用了 warn 级别日志记录, 否则 <code>false</code>
     */
    public boolean isWarnEnabled();


    // -------------------------------------------------------- Logging Methods


    /**
     * <p> 使用 trace 日志级别记录消息. </p>
     *
     * @param message 要记录的消息
     */
    public void trace(Object message);


    /**
     * <p> 使用 trace 日志级别记录错误. </p>
     *
     * @param message 要记录的消息
     * @param t log this cause
     */
    public void trace(Object message, Throwable t);


    /**
     * <p> 使用 debug 日志级别记录消息. </p>
     *
     * @param message 要记录的消息
     */
    public void debug(Object message);


    /**
     * <p> 使用 debug 日志级别记录错误. </p>
     *
     * @param message 要记录的消息
     * @param t log this cause
     */
    public void debug(Object message, Throwable t);


    /**
     * <p> 使用 info 日志级别记录消息. </p>
     *
     * @param message 要记录的消息
     */
    public void info(Object message);


    /**
     * <p> 使用 info 日志级别记录错误. </p>
     *
     * @param message 要记录的消息
     * @param t log this cause
     */
    public void info(Object message, Throwable t);


    /**
     * <p> 使用 warn 日志级别记录消息. </p>
     *
     * @param message 要记录的消息
     */
    public void warn(Object message);


    /**
     * <p> 使用 warn 日志级别记录错误. </p>
     *
     * @param message 要记录的消息
     * @param t log this cause
     */
    public void warn(Object message, Throwable t);


    /**
     * <p> 使用 error 日志级别记录消息. </p>
     *
     * @param message 要记录的消息
     */
    public void error(Object message);


    /**
     * <p> 使用 error 日志级别记录错误. </p>
     *
     * @param message 要记录的消息
     * @param t log this cause
     */
    public void error(Object message, Throwable t);


    /**
     * <p> 使用 fatal 日志级别记录消息. </p>
     *
     * @param message 要记录的消息
     */
    public void fatal(Object message);


    /**
     * <p> 使用 fatal 日志级别记录错误. </p>
     *
     * @param message 要记录的消息
     * @param t log this cause
     */
    public void fatal(Object message, Throwable t);


}
