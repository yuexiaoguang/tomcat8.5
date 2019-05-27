package org.apache.catalina.valves;


import java.io.IOException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Properties;

import javax.servlet.ServletException;

import org.apache.catalina.AccessLog;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * <p>
 * 此Tomcat扩展将服务器访问直接记录到数据库中, 可以用来代替常规的基于文件的访问日志（AccessLogValve）.
 * 复制到server/classes Tomcat安装目录, 并在server.xml中配置:
 * </p>
 * <pre>
 *      &lt;Valve className="org.apache.catalina.valves.JDBCAccessLogValve"
 *          driverName="<i>your_jdbc_driver</i>"
 *          connectionURL="<i>your_jdbc_url</i>"
 *          pattern="combined" resolveHosts="false"
 *      /&gt;
 * </pre>
 * <p>
 * 可以配置许多参数, 比如数据库连接(使用<code>driverName</code>和<code>connectionURL</code>),
 * 表名(<code>tableName</code>)和字段名(对应于get/set方法名).
 * 作为AccessLogValve相同选项的支持, 例如<code>resolveHosts</code>和<code>pattern</code> ("common" 或"combined").
 * </p>
 * <p>
 * 当Tomcat启动时, 一个数据库连接(autoReconnect选项)创建并用于所有日志活动. 当Tomcat关闭时, 数据库连接关闭.
 * 此记录器可用于Engine上下文的级别(被所有定义的主机共享)或者Host上下文级别 (每个主机都有一个记录器实例, 可能使用不同的数据库).
 * </p>
 * <p>
 * 可以用以下命令创建数据库表:
 * </p>
 * <pre>
 * CREATE TABLE access (
 * id INT UNSIGNED AUTO_INCREMENT NOT NULL,
 * remoteHost CHAR(15) NOT NULL,
 * userName CHAR(15),
 * timestamp TIMESTAMP NOT NULL,
 * virtualHost VARCHAR(64) NOT NULL,
 * method VARCHAR(8) NOT NULL,
 * query VARCHAR(255) NOT NULL,
 * status SMALLINT UNSIGNED NOT NULL,
 * bytes INT UNSIGNED NOT NULL,
 * referer VARCHAR(128),
 * userAgent VARCHAR(128),
 * PRIMARY KEY (id),
 * INDEX (timestamp),
 * INDEX (remoteHost),
 * INDEX (virtualHost),
 * INDEX (query),
 * INDEX (userAgent)
 * );
 * </pre>
 * <p>设置JDBCAccessLogValve 属性 useLongContentLength="true", 如果你有多于 4GB 输出.
 * 在access.bytes属性使用 long SQL datatype.
 * bytes的数据类型在 oracle是<i>number</i>, 其它数据库使用<i>bytes BIGINT NOT NULL</i>.
 * </p>
 *
 * <p>
 * 如果表是如上所创建的, 它的名称和字段名不需要定义.
 * </p>
 * <p>
 * 如果请求方法是"common", 只有这些字段被使用:
 * <code>remoteHost, user, timeStamp, query, status, bytes</code>
 * </p>
 * <p>
 * <i>TO DO: 提供排除某些MIME类型日志记录的选项.</i>
 * </p>
 */
public final class JDBCAccessLogValve extends ValveBase implements AccessLog {

    // ----------------------------------------------------------- Constructors


    /**
     * 用默认值初始化字段.
     * 默认是:
     * <pre>
     *      driverName = null;
     *      connectionURL = null;
     *      tableName = "access";
     *      remoteHostField = "remoteHost";
     *      userField = "userName";
     *      timestampField = "timestamp";
     *      virtualHostField = "virtualHost";
     *      methodField = "method";
     *      queryField = "query";
     *      statusField = "status";
     *      bytesField = "bytes";
     *      refererField = "referer";
     *      userAgentField = "userAgent";
     *      pattern = "common";
     *      resolveHosts = false;
     * </pre>
     */
    public JDBCAccessLogValve() {
        super(true);
        driverName = null;
        connectionURL = null;
        tableName = "access";
        remoteHostField = "remoteHost";
        userField = "userName";
        timestampField = "timestamp";
        virtualHostField = "virtualHost";
        methodField = "method";
        queryField = "query";
        statusField = "status";
        bytesField = "bytes";
        refererField = "referer";
        userAgentField = "userAgent";
        pattern = "common";
        resolveHosts = false;
        conn = null;
        ps = null;
        currentTimeMillis = new java.util.Date().getTime();
    }


