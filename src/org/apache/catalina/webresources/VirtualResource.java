package org.apache.catalina.webresources;

import org.apache.catalina.WebResourceRoot;

public class VirtualResource extends EmptyResource {

    private final String name;

    public VirtualResource(WebResourceRoot root, String webAppPath,
            String name) {
        super(root, webAppPath);
        this.name = name;
    }

    @Override
    public boolean isVirtual() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public String getName() {
        return name;
    }
}
