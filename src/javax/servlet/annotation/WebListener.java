package javax.servlet.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于为各种类型的事件声明监听器, 在给定的Web应用程序上下文中.<br>
 * <br>
 *
 * 注解的接口必须实现下列接口中的其中之一: {@link javax.servlet.http.HttpSessionAttributeListener},
 * {@link javax.servlet.http.HttpSessionListener},
 * {@link javax.servlet.ServletContextAttributeListener},
 * {@link javax.servlet.ServletContextListener},
 * {@link javax.servlet.ServletRequestAttributeListener},
 * {@link javax.servlet.ServletRequestListener} or
 * {@link javax.servlet.http.HttpSessionIdListener}
 * <br>
 *
 * E.g. <code>@WebListener</code><br>
 * <code>public TestListener implements ServletContextListener {</code><br>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebListener {

    /**
     * @return 监听器的描述
     */
    String value() default "";
}
