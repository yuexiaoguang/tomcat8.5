package org.apache.tomcat.util.bcel.classfile;

public class AnnotationElementValue extends ElementValue
{
    // 对于注解元素值, 这是注解
    private final AnnotationEntry annotationEntry;

    AnnotationElementValue(final int type, final AnnotationEntry annotationEntry,
            final ConstantPool cpool)
    {
        super(type, cpool);
        if (type != ANNOTATION) {
            throw new RuntimeException(
                    "Only element values of type annotation can be built with this ctor - type specified: " + type);
        }
        this.annotationEntry = annotationEntry;
    }

    @Override
    public String stringifyValue()
    {
        return annotationEntry.toString();
    }

    public AnnotationEntry getAnnotationEntry()
    {
        return annotationEntry;
    }
}
