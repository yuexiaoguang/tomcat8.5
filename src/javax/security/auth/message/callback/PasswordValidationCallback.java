package javax.security.auth.message.callback;

import java.util.Arrays;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;

/**
 * 回调，允许身份验证模块提供用户名和密码（对运行时？）并确定验证结果是否正确.
 */
public class PasswordValidationCallback implements Callback {

    private final Subject subject;
    private final String username;
    private char[] password;
    private boolean result;

    public PasswordValidationCallback(Subject subject, String username, char[] password) {
        this.subject = subject;
        this.username = username;
        this.password = password;
    }

    public Subject getSubject() {
        return subject;
    }

    public String getUsername() {
        return username;
    }

    public char[] getPassword() {
        return password;
    }

    public void clearPassword() {
        Arrays.fill(password, (char) 0);
        password = new char[0];
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    public boolean getResult() {
        return result;
    }
}
