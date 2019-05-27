package org.apache.catalina.security;

import java.security.Security;

import org.apache.catalina.startup.CatalinaProperties;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 工具类保护 Catalina 对包的访问和插入.
 * 代码是从Catalina.java挪过来的
 */
public final class SecurityConfig{
    private static SecurityConfig singleton = null;

    private static final Log log = LogFactory.getLog(SecurityConfig.class);


    private static final String PACKAGE_ACCESS =  "sun.,"
                                                + "org.apache.catalina."
                                                + ",org.apache.jasper."
                                                + ",org.apache.coyote."
                                                + ",org.apache.tomcat.";

    // FIX ME package "javax." was removed to prevent HotSpot
    // fatal internal errors
    private static final String PACKAGE_DEFINITION= "java.,sun."
                                                + ",org.apache.catalina."
                                                + ",org.apache.coyote."
                                                + ",org.apache.tomcat."
                                                + ",org.apache.jasper.";
    /**
     * conf/catalina.properties中受保护的包的列表
     */
    private final String packageDefinition;


    /**
     * conf/catalina.properties中受保护的包的列表
     */
    private final String packageAccess;


    /**
     * 创建这个类的单例.
     */
    private SecurityConfig() {
        String definition = null;
        String access = null;
        try{
            definition = CatalinaProperties.getProperty("package.definition");
            access = CatalinaProperties.getProperty("package.access");
        } catch (java.lang.Exception ex){
            if (log.isDebugEnabled()){
                log.debug("Unable to load properties using CatalinaProperties", ex);
            }
        } finally {
            packageDefinition = definition;
            packageAccess = access;
        }
    }


    /**
     * 返回单例.
     * @return an instance of that class.
     */
    public static SecurityConfig newInstance(){
        if (singleton == null){
            singleton = new SecurityConfig();
        }
        return singleton;
    }


    /**
     * 设置安全 package.access 的值.
     */
    public void setPackageAccess(){
        // If catalina.properties is missing, protect all by default.
        if (packageAccess == null){
            setSecurityProperty("package.access", PACKAGE_ACCESS);
        } else {
            setSecurityProperty("package.access", packageAccess);
        }
    }


    /**
     * 设置安全 package.definition 的值.
     */
     public void setPackageDefinition(){
        // If catalina.properties is missing, protect all by default.
         if (packageDefinition == null){
            setSecurityProperty("package.definition", PACKAGE_DEFINITION);
         } else {
            setSecurityProperty("package.definition", packageDefinition);
         }
    }


    /**
     * 设置适当的安全属性
     * @param properties the package.* property.
     */
    private final void setSecurityProperty(String properties, String packageList){
        if (System.getSecurityManager() != null){
            String definition = Security.getProperty(properties);
            if( definition != null && definition.length() > 0 ){
                if (packageList.length() > 0) {
                    definition = definition + ',' + packageList;
                }
            } else {
                definition = packageList;
            }

            Security.setProperty(properties, definition);
        }
    }
}
