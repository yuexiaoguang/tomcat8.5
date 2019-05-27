package javax.xml.ws;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface WebServiceRef {
    public String name() default "";
    @SuppressWarnings("rawtypes") // Can't use Class<?> because API needs to match specification
    public Class type() default java.lang.Object.class;
    @SuppressWarnings("rawtypes") // Can't use Class<?> because API needs to match specification
    public Class value() default java.lang.Object.class;
    public String wsdlLocation() default "";
    public String mappedName() default "";
}
