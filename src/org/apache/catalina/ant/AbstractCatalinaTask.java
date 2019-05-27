package org.apache.catalina.ant;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

/**
 * Ant任务的Abstract基础类 ，用动态部署和取消部署应用程序与<em>Manager</em>Web应用程序交互
 * 这些任务需要Ant 1.4 or later.
 */
public abstract class AbstractCatalinaTask extends BaseRedirectorHelperTask {

    // ----------------------------------------------------- Instance Variables

    /**
     * 管理器webapp的编码.
     */
    private static final String CHARSET = "utf-8";


    // ------------------------------------------------------------- Properties

    /**
     * 在URL编码的字符集.
     */
    protected String charset = "ISO-8859-1";

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }


    /**
     * <code>Manager</code>应用程序的登录密码
     */
    protected String password = null;

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }


    /**
     * <code>Manager</code>应用的URL
     */
    protected String url = "http://localhost:8080/manager/text";

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }


    /**
     * <code>Manager</code>应用的登录用户名.
     */
    protected String username = null;

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 如果设置为true - 忽略必须是 "OK -"的响应消息的第一行的约束.
     * <p>
     * 如果设置为{@code false} (默认), 服务器响应的第一行预计以 "OK -"开始. 如果它不这样，则任务被认为是失败的，并且第一行被当作错误消息对待.
     * <p>
     * 如果设置为{@code true}, 响应的第一行与其他任何处理一样，不管其文本如何.
     */
    protected boolean ignoreResponseConstraint = false;

    public boolean isIgnoreResponseConstraint() {
        return ignoreResponseConstraint;
    }

    public void setIgnoreResponseConstraint(boolean ignoreResponseConstraint) {
        this.ignoreResponseConstraint = ignoreResponseConstraint;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 执行指定命令.
     * 此逻辑只执行所有子类所需的公共属性验证; 它不直接执行任何功能逻辑.
     *
     * @exception BuildException 验证错误
     */
    @Override
    public void execute() throws BuildException {
        if ((username == null) || (password == null) || (url == null)) {
            throw new BuildException("Must specify all of 'username', 'password', and 'url'");
        }
    }


    /**
     * 根据所配置的属性执行指定的命令..
     *
     * @param command 要执行的命令
     *
     * @exception BuildException if an error occurs
     */
    public void execute(String command) throws BuildException {
        execute(command, null, null, -1);
    }


    /**
     * 根据所配置的属性执行指定的命令
     * 完成任务后，输入流将被关闭，无论它是否成功执行.
     *
     * @param command 执行的命令
     * @param istream InputStream , 包括一个HTTP PUT
     * @param contentType 指定的内容类型
     * @param contentLength 内容长度
     *
     * @exception BuildException if an error occurs
     */
    public void execute(String command, InputStream istream, String contentType, long contentLength)
                    throws BuildException {

        URLConnection conn = null;
        InputStreamReader reader = null;
        try {

            // Create a connection for this command
            conn = (new URL(url + command)).openConnection();
            HttpURLConnection hconn = (HttpURLConnection) conn;

            // Set up standard connection characteristics
            hconn.setAllowUserInteraction(false);
            hconn.setDoInput(true);
            hconn.setUseCaches(false);
            if (istream != null) {
                hconn.setDoOutput(true);
                hconn.setRequestMethod("PUT");
                if (contentType != null) {
                    hconn.setRequestProperty("Content-Type", contentType);
                }
                if (contentLength >= 0) {
                    hconn.setRequestProperty("Content-Length", "" + contentLength);

                    hconn.setFixedLengthStreamingMode(contentLength);
                }
            } else {
                hconn.setDoOutput(false);
                hconn.setRequestMethod("GET");
            }
            hconn.setRequestProperty("User-Agent", "Catalina-Ant-Task/1.0");

            // Set up authorization with our credentials
            Authenticator.setDefault(new TaskAuthenticator(username, password));

            // Establish the connection with the server
            hconn.connect();

            // Send the request data (if any)
            if (istream != null) {
                try (BufferedOutputStream ostream = new BufferedOutputStream(
                                hconn.getOutputStream(), 1024);) {
                    byte buffer[] = new byte[1024];
                    while (true) {
                        int n = istream.read(buffer);
                        if (n < 0) {
                            break;
                        }
                        ostream.write(buffer, 0, n);
                    }
                    ostream.flush();
                } finally {
                    try {
                        istream.close();
                    } catch (Exception e) {
                    }
                }
            }

            // Process the response message
            reader = new InputStreamReader(hconn.getInputStream(), CHARSET);
            StringBuilder buff = new StringBuilder();
            String error = null;
            int msgPriority = Project.MSG_INFO;
            boolean first = true;
            while (true) {
                int ch = reader.read();
                if (ch < 0) {
                    break;
                } else if ((ch == '\r') || (ch == '\n')) {
                	// 如果 \r\n 导致 handleOutput() 被成功调用两次, 第二次用一个空字符串, 产生空行
                    if (buff.length() > 0) {
                        String line = buff.toString();
                        buff.setLength(0);
                        if (!ignoreResponseConstraint && first) {
                            if (!line.startsWith("OK -")) {
                                error = line;
                                msgPriority = Project.MSG_ERR;
                            }
                            first = false;
                        }
                        handleOutput(line, msgPriority);
                    }
                } else {
                    buff.append((char) ch);
                }
            }
            if (buff.length() > 0) {
                handleOutput(buff.toString(), msgPriority);
            }
            if (error != null && isFailOnError()) {
                // exception should be thrown only if failOnError == true
                // or error line will be logged twice
                throw new BuildException(error);
            }
        } catch (Exception e) {
            if (isFailOnError()) {
                throw new BuildException(e);
            } else {
                handleErrorOutput(e.getMessage());
            }
        } finally {
            closeRedirector();
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ioe) {
                    // Ignore
                }
                reader = null;
            }
            if (istream != null) {
                try {
                    istream.close();
                } catch (IOException ioe) {
                    // Ignore
                }
            }
        }
    }


    private static class TaskAuthenticator extends Authenticator {

        private final String user;
        private final String password;

        private TaskAuthenticator(String user, String password) {
            this.user = user;
            this.password = password;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(user, password.toCharArray());
        }
    }
}
