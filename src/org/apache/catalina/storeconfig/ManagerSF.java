package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.Manager;
import org.apache.catalina.SessionIdGenerator;
import org.apache.catalina.session.StandardManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 保存 server.xml Manager 元素
 */
public class ManagerSF extends StoreFactoryBase {

    private static Log log = LogFactory.getLog(ManagerSF.class);

    /**
     * 只保存Manager 元素
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement)
            throws Exception {
        StoreDescription elementDesc = getRegistry().findDescription(
                aElement.getClass());
        if (elementDesc != null) {
            if (aElement instanceof StandardManager) {
                StandardManager manager = (StandardManager) aElement;
                if (!isDefaultManager(manager)) {
                    if (log.isDebugEnabled())
                        log.debug(sm.getString("factory.storeTag", elementDesc
                                .getTag(), aElement));
                    super.store(aWriter, indent, aElement);
                }
            } else {
                super.store(aWriter, indent, aElement);
            }
        } else {
            if (log.isWarnEnabled())
                log.warn(sm.getString("factory.storeNoDescriptor", aElement
                        .getClass()));
        }
    }

    /**
     * 是否是默认的<code>Manager</code> 实例, 使用所有默认的属性?
     *
     * @param smanager 要测试的Manager
     * @return <code>true</code>是否是默认的manager实例
     */
    protected boolean isDefaultManager(StandardManager smanager) {

        if (!"SESSIONS.ser".equals(smanager.getPathname())
                || (smanager.getMaxActiveSessions() != -1)) {
            return false;
        }
        return true;

    }

    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aManager,
            StoreDescription parentDesc) throws Exception {
        if (aManager instanceof Manager) {
            Manager manager = (Manager) aManager;
            // 保存嵌套的 <SessionIdGenerator> 元素;
            SessionIdGenerator sessionIdGenerator = manager.getSessionIdGenerator();
            if (sessionIdGenerator != null) {
                storeElement(aWriter, indent, sessionIdGenerator);
            }
        }
    }

}
