package javax.servlet.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.servlet.annotation.ServletSecurity.EmptyRoleSemantic;
import javax.servlet.annotation.ServletSecurity.TransportGuarantee;

/**
 * 特定的安全约束可以应用于不同类型的请求, 通过在{@link javax.servlet.annotation.ServletSecurity}注解中使用此注解，由http协议方法类型进行区分.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HttpMethodConstraint {

    /**
     * HTTP协议方法名(e.g. POST, PUT)
     *
     * @return 方法名
     */
    String value();

    /**
     * EmptyRoleSemantic决定当rolesAllowed列表为空时的行为.
     *
     * @return 空的角色语义
     */
    EmptyRoleSemantic emptyRoleSemantic() default EmptyRoleSemantic.PERMIT;

    /**
     * 决定是否需要SSL/TLS处理当前请求.
     *
     * @return 运输保障
     */
    TransportGuarantee transportGuarantee() default TransportGuarantee.NONE;

    /**
     * 授权角色的名称. 容器在注解过程中可能会丢弃重复的角色名.
     * "*"如果作为角色名出现，则没有特殊意义.
     *
     * @return 名称数组. 该数组可能为零长度, 在EmptyRoleSemantic应用的情况下;
     * 	返回的值决定了是否允许访问或拒绝访问，不管这两种情况下的身份和身份验证状态如何, PERMIT or DENY.<br>
     *         否则，当数组包含一个或多个角色名称时，如果用户是至少一个命名角色的成员，则允许访问. EmptyRoleSemantic在这种情况下不会应用.
     */
    String[] rolesAllowed() default {};
}
