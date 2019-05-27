package org.apache.catalina.session;

import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.WriteAbortedException;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.SessionEvent;
import org.apache.catalina.SessionListener;
import org.apache.catalina.TomcatPrincipal;
import org.apache.catalina.security.SecurityUtil;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * <b>Session</b>接口标准实现类.
 * 这个对象是可序列化的, 因此，它可以存储在持久存储或转移到一个不同的虚拟机可分配会话支持.
 * <p>
 * <b>实现注意</b>: 这个类的实例表示内部（会话）和应用层（HttpSession）的会话视图.
 * 但是, 因为类本身没有被声明为public, <code>org.apache.catalina.session</code>包之外的类不能使用此实例HTTPSession视图返回到会话视图.
 * <p>
 * <b>实现注意</b>: 如果将字段添加到该类, 必须确保在读/写对象方法中进行了这些操作，这样就可以正确地序列化这个类.
 */
public class StandardSession implements HttpSession, Session, Serializable {

    private static final long serialVersionUID = 1L;

    protected static final boolean STRICT_SERVLET_COMPLIANCE;

    protected static final boolean ACTIVITY_CHECK;

    protected static final boolean LAST_ACCESS_AT_START;

    static {
        STRICT_SERVLET_COMPLIANCE = Globals.STRICT_SERVLET_COMPLIANCE;

        String activityCheck = System.getProperty(
                "org.apache.catalina.session.StandardSession.ACTIVITY_CHECK");
        if (activityCheck == null) {
            ACTIVITY_CHECK = STRICT_SERVLET_COMPLIANCE;
        } else {
            ACTIVITY_CHECK = Boolean.parseBoolean(activityCheck);
        }

        String lastAccessAtStart = System.getProperty(
                "org.apache.catalina.session.StandardSession.LAST_ACCESS_AT_START");
        if (lastAccessAtStart == null) {
            LAST_ACCESS_AT_START = STRICT_SERVLET_COMPLIANCE;
        } else {
            LAST_ACCESS_AT_START = Boolean.parseBoolean(lastAccessAtStart);
        }
    }


    // ----------------------------------------------------------- Constructors


