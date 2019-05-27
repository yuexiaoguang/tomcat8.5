package org.apache.tomcat.util.modeler;


import java.util.HashSet;

import javax.management.AttributeChangeNotification;
import javax.management.Notification;
import javax.management.NotificationFilter;


/**
 * <p>用于属性更改通知的<code>NotificationFilter</code>的实现.
 * 当应用未提供过滤器时，<code>BaseModelMBean</code>使用此类来构造属性更改通知事件过滤器.</p>
 */
public class BaseAttributeFilter implements NotificationFilter {

    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------------- Constructors

    /**
     * @param name 此过滤器要接受的属性的名称, 或<code>null</code>接受所有的属性名
     */
    public BaseAttributeFilter(String name) {

        super();
        if (name != null)
            addAttribute(name);

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 此过滤器接受的属性名称集. 如果此列表为空, 接受所有属性名称.
     */
    private HashSet<String> names = new HashSet<>();


    // --------------------------------------------------------- Public Methods


    /**
     * 将新属性名称添加到此过滤器接受的名称集中.
     *
     * @param name 要接受的属性的名称
     */
    public void addAttribute(String name) {
        synchronized (names) {
            names.add(name);
        }
    }


    /**
     * 清除此过滤器中的所有可接受名称, 这样它就会接受所有属性名称.
     */
    public void clear() {
        synchronized (names) {
            names.clear();
        }
    }


    /**
     * 返回此过滤器接受的名称集. 如果此过滤器接受所有属性名称, 将返回零长度数组.
     * 
     * @return 名称数组
     */
    public String[] getNames() {
        synchronized (names) {
            return names.toArray(new String[names.size()]);
        }
    }


    /**
     * <p>测试是否为此事件启用了通知.
     * 返回 true, 如果:</p>
     * <ul>
     * <li>这是属性更改通知</li>
     * <li>可接受的名称集合为空 (暗示所有属性名称都是有意义的), 或者接受的名称集包括此通知中的属性名称</li>
     * </ul>
     */
    @Override
    public boolean isNotificationEnabled(Notification notification) {

        if (notification == null)
            return false;
        if (!(notification instanceof AttributeChangeNotification))
            return false;
        AttributeChangeNotification acn =
            (AttributeChangeNotification) notification;
        if (!AttributeChangeNotification.ATTRIBUTE_CHANGE.equals(acn.getType()))
            return false;
        synchronized (names) {
            if (names.size() < 1)
                return true;
            else
                return (names.contains(acn.getAttributeName()));
        }
    }


    /**
     * 从此过滤器接受的名称集中删除属性名称.
     *
     * @param name 要删除的属性的名称
     */
    public void removeAttribute(String name) {

        synchronized (names) {
            names.remove(name);
        }
    }
}
