package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

public interface IStoreFactory {
    StoreAppender getStoreAppender();

    void setStoreAppender(StoreAppender storeWriter);

    void setRegistry(StoreRegistry aRegistry);

    StoreRegistry getRegistry();

    void store(PrintWriter aWriter, int indent, Object aElement)
            throws Exception;

    void storeXMLHead(PrintWriter aWriter);
}