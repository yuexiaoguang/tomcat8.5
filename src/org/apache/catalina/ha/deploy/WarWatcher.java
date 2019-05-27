package org.apache.catalina.ha.deploy;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * <b>WarWatcher </b>监视 deployDir的修改, 并通知监听器.
 */
public class WarWatcher {

    /*--Static Variables----------------------------------------*/
    private static final Log log = LogFactory.getLog(WarWatcher.class);
    private static final StringManager sm = StringManager.getManager(WarWatcher.class);

    /*--Instance Variables--------------------------------------*/
    /**
     * 监视war文件的目录
     */
    protected final File watchDir;

    /**
     * 通知父级的修改
     */
    protected final FileChangeListener listener;

    /**
     * 当前部署的文件
     */
    protected final Map<String, WarInfo> currentStatus = new HashMap<>();

    /*--Constructor---------------------------------------------*/

    public WarWatcher(FileChangeListener listener, File watchDir) {
        this.listener = listener;
        this.watchDir = watchDir;
    }

    /*--Logic---------------------------------------------------*/

    /**
     * 检查修改并向监听器发送通知
     */
    public void check() {
        if (log.isDebugEnabled())
            log.debug(sm.getString("warWatcher.checkingWars", watchDir));
        File[] list = watchDir.listFiles(new WarFilter());
        if (list == null) {
            log.warn(sm.getString("warWatcher.cantListWatchDir",
                                  watchDir));

            list = new File[0];
        }
        // 首先确保所有文件都列在当前的状态
        for (int i = 0; i < list.length; i++) {
            if(!list[i].exists())
                log.warn(sm.getString("warWatcher.listedFileDoesNotExist",
                                      list[i], watchDir));

            addWarInfo(list[i]);
        }

        // 检查所有状态码 并更新 FarmDeployer
        for (Iterator<Map.Entry<String,WarInfo>> i =
                currentStatus.entrySet().iterator(); i.hasNext();) {
            Map.Entry<String,WarInfo> entry = i.next();
            WarInfo info = entry.getValue();
            if(log.isTraceEnabled())
                log.trace(sm.getString("warWatcher.checkingWar",
                                       info.getWar()));
            int check = info.check();
            if (check == 1) {
                listener.fileModified(info.getWar());
            } else if (check == -1) {
                listener.fileRemoved(info.getWar());
                //no need to keep in memory
                i.remove();
            }
            if(log.isTraceEnabled())
                log.trace(sm.getString("warWatcher.checkWarResult",
                                       Integer.valueOf(check),
                                       info.getWar()));
        }

    }

    /**
     * 添加集群 war到监视器状态
     * 
     * @param warfile 要添加的WAR
     */
    protected void addWarInfo(File warfile) {
        WarInfo info = currentStatus.get(warfile.getAbsolutePath());
        if (info == null) {
            info = new WarInfo(warfile);
            info.setLastState(-1); //assume file is non existent
            currentStatus.put(warfile.getAbsolutePath(), info);
        }
    }

    /**
     * 清空监视器
     */
    public void clear() {
        currentStatus.clear();
    }


    /*--Inner classes-------------------------------------------*/

    /**
     * war文件的文件名称过滤器
     */
    protected static class WarFilter implements java.io.FilenameFilter {
        @Override
        public boolean accept(File path, String name) {
            if (name == null)
                return false;
            return name.endsWith(".war");
        }
    }

    /**
     * 现有的WAR文件的文件信息
     */
    protected static class WarInfo {
        protected final File war;

        protected long lastChecked = 0;

        protected long lastState = 0;

        public WarInfo(File war) {
            this.war = war;
            this.lastChecked = war.lastModified();
            if (!war.exists())
                lastState = -1;
        }

        public boolean modified() {
            return war.exists() && war.lastModified() > lastChecked;
        }

        public boolean exists() {
            return war.exists();
        }

        /**
         * 返回 1, 如果文件已被添加/修改; 0 , 如果文件未修改; -1, 如果文件已经删除
         *
         * @return int 1=file added; 0=unchanged; -1=file removed
         */
        public int check() {
            //file unchanged by default
            int result = 0;

            if (modified()) {
                //file has changed - timestamp
                result = 1;
                lastState = result;
            } else if ((!exists()) && (!(lastState == -1))) {
                //file was removed
                result = -1;
                lastState = result;
            } else if ((lastState == -1) && exists()) {
                //file was added
                result = 1;
                lastState = result;
            }
            this.lastChecked = System.currentTimeMillis();
            return result;
        }

        public File getWar() {
            return war;
        }

        @Override
        public int hashCode() {
            return war.getAbsolutePath().hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof WarInfo) {
                WarInfo wo = (WarInfo) other;
                return wo.getWar().equals(getWar());
            } else {
                return false;
            }
        }

        protected void setLastState(int lastState) {
            this.lastState = lastState;
        }
    }
}