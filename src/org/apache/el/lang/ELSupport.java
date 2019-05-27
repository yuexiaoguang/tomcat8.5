package org.apache.el.lang;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.el.ELContext;
import javax.el.ELException;

import org.apache.el.util.MessageFactory;


/**
 * 实现EL规范的帮助类
 */
public class ELSupport {

    private static final Long ZERO = Long.valueOf(0L);

    private static final boolean IS_SECURITY_ENABLED =
            (System.getSecurityManager() != null);

    protected static final boolean COERCE_TO_ZERO;

    static {
        String coerceToZeroStr;
        if (IS_SECURITY_ENABLED) {
            coerceToZeroStr = AccessController.doPrivileged(
                    new PrivilegedAction<String>(){
                        @Override
                        public String run() {
                            return System.getProperty(
                                    "org.apache.el.parser.COERCE_TO_ZERO",
                                    "false");
                        }

                    }
            );
        } else {
            coerceToZeroStr = System.getProperty(
                    "org.apache.el.parser.COERCE_TO_ZERO", "false");
        }
        COERCE_TO_ZERO = Boolean.parseBoolean(coerceToZeroStr);
    }


    /**
     * 比较两个对象, 在强制为相同的类型之后.
     *
     * 如果对象是相同的, 或根据{@link #equals(ELContext, Object, Object)}它们是相同的, 返回 0.
     *
     * 如果对象是一个 BigDecimal, 那么首先强制为 BigDecimal.
     * 类似的 Double(Float), BigInteger, 和 Long(Integer, Char, Short, Byte).
     *
     * 否则, 检查第一个对象是否是 Comparable 的实例, 并和第二个对象比较. 如果是 null, 返回 1, 否则返回比较的结果.
     *
     * 类似的, 如果第二个对象是 Comparable, 如果第一个是 null, 返回 -1, 否则返回比较的结果.
     *
     * null 对象:
     * <ul>
     * <li>ZERO 如果和 Number比较</li>
     * <li>空字符串, 如果和 String 比较</li>
     * <li>否则为 null.</li>
     * </ul>
     *
     * @param ctx 上下文
     * @param obj0 first object
     * @param obj1 second object
     * 
     * @return -1, 0, 或 1 如果对象小于, 等于, 或大于 val.
     * @throws ELException 如果没有对象是 Comparable
     * @throws ClassCastException 如果对象不是相互可比的
     */
    public static final int compare(final ELContext ctx, final Object obj0, final Object obj1)
            throws ELException {
        if (obj0 == obj1 || equals(ctx, obj0, obj1)) {
            return 0;
        }
        if (isBigDecimalOp(obj0, obj1)) {
            BigDecimal bd0 = (BigDecimal) coerceToNumber(ctx, obj0, BigDecimal.class);
            BigDecimal bd1 = (BigDecimal) coerceToNumber(ctx, obj1, BigDecimal.class);
            return bd0.compareTo(bd1);
        }
        if (isDoubleOp(obj0, obj1)) {
            Double d0 = (Double) coerceToNumber(ctx, obj0, Double.class);
            Double d1 = (Double) coerceToNumber(ctx, obj1, Double.class);
            return d0.compareTo(d1);
        }
        if (isBigIntegerOp(obj0, obj1)) {
            BigInteger bi0 = (BigInteger) coerceToNumber(ctx, obj0, BigInteger.class);
            BigInteger bi1 = (BigInteger) coerceToNumber(ctx, obj1, BigInteger.class);
            return bi0.compareTo(bi1);
        }
        if (isLongOp(obj0, obj1)) {
            Long l0 = (Long) coerceToNumber(ctx, obj0, Long.class);
            Long l1 = (Long) coerceToNumber(ctx, obj1, Long.class);
            return l0.compareTo(l1);
        }
        if (obj0 instanceof String || obj1 instanceof String) {
            return coerceToString(ctx, obj0).compareTo(coerceToString(ctx, obj1));
        }
        if (obj0 instanceof Comparable<?>) {
            @SuppressWarnings("unchecked") // checked above
            final Comparable<Object> comparable = (Comparable<Object>) obj0;
            return (obj1 != null) ? comparable.compareTo(obj1) : 1;
        }
        if (obj1 instanceof Comparable<?>) {
            @SuppressWarnings("unchecked") // checked above
            final Comparable<Object> comparable = (Comparable<Object>) obj1;
            return (obj0 != null) ? -comparable.compareTo(obj0) : -1;
        }
        throw new ELException(MessageFactory.get("error.compare", obj0, obj1));
    }