    /**
     * @param manager The manager with which this Session is associated
     */
    public StandardSession(Manager manager) {

        super();
        this.manager = manager;

        // Initialize access count
        if (ACTIVITY_CHECK) {
            accessCount = new AtomicInteger();
        }
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 类型数组.
     */
    protected static final String EMPTY_ARRAY[] = new String[0];


    /**
     * 用户数据属性集合.
     */
    protected ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();


    /**
     * 用于验证缓存Principal的身份验证类型. 
     * NOTE: 此值不包含在该对象的序列化版本中.
     */
    protected transient String authType = null;


    /**
     * 创建会话的时间, 午夜以来的毫秒,
     * January 1, 1970 GMT.
     */
    protected long creationTime = 0L;


    /**
     * 目前正在处理的会话过期, 所以绕过某些 IllegalStateException 测试. NOTE: 此值不包含在该对象的序列化版本中.
     */
    protected transient volatile boolean expiring = false;


    /**
     * 这个session的外观模式.
     * NOTE: 此值不包含在该对象的序列化版本中.
     */
    protected transient StandardSessionFacade facade = null;


    /**
     * 这个Session的会话标识符.
     */
    protected String id = null;


    /**
     * 此会话的最后一次访问时间.
     */
    protected volatile long lastAccessedTime = creationTime;


    /**
     * 会话事件监听器.
     */
    protected transient ArrayList<SessionListener> listeners = new ArrayList<>();


    /**
     * 关联的Manager
     */
    protected transient Manager manager = null;


    /**
     * 最大时间间隔, in seconds, 在servlet容器可能使该会话无效之前，客户端请求之间. 
     * 负值表示会话不应该超时.
     */
    protected volatile int maxInactiveInterval = -1;


    /**
     * 这个会话是不是新的.
     */
    protected volatile boolean isNew = false;


    /**
     * 此会话有效与否.
     */
    protected volatile boolean isValid = false;


    /**
     * 内部注释.  <b>IMPLEMENTATION NOTE:</b> 这个对象不是保存和恢复整个会话序列!
     */
    protected transient Map<String, Object> notes = new Hashtable<>();


    /**
     * 认证过的 Principal.
     * <b>IMPLEMENTATION NOTE:</b> 这个对象不是保存和恢复整个会话序列!
     */
    protected transient Principal principal = null;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(StandardSession.class);


    /**
     * 属性修改支持. 
     * NOTE: 此值不包含在该对象的序列化版本中.
     */
    protected final transient PropertyChangeSupport support =
        new PropertyChangeSupport(this);


    /**
     * 这个会话的当前访问时间.
     */
    protected volatile long thisAccessedTime = creationTime;


    /**
     * 此会话的访问计数.
     */
    protected transient AtomicInteger accessCount = null;


    // ----------------------------------------------------- Session Properties


    /**
     * 返回用于验证缓存Principal的身份验证类型.
     */
    @Override
    public String getAuthType() {
        return (this.authType);
    }


    /**
     * 设置用于验证缓存Principal的身份验证类型.
     *
     * @param authType 缓存的验证类型
     */
    @Override
    public void setAuthType(String authType) {
        String oldAuthType = this.authType;
        this.authType = authType;
        support.firePropertyChange("authType", oldAuthType, this.authType);
    }


    /**
     * 设置此会话的创建时间. 
     * 当现有会话实例被重用时, 这个方法被Manager调用.
     *
     * @param time The new creation time
     */
    @Override
    public void setCreationTime(long time) {
        this.creationTime = time;
        this.lastAccessedTime = time;
        this.thisAccessedTime = time;
    }


    /**
     * 返回会话标识符.
     */
    @Override
    public String getId() {
        return (this.id);
    }


    /**
     * 返回会话标识符.
     */
    @Override
    public String getIdInternal() {
        return (this.id);
    }


    /**
     * 设置会话标识符.
     *
     * @param id 会话标识符
     */
    @Override
    public void setId(String id) {
        setId(id, true);
    }


    @Override
    public void setId(String id, boolean notify) {

        if ((this.id != null) && (manager != null))
            manager.remove(this);

        this.id = id;

        if (manager != null)
            manager.add(this);

        if (notify) {
            tellNew();
        }
    }


    /**
     * 通知监听器有关新会话的情况.
     */
    public void tellNew() {

        // Notify interested session event listeners
        fireSessionEvent(Session.SESSION_CREATED_EVENT, null);

        // Notify interested application event listeners
        Context context = manager.getContext();
        Object listeners[] = context.getApplicationLifecycleListeners();
        if (listeners != null && listeners.length > 0) {
            HttpSessionEvent event =
                new HttpSessionEvent(getSession());
            for (int i = 0; i < listeners.length; i++) {
                if (!(listeners[i] instanceof HttpSessionListener))
                    continue;
                HttpSessionListener listener =
                    (HttpSessionListener) listeners[i];
                try {
                    context.fireContainerEvent("beforeSessionCreated",
                            listener);
                    listener.sessionCreated(event);
                    context.fireContainerEvent("afterSessionCreated", listener);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    try {
                        context.fireContainerEvent("afterSessionCreated",
                                listener);
                    } catch (Exception e) {
                        // Ignore
                    }
                    manager.getContext().getLogger().error
                        (sm.getString("standardSession.sessionEvent"), t);
                }
            }
        }

    }

    /**
     * 通知监听器关于会话ID的修改.
     *
     * @param newId  new session ID
     * @param oldId  old session ID
     * @param notifySessionListeners  是否应通知任何关联的SessionListener会话ID已更改?
     * @param notifyContainerListeners  是否应通知任何关联的ContainerListener会话ID已更改?
     */
    @Override
    public void tellChangedSessionId(String newId, String oldId,
            boolean notifySessionListeners, boolean notifyContainerListeners) {
        Context context = manager.getContext();
         // notify ContainerListeners
        if (notifyContainerListeners) {
            context.fireContainerEvent(Context.CHANGE_SESSION_ID_EVENT,
                    new String[] {oldId, newId});
        }

        // notify HttpSessionIdListener
        if (notifySessionListeners) {
            Object listeners[] = context.getApplicationEventListeners();
            if (listeners != null && listeners.length > 0) {
                HttpSessionEvent event =
                    new HttpSessionEvent(getSession());

                for(Object listener : listeners) {
                    if (!(listener instanceof HttpSessionIdListener))
                        continue;

                    HttpSessionIdListener idListener =
                        (HttpSessionIdListener)listener;
                    try {
                        idListener.sessionIdChanged(event, oldId);
                    } catch (Throwable t) {
                        manager.getContext().getLogger().error
                            (sm.getString("standardSession.sessionEvent"), t);
                    }
                }
            }
        }
    }


    /**
     * 返回客户端发送请求的最后一次时间, 从午夜起的毫秒数, January 1, 1970
     * GMT.  应用程序所采取的操作, 比如获取或设置一个值, 不影响访问时间.
     * 每当请求开始时，这个更新.
     */
    @Override
    public long getThisAccessedTime() {

        if (!isValidInternal()) {
            throw new IllegalStateException
                (sm.getString("standardSession.getThisAccessedTime.ise"));
        }

        return (this.thisAccessedTime);
    }

    /**
     * 返回最后的客户端访问时间, 不包括失效检查
     */
    @Override
    public long getThisAccessedTimeInternal() {
        return (this.thisAccessedTime);
    }

    /**
     * 返回客户端发送请求的最后一次时间, 从午夜起的毫秒数, January 1, 1970
     * GMT.  应用程序所采取的操作, 比如获取或设置一个值, 不影响访问时间.
     * 每当请求完成时，这个更新.
     */
    @Override
    public long getLastAccessedTime() {

        if (!isValidInternal()) {
            throw new IllegalStateException
                (sm.getString("standardSession.getLastAccessedTime.ise"));
        }

        return (this.lastAccessedTime);
    }

    /**
     * 返回最后的客户端访问时间, 不包括失效检查
     */
    @Override
    public long getLastAccessedTimeInternal() {
        return (this.lastAccessedTime);
    }

    /**
     * 返回从上次客户端访问时间以来的空闲时间(in milliseconds).
     */
    @Override
    public long getIdleTime() {

        if (!isValidInternal()) {
            throw new IllegalStateException
                (sm.getString("standardSession.getIdleTime.ise"));
        }

        return getIdleTimeInternal();
    }

    /**
     * 返回从上次客户端访问时间以来的空闲时间, 不包括有效性检查
     */
    @Override
    public long getIdleTimeInternal() {
        long timeNow = System.currentTimeMillis();
        long timeIdle;
        if (LAST_ACCESS_AT_START) {
            timeIdle = timeNow - lastAccessedTime;
        } else {
            timeIdle = timeNow - thisAccessedTime;
        }
        return timeIdle;
    }

    /**
     * 返回其中会话有效的Manager.
     */
    @Override
    public Manager getManager() {
        return this.manager;
    }


    /**
     * 设置其中会话有效的Manager.
     *
     * @param manager The new Manager
     */
    @Override
    public void setManager(Manager manager) {
        this.manager = manager;
    }


    /**
     * 返回最大时间间隔, in seconds, 在servlet容器将使会话无效之前，客户端请求之间.
     * 负值表示会话不应该超时.
     */
    @Override
    public int getMaxInactiveInterval() {
        return (this.maxInactiveInterval);
    }


    /**
     * 设置最大时间间隔, in seconds, 在servlet容器将使会话无效之前，客户端请求之间.
     * 负值表示会话不应该超时.
     *
     * @param interval The new maximum interval
     */
    @Override
    public void setMaxInactiveInterval(int interval) {
        this.maxInactiveInterval = interval;
    }


    /**
     * Set the <code>isNew</code> flag for this session.
     *
     * @param isNew The new value for the <code>isNew</code> flag
     */
    @Override
    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }


