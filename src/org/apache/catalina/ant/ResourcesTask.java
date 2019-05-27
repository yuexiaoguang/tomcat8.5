package org.apache.catalina.ant;


import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.tools.ant.BuildException;


/**
 * 实现了<code>/resources</code>命令的Ant任务，由Tomcat管理器应用程序支持.
 */
public class ResourcesTask extends AbstractCatalinaTask {


    // ------------------------------------------------------------- Properties


    /**
     * 请求的资源类型的完全限定类名.
     */
    protected String type = null;

    public String getType() {
        return (this.type);
    }

    public void setType(String type) {
        this.type = type;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 执行所请求的操作.
     *
     * @exception BuildException if an error occurs
     */
    @Override
    public void execute() throws BuildException {

        super.execute();
        if (type != null) {
            try {
                execute("/resources?type=" +
                        URLEncoder.encode(type, getCharset()));
            } catch (UnsupportedEncodingException e) {
                throw new BuildException
                    ("Invalid 'charset' attribute: " + getCharset());
            }
        } else {
            execute("/resources");
        }
    }
}
