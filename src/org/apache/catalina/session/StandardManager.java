package org.apache.catalina.session;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Iterator;

import javax.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Session;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.util.CustomObjectInputStream;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * <b>Manager</b>接口的标准实现类， 在重新启动时提供简单会话持久性(例如，当整个服务器关闭并重新启动时, 或者当一个特定的Web应用程序加载).
 * <p>
 * <b>实现注意</b>: 会话存储和重新加载的正确行为取决于外部调用这个类的<code>start()</code>
 * 和<code>stop()</code>方法在正确的时间.
 */
public class StandardManager extends ManagerBase {

    private final Log log = LogFactory.getLog(StandardManager.class); // must not be static

    // ---------------------------------------------------- Security Classes

    private class PrivilegedDoLoad
        implements PrivilegedExceptionAction<Void> {

        PrivilegedDoLoad() {
            // NOOP
        }

        @Override
        public Void run() throws Exception{
           doLoad();
           return null;
        }
    }

    private class PrivilegedDoUnload
        implements PrivilegedExceptionAction<Void> {

        PrivilegedDoUnload() {
            // NOOP
        }

        @Override
        public Void run() throws Exception{
            doUnload();
            return null;
        }

    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 实现类描述信息.
     */
    protected static final String name = "StandardManager";


    /**
     * 当停止活动会话时保存的活动磁盘文件的路径名, 开始时加载这些会话.
     * <code>null</code>值表示不需要持久性.
     * 如果路径名是相对的, 它将根据上下文提供的临时工作目录解决, 通过<code>javax.servlet.context.tempdir</code>上下文属性可用.
     */
    protected String pathname = "SESSIONS.ser";


    // ------------------------------------------------------------- Properties

    @Override
    public String getName() {
        return name;
    }


    /**
     * @return 会话持久的路径.
     */
    public String getPathname() {
        return pathname;
    }


    /**
     * 设置会话持久性路径来指定值.
     * 如果不需要持久性支持, 设置路径名为 <code>null</code>.
     *
     * @param pathname New session persistence pathname
     */
    public void setPathname(String pathname) {
        String oldPathname = this.pathname;
        this.pathname = pathname;
        support.firePropertyChange("pathname", oldPathname, this.pathname);
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public void load() throws ClassNotFoundException, IOException {
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                AccessController.doPrivileged( new PrivilegedDoLoad() );
            } catch (PrivilegedActionException ex){
                Exception exception = ex.getException();
                if (exception instanceof ClassNotFoundException) {
                    throw (ClassNotFoundException)exception;
                } else if (exception instanceof IOException) {
                    throw (IOException)exception;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Unreported exception in load() ", exception);
                }
            }
        } else {
            doLoad();
        }
    }


