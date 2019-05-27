package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.Engine;
import org.apache.catalina.Executor;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardService;

/**
 * 保存 server.xml Element Service及其所有子级
 */
public class StandardServiceSF extends StoreFactoryBase {

    /**
     * 保存指定的service 元素子级.
     *
     * @param aWriter Current output writer
     * @param indent 缩进级别
     * @param aService 要保存的Service
     * @param parentDesc 元素描述
     * @throws Exception 配置保存错误
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aService,
            StoreDescription parentDesc) throws Exception {
        if (aService instanceof StandardService) {
            StandardService service = (StandardService) aService;
            // 保存嵌套的 <Listener> 元素
            LifecycleListener listeners[] = ((Lifecycle) service)
                    .findLifecycleListeners();
            storeElementArray(aWriter, indent, listeners);

            // 保存嵌套的 <Executor> 元素
            Executor[] executors = service.findExecutors();
            storeElementArray(aWriter, indent, executors);

            Connector connectors[] = service.findConnectors();
            storeElementArray(aWriter, indent, connectors);

            // 保存嵌套的 <Engine> 元素
            Engine container = service.getContainer();
            if (container != null) {
                StoreDescription elementDesc = getRegistry().findDescription(container.getClass());
                if (elementDesc != null) {
                    IStoreFactory factory = elementDesc.getStoreFactory();
                    factory.store(aWriter, indent, container);
                }
            }
        }
    }
}