package org.apache.catalina.storeconfig;

import javax.management.DynamicMBean;
import javax.management.ObjectName;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.catalina.mbeans.MBeanUtils;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

/**
 * 加载并注册StoreConfig MBean， 使用<i>Catalina:type=StoreConfig</i>名称. 整个监听器只能和{@link Server}一起使用.
 */
public class StoreConfigLifecycleListener implements LifecycleListener {

    private static Log log = LogFactory.getLog(StoreConfigLifecycleListener.class);
    private static StringManager sm = StringManager.getManager(StoreConfigLifecycleListener.class);

    /**
     * 管理的bean注册的配置信息.
     */
    protected final Registry registry = MBeanUtils.createRegistry();


    IStoreConfig storeConfig;

    private String storeConfigClass = "org.apache.catalina.storeconfig.StoreConfig";

    private String storeRegistry = null;
    private ObjectName oname = null;

    /**
     * 完全启动Server后，注册 StoreRegistry.
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if (Lifecycle.AFTER_START_EVENT.equals(event.getType())) {
            if (event.getSource() instanceof Server) {
                createMBean((Server) event.getSource());
            } else {
                log.warn(sm.getString("storeConfigListener.notServer"));
            }
        } else if (Lifecycle.AFTER_STOP_EVENT.equals(event.getType())) {
            if (oname != null) {
                registry.unregisterComponent(oname);
                oname = null;
            }
        }
     }

    /**
     * 创建StoreConfig MBean 并加载名称是<code>Catalina:type=StoreConfig</code>的StoreRegistry MBean.
     * @param server The Server instance
     */
    protected void createMBean(Server server) {
        StoreLoader loader = new StoreLoader();
        try {
            Class<?> clazz = Class.forName(getStoreConfigClass(), true, this
                    .getClass().getClassLoader());
            storeConfig = (IStoreConfig) clazz.getConstructor().newInstance();
            if (null == getStoreRegistry())
                // default Loading
                loader.load();
            else
                // load a special file registry (url)
                loader.load(getStoreRegistry());
            // use the loader Registry
            storeConfig.setRegistry(loader.getRegistry());
            storeConfig.setServer(server);
        } catch (Exception e) {
            log.error("createMBean load", e);
            return;
        }
        try {
            // Note: Hard-coded domain used since this object is per Server/JVM
            oname = new ObjectName("Catalina:type=StoreConfig" );
            registry.registerComponent(storeConfig, oname, "StoreConfig");
        } catch (Exception ex) {
            log.error("createMBean register MBean", ex);
        }
    }

    /**
     * 创建一个 ManagedBean (StoreConfig).
     *
     * @param object 要管理的对象
     * @return 包装了对象的MBean
     * @throws Exception if an error occurred
     */
    protected DynamicMBean getManagedBean(Object object) throws Exception {
        ManagedBean managedBean = registry.findManagedBean("StoreConfig");
        return managedBean.createMBean(object);
    }

    public IStoreConfig getStoreConfig() {
        return storeConfig;
    }

    public void setStoreConfig(IStoreConfig storeConfig) {
        this.storeConfig = storeConfig;
    }

    /**
     * @return 主要的存储配置类名
     */
    public String getStoreConfigClass() {
        return storeConfigClass;
    }

    public void setStoreConfigClass(String storeConfigClass) {
        this.storeConfigClass = storeConfigClass;
    }

    /**
     * @return 存储注册
     */
    public String getStoreRegistry() {
        return storeRegistry;
    }

    public void setStoreRegistry(String storeRegistry) {
        this.storeRegistry = storeRegistry;
    }
}
