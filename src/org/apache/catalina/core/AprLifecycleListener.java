package org.apache.catalina.core;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.Library;
import org.apache.tomcat.jni.LibraryNotFoundError;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;


/**
 * <code>LifecycleListener</code>实现类将初始化和销毁 APR.
 */
public class AprLifecycleListener
    implements LifecycleListener {

    private static final Log log = LogFactory.getLog(AprLifecycleListener.class);
    private static boolean instanceCreated = false;
    /**
     * 缓存init()直到Lifecycle.BEFORE_INIT_EVENT期间的信息.
     * 因此, 没有错误的情况下, init() 相关日志消息出现在生命周期中的预期点.
     */
    private static final List<String> initInfoLogMessages = new ArrayList<>(3);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // ---------------------------------------------- Constants


    protected static final int TCN_REQUIRED_MAJOR = 1;
    protected static final int TCN_REQUIRED_MINOR = 2;
    protected static final int TCN_REQUIRED_PATCH = 14;
    protected static final int TCN_RECOMMENDED_MINOR = 2;
    protected static final int TCN_RECOMMENDED_PV = 14;


    // ---------------------------------------------- Properties
    protected static String SSLEngine = "on"; //default on
    protected static String FIPSMode = "off"; // default off, valid only when SSLEngine="on"
    protected static String SSLRandomSeed = "builtin";
    protected static boolean sslInitialized = false;
    protected static boolean aprInitialized = false;
    protected static boolean aprAvailable = false;
    protected static boolean useAprConnector = false;
    protected static boolean useOpenSSL = true;
    protected static boolean fipsModeActive = false;

    /**
     * 使用的"FIPS mode"等级作为 OpenSSL方法<code>FIPS_mode_set()</code>的参数，来启用FIPS 模式.
     * 当FIPS启用的时候，<code>FIPS_mode()</code>返回这个值.
     * <p>
     * 在未来，OpenSSL库可能会支持不同的非零“FIPS”模式，这些模式指定不同的密码子集或其他任何子集, 但现在只支持“1”.
     * </p>
     * @see <a href="http://wiki.openssl.org/index.php/FIPS_mode_set%28%29">OpenSSL method FIPS_mode_set()</a>
     * @see <a href="http://wiki.openssl.org/index.php/FIPS_mode%28%29">OpenSSL method FIPS_mode()</a>
     */
    private static final int FIPS_ON = 1;

    private static final int FIPS_OFF = 0;

    protected static final Object lock = new Object();

    public static boolean isAprAvailable() {
        //https://bz.apache.org/bugzilla/show_bug.cgi?id=48613
        if (instanceCreated) {
            synchronized (lock) {
                init();
            }
        }
        return aprAvailable;
    }

    public AprLifecycleListener() {
        instanceCreated = true;
    }

    // ---------------------------------------------- LifecycleListener Methods

    /**
     * 启动和关闭事件的主要入口点.
     *
     * @param event 发生的事件
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        if (Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {
            synchronized (lock) {
                init();
                for (String msg : initInfoLogMessages) {
                    log.info(msg);
                }
                initInfoLogMessages.clear();
                if (aprAvailable) {
                    try {
                        initializeSSL();
                    } catch (Throwable t) {
                        t = ExceptionUtils.unwrapInvocationTargetException(t);
                        ExceptionUtils.handleThrowable(t);
                        log.error(sm.getString("aprListener.sslInit"), t);
                    }
                }
                // 初始化FIPS模式失败是致命的
                if (!(null == FIPSMode || "off".equalsIgnoreCase(FIPSMode)) && !isFIPSModeActive()) {
                    Error e = new Error(
                            sm.getString("aprListener.initializeFIPSFailed"));
                    // 在这里记录，因为抛出错误可能没有记录
                    log.fatal(e.getMessage(), e);
                    throw e;
                }
            }
        } else if (Lifecycle.AFTER_DESTROY_EVENT.equals(event.getType())) {
            synchronized (lock) {
                if (!aprAvailable) {
                    return;
                }
                try {
                    terminateAPR();
                } catch (Throwable t) {
                    t = ExceptionUtils.unwrapInvocationTargetException(t);
                    ExceptionUtils.handleThrowable(t);
                    log.info(sm.getString("aprListener.aprDestroy"));
                }
            }
        }

    }

    private static void terminateAPR()
        throws ClassNotFoundException, NoSuchMethodException,
               IllegalAccessException, InvocationTargetException
    {
        String methodName = "terminate";
        Method method = Class.forName("org.apache.tomcat.jni.Library")
            .getMethod(methodName, (Class [])null);
        method.invoke(null, (Object []) null);
        aprAvailable = false;
        aprInitialized = false;
        sslInitialized = false; // Well we cleaned the pool in terminate.
        fipsModeActive = false;
    }

    private static void init()
    {
        int major = 0;
        int minor = 0;
        int patch = 0;
        int apver = 0;
        int rqver = TCN_REQUIRED_MAJOR * 1000 + TCN_REQUIRED_MINOR * 100 + TCN_REQUIRED_PATCH;
        int rcver = TCN_REQUIRED_MAJOR * 1000 + TCN_RECOMMENDED_MINOR * 100 + TCN_RECOMMENDED_PV;

        if (aprInitialized) {
            return;
        }
        aprInitialized = true;

        try {
            Library.initialize(null);
            major = Library.TCN_MAJOR_VERSION;
            minor = Library.TCN_MINOR_VERSION;
            patch = Library.TCN_PATCH_VERSION;
            apver = major * 1000 + minor * 100 + patch;
        } catch (LibraryNotFoundError lnfe) {
            // Library not on path
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("aprListener.aprInitDebug",
                        lnfe.getLibraryNames(), System.getProperty("java.library.path"),
                        lnfe.getMessage()), lnfe);
            }
            initInfoLogMessages.add(sm.getString("aprListener.aprInit",
                    System.getProperty("java.library.path")));
            return;
        } catch (Throwable t) {
            // Library present but failed to load
            t = ExceptionUtils.unwrapInvocationTargetException(t);
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString("aprListener.aprInitError", t.getMessage()), t);
            return;
        }
        if (apver < rqver) {
            log.error(sm.getString("aprListener.tcnInvalid", major + "."
                    + minor + "." + patch,
                    TCN_REQUIRED_MAJOR + "." +
                    TCN_REQUIRED_MINOR + "." +
                    TCN_REQUIRED_PATCH));
            try {
                // 在版本低于要求的情况下终止APR.
                terminateAPR();
            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
            }
            return;
        }
        if (apver < rcver) {
            initInfoLogMessages.add(sm.getString("aprListener.tcnVersion",
                    major + "." + minor + "." + patch,
                    TCN_REQUIRED_MAJOR + "." +
                    TCN_RECOMMENDED_MINOR + "." +
                    TCN_RECOMMENDED_PV));
        }

        initInfoLogMessages.add(sm.getString("aprListener.tcnValid",
                major + "." + minor + "." + patch,
                Library.APR_MAJOR_VERSION + "." +
                Library.APR_MINOR_VERSION + "." +
                Library.APR_PATCH_VERSION));

        // Log APR flags
        initInfoLogMessages.add(sm.getString("aprListener.flags",
                Boolean.valueOf(Library.APR_HAVE_IPV6),
                Boolean.valueOf(Library.APR_HAS_SENDFILE),
                Boolean.valueOf(Library.APR_HAS_SO_ACCEPTFILTER),
                Boolean.valueOf(Library.APR_HAS_RANDOM)));

        initInfoLogMessages.add(sm.getString("aprListener.config",
                Boolean.valueOf(useAprConnector),
                Boolean.valueOf(useOpenSSL)));

        aprAvailable = true;
    }

    private static void initializeSSL() throws Exception {

        if ("off".equalsIgnoreCase(SSLEngine)) {
            return;
        }
        if (sslInitialized) {
             //only once per VM
            return;
        }

        sslInitialized = true;

        String methodName = "randSet";
        Class<?> paramTypes[] = new Class[1];
        paramTypes[0] = String.class;
        Object paramValues[] = new Object[1];
        paramValues[0] = SSLRandomSeed;
        Class<?> clazz = Class.forName("org.apache.tomcat.jni.SSL");
        Method method = clazz.getMethod(methodName, paramTypes);
        method.invoke(null, paramValues);


        methodName = "initialize";
        paramValues[0] = "on".equalsIgnoreCase(SSLEngine)?null:SSLEngine;
        method = clazz.getMethod(methodName, paramTypes);
        method.invoke(null, paramValues);

        if (!(null == FIPSMode || "off".equalsIgnoreCase(FIPSMode))) {

            fipsModeActive = false;

            final boolean enterFipsMode;
            int fipsModeState = SSL.fipsModeGet();

            if(log.isDebugEnabled()) {
                log.debug(sm.getString("aprListener.currentFIPSMode",
                                       Integer.valueOf(fipsModeState)));
            }

            if ("on".equalsIgnoreCase(FIPSMode)) {
                if (fipsModeState == FIPS_ON) {
                    log.info(sm.getString("aprListener.skipFIPSInitialization"));
                    fipsModeActive = true;
                    enterFipsMode = false;
                } else {
                    enterFipsMode = true;
                }
            } else if ("require".equalsIgnoreCase(FIPSMode)) {
                if (fipsModeState == FIPS_ON) {
                    fipsModeActive = true;
                    enterFipsMode = false;
                } else {
                    throw new IllegalStateException(
                            sm.getString("aprListener.requireNotInFIPSMode"));
                }
            } else if ("enter".equalsIgnoreCase(FIPSMode)) {
                if (fipsModeState == FIPS_OFF) {
                    enterFipsMode = true;
                } else {
                    throw new IllegalStateException(sm.getString(
                            "aprListener.enterAlreadyInFIPSMode",
                            Integer.valueOf(fipsModeState)));
                }
            } else {
                throw new IllegalArgumentException(sm.getString(
                        "aprListener.wrongFIPSMode", FIPSMode));
            }

            if (enterFipsMode) {
                log.info(sm.getString("aprListener.initializingFIPS"));

                fipsModeState = SSL.fipsModeSet(FIPS_ON);
                if (fipsModeState != FIPS_ON) {
                    // 这种情况应该用native方法处理, 但绝对可以肯定.
                    String message = sm.getString("aprListener.initializeFIPSFailed");
                    log.error(message);
                    throw new IllegalStateException(message);
                }

                fipsModeActive = true;
                log.info(sm.getString("aprListener.initializeFIPSSuccess"));
            }
        }

        log.info(sm.getString("aprListener.initializedOpenSSL", SSL.versionString()));
    }

    public String getSSLEngine() {
        return SSLEngine;
    }

    public void setSSLEngine(String SSLEngine) {
        if (!SSLEngine.equals(AprLifecycleListener.SSLEngine)) {
            // 确保SSLEngine与SSL初始化一致
            if (sslInitialized) {
                throw new IllegalStateException(
                        sm.getString("aprListener.tooLateForSSLEngine"));
            }

            AprLifecycleListener.SSLEngine = SSLEngine;
        }
    }

    public String getSSLRandomSeed() {
        return SSLRandomSeed;
    }

    public void setSSLRandomSeed(String SSLRandomSeed) {
        if (!SSLRandomSeed.equals(AprLifecycleListener.SSLRandomSeed)) {
            // 确保随机种子与SSL init使用的种子一致
            if (sslInitialized) {
                throw new IllegalStateException(
                        sm.getString("aprListener.tooLateForSSLRandomSeed"));
            }

            AprLifecycleListener.SSLRandomSeed = SSLRandomSeed;
        }
    }

    public String getFIPSMode() {
        return FIPSMode;
    }

    public void setFIPSMode(String FIPSMode) {
        if (!FIPSMode.equals(AprLifecycleListener.FIPSMode)) {
            // 确保FIPS模式和SSL init使用的一致
            if (sslInitialized) {
                throw new IllegalStateException(
                        sm.getString("aprListener.tooLateForFIPSMode"));
            }

            AprLifecycleListener.FIPSMode = FIPSMode;
        }
    }

    public boolean isFIPSModeActive() {
        return fipsModeActive;
    }

    public void setUseAprConnector(boolean useAprConnector) {
        if (useAprConnector != AprLifecycleListener.useAprConnector) {
            AprLifecycleListener.useAprConnector = useAprConnector;
        }
    }

    public static boolean getUseAprConnector() {
        return useAprConnector;
    }

    public void setUseOpenSSL(boolean useOpenSSL) {
        if (useOpenSSL != AprLifecycleListener.useOpenSSL) {
            AprLifecycleListener.useOpenSSL = useOpenSSL;
        }
    }

    public static boolean getUseOpenSSL() {
        return useOpenSSL;
    }

}