    /**
     * 返回已认证的Principal.
     * 提供了一个<code>Authenticator</code>缓存先前已验证过的Principal的方法, 
     * 避免潜在的每个请求的 <code>Realm.authenticate()</code>调用.
     * 如果没有关联的Principal, 返回<code>null</code>.
     */
    @Override
    public Principal getPrincipal() {
        return (this.principal);
    }


    /**
     * 设置已认证的Principal.
     * 提供了一个<code>Authenticator</code>缓存先前已验证过的Principal的方法, 
     * 避免潜在的每个请求的 <code>Realm.authenticate()</code>调用.
     * 如果没有关联的Principal, 返回<code>null</code>.
     *
     * @param principal The new Principal, or <code>null</code> if none
     */
    @Override
    public void setPrincipal(Principal principal) {

        Principal oldPrincipal = this.principal;
        this.principal = principal;
        support.firePropertyChange("principal", oldPrincipal, this.principal);

    }


    /**
     * 返回代理的<code>HttpSession</code>.
     */
    @Override
    public HttpSession getSession() {

        if (facade == null){
            if (SecurityUtil.isPackageProtectionEnabled()){
                final StandardSession fsession = this;
                facade = AccessController.doPrivileged(
                        new PrivilegedAction<StandardSessionFacade>(){
                    @Override
                    public StandardSessionFacade run(){
                        return new StandardSessionFacade(fsession);
                    }
                });
            } else {
                facade = new StandardSessionFacade(this);
            }
        }
        return (facade);

    }


    /**
     * Return the <code>isValid</code> flag for this session.
     */
    @Override
    public boolean isValid() {

        if (!this.isValid) {
            return false;
        }

        if (this.expiring) {
            return true;
        }

        if (ACTIVITY_CHECK && accessCount.get() > 0) {
            return true;
        }

        if (maxInactiveInterval > 0) {
            int timeIdle = (int) (getIdleTimeInternal() / 1000L);
            if (timeIdle >= maxInactiveInterval) {
                expire(true);
            }
        }

        return this.isValid;
    }


    /**
     * Set the <code>isValid</code> flag for this session.
     *
     * @param isValid The new value for the <code>isValid</code> flag
     */
    @Override
    public void setValid(boolean isValid) {
        this.isValid = isValid;
    }


    // ------------------------------------------------- Session Public Methods


    /**
     * 更新访问的时间信息. 当一个请求进入某个特定会话时，该方法应该由上下文调用, 即使应用程序不引用它.
     */
    @Override
    public void access() {
        this.thisAccessedTime = System.currentTimeMillis();

        if (ACTIVITY_CHECK) {
            accessCount.incrementAndGet();
        }
    }


    /**
     * 结束访问
     */
    @Override
    public void endAccess() {

        isNew = false;

        /**
         * servlet规范要求忽略lastAccessedTime中的请求处理时间.
         */
        if (LAST_ACCESS_AT_START) {
            this.lastAccessedTime = this.thisAccessedTime;
            this.thisAccessedTime = System.currentTimeMillis();
        } else {
            this.thisAccessedTime = System.currentTimeMillis();
            this.lastAccessedTime = this.thisAccessedTime;
        }

        if (ACTIVITY_CHECK) {
            accessCount.decrementAndGet();
        }

    }


    /**
     * 添加会话事件监听器.
     */
    @Override
    public void addSessionListener(SessionListener listener) {
        listeners.add(listener);
    }


