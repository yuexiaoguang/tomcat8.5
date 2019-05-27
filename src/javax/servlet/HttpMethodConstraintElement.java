package javax.servlet;

import java.util.ResourceBundle;

public class HttpMethodConstraintElement extends HttpConstraintElement {

    // 不能继承HttpConstraintElement，API不允许
    private static final String LSTRING_FILE = "javax.servlet.LocalStrings";
    private static final ResourceBundle lStrings =
        ResourceBundle.getBundle(LSTRING_FILE);

    private final String methodName;

    public HttpMethodConstraintElement(String methodName) {
        if (methodName == null || methodName.length() == 0) {
            throw new IllegalArgumentException(lStrings.getString(
                    "httpMethodConstraintElement.invalidMethod"));
        }
        this.methodName = methodName;
    }

    public HttpMethodConstraintElement(String methodName,
            HttpConstraintElement constraint) {
        super(constraint.getEmptyRoleSemantic(),
                constraint.getTransportGuarantee(),
                constraint.getRolesAllowed());
        if (methodName == null || methodName.length() == 0) {
            throw new IllegalArgumentException(lStrings.getString(
                    "httpMethodConstraintElement.invalidMethod"));
        }
        this.methodName = methodName;
    }

    public String getMethodName() {
        return methodName;
    }
}