package javax.servlet;

import java.util.ResourceBundle;

import javax.servlet.annotation.ServletSecurity.EmptyRoleSemantic;
import javax.servlet.annotation.ServletSecurity.TransportGuarantee;

/**
 * 等效于{@link javax.servlet.annotation.HttpConstraint}对安全约束的编程配置.
 */
public class HttpConstraintElement {

    private static final String LSTRING_FILE = "javax.servlet.LocalStrings";
    private static final ResourceBundle lStrings =
        ResourceBundle.getBundle(LSTRING_FILE);

    private final EmptyRoleSemantic emptyRoleSemantic;// = EmptyRoleSemantic.PERMIT;
    private final TransportGuarantee transportGuarantee;// = TransportGuarantee.NONE;
    private final String[] rolesAllowed;// = new String[0];

    /**
     * 默认约束是允许的，没有传输保证.
     */
    public HttpConstraintElement() {
        // Default constructor
        this.emptyRoleSemantic = EmptyRoleSemantic.PERMIT;
        this.transportGuarantee = TransportGuarantee.NONE;
        this.rolesAllowed = new String[0];
    }

    /**
     * 用空角色语义构建约束. 通常和{@link EmptyRoleSemantic#DENY}一起使用.
     *
     * @param emptyRoleSemantic 应用于新创建的约束的空角色语义
     */
    public HttpConstraintElement(EmptyRoleSemantic emptyRoleSemantic) {
        this.emptyRoleSemantic = emptyRoleSemantic;
        this.transportGuarantee = TransportGuarantee.NONE;
        this.rolesAllowed = new String[0];
    }

    /**
     * 用传输保证和角色构造约束.
     *
     * @param transportGuarantee 适用于新创建约束的传输保证
     * @param rolesAllowed       与新创建的约束相关联的角色
     */
    public HttpConstraintElement(TransportGuarantee transportGuarantee,
            String... rolesAllowed) {
        this.emptyRoleSemantic = EmptyRoleSemantic.PERMIT;
        this.transportGuarantee = transportGuarantee;
        this.rolesAllowed = rolesAllowed;
    }

    /**
     * 用空角色语义、传输保证和角色构建约束.
     *
     * @param emptyRoleSemantic 应用于新创建的约束的空角色语义
     * @param transportGuarantee 适用于新创建约束的传输保证
     * @param rolesAllowed       与新创建的约束相关联的角色
     * @throws IllegalArgumentException 当使用DENY时，已经指定了角色
     */
    public HttpConstraintElement(EmptyRoleSemantic emptyRoleSemantic,
            TransportGuarantee transportGuarantee, String... rolesAllowed) {
        if (rolesAllowed != null && rolesAllowed.length > 0 &&
                EmptyRoleSemantic.DENY.equals(emptyRoleSemantic)) {
            throw new IllegalArgumentException(lStrings.getString(
                    "httpConstraintElement.invalidRolesDeny"));
        }
        this.emptyRoleSemantic = emptyRoleSemantic;
        this.transportGuarantee = transportGuarantee;
        this.rolesAllowed = rolesAllowed;
    }

    public EmptyRoleSemantic getEmptyRoleSemantic() {
        return emptyRoleSemantic;
    }

    public TransportGuarantee getTransportGuarantee() {
        return transportGuarantee;
    }

    public String[] getRolesAllowed() {
        return rolesAllowed;
    }
}