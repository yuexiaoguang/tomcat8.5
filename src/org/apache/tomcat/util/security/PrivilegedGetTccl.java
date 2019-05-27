package org.apache.tomcat.util.security;

import java.security.PrivilegedAction;

public class PrivilegedGetTccl implements PrivilegedAction<ClassLoader> {
    @Override
    public ClassLoader run() {
        return Thread.currentThread().getContextClassLoader();
    }
}


