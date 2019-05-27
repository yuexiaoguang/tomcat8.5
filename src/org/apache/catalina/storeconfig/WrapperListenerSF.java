package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.core.StandardContext;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class WrapperListenerSF extends StoreFactoryBase {
    private static Log log = LogFactory.getLog(WrapperListenerSF.class);

    /*
     * Store nested Element Value Arrays
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement)
            throws Exception {
        if (aElement instanceof StandardContext) {
            StoreDescription elementDesc = getRegistry().findDescription(
                    aElement.getClass().getName() + ".[WrapperListener]");
            String[] listeners = ((StandardContext) aElement)
                    .findWrapperListeners();
            if (elementDesc != null) {
                if (log.isDebugEnabled())
                    log.debug("store " + elementDesc.getTag() + "( " + aElement
                            + " )");
                getStoreAppender().printTagArray(aWriter, "WrapperListener",
                        indent, listeners);
            }
        } else
            log.warn("Descriptor for element" + aElement.getClass()
                    + ".[WrapperListener] not configured!");
    }
}