    /**
     * 执行使会话无效的内部处理, 如果会话已经过期，则不触发异常.
     */
    @Override
    public void expire() {
        expire(true);
    }


    /**
     * 执行使会话无效的内部处理, 如果会话已经过期，则不触发异常.
     *
     * @param notify 应该通知监听器这个会话的死亡?
     */
    public void expire(boolean notify) {

        // 检查会话是否已失效.
        // 在期满时不要检查过期，因为过期不能返回, 直到 isValid 是 false
        if (!isValid)
            return;

        synchronized (this) {
            // 再次检查, 现在在同步中，所以这个代码只运行一次
            // 双重检查锁 - isValid 需要是 volatile
            // The check of expiring is to ensure that an infinite loop is not
            // entered as per bug 56339
            if (expiring || !isValid)
                return;

            if (manager == null)
                return;

            // Mark this session as "being expired"
            expiring = true;

            // 通知相关的应用事件监听器
            // FIXME - Assumes we call listeners in reverse order
            Context context = manager.getContext();

            // 调用 expire() 可能没有被WebApp触发.
            // 确保在调用监听器时, 设置WebApp的类加载器
            if (notify) {
                ClassLoader oldContextClassLoader = null;
                try {
                    oldContextClassLoader = context.bind(Globals.IS_SECURITY_ENABLED, null);
                    Object listeners[] = context.getApplicationLifecycleListeners();
                    if (listeners != null && listeners.length > 0) {
                        HttpSessionEvent event =
                            new HttpSessionEvent(getSession());
                        for (int i = 0; i < listeners.length; i++) {
                            int j = (listeners.length - 1) - i;
                            if (!(listeners[j] instanceof HttpSessionListener))
                                continue;
                            HttpSessionListener listener =
                                (HttpSessionListener) listeners[j];
                            try {
                                context.fireContainerEvent("beforeSessionDestroyed",
                                        listener);
                                listener.sessionDestroyed(event);
                                context.fireContainerEvent("afterSessionDestroyed",
                                        listener);
                            } catch (Throwable t) {
                                ExceptionUtils.handleThrowable(t);
                                try {
                                    context.fireContainerEvent(
                                            "afterSessionDestroyed", listener);
                                } catch (Exception e) {
                                    // Ignore
                                }
                                manager.getContext().getLogger().error
                                    (sm.getString("standardSession.sessionEvent"), t);
                            }
                        }
                    }
                } finally {
                    context.unbind(Globals.IS_SECURITY_ENABLED, oldContextClassLoader);
                }
            }

            if (ACTIVITY_CHECK) {
                accessCount.set(0);
            }

            // 从管理器的活动会话中删除此会话
            manager.remove(this, true);

            // 通知相关的会话事件监听器
            if (notify) {
                fireSessionEvent(Session.SESSION_DESTROYED_EVENT, null);
            }

            // 调用注销方法
            if (principal instanceof TomcatPrincipal) {
                TomcatPrincipal gp = (TomcatPrincipal) principal;
                try {
                    gp.logout();
                } catch (Exception e) {
                    manager.getContext().getLogger().error(
                            sm.getString("standardSession.logoutfail"),
                            e);
                }
            }

            // 会话已过期
            setValid(false);
            expiring = false;

            // 解绑这个会话关联的对象
            String keys[] = keys();
            ClassLoader oldContextClassLoader = null;
            try {
                oldContextClassLoader = context.bind(Globals.IS_SECURITY_ENABLED, null);
                for (int i = 0; i < keys.length; i++) {
                    removeAttributeInternal(keys[i], notify);
                }
            } finally {
                context.unbind(Globals.IS_SECURITY_ENABLED, oldContextClassLoader);
            }
        }
    }


    /**
     * 执行所需的钝化.
     */
    public void passivate() {

        // 通知相关会话事件监听器
        fireSessionEvent(Session.SESSION_PASSIVATED_EVENT, null);

        // Notify ActivationListeners
        HttpSessionEvent event = null;
        String keys[] = keys();
        for (int i = 0; i < keys.length; i++) {
            Object attribute = attributes.get(keys[i]);
            if (attribute instanceof HttpSessionActivationListener) {
                if (event == null)
                    event = new HttpSessionEvent(getSession());
                try {
                    ((HttpSessionActivationListener)attribute)
                        .sessionWillPassivate(event);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    manager.getContext().getLogger().error
                        (sm.getString("standardSession.attributeEvent"), t);
                }
            }
        }
    }


    /**
     * 执行激活此会话所需的内部处理.
     */
    public void activate() {

        // Initialize access count
        if (ACTIVITY_CHECK) {
            accessCount = new AtomicInteger();
        }

        // 通知相关会话事件监听器
        fireSessionEvent(Session.SESSION_ACTIVATED_EVENT, null);

        // Notify ActivationListeners
        HttpSessionEvent event = null;
        String keys[] = keys();
        for (int i = 0; i < keys.length; i++) {
            Object attribute = attributes.get(keys[i]);
            if (attribute instanceof HttpSessionActivationListener) {
                if (event == null)
                    event = new HttpSessionEvent(getSession());
                try {
                    ((HttpSessionActivationListener)attribute)
                        .sessionDidActivate(event);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    manager.getContext().getLogger().error
                        (sm.getString("standardSession.attributeEvent"), t);
                }
            }
        }

    }


