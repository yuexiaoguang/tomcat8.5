package org.apache.tomcat;

import java.lang.instrument.ClassFileTransformer;

/**
 * 指定能够使用{@link ClassFileTransformer}进行修饰的类加载器.
 * 这些变换器可以检测（或编织）通过此类加载器加载的类的字节码，以改变它们的行为.
 * 当前只有 {@link org.apache.catalina.loader.WebappClassLoaderBase} 实现了这个接口.
 * 这允许Web应用程序框架或与Web应用程序捆绑在一起的JPA提供者根据需要检测Web应用程序类.
 * <p>
 * 您应该始终针对此接口的方法进行编程 (是否使用反射或其他方式). {@code WebappClassLoaderBase}中的方法受默认安全管理器的保护.
 */
public interface InstrumentableClassLoader {

    /**
     * 将指定的类文件转换器添加到此类加载器. 然后，变换器将能够在调用此方法后, 检测由此类加载器加载的任何类的字节码.
     *
     * @param transformer 要添加到类加载器的转换器
     * @throws IllegalArgumentException 如果{@literal transformer} 是 null.
     */
    void addTransformer(ClassFileTransformer transformer);

    /**
     * 从此类加载器中删除指定的类文件转换器.
     * 在调用此方法之后，它将无法再检测由类加载器加载的类的字节码.
     * 但是，在此方法调用之前已由此转换器检测的任何类将保持其已检测状态.
     *
     * @param transformer 要删除的转换器
     */
    void removeTransformer(ClassFileTransformer transformer);

    /**
     * 返回此类加载器的副本，不带任何类文件转换器.
     * 这是Java Persistence API提供者经常用于在没有任何检测的情况下检查实体类的工具,
     * 在{@link ClassFileTransformer}的
     * {@link ClassFileTransformer#transform(ClassLoader, String, Class, java.security.ProtectionDomain, byte[]) transform}方法的上下文中无法保证的东西.
     * <p>
     * 返回的类加载器的资源缓存将被清除，因此不会保留或返回已经检测的类.
     *
     * @return 这个类加载器的无转换器的副本.
     */
    ClassLoader copyWithoutTransformers();

}