    /**
     * 比较两个对象, 在强制为相同的类型之后.
     *
     * 如果对象是相同的 (包括都是 null) 返回 true.
     * 如果只有一个对象是 null, 返回 false.
     * 如果一个对象是 Boolean, 都强制为 Boolean 并检查相等.
     * 类似的 Enum, String, BigDecimal, Double(Float), Long(Integer, Short, Byte, Character)
     * 否则默认使用 Object.equals().
     *
     * @param ctx 上下文
     * @param obj0 the first object
     * @param obj1 the second object
     * 
     * @return true 如果对象相等
     * @throws ELException 如果其中一个强制失败
     */
    public static final boolean equals(final ELContext ctx, final Object obj0, final Object obj1)
            throws ELException {
        if (obj0 == obj1) {
            return true;
        } else if (obj0 == null || obj1 == null) {
            return false;
        } else if (isBigDecimalOp(obj0, obj1)) {
            BigDecimal bd0 = (BigDecimal) coerceToNumber(ctx, obj0, BigDecimal.class);
            BigDecimal bd1 = (BigDecimal) coerceToNumber(ctx, obj1, BigDecimal.class);
            return bd0.equals(bd1);
        } else if (isDoubleOp(obj0, obj1)) {
            Double d0 = (Double) coerceToNumber(ctx, obj0, Double.class);
            Double d1 = (Double) coerceToNumber(ctx, obj1, Double.class);
            return d0.equals(d1);
        } else if (isBigIntegerOp(obj0, obj1)) {
            BigInteger bi0 = (BigInteger) coerceToNumber(ctx, obj0, BigInteger.class);
            BigInteger bi1 = (BigInteger) coerceToNumber(ctx, obj1, BigInteger.class);
            return bi0.equals(bi1);
        } else         if (isLongOp(obj0, obj1)) {
            Long l0 = (Long) coerceToNumber(ctx, obj0, Long.class);
            Long l1 = (Long) coerceToNumber(ctx, obj1, Long.class);
            return l0.equals(l1);
        } else if (obj0 instanceof Boolean || obj1 instanceof Boolean) {
            return coerceToBoolean(ctx, obj0, false).equals(coerceToBoolean(ctx, obj1, false));
        } else if (obj0.getClass().isEnum()) {
            return obj0.equals(coerceToEnum(ctx, obj1, obj0.getClass()));
        } else if (obj1.getClass().isEnum()) {
            return obj1.equals(coerceToEnum(ctx, obj0, obj1.getClass()));
        } else if (obj0 instanceof String || obj1 instanceof String) {
            int lexCompare = coerceToString(ctx, obj0).compareTo(coerceToString(ctx, obj1));
            return (lexCompare == 0) ? true : false;
        } else {
            return obj0.equals(obj1);
        }
    }

