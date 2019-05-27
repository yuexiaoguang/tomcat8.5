package org.apache.catalina.ant;

import org.apache.tools.ant.BuildException;

/**
 * 实现了<code>/findleaks</code>命令的Ant任务, 由Tomcat管理器应用程序支持.
 */
public class FindLeaksTask extends AbstractCatalinaTask {

    private boolean statusLine = true;

    /**
     * 控制响应是否包含状态行.
     *
     * @param statusLine <code>true</code>如果应该包含状态行
     */
    public void setStatusLine(boolean statusLine) {
        this.statusLine = statusLine;
    }

    /**
     * 控制响应是否包含状态行.
     *
     * @return <code>true</code>如果应该包含状态行,
     *         否则<code>false</code>
     */
    public boolean getStatusLine() {
        return statusLine;
    }


    /**
     * 执行所请求的操作.
     *
     * @exception BuildException if an error occurs
     */
    @Override
    public void execute() throws BuildException {
        super.execute();
        execute("/findleaks?statusLine=" + Boolean.toString(statusLine));
    }
}
