package org.apache.catalina.mbeans;

import java.util.ArrayList;

import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.RuntimeOperationsException;

import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.descriptor.web.ContextResourceLink;
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;

/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.catalina.deploy.NamingResourcesImpl</code> component.</p>
 */
public class NamingResourcesMBean extends BaseModelMBean {


    // ----------------------------------------------------------- Constructors


    /**
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception RuntimeOperationsException 如果发生IllegalArgumentException
     */
    public NamingResourcesMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 管理bean的配置信息注册表.
     */
    protected final Registry registry = MBeanUtils.createRegistry();


    /**
     * 描述这个MBean的<code>ManagedBean</code>
     */
    protected final ManagedBean managed =
        registry.findManagedBean("NamingResources");

    // ------------------------------------------------------------- Attributes


    /**
     * 返回此Web应用程序定义的环境条目的MBean名称集合
     */
    public String[] getEnvironments() {
        ContextEnvironment[] envs =
                            ((NamingResourcesImpl)this.resource).findEnvironments();
        ArrayList<String> results = new ArrayList<>();
        for (int i = 0; i < envs.length; i++) {
            try {
                ObjectName oname =
                    MBeanUtils.createObjectName(managed.getDomain(), envs[i]);
                results.add(oname.toString());
            } catch (MalformedObjectNameException e) {
                IllegalArgumentException iae = new IllegalArgumentException
                    ("Cannot create object name for environment " + envs[i]);
                iae.initCause(e);
                throw iae;
            }
        }
        return results.toArray(new String[results.size()]);
    }


    /**
     * 返回此Web应用程序定义的资源引用的MBean名称集合.
     */
    public String[] getResources() {

        ContextResource[] resources =
                            ((NamingResourcesImpl)this.resource).findResources();
        ArrayList<String> results = new ArrayList<>();
        for (int i = 0; i < resources.length; i++) {
            try {
                ObjectName oname =
                    MBeanUtils.createObjectName(managed.getDomain(), resources[i]);
                results.add(oname.toString());
            } catch (MalformedObjectNameException e) {
                IllegalArgumentException iae = new IllegalArgumentException
                    ("Cannot create object name for resource " + resources[i]);
                iae.initCause(e);
                throw iae;
            }
        }
        return results.toArray(new String[results.size()]);
    }


    /**
     * 返回此Web应用程序定义的资源链接的MBean名称集合.
     */
    public String[] getResourceLinks() {

        ContextResourceLink[] resourceLinks =
                            ((NamingResourcesImpl)this.resource).findResourceLinks();
        ArrayList<String> results = new ArrayList<>();
        for (int i = 0; i < resourceLinks.length; i++) {
            try {
                ObjectName oname =
                    MBeanUtils.createObjectName(managed.getDomain(), resourceLinks[i]);
                results.add(oname.toString());
            } catch (MalformedObjectNameException e) {
                IllegalArgumentException iae = new IllegalArgumentException
                    ("Cannot create object name for resource " + resourceLinks[i]);
                iae.initCause(e);
                throw iae;
            }
        }
        return results.toArray(new String[results.size()]);
    }

    // ------------------------------------------------------------- Operations


    /**
     * 为这个Web应用程序添加一个环境条目.
     *
     * @param envName 新环境条目名称
     * @param type 新环境条目的类型
     * @param value 新环境条目的值
     * 
     * @return 新环境条目的对象名称
     * @throws MalformedObjectNameException 如果对象名称无效
     */
    public String addEnvironment(String envName, String type, String value)
        throws MalformedObjectNameException {

        NamingResourcesImpl nresources = (NamingResourcesImpl) this.resource;
        if (nresources == null) {
            return null;
        }
        ContextEnvironment env = nresources.findEnvironment(envName);
        if (env != null) {
            throw new IllegalArgumentException
                ("Invalid environment name - already exists '" + envName + "'");
        }
        env = new ContextEnvironment();
        env.setName(envName);
        env.setType(type);
        env.setValue(value);
        nresources.addEnvironment(env);

        // Return the corresponding MBean name
        ManagedBean managed = registry.findManagedBean("ContextEnvironment");
        ObjectName oname =
            MBeanUtils.createObjectName(managed.getDomain(), env);
        return (oname.toString());

    }


