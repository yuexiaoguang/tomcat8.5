package org.apache.tomcat.util.descriptor;

public class Constants {

    public static final String PACKAGE_NAME =
            Constants.class.getPackage().getName();

    public static final boolean IS_SECURITY_ENABLED = (System.getSecurityManager() != null);
}
