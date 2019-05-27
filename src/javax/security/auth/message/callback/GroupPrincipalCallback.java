package javax.security.auth.message.callback;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;

/**
 * 回调，允许身份验证模块通知用户所在的组的运行时间.
 */
public class GroupPrincipalCallback implements Callback {

    private final Subject subject;
    private final String[] groups;

    public GroupPrincipalCallback(Subject subject, String[] groups) {
        this.subject = subject;
        this.groups = groups;
    }

    public Subject getSubject() {
        return subject;
    }

    public String[] getGroups() {
        return groups;
    }
}
