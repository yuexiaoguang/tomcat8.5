package org.apache.catalina.webresources;

import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.catalina.webresources.war.Handler;

public class TomcatURLStreamHandlerFactory implements URLStreamHandlerFactory {

    private static final String WAR_PROTOCOL = "war";
    private static final String CLASSPATH_PROTOCOL = "classpath";

    // Singleton instance
    private static volatile TomcatURLStreamHandlerFactory instance = null;

    /**
     * 获取单例. 建议调用者检查 {@link #isRegistered()}的值, 在使用返回的实例之前.
     */
    public static TomcatURLStreamHandlerFactory getInstance() {
        getInstanceInternal(true);
        return instance;
    }


    private static TomcatURLStreamHandlerFactory getInstanceInternal(boolean register) {
        // 双重校验锁. OK 因为实例是 volatile.
        if (instance == null) {
            synchronized (TomcatURLStreamHandlerFactory.class) {
                if (instance == null) {
                    instance = new TomcatURLStreamHandlerFactory(register);
                }
            }
        }
        return instance;
    }


    private final boolean registered;

    // 应用程序定义流处理器工厂的列表.
    private final List<URLStreamHandlerFactory> userFactories =
            new CopyOnWriteArrayList<>();

    /**
     * 使用JVM注册这个工厂. 可能会调用多次. 实现确保注册只发生一次.
     *
     * @return <code>true</code> 如果工厂已在JVM注册，或由于此调用成功注册.
     *         <code>false</code> 如果工厂在这个调用之前已经注册.
     */
    public static boolean register() {
        return getInstanceInternal(true).isRegistered();
    }


    /**
     * 防止这个工厂用JVM注册. 可能会调用多次.
     *
     * @return <code>true</code> 如果该工厂已被禁用或由于该调用而被成功禁用.
     *         <code>false</code>如果工厂在这个调用之前被禁用.

     */
    public static boolean disable() {
        return !getInstanceInternal(false).isRegistered();
    }


    /**
     * 释放对使用提供的类加载器加载的任何用户提供的工厂的引用. 在Web应用程序停止期间调用以防止内存泄漏.
     *
     * @param classLoader 要释放的类加载器
     */
    public static void release(ClassLoader classLoader) {
        if (instance == null) {
            return;
        }
        List<URLStreamHandlerFactory> factories = instance.userFactories;
        for (URLStreamHandlerFactory factory : factories) {
            ClassLoader factoryLoader = factory.getClass().getClassLoader();
            while (factoryLoader != null) {
                if (classLoader.equals(factoryLoader)) {
                    // Implementation note: userFactories 是一个 CopyOnWriteArrayList, 因此条目使用List.remove() 删除, 而不是 Iterator.remove()
                    factories.remove(factory);
                    break;
                }
                factoryLoader = factoryLoader.getParent();
            }
        }
    }


    private TomcatURLStreamHandlerFactory(boolean register) {
        // 隐藏默认的构造器
        // 单例模式以确保该工厂只有一个实例
        this.registered = register;
        if (register) {
            URL.setURLStreamHandlerFactory(this);
        }
    }


    public boolean isRegistered() {
        return registered;
    }


    /**
     * 由于 JVM 只允许单个调用{@link URL#setURLStreamHandlerFactory(URLStreamHandlerFactory)}, 
     * 而且Tomcat 需要注册一个处理器, 提供一个机制允许应用注册自己的处理器.
     *
     * @param factory 用户提供工厂向工厂添加已经注册的Tomcat
     */
    public void addUserFactory(URLStreamHandlerFactory factory) {
        userFactories.add(factory);
    }


    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {

        // Tomcat的处理程序总是优先考虑的，所以应用程序不能覆盖它.
        if (WAR_PROTOCOL.equals(protocol)) {
            return new Handler();
        } else if (CLASSPATH_PROTOCOL.equals(protocol)) {
            return new ClasspathURLStreamHandler();
        }

        // 应用程序处理器
        for (URLStreamHandlerFactory factory : userFactories) {
            URLStreamHandler handler =
                factory.createURLStreamHandler(protocol);
            if (handler != null) {
                return handler;
            }
        }

        // 未知的协议
        return null;
    }
}
