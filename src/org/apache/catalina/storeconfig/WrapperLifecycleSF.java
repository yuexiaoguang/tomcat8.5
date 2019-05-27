package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.core.StandardContext;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class WrapperLifecycleSF extends StoreFactoryBase {
    private static Log log = LogFactory.getLog(WrapperLifecycleSF.class);

    /*
     * Store nested Element Value Arrays
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement)
            throws Exception {
        if (aElement instanceof StandardContext) {
            StoreDescription elementDesc = getRegistry().findDescription(
                    aElement.getClass().getName() + ".[WrapperLifecycle]");
            String[] listeners = ((StandardContext) aElement)
                    .findWrapperLifecycles();
            if (elementDesc != null) {
                if (log.isDebugEnabled())
                    log.debug("store " + elementDesc.getTag() + "( " + aElement
                            + " )");
                getStoreAppender().printTagArray(aWriter, "WrapperLifecycle",
                        indent, listeners);
            }
        } else
            log.warn("Descriptor for element" + aElement.getClass()
                    + ".[WrapperLifecycle] not configured!");
    }
}