package org.apache.naming.factory;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

/**
 * 抽象基类，提供子类所需的通用功能. 此类主要用于减少代码重复.
 */
public abstract class FactoryBase implements ObjectFactory {

    /**
     * @param obj 描述要创建的对象的引用对象
     */
    @Override
    public final Object getObjectInstance(Object obj, Name name, Context nameCtx,
            Hashtable<?,?> environment) throws Exception {

        if (isReferenceTypeSupported(obj)) {
            Reference ref = (Reference) obj;

            Object linked = getLinked(ref);
            if (linked != null) {
                return linked;
            }

            ObjectFactory factory = null;
            RefAddr factoryRefAddr = ref.get(Constants.FACTORY);
            if (factoryRefAddr != null) {
                // 使用指定的工厂
                String factoryClassName = factoryRefAddr.getContent().toString();
                // 加载工厂
                ClassLoader tcl = Thread.currentThread().getContextClassLoader();
                Class<?> factoryClass = null;
                try {
                    if (tcl != null) {
                        factoryClass = tcl.loadClass(factoryClassName);
                    } else {
                        factoryClass = Class.forName(factoryClassName);
                    }
                } catch(ClassNotFoundException e) {
                    NamingException ex = new NamingException("Could not load resource factory class");
                    ex.initCause(e);
                    throw ex;
                }
                try {
                    factory = (ObjectFactory) factoryClass.getConstructor().newInstance();
                } catch(Throwable t) {
                    if (t instanceof NamingException) {
                        throw (NamingException) t;
                    }
                    if (t instanceof ThreadDeath) {
                        throw (ThreadDeath) t;
                    }
                    if (t instanceof VirtualMachineError) {
                        throw (VirtualMachineError) t;
                    }
                    NamingException ex = new NamingException(
                            "Could not create resource factory instance");
                    ex.initCause(t);
                    throw ex;
                }
            } else {
                // 检查默认工厂
                factory = getDefaultFactory(ref);
            }

            if (factory != null) {
                return factory.getObjectInstance(obj, name, nameCtx, environment);
            } else {
                throw new NamingException("Cannot create resource instance");
            }
        }

        return null;
    }


    /**
     * 确定此工厂是否支持处理提供的引用对象.
     *
     * @param obj   要处理的对象
     *
     * @return <code>true</code> 如果这个工厂可以处理对象,
     *         否则<code>false</code>
     */
    protected abstract boolean isReferenceTypeSupported(Object obj);

    /**
     * 如果给定引用类型的默认工厂可用，创建默认工厂.
     *
     * @param ref   要处理的引用对象
     *
     * @return  给定引用对象的默认工厂, 或 <code>null</code>如果没有默认工厂.
     *
     * @throws NamingException  如果无法创建默认工厂
     */
    protected abstract ObjectFactory getDefaultFactory(Reference ref)
            throws NamingException;

    /**
     * 如果此引用是指向另一个JNDI对象的链接，获取该对象.
     *
     * @param ref  要处理的引用对象
     *
     * @return  链接的对象或<code>null</code>, 如果此引用对象不支持或未配置链接的对象
     * 
     * @throws NamingException 访问链接对象时出错
     */
    protected abstract Object getLinked(Reference ref) throws NamingException;
}
