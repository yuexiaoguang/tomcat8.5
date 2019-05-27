package org.apache.naming.factory;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.apache.naming.ResourceRef;

/**
 * 符合JavaBean规范的任何资源的对象工厂.
 * 
 * <p>这个工厂可以在<code>conf/server.xml</code>配置文件中使用<code>&lt;DefaultContext&gt;</code>
 * 或<code>&lt;Context&gt;</code>元素配置. 工厂配置的一个例子是:</p>
 * <pre>
 * &lt;Resource name="jdbc/myDataSource" auth="SERVLET"
 *   type="oracle.jdbc.pool.OracleConnectionCacheImpl"/&gt;
 * &lt;ResourceParams name="jdbc/myDataSource"&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;factory&lt;/name&gt;
 *     &lt;value&gt;org.apache.naming.factory.BeanFactory&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;driverType&lt;/name&gt;
 *     &lt;value&gt;thin&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;serverName&lt;/name&gt;
 *     &lt;value&gt;hue&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;networkProtocol&lt;/name&gt;
 *     &lt;value&gt;tcp&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;databaseName&lt;/name&gt;
 *     &lt;value&gt;XXXX&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;portNumber&lt;/name&gt;
 *     &lt;value&gt;NNNN&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;user&lt;/name&gt;
 *     &lt;value&gt;XXXX&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;password&lt;/name&gt;
 *     &lt;value&gt;XXXX&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;maxLimit&lt;/name&gt;
 *     &lt;value&gt;5&lt;/value&gt;
 *   &lt;/parameter&gt;
 * &lt;/ResourceParams&gt;
 * </pre>
 */
public class BeanFactory implements ObjectFactory {

    /**
     * 创建一个新bean实例
     * 
     * @param obj 描述bean的引用对象
     */
    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                    Hashtable<?,?> environment)
        throws NamingException {

        if (obj instanceof ResourceRef) {

            try {

                Reference ref = (Reference) obj;
                String beanClassName = ref.getClassName();
                Class<?> beanClass = null;
                ClassLoader tcl =
                    Thread.currentThread().getContextClassLoader();
                if (tcl != null) {
                    try {
                        beanClass = tcl.loadClass(beanClassName);
                    } catch(ClassNotFoundException e) {
                    }
                } else {
                    try {
                        beanClass = Class.forName(beanClassName);
                    } catch(ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                if (beanClass == null) {
                    throw new NamingException
                        ("Class not found: " + beanClassName);
                }

                BeanInfo bi = Introspector.getBeanInfo(beanClass);
                PropertyDescriptor[] pda = bi.getPropertyDescriptors();

                Object bean = beanClass.getConstructor().newInstance();

                /* 使用显式配置的setter查找属性 */
                RefAddr ra = ref.get("forceString");
                Map<String, Method> forced = new HashMap<>();
                String value;

                if (ra != null) {
                    value = (String)ra.getContent();
                    Class<?> paramTypes[] = new Class[1];
                    paramTypes[0] = String.class;
                    String setterName;
                    int index;

                    /* Items are given as comma separated list */
                    for (String param: value.split(",")) {
                        param = param.trim();
                        /* 
                         * 单个item 可以是 name=method的形式，也可以只有一个属性名(并且将使用标准的setter) */
                        index = param.indexOf('=');
                        if (index >= 0) {
                            setterName = param.substring(index + 1).trim();
                            param = param.substring(0, index).trim();
                        } else {
                            setterName = "set" +
                                         param.substring(0, 1).toUpperCase(Locale.ENGLISH) +
                                         param.substring(1);
                        }
                        try {
                            forced.put(param,
                                       beanClass.getMethod(setterName, paramTypes));
                        } catch (NoSuchMethodException|SecurityException ex) {
                            throw new NamingException
                                ("Forced String setter " + setterName +
                                 " not found for property " + param);
                        }
                    }
                }

                Enumeration<RefAddr> e = ref.getAll();

                while (e.hasMoreElements()) {

                    ra = e.nextElement();
                    String propName = ra.getType();

                    if (propName.equals(Constants.FACTORY) ||
                        propName.equals("scope") || propName.equals("auth") ||
                        propName.equals("forceString") ||
                        propName.equals("singleton")) {
                        continue;
                    }

                    value = (String)ra.getContent();

                    Object[] valueArray = new Object[1];

                    /* Shortcut for properties with explicitly configured setter */
                    Method method = forced.get(propName);
                    if (method != null) {
                        valueArray[0] = value;
                        try {
                            method.invoke(bean, valueArray);
                        } catch (IllegalAccessException|
                                 IllegalArgumentException|
                                 InvocationTargetException ex) {
                            throw new NamingException
                                ("Forced String setter " + method.getName() +
                                 " threw exception for property " + propName);
                        }
                        continue;
                    }

                    int i = 0;
                    for (i = 0; i<pda.length; i++) {

                        if (pda[i].getName().equals(propName)) {

                            Class<?> propType = pda[i].getPropertyType();

                            if (propType.equals(String.class)) {
                                valueArray[0] = value;
                            } else if (propType.equals(Character.class)
                                       || propType.equals(char.class)) {
                                valueArray[0] =
                                    Character.valueOf(value.charAt(0));
                            } else if (propType.equals(Byte.class)
                                       || propType.equals(byte.class)) {
                                valueArray[0] = Byte.valueOf(value);
                            } else if (propType.equals(Short.class)
                                       || propType.equals(short.class)) {
                                valueArray[0] = Short.valueOf(value);
                            } else if (propType.equals(Integer.class)
                                       || propType.equals(int.class)) {
                                valueArray[0] = Integer.valueOf(value);
                            } else if (propType.equals(Long.class)
                                       || propType.equals(long.class)) {
                                valueArray[0] = Long.valueOf(value);
                            } else if (propType.equals(Float.class)
                                       || propType.equals(float.class)) {
                                valueArray[0] = Float.valueOf(value);
                            } else if (propType.equals(Double.class)
                                       || propType.equals(double.class)) {
                                valueArray[0] = Double.valueOf(value);
                            } else if (propType.equals(Boolean.class)
                                       || propType.equals(boolean.class)) {
                                valueArray[0] = Boolean.valueOf(value);
                            } else {
                                throw new NamingException
                                    ("String conversion for property " + propName +
                                     " of type '" + propType.getName() +
                                     "' not available");
                            }

                            Method setProp = pda[i].getWriteMethod();
                            if (setProp != null) {
                                setProp.invoke(bean, valueArray);
                            } else {
                                throw new NamingException
                                    ("Write not allowed for property: "
                                     + propName);
                            }

                            break;
                        }
                    }

                    if (i == pda.length) {
                        throw new NamingException
                            ("No set method found for property: " + propName);
                    }
                }

                return bean;
            } catch (java.beans.IntrospectionException ie) {
                NamingException ne = new NamingException(ie.getMessage());
                ne.setRootCause(ie);
                throw ne;
            } catch (java.lang.ReflectiveOperationException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ThreadDeath) {
                    throw (ThreadDeath) cause;
                }
                if (cause instanceof VirtualMachineError) {
                    throw (VirtualMachineError) cause;
                }
                NamingException ne = new NamingException(e.getMessage());
                ne.setRootCause(e);
                throw ne;
            }

        } else {
            return null;
        }

    }
}
