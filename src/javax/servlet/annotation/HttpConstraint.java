package javax.servlet.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.servlet.annotation.ServletSecurity.EmptyRoleSemantic;
import javax.servlet.annotation.ServletSecurity.TransportGuarantee;

/**
 * 表示应用于所有请求的安全约束，而HTTP协议方法类型没有其他表示方式，
 * 除了{@link javax.servlet.annotation.ServletSecurity}注解中的{@link javax.servlet.annotation.HttpMethodConstraint}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HttpConstraint {

    /**
     * EmptyRoleSemantic定义当rolesAllowed列表是空的时候的行为.
     *
     * @return 空的角色语义
     */
    EmptyRoleSemantic value() default EmptyRoleSemantic.PERMIT;

    /**
     * 定义是否需要SSL/TLS来处理当前请求.
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