    /**
     * 将指定名称绑定的对象返回给此会话的内部注释, 或者<code>null</code>.
     *
     * @param name Name of the note to be returned
     */
    @Override
    public Object getNote(String name) {
        return (notes.get(name));
    }


    /**
     * 返回此会话存在的所有Notes绑定的字符串名称的迭代器.
     */
    @Override
    public Iterator<String> getNoteNames() {
        return (notes.keySet().iterator());
    }


    /**
     * 释放所有对象引用, 初始化实例变量, 准备重用这个对象.
     */
    @Override
    public void recycle() {

        // 重置关联的实际变量
        attributes.clear();
        setAuthType(null);
        creationTime = 0L;
        expiring = false;
        id = null;
        lastAccessedTime = 0L;
        maxInactiveInterval = -1;
        notes.clear();
        setPrincipal(null);
        isNew = false;
        isValid = false;
        manager = null;

    }


    /**
     * 删除在内部注释中绑定到指定名称的任何对象.
     *
     * @param name Name of the note to be removed
     */
    @Override
    public void removeNote(String name) {
        notes.remove(name);
    }


    /**
     * 移除会话事件监听器.
     */
    @Override
    public void removeSessionListener(SessionListener listener) {
        listeners.remove(listener);
    }


    /**
     * 将对象绑定到内部注释中指定的名称, 替换此名称的任何现有绑定.
     *
     * @param name Name to which the object should be bound
     * @param value Object to be bound to the specified name
     */
    @Override
    public void setNote(String name, Object value) {
        notes.put(name, value);
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("StandardSession[");
        sb.append(id);
        sb.append("]");
        return (sb.toString());
    }


    // ------------------------------------------------ Session Package Methods


    /**
     * 从指定的对象输入流中读取该会话对象的内容的序列化版本, StandardSession本身已序列化.
     *
     * @param stream The object input stream to read from
     *
     * @exception ClassNotFoundException if an unknown class is specified
     * @exception IOException if an input/output error occurs
     */
    public void readObjectData(ObjectInputStream stream)
        throws ClassNotFoundException, IOException {
        doReadObject(stream);
    }


    /**
     * 将该会话对象的内容的序列化版本写入指定的对象输出流, StandardSession本身已序列化.
     *
     * @param stream The object output stream to write to
     *
     * @exception IOException if an input/output error occurs
     */
    public void writeObjectData(ObjectOutputStream stream)
        throws IOException {
        doWriteObject(stream);
    }


    // ------------------------------------------------- HttpSession Properties


    /**
     * 返回此会话创建时的时间, 午夜以来的毫秒, January 1, 1970 GMT.
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    @Override
    public long getCreationTime() {

        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.getCreationTime.ise"));

        return (this.creationTime);

    }


    /**
     * 返回此会话创建时的时间, 午夜以来的毫秒, January 1, 1970 GMT, 绕过会话验证检查.
     */
    @Override
    public long getCreationTimeInternal() {
        return this.creationTime;
    }


    /**
     * 返回所属的ServletContext.
     */
    @Override
    public ServletContext getServletContext() {
        if (manager == null) {
            return null;
        }
        Context context = manager.getContext();
        return context.getServletContext();
    }


    // ----------------------------------------------HttpSession Public Methods


    /**
     * 返回指定名称的属性或<code>null</code>.
     *
     * @param name Name of the attribute to be returned
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    @Override
    public Object getAttribute(String name) {

        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.getAttribute.ise"));

        if (name == null) return null;

        return (attributes.get(name));

    }


    /**
     * 返回所有属性的名称的枚举.
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    @Override
    public Enumeration<String> getAttributeNames() {

        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.getAttributeNames.ise"));

        Set<String> names = new HashSet<>();
        names.addAll(attributes.keySet());
        return Collections.enumeration(names);
    }


    /**
     * 返回指定名称的值, 或<code>null</code>.
     *
     * @param name Name of the value to be returned
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     *
     * @deprecated As of Version 2.2, this method is replaced by
     *  <code>getAttribute()</code>
     */
    @Override
    @Deprecated
    public Object getValue(String name) {

        return (getAttribute(name));

    }


    /**
     * 返回所有属性的名称. 如果没有, 返回零长度数组.
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     *
     * @deprecated As of Version 2.2, this method is replaced by
     *  <code>getAttributeNames()</code>
     */
    @Override
    @Deprecated
    public String[] getValueNames() {

        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.getValueNames.ise"));

        return (keys());

    }


    /**
     * 使会话无效并解绑所有对象.
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    @Override
    public void invalidate() {

        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.invalidate.ise"));

        // Cause this session to expire
        expire();

    }


    /**
     * 返回<code>true</code>，如果客户端还不知道会话, 或者如果客户端选择不加入会话.
     * 例如, 如果服务器只使用基于cookie的会话, 客户端禁用了cookie的使用, 然后每个请求都会有一个会话.
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    @Override
    public boolean isNew() {

        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.isNew.ise"));

        return (this.isNew);

    }


    /**
     * 设置属性
     * <p>
     * After this method executes, and if the object implements
     * <code>HttpSessionBindingListener</code>, the container calls
     * <code>valueBound()</code> on the object.
     *
     * @param name Name to which the object is bound, cannot be null
     * @param value Object to be bound, cannot be null
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     *
     * @deprecated As of Version 2.2, this method is replaced by
     *  <code>setAttribute()</code>
     */
    @Override
    @Deprecated
    public void putValue(String name, Object value) {
        setAttribute(name, value);
    }


