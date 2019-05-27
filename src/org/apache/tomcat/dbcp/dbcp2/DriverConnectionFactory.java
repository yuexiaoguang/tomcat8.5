package org.apache.tomcat.dbcp.dbcp2;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

public class DriverConnectionFactory implements ConnectionFactory {
    public DriverConnectionFactory(final Driver driver, final String connectUri, final Properties props) {
        _driver = driver;
        _connectUri = connectUri;
        _props = props;
    }

    @Override
    public Connection createConnection() throws SQLException {
        return _driver.connect(_connectUri,_props);
    }

    private final Driver _driver;
    private final String _connectUri;
    private final Properties _props;

    @Override
    public String toString() {
        return this.getClass().getName() + " [" + String.valueOf(_driver) + ";" +
                String.valueOf(_connectUri) + ";"  + String.valueOf(_props) + "]";
    }
}
