package org.apache.catalina.webresources;

import java.util.jar.Manifest;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.util.LifecycleBase;
import org.apache.tomcat.util.res.StringManager;

public abstract class AbstractResourceSet extends LifecycleBase
        implements WebResourceSet {

    private WebResourceRoot root;
    private String base;
    private String internalPath = "";
    private String webAppMount;
    private boolean classLoaderOnly;
    private boolean staticOnly;
    private Manifest manifest;


    protected static final StringManager sm = StringManager.getManager(AbstractResourceSet.class);


    protected final void checkPath(String path) {
        if (path == null || path.length() == 0 || path.charAt(0) != '/') {
            throw new IllegalArgumentException(
                    sm.getString("abstractResourceSet.checkPath", path));
        }
    }

    @Override
    public final void setRoot(WebResourceRoot root) {
        this.root = root;
    }

    protected final WebResourceRoot getRoot() {
        return root;
    }


    protected final String getInternalPath() {
        return internalPath;
    }

    public final void setInternalPath(String internalPath) {
        checkPath(internalPath);
        // Optimise internal processing
        if (internalPath.equals("/")) {
            this.internalPath = "";
        } else {
            this.internalPath = internalPath;
        }
    }

    public final void setWebAppMount(String webAppMount) {
        checkPath(webAppMount);
        // Optimise internal processing
        if (webAppMount.equals("/")) {
            this.webAppMount = "";
        } else {
            this.webAppMount = webAppMount;
        }
    }

    protected final String getWebAppMount() {
        return webAppMount;
    }

    public final void setBase(String base) {
        this.base = base;
    }

    protected final String getBase() {
        return base;
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

    protected final void setManifest(Manifest manifest) {
        this.manifest = manifest;
    }

    protected final Manifest getManifest() {
        return manifest;
    }


    //-------------------------------------------------------- Lifecycle methods
    @Override
    protected final void startInternal() throws LifecycleException {
        setState(LifecycleState.STARTING);
    }

    @Override
    protected final void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
    }

    @Override
    protected final void destroyInternal() throws LifecycleException {
        gc();
    }
}
