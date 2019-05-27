package javax.security.auth.message.callback;

import java.security.Principal;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;

/**
 * 回调，使身份验证模块能够通知调用主体或调用主体的名称的运行时间.
 */
public class CallerPrincipalCallback implements Callback {

    private final Subject subject;
    private final Principal principal;
    private final String name;

    public CallerPrincipalCallback(Subject subject, Principal principal) {
        this.subject = subject;
        this.principal = principal;
        this.name = null;
    }

    public CallerPrincipalCallback(Subject subject, String name) {
        this.subject = subject;
        this.principal = null;
        this.name = name;
    }

    public Subject getSubject() {
        return subject;
    }

    public Principal getPrincipal() {
        return principal;
    }

    public String getName() {
        return name;
    }
}
