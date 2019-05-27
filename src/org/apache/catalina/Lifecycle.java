package org.apache.catalina;


/**
 * 组件生命周期方法的通用接口. 
 * 可能是Catalina组件, 但不是必须的, 实现这个接口(以及它们支持的功能的相应接口)为了提供一个一致的机制来启动和停止组件
 * <br>
 * 支持{@link Lifecycle}的组件的有效状态转换:
 * <pre>
 *            start()
 *  -----------------------------
 *  |                           |
 *  | init()                    |
 * NEW -»-- INITIALIZING        |
 * | |           |              |     ------------------«-----------------------
 * | |           |auto          |     |                                        |
 * | |          \|/    start() \|/   \|/     auto          auto         stop() |
 * | |      INITIALIZED --»-- STARTING_PREP --»- STARTING --»- STARTED --»---  |
 * | |         |                                                            |  |
 * | |destroy()|                                                            |  |
 * | --»-----«--    ------------------------«--------------------------------  ^
 * |     |          |                                                          |
 * |     |         \|/          auto                 auto              start() |
 * |     |     STOPPING_PREP ----»---- STOPPING ------»----- STOPPED -----»-----
 * |    \|/                               ^                     |  ^
 * |     |               stop()           |                     |  |
 * |     |       --------------------------                     |  |
 * |     |       |                                              |  |
 * |     |       |    destroy()                       destroy() |  |
 * |     |    FAILED ----»------ DESTROYING ---«-----------------  |
 * |     |                        ^     |                          |
 * |     |     destroy()          |     |auto                      |
 * |     --------»-----------------    \|/                         |
 * |                                 DESTROYED                     |
 * |                                                               |
 * |                            stop()                             |
 * ----»-----------------------------»------------------------------
 *
 * 任何状态都可以转换为失败.
 *
 * 在STARTING_PREP, STARTING, STARTED状态下调用start() 无效.
 *
 * 在NEW状态下调用 start() 将导致进入start()方法之后立即调用init().
 *
 * 在STOPPING_PREP, STOPPING, STOPPED状态下调用 stop()无效.
 *
 * 在NEW状态下调用 stop() 转换组件状态为 STOPPED. 当组件启动失败且没有启动所有子组件时，通常会遇到这种情况.
 * 当组件停止时, 它将尝试停止所有子组件 - 甚至那些没有开始的.
 *
 * 尝试其他转换将抛出 {@link LifecycleException}.
 *
 * </pre>
 * {@link LifecycleEvent}被触发，在方法中发生状态更改. 不会触发{@link LifecycleEvent}， 如果尝试的转换无效.
 */
public interface Lifecycle {


    // ----------------------------------------------------- Manifest Constants


    /**
     * "组件初始化之前"事件的LifecycleEvent类型.
     */
    public static final String BEFORE_INIT_EVENT = "before_init";


    /**
     * "组件初始化之后"事件的LifecycleEvent类型.
     */
    public static final String AFTER_INIT_EVENT = "after_init";


    /**
     * "组件启动"事件的LifecycleEvent类型.
     */
    public static final String START_EVENT = "start";


    /**
     * "组件启动之前"事件的LifecycleEvent类型.
     */
    public static final String BEFORE_START_EVENT = "before_start";


    /**
     * "组件启动之后"事件的LifecycleEvent类型.
     */
    public static final String AFTER_START_EVENT = "after_start";


    /**
     * "组件停止"事件的LifecycleEvent类型.
     */
    public static final String STOP_EVENT = "stop";


    /**
     * "组件停止之前"事件的LifecycleEvent类型.
     */
    public static final String BEFORE_STOP_EVENT = "before_stop";


    /**
     * "组件停止之后"事件的LifecycleEvent类型.
     */
    public static final String AFTER_STOP_EVENT = "after_stop";


    /**
     * "组件销毁之后"事件的LifecycleEvent类型.
     */
    public static final String AFTER_DESTROY_EVENT = "after_destroy";


    /**
     * "组件销毁之前"事件的LifecycleEvent类型.
     */
    public static final String BEFORE_DESTROY_EVENT = "before_destroy";


    /**
     * "周期"事件的LifecycleEvent类型.
     */
    public static final String PERIODIC_EVENT = "periodic";


