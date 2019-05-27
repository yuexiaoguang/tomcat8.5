package org.apache.catalina.ha.deploy;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.ha.ClusterDeployer;
import org.apache.catalina.ha.ClusterListener;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.util.ContextName;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;


/**
 * <p>
 * 能够从集群内部署/卸载部署在WAR中的Web应用程序.
 * </p>
 * 任何主机都可以充当管理员, 将有三个目录
 * <ul>
 * <li>watchDir - 关注变化的目录</li>
 * <li>deployDir - 安装应用程序的目录</li>
 * <li>tempDir - 从集群下载war时存储二进制数据的temporaryDirectory</li>
 * </ul>
 * 目前，只支持部署WAR文件，因为它们更容易跨线发送.
 */
public class FarmWarDeployer extends ClusterListener implements ClusterDeployer, FileChangeListener {
    /*--Static Variables----------------------------------------*/
    private static final Log log = LogFactory.getLog(FarmWarDeployer.class);
    private static final StringManager sm = StringManager.getManager(FarmWarDeployer.class);

    /*--Instance Variables--------------------------------------*/
    protected boolean started = false;

    protected final HashMap<String, FileMessageFactory> fileFactories =
        new HashMap<>();

    /**
     * 部署目录.
     */
    protected String deployDir;
    private File deployDirFile = null;

    /**
     * 临时目录.
     */
    protected String tempDir;
    private File tempDirFile = null;

    /**
     * 监视目录.
     */
    protected String watchDir;
    private File watchDirFile = null;

    protected boolean watchEnabled = false;

    protected WarWatcher watcher = null;

    /**
     * 后台处理的迭代计数.
     */
    private int count = 0;

    /**
     * 检查频率.
     * 对于指定数量的后台进程调用，将对集群进行一次部署 (ie, 数量越低，检查越频繁).
     */
    protected int processDeployFrequency = 2;

    /**
     * 部署上下文描述符的路径.
     */
    protected File configBase = null;

    /**
     * 关联的主机.
     */
    protected Host host = null;

    /**
     * MBean服务器.
     */
    protected MBeanServer mBeanServer = null;

    /**
     * 关联的部署程序ObjectName.
     */
    protected ObjectName oname = null;

    /**
     * FileMessageFactory的最大有效时间(秒).
     */
    protected int maxValidTime = 5 * 60;

    /*--Constructor---------------------------------------------*/
    public FarmWarDeployer() {
    }

    /*--Logic---------------------------------------------------*/
    @Override
    public void start() throws Exception {
        if (started)
            return;
        Container hcontainer = getCluster().getContainer();
        if(!(hcontainer instanceof Host)) {
            log.error(sm.getString("farmWarDeployer.hostOnly"));
            return ;
        }
        host = (Host) hcontainer;

        // 检查引擎和主机设置是否正确
        Container econtainer = host.getParent();
        if(!(econtainer instanceof Engine)) {
            log.error(sm.getString("farmWarDeployer.hostParentEngine",
                    host.getName()));
            return ;
        }
        Engine engine = (Engine) econtainer;
        String hostname = null;
        hostname = host.getName();
        try {
            oname = new ObjectName(engine.getName() + ":type=Deployer,host="
                    + hostname);
        } catch (Exception e) {
            log.error(sm.getString("farmWarDeployer.mbeanNameFail",
                    engine.getName(), hostname),e);
            return;
        }
        if (watchEnabled) {
            watcher = new WarWatcher(this, getWatchDirFile());
            if (log.isInfoEnabled()) {
                log.info(sm.getString(
                        "farmWarDeployer.watchDir", getWatchDir()));
            }
        }

        configBase = host.getConfigBaseFile();

        // Retrieve the MBean server
        mBeanServer = Registry.getRegistry(null, null).getMBeanServer();

        started = true;
        count = 0;

        getCluster().addClusterListener(this);

        if (log.isInfoEnabled())
            log.info(sm.getString("farmWarDeployer.started"));
    }

