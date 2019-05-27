package org.apache.catalina.loader;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;

/**
 * 这个类通过{@link WebappClassLoaderBase}加载，使其能够取消Web应用程序忘记的JDBC驱动程序.
 *
 * 自从这个类通过{@link WebappClassLoaderBase}加载, 它不能引用任何内部的Tomcat类，因为这会导致安全管理器拒绝.
 */
public class JdbcLeakPrevention {

    public List<String> clearJdbcDriverRegistrations() throws SQLException {
        List<String> driverNames = new ArrayList<>();

        /*
         * DriverManager.getDrivers() 有一个注册驱动的副作用.
         * 因此, 第一次调用这个方法 a) 获取原始加载的驱动程序列表，而且 b) 触发不必要的副作用.
         * 第二个调用获得完整的驱动程序列表，确保原始驱动程序和任何因副作用而加载的都被注销.
         */
        HashSet<Driver> originalDrivers = new HashSet<>();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            originalDrivers.add(drivers.nextElement());
        }
        drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            // 只卸载这个Web应用程序加载的驱动程序
            if (driver.getClass().getClassLoader() !=
                this.getClass().getClassLoader()) {
                continue;
            }
            // 仅报告原始注册的驱动程序. 跳过任何作为此代码的副作用注册的任何代码.
            if (originalDrivers.contains(driver)) {
                driverNames.add(driver.getClass().getCanonicalName());
            }
            DriverManager.deregisterDriver(driver);
        }
        return driverNames;
    }
}
