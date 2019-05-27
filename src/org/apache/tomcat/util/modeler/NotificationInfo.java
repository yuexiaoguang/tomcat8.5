package org.apache.tomcat.util.modeler;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.MBeanNotificationInfo;

/**
 * <p><code>Notification</code>描述符的内部配置信息.</p>
 */
public class NotificationInfo extends FeatureInfo {

    static final long serialVersionUID = -6319885418912650856L;

    // ----------------------------------------------------- Instance Variables


    /**
     * 与此<code>NotificationInfo</code>实例对应的<code>ModelMBeanNotificationInfo</code>对象.
     */
    transient MBeanNotificationInfo info = null;
    protected String notifTypes[] = new String[0];
    protected final ReadWriteLock notifTypesLock = new ReentrantReadWriteLock();

    // ------------------------------------------------------------- Properties


    @Override
    public void setDescription(String description) {
        super.setDescription(description);
        this.info = null;
    }


    @Override
    public void setName(String name) {
        super.setName(name);
        this.info = null;
    }


    /**
     * @return 此MBean的通知类型集.
     */
    public String[] getNotifTypes() {
        Lock readLock = notifTypesLock.readLock();
        readLock.lock();
        try {
            return this.notifTypes;
        } finally {
            readLock.unlock();
        }
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 将新通知类型添加到MBean管理的集合中.
     *
     * @param notifType 通知类型
     */
    public void addNotifType(String notifType) {

        Lock writeLock = notifTypesLock.writeLock();
        writeLock.lock();
        try {

            String results[] = new String[notifTypes.length + 1];
            System.arraycopy(notifTypes, 0, results, 0, notifTypes.length);
            results[notifTypes.length] = notifType;
            notifTypes = results;
            this.info = null;
        } finally {
            writeLock.unlock();
        }
    }


    /**
     * 创建并返回与此实例描述的属性对应的<code>ModelMBeanNotificationInfo</code>对象.
     */
    public MBeanNotificationInfo createNotificationInfo() {

        // 返回缓存的信息
        if (info != null)
            return info;

        info = new MBeanNotificationInfo
            (getNotifTypes(), getName(), getDescription());
        //Descriptor descriptor = info.getDescriptor();
        //addFields(descriptor);
        //info.setDescriptor(descriptor);
        return info;

    }


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("NotificationInfo[");
        sb.append("name=");
        sb.append(name);
        sb.append(", description=");
        sb.append(description);
        sb.append(", notifTypes=");
        Lock readLock = notifTypesLock.readLock();
        readLock.lock();
        try {
            sb.append(notifTypes.length);
        } finally {
            readLock.unlock();
        }
        sb.append("]");
        return (sb.toString());
    }
}
