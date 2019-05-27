package org.apache.catalina.startup;


import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * 启动<b>Host</b>的事件监听器，配置Contexts (web applications)为所有定义的 "users", 这些用户在它们的主目录中的目录中拥有指定名称的web应用.
 * 将每个部署的应用程序的上下文路径设置为<code>~xxxxx</code>, web应用所属用户的用户名是xxxxx
 */
public final class UserConfig implements LifecycleListener {


    private static final Log log = LogFactory.getLog(UserConfig.class);


    // ----------------------------------------------------- Instance Variables


    /**
     * 上下文配置类的Java类名.
     */
    private String configClass = "org.apache.catalina.startup.ContextConfig";


    /**
     * 上下文实现类的Java类名.
     */
    private String contextClass = "org.apache.catalina.core.StandardContext";


    /**
     * 要在每个用户主目录中搜索的目录名.
     */
    private String directoryName = "public_html";


    /**
     * 包含用户主目录的基本目录.
     */
    private String homeBase = null;


    /**
     * 关联的Host.
     */
    private Host host = null;


    /**
     * The string resources for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * 用户的数据库类的java类名.
     */
    private String userClass =
        "org.apache.catalina.startup.PasswdUserDatabase";

    /**
     * 定义允许部署的用户的正则表达式.
     */
    Pattern allow = null;

    /**
     * 定义不允许部署的用户的正则表达式.
     */
    Pattern deny = null;

    // ------------------------------------------------------------- Properties


    /**
     * @return 上下文配置类名.
     */
    public String getConfigClass() {
        return (this.configClass);
    }


    /**
     * 设置上下文配置类名.
     *
     * @param configClass Context 配置类名.
     */
    public void setConfigClass(String configClass) {
        this.configClass = configClass;
    }


    /**
     * @return the Context实现类名.
     */
    public String getContextClass() {
        return (this.contextClass);
    }


    /**
     * 设置Context 实现类名.
     *
     * @param contextClass Context 实现类名.
     */
    public void setContextClass(String contextClass) {
        this.contextClass = contextClass;
    }


    /**
     * @return 用户Web应用程序的目录名.
     */
    public String getDirectoryName() {
        return (this.directoryName);
    }


    /**
     * 设置用户Web应用程序的目录名.
     *
     * @param directoryName The new directory name
     */
    public void setDirectoryName(String directoryName) {
        this.directoryName = directoryName;
    }


    /**
     * @return 包含用户主目录的基本目录.
     */
    public String getHomeBase() {
        return (this.homeBase);
    }


    /**
     * 设置包含用户主目录的基本目录.
     *
     * @param homeBase The new base directory
     */
    public void setHomeBase(String homeBase) {
        this.homeBase = homeBase;
    }


    /**
     * @return 用户数据库类名.
     */
    public String getUserClass() {
        return (this.userClass);
    }


    /**
     * 设置用户数据库类名.
     * 
     * @param userClass 用户数据库类名
     */
    public void setUserClass(String userClass) {
        this.userClass = userClass;
    }

    /**
     * @return 用于测试允许部署的用户的正则表达式.
     */
    public String getAllow() {
        if (allow == null) return null;
        return allow.toString();
    }


    /**
     * 设置用于测试允许部署的用户的正则表达式.
     *
     * @param allow The new allow expression
     */
    public void setAllow(String allow) {
        if (allow == null || allow.length() == 0) {
            this.allow = null;
        } else {
            this.allow = Pattern.compile(allow);
        }
    }


    /**
     * @return 定义不允许部署的用户的正则表达式.
     */
    public String getDeny() {
        if (deny == null) return null;
        return deny.toString();
    }


    /**
     * 设置定义不允许部署的用户的正则表达式.
     *
     * @param deny The new deny expression
     */
    public void setDeny(String deny) {
        if (deny == null || deny.length() == 0) {
            this.deny = null;
        } else {
            this.deny = Pattern.compile(deny);
        }
    }