    // ----------------------------------------------------- Instance Variables

   /**
    * 使用long contentLength, 如果有多于 4 GB 输出.
    */
    boolean useLongContentLength = false;

   /**
     * 试图连接数据库时使用的连接用户名.
     */
    String connectionName = null;


    /**
     * 连接到数据库时要使用的连接密码.
     */
    String connectionPassword = null;

   /**
     * 连接到数据库时要使用的JDBC Driver类实例.
     */
    Driver driver = null;


    private String driverName;
    private String connectionURL;
    private String tableName;
    private String remoteHostField;
    private String userField;
    private String timestampField;
    private String virtualHostField;
    private String methodField;
    private String queryField;
    private String statusField;
    private String bytesField;
    private String refererField;
    private String userAgentField;
    private String pattern;
    private boolean resolveHosts;


    private Connection conn;
    private PreparedStatement ps;


    private long currentTimeMillis;

    /**
     * 这个阀门是否应该为请求使用的IP地址、主机名、协议和端口设置请求属性.
     * 默认是<code>true</code>.
     */
    boolean requestAttributesEnabled = true;


    // ------------------------------------------------------------- Properties

    /**
     * 默认是<code>true</code>.
     */
    @Override
    public void setRequestAttributesEnabled(boolean requestAttributesEnabled) {
        this.requestAttributesEnabled = requestAttributesEnabled;
    }

    @Override
    public boolean getRequestAttributesEnabled() {
        return requestAttributesEnabled;
    }

    /**
     * @return 连接数据库时使用的连接用户名.
     */
    public String getConnectionName() {
        return connectionName;
    }

    /**
     * 连接数据库时使用的连接用户名.
     *
     * @param connectionName Username
     */
    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    /**
     * 设置数据库驱动程序名称
     * 
     * @param driverName 数据库驱动程序类的完整名称
     */
    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    /**
     * @return 连接到数据库时要使用的连接密码.
     */
    public String getConnectionPassword() {
        return connectionPassword;
    }

    /**
     * 连接到数据库时要使用的连接密码.
     *
     * @param connectionPassword User password
     */
    public void setConnectionPassword(String connectionPassword) {
        this.connectionPassword = connectionPassword;
    }

    /**
     * 为存储日志的数据库设置JDBC URL.
     *
     * @param connectionURL The JDBC URL of the database.
     */
    public void setConnectionURL(String connectionURL) {
        this.connectionURL = connectionURL;
    }


    /**
     * 设置存储日志的表的名称.
     *
     * @param tableName The name of the table.
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }


    /**
     * 设置包含远程主机的字段的名称.
     *
     * @param remoteHostField The name of the remote host field.
     */
    public void setRemoteHostField(String remoteHostField) {
        this.remoteHostField = remoteHostField;
    }


    /**
     * 设置包含远程用户名的字段的名称.
     *
     * @param userField The name of the remote user field.
     */
    public void setUserField(String userField) {
        this.userField = userField;
    }


    /**
     * 设置包含服务器确定的时间戳的字段的名称
     * 
     * @param timestampField 服务器确定的时间戳字段的名称
     */
    public void setTimestampField(String timestampField) {
        this.timestampField = timestampField;
    }


    /**
     * 设置包含虚拟主机信息的字段的名称 (这实际上是服务器名).
     * 
     * @param virtualHostField 虚拟主机字段的名称
     */
    public void setVirtualHostField(String virtualHostField) {
        this.virtualHostField = virtualHostField;
    }