    @SuppressWarnings("unchecked")
    public static final Enum<?> coerceToEnum(final ELContext ctx, final Object obj,
            @SuppressWarnings("rawtypes") Class type) {

        if (ctx != null) {
            boolean originalIsPropertyResolved = ctx.isPropertyResolved();
            try {
                Object result = ctx.getELResolver().convertToType(ctx, obj, type);
                if (ctx.isPropertyResolved()) {
                    return (Enum<?>) result;
                }
            } finally {
                ctx.setPropertyResolved(originalIsPropertyResolved);
            }
        }

        if (obj == null || "".equals(obj)) {
            return null;
        }
        if (type.isAssignableFrom(obj.getClass())) {
            return (Enum<?>) obj;
        }

        if (!(obj instanceof String)) {
            throw new ELException(MessageFactory.get("error.convert",
                    obj, obj.getClass(), type));
        }

        Enum<?> result;
        try {
             result = Enum.valueOf(type, (String) obj);
        } catch (IllegalArgumentException iae) {
            throw new ELException(MessageFactory.get("error.convert",
                    obj, obj.getClass(), type));
        }
        return result;
    }

    /**
     * 转换一个对象为 Boolean. Null 和空字符串是 false.
     * @param ctx 上下文
     * @param obj 要转换的对象
     * @param primitive 目标是否是原始的, 在这种情况下 null 是不允许的
     * 
     * @return 对象的 Boolean 值
     * @throws ELException 如果对象不是 Boolean 或 String
     */
    public static final Boolean coerceToBoolean(final ELContext ctx, final Object obj,
            boolean primitive) throws ELException {

        if (ctx != null) {
            boolean originalIsPropertyResolved = ctx.isPropertyResolved();
            try {
                Object result = ctx.getELResolver().convertToType(ctx, obj, Boolean.class);
                if (ctx.isPropertyResolved()) {
                    return (Boolean) result;
                }
            } finally {
                ctx.setPropertyResolved(originalIsPropertyResolved);
            }
        }

        if (!COERCE_TO_ZERO && !primitive) {
            if (obj == null) {
                return null;
            }
        }

        if (obj == null || "".equals(obj)) {
            return Boolean.FALSE;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (obj instanceof String) {
            return Boolean.valueOf((String) obj);
        }

        throw new ELException(MessageFactory.get("error.convert",
                obj, obj.getClass(), Boolean.class));
    }

    private static final Character coerceToCharacter(final ELContext ctx, final Object obj)
            throws ELException {

        if (ctx != null) {
            boolean originalIsPropertyResolved = ctx.isPropertyResolved();
            try {
                Object result = ctx.getELResolver().convertToType(ctx, obj, Character.class);
                if (ctx.isPropertyResolved()) {
                    return (Character) result;
                }
            } finally {
                ctx.setPropertyResolved(originalIsPropertyResolved);
            }
        }

        if (obj == null || "".equals(obj)) {
            return Character.valueOf((char) 0);
        }
        if (obj instanceof String) {
            return Character.valueOf(((String) obj).charAt(0));
        }
        if (ELArithmetic.isNumber(obj)) {
            return Character.valueOf((char) ((Number) obj).shortValue());
        }
        Class<?> objType = obj.getClass();
        if (obj instanceof Character) {
            return (Character) obj;
        }

        throw new ELException(MessageFactory.get("error.convert",
                obj, objType, Character.class));
    }

    protected static final Number coerceToNumber(final Number number,
            final Class<?> type) throws ELException {
        if (Long.TYPE == type || Long.class.equals(type)) {
            return Long.valueOf(number.longValue());
        }
        if (Double.TYPE == type || Double.class.equals(type)) {
            return Double.valueOf(number.doubleValue());
        }
        if (Integer.TYPE == type || Integer.class.equals(type)) {
            return Integer.valueOf(number.intValue());
        }
        if (BigInteger.class.equals(type)) {
            if (number instanceof BigDecimal) {
                return ((BigDecimal) number).toBigInteger();
            }
            if (number instanceof BigInteger) {
                return number;
            }
            return BigInteger.valueOf(number.longValue());
        }
        if (BigDecimal.class.equals(type)) {
            if (number instanceof BigDecimal) {
                return number;
            }
            if (number instanceof BigInteger) {
                return new BigDecimal((BigInteger) number);
            }
            return new BigDecimal(number.doubleValue());
        }
        if (Byte.TYPE == type || Byte.class.equals(type)) {
            return Byte.valueOf(number.byteValue());
        }
        if (Short.TYPE == type || Short.class.equals(type)) {
            return Short.valueOf(number.shortValue());
        }
        if (Float.TYPE == type || Float.class.equals(type)) {
            return Float.valueOf(number.floatValue());
        }
        if (Number.class.equals(type)) {
            return number;
        }

        throw new ELException(MessageFactory.get("error.convert",
                number, number.getClass(), type));
    }

    public static final Number coerceToNumber(final ELContext ctx, final Object obj,
            final Class<?> type) throws ELException {

        if (ctx != null) {
            boolean originalIsPropertyResolved = ctx.isPropertyResolved();
            try {
                Object result = ctx.getELResolver().convertToType(ctx, obj, type);
                if (ctx.isPropertyResolved()) {
                    return (Number) result;
                }
            } finally {
                ctx.setPropertyResolved(originalIsPropertyResolved);
            }
        }

        if (!COERCE_TO_ZERO) {
            if (obj == null && !type.isPrimitive()) {
                return null;
            }
        }

        if (obj == null || "".equals(obj)) {
            return coerceToNumber(ZERO, type);
        }
        if (obj instanceof String) {
            return coerceToNumber((String) obj, type);
        }
        if (ELArithmetic.isNumber(obj)) {
            return coerceToNumber((Number) obj, type);
        }

        if (obj instanceof Character) {
            return coerceToNumber(Short.valueOf((short) ((Character) obj)
                    .charValue()), type);
        }

        throw new ELException(MessageFactory.get("error.convert",
                obj, obj.getClass(), type));
    }

    protected static final Number coerceToNumber(final String val,
            final Class<?> type) throws ELException {
        if (Long.TYPE == type || Long.class.equals(type)) {
            try {
                return Long.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }
        if (Integer.TYPE == type || Integer.class.equals(type)) {
            try {
                return Integer.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }
        if (Double.TYPE == type || Double.class.equals(type)) {
            try {
                return Double.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }
        if (BigInteger.class.equals(type)) {
            try {
                return new BigInteger(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }
        if (BigDecimal.class.equals(type)) {
            try {
                return new BigDecimal(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }
        if (Byte.TYPE == type || Byte.class.equals(type)) {
            try {
                return Byte.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }
        if (Short.TYPE == type || Short.class.equals(type)) {
            try {
                return Short.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }
        if (Float.TYPE == type || Float.class.equals(type)) {
            try {
                return Float.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }

        throw new ELException(MessageFactory.get("error.convert",
                val, String.class, type));
    }

    /**
     * 转换对象为一个 string.
     * @param ctx 上下文
     * @param obj 要转换的对象
     * @return 对象的 String 值
     */
    public static final String coerceToString(final ELContext ctx, final Object obj) {

        if (ctx != null) {
            boolean originalIsPropertyResolved = ctx.isPropertyResolved();
            try {
                Object result = ctx.getELResolver().convertToType(ctx, obj, String.class);
                if (ctx.isPropertyResolved()) {
                    return (String) result;
                }
            } finally {
                ctx.setPropertyResolved(originalIsPropertyResolved);
            }
        }

        if (obj == null) {
            return "";
        } else if (obj instanceof String) {
            return (String) obj;
        } else if (obj instanceof Enum<?>) {
            return ((Enum<?>) obj).name();
        } else {
            return obj.toString();
        }
    }

    public static final Object coerceToType(final ELContext ctx, final Object obj,
            final Class<?> type) throws ELException {

        if (ctx != null) {
            boolean originalIsPropertyResolved = ctx.isPropertyResolved();
            try {
                Object result = ctx.getELResolver().convertToType(ctx, obj, type);
                if (ctx.isPropertyResolved()) {
                    return result;
                }
            } finally {
                ctx.setPropertyResolved(originalIsPropertyResolved);
            }
        }

        if (type == null || Object.class.equals(type) ||
                (obj != null && type.isAssignableFrom(obj.getClass()))) {
            return obj;
        }

        if (!COERCE_TO_ZERO) {
            if (obj == null && !type.isPrimitive() &&
                    !String.class.isAssignableFrom(type)) {
                return null;
            }
        }

        if (String.class.equals(type)) {
            return coerceToString(ctx, obj);
        }
        if (ELArithmetic.isNumberType(type)) {
            return coerceToNumber(ctx, obj, type);
        }
        if (Character.class.equals(type) || Character.TYPE == type) {
            return coerceToCharacter(ctx, obj);
        }
        if (Boolean.class.equals(type) || Boolean.TYPE == type) {
            return coerceToBoolean(ctx, obj, Boolean.TYPE == type);
        }
        if (type.isEnum()) {
            return coerceToEnum(ctx, obj, type);
        }

        // new to spec
        if (obj == null)
            return null;
        if (obj instanceof String) {
            PropertyEditor editor = PropertyEditorManager.findEditor(type);
            if (editor == null) {
                if ("".equals(obj)) {
                    return null;
                }
                throw new ELException(MessageFactory.get("error.convert", obj,
                        obj.getClass(), type));
            } else {
                try {
                    editor.setAsText((String) obj);
                    return editor.getValue();
                } catch (RuntimeException e) {
                    if ("".equals(obj)) {
                        return null;
                    }
                    throw new ELException(MessageFactory.get("error.convert",
                            obj, obj.getClass(), type), e);
                }
            }
        }

        // 处理特殊情况, 因为空set的语法和空map的语法一样. 解析器总是将 {} 解析为空 set.
        if (obj instanceof Set && type == Map.class &&
                ((Set<?>) obj).isEmpty()) {
            return Collections.EMPTY_MAP;
        }

        // Handle arrays
        if (type.isArray() && obj.getClass().isArray()) {
            return coerceToArray(ctx, obj, type);
        }

        throw new ELException(MessageFactory.get("error.convert",
                obj, obj.getClass(), type));
    }

    private static Object coerceToArray(final ELContext ctx, final Object obj,
            final Class<?> type) {
        // Note: 嵌套数组将导致对该方法的嵌套调用.

        // Note: 调用方法已经检查了 obj 是一个数组.

        int size = Array.getLength(obj);
        // 获取数组元素的目标类型
        Class<?> componentType = type.getComponentType();
        // 创建对应类型的新的数组
        Object result = Array.newInstance(componentType, size);
        // 强转每个元素.
        for (int i = 0; i < size; i++) {
            Array.set(result, i, coerceToType(ctx, Array.get(obj, i), componentType));
        }

        return result;
    }

    public static final boolean isBigDecimalOp(final Object obj0,
            final Object obj1) {
        return (obj0 instanceof BigDecimal || obj1 instanceof BigDecimal);
    }

    public static final boolean isBigIntegerOp(final Object obj0,
            final Object obj1) {
        return (obj0 instanceof BigInteger || obj1 instanceof BigInteger);
    }

    public static final boolean isDoubleOp(final Object obj0, final Object obj1) {
        return (obj0 instanceof Double
                || obj1 instanceof Double
                || obj0 instanceof Float
                || obj1 instanceof Float);
    }

    public static final boolean isLongOp(final Object obj0, final Object obj1) {
        return (obj0 instanceof Long
                || obj1 instanceof Long
                || obj0 instanceof Integer
                || obj1 instanceof Integer
                || obj0 instanceof Character
                || obj1 instanceof Character
                || obj0 instanceof Short
                || obj1 instanceof Short
                || obj0 instanceof Byte
                || obj1 instanceof Byte);
    }

    public static final boolean isStringFloat(final String str) {
        int len = str.length();
        if (len > 1) {
            for (int i = 0; i < len; i++) {
                switch (str.charAt(i)) {
                case 'E':
                    return true;
                case 'e':
                    return true;
                case '.':
                    return true;
                }
            }
        }
        return false;
    }

    public ELSupport() {
        super();
    }

}
