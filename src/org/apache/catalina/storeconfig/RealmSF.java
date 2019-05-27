package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.CredentialHandler;
import org.apache.catalina.Realm;
import org.apache.catalina.realm.CombinedRealm;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Store server.xml Element Realm
 */
public class RealmSF extends StoreFactoryBase {

    private static Log log = LogFactory.getLog(RealmSF.class);

    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement)
            throws Exception {
        if (aElement instanceof CombinedRealm) {
            StoreDescription elementDesc = getRegistry().findDescription(
                    aElement.getClass());

            if (elementDesc != null) {
                if (log.isDebugEnabled())
                    log.debug(sm.getString("factory.storeTag",
                            elementDesc.getTag(), aElement));
                getStoreAppender().printIndent(aWriter, indent + 2);
                getStoreAppender().printOpenTag(aWriter, indent + 2, aElement,
                            elementDesc);
                storeChildren(aWriter, indent + 2, aElement, elementDesc);
                getStoreAppender().printIndent(aWriter, indent + 2);
                getStoreAppender().printCloseTag(aWriter, elementDesc);
            } else {
                if (log.isWarnEnabled())
                    log.warn(sm.getString("factory.storeNoDescriptor",
                            aElement.getClass()));
            }
        } else {
            super.store(aWriter, indent, aElement);
        }
    }

    /**
     * 保存指定的 Realm 属性及其子级 (Realm)
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent 这个元素缩进的空格数量
     * @param aRealm 要保存属性的 Realm
     *
     * @exception Exception 保存期间发生异常
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aRealm,
            StoreDescription parentDesc) throws Exception {
        if (aRealm instanceof CombinedRealm) {
            CombinedRealm combinedRealm = (CombinedRealm) aRealm;

            // 保存嵌套的 <Realm> 元素
            Realm[] realms = combinedRealm.getNestedRealms();
            storeElementArray(aWriter, indent, realms);
        }
        // 保存嵌套的  <CredentialHandler> 元素
        CredentialHandler credentialHandler = ((Realm) aRealm).getCredentialHandler();
        if (credentialHandler != null) {
            storeElement(aWriter, indent, credentialHandler);
        }
    }

}