package org.apache.catalina.ant;


import org.apache.tools.ant.BuildException;


/**
 * 实现了<code>/sessions</code>命令的Ant任务，由Tomcat管理器应用程序支持.
 */
public class SessionsTask extends AbstractCatalinaCommandTask {


    protected String idle = null;

    public String getIdle() {
        return this.idle;
    }

    public void setIdle(String idle) {
        this.idle = idle;
    }

    @Override
    public StringBuilder createQueryString(String command) {
        StringBuilder buffer = super.createQueryString(command);
        if (path != null && idle != null) {
            buffer.append("&idle=");
            buffer.append(this.idle);
        }
        return buffer;
    }

    /**
     * 执行所请求的操作.
     *
     * @exception BuildException if an error occurs
     */
    @Override
    public void execute() throws BuildException {

        super.execute();
        execute(createQueryString("/sessions").toString());
    }
}