    /**
     * 删除指定名称的属性. 如果属性不存在, 什么都不做.
     * <p>
     * 这个方法执行之后, 如果对象实现了
     * <code>HttpSessionBindingListener</code>, 容器将调用这个对象的
     * <code>valueUnbound()</code>方法.
     *
     * @param name Name of the object to remove from this session.
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    @Override
    public void removeAttribute(String name) {
        removeAttribute(name, true);
    }


    /**
     * 删除指定名称的属性. 如果属性不存在, 什么都不做.
     * <p>
     * 这个方法执行之后, 如果对象实现了
     * <code>HttpSessionBindingListener</code>, 容器将调用这个对象的
     * <code>valueUnbound()</code>方法.
     *
     * @param name Name of the object to remove from this session.
     * @param notify 是否通知内部监听器?
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public void removeAttribute(String name, boolean notify) {

        // Validate our current state
        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.removeAttribute.ise"));

        removeAttributeInternal(name, notify);

    }


    /**
     * 移除指定名称的属性. 如果属性不存在, 什么都不做.
     * <p>
     * 这个方法执行之后, 如果对象实现了
     * <code>HttpSessionBindingListener</code>, 容器将调用这个对象的
     * <code>valueUnbound()</code>方法.
     *
     * @param name Name of the object to remove from this session.
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     *
     * @deprecated As of Version 2.2, this method is replaced by
     *  <code>removeAttribute()</code>
     */
    @Override
    @Deprecated
    public void removeValue(String name) {
        removeAttribute(name);
    }


    /**
     * 设置指定名称的值. 
     * <p>
     * 这个方法执行之后, 如果对象实现了
     * <code>HttpSessionBindingListener</code>, 容器将调用这个对象的
     * <code>valueBound()</code>方法.
     *
     * @param name Name to which the object is bound, cannot be null
     * @param value Object to be bound, cannot be null
     *
     * @exception IllegalArgumentException 如果尝试添加一个非可序列化的对象在一个可分配的环境中.
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    @Override
    public void setAttribute(String name, Object value) {
        setAttribute(name,value,true);
    }
    
    /**
     * 设置指定名称的值. 
     * <p>
     * 这个方法执行之后, 如果对象实现了
     * <code>HttpSessionBindingListener</code>, 容器将调用这个对象的
     * <code>valueBound()</code>方法.
     *
     * @param name Name to which the object is bound, cannot be null
     * @param value Object to be bound, cannot be null
     * @param notify 是否通知会话监听器
     * 
     * @exception IllegalArgumentException 如果尝试添加一个非可序列化的对象在一个可分配的环境中.
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public void setAttribute(String name, Object value, boolean notify) {

        // Name cannot be null
        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("standardSession.setAttribute.namenull"));

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        // Validate our current state
        if (!isValidInternal()) {
            throw new IllegalStateException(sm.getString(
                    "standardSession.setAttribute.ise", getIdInternal()));
        }
        if ((manager != null) && manager.getContext().getDistributable() &&
                !isAttributeDistributable(name, value) && !exclude(name, value)) {
            throw new IllegalArgumentException(sm.getString(
                    "standardSession.setAttribute.iae", name));
        }
        // Construct an event with the new value
        HttpSessionBindingEvent event = null;

        // Call the valueBound() method if necessary
        if (notify && value instanceof HttpSessionBindingListener) {
            // Don't call any notification if replacing with the same value
            Object oldValue = attributes.get(name);
            if (value != oldValue) {
                event = new HttpSessionBindingEvent(getSession(), name, value);
                try {
                    ((HttpSessionBindingListener) value).valueBound(event);
                } catch (Throwable t){
                    manager.getContext().getLogger().error
                    (sm.getString("standardSession.bindingEvent"), t);
                }
            }
        }

        // Replace or add this attribute
        Object unbound = attributes.put(name, value);

        // Call the valueUnbound() method if necessary
        if (notify && (unbound != null) && (unbound != value) &&
            (unbound instanceof HttpSessionBindingListener)) {
            try {
                ((HttpSessionBindingListener) unbound).valueUnbound
                    (new HttpSessionBindingEvent(getSession(), name));
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                manager.getContext().getLogger().error
                    (sm.getString("standardSession.bindingEvent"), t);
            }
        }

        if ( !notify ) return;

        // Notify interested application event listeners
        Context context = manager.getContext();
        Object listeners[] = context.getApplicationEventListeners();
        if (listeners == null)
            return;
        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof HttpSessionAttributeListener))
                continue;
            HttpSessionAttributeListener listener =
                (HttpSessionAttributeListener) listeners[i];
            try {
                if (unbound != null) {
                    context.fireContainerEvent("beforeSessionAttributeReplaced",
                            listener);
                    if (event == null) {
                        event = new HttpSessionBindingEvent
                            (getSession(), name, unbound);
                    }
                    listener.attributeReplaced(event);
                    context.fireContainerEvent("afterSessionAttributeReplaced",
                            listener);
                } else {
                    context.fireContainerEvent("beforeSessionAttributeAdded",
                            listener);
                    if (event == null) {
                        event = new HttpSessionBindingEvent
                            (getSession(), name, value);
                    }
                    listener.attributeAdded(event);
                    context.fireContainerEvent("afterSessionAttributeAdded",
                            listener);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                try {
                    if (unbound != null) {
                        context.fireContainerEvent(
                                "afterSessionAttributeReplaced", listener);
                    } else {
                        context.fireContainerEvent("afterSessionAttributeAdded",
                                listener);
                    }
                } catch (Exception e) {
                    // Ignore
                }
                manager.getContext().getLogger().error
                    (sm.getString("standardSession.attributeEvent"), t);
            }
        }
    }


    // ------------------------------------------ HttpSession Protected Methods


    /**
     * @return 会话的<code>isValid</code>标志, 没有任何过期检查.
     */
    protected boolean isValidInternal() {
        return this.isValid;
    }