    /**
     * "configure_start"事件的LifecycleEvent类型.
     * 这些组件使用单独的组件执行配置，需要在配置执行时发出信号 - 通常在{@link #BEFORE_START_EVENT}之后和{@link #START_EVENT}之前.
     */
    public static final String CONFIGURE_START_EVENT = "configure_start";


    /**
     * "configure_stop"事件的LifecycleEvent类型.
     * 这些组件使用单独的组件执行配置，需要在执行配置时发出信号 - 通常在{@link #STOP_EVENT}之后和{@link #AFTER_STOP_EVENT}之前.
     */
    public static final String CONFIGURE_STOP_EVENT = "configure_stop";


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个LifecycleEvent监听器到这个组件.
     *
     * @param listener 要添加的监听器
     */
    public void addLifecycleListener(LifecycleListener listener);


    /**
     * 获取与此生命周期相关联的生命周期监听器.
     *
     * @return 包含与此生命周期相关联的生命周期监听器的数组. 如果没有监听器, 返回零长度数组.
     */
    public LifecycleListener[] findLifecycleListeners();


    /**
     * 删除一个LifecycleEvent监听器.
     *
     * @param listener 要删除的监听器
     */
    public void removeLifecycleListener(LifecycleListener listener);


    /**
     * 准备启动组件.
     * 此方法应该执行任何初始化所需的后期对象创建. 下列{@link LifecycleEvent}将按下列顺序触发:
     * <ol>
     *   <li>INIT_EVENT: 组件初始化成功.</li>
     * </ol>
     *
     * @exception LifecycleException 如果此组件检测到防止该组件被使用的致命错误
     */
    public void init() throws LifecycleException;

    /**
     * 这个方法应该在公共方法调用之前调用.
     * 下面的{@link LifecycleEvent}将按一下顺序触发:
     * <ol>
     *   <li>BEFORE_START_EVENT: 在方法开始时. 状态转换到{@link LifecycleState#STARTING_PREP}.</li>
     *   <li>START_EVENT: 方法一旦调用，就可以安全的调用start()为任何子组件. 状态转换到{@link LifecycleState#STARTING}.</li>
     *   <li>AFTER_START_EVENT: 在方法结尾, 返回之前调用. 状态转换到{@link LifecycleState#STARTED}.</li>
     * </ol>
     *
     * @exception LifecycleException 如果此组件检测到防止该组件被使用的致命错误
     */
    public void start() throws LifecycleException;


    /**
     * 下列{@link LifecycleEvent}将按以下顺序触发:
     * <ol>
     *   <li>BEFORE_STOP_EVENT: 在方法开始时. 状态转换到{@link LifecycleState#STOPPING_PREP}.</li>
     *   <li>STOP_EVENT: 方法一旦调用，就可以安全的调用stop() 为任何子组件. 状态转换到{@link LifecycleState#STOPPING}.</li>
     *   <li>AFTER_STOP_EVENT: 在方法结尾, 返回之前调用. 状态转换到{@link LifecycleState#STOPPED}.</li>
     * </ol>
     *
     * 注意，如果从{@link LifecycleState#FAILED}转变，那么上面的三个事件将被触发，
     * 组件将直接从{@link LifecycleState#FAILED}转换到{@link LifecycleState#STOPPING}, 通过传递{@link LifecycleState#STOPPING_PREP}
     *
     * @exception LifecycleException 如果此组件检测到防止该组件被使用的致命错误
     */
    public void stop() throws LifecycleException;

    /**
     * 准备销毁对象. 下列{@link LifecycleEvent}将按以下顺序触发:
     * <ol>
     *   <li>DESTROY_EVENT: 组件成功销毁.</li>
     * </ol>
     *
     * @exception LifecycleException 如果此组件检测到防止该组件被使用的致命错误
     */
    public void destroy() throws LifecycleException;


    /**
     * 获取源组件的当前状态.
     *
     * @return 源组件的当前状态.
     */
    public LifecycleState getState();


    /**
     * 获取当前组件状态的文本表示形式.
     * 对JMX有用. 确定组件状态, 使用{@link #getState()}.
     *
     * @return 当前组件状态的名称.
     */
    public String getStateName();


    /**
     * 标记接口，用于指示实例只应使用一次.
     * 调用支持这个接口的实例上的{@link #stop()}方法将自动调用{@link #destroy()}，在{@link #stop()}完成之后.
     */
    public interface SingleUse {
    }
}
