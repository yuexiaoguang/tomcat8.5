package org.apache.juli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;


/**
 * 每个类加载器LogManager实现.
 *
 * 用于轻型调试, 设置系统属性 <code>org.apache.juli.ClassLoaderLogManager.debug=true</code>.
 * 短配置信息将发送到 <code>System.err</code>.
 */
public class ClassLoaderLogManager extends LogManager {

    private static final boolean isJava9;

    public static final String DEBUG_PROPERTY =
            ClassLoaderLogManager.class.getName() + ".debug";

    static {
        Class<?> c = null;
        try {
            c = Class.forName("java.lang.Runtime$Version");
        } catch (ClassNotFoundException e) {
            // Must be Java 8
        }
        isJava9 = c != null;
    }

    private final class Cleaner extends Thread {

        @Override
        public void run() {
            if (useShutdownHook) {
                shutdown();
            }
        }

    }


    // ------------------------------------------------------------Constructors

    public ClassLoaderLogManager() {
        super();
        try {
            Runtime.getRuntime().addShutdownHook(new Cleaner());
        } catch (IllegalStateException ise) {
            // 可能已经关机了. 忽略这个错误.
        }
    }


    // -------------------------------------------------------------- Variables


    /**
     * 包含类加载器信息, 每个类加载器作为Key.
     * 弱引用的 hashmap 用于确保应用程序重新部署不会泄漏类加载器引用.
     */
    protected final Map<ClassLoader, ClassLoaderLogInfo> classLoaderLoggers = new WeakHashMap<>(); // Guarded by this


    /**
     * 此前缀用于允许为处理程序及其子组件的属性名称使用前缀.
     */
    protected final ThreadLocal<String> prefix = new ThreadLocal<>();


    /**
     * 确定是否使用关闭挂钩执行任何必要的清理，例如在JVM关闭时刷新缓冲的处理程序.
     * 默认是<code>true</code>, 但可能设置为 false, 如果另一个组件确保调用 {@link #shutdown()}.
     */
    protected volatile boolean useShutdownHook = true;


    // ------------------------------------------------------------- Properties


    public boolean isUseShutdownHook() {
        return useShutdownHook;
    }


    public void setUseShutdownHook(boolean useShutdownHook) {
        this.useShutdownHook = useShutdownHook;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 将指定的 Logger 添加到类加载器本地配置中.
     *
     * @param logger 要添加的logger
     */
    @Override
    public synchronized boolean addLogger(final Logger logger) {

        final String loggerName = logger.getName();

        ClassLoader classLoader =
            Thread.currentThread().getContextClassLoader();
        ClassLoaderLogInfo info = getClassLoaderInfo(classLoader);
        if (info.loggers.containsKey(loggerName)) {
            return false;
        }
        info.loggers.put(loggerName, logger);

        // 为新 logger 应用初始级别
        final String levelString = getProperty(loggerName + ".level");
        if (levelString != null) {
            try {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    @Override
                    public Void run() {
                        logger.setLevel(Level.parse(levelString.trim()));
                        return null;
                    }
                });
            } catch (IllegalArgumentException e) {
                // 将级别设置为 null
            }
        }

        // 始终实例化父logger，以便即使在运行时也可以控制日志类别
        int dotIndex = loggerName.lastIndexOf('.');
        if (dotIndex >= 0) {
            final String parentName = loggerName.substring(0, dotIndex);
            Logger.getLogger(parentName);
        }

        // 查找关联的节点
        LogNode node = info.rootNode.findNode(loggerName);
        node.logger = logger;

        // 设置父 logger
        Logger parentLogger = node.findParentLogger();
        if (parentLogger != null) {
            doSetParentLogger(logger, parentLogger);
        }

        // Tell children we are their new parent
        node.setParentLogger(logger);

