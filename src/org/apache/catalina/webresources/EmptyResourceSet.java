package org.apache.catalina.webresources;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Set;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.util.LifecycleBase;

/**
 * 不受文件系统支持，表现为它没有可用的资源.
 * 当Web应用程序完全以编程方式配置并且不使用来自文件系统的任何静态资源时，主要用于嵌入式模式.
 */
public class EmptyResourceSet extends LifecycleBase implements WebResourceSet {

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private WebResourceRoot root;
    private boolean classLoaderOnly;
    private boolean staticOnly;

    public EmptyResourceSet(WebResourceRoot root) {
        this.root = root;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 总是返回 {@link EmptyResource}.
     */
    @Override
    public WebResource getResource(String path) {
        return new EmptyResource(root, path);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 总是返回空数组.
     */
    @Override
    public String[] list(String path) {
        return EMPTY_STRING_ARRAY;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 总是返回空Set.
     */
    @Override
    public Set<String> listWebAppPaths(String path) {
        return Collections.emptySet();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 总是返回 false.
     */
    @Override
    public boolean mkdir(String path) {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 总是返回 false.
     */
    @Override
    public boolean write(String path, InputStream is, boolean overwrite) {
        return false;
    }

    @Override
    public void setRoot(WebResourceRoot root) {
        this.root = root;
    }

    @Override
    public boolean getClassLoaderOnly() {
        return classLoaderOnly;
    }

    @Override
    public void setClassLoaderOnly(boolean classLoaderOnly) {
        this.classLoaderOnly = classLoaderOnly;
    }

    @Override
    public boolean getStaticOnly() {
        return staticOnly;
    }

    @Override
    public void setStaticOnly(boolean staticOnly) {
        this.staticOnly = staticOnly;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 总是返回 null.
     */
    @Override
    public URL getBaseUrl() {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 此方法的调用将被忽略，因为此实现总是只读.
     */
    @Override
    public void setReadOnly(boolean readOnly) {

    }

    /**
     * {@inheritDoc}
     * <p>
     * 总是返回 true.
     */
    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public void gc() {
        // NO-OP
    }

    @Override
    protected void initInternal() throws LifecycleException {
        // NO-OP
    }

    @Override
    protected void startInternal() throws LifecycleException {
        setState(LifecycleState.STARTING);
    }

    @Override
    protected void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
    }

    @Override
    protected void destroyInternal() throws LifecycleException {
        // NO-OP
    }
}
