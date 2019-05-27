package org.apache.catalina.storeconfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Bean of a StoreDescription
 *
 * <pre>
 *
 *  &lt;Description
 *  tag=&quot;Context&quot;
 *  standard=&quot;true&quot;
 *  default=&quot;true&quot;
 *  externalAllowed=&quot;true&quot;
 *  storeSeparate=&quot;true&quot;
 *  backup=&quot;true&quot;
 *  children=&quot;true&quot;
 *  tagClass=&quot;org.apache.catalina.core.StandardContext&quot;
 *  storeFactoryClass=&quot;org.apache.catalina.storeconfig.StandardContextSF&quot;
 *  storeAppenderClass=&quot;org.apache.catalina.storeconfig.StoreContextAppender&quot;&gt;
 *     &lt;TransientAttribute&gt;available&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;configFile&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;configured&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;displayName&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;distributable&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;domain&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;engineName&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;name&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;publicId&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;replaceWelcomeFiles&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;saveConfig&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;sessionTimeout&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;startupTime&lt;/TransientAttribute&gt;
 *     &lt;TransientAttribute&gt;tldScanTime&lt;/TransientAttribute&gt;
 *  &lt;/Description&gt;
 *
 *
 * </pre>
 */
public class StoreDescription {

    private String id;

    private String tag;

    private String tagClass;

    private boolean standard = false;

    private boolean backup = false;

    private boolean externalAllowed = false;

    private boolean externalOnly = false;

    private boolean myDefault = false;

    private boolean attributes = true;

    private String storeFactoryClass;

    private IStoreFactory storeFactory;

    private String storeWriterClass;

    private boolean children = false;

    private List<String> transientAttributes;

    private List<String> transientChildren;

    private boolean storeSeparate = false;

    public boolean isExternalAllowed() {
        return externalAllowed;
    }

    public void setExternalAllowed(boolean external) {
        this.externalAllowed = external;
    }

    public boolean isExternalOnly() {
        return externalOnly;
    }

    public void setExternalOnly(boolean external) {
        this.externalOnly = external;
    }

    public boolean isStandard() {
        return standard;
    }

    public void setStandard(boolean standard) {
        this.standard = standard;
    }

    public boolean isBackup() {
        return backup;
    }

    public void setBackup(boolean backup) {
        this.backup = backup;
    }

    public boolean isDefault() {
        return myDefault;
    }

    public void setDefault(boolean aDefault) {
        this.myDefault = aDefault;
    }

    public String getStoreFactoryClass() {
        return storeFactoryClass;
    }

    public void setStoreFactoryClass(String storeFactoryClass) {
        this.storeFactoryClass = storeFactoryClass;
    }

    public IStoreFactory getStoreFactory() {
        return storeFactory;
    }

    public void setStoreFactory(IStoreFactory storeFactory) {
        this.storeFactory = storeFactory;
    }

    public String getStoreWriterClass() {
        return storeWriterClass;
    }

    public void setStoreWriterClass(String storeWriterClass) {
        this.storeWriterClass = storeWriterClass;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getTagClass() {
        return tagClass;
    }

    public void setTagClass(String tagClass) {
        this.tagClass = tagClass;
    }

    public List<String> getTransientAttributes() {
        return transientAttributes;
    }

    public void setTransientAttributes(List<String> transientAttributes) {
        this.transientAttributes = transientAttributes;
    }

    public void addTransientAttribute(String attribute) {
        if (transientAttributes == null)
            transientAttributes = new ArrayList<>();
        transientAttributes.add(attribute);
    }

    public void removeTransientAttribute(String attribute) {
        if (transientAttributes != null)
            transientAttributes.remove(attribute);
    }

    public List<String> getTransientChildren() {
        return transientChildren;
    }

    public void setTransientChildren(List<String> transientChildren) {
        this.transientChildren = transientChildren;
    }

    public void addTransientChild(String classname) {
        if (transientChildren == null)
            transientChildren = new ArrayList<>();
        transientChildren.add(classname);
    }

    public void removeTransientChild(String classname) {
        if (transientChildren != null)
            transientChildren.remove(classname);
    }

    /**
     * 子级不需要序列化, 不要保存这个.
     *
     * @param classname 要检查的类名
     * @return is classname attribute?
     */
    public boolean isTransientChild(String classname) {
        if (transientChildren != null)
            return transientChildren.contains(classname);
        return false;
    }

    /**
     * 属性不需要序列化, 不要保存这个.
     *
     * @param attribute 要检查的属性
     * @return is transient attribute?
     */
    public boolean isTransientAttribute(String attribute) {
        if (transientAttributes != null)
            return transientAttributes.contains(attribute);
        return false;
    }

    /**
     * 返回真正的 id 或 TagClass
     *
     * @return Returns the id.
     */
    public String getId() {
        if (id != null)
            return id;
        else
            return getTagClass();
    }

    /**
     * @param id The id to set.
     */
    public void setId(String id) {
        this.id = id;
    }

    public boolean isAttributes() {
        return attributes;
    }

    public void setAttributes(boolean attributes) {
        this.attributes = attributes;
    }

    /**
     * @return True 如果是分割的store
     */
    public boolean isStoreSeparate() {
        return storeSeparate;
    }

    public void setStoreSeparate(boolean storeSeparate) {
        this.storeSeparate = storeSeparate;
    }

    public boolean isChildren() {
        return children;
    }

    public void setChildren(boolean children) {
        this.children = children;
    }
}
