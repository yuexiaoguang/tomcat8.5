package javax.servlet.http;

import java.util.Enumeration;

import javax.servlet.ServletContext;

/**
 * 提供一种方法，可以识别多个页面请求或访问Web站点的用户，并存储有关该用户的信息.
 * <p>
 * servlet容器使用此接口在HTTP客户端和HTTP服务器之间创建会话. 会话在指定的时间段内仍然存在, 跨越用户的多个连接或页面请求.
 * 会话通常对应于一个可以多次访问站点的用户. 服务器可以通过多种方式维护会话，比如使用cookie或重写URL.
 * <p>
 * 该接口允许servlet
 * <ul>
 * <li>查看和操作关于会话的信息, 例如会话标识符、创建时间和最后访问时间
 * <li>将对象绑定到会话，允许用户信息跨多个用户连接持久化
 * </ul>
 * <p>
 * 当应用程序将对象存储在会话中或从会话中删除对象时, 会话检查对象是否实现 {@link HttpSessionBindingListener}.
 * 如果是的话, servlet通知对象已绑定到会话或未绑定到会话. 在绑定方法完成后发送通知. 对于无效或过期的会话，在会话失效或过期后发送通知.
 * <p>
 * 当容器在分布式容器环境中在VM之间迁移会话时, 所有实现了{@link HttpSessionActivationListener}接口的会话属性会被通知.
 * <p>
 * servlet应该能够处理客户端不选择加入会话的情况，例如当cookie故意关闭时. 直到客户端加入会话, <code>isNew</code>返回<code>true</code>.
 * 如果客户端选择不加入会话, <code>getSession</code>将在每个请求上返回不同的会话, 而且<code>isNew</code>将总是返回<code>true</code>.
 * <p>
 * 会话信息仅限于当前的Web应用程序(<code>ServletContext</code>), 因此，存储在一个上下文中的信息不会在另一个上下文中直接可见.
 */
public interface HttpSession {

    /**
     * 返回创建此会话的时间, 毫秒数自从January 1, 1970 GMT午夜以来.
     * 
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public long getCreationTime();

    /**
     * 返回一个字符串，该字符串包含分配给该会话的唯一标识符.
     * 标识符由servlet容器分配，并且是依赖于实现的.
     *
     * @return 分配给此会话的标识符
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public String getId();

    /**
     * 返回客户端上次发送与此会话关联的请求的时间, 毫秒数, 并以容器收到请求的时间为标记.
     * <p>
     * 应用程序所采取的操作, 例如获取或设置与会话相关联的值, 不影响访问时间.
     * 
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public long getLastAccessedTime();

    /**
     * 返回这个会话所属的ServletContext.
     *
     * @since 2.3
     */
    public ServletContext getServletContext();

    /**
     * 指定时间, 秒为单位, 在servlet容器将使会话无效之前，客户机请求之间的最大间隔.
     * 零或负时间表示会话不应超时.
     *
     * @param interval 指定秒数
     */
    public void setMaxInactiveInterval(int interval);

    /**
     * 返回最大时间间隔, 秒为单位, servlet容器将在客户端访问之间保持此会话开放.
     * 在此间隔之后，servlet容器将使会话无效. 
     * 零或负时间表示会话不应超时.
     */
    public int getMaxInactiveInterval();

    /**
     * 返回此会话中指定的名称绑定的对象, 或者<code>null</code>.
     *
     * @param name 对象名
     * @return 具有指定名称的对象
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public Object getAttribute(String name);

    /**
     * @param name 对象名称
     * @return 具有指定名称的对象
     * @exception IllegalStateException 如果在无效会话上调用此方法
     * @deprecated As of Version 2.2, this method is replaced by
     *             {@link #getAttribute}.
     */
    @SuppressWarnings("dep-ann")
    // Spec API does not use @Deprecated
    public Object getValue(String name);

    /**
     * 绑定到该会话的所有对象的名称.
     * 
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public Enumeration<String> getAttributeNames();

    /**
     * @return 绑定到该会话的所有对象的名称
     * @exception IllegalStateException 如果在无效会话上调用此方法
     * @deprecated As of Version 2.2, this method is replaced by
     *             {@link #getAttributeNames}
     */
    @SuppressWarnings("dep-ann")
    // Spec API does not use @Deprecated
    public String[] getValueNames();

    /**
     * 使用指定的名称将对象绑定到此会话. 如果同一名称的对象已经绑定到会话中, 对象被替换.
     * <p>
     * 这个方法执行之后, 如果新的对象实现了<code>HttpSessionBindingListener</code>, 容器调用
     * <code>HttpSessionBindingListener.valueBound</code>. 容器随后通知所有的<code>HttpSessionAttributeListener</code>.
     * <p>
     * 如果原来绑定的对象实现了<code>HttpSessionBindingListener</code>, 调用它的
     * <code>HttpSessionBindingListener.valueUnbound</code>方法.
     * <p>
     * 如果传过来的值是 null, 和<code>removeAttribute()</code>作用相同.
     *
     * @param name 对象要绑定的名称; 不能是 null
     * @param value 要绑定的对象
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public void setAttribute(String name, Object value);

    /**
     * @param name 对象被绑定的名称; 不能是 null
     * @param value 要绑定的对象; 不能是 null
     * @exception IllegalStateException 如果在无效会话上调用此方法
     * @deprecated As of Version 2.2, this method is replaced by
     *             {@link #setAttribute}
     */
    @SuppressWarnings("dep-ann")
    // Spec API does not use @Deprecated
    public void putValue(String name, Object value);

    /**
     * 从该会话中删除具有指定名称的对象. 如果会话没有与指定名称绑定的对象, 这个方法什么都不做.
     * <p>
     * 这个方法执行之后, 如果对象实现了<code>HttpSessionBindingListener</code>, 容器调用
     * <code>HttpSessionBindingListener.valueUnbound</code>. 容器随后通知所有的<code>HttpSessionAttributeListener</code>.
     *
     * @param name 要删除的对象的名称
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public void removeAttribute(String name);

    /**
     * @param name 要从会话中删除的对象的名称
     * @exception IllegalStateException 如果在无效会话上调用此方法
     * @deprecated As of Version 2.2, this method is replaced by
     *             {@link #removeAttribute}
     */
    @SuppressWarnings("dep-ann")
    // Spec API does not use @Deprecated
    public void removeValue(String name);

    /**
     * 让这个对象无效，并解绑所有绑定的对象.
     *
     * @exception IllegalStateException 如果在已经失效的会话上调用此方法
     */
    public void invalidate();

    /**
     * 返回<code>true</code>如果客户端还不知道会话，或者客户端选择不加入会话.
     * 例如，如果服务器只使用基于cookie的会话，而客户端禁用了cookie的使用，那么每个请求都会出现一个会话.
     *
     * @return <code>true</code>如果服务器创建了会话，但客户端还没有加入
     * @exception IllegalStateException 如果在已经失效的会话上调用此方法
     */
    public boolean isNew();
}
