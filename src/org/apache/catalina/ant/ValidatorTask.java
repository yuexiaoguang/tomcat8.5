package org.apache.catalina.ant;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.catalina.Globals;
import org.apache.catalina.startup.Constants;
import org.apache.tomcat.util.descriptor.DigesterFactory;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tools.ant.BuildException;
import org.xml.sax.InputSource;


/**
 * 验证Web应用程序部署描述符的任务, 使用 XML 框架验证.
 */
public class ValidatorTask extends BaseRedirectorHelperTask {

    /**
     * webapp目录的路径.
     */
    protected String path = null;

    public String getPath() {
        return (this.path);
    }

    public void setPath(String path) {
        this.path = path;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 执行指定命令. 此逻辑只执行所有子类所需的公共属性验证; 它不直接执行任何功能逻辑.
     *
     * @exception BuildException 如果出现验证错误
     */
    @Override
    public void execute() throws BuildException {

        if (path == null) {
            throw new BuildException("Must specify 'path'");
        }

        File file = new File(path, Constants.ApplicationWebXml);
        if (!file.canRead()) {
            throw new BuildException("Cannot find web.xml");
        }

        // Commons-logging likes having the context classloader set
        ClassLoader oldCL = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader
            (ValidatorTask.class.getClassLoader());

        // Called through trusted manager interface. If running under a
        // SecurityManager assume that untrusted applications may be deployed.
        Digester digester = DigesterFactory.newDigester(
                true, true, null, Globals.IS_SECURITY_ENABLED);
        try (InputStream stream = new BufferedInputStream(new FileInputStream(file.getCanonicalFile()));) {
            InputSource is = new InputSource(file.toURI().toURL().toExternalForm());
            is.setByteStream(stream);
            digester.parse(is);
            handleOutput("web.xml validated");
        } catch (Exception e) {
            if (isFailOnError()) {
                throw new BuildException("Validation failure", e);
            } else {
                handleErrorOutput("Validation failure: " + e);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(oldCL);
            closeRedirector();
        }
    }
}
