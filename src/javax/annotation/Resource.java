package javax.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Resource {
    public enum AuthenticationType {
        CONTAINER,
        APPLICATION
    }
    public String name() default "";
    /**
     * 使用普通注释1.2以来的泛型.
     */
    public Class<?> type() default Object.class;
    public AuthenticationType authenticationType() default AuthenticationType.CONTAINER;
    public boolean shareable() default true;
    public String description() default "";
    public String mappedName() default "";
    public String lookup() default "";
}
