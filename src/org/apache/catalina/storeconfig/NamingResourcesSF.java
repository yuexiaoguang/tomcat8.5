package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.descriptor.web.ContextEjb;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextLocalEjb;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.descriptor.web.ContextResourceEnvRef;
import org.apache.tomcat.util.descriptor.web.ContextResourceLink;

/**
 * 在上下文和GlobalNamingResources保存 server.xml Resources元素
 */
public class NamingResourcesSF extends StoreFactoryBase {
    private static Log log = LogFactory.getLog(NamingResourcesSF.class);

    /**
     * 只保存 NamingResources 元素
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement)
            throws Exception {
        StoreDescription elementDesc = getRegistry().findDescription(
                aElement.getClass());
        if (elementDesc != null) {
            if (log.isDebugEnabled())
                log.debug("store " + elementDesc.getTag() + "( " + aElement
                        + " )");
            storeChildren(aWriter, indent, aElement, elementDesc);
        } else {
            if (log.isWarnEnabled())
                log.warn("Descriptor for element" + aElement.getClass()
                        + " not configured!");
        }
    }

    /**
     * 保存指定的 NamingResources 属性.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent 这个元素缩进的空格数量
     * @param aElement 要保存属性的 Object
     * @param elementDesc 元素描述符
     *
     * @exception Exception 保存期间发生异常
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aElement,
            StoreDescription elementDesc) throws Exception {

        if (aElement instanceof NamingResourcesImpl) {
            NamingResourcesImpl resources = (NamingResourcesImpl) aElement;
            // 保存嵌套的 <Ejb> 元素
            ContextEjb[] ejbs = resources.findEjbs();
            storeElementArray(aWriter, indent, ejbs);
            // 保存嵌套的 <Environment> 元素
            ContextEnvironment[] envs = resources.findEnvironments();
            storeElementArray(aWriter, indent, envs);
            // 保存嵌套的 <LocalEjb> 元素
            ContextLocalEjb[] lejbs = resources.findLocalEjbs();
            storeElementArray(aWriter, indent, lejbs);

            // 保存嵌套的 <Resource> 元素
            ContextResource[] dresources = resources.findResources();
            storeElementArray(aWriter, indent, dresources);

            // 保存嵌套的 <ResourceEnvRef> 元素
            ContextResourceEnvRef[] resEnv = resources.findResourceEnvRefs();
            storeElementArray(aWriter, indent, resEnv);

            // 保存嵌套的 <ResourceLink> 元素
            ContextResourceLink[] resourceLinks = resources.findResourceLinks();
            storeElementArray(aWriter, indent, resourceLinks);
        }
    }
}

