package org.apache.tomcat.util.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

class Jre8Compat extends JreCompat {

    private static final int RUNTIME_MAJOR_VERSION = 8;

    private static final Method setUseCipherSuitesOrderMethod;


    static {
        Method m1 = null;
        try {
            // The class is Java6+...
            Class<?> c1 = Class.forName("javax.net.ssl.SSLParameters");
            // ...but this method is Java8+
            m1 = c1.getMethod("setUseCipherSuitesOrder", boolean.class);
        } catch (SecurityException e) {
            // Should never happen
        } catch (NoSuchMethodException e) {
            // Expected on Java < 8
        } catch (ClassNotFoundException e) {
            // Should never happen
        }
        setUseCipherSuitesOrderMethod = m1;
    }


    static boolean isSupported() {
        return setUseCipherSuitesOrderMethod != null;
    }


    @Override
    public void setUseServerCipherSuitesOrder(SSLEngine engine,
            boolean useCipherSuitesOrder) {
        SSLParameters sslParameters = engine.getSSLParameters();
        try {
            setUseCipherSuitesOrderMethod.invoke(sslParameters,
                    Boolean.valueOf(useCipherSuitesOrder));
            engine.setSSLParameters(sslParameters);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException(e);
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException(e);
        } catch (InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @Override
    public int jarFileRuntimeMajorVersion() {
        return RUNTIME_MAJOR_VERSION;
    }
}
