package org.apache.tomcat.util.modeler;


import java.util.ArrayList;
import java.util.Iterator;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;


/**
 * <p>用于监听属性修改的<code>NotificationBroadcaster</code>实现.
 * <code>BaseModelMBean</code>使用此类来处理属性更改事件的通知.
 *</p>
 */
public class BaseNotificationBroadcaster implements NotificationBroadcaster {

    // ----------------------------------------------------- Instance Variables

    /**
     * 注册的<code>BaseNotificationBroadcasterEntry</code>
     */
    protected ArrayList<BaseNotificationBroadcasterEntry> entries =
            new ArrayList<>();


    // --------------------------------------------------------- Public Methods


    /**
     * 向此MBean添加通知事件监听器.
     *
     * @param listener 将收到事件通知的监听器
     * @param filter 用于过滤实际传递的事件通知的过滤器, 或<code>null</code>
     * @param handback 要与事件通知一起发送的Handback对象
     *
     * @exception IllegalArgumentException 如果listener参数为null
     */
    @Override
    public void addNotificationListener(NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback)
        throws IllegalArgumentException {

        synchronized (entries) {

            // 优化以合并属性名称过滤器
            if (filter instanceof BaseAttributeFilter) {
                BaseAttributeFilter newFilter = (BaseAttributeFilter) filter;
                Iterator<BaseNotificationBroadcasterEntry> items =
                    entries.iterator();
                while (items.hasNext()) {
                    BaseNotificationBroadcasterEntry item = items.next();
                    if ((item.listener == listener) &&
                        (item.filter != null) &&
                        (item.filter instanceof BaseAttributeFilter) &&
                        (item.handback == handback)) {
                        BaseAttributeFilter oldFilter =
                            (BaseAttributeFilter) item.filter;
                        String newNames[] = newFilter.getNames();
                        String oldNames[] = oldFilter.getNames();
                        if (newNames.length == 0) {
                            oldFilter.clear();
                        } else {
                            if (oldNames.length != 0) {
                                for (int i = 0; i < newNames.length; i++)
                                    oldFilter.addAttribute(newNames[i]);
                            }
                        }
                        return;
                    }
                }
            }

            // General purpose addition of a new entry
            entries.add(new BaseNotificationBroadcasterEntry
                        (listener, filter, handback));
        }

    }


    /**
     * 返回描述此MBean发送的通知的<code>MBeanNotificationInfo</code>对象.
     */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {

        return (new MBeanNotificationInfo[0]);

    }


    /**
     * 从此MBean中删除通知事件监听器.
     *
     * @param listener 要删除的监听器 (此监听器的所有注册关系都将删除)
     *
     * @exception ListenerNotFoundException 如果此监听器未在MBean中注册
     */
    @Override
    public void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {

        synchronized (entries) {
            Iterator<BaseNotificationBroadcasterEntry> items =
                entries.iterator();
            while (items.hasNext()) {
                BaseNotificationBroadcasterEntry item = items.next();
                if (item.listener == listener)
                    items.remove();
            }
        }

    }


    /**
     * 将指定的通知发送给所有感兴趣的监听器.
     *
     * @param notification 要发送的通知
     */
    public void sendNotification(Notification notification) {

        synchronized (entries) {
            Iterator<BaseNotificationBroadcasterEntry> items =
                entries.iterator();
            while (items.hasNext()) {
                BaseNotificationBroadcasterEntry item = items.next();
                if ((item.filter != null) &&
                    (!item.filter.isNotificationEnabled(notification)))
                    continue;
                item.listener.handleNotification(notification, item.handback);
            }
        }

    }

}


/**
 * 表示特定注册的监听器条目.
 */
class BaseNotificationBroadcasterEntry {

    public BaseNotificationBroadcasterEntry(NotificationListener listener,
                                            NotificationFilter filter,
                                            Object handback) {
        this.listener = listener;
        this.filter = filter;
        this.handback = handback;
    }

    public NotificationFilter filter = null;

    public Object handback = null;

    public NotificationListener listener = null;

}
