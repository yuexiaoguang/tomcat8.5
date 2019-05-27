package org.apache.tomcat.util.descriptor.web;

/**
 * 定义添加到web.xml中JNDI资源表示的对象的接口，以使其也能够实现该JNDI资源.
 * 只有Catalina实现了这个接口，但因为web.xml表示是共享的，所以这个接口必须对Catalina和Jasper可见.
 */
public interface NamingResources {

    void addEnvironment(ContextEnvironment ce);
    void removeEnvironment(String name);

    void addResource(ContextResource cr);
    void removeResource(String name);

    void addResourceLink(ContextResourceLink crl);
    void removeResourceLink(String name);

    Object getContainer();
}