    /*
     * 停止集群范围的部署
     */
    @Override
    public void stop() throws LifecycleException {
        started = false;
        getCluster().removeClusterListener(this);
        count = 0;
        if (watcher != null) {
            watcher.clear();
            watcher = null;

        }
        if (log.isInfoEnabled())
            log.info(sm.getString("farmWarDeployer.stopped"));
    }

    /**
     * 集群的回调, 当接收到消息时, 集群将广播它，调用接收器上的messageReceived.
     *
     * @param msg - 从集群接收的消息
     */
    @Override
    public void messageReceived(ClusterMessage msg) {
        try {
            if (msg instanceof FileMessage) {
                FileMessage fmsg = (FileMessage) msg;
                if (log.isDebugEnabled())
                    log.debug(sm.getString("farmWarDeployer.msgRxDeploy",
                            fmsg.getContextName(), fmsg.getFileName()));
                FileMessageFactory factory = getFactory(fmsg);
                // TODO correct second try after app is in service!
                if (factory.writeMessage(fmsg)) {
                    // 接收到的WAR文件的最后一个消息已完成
                    String name = factory.getFile().getName();
                    if (!name.endsWith(".war"))
                        name = name + ".war";
                    File deployable = new File(getDeployDirFile(), name);
                    try {
                        String contextName = fmsg.getContextName();
                        if (!isServiced(contextName)) {
                            addServiced(contextName);
                            try {
                                remove(contextName);
                                if (!factory.getFile().renameTo(deployable)) {
                                    log.error(sm.getString(
                                            "farmWarDeployer.renameFail",
                                            factory.getFile(), deployable));
                                }
                                check(contextName);
                            } finally {
                                removeServiced(contextName);
                            }
                            if (log.isDebugEnabled())
                                log.debug(sm.getString(
                                        "farmWarDeployer.deployEnd",
                                        contextName));
                        } else
                            log.error(sm.getString(
                                    "farmWarDeployer.servicingDeploy",
                                    contextName, name));
                    } catch (Exception ex) {
                        log.error(ex);
                    } finally {
                        removeFactory(fmsg);
                    }
                }
            } else if (msg instanceof UndeployMessage) {
                try {
                    UndeployMessage umsg = (UndeployMessage) msg;
                    String contextName = umsg.getContextName();
                    if (log.isDebugEnabled())
                        log.debug(sm.getString("farmWarDeployer.msgRxUndeploy",
                                contextName));
                    if (!isServiced(contextName)) {
                        addServiced(contextName);
                        try {
                            remove(contextName);
                        } finally {
                            removeServiced(contextName);
                        }
                        if (log.isDebugEnabled())
                            log.debug(sm.getString(
                                    "farmWarDeployer.undeployEnd",
                                    contextName));
                    } else
                        log.error(sm.getString(
                                "farmWarDeployer.servicingUndeploy",
                                contextName));
                } catch (Exception ex) {
                    log.error(ex);
                }
            }
        } catch (java.io.IOException x) {
            log.error(sm.getString("farmWarDeployer.msgIoe"), x);
        }
    }

    /**
     * 为所有传输的war文件创建工厂
     *
     * @param msg 文件
     * 
     * @throws java.io.FileNotFoundException 丢失文件
     * @throws java.io.IOException 其他IO 错误
     */
    public synchronized FileMessageFactory getFactory(FileMessage msg)
            throws java.io.FileNotFoundException, java.io.IOException {
        File writeToFile = new File(getTempDirFile(), msg.getFileName());
        FileMessageFactory factory = fileFactories.get(msg.getFileName());
        if (factory == null) {
            factory = FileMessageFactory.getInstance(writeToFile, true);
            factory.setMaxValidTime(maxValidTime);
            fileFactories.put(msg.getFileName(), factory);
        }
        return factory;
    }

    /**
     * 从信息删除文件(war)
     *
     * @param msg 文件
     */
    public void removeFactory(FileMessage msg) {
        fileFactories.remove(msg.getFileName());
    }

    /**
     * 在集群调用messageReceived之前，集群将要求接收方接受或拒绝消息, 在未来, 当消息变大时，接受方法只接受消息头
     *
     * @param msg ClusterMessage
     * 
     * @return boolean - true表示调用 messageReceived. false表示不调用 messageReceived.
     */
    @Override
    public boolean accept(ClusterMessage msg) {
        return (msg instanceof FileMessage) || (msg instanceof UndeployMessage);
    }

