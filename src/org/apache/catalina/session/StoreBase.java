package org.apache.catalina.session;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Manager;
import org.apache.catalina.Store;
import org.apache.catalina.util.CustomObjectInputStream;
import org.apache.catalina.util.LifecycleBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * Store接口的抽象实现类提供了大多数Store所需的功能.
 */
public abstract class StoreBase extends LifecycleBase implements Store {

    // ----------------------------------------------------- Instance Variables

    /**
     * 这个Store注册名称, 用于记录日志.
     */
    protected static final String storeName = "StoreBase";

    /**
     * 属性修改支持.
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(StoreBase.class);

    /**
     * Store关联的Manager.
     */
    protected Manager manager;


    // ------------------------------------------------------------- Properties

    /**
     * @return 这个Store的名称, 用于记录日志.
     */
    public String getStoreName() {
        return storeName;
    }


    /**
     * 设置关联的Manager.
     *
     * @param manager The newly associated Manager
     */
    @Override
    public void setManager(Manager manager) {
        Manager oldManager = this.manager;
        this.manager = manager;
        support.firePropertyChange("manager", oldManager, this.manager);
    }

    /**
     * @return 关联的Manager.
     */
    @Override
    public Manager getManager() {
        return this.manager;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 添加生命周期事件监听器.
     *
     * @param listener a value of type {@link PropertyChangeListener}
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    /**
     * 移除生命周期事件监听器.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }

    /**
     * 只获取会话的键, 其保存在Store中并且被过期.
     *
     * @return 会话的key的列表, 要过期的
     * @throws IOException if an input-/output error occurred
     */
    public String[] expiredKeys() throws IOException {
        return keys();
    }

    /**
     *  由后台线程调用，以检查保存在存储中的会话是否过期.
     * 如果是这样，则终止Session并将其从Store中删除.
     */
    public void processExpires() {
        String[] keys = null;

        if(!getState().isAvailable()) {
            return;
        }

        try {
            keys = expiredKeys();
        } catch (IOException e) {
            manager.getContext().getLogger().error("Error getting keys", e);
            return;
        }
        if (manager.getContext().getLogger().isDebugEnabled()) {
            manager.getContext().getLogger().debug(getStoreName()+ ": processExpires check number of " + keys.length + " sessions" );
        }

        long timeNow = System.currentTimeMillis();

        for (int i = 0; i < keys.length; i++) {
            try {
                StandardSession session = (StandardSession) load(keys[i]);
                if (session == null) {
                    continue;
                }
                int timeIdle = (int) ((timeNow - session.getThisAccessedTime()) / 1000L);
                if (timeIdle < session.getMaxInactiveInterval()) {
                    continue;
                }
                if (manager.getContext().getLogger().isDebugEnabled()) {
                    manager.getContext().getLogger().debug(getStoreName()+ ": processExpires expire store session " + keys[i] );
                }
                boolean isLoaded = false;
                if (manager instanceof PersistentManagerBase) {
                    isLoaded = ((PersistentManagerBase) manager).isLoaded(keys[i]);
                } else {
                    try {
                        if (manager.findSession(keys[i]) != null) {
                            isLoaded = true;
                        }
                    } catch (IOException ioe) {
                        // Ignore - session will be expired
                    }
                }
                if (isLoaded) {
                    // recycle old backup session
                    session.recycle();
                } else {
                    // expire swapped out session
                    session.expire();
                }
                remove(keys[i]);
            } catch (Exception e) {
                manager.getContext().getLogger().error("Session: "+keys[i]+"; ", e);
                try {
                    remove(keys[i]);
                } catch (IOException e2) {
                    manager.getContext().getLogger().error("Error removing key", e2);
                }
            }
        }
    }


    // --------------------------------------------------------- Protected Methods

    /**
     * 创建用于从存储库读取会话的对象输入流.
     * 子类必须在调用此方法之前设置线程上下文类加载器.
     *
     * @param is 由提供会话数据的子类提供的输入流
     *
     * @return 可以读取会话的适当配置的ObjectInputStream.
     *
     * @throws IOException 创建ObjectInputStream时发生错误
     */
    protected ObjectInputStream getObjectInputStream(InputStream is) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(is);

        CustomObjectInputStream ois;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        if (manager instanceof ManagerBase) {
            ManagerBase managerBase = (ManagerBase) manager;
            ois = new CustomObjectInputStream(bis, classLoader, manager.getContext().getLogger(),
                    managerBase.getSessionAttributeValueClassNamePattern(),
                    managerBase.getWarnOnSessionAttributeFilterFailure());
        } else {
            ois = new CustomObjectInputStream(bis, classLoader);
        }

        return ois;
    }


    @Override
    protected void initInternal() {
        // NOOP
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        setState(LifecycleState.STARTING);
    }


    /**
     * @exception LifecycleException 如果此组件检测到需要报告的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);
    }


    @Override
    protected void destroyInternal() {
        // NOOP
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append('[');
        if (manager == null) {
            sb.append("Manager is null");
        } else {
            sb.append(manager);
        }
        sb.append(']');
        return sb.toString();
    }
}