    /**
     * 将以前卸载的当前活动会话加载到适当的持久化机制. 
     * 如果不支持持久化, 这个方法不做任何事情就返回.
     *
     * @exception ClassNotFoundException 如果在重新加载期间找不到序列化类
     * @exception IOException if an input/output error occurs
     */
    protected void doLoad() throws ClassNotFoundException, IOException {
        if (log.isDebugEnabled()) {
            log.debug("Start: Loading persisted sessions");
        }

        // 初始化内部数据结构
        sessions.clear();

        // 打开一个输入流到指定的路径
        File file = file();
        if (file == null) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("standardManager.loading", pathname));
        }
        Loader loader = null;
        ClassLoader classLoader = null;
        Log logger = null;
        try (FileInputStream fis = new FileInputStream(file.getAbsolutePath());
                BufferedInputStream bis = new BufferedInputStream(fis)) {
            Context c = getContext();
            loader = c.getLoader();
            logger = c.getLogger();
            if (loader != null) {
                classLoader = loader.getClassLoader();
            }
            if (classLoader == null) {
                classLoader = getClass().getClassLoader();
            }

            // 加载以前卸载的活动会话
            synchronized (sessions) {
                try (ObjectInputStream ois = new CustomObjectInputStream(bis, classLoader, logger,
                        getSessionAttributeValueClassNamePattern(),
                        getWarnOnSessionAttributeFilterFailure())) {
                    Integer count = (Integer) ois.readObject();
                    int n = count.intValue();
                    if (log.isDebugEnabled())
                        log.debug("Loading " + n + " persisted sessions");
                    for (int i = 0; i < n; i++) {
                        StandardSession session = getNewSession();
                        session.readObjectData(ois);
                        session.setManager(this);
                        sessions.put(session.getIdInternal(), session);
                        session.activate();
                        if (!session.isValidInternal()) {
                            // If session is already invalid,
                            // expire session to prevent memory leak.
                            session.setValid(true);
                            session.expire();
                        }
                        sessionCounter++;
                    }
                } finally {
                    // Delete the persistent storage file
                    if (file.exists()) {
                        file.delete();
                    }
                }
            }
        } catch (FileNotFoundException e) {
            if (log.isDebugEnabled()) {
                log.debug("No persisted data file found");
            }
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Finish: Loading persisted sessions");
        }
    }


    @Override
    public void unload() throws IOException {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            try {
                AccessController.doPrivileged(new PrivilegedDoUnload());
            } catch (PrivilegedActionException ex){
                Exception exception = ex.getException();
                if (exception instanceof IOException) {
                    throw (IOException)exception;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Unreported exception in unLoad()", exception);
                }
            }
        } else {
            doUnload();
        }
    }


    /**
     * 在适当的持久性机制中保存当前活动的会话. 
     * 如果不支持持久性, 这个方法不做任何事情就返回.
     *
     * @exception IOException if an input/output error occurs
     */
    protected void doUnload() throws IOException {

        if (log.isDebugEnabled())
            log.debug(sm.getString("standardManager.unloading.debug"));

        if (sessions.isEmpty()) {
            log.debug(sm.getString("standardManager.unloading.nosessions"));
            return; // nothing to do
        }

        // 打开输出流到指定的路径
        File file = file();
        if (file == null) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("standardManager.unloading", pathname));
        }

        // Keep a note of sessions that are expired
        ArrayList<StandardSession> list = new ArrayList<>();

        try (FileOutputStream fos = new FileOutputStream(file.getAbsolutePath());
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {

            synchronized (sessions) {
                if (log.isDebugEnabled()) {
                    log.debug("Unloading " + sessions.size() + " sessions");
                }
                // Write the number of active sessions, followed by the details
                oos.writeObject(Integer.valueOf(sessions.size()));
                Iterator<Session> elements = sessions.values().iterator();
                while (elements.hasNext()) {
                    StandardSession session =
                        (StandardSession) elements.next();
                    list.add(session);
                    session.passivate();
                    session.writeObjectData(oos);
                }
            }
        }

        // Expire all the sessions we just wrote
        if (log.isDebugEnabled()) {
            log.debug("Expiring " + list.size() + " persisted sessions");
        }
        Iterator<StandardSession> expires = list.iterator();
        while (expires.hasNext()) {
            StandardSession session = expires.next();
            try {
                session.expire(false);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            } finally {
                session.recycle();
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Unloading complete");
        }
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        super.startInternal();

        // Load unloaded sessions, if any
        try {
            load();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("standardManager.managerLoad"), t);
        }

        setState(LifecycleState.STARTING);
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        if (log.isDebugEnabled()) {
            log.debug("Stopping");
        }

        setState(LifecycleState.STOPPING);

        // Write out sessions
        try {
            unload();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("standardManager.managerUnload"), t);
        }

        // Expire all active sessions
        Session sessions[] = findSessions();
        for (int i = 0; i < sessions.length; i++) {
            Session session = sessions[i];
            try {
                if (session.isValid()) {
                    session.expire();
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            } finally {
                // 如果对会话对象的引用保存在某个共享字段中，则可以防止内存泄漏
                session.recycle();
            }
        }

        // Require a new random number generator if we are restarted
        super.stopInternal();
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * 持久化文件的路径名.
     * 
     * @return the file
     */
    protected File file() {
        if (pathname == null || pathname.length() == 0) {
            return null;
        }
        File file = new File(pathname);
        if (!file.isAbsolute()) {
            Context context = getContext();
            ServletContext servletContext = context.getServletContext();
            File tempdir = (File) servletContext.getAttribute(ServletContext.TEMPDIR);
            if (tempdir != null) {
                file = new File(tempdir, pathname);
            }
        }
        return file;
    }
}