    /**
     * 此实现简单地检查可序列化的值.
     * 子类可能使用不基于序列化的其他分发技术，并且可以重写该检查.
     */
    @Override
    public boolean isAttributeDistributable(String name, Object value) {
        return value instanceof Serializable;
    }


    /**
     * 从指定的对象输入流中读取此会话对象的序列化版本.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>: 此方法没有恢复对所属Manager的引用 , 必须明确设置.
     *
     * @param stream 要从中读取的输入流
     *
     * @exception ClassNotFoundException 如果指定了未知类
     * @exception IOException 如果发生输入/输出错误
     */
    protected void doReadObject(ObjectInputStream stream)
        throws ClassNotFoundException, IOException {

        // Deserialize the scalar instance variables (except Manager)
        authType = null;        // Transient only
        creationTime = ((Long) stream.readObject()).longValue();
        lastAccessedTime = ((Long) stream.readObject()).longValue();
        maxInactiveInterval = ((Integer) stream.readObject()).intValue();
        isNew = ((Boolean) stream.readObject()).booleanValue();
        isValid = ((Boolean) stream.readObject()).booleanValue();
        thisAccessedTime = ((Long) stream.readObject()).longValue();
        principal = null;        // Transient only
        //        setId((String) stream.readObject());
        id = (String) stream.readObject();
        if (manager.getContext().getLogger().isDebugEnabled())
            manager.getContext().getLogger().debug
                ("readObject() loading session " + id);

        // Deserialize the attribute count and attribute values
        if (attributes == null)
            attributes = new ConcurrentHashMap<>();
        int n = ((Integer) stream.readObject()).intValue();
        boolean isValidSave = isValid;
        isValid = true;
        for (int i = 0; i < n; i++) {
            String name = (String) stream.readObject();
            final Object value;
            try {
                value = stream.readObject();
            } catch (WriteAbortedException wae) {
                if (wae.getCause() instanceof NotSerializableException) {
                    String msg = sm.getString("standardSession.notDeserializable", name, id);
                    if (manager.getContext().getLogger().isDebugEnabled()) {
                        manager.getContext().getLogger().debug(msg, wae);
                    } else {
                        manager.getContext().getLogger().warn(msg);
                    }
                    // Skip non serializable attributes
                    continue;
                }
                throw wae;
            }
            if (manager.getContext().getLogger().isDebugEnabled())
                manager.getContext().getLogger().debug("  loading attribute '" + name +
                    "' with value '" + value + "'");
            // Handle the case where the filter configuration was changed while
            // the web application was stopped.
            if (exclude(name, value)) {
                continue;
            }
            attributes.put(name, value);
        }
        isValid = isValidSave;

        if (listeners == null) {
            listeners = new ArrayList<>();
        }

        if (notes == null) {
            notes = new Hashtable<>();
        }
    }


    /**
     * 将这个会话对象的序列化版本写入指定的对象输出流.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>: 所属Manager不会存储在这个会话的序列化表示中. 
     * 调用<code>readObject()</code>方法之后, 必须显式地设置关联的Manager .
     * <p>
     * <b>IMPLEMENTATION NOTE</b>: 任何属性，不可序列化将从会话中解绑, 适当的行动，如果它实现了HttpSessionBindingListener. 
     * 如果您不想要任何这样的属性, 确保<code>distributable</code>属性被设置为<code>true</code>.
     *
     * @param stream 要写入的输出流
     *
     * @exception IOException 如果发生输入/输出错误
     */
    protected void doWriteObject(ObjectOutputStream stream) throws IOException {

        // Write the scalar instance variables (except Manager)
        stream.writeObject(Long.valueOf(creationTime));
        stream.writeObject(Long.valueOf(lastAccessedTime));
        stream.writeObject(Integer.valueOf(maxInactiveInterval));
        stream.writeObject(Boolean.valueOf(isNew));
        stream.writeObject(Boolean.valueOf(isValid));
        stream.writeObject(Long.valueOf(thisAccessedTime));
        stream.writeObject(id);
        if (manager.getContext().getLogger().isDebugEnabled())
            manager.getContext().getLogger().debug
                ("writeObject() storing session " + id);

        // Accumulate the names of serializable and non-serializable attributes
        String keys[] = keys();
        ArrayList<String> saveNames = new ArrayList<>();
        ArrayList<Object> saveValues = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            Object value = attributes.get(keys[i]);
            if (value == null) {
                continue;
            } else if (isAttributeDistributable(keys[i], value) && !exclude(keys[i], value)) {
                saveNames.add(keys[i]);
                saveValues.add(value);
            } else {
                removeAttributeInternal(keys[i], true);
            }
        }

