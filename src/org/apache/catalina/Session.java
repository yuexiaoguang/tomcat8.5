package org.apache.catalina;

import java.security.Principal;
import java.util.Iterator;

import javax.servlet.http.HttpSession;

/**
 * <b>Session</b> 是Catalina内部外观模式，为<code>HttpSession</code>，
 * 用于维护Web应用程序特定用户请求之间的状态信息.
 */
public interface Session {


    // ----------------------------------------------------- Manifest Constants


    /**
     * 创建session的时候的SessionEvent事件类型.
     */
    public static final String SESSION_CREATED_EVENT = "createSession";


    /**
     * 销毁session的时候的SessionEvent事件类型.
     */
    public static final String SESSION_DESTROYED_EVENT = "destroySession";


    /**
     * 激活session的时候的SessionEvent事件类型.
     */
    public static final String SESSION_ACTIVATED_EVENT = "activateSession";


    /**
     * 休眠session的时候的SessionEvent事件类型.
     */
    public static final String SESSION_PASSIVATED_EVENT = "passivateSession";


    // ------------------------------------------------------------- Properties


    /**
     * 返回用于验证缓存的Principal的验证类型.
     */
    public String getAuthType();


    /**
     * 设置用于验证缓存的Principal的验证类型
     *
     * @param authType 缓存的验证类型
     */
    public void setAuthType(String authType);


    /**
     * 返回会话的创建时间.
     */
    public long getCreationTime();


    /**
     * @return 会话的创建时间, 绕过会话有效性检查.
     */
    public long getCreationTimeInternal();


    /**
     * 设置会话的创建时间. 当现有会话实例重用时，Manager调用此方法..
     *
     * @param time The new creation time
     */
    public void setCreationTime(long time);


    public String getId();


    public String getIdInternal();


    public void setId(String id);


    /**
     * 设置此会话的会话标识符，并可选地通知任何关联的监听器已经创建了新会话.
     *
     * @param id        会话ID
     * @param notify    是否通知关联的监听器已经创建了新会话?
     */
    public void setId(String id, boolean notify);


    /**
     * @return 客户端上次发送与此会话关联的请求时间, 毫秒数，从 midnight, January 1, 1970 GMT.
     * 应用程序所采取的操作，例如获取或设置与会话相关联的值，不影响访问时间. 每当请求开始时，这个更新.
     */
    public long getThisAccessedTime();

    /**
     * @return 没有失效检查的客户端最后一次访问时间
     */
    public long getThisAccessedTimeInternal();

    /**
     * @return 客户端上次发送与此会话关联的请求时间, 毫秒数，从 midnight, January 1, 1970 GMT.
     * 应用程序所采取的操作，例如获取或设置与会话相关联的值，不影响访问时间. 每当请求开始时，这个更新.
     */
    public long getLastAccessedTime();

    /**
     * @return 没有失效检查的客户端最后一次访问时间
     */
    public long getLastAccessedTimeInternal();

    /**
     * @return 从上次客户端访问时间开始的空闲时间（毫秒）.
     */
    public long getIdleTime();

    /**
     * @return 从上次客户端访问时间开始的空闲时间
     */
    public long getIdleTimeInternal();

    /**
     * @return 有效的Manager.
     */
    public Manager getManager();


    /**
     * 设置有效的Manager.
     *
     * @param manager The new Manager
     */
    public void setManager(Manager manager);


    /**
     * 返回两次请求最大时间间隔, 单位是秒, 在servlet容器关闭session之前. 
     * 负值表示session永远不会超时.
     */
    public int getMaxInactiveInterval();


    /**
     * 设置两次请求最大时间间隔, 单位是秒, 在servlet容器关闭session之前. 
     * 负值表示session永远不会超时.
     *
     * @param interval The new maximum interval
     */
    public void setMaxInactiveInterval(int interval);


    public void setNew(boolean isNew);


    /**
     * 返回关联的已验证的Principal.
     * 这提供了一个<code>Authenticator</code> 使用先前缓存的已验证的Principal, 避免潜在的大代价的
     * <code>Realm.authenticate()</code>调用，在每个请求中. 
     * 如果没有当前关联的Principal, 返回<code>null</code>.
     */
    public Principal getPrincipal();


    /**
     * 设置关联的已验证的Principal.
     * 这提供了一个<code>Authenticator</code> 使用先前缓存的已验证的Principal, 避免潜在的大代价的
     * <code>Realm.authenticate()</code>调用，在每个请求中. 
     * 如果没有当前关联的Principal, 返回<code>null</code>.
     *
     * @param principal The new Principal, or <code>null</code> if none
     */
    public void setPrincipal(Principal principal);


    /**
     * 返回包装的<code>HttpSession</code>
     */
    public HttpSession getSession();


    public void setValid(boolean isValid);


    /**
     * @return <code>true</code>如果会话仍然有效
     */
    public boolean isValid();


    // --------------------------------------------------------- Public Methods


    /**
     * 更新此session的访问时间信息. 
     * 这个方法应该被上下文调用，当一个请求到来的时候 ，即使应用程序不引用它.
     */
    public void access();


    /**
     * 添加一个session事件监听器.
     *
     * @param listener 应该被通知会话事件的SessionListener实例
     */
    public void addSessionListener(SessionListener listener);


    /**
     * 结束会话访问.
     */
    public void endAccess();


    /**
     * 执行使会话无效的内部处理, 如果会话已经过期，则不触发异常.
     */
    public void expire();


    /**
     * 将指定名称绑定的对象返回给此会话的内部注释, 或者<code>null</code>
     *
     * @param name 注释名称
     */
    public Object getNote(String name);


    /**
     * 返回所有注释的名称.
     */
    public Iterator<String> getNoteNames();


    /**
     * 释放所有对象引用，初始化实例变量，以准备重用这个对象.
     */
    public void recycle();


    /**
     * 移除指定名称关联的所有注释.
     *
     * @param name 注释名称
     */
    public void removeNote(String name);


    /**
     * 移除一个session事件监听器.
     *
     * @param listener 删除会话监听器, 不再通知这个监听器
     */
    public void removeSessionListener(SessionListener listener);


    /**
     * 将对象绑定到与此会话关联的内部注释中，替换该名称的任何现有绑定.
     *
     * @param name 对象应该绑定到的名称
     * @param value 要绑定到指定名称的对象
     */
    public void setNote(String name, Object value);


    /**
     * 通知监听器有会话ID更改.
     *
     * @param newId  new session ID
     * @param oldId  old session ID
     * @param notifySessionListeners  是否通知关联的sessionListener会话 ID已经修改?
     * @param notifyContainerListeners  是否通知关联的ContainerListener会话 ID已经修改?
     */
    public void tellChangedSessionId(String newId, String oldId,
            boolean notifySessionListeners, boolean notifyContainerListeners);


    /**
     * 会话实现是否支持给定属性的分发?
     * 如果 Manager被标记为可分发的, 然后，在将属性添加到会话之前，必须使用此方法检查属性。 如果该属性不可分配，抛出{@link IllegalArgumentException}.
     * <p>
     * 注意，{@link Manager}实现可以进一步限制哪些属性是分布式的, 但是一个{@link Manager}等级限制不应该
     * 在{@link HttpSession#setAttribute(String, Object)}中触发一个{@link IllegalArgumentException}。
     *
     * @param name  属性名
     * @param value 属性值
     *
     * @return {@code true}如果支持分布式, 否则{@code false}
     */
    public boolean isAttributeDistributable(String name, Object value);
}
