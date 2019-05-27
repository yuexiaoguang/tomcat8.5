package org.apache.tomcat.util.modeler;


import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;
import javax.management.ServiceNotFoundException;

import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.buf.StringUtils.Function;


/**
 * <p>托管bean（MBean）描述符的内部配置信息.</p>
 */
public class ManagedBean implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private static final String BASE_MBEAN = "org.apache.tomcat.util.modeler.BaseModelMBean";
    // ----------------------------------------------------- Instance Variables
    static final Class<?>[] NO_ARGS_PARAM_SIG = new Class[0];


    private final ReadWriteLock mBeanInfoLock = new ReentrantReadWriteLock();
    /**
     * 与此<code>ManagedBean</code>实例对应的<code>ModelMBeanInfo</code>对象.
     */
    private transient volatile MBeanInfo info = null;

    private Map<String,AttributeInfo> attributes = new HashMap<>();

    private Map<String,OperationInfo> operations = new HashMap<>();

    protected String className = BASE_MBEAN;
    protected String description = null;
    protected String domain = null;
    protected String group = null;
    protected String name = null;

    private NotificationInfo notifications[] = new NotificationInfo[0];
    protected String type = null;

    public ManagedBean() {
        AttributeInfo ai=new AttributeInfo();
        ai.setName("modelerType");
        ai.setDescription("Type of the modeled resource. Can be set only once");
        ai.setType("java.lang.String");
        ai.setWriteable(false);
        addAttribute(ai);
    }

    // ------------------------------------------------------------- Properties


    /**
     * @return 此MBean的属性集合.
     */
    public AttributeInfo[] getAttributes() {
        AttributeInfo result[] = new AttributeInfo[attributes.size()];
        attributes.values().toArray(result);
        return result;
    }


    /**
     * 此描述符描述的MBean的Java类的标准名称.
     * 如果没有指定, 将使用标准的 JMX 类 (<code>javax.management.modelmbean.RequiredModeLMBean</code>)
     */
    public String getClassName() {
        return this.className;
    }

    public void setClassName(String className) {
        mBeanInfoLock.writeLock().lock();
        try {
            this.className = className;
            this.info = null;
        } finally {
            mBeanInfoLock.writeLock().unlock();
        }
    }


    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        mBeanInfoLock.writeLock().lock();
        try {
            this.description = description;
            this.info = null;
        } finally {
            mBeanInfoLock.writeLock().unlock();
        }
    }


    /**
     * @return （可选）<code>ObjectName</code>域，其中MBean应在MBeanServer中注册.
     */
    public String getDomain() {
        return this.domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }


    /**
     * @return 此MBean所属的（可选）组.
     */
    public String getGroup() {
        return this.group;
    }

    public void setGroup(String group) {
        this.group = group;
    }


    /**
     * @return 此托管bean的名称, 在特定MBean服务器管理的所有MBean中必须是唯一的.
     */
    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        mBeanInfoLock.writeLock().lock();
        try {
            this.name = name;
            this.info = null;
        } finally {
            mBeanInfoLock.writeLock().unlock();
        }
    }


    /**
     * @return 此MBean的通知集合.
     */
    public NotificationInfo[] getNotifications() {
        return this.notifications;
    }


    /**
     * @return 此MBean的操作集合.
     */
    public OperationInfo[] getOperations() {
        OperationInfo[] result = new OperationInfo[operations.size()];
        operations.values().toArray(result);
        return result;
    }


    /**
     * @return 此描述符描述的托管bean描述的资源实现类的Java类的完全限定名.
     */
    public String getType() {
        return (this.type);
    }

    public void setType(String type) {
        mBeanInfoLock.writeLock().lock();
        try {
            this.type = type;
            this.info = null;
        } finally {
            mBeanInfoLock.writeLock().unlock();
        }
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 将新属性添加到此MBean的属性集.
     *
     * @param attribute 新的属性描述符
     */
    public void addAttribute(AttributeInfo attribute) {
        attributes.put(attribute.getName(), attribute);
    }


    /**
     * 向此MBean的通知集添加新通知.
     *
     * @param notification 新的通知描述符
     */
    public void addNotification(NotificationInfo notification) {
        mBeanInfoLock.writeLock().lock();
        try {
            NotificationInfo results[] =
                new NotificationInfo[notifications.length + 1];
            System.arraycopy(notifications, 0, results, 0,
                             notifications.length);
            results[notifications.length] = notification;
            notifications = results;
            this.info = null;
        } finally {
            mBeanInfoLock.writeLock().unlock();
        }
    }


    /**
     * 将新操作添加到此MBean的操作集.
     *
     * @param operation 新的操作描述符
     */
    public void addOperation(OperationInfo operation) {
        operations.put(createOperationKey(operation), operation);
    }


    /**
     * 创建并返回已使用此托管bean的<code>ModelMBeanInfo</code>信息预配置的<code>ModelMBean</code>, 并与指定的托管对象实例相关联.
     * 返回的<code>ModelMBean</code>不会注册到我们的<code>MBeanServer</code>.
     *
     * @param instance 托管对象的实例, 或<code>null</code>不关联实例
     * 
     * @return the MBean
     * @exception InstanceNotFoundException 如果找不到托管资源对象
     * @exception MBeanException 如果在实例化<code>ModelMBean</code>实例时出现问题
     * @exception RuntimeOperationsException 如果发生JMX运行时错误
     */
    public DynamicMBean createMBean(Object instance)
        throws InstanceNotFoundException,
        MBeanException, RuntimeOperationsException {

        BaseModelMBean mbean = null;

        // 加载ModelMBean实现类
        if(getClassName().equals(BASE_MBEAN)) {
            // Skip introspection
            mbean = new BaseModelMBean();
        } else {
            Class<?> clazz = null;
            Exception ex = null;
            try {
                clazz = Class.forName(getClassName());
            } catch (Exception e) {
            }

            if( clazz==null ) {
                try {
                    ClassLoader cl= Thread.currentThread().getContextClassLoader();
                    if ( cl != null)
                        clazz= cl.loadClass(getClassName());
                } catch (Exception e) {
                    ex=e;
                }
            }

            if( clazz==null) {
                throw new MBeanException
                    (ex, "Cannot load ModelMBean class " + getClassName());
            }
            try {
                // Stupid - this will set the default minfo first....
                mbean = (BaseModelMBean) clazz.getConstructor().newInstance();
            } catch (RuntimeOperationsException e) {
                throw e;
            } catch (Exception e) {
                throw new MBeanException
                    (e, "Cannot instantiate ModelMBean of class " +
                     getClassName());
            }
        }

        mbean.setManagedBean(this);

        // Set the managed resource (if any)
        try {
            if (instance != null)
                mbean.setManagedResource(instance, "ObjectReference");
        } catch (InstanceNotFoundException e) {
            throw e;
        }

        return mbean;
    }


    /**
     * 创建并返回描述整个托管bean的<code>ModelMBeanInfo</ code>对象.
     */
    MBeanInfo getMBeanInfo() {

        // 返回缓存信息 (if any)
        mBeanInfoLock.readLock().lock();
        try {
            if (info != null) {
                return info;
            }
        } finally {
            mBeanInfoLock.readLock().unlock();
        }

        mBeanInfoLock.writeLock().lock();
        try {
            if (info == null) {
                // 根据需要创建从属信息描述符
                AttributeInfo attrs[] = getAttributes();
                MBeanAttributeInfo attributes[] =
                    new MBeanAttributeInfo[attrs.length];
                for (int i = 0; i < attrs.length; i++)
                    attributes[i] = attrs[i].createAttributeInfo();

                OperationInfo opers[] = getOperations();
                MBeanOperationInfo operations[] =
                    new MBeanOperationInfo[opers.length];
                for (int i = 0; i < opers.length; i++)
                    operations[i] = opers[i].createOperationInfo();


                NotificationInfo notifs[] = getNotifications();
                MBeanNotificationInfo notifications[] =
                    new MBeanNotificationInfo[notifs.length];
                for (int i = 0; i < notifs.length; i++)
                    notifications[i] = notifs[i].createNotificationInfo();


                // Construct and return a new ModelMBeanInfo object
                info = new MBeanInfo(getClassName(),
                                     getDescription(),
                                     attributes,
                                     new MBeanConstructorInfo[] {},
                                     operations,
                                     notifications);
            }

            return info;
        } finally {
            mBeanInfoLock.writeLock().unlock();
        }
    }


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ManagedBean[");
        sb.append("name=");
        sb.append(name);
        sb.append(", className=");
        sb.append(className);
        sb.append(", description=");
        sb.append(description);
        if (group != null) {
            sb.append(", group=");
            sb.append(group);
        }
        sb.append(", type=");
        sb.append(type);
        sb.append("]");
        return sb.toString();

    }

    Method getGetter(String aname, BaseModelMBean mbean, Object resource)
            throws AttributeNotFoundException, ReflectionException {

        Method m = null;

        AttributeInfo attrInfo = attributes.get(aname);
        // 查找要使用的实际操作
        if (attrInfo == null)
            throw new AttributeNotFoundException(" Cannot find attribute " + aname + " for " + resource);

        String getMethod = attrInfo.getGetMethod();
        if (getMethod == null)
            throw new AttributeNotFoundException("Cannot find attribute " + aname + " get method name");

        Object object = null;
        NoSuchMethodException exception = null;
        try {
            object = mbean;
            m = object.getClass().getMethod(getMethod, NO_ARGS_PARAM_SIG);
        } catch (NoSuchMethodException e) {
            exception = e;
        }
        if (m== null && resource != null) {
            try {
                object = resource;
                m = object.getClass().getMethod(getMethod, NO_ARGS_PARAM_SIG);
                exception=null;
            } catch (NoSuchMethodException e) {
                exception = e;
            }
        }
        if (exception != null)
            throw new ReflectionException(exception,
                                          "Cannot find getter method " + getMethod);

        return m;
    }

    public Method getSetter(String aname, BaseModelMBean bean, Object resource)
            throws AttributeNotFoundException, ReflectionException {

        Method m = null;

        AttributeInfo attrInfo = attributes.get(aname);
        if (attrInfo == null)
            throw new AttributeNotFoundException(" Cannot find attribute " + aname);

        // 查找要使用的实际操作
        String setMethod = attrInfo.getSetMethod();
        if (setMethod == null)
            throw new AttributeNotFoundException("Cannot find attribute " + aname + " set method name");

        String argType=attrInfo.getType();

        Class<?> signature[] =
            new Class[] { BaseModelMBean.getAttributeClass( argType ) };

        Object object = null;
        NoSuchMethodException exception = null;
        try {
            object = bean;
            m = object.getClass().getMethod(setMethod, signature);
        } catch (NoSuchMethodException e) {
            exception = e;
        }
        if (m == null && resource != null) {
            try {
                object = resource;
                m = object.getClass().getMethod(setMethod, signature);
                exception=null;
            } catch (NoSuchMethodException e) {
                exception = e;
            }
        }
        if (exception != null)
            throw new ReflectionException(exception,
                                          "Cannot find setter method " + setMethod +
                    " " + resource);

        return m;
    }

    public Method getInvoke(String aname, Object[] params, String[] signature, BaseModelMBean bean, Object resource)
            throws MBeanException, ReflectionException {

        Method method = null;

        if (params == null)
            params = new Object[0];
        if (signature == null)
            signature = new String[0];
        if (params.length != signature.length)
            throw new RuntimeOperationsException(
                    new IllegalArgumentException(
                            "Inconsistent arguments and signature"),
                    "Inconsistent arguments and signature");

        // 获取所请求操作的ModelMBeanOperationInfo信息
        OperationInfo opInfo =
                operations.get(createOperationKey(aname, signature));
        if (opInfo == null)
            throw new MBeanException(new ServiceNotFoundException(
                    "Cannot find operation " + aname),
                    "Cannot find operation " + aname);

        // 准备Java反射API所需的签名
        // FIXME - should we use the signature from opInfo?
        Class<?> types[] = new Class[signature.length];
        for (int i = 0; i < signature.length; i++) {
            types[i] = BaseModelMBean.getAttributeClass(signature[i]);
        }

        // 找到要调用的方法, 在此MBean本身或相应的受管资源中
        // FIXME - Accessible methods in superinterfaces?
        Object object = null;
        Exception exception = null;
        try {
            object = bean;
            method = object.getClass().getMethod(aname, types);
        } catch (NoSuchMethodException e) {
            exception = e;
        }
        try {
            if ((method == null) && (resource != null)) {
                object = resource;
                method = object.getClass().getMethod(aname, types);
            }
        } catch (NoSuchMethodException e) {
            exception = e;
        }
        if (method == null) {
            throw new ReflectionException(exception, "Cannot find method "
                    + aname + " with this signature");
        }

        return method;
    }


    private String createOperationKey(OperationInfo operation) {
        StringBuilder key = new StringBuilder(operation.getName());
        key.append('(');
        StringUtils.join(operation.getSignature(), ',', new Function<ParameterInfo>() {
            @Override public String apply(ParameterInfo t) { return t.getType(); }}, key);
        key.append(')');
        return key.toString();
    }


    private String createOperationKey(String methodName, String[] parameterTypes) {
        StringBuilder key = new StringBuilder(methodName);
        key.append('(');
        StringUtils.join(parameterTypes, ',', key);
        key.append(')');

        return key.toString();
    }
}
