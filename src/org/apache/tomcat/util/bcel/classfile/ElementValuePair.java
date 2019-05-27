package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInput;
import java.io.IOException;

import org.apache.tomcat.util.bcel.Const;

/**
 * 注解的元素值对
 */
public class ElementValuePair
{
    private final ElementValue elementValue;

    private final ConstantPool constantPool;

    private final int elementNameIndex;

    ElementValuePair(final DataInput file, final ConstantPool constantPool) throws IOException {
        this.constantPool = constantPool;
        this.elementNameIndex = file.readUnsignedShort();
        this.elementValue = ElementValue.readElementValue(file, constantPool);
    }

    public String getNameString()
    {
        final ConstantUtf8 c = (ConstantUtf8) constantPool.getConstant(
                elementNameIndex, Const.CONSTANT_Utf8);
        return c.getBytes();
    }

    public final ElementValue getValue()
    {
        return elementValue;
    }
}
