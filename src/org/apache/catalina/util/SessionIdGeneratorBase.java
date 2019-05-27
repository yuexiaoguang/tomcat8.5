package org.apache.catalina.util;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.SessionIdGenerator;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public abstract class SessionIdGeneratorBase extends LifecycleBase
        implements SessionIdGenerator {

    private static final Log log = LogFactory.getLog(SessionIdGeneratorBase.class);


    private static final StringManager sm =
        StringManager.getManager("org.apache.catalina.util");


    /**
     * 要使用的随机数生成器的队列, 当创建会话标识符的时候.
     * 如果需要随机数生成器, 则队列为空, 将创建一个新的随机数生成器对象.
     * 随机数生成器使用同步使线程安全, 而且同步使用单例对象 slow(er).
     */
    private final Queue<SecureRandom> randoms = new ConcurrentLinkedQueue<>();

    private String secureRandomClass = null;

    private String secureRandomAlgorithm = "SHA1PRNG";

    private String secureRandomProvider = null;


    /** 集群节点标识符. */
    private String jvmRoute = "";


    /** session ID的字节数. 默认为 16. */
    private int sessionIdLength = 16;


    /**
     * 获取用于生成session ID的{@link SecureRandom}实现类的类名.
     *
     * @return 完全限定类名. 返回{@code null} 将使用JRE 提供的 {@link SecureRandom}实现类
     */
    public String getSecureRandomClass() {
        return secureRandomClass;
    }


    /**
     * 指定一个非默认的 {@link SecureRandom}实现类. 实现必须是self-seeding，并且具有零参数构造函数.
     * 如果未指定, 将生成 {@link SecureRandom}实例.
     *
     * @param secureRandomClass 完全限定类名
     */
    public void setSecureRandomClass(String secureRandomClass) {
        this.secureRandomClass = secureRandomClass;
    }


    /**
     * 获取用于创建生成session ID的{@link SecureRandom}实例的算法的名称.
     *
     * @return 算法名称. {@code null} 或空字符串意味着将使用平台默认的
     */
    public String getSecureRandomAlgorithm() {
        return secureRandomAlgorithm;
    }


    /**
     * 指定一个非默认的用于创建生成session ID的 {@link SecureRandom}实现类.
     * 如果未指定, 将使用SHA1PRNG. 为了使用平台默认的(可能是 SHA1PRNG), 指定 {@code null} 或空字符串.
     * 如果指定了无效的算法和/或供应者, 将为这个{@link SessionIdGenerator}实现类使用默认的创建 {@link SecureRandom}实例.
     * 如果失败, 将使用平台默认的创建  {@link SecureRandom} 实例.
     *
     * @param secureRandomAlgorithm 算法名称
     */
    public void setSecureRandomAlgorithm(String secureRandomAlgorithm) {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
    }


    /**
     * 获取用于创建生成session ID的{@link SecureRandom}实例的提供者的名称.
     *
     * @return 提供者的名称. {@code null} 或空字符串将使用平台默认的
     */
    public String getSecureRandomProvider() {
        return secureRandomProvider;
    }


    /**
     * 指定一个非默认的用于创建生成session ID的 {@link SecureRandom}实现类.
     * 如果未指定提供者, 使用平台默认的. 为了使用平台默认的, 指定 {@code null} 或空字符串.
     * 如果指定了无效的算法和/或供应者, 将为这个{@link SessionIdGenerator}实现类使用默认的创建 {@link SecureRandom}实例.
     * 如果失败, 将使用平台默认的创建  {@link SecureRandom} 实例.
     *
     * @param secureRandomProvider  提供者的名称
     */
    public void setSecureRandomProvider(String secureRandomProvider) {
        this.secureRandomProvider = secureRandomProvider;
    }


    /**
     * 返回与此节点相关联的节点标识符, 该节点标识符将包含在生成的会话ID中.
     */
    @Override
    public String getJvmRoute() {
        return jvmRoute;
    }


    /**
     * 指定与此节点相关联的节点标识符, 该节点标识符将包含在生成的会话ID中.
     *
     * @param jvmRoute  节点标识符
     */
    @Override
    public void setJvmRoute(String jvmRoute) {
        this.jvmRoute = jvmRoute;
    }


    /**
     * 返回会话ID的字节数
     */
    @Override
    public int getSessionIdLength() {
        return sessionIdLength;
    }


    /**
     * 指定会话ID的字节数
     *
     * @param sessionIdLength   字节数
     */
    @Override
    public void setSessionIdLength(int sessionIdLength) {
        this.sessionIdLength = sessionIdLength;
    }


    /**
     * 生成并返回新的会话标识符.
     */
    @Override
    public String generateSessionId() {
        return generateSessionId(jvmRoute);
    }


    protected void getRandomBytes(byte bytes[]) {

        SecureRandom random = randoms.poll();
        if (random == null) {
            random = createSecureRandom();
        }
        random.nextBytes(bytes);
        randoms.add(random);
    }


    /**
     * 创建一个新的随机数生成器实例, 用于生成会话标识符.
     */
    private SecureRandom createSecureRandom() {

        SecureRandom result = null;

        long t1 = System.currentTimeMillis();
        if (secureRandomClass != null) {
            try {
                // 构造一个新的随机数生成器
                Class<?> clazz = Class.forName(secureRandomClass);
                result = (SecureRandom) clazz.getConstructor().newInstance();
            } catch (Exception e) {
                log.error(sm.getString("sessionIdGeneratorBase.random",
                        secureRandomClass), e);
            }
        }

        boolean error = false;
        if (result == null) {
            // No secureRandomClass or creation failed. Use SecureRandom.
            try {
                if (secureRandomProvider != null &&
                        secureRandomProvider.length() > 0) {
                    result = SecureRandom.getInstance(secureRandomAlgorithm,
                            secureRandomProvider);
                } else if (secureRandomAlgorithm != null &&
                        secureRandomAlgorithm.length() > 0) {
                    result = SecureRandom.getInstance(secureRandomAlgorithm);
                }
            } catch (NoSuchAlgorithmException e) {
                error = true;
                log.error(sm.getString("sessionIdGeneratorBase.randomAlgorithm",
                        secureRandomAlgorithm), e);
            } catch (NoSuchProviderException e) {
                error = true;
                log.error(sm.getString("sessionIdGeneratorBase.randomProvider",
                        secureRandomProvider), e);
            }
        }

        if (result == null && error) {
            // Invalid provider / algorithm
            try {
                result = SecureRandom.getInstance("SHA1PRNG");
            } catch (NoSuchAlgorithmException e) {
                log.error(sm.getString("sessionIdGeneratorBase.randomAlgorithm",
                        secureRandomAlgorithm), e);
            }
        }

        if (result == null) {
            // Nothing works - use platform default
            result = new SecureRandom();
        }

        // Force seeding to take place
        result.nextInt();

        long t2 = System.currentTimeMillis();
        if ((t2 - t1) > 100) {
            log.warn(sm.getString("sessionIdGeneratorBase.createRandom",
                    result.getAlgorithm(), Long.valueOf(t2 - t1)));
        }
        return result;
    }


    @Override
    protected void initInternal() throws LifecycleException {
        // NO-OP
    }


    @Override
    protected void startInternal() throws LifecycleException {
        // 确保已经初始化 SecureRandom
        generateSessionId();

        setState(LifecycleState.STARTING);
    }


    @Override
    protected void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
        randoms.clear();
    }


    @Override
    protected void destroyInternal() throws LifecycleException {
        // NO-OP
    }
}
