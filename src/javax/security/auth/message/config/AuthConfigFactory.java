package javax.security.auth.message.config;

import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.Security;
import java.security.SecurityPermission;
import java.util.Map;

public abstract class AuthConfigFactory {

    public static final String DEFAULT_FACTORY_SECURITY_PROPERTY =
            "authconfigprovider.factory";
    public static final String GET_FACTORY_PERMISSION_NAME =
            "getProperty.authconfigprovider.factory";
    public static final String SET_FACTORY_PERMISSION_NAME =
            "setProperty.authconfigprovider.factory";
    public static final String PROVIDER_REGISTRATION_PERMISSION_NAME =
            "setProperty.authconfigfactory.provider";

    public static final SecurityPermission getFactorySecurityPermission =
            new SecurityPermission(GET_FACTORY_PERMISSION_NAME);

    public static final SecurityPermission setFactorySecurityPermission =
            new SecurityPermission(SET_FACTORY_PERMISSION_NAME);

    public static final SecurityPermission providerRegistrationSecurityPermission =
            new SecurityPermission(PROVIDER_REGISTRATION_PERMISSION_NAME);

    private static final String DEFAULT_JASPI_AUTHCONFIGFACTORYIMPL =
            "org.apache.catalina.authenticator.jaspic.AuthConfigFactoryImpl";

    private static volatile AuthConfigFactory factory;

    public AuthConfigFactory() {
    }

    public static AuthConfigFactory getFactory() {
        checkPermission(getFactorySecurityPermission);
        if (factory != null) {
            return factory;
        }

        synchronized (AuthConfigFactory.class) {
            if (factory == null) {
                final String className = getFactoryClassName();
                try {
                    factory = AccessController.doPrivileged(
                            new PrivilegedExceptionAction<AuthConfigFactory>() {
                        @Override
                        public AuthConfigFactory run() throws ClassNotFoundException,
                                InstantiationException, IllegalAccessException, IllegalArgumentException,
                                InvocationTargetException, NoSuchMethodException, SecurityException {
                            // 使用与该类相同的类装入器加载该类. 注意，不应该使用线程上下文类装入器，因为这将在容器环境中触发内存泄漏.
                            Class<?> clazz = Class.forName(className);
                            return (AuthConfigFactory) clazz.getConstructor().newInstance();
                        }
                    });
                } catch (PrivilegedActionException e) {
                    Exception inner = e.getException();
                    if (inner instanceof InstantiationException) {
                        throw (SecurityException) new SecurityException("AuthConfigFactory error:" +
                                inner.getCause().getMessage()).initCause(inner.getCause());
                    } else {
                        throw (SecurityException) new SecurityException(
                                "AuthConfigFactory error: " + inner).initCause(inner);
                    }
                }
            }
        }
        return factory;
    }

    public static synchronized void setFactory(AuthConfigFactory factory) {
        checkPermission(setFactorySecurityPermission);
        AuthConfigFactory.factory = factory;
    }

    public abstract AuthConfigProvider getConfigProvider(String layer, String appContext,
            RegistrationListener listener);

    @SuppressWarnings("rawtypes") // JASPIC API uses raw types
    public abstract String registerConfigProvider(String className, Map properties, String layer,
            String appContext, String description);

    public abstract String registerConfigProvider(AuthConfigProvider provider, String layer,
            String appContext, String description);

    public abstract boolean removeRegistration(String registrationID);

    public abstract String[] detachListener(RegistrationListener listener, String layer,
            String appContext);

    public abstract String[] getRegistrationIDs(AuthConfigProvider provider);

    public abstract RegistrationContext getRegistrationContext(String registrationID);

    public abstract void refresh();

    private static void checkPermission(Permission permission) {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            securityManager.checkPermission(permission);
        }
    }

    private static String getFactoryClassName() {
        String className = AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return Security.getProperty(DEFAULT_FACTORY_SECURITY_PROPERTY);
            }
        });

        if (className != null) {
            return className;
        }

        return DEFAULT_JASPI_AUTHCONFIGFACTORYIMPL;
    }

    public static interface RegistrationContext {

        String getMessageLayer();

        String getAppContext();

        String getDescription();

        boolean isPersistent();
    }
}
