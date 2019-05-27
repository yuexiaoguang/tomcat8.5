package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Service;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.deploy.NamingResourcesImpl;

/**
 * 保存server.xml Server 元素及其子级(Listener,GlobalNamingResource,Service)
 */
public class StandardServerSF extends StoreFactoryBase {

    /**
     * 保存指定的 Server 属性.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent 这个元素要缩进的空格数量
     * @param aServer 要保存的对象
     *
     * @exception Exception 保存期间发生异常
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Object aServer)
            throws Exception {
        storeXMLHead(aWriter);
        super.store(aWriter, indent, aServer);
    }

    /**
     * 保存指定的server元素子级.
     *
     * @param aWriter Current output writer
     * @param indent 缩进级别
     * @param aObject 要保存的Server
     * @param parentDesc 元素的描述
     * @throws Exception 配置保存错误
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aObject,
            StoreDescription parentDesc) throws Exception {
        if (aObject instanceof StandardServer) {
            StandardServer server = (StandardServer) aObject;
            // 保存嵌套的 <Listener> 元素
            LifecycleListener listeners[] = ((Lifecycle) server)
                    .findLifecycleListeners();
            storeElementArray(aWriter, indent, listeners);
            /*LifecycleListener listener = null;
            for (int i = 0; listener == null && i < listeners.length; i++)
                if (listeners[i] instanceof ServerLifecycleListener)
                    listener = listeners[i];
            if (listener != null) {
                StoreDescription elementDesc = getRegistry()
                        .findDescription(
                                StandardServer.class.getName()
                                        + ".[ServerLifecycleListener]");
                if (elementDesc != null) {
                    elementDesc.getStoreFactory().store(aWriter, indent,
                            listener);
                }
            }*/
            // 保存嵌套的 <GlobalNamingResources> 元素
            NamingResourcesImpl globalNamingResources = server
                    .getGlobalNamingResources();
            StoreDescription elementDesc = getRegistry().findDescription(
                    NamingResourcesImpl.class.getName()
                            + ".[GlobalNamingResources]");
            if (elementDesc != null) {
                elementDesc.getStoreFactory().store(aWriter, indent,
                        globalNamingResources);
            }
            // 保存嵌套的 <Service> 元素
            Service services[] = server.findServices();
            storeElementArray(aWriter, indent, services);
        }
    }

}
