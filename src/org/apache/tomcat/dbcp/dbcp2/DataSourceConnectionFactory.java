package org.apache.tomcat.dbcp.dbcp2;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

public class DataSourceConnectionFactory implements ConnectionFactory {
    public DataSourceConnectionFactory(final DataSource source) {
        this(source,null,null);
    }

    public DataSourceConnectionFactory(final DataSource source, final String uname, final String passwd) {
        _source = source;
        _uname = uname;
        _passwd = passwd;
    }

    @Override
    public Connection createConnection() throws SQLException {
        if(null == _uname && null == _passwd) {
            return _source.getConnection();
        }
        return _source.getConnection(_uname,_passwd);
    }

    private final String _uname;
    private final String _passwd;
    private final DataSource _source;
}
