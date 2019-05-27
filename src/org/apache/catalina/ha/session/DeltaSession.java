package org.apache.catalina.ha.session;

import java.io.Externalizable;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.WriteAbortedException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.catalina.Manager;
import org.apache.catalina.SessionListener;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.ha.ClusterSession;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.tribes.tipis.ReplicatedMapEntry;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * 类似于StandardSession, 除了该会话将在请求期间跟踪deltas.
 */
public class DeltaSession extends StandardSession implements Externalizable,ClusterSession,ReplicatedMapEntry {

    public static final Log log = LogFactory.getLog(DeltaSession.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(DeltaSession.class);

    // ----------------------------------------------------- Instance Variables

    /**
     * 只有主会话过期, 或因不活动而到期. 一旦在会话消息中通过该线接收此会话，设置为false.
     * 这意味着其他人在另一台服务器上提出了请求.
     */
    private transient boolean isPrimarySession = true;

    /**
     * Delta请求包含所有的动作信息
     */
    private transient DeltaRequest deltaRequest = null;

    /**
     * 上次复制会话的时间, 用于会话的分布式到期
     */
    private transient long lastTimeReplicated = System.currentTimeMillis();


    protected final Lock diffLock = new ReentrantReadWriteLock().writeLock();

    private long version;

    // ----------------------------------------------------------- Constructors

    public DeltaSession() {
        this(null);
    }

    /**
     * @param manager 这个Session关联的管理器
     */
    public DeltaSession(Manager manager) {
        super(manager);
        this.resetDeltaRequest();
    }

    // ----------------------------------------------------- ReplicatedMapEntry

    /**
     * 自从上次复制以来，对象是否已更改，并且未处于锁定状态
     */
    @Override
    public boolean isDirty() {
        return getDeltaRequest().getSize()>0;
    }

    /**
     * 如果返回 true, map将使用getDiff()提取不同之处. 否则，它将序列化整个对象.
     */
    @Override
    public boolean isDiffable() {
        return true;
    }

    /**
     * 返回差异之处
     * 
     * @return 差异的序列化视图
     * @throws IOException 序列化的IO错误
     */
    @Override
    public byte[] getDiff() throws IOException {
        lock();
        try {
            return getDeltaRequest().serialize();
        } finally{
            unlock();
        }
    }

    public ClassLoader[] getClassLoaders() {
        if (manager instanceof ClusterManagerBase) {
            return ((ClusterManagerBase)manager).getClassLoaders();
        } else if (manager instanceof ManagerBase) {
            ManagerBase mb = (ManagerBase)manager;
            return ClusterManagerBase.getClassLoaders(mb.getContext());
        }
        return null;
    }

    /**
     * 将差异应用于现有对象.
     * 
     * @param diff 序列化的差异数据
     * @param offset 数组偏移量
     * @param length 数组长度
     * 
     * @throws IOException 反序列化的IO错误
     */
    @Override
    public void applyDiff(byte[] diff, int offset, int length) throws IOException, ClassNotFoundException {
        lock();
        try (ObjectInputStream stream = ((ClusterManager) getManager()).getReplicationStream(diff, offset, length)) {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            try {
                ClassLoader[] loaders = getClassLoaders();
                if (loaders != null && loaders.length > 0)
                    Thread.currentThread().setContextClassLoader(loaders[0]);
                getDeltaRequest().readExternal(stream);
                getDeltaRequest().execute(this, ((ClusterManager)getManager()).isNotifyListenersOnReplication());
            } finally {
                Thread.currentThread().setContextClassLoader(contextLoader);
            }
        } finally {
            unlock();
        }
    }

    /**
     * 重置当前差异状态并重置dirty标志
     */
    @Override
    public void resetDiff() {
        resetDeltaRequest();
    }

    /**
     * 序列化过程中锁定
     */
    @Override
    public void lock() {
        diffLock.lock();
    }

    /**
     * 序列化之后解锁
     */
    @Override
    public void unlock() {
        diffLock.unlock();
    }

    @Override
    public void setOwner(Object owner) {
        if ( owner instanceof ClusterManager && getManager()==null) {
            ClusterManager cm = (ClusterManager)owner;
            this.setManager(cm);
            this.setValid(true);
            this.setPrimarySession(false);
            this.access();
            this.resetDeltaRequest();
            this.endAccess();
        }
    }

    /**
     * 如果返回 true, 复制已被访问的对象
     */
    @Override
    public boolean isAccessReplicate() {
        long replDelta = System.currentTimeMillis() - getLastTimeReplicated();
        if (maxInactiveInterval >=0 && replDelta > (maxInactiveInterval * 1000L)) {
            return true;
        }
        return false;
    }

    /**
     * 对现有对象的访问.
     */
    @Override
    public void accessEntry() {
        this.access();
        this.setPrimarySession(false);
        this.endAccess();
    }

    // ----------------------------------------------------- Session Properties

    /**
     * 返回 true, 如果此会话是主会话, 如果是这种情况, 管理器可以让它过期.
     */
    @Override
    public boolean isPrimarySession() {
        return isPrimarySession;
    }

    /**
     * 设置这是否是主会话.
     *
     * @param primarySession
     */
    @Override
    public void setPrimarySession(boolean primarySession) {
        this.isPrimarySession = primarySession;
    }


    @Override
    public void setId(String id, boolean notify) {
        super.setId(id, notify);
        resetDeltaRequest();
    }


    /**
     * 设置会话的会话标识符.
     *
     * @param id 会话标识符
     */
    @Override
    public void setId(String id) {
        super.setId(id, true);
        resetDeltaRequest();
    }


    @Override
    public void setMaxInactiveInterval(int interval) {
        this.setMaxInactiveInterval(interval,true);
    }


    public void setMaxInactiveInterval(int interval, boolean addDeltaRequest) {
        super.maxInactiveInterval = interval;
        if (addDeltaRequest && (deltaRequest != null)) {
            lock();
            try {
                deltaRequest.setMaxInactiveInterval(interval);
            } finally{
                unlock();
            }
        }
    }

    /**
     * @param isNew <code>isNew</code>标志值
     */
    @Override
    public void setNew(boolean isNew) {
        setNew(isNew, true);
    }

    public void setNew(boolean isNew, boolean addDeltaRequest) {
        super.setNew(isNew);
        if (addDeltaRequest && (deltaRequest != null)){
            lock();
            try {
                deltaRequest.setNew(isNew);
            } finally{
                unlock();
            }
        }
    }

    /**
     * 设置已验证的Principal和这个Session关联.
     * 提供一个<code>Authenticator</code>缓存以前已验证的Principal, 避免每个请求潜在的昂贵的<code>Realm.authenticate()</code>调用.
     *
     * @param principal 新的主题, 或<code>null</code>
     */
    @Override
    public void setPrincipal(Principal principal) {
        setPrincipal(principal, true);
    }

    public void setPrincipal(Principal principal, boolean addDeltaRequest) {
        lock();
        try {
            super.setPrincipal(principal);
            if (addDeltaRequest && (deltaRequest != null))
                deltaRequest.setPrincipal(principal);
        } finally {
            unlock();
        }
    }

    /**
     * 设置用于对缓存的Principal进行身份验证的验证类型.
     *
     * @param authType 缓存的身份验证类型
     */
    @Override
    public void setAuthType(String authType) {
        setAuthType(authType, true);
    }

    public void setAuthType(String authType, boolean addDeltaRequest) {
        lock();
        try {
            super.setAuthType(authType);
            if (addDeltaRequest && (deltaRequest != null))
                deltaRequest.setAuthType(authType);
        } finally {
            unlock();
        }
    }

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
            if (isPrimarySession()) {
                if (timeIdle >= maxInactiveInterval) {
                    expire(true);
                }
            } else {
                if (timeIdle >= (2 * maxInactiveInterval)) {
                    //如果会话空闲时间是允许的两倍, 主要的会话可能已经崩溃, 没有其他的请求进来.
                    // 这就是这样做的原因. 否则会有内存泄漏
                    expire(true, false);
                }
            }
        }
        return (this.isValid);
    }

    /**
     * 结束访问并注册到 ReplicationValve (支持crossContext)
     */
    @Override
    public void endAccess() {
        super.endAccess() ;
        if(manager instanceof ClusterManagerBase) {
            ((ClusterManagerBase)manager).registerSessionAtReplicationValve(this);
        }
    }

    // ------------------------------------------------- Session Public Methods

    /**
     * 执行使会话无效的内部处理, 如果会话已过期, 则不触发异常.
     *
     * @param notify 是否通知监听器?
     */
    @Override
    public void expire(boolean notify) {
        expire(notify, true);
    }

    public void expire(boolean notify, boolean notifyCluster) {

        // 检查会话是否已失效.
        // 不要检查过期，因为过期不能返回，直到isValid是 false
        if (!isValid)
            return;

        synchronized (this) {
            // 再次检查, 现在在同步中，所以这个代码只运行一次
            // 双重检查锁定 - isValid需要是 volatile
            if (!isValid)
                return;

            if (manager == null)
                return;

            String expiredId = getIdInternal();

            if(notifyCluster && expiredId != null &&
                    manager instanceof DeltaManager) {
                DeltaManager dmanager = (DeltaManager)manager;
                CatalinaCluster cluster = dmanager.getCluster();
                ClusterMessage msg = dmanager.requestCompleted(expiredId, true);
                if (msg != null) {
                    cluster.send(msg);
                }
            }

            super.expire(notify);

            if (notifyCluster) {
                if (log.isDebugEnabled())
                    log.debug(sm.getString("deltaSession.notifying",
                                           ((ClusterManager)manager).getName(),
                                           Boolean.valueOf(isPrimarySession()),
                                           expiredId));
                if ( manager instanceof DeltaManager ) {
                    ( (DeltaManager) manager).sessionExpired(expiredId);
                }
            }
        }
    }

    /**
     * 释放所有对象引用, 并初始化实例变量, 准备这个对象的重用.
     */
    @Override
    public void recycle() {
        lock();
        try {
            super.recycle();
            deltaRequest.clear();
        } finally{
            unlock();
        }
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DeltaSession[");
        sb.append(id);
        sb.append("]");
        return (sb.toString());
    }

    @Override
    public void addSessionListener(SessionListener listener) {
        addSessionListener(listener, true);
    }

    public void addSessionListener(SessionListener listener, boolean addDeltaRequest) {
        lock();
        try {
            super.addSessionListener(listener);
            if (addDeltaRequest && deltaRequest != null && listener instanceof ReplicatedSessionListener) {
                deltaRequest.addSessionListener(listener);
            }
        } finally {
            unlock();
        }
    }

    @Override
    public void removeSessionListener(SessionListener listener) {
        removeSessionListener(listener, true);
    }

    public void removeSessionListener(SessionListener listener, boolean addDeltaRequest) {
        lock();
        try {
            super.removeSessionListener(listener);
            if (addDeltaRequest && deltaRequest != null && listener instanceof ReplicatedSessionListener) {
                deltaRequest.removeSessionListener(listener);
            }
        } finally {
            unlock();
        }
    }


    // ------------------------------------------------ Session Package Methods

    @Override
    public void readExternal(ObjectInput in) throws IOException,ClassNotFoundException {
        lock();
        try {
            readObjectData(in);
        } finally{
            unlock();
        }
    }


    /**
     * 从指定的对象输入流读取此会话对象的内容的序列化版本, 不需要已经被序列化了的StandardSession.
     *
     * @param stream 要从中读取的对象输入流
     *
     * @exception ClassNotFoundException 如果指定了未知类
     * @exception IOException 如果发生输入/输出错误
     */
    @Override
    public void readObjectData(ObjectInputStream stream) throws ClassNotFoundException, IOException {
        doReadObject((ObjectInput)stream);
    }
    public void readObjectData(ObjectInput stream) throws ClassNotFoundException, IOException {
        doReadObject(stream);
    }

    /**
     * 将此会话对象的内容的序列化版本写入指定的对象输出流, 不需要已经被序列化了的StandardSession.
     *
     * @param stream 要写入的对象输出流
     *
     * @exception IOException 如果发生输入/输出错误
     */
    @Override
    public void writeObjectData(ObjectOutputStream stream) throws IOException {
        writeObjectData((ObjectOutput)stream);
    }
    public void writeObjectData(ObjectOutput stream) throws IOException {
        doWriteObject(stream);
    }

    public void resetDeltaRequest() {
        lock();
        try {
            if (deltaRequest == null) {
                boolean recordAllActions = manager instanceof ClusterManagerBase &&
                        ((ClusterManagerBase)manager).isRecordAllActions();
                deltaRequest = new DeltaRequest(getIdInternal(), recordAllActions);
            } else {
                deltaRequest.reset();
                deltaRequest.setSessionId(getIdInternal());
            }
        } finally{
            unlock();
        }
    }

    public DeltaRequest getDeltaRequest() {
        if (deltaRequest == null) resetDeltaRequest();
        return deltaRequest;
    }


    /**
     * 从会话中移除具有指定名称的对象绑定.
     * 如果会话没有与此名称绑定的对象, 这个方法什么也不做.
     * <p>
     * 此方法执行后, 以及如果对象实现了<code>HttpSessionBindingListener</code>, 容器调用对象上的<code>valueUnbound()</code>.
     *
     * @param name 要从该会话中移除的对象的名称.
     * @param notify 是否通知相关监听器?
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    @Override
    public void removeAttribute(String name, boolean notify) {
        removeAttribute(name, notify, true);
    }

    public void removeAttribute(String name, boolean notify,boolean addDeltaRequest) {
        // Validate our current state
        if (!isValid()) throw new IllegalStateException(sm.getString("standardSession.removeAttribute.ise"));
        removeAttributeInternal(name, notify, addDeltaRequest);
    }

    /**
     * 将对象绑定到此会话, 使用指定的名称. 如果同一个对象已经绑定到此会话, 替换这个对象.
     * <p>
     * 此方法执行后, 以及如果对象实现了<code>HttpSessionBindingListener</code>, 容器调用对象上的<code>valueBound()</code>.
     *
     * @param name 对象绑定到的名称, 不能是 null
     * @param value 要绑定的对象, 不能是 null
     *
     * @exception IllegalArgumentException 如果试图在可标记的可分配环境中添加不可序列化的对象.
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    @Override
    public void setAttribute(String name, Object value) {
        setAttribute(name, value, true, true);
    }

    public void setAttribute(String name, Object value, boolean notify,boolean addDeltaRequest) {

        // Name cannot be null
        if (name == null) throw new IllegalArgumentException(sm.getString("standardSession.setAttribute.namenull"));

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        lock();
        try {
            super.setAttribute(name,value, notify);
            if (addDeltaRequest && deltaRequest != null && !exclude(name, value)) {
                deltaRequest.setAttribute(name, value);
            }
        } finally {
            unlock();
        }
    }

    // -------------------------------------------- HttpSession Private Methods


    /**
     * 从指定的对象输入流读取此会话对象的序列化版本.
     * <p>
     * <b>IMPLEMENTATION NOTE </b>: 此方法不恢复对拥有的管理器的引用, 必须明确设置.
     *
     * @param stream 要读取的输入流
     *
     * @exception ClassNotFoundException 如果指定了未知类
     * @exception IOException 如果发生输入/输出错误
     */
    @Override
    protected void doReadObject(ObjectInputStream stream) throws ClassNotFoundException, IOException {
        doReadObject((ObjectInput)stream);
    }

    private void doReadObject(ObjectInput stream) throws ClassNotFoundException, IOException {

        // 反序列化标量实例变量 (排除 Manager)
        authType = null; // Transient only
        creationTime = ( (Long) stream.readObject()).longValue();
        lastAccessedTime = ( (Long) stream.readObject()).longValue();
        maxInactiveInterval = ( (Integer) stream.readObject()).intValue();
        isNew = ( (Boolean) stream.readObject()).booleanValue();
        isValid = ( (Boolean) stream.readObject()).booleanValue();
        thisAccessedTime = ( (Long) stream.readObject()).longValue();
        version = ( (Long) stream.readObject()).longValue();
        boolean hasPrincipal = stream.readBoolean();
        principal = null;
        if (hasPrincipal) {
            principal = (Principal) stream.readObject();
        }

        //        setId((String) stream.readObject());
        id = (String) stream.readObject();
        if (log.isDebugEnabled()) log.debug(sm.getString("deltaSession.readSession", id));

        // 反序列化属性计数和属性值
        if (attributes == null) attributes = new ConcurrentHashMap<>();
        int n = ( (Integer) stream.readObject()).intValue();
        boolean isValidSave = isValid;
        isValid = true;
        for (int i = 0; i < n; i++) {
            String name = (String) stream.readObject();
            final Object value;
            try {
                value = stream.readObject();
            } catch (WriteAbortedException wae) {
                if (wae.getCause() instanceof NotSerializableException) {
                    // 跳过不可序列化的属性
                    continue;
                }
                throw wae;
            }
            // 处理在Web应用程序停止时更改过滤器配置的情况.
            if (exclude(name, value)) {
                continue;
            }
            attributes.put(name, value);
        }
        isValid = isValidSave;

        // Session listeners
        n = ((Integer) stream.readObject()).intValue();
        if (listeners == null || n > 0) {
            listeners = new ArrayList<>();
        }
        for (int i = 0; i < n; i++) {
            SessionListener listener = (SessionListener) stream.readObject();
            listeners.add(listener);
        }

        if (notes == null) {
            notes = new Hashtable<>();
        }
        activate();
    }

    @Override
    public void writeExternal(ObjectOutput out ) throws java.io.IOException {
        lock();
        try {
            doWriteObject(out);
        } finally {
            unlock();
        }
    }


    /**
     * 将此会话对象的序列化版本写入指定的对象输出流.
     * <p>
     * <b>IMPLEMENTATION NOTE </b>: 拥有的Manager将不存储在该会话的序列化表示中.
     * 调用<code>readObject()</code>之后, 必须明确地设置关联的Manager.
     * <p>
     * <b>IMPLEMENTATION NOTE </b>: 不可序列化的任何属性将从会话中解除绑定, 使用适合的动作，如果它实现了HttpSessionBindingListener.
     * 如果不想要任何这样的属性, 确定关联的Manager的<code>distributable</code>属性被设置为<code>true</code>.
     *
     * @param stream 要写入的输出流
     *
     * @exception IOException 如果发生输入/输出错误
     */
    @Override
    protected void doWriteObject(ObjectOutputStream stream) throws IOException {
        doWriteObject((ObjectOutput)stream);
    }

    private void doWriteObject(ObjectOutput stream) throws IOException {
        // 写入标量实例变量 (排除 Manager)
        stream.writeObject(Long.valueOf(creationTime));
        stream.writeObject(Long.valueOf(lastAccessedTime));
        stream.writeObject(Integer.valueOf(maxInactiveInterval));
        stream.writeObject(Boolean.valueOf(isNew));
        stream.writeObject(Boolean.valueOf(isValid));
        stream.writeObject(Long.valueOf(thisAccessedTime));
        stream.writeObject(Long.valueOf(version));
        stream.writeBoolean(getPrincipal() instanceof Serializable);
        if (getPrincipal() instanceof Serializable) {
            stream.writeObject(getPrincipal());
        }

        stream.writeObject(id);
        if (log.isDebugEnabled()) log.debug(sm.getString("deltaSession.writeSession", id));

        // 累积可序列化和不可序列化属性的名称
        String keys[] = keys();
        ArrayList<String> saveNames = new ArrayList<>();
        ArrayList<Object> saveValues = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            Object value = null;
            value = attributes.get(keys[i]);
            if (value != null && !exclude(keys[i], value) &&
                    isAttributeDistributable(keys[i], value)) {
                saveNames.add(keys[i]);
                saveValues.add(value);
            }
        }

        // 序列化属性计数和可序列化属性
        int n = saveNames.size();
        stream.writeObject(Integer.valueOf(n));
        for (int i = 0; i < n; i++) {
            stream.writeObject( saveNames.get(i));
            try {
                stream.writeObject(saveValues.get(i));
            } catch (NotSerializableException e) {
                log.error(sm.getString("standardSession.notSerializable", saveNames.get(i), id), e);
            }
        }

        // Serializable listeners
        ArrayList<SessionListener> saveListeners = new ArrayList<>();
        for (SessionListener listener : listeners) {
            if (listener instanceof ReplicatedSessionListener) {
                saveListeners.add(listener);
            }
        }
        stream.writeObject(Integer.valueOf(saveListeners.size()));
        for (SessionListener listener : saveListeners) {
            stream.writeObject(listener);
        }
    }


    // -------------------------------------------------------- Private Methods

    protected void removeAttributeInternal(String name, boolean notify,
                                           boolean addDeltaRequest) {
        lock();
        try {
            // 从集合中删除这个属性
            Object value = attributes.get(name);
            if (value == null) return;

            super.removeAttributeInternal(name,notify);
            if (addDeltaRequest && deltaRequest != null && !exclude(name, null)) {
                deltaRequest.removeAttribute(name);
            }
        } finally {
            unlock();
        }
    }

    @Override
    public long getLastTimeReplicated() {
        return lastTimeReplicated;
    }

    @Override
    public long getVersion() {
        return version;
    }

    @Override
    public void setLastTimeReplicated(long lastTimeReplicated) {
        this.lastTimeReplicated = lastTimeReplicated;
    }

    @Override
    public void setVersion(long version) {
        this.version = version;
    }

    protected void setAccessCount(int count) {
        if ( accessCount == null && ACTIVITY_CHECK ) accessCount = new AtomicInteger();
        if ( accessCount != null ) super.accessCount.set(count);
    }
}
