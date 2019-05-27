package javax.servlet.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于在{@link javax.servlet.Servlet}或{@link javax.servlet.Filter}声明一个初始化参数,
 * 在{@link javax.servlet.annotation.WebFilter}或{@link javax.servlet.annotation.WebServlet}注解中使用.<br>
 * <br>
 *
 * E.g.
 * <code>&amp;#064;WebServlet(name="TestServlet", urlPatterns={"/test"},initParams={&amp;#064;WebInitParam(name="test", value="true")})
 * public class TestServlet extends HttpServlet { ... </code><br>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebInitParam {

    /**
     * @return 初始化参数的名称
     */
    String name();

    /**
     * @return 初始化参数的值
     */
    String value();

    /**
     * @return 初始化参数的说明
     */
    String description() default "";
}
