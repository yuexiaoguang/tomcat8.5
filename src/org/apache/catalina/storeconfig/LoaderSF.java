package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.Loader;
import org.apache.catalina.loader.WebappLoader;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Store Loader Element.
 */
public class LoaderSF extends StoreFactoryBase {

    private static Log log = LogFactory.getLog(LoaderSF.class);

    /**
     * 只保存Loader 元素, 当没有默认的时候
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement)
            throws Exception {
        StoreDescription elementDesc = getRegistry().findDescription(
                aElement.getClass());
        if (elementDesc != null) {
            Loader loader = (Loader) aElement;
            if (!isDefaultLoader(loader)) {
                if (log.isDebugEnabled())
                    log.debug("store " + elementDesc.getTag() + "( " + aElement
                            + " )");
                getStoreAppender().printIndent(aWriter, indent + 2);
                getStoreAppender().printTag(aWriter, indent + 2, loader,
                        elementDesc);
            }
        } else {
            if (log.isWarnEnabled()) {
                log
                        .warn("Descriptor for element"
                                + aElement.getClass()
                                + " not configured or element class not StandardManager!");
            }
        }
    }

    /**
     * 是否是默认的<code>Loader</code>配置实例, 使用所有默认的属性?
     *
     * @param loader 要测试的Loader
     * @return <code>true</code>是默认的loader的实例
     */
    protected boolean isDefaultLoader(Loader loader) {

        if (!(loader instanceof WebappLoader)) {
            return false;
        }
        WebappLoader wloader = (WebappLoader) loader;
        if ((wloader.getDelegate() != false)
                || !wloader.getLoaderClass().equals(
                        "org.apache.catalina.loader.WebappClassLoader")) {
            return false;
        }
        return true;
    }
}