    /**
     * 安装一个新的Web应用程序, 其Web应用程序归档文件位于指定的URL.
     * <p>
     * 如果此应用程序在本地成功安装, 一个<code>INSTALL_EVENT</code>类型的 ContainerEvent将被发送到所有注册的监听器,
     * 使用新创建的<code>Context</code>作为参数.
     *
     * @param contextName 应安装此应用程序的上下文名称(必须唯一)
     * @param webapp 包含要安装的Web应用程序的WAR文件或解压的目录机构
     *
     * @exception IllegalArgumentException 如果指定的上下文名称是异常的
     * @exception IllegalStateException 如果指定的上下文名称已经被部署
     * @exception IOException 如果在安装过程中遇到输入/输出错误
     */
    @Override
    public void install(String contextName, File webapp) throws IOException {
        Member[] members = getCluster().getMembers();
        if (members.length == 0) return;

        Member localMember = getCluster().getLocalMember();
        FileMessageFactory factory =
            FileMessageFactory.getInstance(webapp, false);
        FileMessage msg = new FileMessage(localMember, webapp.getName(),
                contextName);
        if(log.isDebugEnabled())
            log.debug(sm.getString("farmWarDeployer.sendStart", contextName,
                    webapp));
        msg = factory.readMessage(msg);
        while (msg != null) {
            for (int i = 0; i < members.length; i++) {
                if (log.isDebugEnabled())
                    log.debug(sm.getString("farmWarDeployer.sendFragment",
                            contextName, webapp, members[i]));
                getCluster().send(msg, members[i]);
            }
            msg = factory.readMessage(msg);
        }
        if(log.isDebugEnabled())
            log.debug(sm.getString(
                    "farmWarDeployer.sendEnd", contextName, webapp));
    }

    /**
     * 删除现有的Web应用.
     * 如果成功删除此应用程序, 一个<code>REMOVE_EVENT</code>类型的 ContainerEvent将被发送到注册的监听器,
     * 使用要删除的<code>Context</code>作为参数. 删除Host的appBase目录中Web应用的war 文件和目录.
     *
     * @param contextName 要删除的应用程序的上下文名称
     * @param undeploy 是否从服务器删除Web应用
     *
     * @exception IllegalArgumentException 如果指定的上下文名称是异常的
     * @exception IllegalArgumentException 如果指定的上下文名称不标识当前安装的Web应用程序
     * @exception IOException 如果在删除过程中发生输入/输出错误
     */
    @Override
    public void remove(String contextName, boolean undeploy)
            throws IOException {
        if (getCluster().getMembers().length > 0) {
            if (log.isInfoEnabled())
                log.info(sm.getString("farmWarDeployer.removeStart", contextName));
            Member localMember = getCluster().getLocalMember();
            UndeployMessage msg = new UndeployMessage(localMember, System
                    .currentTimeMillis(), "Undeploy:" + contextName + ":"
                    + System.currentTimeMillis(), contextName);
            if (log.isDebugEnabled())
                log.debug(sm.getString("farmWarDeployer.removeTxMsg", contextName));
            cluster.send(msg);
        }
        // remove locally
        if (undeploy) {
            try {
                if (!isServiced(contextName)) {
                    addServiced(contextName);
                    try {
                        remove(contextName);
                    } finally {
                        removeServiced(contextName);
                    }
                } else
                    log.error(sm.getString("farmWarDeployer.removeFailRemote",
                            contextName));

            } catch (Exception ex) {
                log.error(sm.getString("farmWarDeployer.removeFailLocal",
                        contextName), ex);
            }
        }
    }

