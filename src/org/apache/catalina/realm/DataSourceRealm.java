package org.apache.catalina.realm;


import java.security.Principal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.naming.Context;
import javax.sql.DataSource;

import org.apache.catalina.LifecycleException;
import org.apache.naming.ContextBindings;

/**
* <b>Realm</b>实现类，使用JDBC JNDI 数据源.
* 查看 JDBCRealm.howto，知道怎样设置数据源和配置项.
*/
public class DataSourceRealm extends RealmBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * 为角色PreparedStatement生成的字符串
     */
    private String preparedRoles = null;


    /**
     * 为凭据PreparedStatement生成的字符串
     */
    private String preparedCredentials = null;


    /**
     * JNDI JDBC数据源名称
     */
    protected String dataSourceName = null;


    /**
     * 上下文本地数据源.
     */
    protected boolean localDataSource = false;


    /**
     * 关于这个Realm实现类的描述信息.
     * @deprecated This will be removed in Tomcat 9 onwards.
     */
    @Deprecated
    protected static final String name = "DataSourceRealm";


    /**
     * 用户角色表中的列
     */
    protected String roleNameCol = null;


    /**
     * 保存用户凭据的用户表中的列
     */
    protected String userCredCol = null;


    /**
     * 保存用户名称的用户表中的列
     */
    protected String userNameCol = null;


    /**
     * 保存用户和角色之间关系的表
     */
    protected String userRoleTable = null;


    /**
     * 保存用户数据的表.
     */
    protected String userTable = null;


    /**
     * 最后连接尝试.
     */
    private volatile boolean connectionSuccess = true;


    // ------------------------------------------------------------- Properties


    /**
     * @return JNDI JDBC数据源的名称.
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * 设置JNDI JDBC数据源的名称.
     *
     * @param dataSourceName JNDI JDBC数据源的名称
     */
    public void setDataSourceName( String dataSourceName) {
      this.dataSourceName = dataSourceName;
    }

    /**
     * @return 如果数据源将在WebAPP JNDI上下文中查找.
     */
    public boolean getLocalDataSource() {
        return localDataSource;
    }

    /**
     * 设置为 true 在webapp JNDI上下文中查找数据源.
     *
     * @param localDataSource the new flag value
     */
    public void setLocalDataSource(boolean localDataSource) {
      this.localDataSource = localDataSource;
    }

    /**
     * @return 用户角色表中指定角色的列.
     */
    public String getRoleNameCol() {
        return roleNameCol;
    }

    /**
     * 设置名为role的用户角色表中的列.
     *
     * @param roleNameCol 列名
     */
    public void setRoleNameCol( String roleNameCol ) {
        this.roleNameCol = roleNameCol;
    }

    /**
     * @return 保存用户凭据的用户表中的列.
     */
    public String getUserCredCol() {
        return userCredCol;
    }

    /**
     * 设置包含用户凭据的用户表中的列.
     *
     * @param userCredCol 列名
     */
    public void setUserCredCol( String userCredCol ) {
       this.userCredCol = userCredCol;
    }

    /**
     * @return 保存用户名称的用户表中的列.
     */
    public String getUserNameCol() {
        return userNameCol;
    }

    /**
     * 设置包含用户名称的用户表中的列.
     *
     * @param userNameCol 列名
     */
    public void setUserNameCol( String userNameCol ) {
       this.userNameCol = userNameCol;
    }

    /**
     * @return 保存用户和角色之间关系的表.
     */
    public String getUserRoleTable() {
        return userRoleTable;
    }

    /**
     * 设置保存用户和角色之间关系的表.
     *
     * @param userRoleTable 表名
     */
    public void setUserRoleTable( String userRoleTable ) {
        this.userRoleTable = userRoleTable;
    }

    /**
     * @return 保存用户数据的表.
     */
    public String getUserTable() {
        return userTable;
    }

    /**
     * 设置保存用户数据的表.
     *
     * @param userTable 表名
     */
    public void setUserTable( String userTable ) {
      this.userTable = userTable;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 返回指定用户名和凭据的Principal; 或者<code>null</code>.
     *
     * 如果JDBC连接有任何错误, 执行查询或返回null的任何操作 (不验证).
     * 此事件也被记录, 连接将被关闭，以便随后的请求将自动重新打开它.
     *
     * @param username 要查找的Principal的用户名
     * @param credentials 验证用户名时使用的密码或其他凭据
     * 
     * @return 关联的主体, 或<code>null</code>.
     */
    @Override
    public Principal authenticate(String username, String credentials) {

        // No user or no credentials
        // Can't possibly authenticate, don't bother the database then
        if (username == null || credentials == null) {
            return null;
        }

        Connection dbConnection = null;

        // Ensure that we have an open database connection
        dbConnection = open();
        if (dbConnection == null) {
            // If the db connection open fails, return "not authenticated"
            return null;
        }

        try {
            // 获取这个用户的Principal对象
            return authenticate(dbConnection, username, credentials);
        }
        finally
        {
            close(dbConnection);
        }
    }


    @Override
    public boolean isAvailable() {
        return connectionSuccess;
    }

    // ------------------------------------------------------ Protected Methods

    /**
     * 返回指定用户名和凭据关联的Principal; 或者<code>null</code>.
     *
     * @param dbConnection 要使用的数据库连接
     * @param username 要查找的Principal的用户名
     * @param credentials 用于验证这个用户名的密码或其它凭据
     * @return 关联的主体, 或<code>null</code>.
     */
    protected Principal authenticate(Connection dbConnection,
                                     String username,
                                     String credentials) {
        // No user or no credentials
        // Can't possibly authenticate, don't bother the database then
        if (username == null || credentials == null) {
            if (containerLog.isTraceEnabled())
                containerLog.trace(sm.getString("dataSourceRealm.authenticateFailure",
                                                username));
            return null;
        }

        // Look up the user's credentials
        String dbCredentials = getPassword(dbConnection, username);

        if(dbCredentials == null) {
            // User was not found in the database.
            // 浪费一点时间，不要透露用户不存在.
            getCredentialHandler().mutate(credentials);

            if (containerLog.isTraceEnabled())
                containerLog.trace(sm.getString("dataSourceRealm.authenticateFailure",
                                                username));
            return null;
        }

        // 验证用户的凭据
        boolean validated = getCredentialHandler().matches(credentials, dbCredentials);

        if (validated) {
            if (containerLog.isTraceEnabled())
                containerLog.trace(sm.getString("dataSourceRealm.authenticateSuccess",
                                                username));
        } else {
            if (containerLog.isTraceEnabled())
                containerLog.trace(sm.getString("dataSourceRealm.authenticateFailure",
                                                username));
            return null;
        }

        ArrayList<String> list = getRoles(dbConnection, username);

        // 创建并返回这个用户合适的 Principal
        return new GenericPrincipal(username, credentials, list);
    }


    /**
     * 关闭指定的数据库连接.
     *
     * @param dbConnection 要关闭的连接
     */
    protected void close(Connection dbConnection) {

        // 如果数据库连接已经关闭，请不要做任何操作
        if (dbConnection == null)
            return;

        // Commit if not auto committed
        try {
            if (!dbConnection.getAutoCommit()) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            containerLog.error("Exception committing connection before closing:", e);
        }

        // 关闭此数据库连接，并记录任何错误
        try {
            dbConnection.close();
        } catch (SQLException e) {
            containerLog.error(sm.getString("dataSourceRealm.close"), e); // Just log it here
        }

    }

    /**
     * 打开指定的数据库连接.
     *
     * @return Connection to the database
     */
    protected Connection open() {

        try {
            Context context = null;
            if (localDataSource) {
                context = ContextBindings.getClassLoader();
                context = (Context) context.lookup("comp/env");
            } else {
                context = getServer().getGlobalNamingContext();
            }
            DataSource dataSource = (DataSource)context.lookup(dataSourceName);
            Connection connection = dataSource.getConnection();
            connectionSuccess = true;
            return connection;
        } catch (Exception e) {
            connectionSuccess = false;
            // Log the problem for posterity
            containerLog.error(sm.getString("dataSourceRealm.exception"), e);
        }
        return null;
    }

    @Override
    @Deprecated
    protected String getName() {
        return name;
    }

    /**
     * @return 指定用户名的密码.
     */
    @Override
    protected String getPassword(String username) {

        Connection dbConnection = null;

        // 确保有一个打开的数据库连接
        dbConnection = open();
        if (dbConnection == null) {
            return null;
        }

        try {
            return getPassword(dbConnection, username);
        } finally {
            close(dbConnection);
        }
    }

    /**
     * 返回与给定主体的用户名相关联的密码.
     * 
     * @param dbConnection 要使用的数据库连接
     * @param username 要检索密码的用户名
     * 
     * @return 指定用户的密码
     */
    protected String getPassword(Connection dbConnection,
                                 String username) {

        String dbCredentials = null;

        try (PreparedStatement stmt = credentials(dbConnection, username);
                ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                dbCredentials = rs.getString(1);
            }

            return (dbCredentials != null) ? dbCredentials.trim() : null;

        } catch (SQLException e) {
            containerLog.error(
                    sm.getString("dataSourceRealm.getPassword.exception",
                                 username), e);
        }

        return null;
    }


    /**
     * 返回与给定用户名相关联的Principal.
     * 
     * @param username 用户名
     * @return 主体对象
     */
    @Override
    protected Principal getPrincipal(String username) {
        Connection dbConnection = open();
        if (dbConnection == null) {
            return new GenericPrincipal(username, null, null);
        }
        try {
            return (new GenericPrincipal(username,
                    getPassword(dbConnection, username),
                    getRoles(dbConnection, username)));
        } finally {
            close(dbConnection);
        }

    }

    /**
     * 返回与给定用户名相关联的角色.
     * 
     * @param username 应检索角色的用户名
     * 
     * @return 角色名称的数组列表
     */
    protected ArrayList<String> getRoles(String username) {

        Connection dbConnection = null;

        // 确保有一个开放的数据库连接
        dbConnection = open();
        if (dbConnection == null) {
            return null;
        }

        try {
            return getRoles(dbConnection, username);
        } finally {
            close(dbConnection);
        }
    }

    /**
     * 返回与给定用户名相关联的角色
     * 
     * @param dbConnection 要使用的数据库连接
     * @param username 应检索角色的用户名
     * 
     * @return 角色名的列表
     */
    protected ArrayList<String> getRoles(Connection dbConnection,
                                     String username) {

        if (allRolesMode != AllRolesMode.STRICT_MODE && !isRoleStoreDefined()) {
            // 仅使用身份验证配置，而且没有定义角色存储，所以不要花费周期查看
            return null;
        }

        ArrayList<String> list = null;

        try (PreparedStatement stmt = roles(dbConnection, username);
                ResultSet rs = stmt.executeQuery()) {
            list = new ArrayList<>();

            while (rs.next()) {
                String role = rs.getString(1);
                if (role != null) {
                    list.add(role.trim());
                }
            }
            return list;
        } catch(SQLException e) {
            containerLog.error(
                sm.getString("dataSourceRealm.getRoles.exception", username), e);
        }

        return null;
    }

    /**
     * 返回一个 PreparedStatement 配置执行需要的 SELECT, 检索指定用户名的用户凭据.
     *
     * @param dbConnection 要使用的数据库连接
     * @param username 应检索凭证的用户名
     * 
     * @return the prepared statement
     * @exception SQLException 如果发生数据库错误
     */
    private PreparedStatement credentials(Connection dbConnection,
                                            String username)
        throws SQLException {

        PreparedStatement credentials =
            dbConnection.prepareStatement(preparedCredentials);

        credentials.setString(1, username);
        return (credentials);

    }

    /**
     * 返回一个 PreparedStatement 配置执行需要的 SELECT, 检索指定用户名的用户角色.
     *
     * @param dbConnection 要使用的数据库连接
     * @param username 应检索凭证的用户名
     * 
     * @return the prepared statement
     * @exception SQLException 如果发生数据库错误
     */
    private PreparedStatement roles(Connection dbConnection, String username)
        throws SQLException {

        PreparedStatement roles =
            dbConnection.prepareStatement(preparedRoles);

        roles.setString(1, username);
        return (roles);

    }


    private boolean isRoleStoreDefined() {
        return userRoleTable != null || roleNameCol != null;
    }


    // ------------------------------------------------------ Lifecycle Methods

    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected void startInternal() throws LifecycleException {

        // Create the roles PreparedStatement string
        StringBuilder temp = new StringBuilder("SELECT ");
        temp.append(roleNameCol);
        temp.append(" FROM ");
        temp.append(userRoleTable);
        temp.append(" WHERE ");
        temp.append(userNameCol);
        temp.append(" = ?");
        preparedRoles = temp.toString();

        // Create the credentials PreparedStatement string
        temp = new StringBuilder("SELECT ");
        temp.append(userCredCol);
        temp.append(" FROM ");
        temp.append(userTable);
        temp.append(" WHERE ");
        temp.append(userNameCol);
        temp.append(" = ?");
        preparedCredentials = temp.toString();

        super.startInternal();
    }
}
