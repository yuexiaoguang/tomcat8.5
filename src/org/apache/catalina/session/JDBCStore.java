package org.apache.catalina.session;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.catalina.Container;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Session;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * {@link org.apache.catalina.Store Store}接口实现类，在数据库中存储序列化的会话对象.
 * 保存的会话仍将基于不活动而过期.
 */
public class JDBCStore extends StoreBase {

    /**
     * 上下文名称
     */
    private String name = null;

    /**
     * 注册名称, 用于记录日志.
     */
    protected static final String storeName = "JDBCStore";

    /**
     * 后台线程注册的名称.
     */
    protected static final String threadName = "JDBCStore";

    /**
     * 连接数据库时使用的连接用户名.
     */
    protected String connectionName = null;


    /**
     * 连接到数据库时要使用的密码.
     */
    protected String connectionPassword = null;

    /**
     * 连接到数据库时要使用的连接URL.
     */
    protected String connectionURL = null;

    /**
     * 数据库连接.
     */
    private Connection dbConnection = null;

    /**
     * 作为连接工厂使用的JDBC驱动程序类的实例.
     */
    protected Driver driver = null;

    /**
     * 使用的驱动类名.
     */
    protected String driverName = null;

    /**
     * JNDI资源名称
     */
    protected String dataSourceName = null;

    /**
     * 上下文局部数据源.
     */
    private boolean localDataSource = false;

    /**
     * DataSource to use
     */
    protected DataSource dataSource = null;


    // ------------------------------------------------------------ Table & cols

    /**
     * 使用的表.
     */
    protected String sessionTable = "tomcat$sessions";

    /**
     * /Engine/Host/Context 名称的列
     */
    protected String sessionAppCol = "app";

    /**
     * Id 列.
     */
    protected String sessionIdCol = "id";

    /**
     * Data 列.
     */
    protected String sessionDataCol = "data";

    /**
     * Valid 列
     */
    protected String sessionValidCol = "valid";

    /**
     * 最大闲置数列.
     */
    protected String sessionMaxInactiveCol = "maxinactive";

    /**
     * 最后访问列.
     */
    protected String sessionLastAccessedCol = "lastaccess";


    // ----------------------------------------------------------- SQL Variables

    /**
     * 变量来保存<code>getSize()</code> prepared statement.
     */
    protected PreparedStatement preparedSizeSql = null;

    /**
     * 变量来保存<code>save()</code> prepared statement.
     */
    protected PreparedStatement preparedSaveSql = null;

    /**
     * 变量来保存<code>clear()</code> prepared statement.
     */
    protected PreparedStatement preparedClearSql = null;

    /**
     * 变量来保存<code>remove()</code> prepared statement.
     */
    protected PreparedStatement preparedRemoveSql = null;

    /**
     * 变量来保存<code>load()</code> prepared statement.
     */
    protected PreparedStatement preparedLoadSql = null;


    // -------------------------------------------------------------- Properties

    /**
     * @return 此实例的名称(从容器名称创建的)
     */
    public String getName() {
        if (name == null) {
            Container container = manager.getContext();
            String contextName = container.getName();
            if (!contextName.startsWith("/")) {
                contextName = "/" + contextName;
            }
            String hostName = "";
            String engineName = "";

            if (container.getParent() != null) {
                Container host = container.getParent();
                hostName = host.getName();
                if (host.getParent() != null) {
                    engineName = host.getParent().getName();
                }
            }
            name = "/" + engineName + "/" + hostName + contextName;
        }
        return name;
    }

    /**
     * @return 线程名称.
     */
    public String getThreadName() {
        return threadName;
    }

    /**
     * @return 这个Store的名称, 用于记录日志.
     */
    @Override
    public String getStoreName() {
        return storeName;
    }

    /**
     * 设置这个Store的驱动.
     *
     * @param driverName The new driver
     */
    public void setDriverName(String driverName) {
        String oldDriverName = this.driverName;
        this.driverName = driverName;
        support.firePropertyChange("driverName",
                oldDriverName,
                this.driverName);
        this.driverName = driverName;
    }

