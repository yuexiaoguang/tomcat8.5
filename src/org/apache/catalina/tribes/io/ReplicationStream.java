package org.apache.catalina.tribes.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

import org.apache.catalina.tribes.util.StringManager;

/**
 * 自定义的<code>ObjectInputStream</code>的子类, 从这个Web应用的类加载器加载. 这只允许对Web应用程序定义的类进行正确查找.
 */
public final class ReplicationStream extends ObjectInputStream {

    static final StringManager sm = StringManager.getManager(ReplicationStream.class);

    /**
     * 用于解析类的类加载器.
     */
    private ClassLoader[] classLoaders = null;

    /**
     * @param stream 将读取的输入流
     * @param classLoaders 用于实例化对象的类加载器数组
     *
     * @exception IOException 如果发生输入/输出错误
     */
    public ReplicationStream(InputStream stream,
                             ClassLoader[] classLoaders)
        throws IOException {

        super(stream);
        this.classLoaders = classLoaders;
    }

    /**
     * 加载指定的本地类, 通过使用赋值给这个Context的类加载器.
     *
     * @param classDesc 输入流的类描述
     *
     * @exception ClassNotFoundException 如果找不到这个类
     * @exception IOException 如果发生输入/输出错误
     */
    @Override
    public Class<?> resolveClass(ObjectStreamClass classDesc)
        throws ClassNotFoundException, IOException {
        String name = classDesc.getName();
        try {
            return resolveClass(name);
        } catch (ClassNotFoundException e) {
            return super.resolveClass(classDesc);
        }
    }

    public Class<?> resolveClass(String name) throws ClassNotFoundException {

        boolean tryRepFirst = name.startsWith("org.apache.catalina.tribes");
            try {
            if (tryRepFirst)
                return findReplicationClass(name);
            else
                return findExternalClass(name);
        } catch (Exception x) {
            if (tryRepFirst)
                return findExternalClass(name);
            else
                return findReplicationClass(name);
        }
    }

    /**
     * ObjectInputStream.resolveProxyClass 使用错误的类加载器来解析代理类的一些方法, 让我们用我们的方式
     */
    @Override
    protected Class<?> resolveProxyClass(String[] interfaces)
            throws IOException, ClassNotFoundException {

        ClassLoader latestLoader;
        if (classLoaders != null && classLoaders.length > 0) {
            latestLoader = classLoaders[0];
        } else {
            latestLoader = null;
        }
        ClassLoader nonPublicLoader = null;
        boolean hasNonPublicInterface = false;

        // 在非公共接口的类加载器中定义代理
        Class<?>[] classObjs = new Class[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            Class<?> cl = this.resolveClass(interfaces[i]);
            if (latestLoader==null) latestLoader = cl.getClassLoader();
            if ((cl.getModifiers() & Modifier.PUBLIC) == 0) {
                if (hasNonPublicInterface) {
                    if (nonPublicLoader != cl.getClassLoader()) {
                        throw new IllegalAccessError(
                                sm.getString("replicationStream.conflict"));
                    }
                } else {
                    nonPublicLoader = cl.getClassLoader();
                    hasNonPublicInterface = true;
                }
            }
            classObjs[i] = cl;
        }
        try {
            return Proxy.getProxyClass(hasNonPublicInterface ? nonPublicLoader
                    : latestLoader, classObjs);
        } catch (IllegalArgumentException e) {
            throw new ClassNotFoundException(null, e);
        }
    }


    public Class<?> findReplicationClass(String name)
            throws ClassNotFoundException {
        Class<?> clazz = Class.forName(name, false, getClass().getClassLoader());
        return clazz;
    }

    public Class<?> findExternalClass(String name) throws ClassNotFoundException  {
        ClassNotFoundException cnfe = null;
        for (int i=0; i<classLoaders.length; i++ ) {
            try {
                Class<?> clazz = Class.forName(name, false, classLoaders[i]);
                return clazz;
            } catch ( ClassNotFoundException x ) {
                cnfe = x;
            }
        }
        if ( cnfe != null ) throw cnfe;
        else throw new ClassNotFoundException(name);
    }

    @Override
    public void close() throws IOException  {
        this.classLoaders = null;
        super.close();
    }
}
