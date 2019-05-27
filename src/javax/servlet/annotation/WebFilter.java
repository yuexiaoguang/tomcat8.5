package javax.servlet.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.servlet.DispatcherType;

/**
 * 声明一个Servlet {@link javax.servlet.Filter}. <br>
 * <br>
 *
 * 此注解将在部署期间由容器处理, 找到的Filter类将被创建为一个配置并应用到URL模式, {@link javax.servlet.Servlet}, {@link javax.servlet.DispatcherType}.<br>
 * <br>
 *
 * 如果未定义name属性，则使用该类的完全限定名.<br>
 * <br>
 *
 * 至少一个URL模式必须在注解的{@code value}或{@code urlPattern}属性中声明, 但不能两个都指定.<br>
 * <br>
 *
 * 当URL模式是唯一设置的属性时，建议使用{@code value}属性, 否则应该使用{@code urlPattern}属性.<br>
 * <br>
 *
 * 被注解的类必须实现{@link javax.servlet.Filter}.
 *
 * E.g.
 *
 * <code>@WebFilter("/path/*")</code><br>
 * <code>public class AnExampleFilter implements Filter { ... </code><br>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebFilter {

    /**
     * @return 过滤器描述
     */
    String description() default "";

    /**
     * @return 过滤器的显示名称
     */
    String displayName() default "";

    /**
     * @return 过滤器的初始化参数名称
     */
    WebInitParam[] initParams() default {};

    /**
     * @return 过滤器名称
     */
    String filterName() default "";

    /**
     * @return 此过滤器的小图标
     */
    String smallIcon() default "";

    /**
     * @return 此过滤器的大图标
     */
    String largeIcon() default "";

    /**
     * @return 过滤器应用的Servlet名称集合
     */
    String[] servletNames() default {};

    /**
     * 允许对类进行非常简单的注解.
     *
     * @return 一组URL模式
     */
    String[] value() default {};

    /**
     * @return 此过滤器应用的所有URL模式
     */
    String[] urlPatterns() default {};

    /**
     * @return 这个过滤器应用的一组DispatcherType
     */
    DispatcherType[] dispatcherTypes() default {DispatcherType.REQUEST};

    /**
     * @return 此过滤器支持的异步操作
     */
    boolean asyncSupported() default false;
}
