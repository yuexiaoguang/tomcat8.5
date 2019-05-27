package org.apache.catalina.session;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.catalina.Globals;
import org.apache.catalina.valves.CrawlerSessionManagerValve;

/**
 * Manifest constants for the <code>org.apache.catalina.session</code>
 * package.
 */
public class Constants {

    /**
     * Tomcat内部使用的会话属性名称集合，应该总是在它被持久化, 复制, 比较相等之前从会话中移除.
     */
    public static final Set<String> excludedAttributeNames;

    static {
        Set<String> names = new HashSet<>();
        names.add(Globals.SUBJECT_ATTR);
        names.add(Globals.GSS_CREDENTIAL_ATTR);
        names.add(CrawlerSessionManagerValve.class.getName());
        excludedAttributeNames = Collections.unmodifiableSet(names);
    }
}
