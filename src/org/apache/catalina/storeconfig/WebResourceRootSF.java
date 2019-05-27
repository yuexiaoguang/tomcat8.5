package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;

/**
 * 生成 Resources 元素
 */
public class WebResourceRootSF extends StoreFactoryBase {

    /**
     * 保存指定的 Resources 子级.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent 这个元素缩进的空格数量
     *
     * @exception Exception 保存发生错误
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aResourceRoot,
            StoreDescription parentDesc) throws Exception {
        if (aResourceRoot instanceof WebResourceRoot) {
            WebResourceRoot resourceRoot = (WebResourceRoot) aResourceRoot;

            // 保存嵌套的 <PreResources> 元素
            WebResourceSet[] preResourcesArray = resourceRoot.getPreResources();
            StoreDescription preResourcesElementDesc = getRegistry().findDescription(
                    WebResourceSet.class.getName()
                            + ".[PreResources]");
            if (preResourcesElementDesc != null) {
                for (WebResourceSet preResources : preResourcesArray) {
                    preResourcesElementDesc.getStoreFactory().store(aWriter, indent,
                            preResources);
                }
            }

            // 保存嵌套的 <JarResources> 元素
            WebResourceSet[] jarResourcesArray = resourceRoot.getJarResources();
            StoreDescription jarResourcesElementDesc = getRegistry().findDescription(
                    WebResourceSet.class.getName()
                            + ".[JarResources]");
            if (jarResourcesElementDesc != null) {
                for (WebResourceSet jarResources : jarResourcesArray) {
                    jarResourcesElementDesc.getStoreFactory().store(aWriter, indent,
                            jarResources);
                }
            }

            // 保存嵌套的 <PostResources> 元素
            WebResourceSet[] postResourcesArray = resourceRoot.getPostResources();
            StoreDescription postResourcesElementDesc = getRegistry().findDescription(
                    WebResourceSet.class.getName()
                            + ".[PostResources]");
            if (postResourcesElementDesc != null) {
                for (WebResourceSet postResources : postResourcesArray) {
                    postResourcesElementDesc.getStoreFactory().store(aWriter, indent,
                            postResources);
                }
            }
        }
    }
}