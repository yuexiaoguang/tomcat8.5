package org.apache.juli.logging;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ServiceLoader;
import java.util.logging.LogManager;

/**
 * 这是一个经过修改的LogFactory，它使用简单的基于发现机制的{@link ServiceLoader}，使用默认的JDK日志记录.
 * 使用完整的Commons Logging发现机制的实现可作为Tomcat extras下载的一部分提供.
 *
 * Why? 它试图在更简单的代码（没有发现）和提供灵活性之间取得平衡 - 特别是那些嵌入Tomcat或Tomcat组件的项目 - 是期望的替代日志记录实现.
 *
 * 请注意，此实现不仅仅是JDK日志记录的包装器 (就像原始的 commons-logging 实现).
 * 它增加了2个功能 - 更简单的配置  (这实际上是log4j.properties的一个子集) 和一个不那么难看的格式化程序.
 *
 * --------------
 *
 * 原评论:
 * <p>创建 {@link Log} 实例的工厂, 具有类似于标准Java API（如JAXP）所使用的发现和配置功能.</p>
 *
 * <p><strong>IMPLEMENTATION NOTE</strong> -
 * 此实现主要基于Apache Xerces中找到的SAXParserFactory和DocumentBuilderFactory实现 (对应于JAXP可插拔API).</p>
 */
public class LogFactory {

    private static final LogFactory singleton = new LogFactory();

    private final Constructor<? extends Log> discoveredLogConstructor;

    private LogFactory() {
        // 通过ServiceLoader查看具有构造函数的Log实现，该构造函数采用String名称.
        ServiceLoader<Log> logLoader = ServiceLoader.load(Log.class);
        Constructor<? extends Log> m=null;
        for (Log log: logLoader) {
            Class<? extends Log> c=log.getClass();
            try {
                m=c.getConstructor(String.class);
                break;
            }
            catch (NoSuchMethodException | SecurityException e) {
                throw new Error(e);
            }
        }
        discoveredLogConstructor=m;
    }


    // --------------------------------------------------------- Public Methods

    // 只有这两个方法需要更改才能使用不同的直接 logger.

    /**
     * <p>构建并返回一个<code>Log</code> 实例, 使用工厂当前的配置属性集.</p>
     *
     * <p><strong>NOTE</strong> - 取决于使用的<code>LogFactory</code>实现,
     * 返回的<code>Log</code>实例不确定是否是当前应用的本地, 在具有相同名称参数的后续调用中不确定是否会再次返回.</p>
     *
     * @param name 返回的<code>Log</code>实例的逻辑名称 (此名称的含义仅为正在包装的基础日志记录实现所知)
     *
     * @return 具有所请求名称的日志实例
     *
     * @exception LogConfigurationException 如果无法返回合适的<code>Log</code>实例
     */
    public Log getInstance(String name) throws LogConfigurationException {
        if (discoveredLogConstructor == null) {
            return DirectJDKLog.getInstance(name);
        }

        try {
            return discoveredLogConstructor.newInstance(name);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                InvocationTargetException e) {
            throw new LogConfigurationException(e);
        }
    }


    /**
     * 从指定的类派生名称, 并调用 <code>getInstance(String)</code>.
     *
     * @param clazz 将为其派生合适的日志名称的Class
     *
     * @return A log instance with a name of clazz.getName()
     *
     * @exception LogConfigurationException 如果无法返回合适的<code>Log</code>实例
     */
    public Log getInstance(Class<?> clazz) throws LogConfigurationException {
        return getInstance( clazz.getName());
    }


    // --------------------------------------------------------- Static Methods


    /**
     * <p>构建并返回一个<code>LogFactory</code>实例, 使用以下有序查找过程来确定要加载的实现类的名称.</p>
     * <ul>
     * <li><code>org.apache.commons.logging.LogFactory</code>系统属性.</li>
     * <li>JDK 1.3服务发现机制</li>
     * <li>使用属性文件<code>commons-logging.properties</code>, 如果在此类的类路径中找到.
     *     配置文件是标准的 <code>java.util.Properties</code> 格式, 并包含实现类的完全限定名称, 其中Key是上面定义的系统属性.</li>
     * <li>回退到默认的实现类 (<code>org.apache.commons.logging.impl.LogFactoryImpl</code>).</li>
     * </ul>
     *
     * <p><em>NOTE</em> - 如果属性文件方法使用 <code>LogFactory</code> 实现类, 此文件中定义的所有属性都将在相应的<code> LogFactory </code>实例上设置为配置属性.</p>
     *
     * @return 单例的 LogFactory 实例
     *
     * @exception LogConfigurationException 如果实现类不可用或无法实例化.
     */
    public static LogFactory getFactory() throws LogConfigurationException {
        return singleton;
    }


    /**
     * 返回命名的 logger, 不包括应用程序必须关心工厂.
     *
     * @param clazz 将从中派生日志名称的Class
     *
     * @return A log instance with a name of clazz.getName()
     *
     * @exception LogConfigurationException 如果无法返回合适的<code>Log</code>实例
     */
    public static Log getLog(Class<?> clazz)
        throws LogConfigurationException {
        return (getFactory().getInstance(clazz));

    }


    /**
     * 返回命名的 logger, 不包括应用程序必须关心工厂.
     *
     * @param name 返回的<code>Log</code>实例的逻辑名称 (此名称的含义仅为正在包装的基础日志记录实现所知)
     *
     * @return 具有所请求名称的日志实例
     *
     * @exception LogConfigurationException 如果无法返回合适的<code>Log</code>实例
     */
    public static Log getLog(String name)
        throws LogConfigurationException {
        return (getFactory().getInstance(name));

    }


    /**
     * 释放对先前创建的{@link LogFactory}实例的所有内部引用，这些实例已与指定的类加载器关联, 在每个上面调用实例的<code> release()</code>方法之后.
     *
     * @param classLoader 要为其释放LogFactory的ClassLoader
     */
    public static void release(ClassLoader classLoader) {
        // JULI的日志管理器查看当前的classLoader，因此不需要使用传入的classLoader, 默认实现不是, 所以在这种情况下调用reset会破坏事情
        if (!LogManager.getLogManager().getClass().getName().equals(
                "java.util.logging.LogManager")) {
            LogManager.getLogManager().reset();
        }
    }
}
