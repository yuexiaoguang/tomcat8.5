package org.apache.catalina.session;


import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.SessionIdGenerator;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.catalina.util.SessionIdGeneratorBase;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * <b>Manager</b>接口的抽象实现类， 不支持会话持久性或分派的能力. 这个类可以子类化来创建更复杂的管理器的实现.
 */
public abstract class ManagerBase extends LifecycleMBeanBase implements Manager {

    private final Log log = LogFactory.getLog(ManagerBase.class); // must not be static

    // ----------------------------------------------------- Instance Variables

    /**
     * 关联的Context.
     */
    private Context context;


    /**
     * 实现类的描述信息.
     */
    private static final String name = "ManagerBase";


    /**
     * 使用的安全随机数生成器类的 Java类名，生成会话标识符时.
     * 随机数生成器类必须是自播种的，并且具有零参数构造函数. 如果未指定, 将生成{@link java.security.SecureRandom}实例.
     */
    protected String secureRandomClass = null;

    /**
     * 用于创建{@link java.security.SecureRandom}实例的算法的名称 , 用于生成会话ID.
     * 如果没有指定算法, 使用SHA1PRNG. 使用平台默认的SHA1PRNG), 指定空字符串. 如果指定了无效的算法或提供程序，则将使用默认值创建SecureRandom实例.
     * 如果失败, 则将使用默认值创建SecureRandom实例.
     */
    protected String secureRandomAlgorithm = "SHA1PRNG";

    /**
     * 用于创建{@link java.security.SecureRandom}实例的提供程序的名称, 用于生成会话ID.
     * 如果没有指定算法, 使用SHA1PRNG. 如果指定了无效的算法或提供程序，则将使用默认值创建SecureRandom实例.
     * 如果失败, 则将使用默认值创建SecureRandom实例.
     */
    protected String secureRandomProvider = null;

    protected SessionIdGenerator sessionIdGenerator = null;
    protected Class<? extends SessionIdGenerator> sessionIdGeneratorClass = null;

    /**
     * 过期会话存活的最长时间（秒）.
     */
    protected volatile int sessionMaxAliveTime;
    private final Object sessionMaxAliveTimeUpdateLock = new Object();


    protected static final int TIMING_STATS_CACHE_SIZE = 100;

    protected final Deque<SessionTiming> sessionCreationTiming =
            new LinkedList<>();

    protected final Deque<SessionTiming> sessionExpirationTiming =
            new LinkedList<>();

    /**
     * 已过期的会话数.
     */
    protected final AtomicLong expiredSessions = new AtomicLong(0);


    /**
     * 当前活动会话集合, 会话标识符作为key.
     */
    protected Map<String, Session> sessions = new ConcurrentHashMap<>();

    // 此管理器创建的会话数
    protected long sessionCounter=0;

    protected volatile int maxActive=0;

    private final Object maxActiveUpdateLock = new Object();

    /**
     * 允许的活动会话的最大数目, 或 -1不限制.
     */
    protected int maxActiveSessions = -1;

    /**
     * 创建失败的会话次数, 由于maxActiveSessions.
     */
    protected int rejectedSessions = 0;

    // 重复会话ID号 - 大于0 意味着错误
    protected volatile int duplicates=0;

    /**
     * 会话过期期间的处理时间.
     */
    protected long processingTime = 0;

    /**
     * 后台处理的迭代计数.
     */
    private int count = 0;


    /**
     * 会话过期频率, 及相关管理操作.
     * Manager 操作将完成一次指定数量的backgrondProces的调用 (ie, 数额越低, 最常见的检查将发生).
     */
    protected int processExpiresFrequency = 6;

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(ManagerBase.class);

    /**
     * 属性修改支持.
     */
    protected final PropertyChangeSupport support =
            new PropertyChangeSupport(this);

    private Pattern sessionAttributeNamePattern;

    private Pattern sessionAttributeValueClassNamePattern;

    private boolean warnOnSessionAttributeFilterFailure;


    // ------------------------------------------------------------ Constructors

    public ManagerBase() {
        if (Globals.IS_SECURITY_ENABLED) {
            // Minimum set required for default distribution/persistence to work
            // plus String
            setSessionAttributeValueClassNameFilter(
                    "java\\.lang\\.(?:Boolean|Integer|Long|Number|String)");
            setWarnOnSessionAttributeFilterFailure(true);
        }
    }