    // --------------------------------------------------------- Public Methods


    /**
     * 处理关联的Host的START事件.
     *
     * @param event 已发生的生命周期事件
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        // Identify the host we are associated with
        try {
            host = (Host) event.getLifecycle();
        } catch (ClassCastException e) {
            log.error(sm.getString("hostConfig.cce", event.getLifecycle()), e);
            return;
        }

        // Process the event that has occurred
        if (event.getType().equals(Lifecycle.START_EVENT))
            start();
        else if (event.getType().equals(Lifecycle.STOP_EVENT))
            stop();
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 为任何用户在其主目录中具有指定名称的目录中部署Web应用程序.
     */
    private void deploy() {

        if (host.getLogger().isDebugEnabled())
            host.getLogger().debug(sm.getString("userConfig.deploying"));

        // 为这个主机加载用户数据库对象
        UserDatabase database = null;
        try {
            Class<?> clazz = Class.forName(userClass);
            database = (UserDatabase) clazz.getConstructor().newInstance();
            database.setUserConfig(this);
        } catch (Exception e) {
            host.getLogger().error(sm.getString("userConfig.database"), e);
            return;
        }

        ExecutorService executor = host.getStartStopExecutor();
        List<Future<?>> results = new ArrayList<>();

        // 为每个定义的用户部署Web应用程序
        Enumeration<String> users = database.getUsers();
        while (users.hasMoreElements()) {
            String user = users.nextElement();
            if (!isDeployAllowed(user)) continue;
            String home = database.getHome(user);
            results.add(executor.submit(new DeployUserDirectory(this, user, home)));
        }

        for (Future<?> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                host.getLogger().error(sm.getString("userConfig.deploy.threaded.error"), e);
            }
        }
    }


    /**
     * 为任何用户在其主目录中具有指定名称的目录中部署Web应用程序.
     *
     * @param user 拥有部署应用程序的用户名
     * @param home 此用户的主目录
     */
    private void deploy(String user, String home) {

        // 此用户是否有要部署的Web应用程序?
        String contextPath = "/~" + user;
        if (host.findChild(contextPath) != null)
            return;
        File app = new File(home, directoryName);
        if (!app.exists() || !app.isDirectory())
            return;

        host.getLogger().info(sm.getString("userConfig.deploy", user));

        // Deploy the web application for this user
        try {
            Class<?> clazz = Class.forName(contextClass);
            Context context = (Context) clazz.getConstructor().newInstance();
            context.setPath(contextPath);
            context.setDocBase(app.toString());
            clazz = Class.forName(configClass);
            LifecycleListener listener = (LifecycleListener) clazz.getConstructor().newInstance();
            context.addLifecycleListener(listener);
            host.addChild(context);
        } catch (Exception e) {
            host.getLogger().error(sm.getString("userConfig.error", user), e);
        }

    }


    /**
     * 处理这个Host的"start"事件.
     */
    private void start() {

        if (host.getLogger().isDebugEnabled())
            host.getLogger().debug(sm.getString("userConfig.start"));

        deploy();
    }


    /**
     * 处理这个Host的"stop"事件.
     */
    private void stop() {

        if (host.getLogger().isDebugEnabled())
            host.getLogger().debug(sm.getString("userConfig.stop"));
    }

    /**
     * 为提供的用户测试允许和拒绝的规则.
     *
     * @return <code>true</code>如果允许该用户部署,
     *         否则<code>false</code>
     */
    private boolean isDeployAllowed(String user) {
        if (deny != null && deny.matcher(user).matches()) {
            return false;
        }
        if (allow != null) {
            if (allow.matcher(user).matches()) {
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    private static class DeployUserDirectory implements Runnable {

        private UserConfig config;
        private String user;
        private String home;

        public DeployUserDirectory(UserConfig config, String user, String home) {
            this.config = config;
            this.user = user;
            this.home= home;
        }

        @Override
        public void run() {
            config.deploy(user, home);
        }
    }
}
