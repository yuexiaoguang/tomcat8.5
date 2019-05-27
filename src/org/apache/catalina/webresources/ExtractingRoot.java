package org.apache.catalina.webresources;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletContext;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.startup.ExpandWar;
import org.apache.catalina.util.IOTools;
import org.apache.tomcat.util.res.StringManager;

/**
 * 如果将主要资源打包为WAR文件，则任何JAR将被提取到工作目录并从那里使用.
 */
public class ExtractingRoot extends StandardRoot {

    private static final StringManager sm = StringManager.getManager(ExtractingRoot.class);

    private static final String APPLICATION_JARS_DIR = "application-jars";

    @Override
    protected void processWebInfLib() throws LifecycleException {

        // 除非将应用程序部署为打包的WAR文件，否则不提取JAR文件.
        if (!super.isPackedWarFile()) {
            super.processWebInfLib();
            return;
        }

        File expansionTarget = getExpansionTarget();
        if (!expansionTarget.isDirectory()) {
            if (!expansionTarget.mkdirs()) {
                throw new LifecycleException(
                        sm.getString("extractingRoot.targetFailed", expansionTarget));
            }
        }

        WebResource[] possibleJars = listResources("/WEB-INF/lib", false);

        for (WebResource possibleJar : possibleJars) {
            if (possibleJar.isFile() && possibleJar.getName().endsWith(".jar")) {
                try {
                    File dest = new File(expansionTarget, possibleJar.getName());
                    dest = dest.getCanonicalFile();
                    try (InputStream sourceStream = possibleJar.getInputStream();
                            OutputStream destStream= new FileOutputStream(dest)) {
                        IOTools.flow(sourceStream, destStream);
                    }

                    createWebResourceSet(ResourceSetType.CLASSES_JAR,
                            "/WEB-INF/classes", dest.toURI().toURL(), "/");
                } catch (IOException ioe) {
                    throw new LifecycleException(
                            sm.getString("extractingRoot.jarFailed", possibleJar.getName()), ioe);
                }
            }
        }
    }

    private File getExpansionTarget() {
        File tmpDir = (File) getContext().getServletContext().getAttribute(ServletContext.TEMPDIR);
        File expansionTarget = new File(tmpDir, APPLICATION_JARS_DIR);
        return expansionTarget;
    }


    @Override
    protected boolean isPackedWarFile() {
        return false;
    }


    @Override
    protected void stopInternal() throws LifecycleException {
        super.stopInternal();

        if (super.isPackedWarFile()) {
            // 从工作目录中删除提取的JAR
            File expansionTarget = getExpansionTarget();
            ExpandWar.delete(expansionTarget);
        }
    }
}
