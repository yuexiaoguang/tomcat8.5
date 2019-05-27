package org.apache.catalina.authenticator.jaspic;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.config.ServerAuthConfig;
import javax.security.auth.message.config.ServerAuthContext;
import javax.security.auth.message.module.ServerAuthModule;

import org.apache.tomcat.util.res.StringManager;

/**
 * 基本实现主要用于使用只提供模块的第三方{@link ServerAuthModule}实现时.
 * 这个实现支持使用多个模块配置{@link ServerAuthContext}.
 */
public class SimpleServerAuthConfig implements ServerAuthConfig {

    private static StringManager sm = StringManager.getManager(SimpleServerAuthConfig.class);

    private static final String SERVER_AUTH_MODULE_KEY_PREFIX =
            "org.apache.catalina.authenticator.jaspic.ServerAuthModule.";

    private final String layer;
    private final String appContext;
    private final CallbackHandler handler;
    private final Map<String,String> properties;

    private volatile ServerAuthContext serverAuthContext;

    public SimpleServerAuthConfig(String layer, String appContext, CallbackHandler handler,
            Map<String,String> properties) {
        this.layer = layer;
        this.appContext = appContext;
        this.handler = handler;
        this.properties = properties;
    }


    @Override
    public String getMessageLayer() {
        return layer;
    }


    @Override
    public String getAppContext() {
        return appContext;
    }


    @Override
    public String getAuthContextID(MessageInfo messageInfo) {
        return messageInfo.toString();
    }


    @Override
    public void refresh() {
        serverAuthContext = null;
    }


    @Override
    public boolean isProtected() {
        return false;
    }


    @SuppressWarnings({"rawtypes", "unchecked"}) // JASPIC API uses raw types
    @Override
    public ServerAuthContext getAuthContext(String authContextID, Subject serviceSubject,
            Map properties) throws AuthException {
        ServerAuthContext serverAuthContext = this.serverAuthContext;
        if (serverAuthContext == null) {
            synchronized (this) {
                if (this.serverAuthContext == null) {
                    Map<String,String> mergedProperties = new HashMap<>();
                    if (this.properties != null) {
                        mergedProperties.putAll(this.properties);
                    }
                    if (properties != null) {
                        mergedProperties.putAll(properties);
                    }

                    List<ServerAuthModule> modules = new ArrayList<>();
                    int moduleIndex = 1;
                    String key = SERVER_AUTH_MODULE_KEY_PREFIX + moduleIndex;
                    String moduleClassName = mergedProperties.get(key);
                    while (moduleClassName != null) {
                        try {
                            Class<?> clazz = Class.forName(moduleClassName);
                            ServerAuthModule module =
                                    (ServerAuthModule) clazz.getConstructor().newInstance();
                            module.initialize(null, null, handler, mergedProperties);
                            modules.add(module);
                        } catch (ClassNotFoundException | InstantiationException |
                                IllegalAccessException | IllegalArgumentException |
                                InvocationTargetException | NoSuchMethodException |
                                SecurityException e) {
                            AuthException ae = new AuthException();
                            ae.initCause(e);
                            throw ae;
                        }

                        // 寻找下一个模块
                        moduleIndex++;
                        key = SERVER_AUTH_MODULE_KEY_PREFIX + moduleIndex;
                        moduleClassName = mergedProperties.get(key);
                    }

                    if (modules.size() == 0) {
                        throw new AuthException(sm.getString("simpleServerAuthConfig.noModules"));
                    }

                    this.serverAuthContext = createServerAuthContext(modules);
                }
                serverAuthContext = this.serverAuthContext;
            }
        }

        return serverAuthContext;
    }


    protected ServerAuthContext createServerAuthContext(List<ServerAuthModule> modules) {
        return new SimpleServerAuthContext(modules);
    }
}
