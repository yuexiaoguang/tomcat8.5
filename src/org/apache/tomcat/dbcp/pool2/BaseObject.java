package org.apache.tomcat.dbcp.pool2;

/**
 * 通用功能的基类.
 */
public abstract class BaseObject {

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(getClass().getSimpleName());
        builder.append(" [");
        toStringAppendFields(builder);
        builder.append("]");
        return builder.toString();
    }

    /**
     * 子类用于包含由{@link #toString()}输出中的子类定义的字段.
     *
     * @param builder 字段名和值将附加到此对象
     */
    protected void toStringAppendFields(final StringBuilder builder) {
        // do nothing by default, needed for b/w compatibility.
    }
}
