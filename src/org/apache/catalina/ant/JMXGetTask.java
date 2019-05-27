package org.apache.catalina.ant;


import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.tools.ant.BuildException;


/**
 * Ant任务，实现 JMX Get 命令(<code>/jmxproxy/?get</code>).
 * 由Tomcat 管理器应用支持.
 */
public class JMXGetTask extends AbstractCatalinaTask {

    // Properties

    /**
     * 完整bean名称
     */
    protected String bean      = null;

    /**
     * 希望更改的属性
     */
    protected String attribute = null;

    // Public Methods

    public String getBean () {
        return this.bean;
    }

    public void setBean (String bean) {
        this.bean = bean;
    }

    public String getAttribute () {
        return this.attribute;
    }

    public void setAttribute (String attribute) {
        this.attribute = attribute;
    }

    /**
     * 执行所请求的操作.
     *
     * @exception BuildException 如果错误发生
     */
    @Override
    public void execute() throws BuildException {
        super.execute();
        if (bean == null || attribute == null) {
            throw new BuildException
                ("Must specify 'bean' and 'attribute' attributes");
        }
        log("Getting attribute " + attribute +
                " in bean " + bean );
        try {
            execute("/jmxproxy/?get=" + URLEncoder.encode(bean, getCharset())
                    + "&att=" + URLEncoder.encode(attribute, getCharset()));
        } catch (UnsupportedEncodingException e) {
            throw new BuildException
                ("Invalid 'charset' attribute: " + getCharset());
        }
    }
}
