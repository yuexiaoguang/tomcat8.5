package org.apache.catalina.mbeans;

import java.util.ArrayList;
import java.util.List;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Valve;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.HostConfig;
import org.apache.tomcat.util.modeler.BaseModelMBean;

public class ContainerMBean extends BaseModelMBean {

    /**
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception RuntimeOperationsException 如果发生IllegalArgumentException
     */
    public ContainerMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }

    /**
     * 添加一个子级Container. 不要启动它. 必须在完成必要的配置之后调用Start 方法.
     *
     * @param type 要添加的子级的ClassName 
     * @param name 要添加的子级的名称
     *
     * @exception MBeanException 如果不能添加子级
     */
    public void addChild(String type, String name) throws MBeanException{
        Container contained = null;
        try {
            contained = (Container)Class.forName(type).getConstructor().newInstance();
            contained.setName(name);

            if(contained instanceof StandardHost){
                HostConfig config = new HostConfig();
                contained.addLifecycleListener(config);
            } else if(contained instanceof StandardContext){
                ContextConfig config = new ContextConfig();
                contained.addLifecycleListener(config);
            }

        } catch (ReflectiveOperationException e) {
            throw new MBeanException(e);
        }

        boolean oldValue= true;

        ContainerBase container = null;
        try {
            container = (ContainerBase)getManagedResource();
            oldValue = container.getStartChildren();
            container.setStartChildren(false);
            container.addChild(contained);
            contained.init();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        } catch (LifecycleException e){
            throw new MBeanException(e);
        } finally {
            if(container != null) {
                container.setStartChildren(oldValue);
            }
        }
    }

    /**
     * 删除现有的子级Container.
     *
     * @param name 要删除的子级Container的名称
     * @throws MBeanException 如果不能删除子级
     */
    public void removeChild(String name) throws MBeanException{
        if(name != null){
            try {
                Container container = (Container)getManagedResource();
                Container contained = container.findChild(name);
                container.removeChild(contained);
            } catch (InstanceNotFoundException e) {
                throw new MBeanException(e);
            } catch (RuntimeOperationsException e) {
                throw new MBeanException(e);
            } catch (InvalidTargetObjectTypeException e) {
                throw new MBeanException(e);
            }
        }
    }

    /**
     * 向这个Container实例添加一个阀门.
     *
     * @param valveType 要添加的阀门的ClassName
     * 
     * @return 新阀门的MBean名称
     * @throws MBeanException 如果添加阀门失败
     */
    public String addValve(String valveType) throws MBeanException{
        Valve valve = null;
        try {
            valve = (Valve)Class.forName(valveType).getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new MBeanException(e);
        }

        if (valve == null) {
            return null;
        }

        try {
            Container container = (Container)getManagedResource();
            container.getPipeline().addValve(valve);
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        if (valve instanceof JmxEnabled) {
            return ((JmxEnabled)valve).getObjectName().toString();
        } else {
            return null;
        }
    }

    /**
     * 删除现有的Valve.
     *
     * @param valveName 要删除的阀门的MBean名称
     *
     * @exception MBeanException 如果不能删除
     */
    public void removeValve(String valveName) throws MBeanException{
        Container container=null;
        try {
            container = (Container)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        ObjectName oname;
        try {
            oname = new ObjectName(valveName);
        } catch (MalformedObjectNameException e) {
            throw new MBeanException(e);
        } catch (NullPointerException e) {
            throw new MBeanException(e);
        }

        if(container != null){
            Valve[] valves = container.getPipeline().getValves();
            for (int i = 0; i < valves.length; i++) {
                if (valves[i] instanceof JmxEnabled) {
                    ObjectName voname =
                            ((JmxEnabled) valves[i]).getObjectName();
                    if (voname.equals(oname)) {
                        container.getPipeline().removeValve(valves[i]);
                    }
                }
            }
        }
    }

    /**
     * 添加一个LifecycleEvent监听器.
     *
     * @param type 要添加的监听器的ClassName
     * @throws MBeanException 如果添加监听器失败
    */
    public void addLifecycleListener(String type) throws MBeanException{
        LifecycleListener listener = null;
        try {
            listener = (LifecycleListener)Class.forName(type).getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new MBeanException(e);
        }

        if(listener != null){
            try {
                Container container = (Container)getManagedResource();
                container.addLifecycleListener(listener);
            } catch (InstanceNotFoundException e) {
                throw new MBeanException(e);
            } catch (RuntimeOperationsException e) {
                throw new MBeanException(e);
            } catch (InvalidTargetObjectTypeException e) {
                throw new MBeanException(e);
            }
        }
    }

    /**
     * 删除现有的LifecycleEvent监听器.
     *
     * @param type 要删除的监听器的ClassName.
     * 注意，所有已赋予ClassName 的监听器都将被删除.
     * 
     * @throws MBeanException 从管理的资源访问中传播
     */
    public void removeLifecycleListeners(String type) throws MBeanException{
        Container container=null;
        try {
            container = (Container)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        LifecycleListener[] listeners = container.findLifecycleListeners();
        for(LifecycleListener listener: listeners){
            if(listener.getClass().getName().equals(type)){
                container.removeLifecycleListener(listener);
            }
        }
    }


    /**
     * 列出添加到该容器的每个生命周期监听器的类名.
     * 
     * @return 生命周期监听器类名
     * @throws MBeanException 从管理的资源访问中传播
     */
    public String[] findLifecycleListenerNames() throws MBeanException {
        Container container = null;
        List<String> result = new ArrayList<>();

        try {
            container = (Container) getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        LifecycleListener[] listeners = container.findLifecycleListeners();
        for(LifecycleListener listener: listeners){
            result.add(listener.getClass().getName());
        }

        return result.toArray(new String[result.size()]);
    }


    /**
     * 列出添加到此容器的每个容器监听器的类名.
     * 
     * @return 容器监听器类名
     * @throws MBeanException 从管理的资源访问中传播
     */
    public String[] findContainerListenerNames() throws MBeanException {
        Container container = null;
        List<String> result = new ArrayList<>();

        try {
            container = (Container) getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        ContainerListener[] listeners = container.findContainerListeners();
        for(ContainerListener listener: listeners){
            result.add(listener.getClass().getName());
        }

        return result.toArray(new String[result.size()]);
    }
}
