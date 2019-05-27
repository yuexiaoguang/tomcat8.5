package org.apache.tomcat.util.bcel.classfile;

import org.apache.tomcat.util.bcel.Const;

public class SimpleElementValue extends ElementValue
{
    private final int index;

    SimpleElementValue(final int type, final int index, final ConstantPool cpool) {
        super(type, cpool);
        this.index = index;
    }

    /**
     * @return cpool中的值条目索引
     */
    public int getIndex()
    {
        return index;
    }


    // 无论它是什么类型的值，都将它作为一个字符串返回
    @Override
    public String stringifyValue()
    {
        final ConstantPool cpool = super.getConstantPool();
        final int _type = super.getType();
        switch (_type)
        {
        case PRIMITIVE_INT:
            final ConstantInteger c = (ConstantInteger) cpool.getConstant(getIndex(),
                    Const.CONSTANT_Integer);
            return Integer.toString(c.getBytes());
        case PRIMITIVE_LONG:
            final ConstantLong j = (ConstantLong) cpool.getConstant(getIndex(),
                    Const.CONSTANT_Long);
            return Long.toString(j.getBytes());
        case PRIMITIVE_DOUBLE:
            final ConstantDouble d = (ConstantDouble) cpool.getConstant(getIndex(),
                    Const.CONSTANT_Double);
            return Double.toString(d.getBytes());
        case PRIMITIVE_FLOAT:
            final ConstantFloat f = (ConstantFloat) cpool.getConstant(getIndex(),
                    Const.CONSTANT_Float);
            return Float.toString(f.getBytes());
        case PRIMITIVE_SHORT:
            final ConstantInteger s = (ConstantInteger) cpool.getConstant(getIndex(),
                    Const.CONSTANT_Integer);
            return Integer.toString(s.getBytes());
        case PRIMITIVE_BYTE:
            final ConstantInteger b = (ConstantInteger) cpool.getConstant(getIndex(),
                    Const.CONSTANT_Integer);
            return Integer.toString(b.getBytes());
        case PRIMITIVE_CHAR:
            final ConstantInteger ch = (ConstantInteger) cpool.getConstant(
                    getIndex(), Const.CONSTANT_Integer);
            return String.valueOf((char)ch.getBytes());
        case PRIMITIVE_BOOLEAN:
            final ConstantInteger bo = (ConstantInteger) cpool.getConstant(
                    getIndex(), Const.CONSTANT_Integer);
            if (bo.getBytes() == 0) {
                return "false";
            }
            return "true";
        case STRING:
            final ConstantUtf8 cu8 = (ConstantUtf8) cpool.getConstant(getIndex(),
                    Const.CONSTANT_Utf8);
            return cu8.getBytes();
        default:
            throw new RuntimeException("SimpleElementValue class does not know how to stringify type " + _type);
        }
    }
}
