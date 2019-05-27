package org.apache.catalina.session;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.Store;
import org.apache.catalina.StoreManager;
import org.apache.catalina.security.SecurityUtil;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 继承<b>ManagerBase</b>类， class 实现一个支持任何持久性的Manager所需的大部分功能, 即使只有重新启动.
 * <p>
 * <b>实现注意</b>: 会话存储和重新加载的正确行为取决于这个类的<code>start()</code>和<code>stop()</code>方法在正确的时间调用.
 */
public abstract class PersistentManagerBase extends ManagerBase
        implements StoreManager {

    private static final Log log = LogFactory.getLog(PersistentManagerBase.class);

    // ---------------------------------------------------- Security Classes

    private class PrivilegedStoreClear
        implements PrivilegedExceptionAction<Void> {

        PrivilegedStoreClear() {
            // NOOP
        }

        @Override
        public Void run() throws Exception{
           store.clear();
           return null;
        }
    }

    private class PrivilegedStoreRemove
        implements PrivilegedExceptionAction<Void> {

        private String id;

        PrivilegedStoreRemove(String id) {
            this.id = id;
        }

        @Override
        public Void run() throws Exception{
           store.remove(id);
           return null;
        }
    }

    private class PrivilegedStoreLoad
        implements PrivilegedExceptionAction<Session> {

        private String id;

        PrivilegedStoreLoad(String id) {
            this.id = id;
        }

        @Override
        public Session run() throws Exception{
           return store.load(id);
        }
    }

    private class PrivilegedStoreSave
        implements PrivilegedExceptionAction<Void> {

        private Session session;

        PrivilegedStoreSave(Session session) {
            this.session = session;
        }

        @Override
        public Void run() throws Exception{
           store.save(session);
           return null;
        }
    }

    private class PrivilegedStoreKeys
        implements PrivilegedExceptionAction<String[]> {

        PrivilegedStoreKeys() {
            // NOOP
        }

        @Override
        public String[] run() throws Exception{
           return store.keys();
        }
    }

    // ----------------------------------------------------- Instance Variables

    /**
     * 实现类的描述信息.
     */
    private static final String name = "PersistentManagerBase";

    /**
     * 存储最后一个备份的时间戳的会话的注释的Key.
     */
    private static final String PERSISTED_LAST_ACCESSED_TIME =
            "org.apache.catalina.session.PersistentManagerBase.persistedLastAccessedTime";


    /**
     * 管理Session store的Store对象.
     */
    protected Store store = null;


    /**
     * 当Manager的<code>unload</code>和<code>load</code>方法被调用的时候， 会话是否保存和重新加载.
     */
    protected boolean saveOnRestart = true;


    /**
     * 在备份之前，会话必须空闲多长时间. -1 意味着会话不会被备份.
     */
    protected int maxIdleBackup = -1;


    /**
     * 在交换到磁盘之前，会话必须空闲的最小时间. 以保证活动的会话在配置的maxActiveSessions之下.
     * 设置为{@code -1}意味着会话不会被交换出去, 以保持活动会话计数.
     */
    protected int minIdleSwap = -1;


    /**
     * 会话空闲的最大时间(秒), 在因为不活动而被交换到磁盘之前.
     * 设置为{@code -1}意味着会话不会因为不活动而被交换出来.
     */
    protected int maxIdleSwap = -1;


    /**
     * 当前正在交换的会话和相关的锁
     */
    private final Map<String,Object> sessionSwapInLocks = new HashMap<>();


    // ------------------------------------------------------------- Properties


    /**
     * 在备份之前，会话必须空闲多长时间. -1 意味着会话不会被备份.
     */
    public int getMaxIdleBackup() {
        return maxIdleBackup;
    }


    /**
     * 设置一个选项，在请求中使用会话后将其备份到存储区. 
     * 备份后会话仍然可用在内存中. 值集指示会话的可用时间(自上次使用以来)在它必须被备份之前: 
     * -1意味着会话没有备份.
     * <p>
     * 注意，这不是一个硬性限制: 定期检查会话的时间限制，根据{@code processExpiresFrequency}.
     * 应该考虑这个值来指示会话何时可以备份.
     * <p>
     * 因此会话可能在{@code maxIdleBackup + processExpiresFrequency * engine.backgroundProcessorDelay}秒后空闲, 加上处理其他会话过期、交换等任务所需的时间.
     *
     * @param backup 当它们被写入Store时，距离上次访问时间的秒数.
     */
    public void setMaxIdleBackup (int backup) {

        if (backup == this.maxIdleBackup)
            return;
        int oldBackup = this.maxIdleBackup;
        this.maxIdleBackup = backup;
        support.firePropertyChange("maxIdleBackup",
                                   Integer.valueOf(oldBackup),
                                   Integer.valueOf(this.maxIdleBackup));

    }


    /**
     * @return 会话空闲的最大时间(秒), 在因为不活动而被交换到磁盘之前. 设置为-1意味着会话不会因为不活动而被交换出来.
     */
    public int getMaxIdleSwap() {
        return maxIdleSwap;
    }


    /**
     * 会话空闲的最大时间(秒), 在因为不活动而被交换到磁盘之前. 设置为-1意味着会话不会因为不活动而被交换出来.
     *
     * @param max time in seconds to wait for possible swap out
     */
    public void setMaxIdleSwap(int max) {

        if (max == this.maxIdleSwap)
            return;
        int oldMaxIdleSwap = this.maxIdleSwap;
        this.maxIdleSwap = max;
        support.firePropertyChange("maxIdleSwap",
                                   Integer.valueOf(oldMaxIdleSwap),
                                   Integer.valueOf(this.maxIdleSwap));
    }


    /**
     * @return 在交换到磁盘之前，会话必须空闲的最小时间. 以保证活动的会话在配置的maxActiveSessions之下. 设置为-1意味着会话不会被交换出去, 以保持活动会话计数.
     */
    public int getMinIdleSwap() {
        return minIdleSwap;
    }


    /**
     * 在交换到磁盘之前，会话必须空闲的最小时间. 以保证活动的会话在配置的maxActiveSessions之下. 设置为-1意味着会话不会被交换出去, 以保持活动会话计数.
     *
     * @param min time in seconds before a possible swap out
     */
    public void setMinIdleSwap(int min) {

        if (this.minIdleSwap == min)
            return;
        int oldMinIdleSwap = this.minIdleSwap;
        this.minIdleSwap = min;
        support.firePropertyChange("minIdleSwap",
                                   Integer.valueOf(oldMinIdleSwap),
                                   Integer.valueOf(this.minIdleSwap));

    }


    /**
     * 会话是否已加载入内存
     *
     * @param id 要搜索的会话ID
     * @return {@code true}, 如果已经加载入内存; 否则{@code false}
     */
    public boolean isLoaded( String id ){
        try {
            if ( super.findSession(id) != null )
                return true;
        } catch (IOException e) {
            log.error("checking isLoaded for id, " + id + ", "+e.getMessage(), e);
        }
        return false;
    }


    @Override
    public String getName() {
        return name;
    }


    /**
     * 设置将管理持久化会话存储的Store对象.
     *
     * @param store 关联的Store
     */
    public void setStore(Store store) {
        this.store = store;
        store.setManager(this);
    }


    /**
     * @return 将管理持久化会话存储的Store对象.
     */
    @Override
    public Store getStore() {
        return this.store;
    }


    /**
     * 当Manager 被正确关闭时是否保存会话. 这就要求调用{@link #unload()}方法.
     *
     * @return {@code true}, 在重新启动时应保存会话, 否则{code false}
     */
    public boolean getSaveOnRestart() {
        return saveOnRestart;
    }


    /**
     * 当Manager 被正确关闭时是否保存会话, 然后在Manager启动时重新加载.
     * 如果设置为false, 当Manager启动时Store中找到的所有会话将被捡起.
     *
     * @param saveOnRestart {@code true} 在重新启动时应保存会话, 否则{code false}
     */
    public void setSaveOnRestart(boolean saveOnRestart) {

        if (saveOnRestart == this.saveOnRestart)
            return;

        boolean oldSaveOnRestart = this.saveOnRestart;
        this.saveOnRestart = saveOnRestart;
        support.firePropertyChange("saveOnRestart",
                                   Boolean.valueOf(oldSaveOnRestart),
                                   Boolean.valueOf(this.saveOnRestart));

    }


    // --------------------------------------------------------- Public Methods


    /**
     * 从Store清空所有会话.
     */
    public void clearStore() {

        if (store == null)
            return;

        try {
            if (SecurityUtil.isPackageProtectionEnabled()){
                try{
                    AccessController.doPrivileged(new PrivilegedStoreClear());
                }catch(PrivilegedActionException ex){
                    Exception exception = ex.getException();
                    log.error("Exception clearing the Store: " + exception,
                            exception);
                }
            } else {
                store.clear();
            }
        } catch (IOException e) {
            log.error("Exception clearing the Store: " + e, e);
        }

    }


    @Override
    public void processExpires() {

        long timeNow = System.currentTimeMillis();
        Session sessions[] = findSessions();
        int expireHere = 0 ;
        if(log.isDebugEnabled())
             log.debug("Start expire sessions " + getName() + " at " + timeNow + " sessioncount " + sessions.length);
        for (int i = 0; i < sessions.length; i++) {
            if (!sessions[i].isValid()) {
                expiredSessions.incrementAndGet();
                expireHere++;
            }
        }
        processPersistenceChecks();
        if (getStore() instanceof StoreBase) {
            ((StoreBase) getStore()).processExpires();
        }

        long timeEnd = System.currentTimeMillis();
        if(log.isDebugEnabled())
             log.debug("End expire sessions " + getName() + " processingTime " + (timeEnd - timeNow) + " expired sessions: " + expireHere);
        processingTime += (timeEnd - timeNow);

    }


    /**
     * 在有效会话检查到期后，后台线程调用, 允许会话被交换出去, 备份, 等.
     */
    public void processPersistenceChecks() {

        processMaxIdleSwaps();
        processMaxActiveSwaps();
        processMaxIdleBackups();
    }


    /**
     * 如果启用持久性，则此方法检查持久性存储, 否则只从ManagerBase使用功能性.
     */
    @Override
    public Session findSession(String id) throws IOException {

        Session session = super.findSession(id);
        // 不确定另一个线程是否试图删除会话, 因此，唯一的方法就是锁定它（或尝试），然后尝试再次通过这个会话ID获得它. 
        if(session != null) {
            synchronized(session){
                session = super.findSession(session.getIdInternal());
                if(session != null){
                   // 防止任何外部调用代码扰乱并发性.
                   session.access();
                   session.endAccess();
                }
            }
        }
        if (session != null)
            return session;

        // See if the Session is in the Store
        session = swapIn(id);
        return session;
    }

    /**
     * 从活动的会话中移除这个Session, 而不是从Store移除. (Used by the PersistentValve)
     *
     * @param session Session to be removed
     */
    @Override
    public void removeSuper(Session session) {
        super.remove(session, false);
    }

    /**
     * 加载持久性机制中发现的所有会话, 假设它们被标记为有效且没有过期限制.
     * 如果不支持持久性, 这个方法不做任何事情就返回.
     * <p>
     * 注意，默认情况下, 此方法不会被MiddleManager类调用. 为了使用它, 子类必须专门调用它,
     * 例如 start() 或 processPersistenceChecks() 方法.
     */
    @Override
    public void load() {

        // 初始化内部数据结构
        sessions.clear();

        if (store == null)
            return;

        String[] ids = null;
        try {
            if (SecurityUtil.isPackageProtectionEnabled()){
                try{
                    ids = AccessController.doPrivileged(
                            new PrivilegedStoreKeys());
                }catch(PrivilegedActionException ex){
                    Exception exception = ex.getException();
                    log.error("Exception in the Store during load: "
                              + exception, exception);
                    return;
                }
            } else {
                ids = store.keys();
            }
        } catch (IOException e) {
            log.error("Can't load sessions from store, " + e.getMessage(), e);
            return;
        }

        int n = ids.length;
        if (n == 0)
            return;

        if (log.isDebugEnabled())
            log.debug(sm.getString("persistentManager.loading", String.valueOf(n)));

        for (int i = 0; i < n; i++)
            try {
                swapIn(ids[i]);
            } catch (IOException e) {
                log.error("Failed load session from store, " + e.getMessage(), e);
            }

    }


    /**
     * 从Store中删除此会话 .
     */
    @Override
    public void remove(Session session, boolean update) {

        super.remove (session, update);

        if (store != null){
            removeSession(session.getIdInternal());
        }
    }


    /**
     * 从活动的会话和Store中移除这个Session.
     *
     * @param id Session's id to be removed
     */
    protected void removeSession(String id){
        try {
            if (SecurityUtil.isPackageProtectionEnabled()){
                try{
                    AccessController.doPrivileged(new PrivilegedStoreRemove(id));
                }catch(PrivilegedActionException ex){
                    Exception exception = ex.getException();
                    log.error("Exception in the Store during removeSession: "
                              + exception, exception);
                }
            } else {
                 store.remove(id);
            }
        } catch (IOException e) {
            log.error("Exception removing session  " + e.getMessage(), e);
        }
    }

    /**
     * 将所有当前活动的会话保存在适当的持久化机制中. 
     * 如果不支持持久性, 这个方法不做任何事情就返回.
     * <p>
     * 注意，默认情况下, 此方法不会被MiddleManager类调用. 为了使用它, 子类必须专门调用它,
     * 例如 start() 或 processPersistenceChecks() 方法.
     */
    @Override
    public void unload() {

        if (store == null)
            return;

        Session sessions[] = findSessions();
        int n = sessions.length;
        if (n == 0)
            return;

        if (log.isDebugEnabled())
            log.debug(sm.getString("persistentManager.unloading",
                             String.valueOf(n)));

        for (int i = 0; i < n; i++)
            try {
                swapOut(sessions[i]);
            } catch (IOException e) {
                // This is logged in writeSession()
            }

    }


    @Override
    public int getActiveSessionsFull() {
        // In memory session count
        int result = getActiveSessions();
        try {
            // Store session count
            result += getStore().getSize();
        } catch (IOException ioe) {
            log.warn(sm.getString("persistentManager.storeSizeException"));
        }
        return result;
    }


    @Override
    public Set<String> getSessionIdsFull() {
        Set<String> sessionIds = new HashSet<>();
        // In memory session ID list
        sessionIds.addAll(sessions.keySet());
        // Store session ID list
        String[] storeKeys;
        try {
            storeKeys = getStore().keys();
            for (String storeKey : storeKeys) {
                sessionIds.add(storeKey);
            }
        } catch (IOException e) {
            log.warn(sm.getString("persistentManager.storeKeysException"));
        }
        return sessionIds;
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * 从Store中查找会话, 如果有合适的话，在Manager的活动列表中恢复它.
     * 会话将从Store中移除，在交换之后, 但如果活动会话列表无效或过期，则不会将其添加到活动会话列表中.
     *
     * @param id 应该交换的会话ID
     * @return 恢复的会话, 或{@code null}
     * @throws IOException an IO error occurred
     */
    protected Session swapIn(String id) throws IOException {

        if (store == null)
            return null;

        Object swapInLock = null;

        /*
         * 此同步和这些锁的目的是确保会话只加载一次. 如果删除了锁，那么另一个线程进入这个方法并尝试加载同一个会话并不重要.
         * 该线程将为该会话重新创建swapIn锁, 很快发现会话已经在会话列表中, 使用它并继续进行.
         */
        synchronized (this) {
            swapInLock = sessionSwapInLocks.get(id);
            if (swapInLock == null) {
                swapInLock = new Object();
                sessionSwapInLocks.put(id, swapInLock);
            }
        }

        Session session = null;

        synchronized (swapInLock) {
            // 首先检查是否有另一个线程将会话加载到管理器中
            session = sessions.get(id);

            if (session == null) {
                try {
                    if (SecurityUtil.isPackageProtectionEnabled()){
                        try {
                            session = AccessController.doPrivileged(
                                    new PrivilegedStoreLoad(id));
                        } catch (PrivilegedActionException ex) {
                            Exception e = ex.getException();
                            log.error(sm.getString(
                                    "persistentManager.swapInException", id),
                                    e);
                            if (e instanceof IOException){
                                throw (IOException)e;
                            } else if (e instanceof ClassNotFoundException) {
                                throw (ClassNotFoundException)e;
                            }
                        }
                    } else {
                         session = store.load(id);
                    }
                } catch (ClassNotFoundException e) {
                    String msg = sm.getString(
                            "persistentManager.deserializeError", id);
                    log.error(msg, e);
                    throw new IllegalStateException(msg, e);
                }

                if (session != null && !session.isValid()) {
                    log.error(sm.getString(
                            "persistentManager.swapInInvalid", id));
                    session.expire();
                    removeSession(id);
                    session = null;
                }

                if (session != null) {
                    if(log.isDebugEnabled())
                        log.debug(sm.getString("persistentManager.swapIn", id));

                    session.setManager(this);
                    // make sure the listeners know about it.
                    ((StandardSession)session).tellNew();
                    add(session);
                    ((StandardSession)session).activate();
                    // endAccess()确保超时正确发生.
                    // access()保持访问计数正确或以负数结束
                    session.access();
                    session.endAccess();
                }
            }
        }

        // 确保锁被移除
        synchronized (this) {
            sessionSwapInLocks.remove(id);
        }
        return session;
    }


    /**
     * 从活动会话列表中移除会话，并将其写入 Store.
     * 如果会话过期或无效, 什么都不做.
     *
     * @param session 要写入的Session
     * @throws IOException an IO error occurred
     */
    protected void swapOut(Session session) throws IOException {

        if (store == null || !session.isValid()) {
            return;
        }

        ((StandardSession)session).passivate();
        writeSession(session);
        super.remove(session, true);
        session.recycle();
    }


    /**
     * 将所提供的会话写入Store，而不修改内存中的副本或触发钝化事件.
     * 如果会话无效或过期，则不执行任何操作.
     * 
     * @param session 要写入的Session
     * @throws IOException an IO error occurred
     */
    protected void writeSession(Session session) throws IOException {

        if (store == null || !session.isValid()) {
            return;
        }

        try {
            if (SecurityUtil.isPackageProtectionEnabled()){
                try{
                    AccessController.doPrivileged(new PrivilegedStoreSave(session));
                }catch(PrivilegedActionException ex){
                    Exception exception = ex.getException();
                    if (exception instanceof IOException) {
                        throw (IOException) exception;
                    }
                    log.error("Exception in the Store during writeSession: "
                              + exception, exception);
                }
            } else {
                 store.save(session);
            }
        } catch (IOException e) {
            log.error(sm.getString
                ("persistentManager.serializeError", session.getIdInternal(), e));
            throw e;
        }
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        super.startInternal();

        if (store == null)
            log.error("No Store configured, persistence disabled");
        else if (store instanceof Lifecycle)
            ((Lifecycle)store).start();

        setState(LifecycleState.STARTING);
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        if (log.isDebugEnabled())
            log.debug("Stopping");

        setState(LifecycleState.STOPPING);

        if (getStore() != null && saveOnRestart) {
            unload();
        } else {
            // Expire all active sessions
            Session sessions[] = findSessions();
            for (int i = 0; i < sessions.length; i++) {
                StandardSession session = (StandardSession) sessions[i];
                if (!session.isValid())
                    continue;
                session.expire();
            }
        }

        if (getStore() instanceof Lifecycle) {
            ((Lifecycle)getStore()).stop();
        }

        // Require a new random number generator if we are restarted
        super.stopInternal();
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 交换空闲会话到 Store, 如果它们闲置太长时间.
     */
    protected void processMaxIdleSwaps() {

        if (!getState().isAvailable() || maxIdleSwap < 0)
            return;

        Session sessions[] = findSessions();

        // 交换出所有闲置时间超过maxIdleSwap的会话
        if (maxIdleSwap >= 0) {
            for (int i = 0; i < sessions.length; i++) {
                StandardSession session = (StandardSession) sessions[i];
                synchronized (session) {
                    if (!session.isValid())
                        continue;
                    int timeIdle = (int) (session.getIdleTimeInternal() / 1000L);
                    if (timeIdle >= maxIdleSwap && timeIdle >= minIdleSwap) {
                        if (session.accessCount != null &&
                                session.accessCount.get() > 0) {
                            // Session is currently being accessed - skip it
                            continue;
                        }
                        if (log.isDebugEnabled())
                            log.debug(sm.getString
                                ("persistentManager.swapMaxIdle",
                                 session.getIdInternal(),
                                 Integer.valueOf(timeIdle)));
                        try {
                            swapOut(session);
                        } catch (IOException e) {
                            // This is logged in writeSession()
                        }
                    }
                }
            }
        }

    }


    /**
     * 交换空闲会话到Store, 如果太多活动的会话
     */
    protected void processMaxActiveSwaps() {

        if (!getState().isAvailable() || getMaxActiveSessions() < 0)
            return;

        Session sessions[] = findSessions();

        // FIXME: Smarter algorithm (LRU)
        int limit = (int) (getMaxActiveSessions() * 0.9);

        if (limit >= sessions.length)
            return;

        if(log.isDebugEnabled())
            log.debug(sm.getString
                ("persistentManager.tooManyActive",
                 Integer.valueOf(sessions.length)));

        int toswap = sessions.length - limit;

        for (int i = 0; i < sessions.length && toswap > 0; i++) {
            StandardSession session =  (StandardSession) sessions[i];
            synchronized (session) {
                int timeIdle = (int) (session.getIdleTimeInternal() / 1000L);
                if (timeIdle >= minIdleSwap) {
                    if (session.accessCount != null &&
                            session.accessCount.get() > 0) {
                        // Session is currently being accessed - skip it
                        continue;
                    }
                    if(log.isDebugEnabled())
                        log.debug(sm.getString
                            ("persistentManager.swapTooManyActive",
                             session.getIdInternal(),
                             Integer.valueOf(timeIdle)));
                    try {
                        swapOut(session);
                    } catch (IOException e) {
                        // This is logged in writeSession()
                    }
                    toswap--;
                }
            }
        }
    }


    /**
     * 备份空闲会话.
     */
    protected void processMaxIdleBackups() {

        if (!getState().isAvailable() || maxIdleBackup < 0)
            return;

        Session sessions[] = findSessions();

        // 备份所有空闲时间超过maxIdleBackup的会话
        if (maxIdleBackup >= 0) {
            for (int i = 0; i < sessions.length; i++) {
                StandardSession session = (StandardSession) sessions[i];
                synchronized (session) {
                    if (!session.isValid())
                        continue;
                    long lastAccessedTime = session.getLastAccessedTimeInternal();
                    Long persistedLastAccessedTime =
                            (Long) session.getNote(PERSISTED_LAST_ACCESSED_TIME);
                    if (persistedLastAccessedTime != null &&
                            lastAccessedTime == persistedLastAccessedTime.longValue())
                        continue;
                    int timeIdle = (int) (session.getIdleTimeInternal() / 1000L);
                    if (timeIdle >= maxIdleBackup) {
                        if (log.isDebugEnabled())
                            log.debug(sm.getString
                                ("persistentManager.backupMaxIdle",
                                session.getIdInternal(),
                                Integer.valueOf(timeIdle)));

                        try {
                            writeSession(session);
                        } catch (IOException e) {
                            // This is logged in writeSession()
                        }
                        session.setNote(PERSISTED_LAST_ACCESSED_TIME,
                                Long.valueOf(lastAccessedTime));
                    }
                }
            }
        }
    }
}
