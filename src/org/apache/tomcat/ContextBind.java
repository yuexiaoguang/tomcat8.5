package org.apache.tomcat;

public interface ContextBind {

    /**
     * 将当前线程上下文类加载器更改为Web应用程序类加载器.
     * 如果没有定义Web应用程序类加载器, 或者如果当前线程已经在使用Web应用程序类加载器, 则不会进行任何更改.
     * 如果类加载器被修改, 而且配置了一个 {@link org.apache.catalina.ThreadBindingListener},
     * 那么将在修改之后调用 {@link org.apache.catalina.ThreadBindingListener#bind()}.
     *
     * @param usePrivilegedAction 获取当前线程上下文类加载器并设置新加载器时, 是否使用 {@link java.security.PrivilegedAction}?
     * @param originalClassLoader 当前类加载器，已知保存此方法必须查找它
     *
     * @return 如果类加载器已被方法更改，将在调用方法时返回正在使用的线程上下文类加载器. 如果未进行任何更改，则此方法返回null.
     */
    ClassLoader bind(boolean usePrivilegedAction, ClassLoader originalClassLoader);

    /**
     * 将当前线程上下文类加载器恢复为使用中的原始类加载器, 在调用 {@link #bind(boolean, ClassLoader)} 之前.
     * 如果没有将原始类加载器传递给此方法，则不会进行任何更改.
     * 如果类加载器被修改, 而且配置了一个{@link org.apache.catalina.ThreadBindingListener},
     * 那么将在修改之前调用{@link org.apache.catalina.ThreadBindingListener#unbind()}.
     *
     * @param usePrivilegedAction 设置当前线程上下文类加载器时, 是否使用 {@link java.security.PrivilegedAction}?
     * @param originalClassLoader 作为线程上下文类加载器恢复的类加载器
     */
    void unbind(boolean usePrivilegedAction, ClassLoader originalClassLoader);
}
