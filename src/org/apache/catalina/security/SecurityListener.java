package org.apache.catalina.security;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.res.StringManager;

public class SecurityListener implements LifecycleListener {

    private static final Log log = LogFactory.getLog(SecurityListener.class);

    private static final StringManager sm =
        StringManager.getManager(Constants.PACKAGE);

    private static final String UMASK_PROPERTY_NAME =
        Constants.PACKAGE + ".SecurityListener.UMASK";

    private static final String UMASK_FORMAT = "%04o";

    /**
     * 不允许运行Tomcat的操作系统用户列表.
     */
    private final Set<String> checkedOsUsers = new HashSet<>();

    /**
     * 运行Tomcat的操作系统用户必须配置的最小umask. umask作为八进制处理.
     */
    private Integer minimumUmask = Integer.valueOf(7);


    public SecurityListener() {
        checkedOsUsers.add("root");
    }


    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        // 这是Lifecycle中最早的事件
        if (event.getType().equals(Lifecycle.BEFORE_INIT_EVENT)) {
            doChecks();
        }
    }


    /**
     * 设置不允许运行Tomcat的操作系统用户列表.
     * 默认情况下, 只有root被阻止运行Tomcat. 调用这个方法传入null或空字符串，将清空用户列表并禁用此检查.
     * 将使用系统默认位置，以不区分大小写的方式检查用户名.
     *
     * @param userNameList  不允许运行Tomcat的操作系统用户的逗号分隔列表
     */
    public void setCheckedOsUsers(String userNameList) {
        if (userNameList == null || userNameList.length() == 0) {
            checkedOsUsers.clear();
        } else {
            String[] userNames = userNameList.split(",");
            for (String userName : userNames) {
                if (userName.length() > 0) {
                    checkedOsUsers.add(userName.toLowerCase(Locale.getDefault()));
                }
            }
        }
    }


    /**
     * 返回不允许运行Tomcat的当前操作系统的用户列表.
     *
     * @return  操作系统用户名的逗号分隔列表.
     */
    public String getCheckedOsUsers() {
        return StringUtils.join(checkedOsUsers);
    }


    /**
     * 设置Tomcat开始之前必须配置的最小umask.
     *
     * @param umask OS命令<i>umask</i>返回的4位数 umask
     */
    public void setMinimumUmask(String umask) {
        if (umask == null || umask.length() == 0) {
            minimumUmask = Integer.valueOf(0);
        } else {
            minimumUmask = Integer.valueOf(umask, 8);
        }
    }


    /**
     * 获取Tomcat开始之前必须配置的最小umask.
     *
     * @return  OS命令<i>umask</i>返回的4位数 umask
     */
    public String getMinimumUmask() {
        return String.format(UMASK_FORMAT, minimumUmask);
    }


    /**
     * 执行安全检查. 每个检查都应采用单独的方法.
     */
    protected void doChecks() {
        checkOsUser();
        checkUmask();
    }


    protected void checkOsUser() {
        String userName = System.getProperty("user.name");
        if (userName != null) {
            String userNameLC = userName.toLowerCase(Locale.getDefault());

            if (checkedOsUsers.contains(userNameLC)) {
                // 必须抛出Error强迫启动进程中止
                throw new Error(sm.getString(
                        "SecurityListener.checkUserWarning", userName));
            }
        }
    }


    protected void checkUmask() {
        String prop = System.getProperty(UMASK_PROPERTY_NAME);
        Integer umask = null;
        if (prop != null) {
            try {
                 umask = Integer.valueOf(prop, 8);
            } catch (NumberFormatException nfe) {
                log.warn(sm.getString("SecurityListener.checkUmaskParseFail",
                        prop));
            }
        }
        if (umask == null) {
            if (Constants.CRLF.equals(System.lineSeparator())) {
                // 可能运行在Windows上，所以没有umask
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("SecurityListener.checkUmaskSkip"));
                }
                return;
            } else {
                if (minimumUmask.intValue() > 0) {
                    log.warn(sm.getString(
                            "SecurityListener.checkUmaskNone",
                            UMASK_PROPERTY_NAME, getMinimumUmask()));
                }
                return;
            }
        }

        if ((umask.intValue() & minimumUmask.intValue()) !=
                minimumUmask.intValue()) {
            throw new Error(sm.getString("SecurityListener.checkUmaskFail",
                    String.format(UMASK_FORMAT, umask), getMinimumUmask()));
        }
    }
}