    /**
     * @return 这个Store的驱动类名.
     */
    public String getDriverName() {
        return driverName;
    }

    /**
     * @return 用于连接数据库的用户名.
     */
    public String getConnectionName() {
        return connectionName;
    }

    /**
     * 设置用于连接数据库的用户名.
     *
     * @param connectionName Username
     */
    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    /**
     * @return 用于连接数据库的密码.
     */
    public String getConnectionPassword() {
        return connectionPassword;
    }

    /**
     * 设置用于连接数据库的密码.
     *
     * @param connectionPassword User password
     */
    public void setConnectionPassword(String connectionPassword) {
        this.connectionPassword = connectionPassword;
    }

    /**
     * 设置连接 URL.
     *
     * @param connectionURL The new Connection URL
     */
    public void setConnectionURL(String connectionURL) {
        String oldConnString = this.connectionURL;
        this.connectionURL = connectionURL;
        support.firePropertyChange("connectionURL",
                oldConnString,
                this.connectionURL);
    }

    /**
     * @return 连接 URL.
     */
    public String getConnectionURL() {
        return connectionURL;
    }

    /**
     * Set the table for this Store.
     *
     * @param sessionTable The new table
     */
    public void setSessionTable(String sessionTable) {
        String oldSessionTable = this.sessionTable;
        this.sessionTable = sessionTable;
        support.firePropertyChange("sessionTable",
                oldSessionTable,
                this.sessionTable);
    }

    /**
     * @return the table for this Store.
     */
    public String getSessionTable() {
        return sessionTable;
    }

    /**
     * Set the App column for the table.
     *
     * @param sessionAppCol the column name
     */
    public void setSessionAppCol(String sessionAppCol) {
        String oldSessionAppCol = this.sessionAppCol;
        this.sessionAppCol = sessionAppCol;
        support.firePropertyChange("sessionAppCol",
                oldSessionAppCol,
                this.sessionAppCol);
    }

    /**
     * @return the web application name column for the table.
     */
    public String getSessionAppCol() {
        return this.sessionAppCol;
    }

    /**
     * Set the Id column for the table.
     *
     * @param sessionIdCol the column name
     */
    public void setSessionIdCol(String sessionIdCol) {
        String oldSessionIdCol = this.sessionIdCol;
        this.sessionIdCol = sessionIdCol;
        support.firePropertyChange("sessionIdCol",
                oldSessionIdCol,
                this.sessionIdCol);
    }

    /**
     * @return the Id column for the table.
     */
    public String getSessionIdCol() {
        return this.sessionIdCol;
    }

    /**
     * Set the Data column for the table
     *
     * @param sessionDataCol the column name
     */
    public void setSessionDataCol(String sessionDataCol) {
        String oldSessionDataCol = this.sessionDataCol;
        this.sessionDataCol = sessionDataCol;
        support.firePropertyChange("sessionDataCol",
                oldSessionDataCol,
                this.sessionDataCol);
    }

    /**
     * @return the data column for the table
     */
    public String getSessionDataCol() {
        return this.sessionDataCol;
    }

    /**
     * Set the {@code Is Valid} column for the table
     *
     * @param sessionValidCol The column name
     */
    public void setSessionValidCol(String sessionValidCol) {
        String oldSessionValidCol = this.sessionValidCol;
        this.sessionValidCol = sessionValidCol;
        support.firePropertyChange("sessionValidCol",
                oldSessionValidCol,
                this.sessionValidCol);
    }

    /**
     * @return the {@code Is Valid} column
     */
    public String getSessionValidCol() {
        return this.sessionValidCol;
    }

    /**
     * Set the {@code Max Inactive} column for the table
     *
     * @param sessionMaxInactiveCol The column name
     */
    public void setSessionMaxInactiveCol(String sessionMaxInactiveCol) {
        String oldSessionMaxInactiveCol = this.sessionMaxInactiveCol;
        this.sessionMaxInactiveCol = sessionMaxInactiveCol;
        support.firePropertyChange("sessionMaxInactiveCol",
                oldSessionMaxInactiveCol,
                this.sessionMaxInactiveCol);
    }

