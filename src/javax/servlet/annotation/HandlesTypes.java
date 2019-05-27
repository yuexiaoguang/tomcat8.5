package javax.servlet.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于声明应用程序类数组，传递给{@link javax.servlet.ServletContainerInitializer}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HandlesTypes {

    /**
     * @return 类的数组
     */
    Class<?>[] value();

}
