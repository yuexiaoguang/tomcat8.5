package org.apache.catalina.authenticator.jaspic;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.message.callback.CallerPrincipalCallback;
import javax.security.auth.message.callback.GroupPrincipalCallback;

import org.apache.catalina.realm.GenericPrincipal;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * 因为类是无状态的，所以实现为单例.
 */
public class CallbackHandlerImpl implements CallbackHandler {

    private static final Log log = LogFactory.getLog(CallbackHandlerImpl.class);
    private static final StringManager sm = StringManager.getManager(CallbackHandlerImpl.class);

    private static CallbackHandler instance;


    static {
        instance = new CallbackHandlerImpl();
    }


    public static CallbackHandler getInstance() {
        return instance;
    }


    private  CallbackHandlerImpl() {
        // Hide default constructor
    }


    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {

        String name = null;
        Principal principal = null;
        Subject subject = null;
        String[] groups = null;

        if (callbacks != null) {
            // 需要组合来自多个回调的数据，因此使用它来保存数据
            // 处理回调
            for (Callback callback : callbacks) {
                if (callback instanceof CallerPrincipalCallback) {
                    CallerPrincipalCallback cpc = (CallerPrincipalCallback) callback;
                    name = cpc.getName();
                    principal = cpc.getPrincipal();
                    subject = cpc.getSubject();
                } else if (callback instanceof GroupPrincipalCallback) {
                    GroupPrincipalCallback gpc = (GroupPrincipalCallback) callback;
                    groups = gpc.getGroups();
                } else {
                    log.error(sm.getString("callbackHandlerImpl.jaspicCallbackMissing",
                            callback.getClass().getName()));
                }
            }

            // 创建GenericPrincipal
            Principal gp = getPrincipal(principal, name, groups);
            if (subject != null && gp != null) {
                subject.getPrivateCredentials().add(gp);
            }
        }
    }


    private Principal getPrincipal(Principal principal, String name, String[] groups) {
        // 如果Principal缓存在会话中
        if (principal instanceof GenericPrincipal) {
            return principal;
        }
        if (name == null && principal != null) {
            name = principal.getName();
        }
        if (name == null) {
            return null;
        }
        List<String> roles;
        if (groups == null || groups.length == 0) {
            roles = Collections.emptyList();
        } else {
            roles = Arrays.asList(groups);
        }

        return new GenericPrincipal(name, null, roles, principal);
    }
}
