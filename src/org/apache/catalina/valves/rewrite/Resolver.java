package org.apache.catalina.valves.rewrite;

import java.nio.charset.Charset;

/**
 * Resolver抽象类.
 */
public abstract class Resolver {

    public abstract String resolve(String key);

    public String resolveEnv(String key) {
        return System.getProperty(key);
    }

    public abstract String resolveSsl(String key);

    public abstract String resolveHttp(String key);

    public abstract boolean resolveResource(int type, String name);

    /**
     * @return The name of the encoding to use to %nn encode URIs
     *
     * @deprecated This will be removed in Tomcat 9.0.x
     */
    @Deprecated
    public abstract String getUriEncoding();

    public abstract Charset getUriCharset();
}