    /**
     * @return the {@code Max Inactive} column
     */
    public String getSessionMaxInactiveCol() {
        return this.sessionMaxInactiveCol;
    }

    /**
     * Set the {@code Last Accessed} column for the table
     *
     * @param sessionLastAccessedCol The column name
     */
    public void setSessionLastAccessedCol(String sessionLastAccessedCol) {
        String oldSessionLastAccessedCol = this.sessionLastAccessedCol;
        this.sessionLastAccessedCol = sessionLastAccessedCol;
        support.firePropertyChange("sessionLastAccessedCol",
                oldSessionLastAccessedCol,
                this.sessionLastAccessedCol);
    }

    /**
     * @return the {@code Last Accessed} column
     */
    public String getSessionLastAccessedCol() {
        return this.sessionLastAccessedCol;
    }

    /**
     * 设置用于DB访问的数据源工厂的JNDI名称
     *
     * @param dataSourceName 数据源工厂的JNDI名称
     */
    public void setDataSourceName(String dataSourceName) {
        if (dataSourceName == null || "".equals(dataSourceName.trim())) {
            manager.getContext().getLogger().warn(
                    sm.getString(getStoreName() + ".missingDataSourceName"));
            return;
        }
        this.dataSourceName = dataSourceName;
    }

    /**
     * @return JNDI数据源工厂名称
     */
    public String getDataSourceName() {
        return this.dataSourceName;
    }

    /**
     * @return 如果数据源将在WebAPP JNDI上下文中查找.
     */
    public boolean getLocalDataSource() {
        return localDataSource;
    }

    /**
     * 设置为{@code true}, 在WebAPP JNDI上下文中查找数据源.
     *
     * @param localDataSource the new flag value
     */
    public void setLocalDataSource(boolean localDataSource) {
      this.localDataSource = localDataSource;
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public String[] expiredKeys() throws IOException {
        return keys(true);
    }

    @Override
    public String[] keys() throws IOException {
        return keys(false);
    }

    /**
     * 返回当前保存的所有会话的会话标识符.
     * 如果没有, 返回零长度数组.
     *
     * @param expiredOnly 是否仅返回过期会话的密钥
     * 
     * @return array containing the list of session IDs
     *
     * @exception IOException if an input/output error occurred
     */
    private String[] keys(boolean expiredOnly) throws IOException {
        String keys[] = null;
        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {

                Connection _conn = getConnection();
                if (_conn == null) {
                    return new String[0];
                }
                try {

                    String keysSql = "SELECT " + sessionIdCol + " FROM "
                            + sessionTable + " WHERE " + sessionAppCol + " = ?";
                    if (expiredOnly) {
                        keysSql += " AND (" + sessionLastAccessedCol + " + "
                                + sessionMaxInactiveCol + " * 1000 < ?)";
                    }
                    try (PreparedStatement preparedKeysSql = _conn.prepareStatement(keysSql)) {
                        preparedKeysSql.setString(1, getName());
                        if (expiredOnly) {
                            preparedKeysSql.setLong(2, System.currentTimeMillis());
                        }
                        try (ResultSet rst = preparedKeysSql.executeQuery()) {
                            ArrayList<String> tmpkeys = new ArrayList<>();
                            if (rst != null) {
                                while (rst.next()) {
                                    tmpkeys.add(rst.getString(1));
                                }
                            }
                            keys = tmpkeys.toArray(new String[tmpkeys.size()]);
                            // Break out after the finally block
                            numberOfTries = 0;
                        }
                    }
                } catch (SQLException e) {
                    manager.getContext().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    keys = new String[0];
                    // 关闭连接，以便下次重新打开
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    release(_conn);
                }
                numberOfTries--;
            }
        }
        return keys;
    }

