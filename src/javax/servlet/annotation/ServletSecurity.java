package javax.servlet.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 在{@link javax.servlet.Servlet}实现类上声明这个注解在HTTP协议请求上强制安全约束.<br>
 * 容器对映射到每个声明这个注解的servlet的URL模式施加约束.<br>
 * <br>
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ServletSecurity {

    /**
     * 表示空角色语义的两个可能值, 当角色名称列表为空时激活.
     */
    enum EmptyRoleSemantic {

        /**
         * 必须允许访问，而不管身份验证状态或身份
         */
        PERMIT,

        /**
         * 必须拒绝访问，不管身份验证状态或身份
         */
        DENY
    }

    /**
     * 表示数据传输的两个可能值，加密或不加密.
     */
    enum TransportGuarantee {

        /**
         * 在传输过程中，用户数据不能被容器加密
         */
        NONE,

        /**
         * 容器必须在传输过程中加密用户数据
         */
        CONFIDENTIAL
    }

    /**
     * 适用于不受特定方法约束处理的请求的默认约束
     *
     * @return http约束
     */
    HttpConstraint value() default @HttpConstraint;

    /**
     * 一组HttpMethodConstraint对象将应用到安全约束
     *
     * @return HTTP方法约束数组
     */
    HttpMethodConstraint[] httpMethodConstraints() default {};
}
