package org.apache.catalina.core;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Realm;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.realm.NullRealm;
import org.apache.catalina.util.ServerInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * <b>Engine</b>接口的标准实现类.
 * 每个子级容器必须是一个Host实现类，处理该虚拟主机的特定完全限定主机名.
 * 可以直接设置jvmRoute, 或使用System.property <b>jvmRoute</b>.
 */
public class StandardEngine extends ContainerBase implements Engine {

    private static final Log log = LogFactory.getLog(StandardEngine.class);

    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardEngine component with the default basic Valve.
     */
    public StandardEngine() {

        super();
        pipeline.setBasic(new StandardEngineValve());
        /* 设置 jmvRoute, 使用系统属性 jvmRoute */
        try {
            setJvmRoute(System.getProperty("jvmRoute"));
        } catch(Exception ex) {
            log.warn(sm.getString("standardEngine.jvmRouteFail"));
        }
        // 默认情况下, engine 将保存重新加载的线程
        backgroundProcessorDelay = 10;

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 当没有服务器主机时使用的主机名, 或未知主机, 在请求中指定
     */
    private String defaultHost = null;


    /**
     * 属于这个Engine的<code>Service</code>.
     */
    private Service service = null;

    /**
     * 这个Tomcat实例的 JVM Route ID. 所有路由ID在集群中必须是唯一的.
     */
    private String jvmRouteId;

    /**
     * 用于请求/响应对的默认访问日志，在这里无法识别预期的主机和上下文.
     */
    private final AtomicReference<AccessLog> defaultAccessLog =
        new AtomicReference<>();

    // ------------------------------------------------------------- Properties

    /**
     * 如果没有显式配置，则提供默认设置.
     *
     * @return 配置的realm, 或默认的{@link NullRealm}
     */
    @Override
    public Realm getRealm() {
        Realm configured = super.getRealm();
        // 如果没有设置 realm - 默认是 NullRealm
        // 或者可以在engine、context、host等级中覆盖
        if (configured == null) {
            configured = new NullRealm();
            this.setRealm(configured);
        }
        return configured;
    }


    /**
     * 返回默认的主机.
     */
    @Override
    public String getDefaultHost() {
        return (defaultHost);
    }


    /**
     * 设置默认的主机.
     *
     * @param host The new default host
     */
    @Override
    public void setDefaultHost(String host) {

        String oldDefaultHost = this.defaultHost;
        if (host == null) {
            this.defaultHost = null;
        } else {
            this.defaultHost = host.toLowerCase(Locale.ENGLISH);
        }
        support.firePropertyChange("defaultHost", oldDefaultHost,
                                   this.defaultHost);

    }


    /**
     * 设置集群范围唯一标识符.
     * 此值仅在负载平衡方案中有用.
     * <p>
     * 此属性在设置后不应更改.
     */
    @Override
    public void setJvmRoute(String routeId) {
        jvmRouteId = routeId;
    }


    /**
     * 检索群集范围唯一标识符.
     * 此值仅在负载平衡方案中有用.
     */
    @Override
    public String getJvmRoute() {
        return jvmRouteId;
    }


    /**
     * 返回关联的<code>Service</code>.
     */
    @Override
    public Service getService() {
        return (this.service);
    }


    /**
     * 设置关联的<code>Service</code>.
     *
     * @param service 这个Engine的service
     */
    @Override
    public void setService(Service service) {
        this.service = service;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个子级Container, 只有当子级Container是Host实现类时.
     *
     * @param child Child container to be added
     */
    @Override
    public void addChild(Container child) {

        if (!(child instanceof Host))
            throw new IllegalArgumentException
                (sm.getString("standardEngine.notHost"));
        super.addChild(child);
    }


    /**
     * 不允许为这个Container设置一个父级Container, 因为Engine应该位于容器Container结构的顶部.
     *
     * @param container Proposed parent Container
     */
    @Override
    public void setParent(Container container) {
        throw new IllegalArgumentException(sm.getString("standardEngine.notParent"));
    }


    @Override
    protected void initInternal() throws LifecycleException {
        // 在启动任何尝试之前确保存在一个Realm. 可能创建默认的NullRealm.
        getRealm();
        super.initInternal();
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        // 记录服务器标识信息
        if(log.isInfoEnabled())
            log.info( "Starting Servlet Engine: " + ServerInfo.getServerInfo());

        // 标准容器启动
        super.startInternal();
    }


    /**
     * 重写默认实现.
     * 如果没有定义用于Engine的访问日志, 查找Engine的默认主机中的一个，随后是默认主机的ROOT 上下文.
     * 如果还没有找到, 返回默认的 NoOp访问日志.
     */
    @Override
    public void logAccess(Request request, Response response, long time,
            boolean useDefault) {

        boolean logged = false;

        if (getAccessLog() != null) {
            accessLog.log(request, response, time);
            logged = true;
        }

        if (!logged && useDefault) {
            AccessLog newDefaultAccessLog = defaultAccessLog.get();
            if (newDefaultAccessLog == null) {
                // 到这里, 这个Engine没有 AccessLog
                // Look in the defaultHost
                Host host = (Host) findChild(getDefaultHost());
                Context context = null;
                if (host != null && host.getState().isAvailable()) {
                    newDefaultAccessLog = host.getAccessLog();

                    if (newDefaultAccessLog != null) {
                        if (defaultAccessLog.compareAndSet(null,
                                newDefaultAccessLog)) {
                            AccessLogListener l = new AccessLogListener(this,
                                    host, null);
                            l.install();
                        }
                    } else {
                        // Try the ROOT context of default host
                        context = (Context) host.findChild("");
                        if (context != null &&
                                context.getState().isAvailable()) {
                            newDefaultAccessLog = context.getAccessLog();
                            if (newDefaultAccessLog != null) {
                                if (defaultAccessLog.compareAndSet(null,
                                        newDefaultAccessLog)) {
                                    AccessLogListener l = new AccessLogListener(
                                            this, null, context);
                                    l.install();
                                }
                            }
                        }
                    }
                }

                if (newDefaultAccessLog == null) {
                    newDefaultAccessLog = new NoopAccessLog();
                    if (defaultAccessLog.compareAndSet(null,
                            newDefaultAccessLog)) {
                        AccessLogListener l = new AccessLogListener(this, host,
                                context);
                        l.install();
                    }
                }
            }

            newDefaultAccessLog.log(request, response, time);
        }
    }


    /**
     * 返回父类加载器.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return (parentClassLoader);
        if (service != null) {
            return (service.getParentClassLoader());
        }
        return (ClassLoader.getSystemClassLoader());
    }


    @Override
    public File getCatalinaBase() {
        if (service != null) {
            Server s = service.getServer();
            if (s != null) {
                File base = s.getCatalinaBase();
                if (base != null) {
                    return base;
                }
            }
        }
        // Fall-back
        return super.getCatalinaBase();
    }


    @Override
    public File getCatalinaHome() {
        if (service != null) {
            Server s = service.getServer();
            if (s != null) {
                File base = s.getCatalinaHome();
                if (base != null) {
                    return base;
                }
            }
        }
        // Fall-back
        return super.getCatalinaHome();
    }


    // -------------------- JMX registration  --------------------

    @Override
    protected String getObjectNameKeyProperties() {
        return "type=Engine";
    }


    @Override
    protected String getDomainInternal() {
        return getName();
    }


    // ----------------------------------------------------------- Inner classes
    protected static final class NoopAccessLog implements AccessLog {

        @Override
        public void log(Request request, Response response, long time) {
            // NOOP
        }

        @Override
        public void setRequestAttributesEnabled(
                boolean requestAttributesEnabled) {
            // NOOP

        }

        @Override
        public boolean getRequestAttributesEnabled() {
            // NOOP
            return false;
        }
    }

    protected static final class AccessLogListener
            implements PropertyChangeListener, LifecycleListener,
            ContainerListener {

        private final StandardEngine engine;
        private final Host host;
        private final Context context;
        private volatile boolean disabled = false;

        public AccessLogListener(StandardEngine engine, Host host,
                Context context) {
            this.engine = engine;
            this.host = host;
            this.context = context;
        }

        public void install() {
            engine.addPropertyChangeListener(this);
            if (host != null) {
                host.addContainerListener(this);
                host.addLifecycleListener(this);
            }
            if (context != null) {
                context.addLifecycleListener(this);
            }
        }

        private void uninstall() {
            disabled = true;
            if (context != null) {
                context.removeLifecycleListener(this);
            }
            if (host != null) {
                host.removeLifecycleListener(this);
                host.removeContainerListener(this);
            }
            engine.removePropertyChangeListener(this);
        }

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (disabled) return;

            String type = event.getType();
            if (Lifecycle.AFTER_START_EVENT.equals(type) ||
                    Lifecycle.BEFORE_STOP_EVENT.equals(type) ||
                    Lifecycle.BEFORE_DESTROY_EVENT.equals(type)) {
                // Container is being started/stopped/removed
                // 强制重新计算和禁用监听器，因为它不会被重复使用
                engine.defaultAccessLog.set(null);
                uninstall();
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (disabled) return;
            if ("defaultHost".equals(evt.getPropertyName())) {
                // 强制重新计算和禁用监听器，因为它不会被重复使用
                engine.defaultAccessLog.set(null);
                uninstall();
            }
        }

        @Override
        public void containerEvent(ContainerEvent event) {
            // 只对主机有用
            if (disabled) return;
            if (Container.ADD_CHILD_EVENT.equals(event.getType())) {
                Context context = (Context) event.getData();
                if ("".equals(context.getPath())) {
                    // 强制重新计算和禁用监听器，因为它不会被重复使用
                    engine.defaultAccessLog.set(null);
                    uninstall();
                }
            }
        }
    }
}
