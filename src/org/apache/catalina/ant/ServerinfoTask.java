package org.apache.catalina.ant;


import org.apache.tools.ant.BuildException;


/**
 * 实现了<code>/serverinfo</code>命令的Ant任务，由Tomcat管理器应用程序支持.
 */
public class ServerinfoTask extends AbstractCatalinaTask {

    // Public Methods

    /**
     * 执行所请求的操作.
     *
     * @exception BuildException if an error occurs
     */
    @Override
    public void execute() throws BuildException {

        super.execute();
        execute("/serverinfo");

    }

}
