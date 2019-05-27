package org.apache.tomcat.dbcp.dbcp2;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * A {@link DriverManager}-based implementation of {@link ConnectionFactory}.
 */
public class DriverManagerConnectionFactory implements ConnectionFactory {

    static {
        // Related to DBCP-212
        // 驱动程序管理器不同步加载使用服务提供程序接口的驱动程序. 这将导致多线程环境问题.
        // 此hack确保在DBCP尝试使用它们之前加载驱动程序.
        DriverManager.getDrivers();
    }


    /**
     * @param connectUri 数据库URL的格式
     * <code> jdbc:<em>subprotocol</em>:<em>subname</em></code>
     */
    public DriverManagerConnectionFactory(final String connectUri) {
        _connectUri = connectUri;
        _props = new Properties();
    }

    /**
     * @param connectUri 数据库URL的格式
     * <code> jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @param props 任意字符串标记/值对的列表作为连接参数; 通常至少包括 "user" 和 "password"属性.
     */
    public DriverManagerConnectionFactory(final String connectUri, final Properties props) {
        _connectUri = connectUri;
        _props = props;
    }

    /**
     * @param connectUri 数据库URL的格式
     * <code>jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @param uname 数据库用户名
     * @param passwd 用户的密码
     */
    public DriverManagerConnectionFactory(final String connectUri, final String uname, final String passwd) {
        _connectUri = connectUri;
        _uname = uname;
        _passwd = passwd;
    }

    @Override
    public Connection createConnection() throws SQLException {
        if(null == _props) {
            if(_uname == null && _passwd == null) {
                return DriverManager.getConnection(_connectUri);
            }
            return DriverManager.getConnection(_connectUri,_uname,_passwd);
        }
        return DriverManager.getConnection(_connectUri,_props);
    }

    private String _connectUri = null;
    private String _uname = null;
    private String _passwd = null;
    private Properties _props = null;
}
