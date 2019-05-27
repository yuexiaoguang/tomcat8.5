package org.apache.tomcat.util.bcel.classfile;

import org.apache.tomcat.util.bcel.Const;

public class EnumElementValue extends ElementValue
{
    private final int valueIdx;

    EnumElementValue(final int type, final int valueIdx, final ConstantPool cpool) {
        super(type, cpool);
        if (type != ENUM_CONSTANT)
            throw new RuntimeException(
                    "Only element values of type enum can be built with this ctor - type specified: " + type);
        this.valueIdx = valueIdx;
    }

    @Override
    public String stringifyValue()
    {
        final ConstantUtf8 cu8 = (ConstantUtf8) super.getConstantPool().getConstant(valueIdx,
                Const.CONSTANT_Utf8);
        return cu8.getBytes();
    }
}
