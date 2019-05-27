package javax.servlet.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于声明一个{@link javax.servlet.Servlet}. <br>
 *
 * 如果未定义name属性, 使用了类的完全限定名.<br>
 * <br>
 *
 * 至少有一个URL模式必须使用注解的{@code value}或{@code urlPattern}属性声明, 但不能两个都用.<br>
 * <br>
 *
 * 当URL模式是唯一设置的属性时，建议使用{@code value}属性, 否则应该使用{@code urlPattern}属性.<br>
 * <br>
 *
 * 这个注解声明的类必须继承{@link javax.servlet.http.HttpServlet}. <br>
 * <br>
 *
 * E.g. <code>@WebServlet("/path")}<br>
 * public class TestServlet extends HttpServlet ... {</code><br>
 *
 * E.g.
 * <code>@WebServlet(name="TestServlet", urlPatterns={"/path", "/alt"}) <br>
 * public class TestServlet extends HttpServlet ... {</code><br>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebServlet {

    /**
     * @return Servlet的名称
     */
    String name() default "";

    /**
     * 允许对类进行非常简单的注解.
     *
     * @return 一组URL模式
     */
    String[] value() default {};

    /**
     * @return 一组URL模式
     */
    String[] urlPatterns() default {};

    /**
     * @return 启动时加载顺序
     */
    int loadOnStartup() default -1;

    /**
     * @return 这个Servlet的一组初始化参数
     */
    WebInitParam[] initParams() default {};

    /**
     * @return 此servlet支持的异步操作
     */
    boolean asyncSupported() default false;

    /**
     * @return 这个Servlet的小图标
     */
    String smallIcon() default "";

    /**
     * @return 这个Servlet的大图标
     */
    String largeIcon() default "";

    /**
     * @return 这个Servlet的描述
     */
    String description() default "";

    /**
     * @return 这个Servlet的显示名称
     */
    String displayName() default "";
}