    @Override
    public void fileModified(File newWar) {
        try {
            File deployWar = new File(getDeployDirFile(), newWar.getName());
            ContextName cn = new ContextName(deployWar.getName(), true);
            if (deployWar.exists() && deployWar.lastModified() > newWar.lastModified()) {
                if (log.isInfoEnabled())
                    log.info(sm.getString("farmWarDeployer.alreadyDeployed", cn.getName()));
                return;
            }
            if (log.isInfoEnabled())
                log.info(sm.getString("farmWarDeployer.modInstall",
                        cn.getName(), deployWar.getAbsolutePath()));
            // install local
            if (!isServiced(cn.getName())) {
                addServiced(cn.getName());
                try {
                    copy(newWar, deployWar);
                    check(cn.getName());
                } finally {
                    removeServiced(cn.getName());
                }
            } else {
                log.error(sm.getString("farmWarDeployer.servicingDeploy",
                        cn.getName(), deployWar.getName()));
            }
            install(cn.getName(), deployWar);
        } catch (Exception x) {
            log.error(sm.getString("farmWarDeployer.modInstallFail"), x);
        }
    }

    /**
     * 从watchDir删除War
     */
    @Override
    public void fileRemoved(File removeWar) {
        try {
            ContextName cn = new ContextName(removeWar.getName(), true);
            if (log.isInfoEnabled())
                log.info(sm.getString("farmWarDeployer.removeLocal",
                        cn.getName()));
            remove(cn.getName(), true);
        } catch (Exception x) {
            log.error(sm.getString("farmWarDeployer.removeLocalFail"), x);
        }
    }

    /**
     * 调用部署器上的移除方法.
     * 
     * @param contextName 要删除的上下文
     * @throws Exception 如果发生错误，删除上下文
     */
    protected void remove(String contextName) throws Exception {
        // TODO Handle remove also work dir content !
        // Stop the context first to be nicer
        Context context = (Context) host.findChild(contextName);
        if (context != null) {
            if(log.isDebugEnabled())
                log.debug(sm.getString("farmWarDeployer.undeployLocal",
                        contextName));
            context.stop();
            String baseName = context.getBaseName();
            File war = new File(host.getAppBaseFile(), baseName + ".war");
            File dir = new File(host.getAppBaseFile(), baseName);
            File xml = new File(configBase, baseName + ".xml");
            if (war.exists()) {
                if (!war.delete()) {
                    log.error(sm.getString("farmWarDeployer.deleteFail", war));
                }
            } else if (dir.exists()) {
                undeployDir(dir);
            } else {
                if (!xml.delete()) {
                    log.error(sm.getString("farmWarDeployer.deleteFail", xml));
                }
            }
            // 执行新部署，并删除内部的HostConfig 状态
            check(contextName);
        }
    }

    /**
     * 删除指定的目录, 递归地包含所有的内容和子目录.
     *
     * @param dir 表示要删除的目录的文件对象
     */
    protected void undeployDir(File dir) {

        String files[] = dir.list();
        if (files == null) {
            files = new String[0];
        }
        for (int i = 0; i < files.length; i++) {
            File file = new File(dir, files[i]);
            if (file.isDirectory()) {
                undeployDir(file);
            } else {
                if (!file.delete()) {
                    log.error(sm.getString("farmWarDeployer.deleteFail", file));
                }
            }
        }
        if (!dir.delete()) {
            log.error(sm.getString("farmWarDeployer.deleteFail", dir));
        }
    }

    /**
     * 调用观察器检查部署更改
     */
    @Override
    public void backgroundProcess() {
        if (started) {
            if (watchEnabled) {
                count = (count + 1) % processDeployFrequency;
                if (count == 0) {
                    watcher.check();
                }
            }
            removeInvalidFileFactories();
        }

    }

    /*--Deployer Operations ------------------------------------*/