    // -------------------------------------------------------------- Properties

    /**
     * 获取用于根据属性名称筛选会话属性的正则表达式. 正则表达式被锚定，因此它必须与整个名称匹配
     *
     * @return 当前用于过滤属性名称的正则表达式. {@code null}不使用任何过滤器. 如果指定了空字符串，则没有名称与筛选器匹配，所有属性将被阻止.
     */
    public String getSessionAttributeNameFilter() {
        if (sessionAttributeNamePattern == null) {
            return null;
        }
        return sessionAttributeNamePattern.toString();
    }


    /**
     * 设置正则表达式，用于根据属性名称筛选会话属性. 正则表达式被锚定，因此它必须与整个名称匹配.
     *
     * @param sessionAttributeNameFilter 基于属性名称筛选会话属性的正则表达式. {@code null}不使用过滤.
     * 		如果指定了空字符串，则没有名称与筛选器匹配，所有属性将被阻止.
     *
     * @throws PatternSyntaxException 如果表达式无效
     */
    public void setSessionAttributeNameFilter(String sessionAttributeNameFilter)
            throws PatternSyntaxException {
        if (sessionAttributeNameFilter == null || sessionAttributeNameFilter.length() == 0) {
            sessionAttributeNamePattern = null;
        } else {
            sessionAttributeNamePattern = Pattern.compile(sessionAttributeNameFilter);
        }
    }


    /**
     * 提供{@link #getSessionAttributeNameFilter()}作为预编译的正则表达式模式.
     *
     * @return 基于属性名称过滤会话属性的预编译模式. {@code null}不使用任何过滤器.
     */
    protected Pattern getSessionAttributeNamePattern() {
        return sessionAttributeNamePattern;
    }


    /**
     * 基于值的实现类获取用于筛选会话属性的正则表达式. 正则表达式是锚定的，必须与完全限定的类名匹配.
     *
     * @return 当前用来过滤类名的正则表达式. {@code null}不使用任何过滤器. 如果指定了空字符串，则没有名称与筛选器匹配，所有属性将被阻止.
     */
    public String getSessionAttributeValueClassNameFilter() {
        if (sessionAttributeValueClassNamePattern == null) {
            return null;
        }
        return sessionAttributeValueClassNamePattern.toString();
    }


    /**
     * 提供{@link #getSessionAttributeValueClassNameFilter()}作为预编译的正则表达式模式.
     *
     * @return 基于属性名称过滤会话属性的预编译模式. {@code null}不使用任何过滤器.
     */
    protected Pattern getSessionAttributeValueClassNamePattern() {
        return sessionAttributeValueClassNamePattern;
    }


    /**
     * 设置正则表达式用于筛选用于会话属性的类. 正则表达式是锚定的，必须与完全限定的类名匹配.
     *
     * @param sessionAttributeValueClassNameFilter 用于基于类名称过滤会话属性的正则表达式.
     * 		{@code null}不使用任何过滤器. 如果指定了空字符串，则没有名称与筛选器匹配，所有属性将被阻止.
     *
     * @throws PatternSyntaxException 如果表达式无效
     */
    public void setSessionAttributeValueClassNameFilter(String sessionAttributeValueClassNameFilter)
            throws PatternSyntaxException {
        if (sessionAttributeValueClassNameFilter == null ||
                sessionAttributeValueClassNameFilter.length() == 0) {
            sessionAttributeValueClassNamePattern = null;
        } else {
            sessionAttributeValueClassNamePattern =
                    Pattern.compile(sessionAttributeValueClassNameFilter);
        }
    }


    /**
     * 是否应该生成警告级别的日志消息， 如果会话属性不被持久化/复制/恢复.
     *
     * @return {@code true}如果应该生成警告级别日志消息
     */
    public boolean getWarnOnSessionAttributeFilterFailure() {
        return warnOnSessionAttributeFilterFailure;
    }


    /**
     * 配置是否应生成警告级别日志消息, 如果会话属性不被持久化/复制/恢复.
     *
     * @param warnOnSessionAttributeFilterFailure {@code true}如果应该生成警告级别消息
     *
     */
    public void setWarnOnSessionAttributeFilterFailure(
            boolean warnOnSessionAttributeFilterFailure) {
        this.warnOnSessionAttributeFilterFailure = warnOnSessionAttributeFilterFailure;
    }


