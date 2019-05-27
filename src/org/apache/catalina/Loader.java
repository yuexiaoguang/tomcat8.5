package org.apache.catalina;

import java.beans.PropertyChangeListener;

/**
 * <b>Loader</b> 代表一个 Java ClassLoader实现，可以被Container用来加载类文件(以及Loader关联的存储库)，
 * 设计用来重新加载请求, 以及检测底层存储库中是否发生更改的一种机制.
 * <p>
 * 为了<code>Loader</code>能成功操作<code>Context</code>（实现了重新加载方法的接口）, 
 * 它必须遵守以下约束条件:
 * <ul>
 * <li>必须实现<code>Lifecycle</code> 因此Context可以指示一个新的类装入器是必需的
 * <li><code>start()</code>方法必须无条件地创建一个新的<code>ClassLoader</code>实现类
 * <li><code>stop()</code> 方法必须放弃引用 以前使用的<code>ClassLoader</code>, 以便类加载器可以垃圾收集
 * <li>在同一个<code>Loader</code>实例上调用<code>start()</code>方法之后，必须调用<code>stop()</code>方法
 * <li>基于实现类选择的策略, 必须调用<code>Context.reload()</code>方法 拥有<code>Context</code>，
 * 		当类加载器加载的一个或多个类文件发生更改时
 * </ul>
 */
public interface Loader {


    /**
     * 执行周期任务, 例如重新加载, 等. 该方法将在该容器的类加载上下文被调用.
     * 异常将被捕获和记录.
     */
    public void backgroundProcess();


    public ClassLoader getClassLoader();


    public Context getContext();


    public void setContext(Context context);


    /**
     * 返回用于配置 ClassLoader的"遵循标准委托模型"标志.
     */
    public boolean getDelegate();


    /**
     * 设置用于配置 ClassLoader的"遵循标准委托模型"标志.
     *
     * @param delegate The new flag
     */
    public void setDelegate(boolean delegate);


    /**
     * 是否重新加载的标志.
     */
    public boolean getReloadable();


    /**
     * 设置是否重新加载的标志.
     *
     * @param reloadable The new reloadable flag
     */
    public void setReloadable(boolean reloadable);


    /**
     * 添加一个属性修改监听器.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener);


    /**
     * 内部repository是否被修改，是否需要重新加载类？
     *
     * @return <code>true</code>内部库已经被修改,
     *         否则<code>false</code>
     */
    public boolean modified();


    /**
     * 移除一个属性修改监听器.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener);
}
