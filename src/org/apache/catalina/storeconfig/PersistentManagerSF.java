package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.SessionIdGenerator;
import org.apache.catalina.Store;
import org.apache.catalina.session.PersistentManager;

/**
 * 保存使用嵌套的"Store"的 server.xml PersistentManager 元素
 */
public class PersistentManagerSF extends StoreFactoryBase {

    /**
     * 保存指定的 PersistentManager 属性.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent 这个元素缩进的空格数量
     * @param aManager 要保存属性的PersistentManager
     *
     * @exception Exception 保存期间发生异常
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aManager,
            StoreDescription parentDesc) throws Exception {
        if (aManager instanceof PersistentManager) {
            PersistentManager manager = (PersistentManager) aManager;

            // 保存嵌套的 <Store> 元素
            Store store = manager.getStore();
            storeElement(aWriter, indent, store);

            // 保存嵌套的 <SessionIdGenerator> 元素
            SessionIdGenerator sessionIdGenerator = manager.getSessionIdGenerator();
            if (sessionIdGenerator != null) {
                storeElement(aWriter, indent, sessionIdGenerator);
            }

        }
    }

}