    /**
     * 返回当前保存的会话数量. 
     * 如果没有,返回<code>0</code>.
     *
     * @return 当前保存在该Store中的所有会话的计数
     *
     * @exception IOException if an input/output error occurred
     */
    @Override
    public int getSize() throws IOException {
        int size = 0;

        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();

                if (_conn == null) {
                    return size;
                }

                try {
                    if (preparedSizeSql == null) {
                        String sizeSql = "SELECT COUNT(" + sessionIdCol
                                + ") FROM " + sessionTable + " WHERE "
                                + sessionAppCol + " = ?";
                        preparedSizeSql = _conn.prepareStatement(sizeSql);
                    }

                    preparedSizeSql.setString(1, getName());
                    try (ResultSet rst = preparedSizeSql.executeQuery()) {
                        if (rst.next()) {
                            size = rst.getInt(1);
                        }
                        // Break out after the finally block
                        numberOfTries = 0;
                    }
                } catch (SQLException e) {
                    manager.getContext().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    release(_conn);
                }
                numberOfTries--;
            }
        }
        return size;
    }

    /**
     * 加载指定ID关联的Session.
     * 如果没有，返回 <code>null</code>.
     *
     * @param id a value of type <code>String</code>
     * 
     * @return the stored <code>Session</code>
     * @exception ClassNotFoundException if an error occurs
     * @exception IOException if an input/output error occurred
     */
    @Override
    public Session load(String id) throws ClassNotFoundException, IOException {
        StandardSession _session = null;
        org.apache.catalina.Context context = getManager().getContext();
        Log contextLog = context.getLogger();

        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();
                if (_conn == null) {
                    return null;
                }

                ClassLoader oldThreadContextCL = context.bind(Globals.IS_SECURITY_ENABLED, null);

                try {
                    if (preparedLoadSql == null) {
                        String loadSql = "SELECT " + sessionIdCol + ", "
                                + sessionDataCol + " FROM " + sessionTable
                                + " WHERE " + sessionIdCol + " = ? AND "
                                + sessionAppCol + " = ?";
                        preparedLoadSql = _conn.prepareStatement(loadSql);
                    }

                    preparedLoadSql.setString(1, id);
                    preparedLoadSql.setString(2, getName());
                    try (ResultSet rst = preparedLoadSql.executeQuery()) {
                        if (rst.next()) {
                            try (ObjectInputStream ois =
                                    getObjectInputStream(rst.getBinaryStream(2))) {
                                if (contextLog.isDebugEnabled()) {
                                    contextLog.debug(sm.getString(
                                            getStoreName() + ".loading", id, sessionTable));
                                }

                                _session = (StandardSession) manager.createEmptySession();
                                _session.readObjectData(ois);
                                _session.setManager(manager);
                            }
                        } else if (context.getLogger().isDebugEnabled()) {
                            contextLog.debug(getStoreName() + ": No persisted data object found");
                        }
                        // Break out after the finally block
                        numberOfTries = 0;
                    }
                } catch (SQLException e) {
                    contextLog.error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    context.unbind(Globals.IS_SECURITY_ENABLED, oldThreadContextCL);
                    release(_conn);
                }
                numberOfTries--;
            }
        }

        return _session;
    }

    /**
     * 移除指定ID的Session.
     * 如果没有, 什么都不做.
     *
     * @param id Session identifier of the Session to be removed
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void remove(String id) throws IOException {

        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();

                if (_conn == null) {
                    return;
                }

                try {
                    remove(id, _conn);
                    // Break out after the finally block
                    numberOfTries = 0;
                } catch (SQLException e) {
                    manager.getContext().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    release(_conn);
                }
                numberOfTries--;
            }
        }

        if (manager.getContext().getLogger().isDebugEnabled()) {
            manager.getContext().getLogger().debug(sm.getString(getStoreName() + ".removing", id, sessionTable));
        }
    }

    /**
     * 从指定的会话标识符中删除会话. 如果不存在这样的会话, 此方法不采取任何行动.
     *
     * @param id 要删除的会话的会话标识符
     * @param _conn 要使用的打开连接
     * 
     * @throws SQLException 如果在与数据库交互时发生错误
     */
    private void remove(String id, Connection _conn) throws SQLException {
        if (preparedRemoveSql == null) {
            String removeSql = "DELETE FROM " + sessionTable
                    + " WHERE " + sessionIdCol + " = ?  AND "
                    + sessionAppCol + " = ?";
            preparedRemoveSql = _conn.prepareStatement(removeSql);
        }

        preparedRemoveSql.setString(1, id);
        preparedRemoveSql.setString(2, getName());
        preparedRemoveSql.execute();
    }

    /**
     * 删除所有的Sessions.
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void clear() throws IOException {

        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();
                if (_conn == null) {
                    return;
                }

                try {
                    if (preparedClearSql == null) {
                        String clearSql = "DELETE FROM " + sessionTable
                             + " WHERE " + sessionAppCol + " = ?";
                        preparedClearSql = _conn.prepareStatement(clearSql);
                    }

                    preparedClearSql.setString(1, getName());
                    preparedClearSql.execute();
                    // Break out after the finally block
                    numberOfTries = 0;
                } catch (SQLException e) {
                    manager.getContext().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    release(_conn);
                }
                numberOfTries--;
            }
        }
    }

    /**
     * 保存一个session.
     *
     * @param session the session to be stored
     * 
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void save(Session session) throws IOException {
        ByteArrayOutputStream bos = null;

        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();
                if (_conn == null) {
                    return;
                }

                try {
                	// 如果db中已经存在，再次删除和插入
                    // TODO:
                    // * 检查数据库中是否存在这个ID，如果有，使用 UPDATE.
                    remove(session.getIdInternal(), _conn);

                    bos = new ByteArrayOutputStream();
                    try (ObjectOutputStream oos =
                            new ObjectOutputStream(new BufferedOutputStream(bos))) {
                        ((StandardSession) session).writeObjectData(oos);
                    }
                    byte[] obs = bos.toByteArray();
                    int size = obs.length;
                    try (ByteArrayInputStream bis = new ByteArrayInputStream(obs, 0, size);
                            InputStream in = new BufferedInputStream(bis, size)) {
                        if (preparedSaveSql == null) {
                            String saveSql = "INSERT INTO " + sessionTable + " ("
                               + sessionIdCol + ", " + sessionAppCol + ", "
                               + sessionDataCol + ", " + sessionValidCol
                               + ", " + sessionMaxInactiveCol + ", "
                               + sessionLastAccessedCol
                               + ") VALUES (?, ?, ?, ?, ?, ?)";
                           preparedSaveSql = _conn.prepareStatement(saveSql);
                        }

                        preparedSaveSql.setString(1, session.getIdInternal());
                        preparedSaveSql.setString(2, getName());
                        preparedSaveSql.setBinaryStream(3, in, size);
                        preparedSaveSql.setString(4, session.isValid() ? "1" : "0");
                        preparedSaveSql.setInt(5, session.getMaxInactiveInterval());
                        preparedSaveSql.setLong(6, session.getLastAccessedTime());
                        preparedSaveSql.execute();
                        // Break out after the finally block
                        numberOfTries = 0;
                    }
                } catch (SQLException e) {
                    manager.getContext().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } catch (IOException e) {
                    // Ignore
                } finally {
                    release(_conn);
                }
                numberOfTries--;
            }
        }

        if (manager.getContext().getLogger().isDebugEnabled()) {
            manager.getContext().getLogger().debug(sm.getString(getStoreName() + ".saving",
                    session.getIdInternal(), sessionTable));
        }
    }


    // --------------------------------------------------------- Protected Methods

    /**
     * 检查连接, 如果是<code>null</code>或已经关闭，重新打开它.
     * 返回<code>null</code>，如果无法建立连接.
     *
     * @return <code>Connection</code> if the connection succeeded
     */
    protected Connection getConnection() {
        Connection conn = null;
        try {
            conn = open();
            if (conn == null || conn.isClosed()) {
                manager.getContext().getLogger().info(sm.getString(getStoreName() + ".checkConnectionDBClosed"));
                conn = open();
                if (conn == null || conn.isClosed()) {
                    manager.getContext().getLogger().info(sm.getString(getStoreName() + ".checkConnectionDBReOpenFail"));
                }
            }
        } catch (SQLException ex) {
            manager.getContext().getLogger().error(sm.getString(getStoreName() + ".checkConnectionSQLException",
                    ex.toString()));
        }

        return conn;
    }

    /**
     * 打开（如果有必要）并返回数据库连接
     *
     * @return database connection ready to use
     *
     * @exception SQLException if a database error occurs
     */
    protected Connection open() throws SQLException {

        // Do nothing if there is a database connection already open
        if (dbConnection != null)
            return dbConnection;

        if (dataSourceName != null && dataSource == null) {
            org.apache.catalina.Context context = getManager().getContext();
            ClassLoader oldThreadContextCL = null;
            if (localDataSource) {
                oldThreadContextCL = context.bind(Globals.IS_SECURITY_ENABLED, null);
            }

            Context initCtx;
            try {
                initCtx = new InitialContext();
                Context envCtx = (Context) initCtx.lookup("java:comp/env");
                this.dataSource = (DataSource) envCtx.lookup(this.dataSourceName);
            } catch (NamingException e) {
                context.getLogger().error(
                        sm.getString(getStoreName() + ".wrongDataSource",
                                this.dataSourceName), e);
            } finally {
                if (localDataSource) {
                    context.unbind(Globals.IS_SECURITY_ENABLED, oldThreadContextCL);
                }
            }
        }

        if (dataSource != null) {
            return dataSource.getConnection();
        }

        // Instantiate our database driver if necessary
        if (driver == null) {
            try {
                Class<?> clazz = Class.forName(driverName);
                driver = (Driver) clazz.getConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                manager.getContext().getLogger().error(
                        sm.getString(getStoreName() + ".checkConnectionClassNotFoundException",
                        e.toString()));
                throw new SQLException(e);
            }
        }

        // Open a new connection
        Properties props = new Properties();
        if (connectionName != null)
            props.put("user", connectionName);
        if (connectionPassword != null)
            props.put("password", connectionPassword);
        dbConnection = driver.connect(connectionURL, props);
        dbConnection.setAutoCommit(true);
        return dbConnection;

    }

    /**
     * 关闭指定的数据库连接.
     *
     * @param dbConnection The connection to be closed
     */
    protected void close(Connection dbConnection) {

        // Do nothing if the database connection is already closed
        if (dbConnection == null)
            return;

        // Close our prepared statements (if any)
        try {
            preparedSizeSql.close();
        } catch (Throwable f) {
            ExceptionUtils.handleThrowable(f);
        }
        this.preparedSizeSql = null;

        try {
            preparedSaveSql.close();
        } catch (Throwable f) {
            ExceptionUtils.handleThrowable(f);
        }
        this.preparedSaveSql = null;

        try {
            preparedClearSql.close();
        } catch (Throwable f) {
            ExceptionUtils.handleThrowable(f);
        }

        try {
            preparedRemoveSql.close();
        } catch (Throwable f) {
            ExceptionUtils.handleThrowable(f);
        }
        this.preparedRemoveSql = null;

        try {
            preparedLoadSql.close();
        } catch (Throwable f) {
            ExceptionUtils.handleThrowable(f);
        }
        this.preparedLoadSql = null;

        // Commit if autoCommit is false
        try {
            if (!dbConnection.getAutoCommit()) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            manager.getContext().getLogger().error(sm.getString(getStoreName() + ".commitSQLException"), e);
        }

        // 关闭此数据库连接, 并记录错误
        try {
            dbConnection.close();
        } catch (SQLException e) {
            manager.getContext().getLogger().error(sm.getString(getStoreName() + ".close", e.toString())); // Just log it here
        } finally {
            this.dbConnection = null;
        }

    }

    /**
     * 释放连接, 如果它与连接池关联.
     *
     * @param conn 要释放的连接
     */
    protected void release(Connection conn) {
        if (dataSource != null) {
            close(conn);
        }
    }

    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        if (dataSourceName == null) {
            // 如果不使用连接池, 打开与数据库的连接
            this.dbConnection = getConnection();
        }

        super.startInternal();
    }

    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        super.stopInternal();

        // 关闭并释放与数据库相关的所有内容.
        if (dbConnection != null) {
            try {
                dbConnection.commit();
            } catch (SQLException e) {
                // Ignore
            }
            close(dbConnection);
        }
    }
}