        // Serialize the attribute count and the Serializable attributes
        int n = saveNames.size();
        stream.writeObject(Integer.valueOf(n));
        for (int i = 0; i < n; i++) {
            stream.writeObject(saveNames.get(i));
            try {
                stream.writeObject(saveValues.get(i));
                if (manager.getContext().getLogger().isDebugEnabled())
                    manager.getContext().getLogger().debug(
                            "  storing attribute '" + saveNames.get(i) + "' with value '" + saveValues.get(i) + "'");
            } catch (NotSerializableException e) {
                manager.getContext().getLogger().warn(
                        sm.getString("standardSession.notSerializable", saveNames.get(i), id), e);
            }
        }

    }


    /**
     * 是否应排除给定会话属性? 这个实现检查:
     * <ul>
     * <li>{@link Constants#excludedAttributeNames}</li>
     * <li>{@link Manager#willAttributeDistribute(String, Object)}</li>
     * </ul>
     * Note: 这种方法故意不检查
     *       {@link #isAttributeDistributable(String, Object)}, 分开以支持{@link #setAttribute(String, Object, boolean)}所需的检查
     *
     * @param name  The attribute name
     * @param value The attribute value
     *
     * @return {@code true} 如果属性应该从分布中排除, 否则{@code false}
     */
    protected boolean exclude(String name, Object value) {
        if (Constants.excludedAttributeNames.contains(name)) {
            return true;
        }

        // Manager is required for remaining check
        Manager manager = getManager();
        if (manager == null) {
            // Manager may be null during replication of new sessions in a
            // cluster. Avoid the NPE.
            return false;
        }

        // Last check so use a short-cut
        return !manager.willAttributeDistribute(name, value);
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * 通知所有会话事件监听器这个会话发生了一个特殊事件.
     * 默认实现使用调用线程同步执行此通知.
     *
     * @param type Event type
     * @param data Event data
     */
    public void fireSessionEvent(String type, Object data) {
        if (listeners.size() < 1)
            return;
        SessionEvent event = new SessionEvent(this, type, data);
        SessionListener list[] = new SessionListener[0];
        synchronized (listeners) {
            list = listeners.toArray(list);
        }

        for (int i = 0; i < list.length; i++){
            (list[i]).sessionEvent(event);
        }

    }


    /**
     * @return 所有当前定义的会话属性的名称. 如果没有, 返回零长度数组.
     */
    protected String[] keys() {
        return attributes.keySet().toArray(EMPTY_ARRAY);
    }


    /**
     * 从该会话中删除具有指定名称的对象.  如果没有对应的对象, 什么都不做.
     * <p>
     * 这个方法执行之后, 如果对象实现了<code>HttpSessionBindingListener</code>, 容器调用对象上的<code>valueUnbound()</code>方法.
     *
     * @param name 要移除的对象的名称.
     * @param notify 是否通知监听器?
     */
    protected void removeAttributeInternal(String name, boolean notify) {

        // Avoid NPE
        if (name == null) return;

        // Remove this attribute from our collection
        Object value = attributes.remove(name);

        // Do we need to do valueUnbound() and attributeRemoved() notification?
        if (!notify || (value == null)) {
            return;
        }

        // Call the valueUnbound() method if necessary
        HttpSessionBindingEvent event = null;
        if (value instanceof HttpSessionBindingListener) {
            event = new HttpSessionBindingEvent(getSession(), name, value);
            ((HttpSessionBindingListener) value).valueUnbound(event);
        }

        // Notify interested application event listeners
        Context context = manager.getContext();
        Object listeners[] = context.getApplicationEventListeners();
        if (listeners == null)
            return;
        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof HttpSessionAttributeListener))
                continue;
            HttpSessionAttributeListener listener =
                (HttpSessionAttributeListener) listeners[i];
            try {
                context.fireContainerEvent("beforeSessionAttributeRemoved",
                        listener);
                if (event == null) {
                    event = new HttpSessionBindingEvent
                        (getSession(), name, value);
                }
                listener.attributeRemoved(event);
                context.fireContainerEvent("afterSessionAttributeRemoved",
                        listener);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                try {
                    context.fireContainerEvent("afterSessionAttributeRemoved",
                            listener);
                } catch (Exception e) {
                    // Ignore
                }
                manager.getContext().getLogger().error
                    (sm.getString("standardSession.attributeEvent"), t);
            }
        }
    }
}
