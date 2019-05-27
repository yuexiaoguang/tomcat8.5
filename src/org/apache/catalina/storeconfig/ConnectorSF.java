package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.LifecycleListener;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.UpgradeProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;

/**
 * 存储 Connector 和 Listener
 */
public class ConnectorSF extends StoreFactoryBase {

    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aConnector,
            StoreDescription parentDesc) throws Exception {

        if (aConnector instanceof Connector) {
            Connector connector = (Connector) aConnector;
            // 存储嵌套的 <Listener> 元素
            LifecycleListener listeners[] = connector.findLifecycleListeners();
            storeElementArray(aWriter, indent, listeners);
            // 存储嵌套的 <UpgradeProtocol> 元素
            UpgradeProtocol[] upgradeProtocols = connector.findUpgradeProtocols();
            storeElementArray(aWriter, indent, upgradeProtocols);
            // 存储嵌套的 <SSLHostConfig> 元素
            SSLHostConfig[] hostConfigs = connector.findSslHostConfigs();
            storeElementArray(aWriter, indent, hostConfigs);
        }
    }

    protected void printOpenTag(PrintWriter aWriter, int indent, Object bean,
            StoreDescription aDesc) throws Exception {
        aWriter.print("<");
        aWriter.print(aDesc.getTag());
        storeConnectorAttributes(aWriter, indent, bean, aDesc);
        aWriter.println(">");
    }

    protected void storeConnectorAttributes(PrintWriter aWriter, int indent,
            Object bean, StoreDescription aDesc) throws Exception {
        if (aDesc.isAttributes()) {
            getStoreAppender().printAttributes(aWriter, indent, false, bean,
                    aDesc);
        }
    }

    protected void printTag(PrintWriter aWriter, int indent, Object bean,
            StoreDescription aDesc) throws Exception {
        aWriter.print("<");
        aWriter.print(aDesc.getTag());
        storeConnectorAttributes(aWriter, indent, bean, aDesc);
        aWriter.println("/>");
    }

}