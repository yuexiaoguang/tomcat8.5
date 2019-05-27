package javax.websocket.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用来注解POJO端点的方法参数，{@link ServerEndpoint}已经使用 {@link ServerEndpoint#value()}（使用URI模板）定义.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface PathParam {
    String value();
}
