package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tomcat.util.bcel.Const;

/**
 * 表示注解表中的一个注解
 */
public class AnnotationEntry {

    private final int type_index;
    private final ConstantPool constant_pool;

    private final List<ElementValuePair> element_value_pairs;

    /*
     * 从DataInputStream创建AnnotationEntry
     *
     * @param input
     * @param constant_pool
     * @throws IOException
     */
    AnnotationEntry(final DataInput input, final ConstantPool constant_pool) throws IOException {

        this.constant_pool = constant_pool;

        type_index = input.readUnsignedShort();
        final int num_element_value_pairs = input.readUnsignedShort();

        element_value_pairs = new ArrayList<>(num_element_value_pairs);
        for (int i = 0; i < num_element_value_pairs; i++) {
            element_value_pairs.add(new ElementValuePair(input, constant_pool));
        }
    }

    /**
     * @return 注解类型名
     */
    public String getAnnotationType() {
        final ConstantUtf8 c = (ConstantUtf8) constant_pool.getConstant(type_index, Const.CONSTANT_Utf8);
        return c.getBytes();
    }

    /**
     * @return 此注解条目中的元素值对
     */
    public List<ElementValuePair> getElementValuePairs() {
        return element_value_pairs;
    }
}
