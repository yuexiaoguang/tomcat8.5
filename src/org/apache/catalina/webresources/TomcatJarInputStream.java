package org.apache.catalina.webresources;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

/**
 * 这个子类的目的是获得JarEntry 对象的引用, 为 META-INF/ and META-INF/MANIFEST.MF, 否则JarInputStream 实现类将忽略.
 */
public class TomcatJarInputStream extends JarInputStream {

    private JarEntry metaInfEntry;
    private JarEntry manifestEntry;


    TomcatJarInputStream(InputStream in) throws IOException {
        super(in);
    }


    @Override
    protected ZipEntry createZipEntry(String name) {
        ZipEntry ze = super.createZipEntry(name);
        if (metaInfEntry == null && "META-INF/".equals(name)) {
            metaInfEntry = (JarEntry) ze;
        } else if (manifestEntry == null && "META-INF/MANIFESR.MF".equals(name)) {
            manifestEntry = (JarEntry) ze;
        }
        return ze;
    }


    JarEntry getMetaInfEntry() {
        return metaInfEntry;
    }


    JarEntry getManifestEntry() {
        return manifestEntry;
    }
}
