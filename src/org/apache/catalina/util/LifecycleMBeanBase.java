package org.apache.catalina.util;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.catalina.Globals;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.LifecycleException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

public abstract class LifecycleMBeanBase extends LifecycleBase
        implements JmxEnabled {

    private static final Log log = LogFactory.getLog(LifecycleMBeanBase.class);

    private static final StringManager sm =
        StringManager.getManager("org.apache.catalina.util");


    /* 缓存MBean 注册的组件. */
    private String domain = null;
    private ObjectName oname = null;
    protected MBeanServer mserver = null;

    /**
     * 希望执行额外初始化的子类应该重写此方法, 确保重写的方法中第一个调用 super.initInternal().
     */
    @Override
    protected void initInternal() throws LifecycleException {

        // 如果oname 不是 null, 那么注册通过preRegister()已经发生.
        if (oname == null) {
            mserver = Registry.getRegistry(null, null).getMBeanServer();

            oname = register(this, getObjectNameKeyProperties());
        }
    }


    /**
     * 希望执行额外清理的子类应该重写此方法, 确保重写的方法中最后一个调用 super.destroyInternal().
     */
    @Override
    protected void destroyInternal() throws LifecycleException {
        unregister(oname);
    }


    /**
     * 指定这个组件应该被注册的域名. 和无法(轻易)导航的组件一起使用, 确定要使用的正确域名.
     */
    @Override
    public final void setDomain(String domain) {
        this.domain = domain;
    }


    /**
     * 获取将要注册或已经注册的域名.
     */
    @Override
    public final String getDomain() {
        if (domain == null) {
            domain = getDomainInternal();
        }

        if (domain == null) {
            domain = Globals.DEFAULT_MBEAN_DOMAIN;
        }

        return domain;
    }


    /**
     * 标识应该注册MBean的域名.
     *
     * @return 用于注册MBean的域名.
     */
    protected abstract String getDomainInternal();


    /**
     * 获取使用JMX注册的组件的名称.
     */
    @Override
    public final ObjectName getObjectName() {
        return oname;
    }


    /**
     * 允许子类指定用于注册这个组件的{@link ObjectName}的key 属性组件.
     *
     * @return  期望的{@link ObjectName}的key属性组件
     */
    protected abstract String getObjectNameKeyProperties();


    /**
     * 使子类能够轻松注册额外的没有实现组件{@link JmxEnabled}的工具方法.
     * <br>
     * Note: 这个方法只能在调用{@link #initInternal()}之后, 调用{@link #destroyInternal()}之前进行.
     *
     * @param obj                       注册的对象
     * @param objectNameKeyProperties   用于注册对象的对象名称的 key 属性组件
     *
     * @return 用于注册对象的名称
     */
    protected final ObjectName register(Object obj,
            String objectNameKeyProperties) {

        // 构建一个合适的域对象名称
        StringBuilder name = new StringBuilder(getDomain());
        name.append(':');
        name.append(objectNameKeyProperties);

        ObjectName on = null;

        try {
            on = new ObjectName(name.toString());

            Registry.getRegistry(null, null).registerComponent(obj, on, null);
        } catch (MalformedObjectNameException e) {
            log.warn(sm.getString("lifecycleMBeanBase.registerFail", obj, name),
                    e);
        } catch (Exception e) {
            log.warn(sm.getString("lifecycleMBeanBase.registerFail", obj, name),
                    e);
        }

        return on;
    }


    /**
     * 使子类能够轻松注销额外的没有实现组件{@link JmxEnabled}的工具方法.
     * <br>
     * Note: 这个方法只能在调用{@link #initInternal()}之后, 调用{@link #destroyInternal()}之前进行.
     *
     * @param on    要注销的组件的名称
     */
    protected final void unregister(ObjectName on) {

        // If null ObjectName, just return without complaint
        if (on == null) {
            return;
        }

        // If the MBeanServer is null, log a warning & return
        if (mserver == null) {
            log.warn(sm.getString("lifecycleMBeanBase.unregisterNoServer", on));
            return;
        }

        try {
            mserver.unregisterMBean(on);
        } catch (MBeanRegistrationException e) {
            log.warn(sm.getString("lifecycleMBeanBase.unregisterFail", on), e);
        } catch (InstanceNotFoundException e) {
            log.warn(sm.getString("lifecycleMBeanBase.unregisterFail", on), e);
        }

    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void postDeregister() {
        // NOOP
    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void postRegister(Boolean registrationDone) {
        // NOOP
    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void preDeregister() throws Exception {
        // NOOP
    }


    @Override
    public final ObjectName preRegister(MBeanServer server, ObjectName name)
            throws Exception {

        this.mserver = server;
        this.oname = name;
        this.domain = name.getDomain();

        return oname;
    }
}