    @Override
    public Context getContext() {
        return context;
    }


    @Override
    public void setContext(Context context) {
        if (this.context == context) {
            // NO-OP
            return;
        }
        if (!getState().equals(LifecycleState.NEW)) {
            throw new IllegalStateException(sm.getString("managerBase.setContextNotNew"));
        }
        Context oldContext = this.context;
        this.context = context;
        support.firePropertyChange("context", oldContext, this.context);
    }


    /**
     * @return 实现类的名称.
     */
    public String getClassName() {
        return this.getClass().getName();
    }


    @Override
    public SessionIdGenerator getSessionIdGenerator() {
        if (sessionIdGenerator != null) {
            return sessionIdGenerator;
        } else if (sessionIdGeneratorClass != null) {
            try {
                sessionIdGenerator = sessionIdGeneratorClass.getConstructor().newInstance();
                return sessionIdGenerator;
            } catch(ReflectiveOperationException ex) {
                // Ignore
            }
        }
        return null;
    }


    @Override
    public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
        this.sessionIdGenerator = sessionIdGenerator;
        sessionIdGeneratorClass = sessionIdGenerator.getClass();
    }


    /**
     * @return 此Manager实现的描述性短名称.
     */
    public String getName() {
        return (name);
    }

    /**
     * @return 随机数发生器的安全级别.
     */
    public String getSecureRandomClass() {
        return (this.secureRandomClass);
    }


    /**
     * 设置安全随机数生成器类名.
     *
     * @param secureRandomClass 安全随机数生成器类名
     */
    public void setSecureRandomClass(String secureRandomClass) {

        String oldSecureRandomClass = this.secureRandomClass;
        this.secureRandomClass = secureRandomClass;
        support.firePropertyChange("secureRandomClass", oldSecureRandomClass,
                                   this.secureRandomClass);

    }


    /**
     * @return 安全随机数生成器算法名称.
     */
    public String getSecureRandomAlgorithm() {
        return secureRandomAlgorithm;
    }


    /**
     * 设置安全随机数生成器算法名称.
     *
     * @param secureRandomAlgorithm 安全随机数生成器算法名称
     */
    public void setSecureRandomAlgorithm(String secureRandomAlgorithm) {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
    }


    /**
     * @return 安全随机数生成器提供者名称.
     */
    public String getSecureRandomProvider() {
        return secureRandomProvider;
    }


    /**
     * 设置安全随机数生成器提供程序名称.
     *
     * @param secureRandomProvider 安全随机数生成器提供者名称
     */
    public void setSecureRandomProvider(String secureRandomProvider) {
        this.secureRandomProvider = secureRandomProvider;
    }


    @Override
    public int getRejectedSessions() {
        return rejectedSessions;
    }


    @Override
    public long getExpiredSessions() {
        return expiredSessions.get();
    }


    @Override
    public void setExpiredSessions(long expiredSessions) {
        this.expiredSessions.set(expiredSessions);
    }

    public long getProcessingTime() {
        return processingTime;
    }


    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    /**
     * @return 管理器检查的频率.
     */
    public int getProcessExpiresFrequency() {
        return (this.processExpiresFrequency);
    }

    /**
     * 设置管理器检查频率.
     *
     * @param processExpiresFrequency 检查频率
     */
    public void setProcessExpiresFrequency(int processExpiresFrequency) {

        if (processExpiresFrequency <= 0) {
            return;
        }

        int oldProcessExpiresFrequency = this.processExpiresFrequency;
        this.processExpiresFrequency = processExpiresFrequency;
        support.firePropertyChange("processExpiresFrequency",
                                   Integer.valueOf(oldProcessExpiresFrequency),
                                   Integer.valueOf(this.processExpiresFrequency));
    }
    // --------------------------------------------------------- Public Methods


    /**
     * 直接调用{@link #processExpires()}
     */
    @Override
    public void backgroundProcess() {
        count = (count + 1) % processExpiresFrequency;
        if (count == 0)
            processExpires();
    }

    /**
     * 使所有已过期的会话无效.
     */
    public void processExpires() {

        long timeNow = System.currentTimeMillis();
        Session sessions[] = findSessions();
        int expireHere = 0 ;

        if(log.isDebugEnabled())
            log.debug("Start expire sessions " + getName() + " at " + timeNow + " sessioncount " + sessions.length);
        for (int i = 0; i < sessions.length; i++) {
            if (sessions[i]!=null && !sessions[i].isValid()) {
                expireHere++;
            }
        }
        long timeEnd = System.currentTimeMillis();
        if(log.isDebugEnabled())
             log.debug("End expire sessions " + getName() + " processingTime " + (timeEnd - timeNow) + " expired sessions: " + expireHere);
        processingTime += ( timeEnd - timeNow );

    }


    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();

        if (context == null) {
            throw new LifecycleException(sm.getString("managerBase.contextNull"));
        }
    }


    @Override
    protected void startInternal() throws LifecycleException {

        // Ensure caches for timing stats are the right size by filling with
        // nulls.
        while (sessionCreationTiming.size() < TIMING_STATS_CACHE_SIZE) {
            sessionCreationTiming.add(null);
        }
        while (sessionExpirationTiming.size() < TIMING_STATS_CACHE_SIZE) {
            sessionExpirationTiming.add(null);
        }

        /* Create sessionIdGenerator if not explicitly configured */
        SessionIdGenerator sessionIdGenerator = getSessionIdGenerator();
        if (sessionIdGenerator == null) {
            sessionIdGenerator = new StandardSessionIdGenerator();
            setSessionIdGenerator(sessionIdGenerator);
        }

        sessionIdGenerator.setJvmRoute(getJvmRoute());
        if (sessionIdGenerator instanceof SessionIdGeneratorBase) {
            SessionIdGeneratorBase sig = (SessionIdGeneratorBase)sessionIdGenerator;
            sig.setSecureRandomAlgorithm(getSecureRandomAlgorithm());
            sig.setSecureRandomClass(getSecureRandomClass());
            sig.setSecureRandomProvider(getSecureRandomProvider());
        }

        if (sessionIdGenerator instanceof Lifecycle) {
            ((Lifecycle) sessionIdGenerator).start();
        } else {
            // Force initialization of the random number generator
            if (log.isDebugEnabled())
                log.debug("Force random number initialization starting");
            sessionIdGenerator.generateSessionId();
            if (log.isDebugEnabled())
                log.debug("Force random number initialization completed");
        }
    }


    @Override
    protected void stopInternal() throws LifecycleException {
        if (sessionIdGenerator instanceof Lifecycle) {
            ((Lifecycle) sessionIdGenerator).stop();
        }
    }


    @Override
    public void add(Session session) {
        sessions.put(session.getIdInternal(), session);
        int size = getActiveSessions();
        if( size > maxActive ) {
            synchronized(maxActiveUpdateLock) {
                if( size > maxActive ) {
                    maxActive = size;
                }
            }
        }
    }


    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    @Override
    public Session createSession(String sessionId) {

        if ((maxActiveSessions >= 0) &&
                (getActiveSessions() >= maxActiveSessions)) {
            rejectedSessions++;
            throw new TooManyActiveSessionsException(
                    sm.getString("managerBase.createSession.ise"),
                    maxActiveSessions);
        }

        // Recycle or create a Session instance
        Session session = createEmptySession();

        // 初始化新会话的属性并返回它
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(getContext().getSessionTimeout() * 60);
        String id = sessionId;
        if (id == null) {
            id = generateSessionId();
        }
        session.setId(id);
        sessionCounter++;

        SessionTiming timing = new SessionTiming(session.getCreationTime(), 0);
        synchronized (sessionCreationTiming) {
            sessionCreationTiming.add(timing);
            sessionCreationTiming.poll();
        }
        return (session);
    }


    @Override
    public Session createEmptySession() {
        return (getNewSession());
    }


    @Override
    public Session findSession(String id) throws IOException {
        if (id == null) {
            return null;
        }
        return sessions.get(id);
    }


    @Override
    public Session[] findSessions() {
        return sessions.values().toArray(new Session[0]);
    }


    @Override
    public void remove(Session session) {
        remove(session, false);
    }


    @Override
    public void remove(Session session, boolean update) {
        // 如果会话已过期 - 而不是从管理器删除, 因为它被持久化 - 更新过期的统计数据
        if (update) {
            long timeNow = System.currentTimeMillis();
            int timeAlive =
                (int) (timeNow - session.getCreationTimeInternal())/1000;
            updateSessionMaxAliveTime(timeAlive);
            expiredSessions.incrementAndGet();
            SessionTiming timing = new SessionTiming(timeNow, timeAlive);
            synchronized (sessionExpirationTiming) {
                sessionExpirationTiming.add(timing);
                sessionExpirationTiming.poll();
            }
        }

        if (session.getIdInternal() != null) {
            sessions.remove(session.getIdInternal());
        }
    }


    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    @Override
    public void changeSessionId(Session session) {
        String newId = generateSessionId();
        changeSessionId(session, newId, true, true);
    }


    @Override
    public void changeSessionId(Session session, String newId) {
        changeSessionId(session, newId, true, true);
    }


    protected void changeSessionId(Session session, String newId,
            boolean notifySessionListeners, boolean notifyContainerListeners) {
        String oldId = session.getIdInternal();
        session.setId(newId, false);
        session.tellChangedSessionId(newId, oldId,
                notifySessionListeners, notifyContainerListeners);
    }


    /**
     * 此实现排除了来自分布的会话属性，如果:
     * <ul>
     * <li>属性名匹配{@link #getSessionAttributeNameFilter()}</li>
     * </ul>
     */
    @Override
    public boolean willAttributeDistribute(String name, Object value) {
        Pattern sessionAttributeNamePattern = getSessionAttributeNamePattern();
        if (sessionAttributeNamePattern != null) {
            if (!sessionAttributeNamePattern.matcher(name).matches()) {
                if (getWarnOnSessionAttributeFilterFailure() || log.isDebugEnabled()) {
                    String msg = sm.getString("managerBase.sessionAttributeNameFilter",
                            name, sessionAttributeNamePattern);
                    if (getWarnOnSessionAttributeFilterFailure()) {
                        log.warn(msg);
                    } else {
                        log.debug(msg);
                    }
                }
                return false;
            }
        }

        Pattern sessionAttributeValueClassNamePattern = getSessionAttributeValueClassNamePattern();
        if (value != null && sessionAttributeValueClassNamePattern != null) {
            if (!sessionAttributeValueClassNamePattern.matcher(
                    value.getClass().getName()).matches()) {
                if (getWarnOnSessionAttributeFilterFailure() || log.isDebugEnabled()) {
                    String msg = sm.getString("managerBase.sessionAttributeValueClassNameFilter",
                            name, value.getClass().getName(), sessionAttributeValueClassNamePattern);
                    if (getWarnOnSessionAttributeFilterFailure()) {
                        log.warn(msg);
                    } else {
                        log.debug(msg);
                    }
                }
                return false;
            }
        }

        return true;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 获取在doLoad()方法中使用的新会话类.
     */
    protected StandardSession getNewSession() {
        return new StandardSession(this);
    }


    /**
     * 生成并返回新的会话标识符.
     */
    protected String generateSessionId() {

        String result = null;

        do {
            if (result != null) {
                // 不是线程安全的，但是如果多个增量中的一个丢失了，这不是什么大问题，因为有任何重复的事实是一个更大的问题.
                duplicates++;
            }

            result = sessionIdGenerator.generateSessionId();

        } while (sessions.containsKey(result));

        return result;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 检索封闭的Engine.
     *
     * @return an Engine object (or null).
     */
    public Engine getEngine() {
        Engine e = null;
        for (Container c = getContext(); e == null && c != null ; c = c.getParent()) {
            if (c instanceof Engine) {
                e = (Engine)c;
            }
        }
        return e;
    }


    /**
     * 检索封闭的Engine的JvmRoute.
     * 
     * @return the JvmRoute or null.
     */
    public String getJvmRoute() {
        Engine e = getEngine();
        return e == null ? null : e.getJvmRoute();
    }


    // -------------------------------------------------------- Package Methods


    @Override
    public void setSessionCounter(long sessionCounter) {
        this.sessionCounter = sessionCounter;
    }


    @Override
    public long getSessionCounter() {
        return sessionCounter;
    }


    /**
     * 随机源生成的重复会话ID数. 大于0意味着有问题.
     *
     * @return The count of duplicates
     */
    public int getDuplicates() {
        return duplicates;
    }


    public void setDuplicates(int duplicates) {
        this.duplicates = duplicates;
    }


    @Override
    public int getActiveSessions() {
        return sessions.size();
    }


    @Override
    public int getMaxActive() {
        return maxActive;
    }


    @Override
    public void setMaxActive(int maxActive) {
        synchronized (maxActiveUpdateLock) {
            this.maxActive = maxActive;
        }
    }


    /**
     * @return 允许的活动会话的最大数目, 或 -1 不限制.
     */
    public int getMaxActiveSessions() {
        return (this.maxActiveSessions);
    }


    /**
     * 设置允许的活动会话的最大数目, 或 -1 不限制.
     *
     * @param max The new maximum number of sessions
     */
    public void setMaxActiveSessions(int max) {
        int oldMaxActiveSessions = this.maxActiveSessions;
        this.maxActiveSessions = max;
        support.firePropertyChange("maxActiveSessions",
                                   Integer.valueOf(oldMaxActiveSessions),
                                   Integer.valueOf(this.maxActiveSessions));
    }


    @Override
    public int getSessionMaxAliveTime() {
        return sessionMaxAliveTime;
    }


    @Override
    public void setSessionMaxAliveTime(int sessionMaxAliveTime) {
        synchronized (sessionMaxAliveTimeUpdateLock) {
            this.sessionMaxAliveTime = sessionMaxAliveTime;
        }
    }


    /**
     * 更新sessionMaxAliveTime 属性，如果候选值大于当前值.
     *
     * @param sessionAliveTime  sessionMaxAliveTime值 (in seconds).
     */
    public void updateSessionMaxAliveTime(int sessionAliveTime) {
        if (sessionAliveTime > this.sessionMaxAliveTime) {
            synchronized (sessionMaxAliveTimeUpdateLock) {
                if (sessionAliveTime > this.sessionMaxAliveTime) {
                    this.sessionMaxAliveTime = sessionAliveTime;
                }
            }
        }
    }

    /**
     * 基于最后过期的100个会话. 如果少于100个会话已过期，则使用所有可用数据.
     */
    @Override
    public int getSessionAverageAliveTime() {
        // Copy current stats
        List<SessionTiming> copy = new ArrayList<>();
        synchronized (sessionExpirationTiming) {
            copy.addAll(sessionExpirationTiming);
        }

        // Init
        int counter = 0;
        int result = 0;
        Iterator<SessionTiming> iter = copy.iterator();

        // Calculate average
        while (iter.hasNext()) {
            SessionTiming timing = iter.next();
            if (timing != null) {
                int timeAlive = timing.getDuration();
                counter++;
                // 非常小心不要溢出 - 可能不是必要的
                result =
                    (result * ((counter - 1)/counter)) + (timeAlive/counter);
            }
        }
        return result;
    }


    /**
     * 基于先前创建的100个会话的创建时间. 如果少于100个会话，则使用所有可用的数据.
     */
    @Override
    public int getSessionCreateRate() {
        // Copy current stats
        List<SessionTiming> copy = new ArrayList<>();
        synchronized (sessionCreationTiming) {
            copy.addAll(sessionCreationTiming);
        }

        return calculateRate(copy);
    }


    /**
     * 基于之前过期的100个会话的过期时间. 如果少于100个会话已过期，则使用所有可用数据.
     *
     * @return  会话过期的当前速率（每分钟会话数）
     */
    @Override
    public int getSessionExpireRate() {
        // Copy current stats
        List<SessionTiming> copy = new ArrayList<>();
        synchronized (sessionExpirationTiming) {
            copy.addAll(sessionExpirationTiming);
        }

        return calculateRate(copy);
    }


    private static int calculateRate(List<SessionTiming> sessionTiming) {
        // Init
        long now = System.currentTimeMillis();
        long oldest = now;
        int counter = 0;
        int result = 0;
        Iterator<SessionTiming> iter = sessionTiming.iterator();

        // Calculate rate
        while (iter.hasNext()) {
            SessionTiming timing = iter.next();
            if (timing != null) {
                counter++;
                if (timing.getTimestamp() < oldest) {
                    oldest = timing.getTimestamp();
                }
            }
        }
        if (counter > 0) {
            if (oldest < now) {
                result = (1000*60*counter)/(int) (now - oldest);
            } else {
                // Better than reporting zero
                result = Integer.MAX_VALUE;
            }
        }
        return result;
    }


    /**
     * For debugging.
     *
     * @return 当前活动的所有会话ID的空格分隔列表
     */
    public String listSessionIds() {
        StringBuilder sb = new StringBuilder();
        Iterator<String> keys = sessions.keySet().iterator();
        while (keys.hasNext()) {
            sb.append(keys.next()).append(" ");
        }
        return sb.toString();
    }


    /**
     * For debugging.
     *
     * @param sessionId 会话ID
     * @param key       获取属性的键
     *
     * @return 指定会话的属性值, 否则null
     */
    public String getSessionAttribute( String sessionId, String key ) {
        Session s = sessions.get(sessionId);
        if( s==null ) {
            if(log.isInfoEnabled())
                log.info("Session not found " + sessionId);
            return null;
        }
        Object o=s.getSession().getAttribute(key);
        if( o==null ) return null;
        return o.toString();
    }


    /**
     * 返回与给定会话ID有关的会话信息.
     *
     * <p>会话信息作为HashMap, 映射会话属性名称为String.
     *
     * @param sessionId Session id
     *
     * @return HashMap 映射会话属性名称为String, 或 null 如果没有指定ID的会话存在, 或者如果会话没有任何属性
     */
    public HashMap<String, String> getSession(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (log.isInfoEnabled()) {
                log.info("Session not found " + sessionId);
            }
            return null;
        }

        Enumeration<String> ee = s.getSession().getAttributeNames();
        if (ee == null || !ee.hasMoreElements()) {
            return null;
        }

        HashMap<String, String> map = new HashMap<>();
        while (ee.hasMoreElements()) {
            String attrName = ee.nextElement();
            map.put(attrName, getSessionAttribute(sessionId, attrName));
        }

        return map;
    }


    public void expireSession( String sessionId ) {
        Session s=sessions.get(sessionId);
        if( s==null ) {
            if(log.isInfoEnabled())
                log.info("Session not found " + sessionId);
            return;
        }
        s.expire();
    }

    public long getThisAccessedTimestamp( String sessionId ) {
        Session s=sessions.get(sessionId);
        if(s== null)
            return -1 ;
        return s.getThisAccessedTime();
    }

    public String getThisAccessedTime( String sessionId ) {
        Session s=sessions.get(sessionId);
        if( s==null ) {
            if(log.isInfoEnabled())
                log.info("Session not found " + sessionId);
            return "";
        }
        return new Date(s.getThisAccessedTime()).toString();
    }

    public long getLastAccessedTimestamp( String sessionId ) {
        Session s=sessions.get(sessionId);
        if(s== null)
            return -1 ;
        return s.getLastAccessedTime();
    }

    public String getLastAccessedTime( String sessionId ) {
        Session s=sessions.get(sessionId);
        if( s==null ) {
            if(log.isInfoEnabled())
                log.info("Session not found " + sessionId);
            return "";
        }
        return new Date(s.getLastAccessedTime()).toString();
    }

    public String getCreationTime( String sessionId ) {
        Session s=sessions.get(sessionId);
        if( s==null ) {
            if(log.isInfoEnabled())
                log.info("Session not found " + sessionId);
            return "";
        }
        return new Date(s.getCreationTime()).toString();
    }

    public long getCreationTimestamp( String sessionId ) {
        Session s=sessions.get(sessionId);
        if(s== null)
            return -1 ;
        return s.getCreationTime();
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append('[');
        if (context == null) {
            sb.append("Context is null");
        } else {
            sb.append(context.getName());
        }
        sb.append(']');
        return sb.toString();
    }


    // -------------------- JMX and Registration  --------------------
    @Override
    public String getObjectNameKeyProperties() {

        StringBuilder name = new StringBuilder("type=Manager");

        name.append(",host=");
        name.append(context.getParent().getName());

        name.append(",context=");
        String contextName = context.getName();
        if (!contextName.startsWith("/")) {
            name.append('/');
        }
        name.append(contextName);

        return name.toString();
    }

    @Override
    public String getDomainInternal() {
        return context.getDomain();
    }


    // ----------------------------------------------------------- Inner classes

    protected static final class SessionTiming {
        private final long timestamp;
        private final int duration;

        public SessionTiming(long timestamp, int duration) {
            this.timestamp = timestamp;
            this.duration = duration;
        }

        /**
         * @return 与该定时信息相关联的时间戳, in milliseconds.
         */
        public long getTimestamp() {
            return timestamp;
        }

        /**
         * @return 与此定时信息相关的持续时间, in seconds.
         */
        public int getDuration() {
            return duration;
        }
    }
}
