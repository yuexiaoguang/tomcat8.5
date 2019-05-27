package org.apache.catalina.deploy;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.naming.NamingException;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.mbeans.MBeanUtils;
import org.apache.catalina.util.Introspection;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.naming.ContextBindings;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.descriptor.web.ContextEjb;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextLocalEjb;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.descriptor.web.ContextResourceEnvRef;
import org.apache.tomcat.util.descriptor.web.ContextResourceLink;
import org.apache.tomcat.util.descriptor.web.ContextService;
import org.apache.tomcat.util.descriptor.web.ContextTransaction;
import org.apache.tomcat.util.descriptor.web.InjectionTarget;
import org.apache.tomcat.util.descriptor.web.MessageDestinationRef;
import org.apache.tomcat.util.descriptor.web.NamingResources;
import org.apache.tomcat.util.descriptor.web.ResourceBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * 保存和管理命名资源，定义在J2EE企业命名上下文和其相关联的JNDI上下文.
 */
public class NamingResourcesImpl extends LifecycleMBeanBase
        implements Serializable, NamingResources {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(NamingResourcesImpl.class);

    private static final StringManager sm = StringManager.getManager(NamingResourcesImpl.class);

    private volatile boolean resourceRequireExplicitRegistration = false;

    // ----------------------------------------------------------- Constructors


    public NamingResourcesImpl() {
        // NOOP
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 关联的容器对象.
     */
    private Object container = null;


    /**
     * 命名条目列表, 使用名称作为key.
     */
    private final Set<String> entries = new HashSet<>();


    /**
     * 此Web应用程序的EJB资源引用, 使用名称作为key.
     */
    private final HashMap<String, ContextEjb> ejbs = new HashMap<>();


    /**
     * 此Web应用程序的环境条目, 使用名称作为key.
     */
    private final HashMap<String, ContextEnvironment> envs = new HashMap<>();


    /**
     * 此Web应用程序的本地EJB资源引用, 使用名称作为key.
     */
    private final HashMap<String, ContextLocalEjb> localEjbs = new HashMap<>();


    /**
     * 此Web应用程序消息目标引用, 使用名称作为key.
     */
    private final HashMap<String, MessageDestinationRef> mdrs = new HashMap<>();


    /**
     * 此Web应用程序的资源环境引用, 使用名称作为key.
     */
    private final HashMap<String, ContextResourceEnvRef> resourceEnvRefs =
        new HashMap<>();


    /**
     * 此Web应用程序的资源引用, 使用名称作为key.
     */
    private final HashMap<String, ContextResource> resources =
        new HashMap<>();


    /**
     * 此Web应用程序的资源链接, 使用名称作为key.
     */
    private final HashMap<String, ContextResourceLink> resourceLinks =
        new HashMap<>();


    /**
     * 此Web应用程序的Web服务引用, 使用名称作为key.
     */
    private final HashMap<String, ContextService> services =
        new HashMap<>();


    /**
     * 此应用的事务.
     */
    private ContextTransaction transaction = null;


    /**
     * 属性修改支持.
     */
    protected final PropertyChangeSupport support =
            new PropertyChangeSupport(this);


    // ------------------------------------------------------------- Properties


    /**
     * @return 与命名资源相关联的容器.
     */
    @Override
    public Object getContainer() {
        return container;
    }


    /**
     * 设置与命名资源相关联的容器.
     * 
     * @param container 关联的资源
     */
    public void setContainer(Object container) {
        this.container = container;
    }


    /**
     * 设置事务对象.
     * 
     * @param transaction 事务描述符
     */
    public void setTransaction(ContextTransaction transaction) {
        this.transaction = transaction;
    }


    public ContextTransaction getTransaction() {
        return transaction;
    }


    /**
     * 为这个Web应用程序添加一个EJB资源引用.
     *
     * @param ejb EJB资源引用
     */
    public void addEjb(ContextEjb ejb) {

        if (entries.contains(ejb.getName())) {
            return;
        } else {
            entries.add(ejb.getName());
        }

        synchronized (ejbs) {
            ejb.setNamingResources(this);
            ejbs.put(ejb.getName(), ejb);
        }
        support.firePropertyChange("ejb", null, ejb);

    }


    /**
     * 为这个Web应用程序添加一个环境条目.
     *
     * @param environment 环境条目
     */
    @Override
    public void addEnvironment(ContextEnvironment environment) {

        if (entries.contains(environment.getName())) {
            ContextEnvironment ce = findEnvironment(environment.getName());
            ContextResourceLink rl = findResourceLink(environment.getName());
            if (ce != null) {
                if (ce.getOverride()) {
                    removeEnvironment(environment.getName());
                } else {
                    return;
                }
            } else if (rl != null) {
                // Link. 需要查看全局资源
                NamingResourcesImpl global = getServer().getGlobalNamingResources();
                if (global.findEnvironment(rl.getGlobal()) != null) {
                    if (global.findEnvironment(rl.getGlobal()).getOverride()) {
                        removeResourceLink(environment.getName());
                    } else {
                        return;
                    }
                }
            } else {
                // It exists but it isn't an env or a res link...
                return;
            }
        }

        if (!checkResourceType(environment)) {
            throw new IllegalArgumentException(sm.getString(
                    "namingResources.resourceTypeFail", environment.getName(),
                    environment.getType()));
        }

        entries.add(environment.getName());

        synchronized (envs) {
            environment.setNamingResources(this);
            envs.put(environment.getName(), environment);
        }
        support.firePropertyChange("environment", null, environment);

        // Register with JMX
        if (resourceRequireExplicitRegistration) {
            try {
                MBeanUtils.createMBean(environment);
            } catch (Exception e) {
                log.warn(sm.getString("namingResources.mbeanCreateFail",
                        environment.getName()), e);
            }
        }
    }

    // Container应该是Server 或 Context实例. 如果是别的什么, 返回null将触发 NPE.
    private Server getServer() {
        if (container instanceof Server) {
            return (Server) container;
        }
        if (container instanceof Context) {
            // 一次可以做到这一点. Lots of casts so split out for clarity
            Engine engine =
                (Engine) ((Context) container).getParent().getParent();
            return engine.getService().getServer();
        }
        return null;
    }

    /**
     * 为这个Web应用程序添加本地EJB资源引用.
     *
     * @param ejb EJB资源引用
     */
    public void addLocalEjb(ContextLocalEjb ejb) {

        if (entries.contains(ejb.getName())) {
            return;
        } else {
            entries.add(ejb.getName());
        }

        synchronized (localEjbs) {
            ejb.setNamingResources(this);
            localEjbs.put(ejb.getName(), ejb);
        }
        support.firePropertyChange("localEjb", null, ejb);
    }


    /**
     * 为这个Web应用程序添加一个消息目标引用.
     *
     * @param mdr New message destination reference
     */
    public void addMessageDestinationRef(MessageDestinationRef mdr) {

        if (entries.contains(mdr.getName())) {
            return;
        } else {
            if (!checkResourceType(mdr)) {
                throw new IllegalArgumentException(sm.getString(
                        "namingResources.resourceTypeFail", mdr.getName(),
                        mdr.getType()));
            }
            entries.add(mdr.getName());
        }

        synchronized (mdrs) {
            mdr.setNamingResources(this);
            mdrs.put(mdr.getName(), mdr);
        }
        support.firePropertyChange("messageDestinationRef", null, mdr);

    }


    /**
     * 添加一个属性修改监听器.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * 为这个Web应用程序添加资源引用.
     *
     * @param resource 资源引用
     */
    @Override
    public void addResource(ContextResource resource) {

        if (entries.contains(resource.getName())) {
            return;
        } else {
            if (!checkResourceType(resource)) {
                throw new IllegalArgumentException(sm.getString(
                        "namingResources.resourceTypeFail", resource.getName(),
                        resource.getType()));
            }
            entries.add(resource.getName());
        }

        synchronized (resources) {
            resource.setNamingResources(this);
            resources.put(resource.getName(), resource);
        }
        support.firePropertyChange("resource", null, resource);

        // Register with JMX
        if (resourceRequireExplicitRegistration) {
            try {
                MBeanUtils.createMBean(resource);
            } catch (Exception e) {
                log.warn(sm.getString("namingResources.mbeanCreateFail",
                        resource.getName()), e);
            }
        }
    }


    /**
     * 为这个Web应用程序添加资源环境引用.
     *
     * @param resource The resource
     */
    public void addResourceEnvRef(ContextResourceEnvRef resource) {

        if (entries.contains(resource.getName())) {
            return;
        } else {
            if (!checkResourceType(resource)) {
                throw new IllegalArgumentException(sm.getString(
                        "namingResources.resourceTypeFail", resource.getName(),
                        resource.getType()));
            }
            entries.add(resource.getName());
        }

        synchronized (resourceEnvRefs) {
            resource.setNamingResources(this);
            resourceEnvRefs.put(resource.getName(), resource);
        }
        support.firePropertyChange("resourceEnvRef", null, resource);

    }


    /**
     * 为这个Web应用程序添加一个资源链接.
     *
     * @param resourceLink New resource link
     */
    @Override
    public void addResourceLink(ContextResourceLink resourceLink) {

        if (entries.contains(resourceLink.getName())) {
            return;
        } else {
            entries.add(resourceLink.getName());
        }

        synchronized (resourceLinks) {
            resourceLink.setNamingResources(this);
            resourceLinks.put(resourceLink.getName(), resourceLink);
        }
        support.firePropertyChange("resourceLink", null, resourceLink);

        // Register with JMX
        if (resourceRequireExplicitRegistration) {
            try {
                MBeanUtils.createMBean(resourceLink);
            } catch (Exception e) {
                log.warn(sm.getString("namingResources.mbeanCreateFail",
                        resourceLink.getName()), e);
            }
        }
    }


    /**
     * 为Web应用程序添加Web服务引用.
     *
     * @param service New web service reference
     */
    public void addService(ContextService service) {

        if (entries.contains(service.getName())) {
            return;
        } else {
            entries.add(service.getName());
        }

        synchronized (services) {
            service.setNamingResources(this);
            services.put(service.getName(), service);
        }
        support.firePropertyChange("service", null, service);

    }


    /**
     * @return 指定名称的EJB资源引用; 或者<code>null</code>.
     *
     * @param name 所需EJB资源引用的名称
     */
    public ContextEjb findEjb(String name) {
        synchronized (ejbs) {
            return ejbs.get(name);
        }
    }


    /**
     * @return 此应用程序定义的EJB资源引用. 如果没有, 返回零长度的数组.
     */
    public ContextEjb[] findEjbs() {

        synchronized (ejbs) {
            ContextEjb results[] = new ContextEjb[ejbs.size()];
            return ejbs.values().toArray(results);
        }
    }


    /**
     * @return 指定名称的环境条目;或者<code>null</code>.
     *
     * @param name 所需环境条目的名称
     */
    public ContextEnvironment findEnvironment(String name) {

        synchronized (envs) {
            return envs.get(name);
        }
    }


    /**
     * @return 此Web应用程序所定义的环境条目集合. 如果没有, 返回零长度数组.
     */
    public ContextEnvironment[] findEnvironments() {

        synchronized (envs) {
            ContextEnvironment results[] = new ContextEnvironment[envs.size()];
            return envs.values().toArray(results);
        }

    }


    /**
     * @return 指定名称的本地EJB资源引用; 或者<code>null</code>.
     *
     * @param name 所需EJB资源引用的名称
     */
    public ContextLocalEjb findLocalEjb(String name) {
        synchronized (localEjbs) {
            return localEjbs.get(name);
        }
    }


    /**
     * @return 此应用程序定义的本地EJB资源引用. 如果没有, 返回零长度数组.
     */
    public ContextLocalEjb[] findLocalEjbs() {
        synchronized (localEjbs) {
            ContextLocalEjb results[] = new ContextLocalEjb[localEjbs.size()];
            return localEjbs.values().toArray(results);
        }
    }


    /**
     * @return 指定名称的消息目标引用; 或者<code>null</code>.
     *
     * @param name 所需消息目的地引用的名称
     */
    public MessageDestinationRef findMessageDestinationRef(String name) {
        synchronized (mdrs) {
            return mdrs.get(name);
        }
    }


    /**
     * @return 所有的消息目标引用. 如果没有, 返回零长度数组.
     */
    public MessageDestinationRef[] findMessageDestinationRefs() {

        synchronized (mdrs) {
            MessageDestinationRef results[] =
                new MessageDestinationRef[mdrs.size()];
            return mdrs.values().toArray(results);
        }

    }


    /**
     * @return 指定名称的资源引用;或者<code>null</code>.
     *
     * @param name 所需资源引用的名称
     */
    public ContextResource findResource(String name) {

        synchronized (resources) {
            return resources.get(name);
        }

    }


    /**
     * @return 指定名称的资源链接;或者返回<code>null</code>.
     *
     * @param name 所需资源链接的名称
     */
    public ContextResourceLink findResourceLink(String name) {

        synchronized (resourceLinks) {
            return resourceLinks.get(name);
        }
    }


    /**
     * @return 此应用程序定义的资源链接. 如果没有, 返回零长度数组.
     */
    public ContextResourceLink[] findResourceLinks() {

        synchronized (resourceLinks) {
            ContextResourceLink results[] =
                new ContextResourceLink[resourceLinks.size()];
            return resourceLinks.values().toArray(results);
        }
    }


    /**
     * @return 此应用程序定义的资源引用. 如果没有, 返回零长度数组.
     */
    public ContextResource[] findResources() {

        synchronized (resources) {
            ContextResource results[] = new ContextResource[resources.size()];
            return resources.values().toArray(results);
        }
    }


    /**
     * @return 指定名称的资源环境引用类型; 或者<code>null</code>.
     *
     * @param name 所需资源环境引用的名称
     */
    public ContextResourceEnvRef findResourceEnvRef(String name) {

        synchronized (resourceEnvRefs) {
            return resourceEnvRefs.get(name);
        }
    }


    /**
     * @return 此Web应用程序的资源环境引用名称集合. 如果没有, 返回零长度数组.
     */
    public ContextResourceEnvRef[] findResourceEnvRefs() {

        synchronized (resourceEnvRefs) {
            ContextResourceEnvRef results[] = new ContextResourceEnvRef[resourceEnvRefs.size()];
            return resourceEnvRefs.values().toArray(results);
        }
    }


    /**
     * @return 指定名称的 web service引用; 否则返回<code>null</code>.
     *
     * @param name 所需Web服务的名称
     */
    public ContextService findService(String name) {

        synchronized (services) {
            return services.get(name);
        }
    }


    /**
     * @return 所有定义的web service引用. 如果没有, 返回零长度数组.
     */
    public ContextService[] findServices() {
        synchronized (services) {
            ContextService results[] = new ContextService[services.size()];
            return services.values().toArray(results);
        }
    }


    /**
     * 删除指定名称的任何EJB资源引用.
     *
     * @param name 要移除的EJB资源引用的名称
     */
    public void removeEjb(String name) {

        entries.remove(name);

        ContextEjb ejb = null;
        synchronized (ejbs) {
            ejb = ejbs.remove(name);
        }
        if (ejb != null) {
            support.firePropertyChange("ejb", ejb, null);
            ejb.setNamingResources(null);
        }

    }


    /**
     * 删除指定名称的任何环境条目.
     *
     * @param name 要删除的环境条目名称
     */
    @Override
    public void removeEnvironment(String name) {

        entries.remove(name);

        ContextEnvironment environment = null;
        synchronized (envs) {
            environment = envs.remove(name);
        }
        if (environment != null) {
            support.firePropertyChange("environment", environment, null);
            // De-register with JMX
            if (resourceRequireExplicitRegistration) {
                try {
                    MBeanUtils.destroyMBean(environment);
                } catch (Exception e) {
                    log.warn(sm.getString("namingResources.mbeanDestroyFail",
                            environment.getName()), e);
                }
            }
            environment.setNamingResources(null);
        }
    }


    /**
     * 删除指定名称的任何本地EJB资源引用.
     *
     * @param name 要删除的EJB资源引用的名称
     */
    public void removeLocalEjb(String name) {

        entries.remove(name);

        ContextLocalEjb localEjb = null;
        synchronized (localEjbs) {
            localEjb = localEjbs.remove(name);
        }
        if (localEjb != null) {
            support.firePropertyChange("localEjb", localEjb, null);
            localEjb.setNamingResources(null);
        }
    }


    /**
     * 移除指定名称的所有消息目标引用.
     *
     * @param name 要删除的消息目的地资源引用的名称
     */
    public void removeMessageDestinationRef(String name) {

        entries.remove(name);

        MessageDestinationRef mdr = null;
        synchronized (mdrs) {
            mdr = mdrs.remove(name);
        }
        if (mdr != null) {
            support.firePropertyChange("messageDestinationRef",
                                       mdr, null);
            mdr.setNamingResources(null);
        }
    }


    /**
     * 移除一个属性修改监听器.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    /**
     * 删除指定名称的任何资源引用.
     *
     * @param name 要删除的资源引用的名称
     */
    @Override
    public void removeResource(String name) {
        entries.remove(name);

        ContextResource resource = null;
        synchronized (resources) {
            resource = resources.remove(name);
        }
        if (resource != null) {
            support.firePropertyChange("resource", resource, null);
            // De-register with JMX
            if (resourceRequireExplicitRegistration) {
                try {
                    MBeanUtils.destroyMBean(resource);
                } catch (Exception e) {
                    log.warn(sm.getString("namingResources.mbeanDestroyFail",
                            resource.getName()), e);
                }
            }
            resource.setNamingResources(null);
        }
    }


    /**
     * 删除指定名称的任何资源环境引用.
     *
     * @param name 要删除的资源环境引用的名称
     */
    public void removeResourceEnvRef(String name) {

        entries.remove(name);

        ContextResourceEnvRef resourceEnvRef = null;
        synchronized (resourceEnvRefs) {
            resourceEnvRef =
                resourceEnvRefs.remove(name);
        }
        if (resourceEnvRef != null) {
            support.firePropertyChange("resourceEnvRef", resourceEnvRef, null);
            resourceEnvRef.setNamingResources(null);
        }

    }


    /**
     * 删除指定名称的任何资源链接.
     *
     * @param name 要删除的资源链接的名称
     */
    @Override
    public void removeResourceLink(String name) {

        entries.remove(name);

        ContextResourceLink resourceLink = null;
        synchronized (resourceLinks) {
            resourceLink = resourceLinks.remove(name);
        }
        if (resourceLink != null) {
            support.firePropertyChange("resourceLink", resourceLink, null);
            // De-register with JMX
            if (resourceRequireExplicitRegistration) {
                try {
                    MBeanUtils.destroyMBean(resourceLink);
                } catch (Exception e) {
                    log.warn(sm.getString("namingResources.mbeanDestroyFail",
                            resourceLink.getName()), e);
                }
            }
            resourceLink.setNamingResources(null);
        }
    }


    /**
     * 删除指定名称的任何Web服务引用.
     *
     * @param name 要删除的Web服务引用的名称
     */
    public void removeService(String name) {

        entries.remove(name);

        ContextService service = null;
        synchronized (services) {
            service = services.remove(name);
        }
        if (service != null) {
            support.firePropertyChange("service", service, null);
            service.setNamingResources(null);
        }

    }


    // ------------------------------------------------------- Lifecycle methods

    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();

        // 在注册当前已知的命名资源之前设置此项，以避免时间问题. 重复注册不是问题.
        resourceRequireExplicitRegistration = true;

        for (ContextResource cr : resources.values()) {
            try {
                MBeanUtils.createMBean(cr);
            } catch (Exception e) {
                log.warn(sm.getString(
                        "namingResources.mbeanCreateFail", cr.getName()), e);
            }
        }

        for (ContextEnvironment ce : envs.values()) {
            try {
                MBeanUtils.createMBean(ce);
            } catch (Exception e) {
                log.warn(sm.getString(
                        "namingResources.mbeanCreateFail", ce.getName()), e);
            }
        }

        for (ContextResourceLink crl : resourceLinks.values()) {
            try {
                MBeanUtils.createMBean(crl);
            } catch (Exception e) {
                log.warn(sm.getString(
                        "namingResources.mbeanCreateFail", crl.getName()), e);
            }
        }
    }


    @Override
    protected void startInternal() throws LifecycleException {
        fireLifecycleEvent(CONFIGURE_START_EVENT, null);
        setState(LifecycleState.STARTING);
    }


    @Override
    protected void stopInternal() throws LifecycleException {
        cleanUp();
        setState(LifecycleState.STOPPING);
        fireLifecycleEvent(CONFIGURE_STOP_EVENT, null);
    }

    /**
     * 关闭这些资源，明确的关闭可能有助于更快地清理.
     */
    private void cleanUp() {
        if (resources.size() == 0) {
            return;
        }
        javax.naming.Context ctxt;
        try {
            if (container instanceof Server) {
                ctxt = ((Server) container).getGlobalNamingContext();
            } else {
                ctxt = ContextBindings.getClassLoader();
                ctxt = (javax.naming.Context) ctxt.lookup("comp/env");
            }
        } catch (NamingException e) {
            log.warn(sm.getString("namingResources.cleanupNoContext",
                    container), e);
            return;
        }
        for (ContextResource cr: resources.values()) {
            if (cr.getSingleton()) {
                String closeMethod = cr.getCloseMethod();
                if (closeMethod != null && closeMethod.length() > 0) {
                    String name = cr.getName();
                    Object resource;
                    try {
                         resource = ctxt.lookup(name);
                    } catch (NamingException e) {
                        log.warn(sm.getString(
                                "namingResources.cleanupNoResource",
                                cr.getName(), container), e);
                        continue;
                    }
                    cleanUp(resource, name, closeMethod);
                }
            }
        }
    }


    /**
     * 通过调用定义的关闭方法来清理资源.
     * 例如, 关闭数据库连接池将关闭其打开的连接. 这将发生在GC上，但这会导致可能导致问题的DB连接打开.
     *
     * @param resource  要关闭的资源.
     */
    private void cleanUp(Object resource, String name, String closeMethod) {
        // Look for a zero-arg close() method
        Method m = null;
        try {
            m = resource.getClass().getMethod(closeMethod, (Class<?>[]) null);
        } catch (SecurityException e) {
            log.debug(sm.getString("namingResources.cleanupCloseSecurity",
                    closeMethod, name, container));
            return;
        } catch (NoSuchMethodException e) {
            log.debug(sm.getString("namingResources.cleanupNoClose",
                    name, container, closeMethod));
            return;
        }
        try {
            m.invoke(resource, (Object[]) null);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            log.warn(sm.getString("namingResources.cleanupCloseFailed",
                    closeMethod, name, container), e);
        } catch (InvocationTargetException e) {
            Throwable t = ExceptionUtils.unwrapInvocationTargetException(e);
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString("namingResources.cleanupCloseFailed",
                    closeMethod, name, container), t);
        }
    }

    @Override
    protected void destroyInternal() throws LifecycleException {

        // 在删除当前已知的命名资源之前设置此项，以避免时间问题. 重复注销不是问题.
        resourceRequireExplicitRegistration = false;

        // 以创建相反的顺序销毁, 虽然它不重要
        for (ContextResourceLink crl : resourceLinks.values()) {
            try {
                MBeanUtils.destroyMBean(crl);
            } catch (Exception e) {
                log.warn(sm.getString(
                        "namingResources.mbeanDestroyFail", crl.getName()), e);
            }
        }

        for (ContextEnvironment ce : envs.values()) {
            try {
                MBeanUtils.destroyMBean(ce);
            } catch (Exception e) {
                log.warn(sm.getString(
                        "namingResources.mbeanDestroyFail", ce.getName()), e);
            }
        }

        for (ContextResource cr : resources.values()) {
            try {
                MBeanUtils.destroyMBean(cr);
            } catch (Exception e) {
                log.warn(sm.getString(
                        "namingResources.mbeanDestroyFail", cr.getName()), e);
            }
        }

        super.destroyInternal();
    }


    @Override
    protected String getDomainInternal() {
        // Use the same domain as our associated container if we have one
        Object c = getContainer();

        if (c instanceof JmxEnabled) {
            return ((JmxEnabled) c).getDomain();
        }

        return null;
    }


    @Override
    protected String getObjectNameKeyProperties() {
        Object c = getContainer();
        if (c instanceof Container) {
            return "type=NamingResources" +
                    ((Container) c).getMBeanKeyProperties();
        }
        // Server or just unknown
        return "type=NamingResources";
    }

    /**
     * 检查指定资源类型的配置是否与任何注入目标一致，如果未指定类型, 尝试根据注入目标配置类型
     *
     * @param resource  The resource to check
     *
     * @return  <code>true</code>如果资源的类型现在有效(<code>null</code>意味着未设置);
     *          或<code>false</code>如果当前资源类型与注入目标不一致或不能确定
     */
    private boolean checkResourceType(ResourceBase resource) {
        if (!(container instanceof Context)) {
            // Only Context's will have injection targets
            return true;
        }

        if (resource.getInjectionTargets() == null ||
                resource.getInjectionTargets().size() == 0) {
            // 没有注入目标，所以使用所定义的资源类型
            return true;
        }

        Context context = (Context) container;

        String typeName = resource.getType();
        Class<?> typeClass = null;
        if (typeName != null) {
            typeClass = Introspection.loadClass(context, typeName);
            if (typeClass == null) {
                // 无法加载类型 - 稍后会触发异常，所以不要在这里失败
                return true;
            }
        }

        Class<?> compatibleClass =
                getCompatibleType(context, resource, typeClass);
        if (compatibleClass == null) {
            // 指示无法识别为所有注入目标工作的兼容类型
            return false;
        }

        resource.setType(compatibleClass.getCanonicalName());
        return true;
    }

    private Class<?> getCompatibleType(Context context,
            ResourceBase resource, Class<?> typeClass) {

        Class<?> result = null;

        for (InjectionTarget injectionTarget : resource.getInjectionTargets()) {
            Class<?> clazz = Introspection.loadClass(
                    context, injectionTarget.getTargetClass());
            if (clazz == null) {
                // 不能加载类 - 因此忽略这个目标
                continue;
            }

            // Look for a match
            String targetName = injectionTarget.getTargetName();
            // Look for a setter match first
            Class<?> targetType = getSetterType(clazz, targetName);
            if (targetType == null) {
                // Try a field match if no setter match
                targetType = getFieldType(clazz,targetName);
            }
            if (targetType == null) {
                // No match - ignore this injection target
                continue;
            }
            targetType = Introspection.convertPrimitiveType(targetType);

            if (typeClass == null) {
                // 需要在注入目标之间找到共同的类型
                if (result == null) {
                    result = targetType;
                } else if (targetType.isAssignableFrom(result)) {
                    // NO-OP - This will work
                } else if (result.isAssignableFrom(targetType)) {
                    // 需要使用更具体的类型
                    result = targetType;
                } else {
                    // 不兼容的类型
                    return null;
                }
            } else {
                // 每个注入目标需要与定义类型一致
                if (targetType.isAssignableFrom(typeClass)) {
                    result = typeClass;
                } else {
                    // 不兼容的类型
                    return null;
                }
            }
        }
        return result;
    }

    private Class<?> getSetterType(Class<?> clazz, String name) {
        Method[] methods = Introspection.getDeclaredMethods(clazz);
        if (methods != null && methods.length > 0) {
            for (Method method : methods) {
                if (Introspection.isValidSetter(method) &&
                        Introspection.getPropertyName(method).equals(name)) {
                    return method.getParameterTypes()[0];
                }
            }
        }
        return null;
    }

    private Class<?> getFieldType(Class<?> clazz, String name) {
        Field[] fields = Introspection.getDeclaredFields(clazz);
        if (fields != null && fields.length > 0) {
            for (Field field : fields) {
                if (field.getName().equals(name)) {
                    return field.getType();
                }
            }
        }
        return null;
    }
}
