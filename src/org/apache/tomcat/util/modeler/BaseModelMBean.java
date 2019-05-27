package org.apache.tomcat.util.modeler;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;

import javax.management.Attribute;
import javax.management.AttributeChangeNotification;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeErrorException;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.ModelMBeanNotificationBroadcaster;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * <p><code>DynamicMBean</code>接口的基础实现, 它支持接口契约的最低要求.</p>
 *
 * <p>这可以直接用于包装现有的java bean, 或者在mlet内部或任何地方使用MBean.
 *
 * 限制:
 * <ul>
 * <li>仅支持<code>objectReference</code>类型的受管资源.</li>
 * <li>不支持缓存属性值和操作结果. 立即执行对<code>invoke()</code>的所有调用.</li>
 * <li>不支持MBean属性和操作的持久性.</li>
 * <li>引用为属性类型，操作参数或操作返回值的所有类必须是以下之一:
 *     <ul>
 *     <li>Java原始类型之一 (boolean, byte, char, double, float, integer, long, short).  相应的值将自动包装在适当的包装类中.</li>
 *     <li>不返回任何值的操作应声明返回类型<code>void</code>.</li>
 *     </ul>
 * <li>不支持属性缓存</li>
 * </ul>
 */
public class BaseModelMBean implements DynamicMBean, MBeanRegistration, ModelMBeanNotificationBroadcaster {
    private static final Log log = LogFactory.getLog(BaseModelMBean.class);

    // ----------------------------------------------------------- Constructors

