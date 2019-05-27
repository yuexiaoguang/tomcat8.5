package javax.security.auth.message;

public class AuthStatus {

    public static final AuthStatus SUCCESS = new AuthStatus("SUCCESS");
    public static final AuthStatus FAILURE = new AuthStatus("FAILURE");
    public static final AuthStatus SEND_SUCCESS = new AuthStatus("SEND_SUCCESS");
    public static final AuthStatus SEND_FAILURE = new AuthStatus("SEND_FAILURE");
    public static final AuthStatus SEND_CONTINUE = new AuthStatus("SEND_CONTINUE");

    private final String name;

    private AuthStatus(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
