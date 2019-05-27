package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.core.StandardContext;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class WatchedResourceSF extends StoreFactoryBase {
    private static Log log = LogFactory.getLog(WatchedResourceSF.class);

    /*
     * Store nested Element Value Arrays WatchedResource
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement)
            throws Exception {
        if (aElement instanceof StandardContext) {
            StoreDescription elementDesc = getRegistry().findDescription(
                    aElement.getClass().getName() + ".[WatchedResource]");
            String[] resources = ((StandardContext) aElement)
                    .findWatchedResources();
            if (elementDesc != null) {
                if (log.isDebugEnabled())
                    log.debug("store " + elementDesc.getTag() + "( " + aElement
                            + " )");
                getStoreAppender().printTagArray(aWriter, "WatchedResource",
                        indent, resources);
            }
        } else
            log.warn("Descriptor for element" + aElement.getClass()
                    + ".[WatchedResource] not configured!");
    }
}