    /**
     * 检查部署操作的上下文.
     * 
     * @param name 上下文名称
     * @throws Exception 调用部署器时出错
     */
    protected void check(String name) throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "check", params, signature);
    }

    /**
     * 验证上下文是否为服务.
     * 
     * @param name 上下文
     * @return <code>true</code>如果上下文正在被服务
     * @throws Exception 调用部署器时出错
     */
    protected boolean isServiced(String name) throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        Boolean result = (Boolean) mBeanServer.invoke(oname, "isServiced",
                params, signature);
        return result.booleanValue();
    }

    /**
     * 标记上下文正在服务.
     * 
     * @param name 上下文名称
     * @throws Exception 调用部署器时出错
     */
    protected void addServiced(String name) throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "addServiced", params, signature);
    }

    /**
     * 标记上下文不再服务.
     * 
     * @param name 上下文名称
     * @throws Exception 调用部署器时出错
     */
    protected void removeServiced(String name) throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "removeServiced", params, signature);
    }

    /*--Instance Getters/Setters--------------------------------*/
    @Override
    public boolean equals(Object listener) {
        return super.equals(listener);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public String getDeployDir() {
        return deployDir;
    }

    public File getDeployDirFile() {
        if (deployDirFile != null) return deployDirFile;

        File dir = getAbsolutePath(getDeployDir());
        this.deployDirFile = dir;
        return dir;
    }

    public void setDeployDir(String deployDir) {
        this.deployDir = deployDir;
    }

    public String getTempDir() {
        return tempDir;
    }

    public File getTempDirFile() {
        if (tempDirFile != null) return tempDirFile;

        File dir = getAbsolutePath(getTempDir());
        this.tempDirFile = dir;
        return dir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public String getWatchDir() {
        return watchDir;
    }

    public File getWatchDirFile() {
        if (watchDirFile != null) return watchDirFile;

        File dir = getAbsolutePath(getWatchDir());
        this.watchDirFile = dir;
        return dir;
    }

    public void setWatchDir(String watchDir) {
        this.watchDir = watchDir;
    }

    public boolean isWatchEnabled() {
        return watchEnabled;
    }

    public boolean getWatchEnabled() {
        return watchEnabled;
    }

    public void setWatchEnabled(boolean watchEnabled) {
        this.watchEnabled = watchEnabled;
    }

    /**
     * @return 检查频率.
     */
    public int getProcessDeployFrequency() {
        return (this.processDeployFrequency);
    }

    /**
     * 设置检查频率.
     *
     * @param processExpiresFrequency 检查频率
     */
    public void setProcessDeployFrequency(int processExpiresFrequency) {

        if (processExpiresFrequency <= 0) {
            return;
        }
        this.processDeployFrequency = processExpiresFrequency;
    }

    public int getMaxValidTime() {
        return maxValidTime;
    }

    public void setMaxValidTime(int maxValidTime) {
        this.maxValidTime = maxValidTime;
    }

    /**
     * 将文件复制到指定的临时目录.
     * 
     * @param from 从temp复制
     * @param to   到主机 appBase 目录
     * 
     * @return true, 成功复制
     */
    protected boolean copy(File from, File to) {
        try {
            if (!to.exists()) {
                if (!to.createNewFile()) {
                    log.error(sm.getString("fileNewFail", to));
                    return false;
                }
            }
        } catch (IOException e) {
            log.error(sm.getString("farmWarDeployer.fileCopyFail",
                    from, to), e);
            return false;
        }

        try (java.io.FileInputStream is = new java.io.FileInputStream(from);
                java.io.FileOutputStream os = new java.io.FileOutputStream(to, false);) {
            byte[] buf = new byte[4096];
            while (true) {
                int len = is.read(buf);
                if (len < 0)
                    break;
                os.write(buf, 0, len);
            }
        } catch (IOException e) {
            log.error(sm.getString("farmWarDeployer.fileCopyFail",
                    from, to), e);
            return false;
        }
        return true;
    }

    protected void removeInvalidFileFactories() {
        String[] fileNames = fileFactories.keySet().toArray(new String[0]);
        for (String fileName : fileNames) {
            FileMessageFactory factory = fileFactories.get(fileName);
            if (!factory.isValid()) {
                fileFactories.remove(fileName);
            }
        }
    }

    private File getAbsolutePath(String path) {
        File dir = new File(path);
        if (!dir.isAbsolute()) {
            dir = new File(getCluster().getContainer().getCatalinaBase(),
                    dir.getPath());
        }
        try {
            dir = dir.getCanonicalFile();
        } catch (IOException e) {// ignore
        }
        return dir;
    }
}
