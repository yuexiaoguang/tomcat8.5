package org.apache.catalina.ant;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.tools.ant.BuildException;

public abstract class AbstractCatalinaCommandTask extends AbstractCatalinaTask {

    /**
     * 正在管理的Web应用程序的上下文路径.
     */
    protected String path = null;

    public String getPath() {
        return this.path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    /**
     * 正在管理的Web应用程序的上下文版本.
     */
    protected String version = null;

    public String getVersion() {
        return this.version;
    }

    public void setVersion(String version) {
        this.version = version;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 为指定的命令创建查询字符串.
     *
     * @param command 要执行的命令
     *
     * @return 生成的查询字符串
     *
     * @exception BuildException 如果发生错误
     */
    public StringBuilder createQueryString(String command) throws BuildException {
        StringBuilder buffer = new StringBuilder();

        try {
            buffer.append(command);
            if (path == null) {
                throw new BuildException("Must specify 'path' attribute");
            } else {
                buffer.append("?path=");
                buffer.append(URLEncoder.encode(this.path, getCharset()));
                if (this.version != null) {
                    buffer.append("&version=");
                    buffer.append(URLEncoder.encode(this.version, getCharset()));
                }
            }
        } catch (UnsupportedEncodingException e) {
            throw new BuildException("Invalid 'charset' attribute: " + getCharset());
        }
        return buffer;
    }
}