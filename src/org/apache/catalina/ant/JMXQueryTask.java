package org.apache.catalina.ant;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.tools.ant.BuildException;

/**
 * Ant任务，实现了JMX Query 命令(<code>/jmxproxy/?qry</code>).
 */
public class JMXQueryTask extends AbstractCatalinaTask {

    // Properties

    /**
     * JMX 查询字符串
     */
    protected String query      = null;

    // Public Methods

    public String getQuery () {
        return this.query;
    }

    /**
     * 设置 JMX 查询字符串.
    * <p>查询格式示例:</p>
     * <UL>
     * <LI>*:*</LI>
     * <LI>*:type=RequestProcessor,*</LI>
     * <LI>*:j2eeType=Servlet,*</LI>
     * <LI>Catalina:type=Environment,resourcetype=Global,name=simpleValue</LI>
     * </UL>
     * @param query JMX查询字符串
     */
    public void setQuery (String query) {
        this.query = query;
    }

    /**
     * 执行所请求的操作.
     *
     * @exception BuildException 如果发生错误
     */
    @Override
    public void execute() throws BuildException {
        super.execute();
        String queryString;
        if (query == null) {
            queryString = "";
        } else {
            try {
                queryString = "?qry=" + URLEncoder.encode(query, getCharset());
            } catch (UnsupportedEncodingException e) {
                throw new BuildException
                    ("Invalid 'charset' attribute: " + getCharset());
            }
        }
        log("Query string is " + queryString);
        execute ("/jmxproxy/" + queryString);
    }
}
