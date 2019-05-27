package org.apache.tomcat.util.bcel.classfile;

import org.apache.tomcat.util.bcel.Const;

public class ClassElementValue extends ElementValue
{
    // 对于原始类型和字符串类型, 这指向cpool中的值条目
    // 对于 'class', 这指向cpool中的类条目
    private final int idx;

    ClassElementValue(final int type, final int idx, final ConstantPool cpool) {
        super(type, cpool);
        this.idx = idx;
    }


    @Override
    public String stringifyValue()
    {
        final ConstantUtf8 cu8 = (ConstantUtf8) super.getConstantPool().getConstant(idx,
                Const.CONSTANT_Utf8);
        return cu8.getBytes();
    }
}
