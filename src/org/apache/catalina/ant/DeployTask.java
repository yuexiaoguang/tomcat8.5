package org.apache.catalina.ant;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;

/**
 * Ant任务，实现<code>/deploy</code>命令, 由Tomcat管理器支持
 */
public class DeployTask extends AbstractCatalinaCommandTask {

    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("\\w{3,5}\\:");

    /**
     * 此应用程序的上下文配置文件的URL.
     */
    protected String config = null;

    public String getConfig() {
        return this.config;
    }

    public void setConfig(String config) {
        this.config = config;
    }


    /**
     * 要部署的服务器本地Web应用程序归档（WAR）文件的URL.
     */
    protected String localWar = null;

    public String getLocalWar() {
        return this.localWar;
    }

    public void setLocalWar(String localWar) {
        this.localWar = localWar;
    }


    /**
     * 被部署的Web应用的标签
     */
    protected String tag = null;

    public String getTag() {
        return this.tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }


    /**
     * 更新现有的应用程序
     */
    protected boolean update = false;

    public boolean getUpdate() {
        return this.update;
    }

    public void setUpdate(boolean update) {
        this.update = update;
    }


    /**
     * 要部署的Web应用程序归档（WAR）文件的URL.
     */
    protected String war = null;

    public String getWar() {
        return this.war;
    }

    public void setWar(String war) {
        this.war = war;
    }


    /**
     * 执行所请求的操作.
     *
     * @exception BuildException if an error occurs
     */
    @Override
    public void execute() throws BuildException {
        super.execute();
        if (path == null) {
            throw new BuildException("Must specify 'path' attribute");
        }
        if ((war == null) && (localWar == null) && (config == null) && (tag == null)) {
            throw new BuildException(
                            "Must specify either 'war', 'localWar', 'config', or 'tag' attribute");
        }
        // 在上传WAR时建立一个输入流
        BufferedInputStream stream = null;
        String contentType = null;
        long contentLength = -1;
        if (war != null) {
            if (PROTOCOL_PATTERN.matcher(war).lookingAt()) {
                try {
                    URL url = new URL(war);
                    URLConnection conn = url.openConnection();
                    contentLength = conn.getContentLengthLong();
                    stream = new BufferedInputStream(conn.getInputStream(), 1024);
                } catch (IOException e) {
                    throw new BuildException(e);
                }
            } else {
                FileInputStream fsInput = null;
                try {
                    fsInput = new FileInputStream(war);
                    contentLength = fsInput.getChannel().size();
                    stream = new BufferedInputStream(fsInput, 1024);
                } catch (IOException e) {
                    if (fsInput != null) {
                        try {
                            fsInput.close();
                        } catch (IOException ioe) {
                            // Ignore
                        }
                    }
                    throw new BuildException(e);
                }
            }
            contentType = "application/octet-stream";
        }
        // Building URL
        StringBuilder sb = createQueryString("/deploy");
        try {
            if ((war == null) && (config != null)) {
                sb.append("&config=");
                sb.append(URLEncoder.encode(config, getCharset()));
            }
            if ((war == null) && (localWar != null)) {
                sb.append("&war=");
                sb.append(URLEncoder.encode(localWar, getCharset()));
            }
            if (update) {
                sb.append("&update=true");
            }
            if (tag != null) {
                sb.append("&tag=");
                sb.append(URLEncoder.encode(tag, getCharset()));
            }
            execute(sb.toString(), stream, contentType, contentLength);
        } catch (UnsupportedEncodingException e) {
            throw new BuildException("Invalid 'charset' attribute: " + getCharset());
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ioe) {
                    // Ignore
                }
            }
        }
    }
}