    /**
     * 为这个Web应用程序添加资源引用.
     *
     * @param resourceName 新资源引用名称
     * @param type 新资源引用类型
     * 
     * @return 新资源的对象名称
     * @throws MalformedObjectNameException 如果对象名称无效
     */
    public String addResource(String resourceName, String type)
        throws MalformedObjectNameException {

        NamingResourcesImpl nresources = (NamingResourcesImpl) this.resource;
        if (nresources == null) {
            return null;
        }
        ContextResource resource = nresources.findResource(resourceName);
        if (resource != null) {
            throw new IllegalArgumentException
                ("Invalid resource name - already exists'" + resourceName + "'");
        }
        resource = new ContextResource();
        resource.setName(resourceName);
        resource.setType(type);
        nresources.addResource(resource);

        // Return the corresponding MBean name
        ManagedBean managed = registry.findManagedBean("ContextResource");
        ObjectName oname =
            MBeanUtils.createObjectName(managed.getDomain(), resource);
        return (oname.toString());
    }


    /**
     * 为这个Web应用程序添加一个资源链接引用.
     *
     * @param resourceLinkName 新资源链接引用名称
     * @param type 新资源链接引用类型
     * 
     * @return 新资源链接的对象名称
     * @throws MalformedObjectNameException 如果对象名称无效
     */
    public String addResourceLink(String resourceLinkName, String type)
        throws MalformedObjectNameException {

        NamingResourcesImpl nresources = (NamingResourcesImpl) this.resource;
        if (nresources == null) {
            return null;
        }
        ContextResourceLink resourceLink =
                            nresources.findResourceLink(resourceLinkName);
        if (resourceLink != null) {
            throw new IllegalArgumentException
                ("Invalid resource link name - already exists'" +
                resourceLinkName + "'");
        }
        resourceLink = new ContextResourceLink();
        resourceLink.setName(resourceLinkName);
        resourceLink.setType(type);
        nresources.addResourceLink(resourceLink);

        // Return the corresponding MBean name
        ManagedBean managed = registry.findManagedBean("ContextResourceLink");
        ObjectName oname =
            MBeanUtils.createObjectName(managed.getDomain(), resourceLink);
        return (oname.toString());
    }


    /**
     * 删除指定名称的任何环境条目.
     *
     * @param envName 要删除的环境项的名称
     */
    public void removeEnvironment(String envName) {

        NamingResourcesImpl nresources = (NamingResourcesImpl) this.resource;
        if (nresources == null) {
            return;
        }
        ContextEnvironment env = nresources.findEnvironment(envName);
        if (env == null) {
            throw new IllegalArgumentException
                ("Invalid environment name '" + envName + "'");
        }
        nresources.removeEnvironment(envName);
    }


    /**
     * 删除指定名称的任何资源引用.
     *
     * @param resourceName 要删除的资源引用的名称
     */
    public void removeResource(String resourceName) {

        resourceName = ObjectName.unquote(resourceName);
        NamingResourcesImpl nresources = (NamingResourcesImpl) this.resource;
        if (nresources == null) {
            return;
        }
        ContextResource resource = nresources.findResource(resourceName);
        if (resource == null) {
            throw new IllegalArgumentException
                ("Invalid resource name '" + resourceName + "'");
        }
        nresources.removeResource(resourceName);
    }


    /**
     * 删除指定名称的任何资源链接引用.
     *
     * @param resourceLinkName 要删除的资源链接引用的名称
     */
    public void removeResourceLink(String resourceLinkName) {

        resourceLinkName = ObjectName.unquote(resourceLinkName);
        NamingResourcesImpl nresources = (NamingResourcesImpl) this.resource;
        if (nresources == null) {
            return;
        }
        ContextResourceLink resourceLink =
                            nresources.findResourceLink(resourceLinkName);
        if (resourceLink == null) {
            throw new IllegalArgumentException
                ("Invalid resource Link name '" + resourceLinkName + "'");
        }
        nresources.removeResourceLink(resourceLinkName);
    }

}