    /**
     * 设置包含HTTP请求方法的字段的名称
     * 
     * @param methodField HTTP请求方法字段的名称
     */
    public void setMethodField(String methodField) {
        this.methodField = methodField;
    }


    /**
     * 设置包含HTTP查询URL部分的字段的名称
     * 
     * @param queryField 包含HTTP查询URL部分的字段的名称
     */
    public void setQueryField(String queryField) {
        this.queryField = queryField;
    }


    /**
     * 设置包含HTTP响应状态码的字段的名称
     * 
     * @param statusField HTTP响应状态码字段的名称
     */
    public void setStatusField(String statusField) {
        this.statusField = statusField;
    }


    /**
     * 设置包含返回字节数的字段的名称
     * 
     * @param bytesField 返回字节字段的名称
     */
    public void setBytesField(String bytesField) {
        this.bytesField = bytesField;
    }


    /**
     * 设置包含Referer的字段名称.
     * 
     * @param refererField Referer字段名称
     */
    public void setRefererField(String refererField) {
        this.refererField = refererField;
    }


    /**
     * 设置包含用户代理的字段的名称
     * 
     * @param userAgentField 用户代理字段的名称
     */
    public void setUserAgentField(String userAgentField) {
        this.userAgentField = userAgentField;
    }


    /**
     * 设置日志模式. 支持的模式对应于基于文件的"common" 和 "combined". 这些转换成包含两组字段的表的使用
     * <P><I>TO DO: 更灵活的领域选择.</I></P>
     * 
     * @param pattern 日志模式的名称
     */
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }


    /**
     * 确定IP主机名称解析是否完成.
     *
     * @param resolveHosts "true" or "false", 是否需要host IP
     */
    public void setResolveHosts(String resolveHosts) {
        this.resolveHosts = Boolean.parseBoolean(resolveHosts);
    }

    /**
     * @return <code>true</code>如果内容长度应该被认为是long而不是int, 默认是<code>false</code>
     */
    public boolean getUseLongContentLength() {
        return this.useLongContentLength;
    }

    /**
     * @param useLongContentLength 要设置的useLongContentLength
     */
    public void setUseLongContentLength(boolean useLongContentLength) {
        this.useLongContentLength = useLongContentLength;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * 此方法在每个查询中由Tomcat调用.
     *
     * @param request The Request object.
     * @param response The Response object.
     *
     * @exception IOException Should not be thrown.
     * @exception ServletException Database SQLException is wrapped
     * in a ServletException.
     */
    @Override
    public void invoke(Request request, Response response) throws IOException,
            ServletException {
        getNext().invoke(request, response);
    }


    @Override
    public void log(Request request, Response response, long time) {
        if (!getState().isAvailable()) {
            return;
        }

        final String EMPTY = "" ;

        String remoteHost;
        if(resolveHosts) {
            if (requestAttributesEnabled) {
                Object host = request.getAttribute(REMOTE_HOST_ATTRIBUTE);
                if (host == null) {
                    remoteHost = request.getRemoteHost();
                } else {
                    remoteHost = (String) host;
                }
            } else {
                remoteHost = request.getRemoteHost();
            }
        } else {
            if (requestAttributesEnabled) {
                Object addr = request.getAttribute(REMOTE_ADDR_ATTRIBUTE);
                if (addr == null) {
                    remoteHost = request.getRemoteAddr();
                } else {
                    remoteHost = (String) addr;
                }
            } else {
                remoteHost = request.getRemoteAddr();
            }
        }
        String user = request.getRemoteUser();
        String query=request.getRequestURI();

        long bytes = response.getBytesWritten(true);
        if(bytes < 0) {
            bytes = 0;
        }
        int status = response.getStatus();
        String virtualHost = EMPTY;
        String method = EMPTY;
        String referer = EMPTY;
        String userAgent = EMPTY;
        String logPattern = pattern;
        if (logPattern.equals("combined")) {
            virtualHost = request.getServerName();
            method = request.getMethod();
            referer = request.getHeader("referer");
            userAgent = request.getHeader("user-agent");
        }
        synchronized (this) {
          int numberOfTries = 2;
          while (numberOfTries>0) {
            try {
                open();

                ps.setString(1, remoteHost);
                ps.setString(2, user);
                ps.setTimestamp(3, new Timestamp(getCurrentTimeMillis()));
                ps.setString(4, query);
                ps.setInt(5, status);

                if(useLongContentLength) {
                    ps.setLong(6, bytes);
                } else {
                    if (bytes > Integer.MAX_VALUE) {
                        bytes = -1 ;
                    }
                    ps.setInt(6, (int) bytes);
                }
                if (logPattern.equals("combined")) {
                      ps.setString(7, virtualHost);
                      ps.setString(8, method);
                      ps.setString(9, referer);
                      ps.setString(10, userAgent);
                }
                ps.executeUpdate();
                return;
              } catch (SQLException e) {
                // Log the problem for posterity
                  container.getLogger().error(sm.getString("jdbcAccessLogValve.exception"), e);

                // Close the connection so that it gets reopened next time
                if (conn != null) {
                    close();
                }
              }
              numberOfTries--;
           }
        }

    }


    /**
     * 使用这个AccessLogValve打开并返回一个数据库连接.
     *
     * @exception SQLException if a database error occurs
     */
    protected void open() throws SQLException {

        // Do nothing if there is a database connection already open
        if (conn != null) {
            return ;
        }

        // 初始化数据库驱动
        if (driver == null) {
            try {
                Class<?> clazz = Class.forName(driverName);
                driver = (Driver) clazz.getConstructor().newInstance();
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                throw new SQLException(e.getMessage(), e);
            }
        }

        // Open a new connection
        Properties props = new Properties();
        if (connectionName != null) {
            props.put("user", connectionName);
        }
        if (connectionPassword != null) {
            props.put("password", connectionPassword);
        }
        conn = driver.connect(connectionURL, props);
        conn.setAutoCommit(true);
        String logPattern = pattern;
        if (logPattern.equals("common")) {
                ps = conn.prepareStatement
                    ("INSERT INTO " + tableName + " ("
                     + remoteHostField + ", " + userField + ", "
                     + timestampField +", " + queryField + ", "
                     + statusField + ", " + bytesField
                     + ") VALUES(?, ?, ?, ?, ?, ?)");
        } else if (logPattern.equals("combined")) {
                ps = conn.prepareStatement
                    ("INSERT INTO " + tableName + " ("
                     + remoteHostField + ", " + userField + ", "
                     + timestampField + ", " + queryField + ", "
                     + statusField + ", " + bytesField + ", "
                     + virtualHostField + ", " + methodField + ", "
                     + refererField + ", " + userAgentField
                     + ") VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        }
    }

    /**
     * 关闭指定的数据库连接.
     */
    protected void close() {

        // Do nothing if the database connection is already closed
        if (conn == null) {
            return;
        }

        // Close our prepared statements (if any)
        try {
            ps.close();
        } catch (Throwable f) {
            ExceptionUtils.handleThrowable(f);
        }
        this.ps = null;



        // 关闭此数据库连接，并记录任何错误
        try {
            conn.close();
        } catch (SQLException e) {
            container.getLogger().error(sm.getString("jdbcAccessLogValve.close"), e); // Just log it here
        } finally {
           this.conn = null;
        }

    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        try {
            open() ;
        } catch (SQLException e) {
            throw new LifecycleException(e);
        }

        setState(LifecycleState.STARTING);
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);

        close() ;
    }


    public long getCurrentTimeMillis() {
        long systime  =  System.currentTimeMillis();
        if ((systime - currentTimeMillis) > 1000) {
            currentTimeMillis  =  new java.util.Date(systime).getTime();
        }
        return currentTimeMillis;
    }
}