        // 添加关联的处理器, 如果使用 .handlers 属性定义了某些东西.
        // 这种情况下, 父 logger 的处理器将不会使用
        String handlers = getProperty(loggerName + ".handlers");
        if (handlers != null) {
            logger.setUseParentHandlers(false);
            StringTokenizer tok = new StringTokenizer(handlers, ",");
            while (tok.hasMoreTokens()) {
                String handlerName = (tok.nextToken().trim());
                Handler handler = null;
                ClassLoader current = classLoader;
                while (current != null) {
                    info = classLoaderLoggers.get(current);
                    if (info != null) {
                        handler = info.handlers.get(handlerName);
                        if (handler != null) {
                            break;
                        }
                    }
                    current = current.getParent();
                }
                if (handler != null) {
                    logger.addHandler(handler);
                }
            }
        }

        // 解析 useParentHandlers, 如果 logger 应该委托给它的父级.
        // 不像 java.util.logging, 如果已为 logger 指定了处理器列表，则默认为不委派.
        String useParentHandlersString = getProperty(loggerName + ".useParentHandlers");
        if (Boolean.parseBoolean(useParentHandlersString)) {
            logger.setUseParentHandlers(true);
        }

        return true;
    }


    /**
     * 获取与类加载器本地配置中的指定名称关联的logger.
     * 如果返回 null, 并且调用始于Logger.getLogger, 将使用指定的名称实例化一个新的 logger, 并使用 addLogger 添加.
     *
     * @param name 要检索的 logger 的名称
     */
    @Override
    public synchronized Logger getLogger(final String name) {
        ClassLoader classLoader = Thread.currentThread()
                .getContextClassLoader();
        return getClassLoaderInfo(classLoader).loggers.get(name);
    }


    /**
     * 获取当前在类加载器本地配置中定义的logger名称的枚举.
     */
    @Override
    public synchronized Enumeration<String> getLoggerNames() {
        ClassLoader classLoader = Thread.currentThread()
                .getContextClassLoader();
        return Collections.enumeration(getClassLoaderInfo(classLoader).loggers.keySet());
    }


    /**
     * 获取类加载器本地配置中指定属性的值.
     *
     * @param name 属性名称
     */
    @Override
    public String getProperty(String name) {
        String prefix = this.prefix.get();
        String result = null;

        // 如果定义了前缀，则首先查找带前缀的属性
        if (prefix != null) {
            result = findProperty(prefix + name);
        }

        // 如果没有前缀或没有属性与前缀匹配，请尝试只使用名称
        if (result == null) {
            result = findProperty(name);
        }

        // 简单的属性替换 (大多用于文件夹名)
        if (result != null) {
            result = replace(result);
        }
        return result;
    }


    private synchronized String findProperty(String name) {
        ClassLoader classLoader = Thread.currentThread()
                .getContextClassLoader();
        ClassLoaderLogInfo info = getClassLoaderInfo(classLoader);
        String result = info.props.getProperty(name);
        // 如果找不到该属性, 并且当前的类加载器没有配置 (属性列表为空), 查找父类加载器的属性.
        if ((result == null) && (info.props.isEmpty())) {
            ClassLoader current = classLoader.getParent();
            while (current != null) {
                info = classLoaderLoggers.get(current);
                if (info != null) {
                    result = info.props.getProperty(name);
                    if ((result != null) || (!info.props.isEmpty())) {
                        break;
                    }
                }
                current = current.getParent();
            }
            if (result == null) {
                result = super.getProperty(name);
            }
        }
        return result;
    }

    @Override
    public void readConfiguration()
        throws IOException, SecurityException {

        checkAccess();

        readConfiguration(Thread.currentThread().getContextClassLoader());

    }

    @Override
    public void readConfiguration(InputStream is)
        throws IOException, SecurityException {

        checkAccess();
        reset();

        readConfiguration(is, Thread.currentThread().getContextClassLoader());

    }

    @Override
    public void reset() throws SecurityException {
        Thread thread = Thread.currentThread();
        if (thread.getClass().getName().startsWith(
                "java.util.logging.LogManager$")) {
            // 忽略从 java.util.logging.LogManager.Cleaner 的调用, /因为我们有自己的关闭钩子
            return;
        }
        ClassLoader classLoader = thread.getContextClassLoader();
        ClassLoaderLogInfo clLogInfo = getClassLoaderInfo(classLoader);
        resetLoggers(clLogInfo);
        // 不要调用 super.reset(). 它应该是NO-OP，因为所有 logger 都应该通过该管理器注册.
        // 调用super.reset()时，单元测试中很少出现ConcurrentModificationException，该异常可能导致Web应用程序停止失败.
    }

    /**
     * 关闭记录系统.
     */
    public synchronized void shutdown() {
        // JVM正在关闭. 确保所有类加载器的所有logger都已关闭
        for (ClassLoaderLogInfo clLogInfo : classLoaderLoggers.values()) {
            resetLoggers(clLogInfo);
        }
    }

    // -------------------------------------------------------- Private Methods
    private void resetLoggers(ClassLoaderLogInfo clLogInfo) {
        // 这与LogManager＃resetLogger()的不同之处在于, 我们并不关闭所有记录器的所有处理程序，而只关闭我们的ClassLoaderLogInfo#handlers列表中的处理程序.
        // 这是因为我们的#addLogger()方法可以使用父类加载器中的处理程序，而且关闭当前类加载器不拥有的处理程序也不好.
        synchronized (clLogInfo) {
            for (Logger logger : clLogInfo.loggers.values()) {
                Handler[] handlers = logger.getHandlers();
                for (Handler handler : handlers) {
                    logger.removeHandler(handler);
                }
            }
            for (Handler handler : clLogInfo.handlers.values()) {
                try {
                    handler.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
            clLogInfo.handlers.clear();
        }
    }

    // ------------------------------------------------------ Protected Methods


    /**
     * 检索与指定的类加载器关联的配置. 如果它不存在, 它将被创建.
     *
     * @param classLoader 将检索或构建配置的类加载器
     * @return 日志配置
     */
    protected synchronized ClassLoaderLogInfo getClassLoaderInfo(ClassLoader classLoader) {

        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        ClassLoaderLogInfo info = classLoaderLoggers.get(classLoader);
        if (info == null) {
            final ClassLoader classLoaderParam = classLoader;
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    try {
                        readConfiguration(classLoaderParam);
                    } catch (IOException e) {
                        // Ignore
                    }
                    return null;
                }
            });
            info = classLoaderLoggers.get(classLoader);
        }
        return info;
    }


    /**
     * 读取指定类加载器的配置.
     *
     * @param classLoader 类加载器
     * @throws IOException 读取配置时出错
     */
    protected synchronized void readConfiguration(ClassLoader classLoader)
        throws IOException {

        InputStream is = null;
        // 在容器中使用的URL类加载器的特例: 仅查看本地存储库以避免重新定义logger 20次
        try {
            if (classLoader instanceof WebappProperties) {
                if (((WebappProperties) classLoader).hasLoggingConfig()) {
                    is = classLoader.getResourceAsStream("logging.properties");
                }
            } else if (classLoader instanceof URLClassLoader) {
                URL logConfig = ((URLClassLoader)classLoader).findResource("logging.properties");

                if(null != logConfig) {
                    if(Boolean.getBoolean(DEBUG_PROPERTY))
                        System.err.println(getClass().getName()
                                           + ".readConfiguration(): "
                                           + "Found logging.properties at "
                                           + logConfig);

                    is = classLoader.getResourceAsStream("logging.properties");
                } else {
                    if(Boolean.getBoolean(DEBUG_PROPERTY))
                        System.err.println(getClass().getName()
                                           + ".readConfiguration(): "
                                           + "Found no logging.properties");
                }
            }
        } catch (AccessControlException ace) {
            // 无权在上下文中配置日志记录
            // 记录并继续
            ClassLoaderLogInfo info = classLoaderLoggers.get(ClassLoader.getSystemClassLoader());
            if (info != null) {
                Logger log = info.loggers.get("");
                if (log != null) {
                    Permission perm = ace.getPermission();
                    if (perm instanceof FilePermission && perm.getActions().equals("read")) {
                        log.warning("Reading " + perm.getName() + " is not permitted. See \"per context logging\" in the default catalina.policy file.");
                    }
                    else {
                        log.warning("Reading logging.properties is not permitted in some context. See \"per context logging\" in the default catalina.policy file.");
                        log.warning("Original error was: " + ace.getMessage());
                    }
                }
            }
        }
        if ((is == null) && (classLoader == ClassLoader.getSystemClassLoader())) {
            String configFileStr = System.getProperty("java.util.logging.config.file");
            if (configFileStr != null) {
                try {
                    is = new FileInputStream(replace(configFileStr));
                } catch (IOException e) {
                    System.err.println("Configuration error");
                    e.printStackTrace();
                }
            }
            // 尝试默认的JVM配置
            if (is == null) {
                File defaultFile = new File(new File(System.getProperty("java.home"),
                                                     isJava9 ? "conf" : "lib"),
                    "logging.properties");
                try {
                    is = new FileInputStream(defaultFile);
                } catch (IOException e) {
                    System.err.println("Configuration error");
                    e.printStackTrace();
                }
            }
        }

        Logger localRootLogger = new RootLogger();
        if (is == null) {
            // 检索父类加载器的根 logger
            ClassLoader current = classLoader.getParent();
            ClassLoaderLogInfo info = null;
            while (current != null && info == null) {
                info = getClassLoaderInfo(current);
                current = current.getParent();
            }
            if (info != null) {
                localRootLogger.setParent(info.rootNode.logger);
            }
        }
        ClassLoaderLogInfo info =
            new ClassLoaderLogInfo(new LogNode(null, localRootLogger));
        classLoaderLoggers.put(classLoader, info);

        if (is != null) {
            readConfiguration(is, classLoader);
        }
        addLogger(localRootLogger);

    }


    /**
     * 加载指定的配置.
     *
     * @param is 属性文件的输入流
     * @param classLoader 将加载配置的类加载器
     * 
     * @throws IOException 如果在加载过程中出现错误
     */
    protected synchronized void readConfiguration(InputStream is, ClassLoader classLoader)
        throws IOException {

        ClassLoaderLogInfo info = classLoaderLoggers.get(classLoader);

        try {
            info.props.load(is);
        } catch (IOException e) {
            // Report error
            System.err.println("Configuration error");
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException ioe) {
                // Ignore
            }
        }

        // 为此类加载器的根logger创建处理程序
        String rootHandlers = info.props.getProperty(".handlers");
        String handlers = info.props.getProperty("handlers");
        Logger localRootLogger = info.rootNode.logger;
        if (handlers != null) {
            StringTokenizer tok = new StringTokenizer(handlers, ",");
            while (tok.hasMoreTokens()) {
                String handlerName = (tok.nextToken().trim());
                String handlerClassName = handlerName;
                String prefix = "";
                if (handlerClassName.length() <= 0) {
                    continue;
                }
                // 解析并删除前缀 (前缀以数字开头, 例如 "10WebappFooHandler.")
                if (Character.isDigit(handlerClassName.charAt(0))) {
                    int pos = handlerClassName.indexOf('.');
                    if (pos >= 0) {
                        prefix = handlerClassName.substring(0, pos + 1);
                        handlerClassName = handlerClassName.substring(pos + 1);
                    }
                }
                try {
                    this.prefix.set(prefix);
                    Handler handler = (Handler) classLoader.loadClass(
                            handlerClassName).getConstructor().newInstance();
                    // 该规范强烈暗示应在创建处理程序对象期间完成所有配置.
                    // 这包括设置级别，过滤器，格式化器和编码.
                    this.prefix.set(null);
                    info.handlers.put(handlerName, handler);
                    if (rootHandlers == null) {
                        localRootLogger.addHandler(handler);
                    }
                } catch (Exception e) {
                    // Report error
                    System.err.println("Handler error");
                    e.printStackTrace();
                }
            }

        }

    }


    /**
     * 在两个指定的 logger 之间设置父子关系.
     *
     * @param logger The logger
     * @param parent The parent logger
     */
    protected static void doSetParentLogger(final Logger logger,
            final Logger parent) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                logger.setParent(parent);
                return null;
            }
        });
    }


    /**
     * 给定字符串中的系统属性替换.
     *
     * @param str 原始字符串
     * @return 编辑的字符串
     */
    protected String replace(String str) {
        String result = str;
        int pos_start = str.indexOf("${");
        if (pos_start >= 0) {
            StringBuilder builder = new StringBuilder();
            int pos_end = -1;
            while (pos_start >= 0) {
                builder.append(str, pos_end + 1, pos_start);
                pos_end = str.indexOf('}', pos_start + 2);
                if (pos_end < 0) {
                    pos_end = pos_start - 1;
                    break;
                }
                String propName = str.substring(pos_start + 2, pos_end);

                String replacement = replaceWebApplicationProperties(propName);
                if (replacement == null) {
                    replacement = propName.length() > 0 ? System.getProperty(propName) : null;
                }
                if (replacement != null) {
                    builder.append(replacement);
                } else {
                    builder.append(str, pos_start, pos_end + 1);
                }
                pos_start = str.indexOf("${", pos_end + 1);
            }
            builder.append(str, pos_end + 1, str.length());
            result = builder.toString();
        }
        return result;
    }


    private String replaceWebApplicationProperties(String propName) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl instanceof WebappProperties) {
            WebappProperties wProps = (WebappProperties) cl;
            if ("classloader.webappName".equals(propName)) {
                return wProps.getWebappName();
            } else if ("classloader.hostName".equals(propName)) {
                return wProps.getHostName();
            } else if ("classloader.serviceName".equals(propName)) {
                return wProps.getServiceName();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }


    // ---------------------------------------------------- LogNode Inner Class


    protected static final class LogNode {
        Logger logger;

        final Map<String, LogNode> children = new HashMap<>();

        final LogNode parent;

        LogNode(final LogNode parent, final Logger logger) {
            this.parent = parent;
            this.logger = logger;
        }

        LogNode(final LogNode parent) {
            this(parent, null);
        }

        LogNode findNode(String name) {
            LogNode currentNode = this;
            if (logger.getName().equals(name)) {
                return this;
            }
            while (name != null) {
                final int dotIndex = name.indexOf('.');
                final String nextName;
                if (dotIndex < 0) {
                    nextName = name;
                    name = null;
                } else {
                    nextName = name.substring(0, dotIndex);
                    name = name.substring(dotIndex + 1);
                }
                LogNode childNode = currentNode.children.get(nextName);
                if (childNode == null) {
                    childNode = new LogNode(currentNode);
                    currentNode.children.put(nextName, childNode);
                }
                currentNode = childNode;
            }
            return currentNode;
        }

        Logger findParentLogger() {
            Logger logger = null;
            LogNode node = parent;
            while (node != null && logger == null) {
                logger = node.logger;
                node = node.parent;
            }
            return logger;
        }

        void setParentLogger(final Logger parent) {
            for (final Iterator<LogNode> iter =
                children.values().iterator(); iter.hasNext();) {
                final LogNode childNode = iter.next();
                if (childNode.logger == null) {
                    childNode.setParentLogger(parent);
                } else {
                    doSetParentLogger(childNode.logger, parent);
                }
            }
        }

    }


    // -------------------------------------------- ClassLoaderInfo Inner Class


    protected static final class ClassLoaderLogInfo {
        final LogNode rootNode;
        final Map<String, Logger> loggers = new ConcurrentHashMap<>();
        final Map<String, Handler> handlers = new HashMap<>();
        final Properties props = new Properties();

        ClassLoaderLogInfo(final LogNode rootNode) {
            this.rootNode = rootNode;
        }

    }


    // ------------------------------------------------- RootLogger Inner Class


    /**
     * 需要此类来实例化每个类加载器层次结构的根.
     */
    protected static class RootLogger extends Logger {
        public RootLogger() {
            super("", null);
        }
    }
}
