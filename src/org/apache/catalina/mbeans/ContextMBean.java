package org.apache.catalina.mbeans;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;

import org.apache.catalina.Context;
import org.apache.tomcat.util.descriptor.web.ApplicationParameter;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;

public class ContextMBean extends ContainerMBean {

    public ContextMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }

     /**
     * 返回这个应用的应用参数.
     * 
     * @throws MBeanException 从管理的资源访问中传播
     */
    public String[] findApplicationParameters() throws MBeanException {

        Context context;
        try {
            context = (Context)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        ApplicationParameter[] params = context.findApplicationParameters();
        String[] stringParams = new String[params.length];
        for(int counter=0; counter < params.length; counter++){
           stringParams[counter]=params[counter].toString();
        }

        return stringParams;
    }

    /**
     * 返回此Web应用程序的安全约束. 如果没有, 返回零长度数组.
     * 
     * @throws MBeanException 从管理的资源访问中传播
     */
    public String[] findConstraints() throws MBeanException {

        Context context;
        try {
            context = (Context)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        SecurityConstraint[] constraints = context.findConstraints();
        String[] stringConstraints = new String[constraints.length];
        for(int counter=0; counter < constraints.length; counter++){
            stringConstraints[counter]=constraints[counter].toString();
        }

        return stringConstraints;
    }

    /**
     * 返回指定HTTP错误码的错误页条目; 否则返回<code>null</code>.
     *
     * @param errorCode 要查找的错误码
     * 
     * @throws MBeanException从管理的资源访问中传播
     */
    public String findErrorPage(int errorCode) throws MBeanException {

        Context context;
        try {
            context = (Context)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        return context.findErrorPage(errorCode).toString();

    }

    /**
     * 返回指定的Java异常类型的错误页面条目; 否则返回<code>null</code>.
     *
     * @param exceptionType 要查找的异常类型
     * 
     * @throws MBeanException 从管理的资源访问中传播
     */
    public String findErrorPage(String exceptionType) throws MBeanException {

        Context context;
        try {
            context = (Context)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        return context.findErrorPage(exceptionType).toString();

    }

    /**
     * 返回所有指定的错误码和异常类型定义的错误页面.
     * 
     * @throws MBeanException 从管理的资源访问中传播
     */
    public String[] findErrorPages() throws MBeanException {

        Context context;
        try {
            context = (Context)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        ErrorPage[] pages = context.findErrorPages();
        String[] stringPages = new String[pages.length];
        for(int counter=0; counter < pages.length; counter++){
            stringPages[counter]=pages[counter].toString();
        }

        return stringPages;

    }

    /**
     * 返回指定名称的过滤器定义; 否则返回<code>null</code>.
     *
     * @param name 要查找的过滤器名称
     * 
     * @throws MBeanException 从管理的资源访问中传播
     */
    public String findFilterDef(String name) throws MBeanException {

        Context context;
        try {
            context = (Context)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        FilterDef filterDef = context.findFilterDef(name);
        return filterDef.toString();

    }

    /**
     * 返回此上下文定义的过滤器集合.
     * 
     * @throws MBeanException 从管理的资源访问中传播
     */
    public String[] findFilterDefs() throws MBeanException {

        Context context;
        try {
            context = (Context)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        FilterDef[] filterDefs = context.findFilterDefs();
        String[] stringFilters = new String[filterDefs.length];
        for(int counter=0; counter < filterDefs.length; counter++){
            stringFilters[counter]=filterDefs[counter].toString();
        }

        return stringFilters;
    }

    /**
     * 返回此上下文的过滤器映射.
     * 
     * @throws MBeanException 从管理的资源访问中传播
     */
    public String[] findFilterMaps() throws MBeanException {

        Context context;
        try {
            context = (Context)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        FilterMap[] maps = context.findFilterMaps();
        String[] stringMaps = new String[maps.length];
        for(int counter=0; counter < maps.length; counter++){
            stringMaps[counter]=maps[counter].toString();
        }

        return stringMaps;
    }
}
