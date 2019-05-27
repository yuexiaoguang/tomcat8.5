package org.apache.catalina.storeconfig;

import java.io.IOException;
import java.io.PrintWriter;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * StoreFactory 保存特殊的元素. 输出使用 StoreAppenders生成.
 */
public class StoreFactoryBase implements IStoreFactory {
    private static Log log = LogFactory.getLog(StoreFactoryBase.class);

    private StoreRegistry registry;

    private StoreAppender storeAppender = new StoreAppender();

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager
            .getManager(Constants.Package);

    /**
     * 实现类的描述信息.
     */
    private static final String info = "org.apache.catalina.config.StoreFactoryBase/1.0";

    /**
     * @return 实现类的描述信息.
     */
    public String getInfo() {
        return (info);
    }

    @Override
    public StoreAppender getStoreAppender() {
        return storeAppender;
    }

    @Override
    public void setStoreAppender(StoreAppender storeAppender) {
        this.storeAppender = storeAppender;
    }

    @Override
    public void setRegistry(StoreRegistry aRegistry) {
        registry = aRegistry;
    }

    @Override
    public StoreRegistry getRegistry() {
        return registry;
    }

    @Override
    public void storeXMLHead(PrintWriter aWriter) {
        // 保存元素的开始
        aWriter.print("<?xml version=\"1.0\" encoding=\"");
        aWriter.print(getRegistry().getEncoding());
        aWriter.println("\"?>");
    }

    /**
     * 使用属性和子级保存一个 server.xml 元素
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement)
            throws Exception {

        StoreDescription elementDesc = getRegistry().findDescription(
                aElement.getClass());

        if (elementDesc != null) {
            if (log.isDebugEnabled())
                log.debug(sm.getString("factory.storeTag",
                        elementDesc.getTag(), aElement));
            getStoreAppender().printIndent(aWriter, indent + 2);
            if (!elementDesc.isChildren()) {
                getStoreAppender().printTag(aWriter, indent, aElement,
                        elementDesc);
            } else {
                getStoreAppender().printOpenTag(aWriter, indent + 2, aElement,
                        elementDesc);
                storeChildren(aWriter, indent + 2, aElement, elementDesc);
                getStoreAppender().printIndent(aWriter, indent + 2);
                getStoreAppender().printCloseTag(aWriter, elementDesc);
            }
        } else
            log.warn(sm.getString("factory.storeNoDescriptor", aElement
                    .getClass()));
    }

    /**
     * 必须在子类实现，用于自定义保存子级.
     *
     * @param aWriter Current output writer
     * @param indent 缩进级别
     * @param aElement 当前元素
     * @param elementDesc 元素描述
     * @throws Exception 保存配置错误
     */
    public void storeChildren(PrintWriter aWriter, int indent, Object aElement,
            StoreDescription elementDesc) throws Exception {
    }

    /**
     * 只保存来自storeChildren方法，需要序列化的元素.
     *
     * @param aWriter Current output writer
     * @param indent 缩进级别
     * @param aTagElement 当前元素
     * @throws Exception Configuration storing error
     */
    protected void storeElement(PrintWriter aWriter, int indent,
            Object aTagElement) throws Exception {
        if (aTagElement != null) {
            IStoreFactory elementFactory = getRegistry().findStoreFactory(
                    aTagElement.getClass());

            if (elementFactory != null) {
                StoreDescription desc = getRegistry().findDescription(
                        aTagElement.getClass());
                if (!desc.isTransientChild(aTagElement.getClass().getName()))
                    elementFactory.store(aWriter, indent, aTagElement);
            } else {
                log.warn(sm.getString("factory.storeNoDescriptor", aTagElement
                        .getClass()));
            }
        }
    }

    /**
     * 保存一组元素.
     * @param aWriter Current output writer
     * @param indent 缩进级别
     * @param elements 元素数组
     * @throws Exception Configuration storing error
     */
    protected void storeElementArray(PrintWriter aWriter, int indent,
            Object[] elements) throws Exception {
        if (elements != null) {
            for (int i = 0; i < elements.length; i++) {
                try {
                    storeElement(aWriter, indent, elements[i]);
                } catch (IOException ioe) {
                    // ignore children report error them self!
                    // see StandardContext.storeWithBackup()
                }
            }
        }
    }
}