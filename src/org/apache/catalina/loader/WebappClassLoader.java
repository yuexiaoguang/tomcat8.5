package org.apache.catalina.loader;

import org.apache.catalina.LifecycleException;

public class WebappClassLoader extends WebappClassLoaderBase {

    public WebappClassLoader() {
        super();
    }


    public WebappClassLoader(ClassLoader parent) {
        super(parent);
    }


    /**
     * 返回这个类加载器的副本, 不包括任何类文件转换器.
     * 这是Java Persistence API提供者经常使用的工具，在没有任何仪器的情况下检查实体类, 不保证
     * {@link java.lang.instrument.ClassFileTransformer}的
     * {@link java.lang.instrument.ClassFileTransformer#transform(ClassLoader,
     * String, Class, java.security.ProtectionDomain, byte[]) transform}方法的上下文.
     * <p>
     * 返回的类加载器的资源缓存将被清除，因此，已检测的类将不被保留或返回.
     *
     * @return the transformer-free copy of this class loader.
     */
    @Override
    public WebappClassLoader copyWithoutTransformers() {

        WebappClassLoader result = new WebappClassLoader(getParent());

        super.copyStateWithoutTransformers(result);

        try {
            result.start();
        } catch (LifecycleException e) {
            throw new IllegalStateException(e);
        }

        return result;
    }


    /**
     * 这个类加载器没有并行的能力，所以锁在类加载器上，而不是每个类上.
     */
    @Override
    protected Object getClassLoadingLock(String className) {
        return this;
    }
}