    /**
     * @exception MBeanException 如果对象的初始化程序抛出异常
     * @exception RuntimeOperationsException 如果发生 IllegalArgumentException
     */
    protected BaseModelMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }

    // ----------------------------------------------------- Instance Variables

    protected ObjectName oname=null;

    /**
     * 广播通知属性的更改.
     */
    protected BaseNotificationBroadcaster attributeBroadcaster = null;

    /**
     * 一般通知的广播通知.
     */
    protected BaseNotificationBroadcaster generalBroadcaster = null;

    /** mbean实例的元数据.
     */
    protected ManagedBean managedBean = null;

    /**
     * 此MBean与之关联的受管资源.
     */
    protected Object resource = null;

    // --------------------------------------------------- DynamicMBean Methods
    // TODO: move to ManagedBean
    static final Object[] NO_ARGS_PARAM = new Object[0];

    protected String resourceType = null;

    // key: operation val: invoke method
    //private Hashtable invokeAttMap=new Hashtable();

    /**
     * 获取并返回此MBean的特定属性的值.
     *
     * @param name 请求的属性的名称
     *
     * @exception AttributeNotFoundException 如果此MBean不支持此属性
     * @exception MBeanException 如果对象的初始化程序抛出异常
     * @exception ReflectionException 如果在调用getter时发生Java反射异常
     */
    @Override
    public Object getAttribute(String name)
        throws AttributeNotFoundException, MBeanException,
            ReflectionException {
        // Validate the input parameters
        if (name == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Attribute name is null"),
                 "Attribute name is null");

        if( (resource instanceof DynamicMBean) &&
             ! ( resource instanceof BaseModelMBean )) {
            return ((DynamicMBean)resource).getAttribute(name);
        }

        Method m=managedBean.getGetter(name, this, resource);
        Object result = null;
        try {
            Class<?> declaring = m.getDeclaringClass();
            // workaround for catalina weird mbeans - the declaring class is BaseModelMBean.
            // but this is the catalina class.
            if( declaring.isAssignableFrom(this.getClass()) ) {
                result = m.invoke(this, NO_ARGS_PARAM );
            } else {
                result = m.invoke(resource, NO_ARGS_PARAM );
            }
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if (t == null)
                t = e;
            if (t instanceof RuntimeException)
                throw new RuntimeOperationsException
                    ((RuntimeException) t, "Exception invoking method " + name);
            else if (t instanceof Error)
                throw new RuntimeErrorException
                    ((Error) t, "Error invoking method " + name);
            else
                throw new MBeanException
                    (e, "Exception invoking method " + name);
        } catch (Exception e) {
            throw new MBeanException
                (e, "Exception invoking method " + name);
        }

        // 返回此方法调用的结果
        // FIXME - should we validate the return type?
        return (result);
    }


    /**
     * 获取并返回此MBean的多个属性的值.
     *
     * @param names 请求的属性的名称
     */
    @Override
    public AttributeList getAttributes(String names[]) {

        // Validate the input parameters
        if (names == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Attribute names list is null"),
                 "Attribute names list is null");

        // Prepare our response, eating all exceptions
        AttributeList response = new AttributeList();
        for (int i = 0; i < names.length; i++) {
            try {
                response.add(new Attribute(names[i],getAttribute(names[i])));
            } catch (Exception e) {
                // 响应中没有特定属性, 表示getter有问题
            }
        }
        return (response);

    }

    public void setManagedBean(ManagedBean managedBean) {
        this.managedBean = managedBean;
    }

    /**
     * 返回此MBean的<code>MBeanInfo</code>对象.
     */
    @Override
    public MBeanInfo getMBeanInfo() {
        return managedBean.getMBeanInfo();
    }


    /**
     * 在此MBean上调用特定方法，并返回任何返回的值.
     *
     * <p><strong>IMPLEMENTATION NOTE</strong> - 此实现将尝试在MBean本身上调用此方法, 或者（如果不可用）与此MBean关联的受管资源对象.</p>
     *
     * @param name 要调用的操作的名称
     * @param params 包含此操作的方法参数的数组
     * @param signature 包含表示此操作的签名的类名的数组
     *
     * @exception MBeanException 如果对象的初始化程序抛出异常
     * @exception ReflectionException 如果在调用方法时发生Java反射异常
     */
    @Override
    public Object invoke(String name, Object params[], String signature[])
        throws MBeanException, ReflectionException
    {
        if( (resource instanceof DynamicMBean) &&
             ! ( resource instanceof BaseModelMBean )) {
            return ((DynamicMBean)resource).invoke(name, params, signature);
        }

        // Validate the input parameters
        if (name == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Method name is null"),
                 "Method name is null");

        if( log.isDebugEnabled()) log.debug("Invoke " + name);

        Method method= managedBean.getInvoke(name, params, signature, this, resource);

        // 在适当的对象上调用所选方法
        Object result = null;
        try {
            if( method.getDeclaringClass().isAssignableFrom( this.getClass()) ) {
                result = method.invoke(this, params );
            } else {
                result = method.invoke(resource, params);
            }
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            log.error("Exception invoking method " + name , t );
            if (t == null)
                t = e;
            if (t instanceof RuntimeException)
                throw new RuntimeOperationsException
                    ((RuntimeException) t, "Exception invoking method " + name);
            else if (t instanceof Error)
                throw new RuntimeErrorException
                    ((Error) t, "Error invoking method " + name);
            else
                throw new MBeanException
                    ((Exception)t, "Exception invoking method " + name);
        } catch (Exception e) {
            log.error("Exception invoking method " + name , e );
            throw new MBeanException
                (e, "Exception invoking method " + name);
        }

        // 返回此方法调用的结果
        // FIXME - should we validate the return type?
        return (result);

    }

    static Class<?> getAttributeClass(String signature)
        throws ReflectionException
    {
        if (signature.equals(Boolean.TYPE.getName()))
            return Boolean.TYPE;
        else if (signature.equals(Byte.TYPE.getName()))
            return Byte.TYPE;
        else if (signature.equals(Character.TYPE.getName()))
            return Character.TYPE;
        else if (signature.equals(Double.TYPE.getName()))
            return Double.TYPE;
        else if (signature.equals(Float.TYPE.getName()))
            return Float.TYPE;
        else if (signature.equals(Integer.TYPE.getName()))
            return Integer.TYPE;
        else if (signature.equals(Long.TYPE.getName()))
            return Long.TYPE;
        else if (signature.equals(Short.TYPE.getName()))
            return Short.TYPE;
        else {
            try {
                ClassLoader cl=Thread.currentThread().getContextClassLoader();
                if( cl!=null )
                    return cl.loadClass(signature);
            } catch( ClassNotFoundException e ) {
            }
            try {
                return Class.forName(signature);
            } catch (ClassNotFoundException e) {
                throw new ReflectionException
                    (e, "Cannot find Class for " + signature);
            }
        }
    }

    /**
     * 设置此MBean的特定属性的值.
     *
     * @param attribute 要设置的属性的标识和新值
     *
     * @exception AttributeNotFoundException 如果此MBean不支持此属性
     * @exception MBeanException 如果对象的初始化程序抛出异常
     * @exception ReflectionException 如果在调用getter时发生Java反射异常
     */
    @Override
    public void setAttribute(Attribute attribute)
        throws AttributeNotFoundException, MBeanException,
        ReflectionException
    {
        if( log.isDebugEnabled() )
            log.debug("Setting attribute " + this + " " + attribute );

        if( (resource instanceof DynamicMBean) &&
             ! ( resource instanceof BaseModelMBean )) {
            try {
                ((DynamicMBean)resource).setAttribute(attribute);
            } catch (InvalidAttributeValueException e) {
                throw new MBeanException(e);
            }
            return;
        }

        // Validate the input parameters
        if (attribute == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Attribute is null"),
                 "Attribute is null");

        String name = attribute.getName();
        Object value = attribute.getValue();

        if (name == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Attribute name is null"),
                 "Attribute name is null");

        Object oldValue=null;
        //if( getAttMap.get(name) != null )
        //    oldValue=getAttribute( name );

        Method m=managedBean.getSetter(name,this,resource);

        try {
            if( m.getDeclaringClass().isAssignableFrom( this.getClass()) ) {
                m.invoke(this, new Object[] { value });
            } else {
                m.invoke(resource, new Object[] { value });
            }
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if (t == null)
                t = e;
            if (t instanceof RuntimeException)
                throw new RuntimeOperationsException
                    ((RuntimeException) t, "Exception invoking method " + name);
            else if (t instanceof Error)
                throw new RuntimeErrorException
                    ((Error) t, "Error invoking method " + name);
            else
                throw new MBeanException
                    (e, "Exception invoking method " + name);
        } catch (Exception e) {
            log.error("Exception invoking method " + name , e );
            throw new MBeanException
                (e, "Exception invoking method " + name);
        }
        try {
            sendAttributeChangeNotification(new Attribute( name, oldValue),
                    attribute);
        } catch(Exception ex) {
            log.error("Error sending notification " + name, ex);
        }
        //attributes.put( name, value );
//        if( source != null ) {
//            // this mbean is associated with a source - maybe we want to persist
//            source.updateField(oname, name, value);
//        }
    }

    @Override
    public String toString() {
        if( resource==null )
            return "BaseModelMbean[" + resourceType + "]";
        return resource.toString();
    }

    /**
     * 设置此MBean的多个属性的值.
     *
     * @param attributes 要设置的名称和值
     *
     * @return 已设置的属性列表及其新值
     */
    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        AttributeList response = new AttributeList();

        // 验证输入参数
        if (attributes == null)
            return response;

        // Prepare and return our response, eating all exceptions
        String names[] = new String[attributes.size()];
        int n = 0;
        Iterator<?> items = attributes.iterator();
        while (items.hasNext()) {
            Attribute item = (Attribute) items.next();
            names[n++] = item.getName();
            try {
                setAttribute(item);
            } catch (Exception e) {
                // Ignore all exceptions
            }
        }

        return (getAttributes(names));

    }


    // ----------------------------------------------------- ModelMBean Methods


    /**
     * 获取在此ModelMBean管理接口中执行所有方法的对象的实例句柄.
     *
     * @return 后端管理对象
     * @exception InstanceNotFoundException 如果找不到管理的资源对象
     * @exception InvalidTargetObjectTypeException 如果受管资源对象的类型错误
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception RuntimeOperationsException 如果受管资源或资源类型为<code>null</code>或无效
     */
    public Object getManagedResource()
        throws InstanceNotFoundException, InvalidTargetObjectTypeException,
        MBeanException, RuntimeOperationsException {

        if (resource == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Managed resource is null"),
                 "Managed resource is null");

        return resource;

    }


    /**
     * 设置对象的实例句柄，将在该对象的实例句柄中执行此ModelMBean管理接口中的所有方法.
     *
     * 调用者可以向资源提供mbean实例或对象名称.
     *
     * @param resource 要管理的资源对象
     * @param type 受管资源的引用类型 ("ObjectReference", "Handle", "IOR", "EJBHandle", "RMIReference")
     *
     * @exception InstanceNotFoundException 如果找不到管理的资源对象
     * @exception MBeanException 如果对象的初始化器项抛出异常
     * @exception RuntimeOperationsException 如果受管资源或资源类型为<code>null</code>或无效
     */
    public void setManagedResource(Object resource, String type)
        throws InstanceNotFoundException,
        MBeanException, RuntimeOperationsException
    {
        if (resource == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Managed resource is null"),
                 "Managed resource is null");

//        if (!"objectreference".equalsIgnoreCase(type))
//            throw new InvalidTargetObjectTypeException(type);

        this.resource = resource;
        this.resourceType = resource.getClass().getName();

//        // Make the resource aware of the model mbean.
//        try {
//            Method m=resource.getClass().getMethod("setModelMBean",
//                    new Class[] {ModelMBean.class});
//            if( m!= null ) {
//                m.invoke(resource, new Object[] {this});
//            }
//        } catch( NoSuchMethodException t ) {
//            // ignore
//        } catch( Throwable t ) {
//            log.error( "Can't set model mbean ", t );
//        }
    }


    // ------------------------------ ModelMBeanNotificationBroadcaster Methods


    /**
     * 向此MBean添加属性更改通知事件监听器.
     *
     * @param listener 将收到事件通知的监听器
     * @param name 属性的名称, 或<code>null</code>表示监听所有属性
     * @param handback 要与事件通知一起发送的Handback对象
     *
     * @exception IllegalArgumentException 如果listener参数为null
     */
    @Override
    public void addAttributeChangeNotificationListener
        (NotificationListener listener, String name, Object handback)
        throws IllegalArgumentException {

        if (listener == null)
            throw new IllegalArgumentException("Listener is null");
        if (attributeBroadcaster == null)
            attributeBroadcaster = new BaseNotificationBroadcaster();

        if( log.isDebugEnabled() )
            log.debug("addAttributeNotificationListener " + listener);

        BaseAttributeFilter filter = new BaseAttributeFilter(name);
        attributeBroadcaster.addNotificationListener
            (listener, filter, handback);

    }


    /**
     * 从此MBean中删除属性更改通知事件监听器.
     *
     * @param listener 要删除的监听器
     * @param name 不再需要事件的属性名称
     *
     *
     * @exception ListenerNotFoundException 如果此监听器未在MBean中注册
     */
    @Override
    public void removeAttributeChangeNotificationListener
        (NotificationListener listener, String name)
        throws ListenerNotFoundException {

        if (listener == null)
            throw new IllegalArgumentException("Listener is null");

        // FIXME - currently this removes *all* notifications for this listener
        if (attributeBroadcaster != null) {
            attributeBroadcaster.removeNotificationListener(listener);
        }

    }


    /**
     * 发送一个 <code>AttributeChangeNotification</code>到所有注册的监听器.
     *
     * @param notification 将传递的<code>AttributeChangeNotification</code>
     *
     * @exception MBeanException 如果对象初始化程序抛出异常
     * @exception RuntimeOperationsException 当指定的通知为<code>null</code>或无效时，将包装IllegalArgumentException
     */
    @Override
    public void sendAttributeChangeNotification
        (AttributeChangeNotification notification)
        throws MBeanException, RuntimeOperationsException {

        if (notification == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Notification is null"),
                 "Notification is null");
        if (attributeBroadcaster == null)
            return; // This means there are no registered listeners
        if( log.isDebugEnabled() )
            log.debug( "AttributeChangeNotification " + notification );
        attributeBroadcaster.sendNotification(notification);

    }


    /**
     * 发送一个 <code>AttributeChangeNotification</code>到所有注册的监听器.
     *
     * @param oldValue <code>Attribute</code>的原始值
     * @param newValue <code>Attribute</code>的新值
     *
     * @exception MBeanException 如果对象初始化程序抛出异常
     * @exception RuntimeOperationsException 当指定的通知为<code>null</code>或无效时，将包装IllegalArgumentException
     */
    @Override
    public void sendAttributeChangeNotification
        (Attribute oldValue, Attribute newValue)
        throws MBeanException, RuntimeOperationsException {

        // Calculate the class name for the change notification
        String type = null;
        if (newValue.getValue() != null)
            type = newValue.getValue().getClass().getName();
        else if (oldValue.getValue() != null)
            type = oldValue.getValue().getClass().getName();
        else
            return;  // Old and new are both null == no change

        AttributeChangeNotification notification =
            new AttributeChangeNotification
            (this, 1, System.currentTimeMillis(),
             "Attribute value has changed",
             oldValue.getName(), type,
             oldValue.getValue(), newValue.getValue());
        sendAttributeChangeNotification(notification);

    }


    /**
     * 将<code>Notification</code>作为<code>jmx.modelmbean.general</code>通知发送给所有已注册的监听器.
     *
     * @param notification 将传递的<code>Notification</code>
     *
     * @exception MBeanException 如果对象初始化程序抛出异常
     * @exception RuntimeOperationsException 当指定的通知为<code>null</code>或无效时，将包装IllegalArgumentException
     */
    @Override
    public void sendNotification(Notification notification)
        throws MBeanException, RuntimeOperationsException {

        if (notification == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Notification is null"),
                 "Notification is null");
        if (generalBroadcaster == null)
            return; // This means there are no registered listeners
        generalBroadcaster.sendNotification(notification);

    }


    /**
     * 发送包含指定字符串的<code>Notification</code>, 作为<code>jmx.modelmbean.generic</code>通知.
     *
     * @param message 要传递的消息字符串
     *
     * @exception MBeanException 如果对象初始化程序抛出异常
     * @exception RuntimeOperationsException 当指定的通知为<code>null</code>或无效时，将包装IllegalArgumentException
     */
    @Override
    public void sendNotification(String message)
        throws MBeanException, RuntimeOperationsException {

        if (message == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Message is null"),
                 "Message is null");
        Notification notification = new Notification
            ("jmx.modelmbean.generic", this, 1, message);
        sendNotification(notification);

    }


    // ---------------------------------------- NotificationBroadcaster Methods


    /**
     * 向此MBean添加通知事件监听器.
     *
     * @param listener 将收到事件通知的监听器
     * @param filter 用于过滤实际传递的事件通知的过滤器对象, 或<code>null</code>
     * @param handback 要与事件通知一起发送的Handback对象
     *
     * @exception IllegalArgumentException 如果listener参数为null
     */
    @Override
    public void addNotificationListener(NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback)
        throws IllegalArgumentException {

        if (listener == null)
            throw new IllegalArgumentException("Listener is null");

        if( log.isDebugEnabled() ) log.debug("addNotificationListener " + listener);

        if (generalBroadcaster == null)
            generalBroadcaster = new BaseNotificationBroadcaster();
        generalBroadcaster.addNotificationListener
            (listener, filter, handback);

        // 会将属性更改通知发送给所有监听器
        // 可以使用正常过滤.
        // 问题是没有其他方法可以将属性更改侦听器添加到模型mbean ( AFAIK ). 我想应该修复规范.
        if (attributeBroadcaster == null)
            attributeBroadcaster = new BaseNotificationBroadcaster();

        if( log.isDebugEnabled() )
            log.debug("addAttributeNotificationListener " + listener);

        attributeBroadcaster.addNotificationListener
                (listener, filter, handback);
    }


    /**
     * 返回描述此MBean发送的通知的<code>MBeanNotificationInfo</code>对象.
     */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {

        // 获取应用程序通知集
        MBeanNotificationInfo current[] = getMBeanInfo().getNotifications();
        MBeanNotificationInfo response[] =
            new MBeanNotificationInfo[current.length + 2];
 //       Descriptor descriptor = null;

        // Fill in entry for general notifications
//        descriptor = new DescriptorSupport
//            (new String[] { "name=GENERIC",
//                            "descriptorType=notification",
//                            "log=T",
//                            "severity=5",
//                            "displayName=jmx.modelmbean.generic" });
        response[0] = new MBeanNotificationInfo
            (new String[] { "jmx.modelmbean.generic" },
             "GENERIC",
             "Text message notification from the managed resource");
             //descriptor);

        // Fill in entry for attribute change notifications
//        descriptor = new DescriptorSupport
//            (new String[] { "name=ATTRIBUTE_CHANGE",
//                            "descriptorType=notification",
//                            "log=T",
//                            "severity=5",
//                            "displayName=jmx.attribute.change" });
        response[1] = new MBeanNotificationInfo
            (new String[] { "jmx.attribute.change" },
             "ATTRIBUTE_CHANGE",
             "Observed MBean attribute value has changed");
             //descriptor);

        // 复制应用程序报告的剩余通知
        System.arraycopy(current, 0, response, 2, current.length);
        return (response);

    }


    /**
     * 从此MBean中删除通知事件监听器.
     *
     * @param listener 要删除的监听器 (将删除监听器的所有注册)
     *
     * @exception ListenerNotFoundException 如果此监听器未在MBean中注册
     */
    @Override
    public void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {

        if (listener == null)
            throw new IllegalArgumentException("Listener is null");

        if (generalBroadcaster != null) {
            generalBroadcaster.removeNotificationListener(listener);
        }

        if (attributeBroadcaster != null) {
            attributeBroadcaster.removeNotificationListener(listener);
        }
     }


    public String getModelerType() {
        return resourceType;
    }

    public String getClassName() {
        return getModelerType();
    }

    public ObjectName getJmxName() {
        return oname;
    }

    public String getObjectName() {
        if (oname != null) {
            return oname.toString();
        } else {
            return null;
        }
    }


    // -------------------- Registration  --------------------
    // XXX 可以在这里添加一些方法模式 - 像setName()和setDomain()一样，代码没有实现注册

    @Override
    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name)
            throws Exception
    {
        if( log.isDebugEnabled())
            log.debug("preRegister " + resource + " " + name );
        oname=name;
        if( resource instanceof MBeanRegistration ) {
            oname = ((MBeanRegistration)resource).preRegister(server, name );
        }
        return oname;
    }

    @Override
    public void postRegister(Boolean registrationDone) {
        if( resource instanceof MBeanRegistration ) {
            ((MBeanRegistration)resource).postRegister(registrationDone);
        }
    }

    @Override
    public void preDeregister() throws Exception {
        if( resource instanceof MBeanRegistration ) {
            ((MBeanRegistration)resource).preDeregister();
        }
    }

    @Override
    public void postDeregister() {
        if( resource instanceof MBeanRegistration ) {
            ((MBeanRegistration)resource).postDeregister();
        }
    }
}
