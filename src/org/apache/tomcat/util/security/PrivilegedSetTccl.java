package org.apache.tomcat.util.security;

import java.security.PrivilegedAction;

public class PrivilegedSetTccl implements PrivilegedAction<Void> {

    private final ClassLoader cl;
    private final Thread t;

    public PrivilegedSetTccl(ClassLoader cl) {
        this(Thread.currentThread(), cl);
    }

    public PrivilegedSetTccl(Thread t, ClassLoader cl) {
        this.t = t;
        this.cl = cl;
    }


    @Override
    public Void run() {
        t.setContextClassLoader(cl);
        return null;
    }
}