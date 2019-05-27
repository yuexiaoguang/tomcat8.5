package javax.ejb;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EJB {
   String name() default "";
   String description() default "";
   @SuppressWarnings("rawtypes") // 不能使用 Class<?> , 因为API 需要符合规范
   Class beanInterface() default java.lang.Object.class;
   String beanName() default "";
   String mappedName() default "";
   String lookup() default "";
}
