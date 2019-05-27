package org.apache.catalina;

import java.util.Set;

/**
 * <p>接口描述Valves的集合应该被顺序执行，当<code>invoke()</code>方法执行的时候.
 * 在传递过程中，某个Valve必须处理请求并且创建相应的响应，而不是继续传递请求 .</p>
 *
 * <p>通常每个Container关联单个Pipeline实例。
 * 容器的正常请求处理功能通常封装在容器指定的Valve，应该始终在pipeline的最后执行. 
 * 为了辅助上面的功能,<code>setBasic()</code>方法设置最后被执行的Valve实例.
 * 其他Valves按照它们被添加的顺序执行,在basic Valve执行之前.</p>
 */
public interface Pipeline {


    // ------------------------------------------------------------- Properties


    /**
     * @return 返回basic Valve实例.
     */
    public Valve getBasic();


    /**
     * <p>设置basic Valve实例. 
     * 要设置basic Valve,如果它实现了<code>Contained</code>,Valve 的<code>setContainer()</code>方法将被调用,并拥有Container作为一个参数
     * 如果选择的Valve没有关联到当前Container,方法可能抛出<code>IllegalArgumentException</code>
     * 如果已经关联到其他的Container，将抛出<code>IllegalStateException</code></p>
     *
     * @param valve basic Valve
     */
    public void setBasic(Valve valve);


    // --------------------------------------------------------- Public Methods


    /**
     * <p>添加一个新的Valve到pipeline结尾. 
     * 要设置basic Valve,如果它实现了<code>Contained</code>,Valve 的<code>setContainer()</code>方法将被调用,并拥有Container作为一个参数
     * 如果选择的Valve没有关联到当前Container,方法可能抛出<code>IllegalArgumentException</code>
     * 如果已经关联到其他的Container，将抛出<code>IllegalStateException</code></p>
     *
     * <p>实现注意: 实现将触发{@link Container#ADD_VALVE_EVENT}，如果调用成功.</p>
     *
     * @param valve 要添加的Valve
     *
     * @exception IllegalArgumentException 如果Container拒绝接受指定的Valve
     * @exception IllegalArgumentException 如果指定的Valve拒绝关联到Container
     * @exception IllegalStateException 如果指定的Valve已经关联到其他的Container
     */
    public void addValve(Valve valve);


    /**
     * 返回Valves数组, 包括basic Valve. 
     * 如果没有Valves, 将返回一个零长度的数组
     */
    public Valve[] getValves();


    /**
     * 从pipeline移除指定的Valve; 如果没有找到，什么都不做. 
     * 如果Valve被找到并被移除, Valve 的 <code>setContainer(null)</code>方法将被调用,如果它实现了<code>Contained</code>.
     *
     * <p>实现注意: 实现将触发{@link Container#REMOVE_VALVE_EVENT}，如果调用成功.</p>
     *
     * @param valve 要移除的Valve
     */
    public void removeValve(Valve valve);


    /**
     * <p>返回basic Valve.
     */
    public Valve getFirst();

    /**
     * 返回true 如果管道中的所有的阀门支持异步, 否则false
     */
    public boolean isAsyncSupported();


    /**
     * @return 管道关联的Container.
     */
    public Container getContainer();


    /**
     * 设置关联的Container.
     *
     * @param container 关联的容器
     */
    public void setContainer(Container container);


    /**
     * 标识Valve, 这个管道不支持异步.
     *
     * @param result 这个管道中阀门的完全限定类名
     */
    public void findNonAsyncValves(Set<String> result);
}
