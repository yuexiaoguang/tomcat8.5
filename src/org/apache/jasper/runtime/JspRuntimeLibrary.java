package org.apache.jasper.runtime;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.util.Enumeration;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.BodyContent;
import javax.servlet.jsp.tagext.BodyTag;
import javax.servlet.jsp.tagext.Tag;

import org.apache.jasper.JasperException;
import org.apache.jasper.compiler.Localizer;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;

/**
 * 为useBean, getProperty, setProperty生成的代码使用的工具方法.
 *
 * __begin, __end 如果不想在这个类上运行时依赖，JSP引擎就可以实际解析这个文件并内联它们. 但是, 我不知道现在是否有效.
 */
public class JspRuntimeLibrary {

    private static final Log log = LogFactory.getLog(JspRuntimeLibrary.class);

    /**
     * 返回javax.servlet.error.exception 请求属性值, 否则返回
     * javax.servlet.jsp.jspException 请求属性值.
     *
     * 这个方法在生成JSP错误页面的servlet代码开始的时候调用, 当"exception"初始化隐式脚本语言变量的时候.
     * 
     * @param request The Servlet request
     * @return the throwable in the error attribute if any
     */
    public static Throwable getThrowable(ServletRequest request) {
        Throwable error = (Throwable) request.getAttribute(
                RequestDispatcher.ERROR_EXCEPTION);
        if (error == null) {
            error = (Throwable) request.getAttribute(PageContext.EXCEPTION);
            if (error != null) {
                /*
                 * 唯一设置 JSP_EXCEPTION的地方 是 PageContextImpl.handlePageException(). 它真的应该设置
                 * SERVLET_EXCEPTION, 但这会影响到 ErrorReportValve. 因此, 如果JSP_EXCEPTION 被设置, 需要设置 SERVLET_EXCEPTION.
                 */
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, error);
            }
        }

        return error;
    }

    public static boolean coerceToBoolean(String s) {
        if (s == null || s.length() == 0)
            return false;
        else
            return Boolean.parseBoolean(s);
    }

    public static byte coerceToByte(String s) {
        if (s == null || s.length() == 0)
            return (byte) 0;
        else
            return Byte.parseByte(s);
    }

    public static char coerceToChar(String s) {
        if (s == null || s.length() == 0) {
            return (char) 0;
        } else {
            return s.charAt(0);
        }
    }

    public static double coerceToDouble(String s) {
        if (s == null || s.length() == 0)
            return 0;
        else
            return Double.parseDouble(s);
    }

    public static float coerceToFloat(String s) {
        if (s == null || s.length() == 0)
            return 0;
        else
            return Float.parseFloat(s);
    }

    public static int coerceToInt(String s) {
        if (s == null || s.length() == 0)
            return 0;
        else
            return Integer.parseInt(s);
    }

    public static short coerceToShort(String s) {
        if (s == null || s.length() == 0)
            return (short) 0;
        else
            return Short.parseShort(s);
    }

    public static long coerceToLong(String s) {
        if (s == null || s.length() == 0)
            return 0;
        else
            return Long.parseLong(s);
    }

    public static Object coerce(String s, Class<?> target) {

        boolean isNullOrEmpty = (s == null || s.length() == 0);

        if (target == Boolean.class) {
            if (isNullOrEmpty) {
                s = "false";
            }
            return Boolean.valueOf(s);
        } else if (target == Byte.class) {
            if (isNullOrEmpty)
                return Byte.valueOf((byte) 0);
            else
                return Byte.valueOf(s);
        } else if (target == Character.class) {
            if (isNullOrEmpty)
                return Character.valueOf((char) 0);
            else {
                @SuppressWarnings("null")
                Character result = Character.valueOf(s.charAt(0));
                return result;
            }
        } else if (target == Double.class) {
            if (isNullOrEmpty)
                return Double.valueOf(0);
            else
                return Double.valueOf(s);
        } else if (target == Float.class) {
            if (isNullOrEmpty)
                return Float.valueOf(0);
            else
                return Float.valueOf(s);
        } else if (target == Integer.class) {
            if (isNullOrEmpty)
                return Integer.valueOf(0);
            else
                return Integer.valueOf(s);
        } else if (target == Short.class) {
            if (isNullOrEmpty)
                return Short.valueOf((short) 0);
            else
                return Short.valueOf(s);
        } else if (target == Long.class) {
            if (isNullOrEmpty)
                return Long.valueOf(0);
            else
                return Long.valueOf(s);
        } else {
            return null;
        }
    }

    public static Object convert(String propertyName, String s, Class<?> t,
            Class<?> propertyEditorClass)
       throws JasperException
    {
        try {
            if (s == null) {
                if (t.equals(Boolean.class) || t.equals(Boolean.TYPE))
                    s = "false";
                else
                    return null;
            }
            if (propertyEditorClass != null) {
                return getValueFromBeanInfoPropertyEditor(
                                    t, propertyName, s, propertyEditorClass);
            } else if ( t.equals(Boolean.class) || t.equals(Boolean.TYPE) ) {
                if (s.equalsIgnoreCase("on") || s.equalsIgnoreCase("true"))
                    s = "true";
                else
                    s = "false";
                return Boolean.valueOf(s);
            } else if ( t.equals(Byte.class) || t.equals(Byte.TYPE) ) {
                return Byte.valueOf(s);
            } else if (t.equals(Character.class) || t.equals(Character.TYPE)) {
                return s.length() > 0 ? Character.valueOf(s.charAt(0)) : null;
            } else if ( t.equals(Short.class) || t.equals(Short.TYPE) ) {
                return Short.valueOf(s);
            } else if ( t.equals(Integer.class) || t.equals(Integer.TYPE) ) {
                return Integer.valueOf(s);
            } else if ( t.equals(Float.class) || t.equals(Float.TYPE) ) {
                return Float.valueOf(s);
            } else if ( t.equals(Long.class) || t.equals(Long.TYPE) ) {
                return Long.valueOf(s);
            } else if ( t.equals(Double.class) || t.equals(Double.TYPE) ) {
                return Double.valueOf(s);
            } else if ( t.equals(String.class) ) {
                return s;
            } else if ( t.equals(java.io.File.class) ) {
                return new java.io.File(s);
            } else if (t.getName().equals("java.lang.Object")) {
                return new Object[] {s};
            } else {
                return getValueFromPropertyEditorManager(
                                            t, propertyName, s);
            }
        } catch (Exception ex) {
            throw new JasperException(ex);
        }
    }

    public static void introspect(Object bean, ServletRequest request)
                                  throws JasperException
    {
        Enumeration<String> e = request.getParameterNames();
        while ( e.hasMoreElements() ) {
            String name  = e.nextElement();
            String value = request.getParameter(name);
            introspecthelper(bean, name, value, request, name, true);
        }
    }

    public static void introspecthelper(Object bean, String prop,
                                        String value, ServletRequest request,
                                        String param, boolean ignoreMethodNF)
                                        throws JasperException {
        Method method = null;
        Class<?> type = null;
        Class<?> propertyEditorClass = null;
        try {
            java.beans.BeanInfo info
                = java.beans.Introspector.getBeanInfo(bean.getClass());
            if ( info != null ) {
                java.beans.PropertyDescriptor pd[]
                    = info.getPropertyDescriptors();
                for (int i = 0 ; i < pd.length ; i++) {
                    if ( pd[i].getName().equals(prop) ) {
                        method = pd[i].getWriteMethod();
                        type   = pd[i].getPropertyType();
                        propertyEditorClass = pd[i].getPropertyEditorClass();
                        break;
                    }
                }
            }
            if (method != null && type != null) {
                if (type.isArray()) {
                    if (request == null) {
                        throw new JasperException(
                            Localizer.getMessage("jsp.error.beans.setproperty.noindexset"));
                    }
                    Class<?> t = type.getComponentType();
                    String[] values = request.getParameterValues(param);
                    //XXX Please check.
                    if(values == null) return;
                    if(t.equals(String.class)) {
                        method.invoke(bean, new Object[] { values });
                    } else {
                        createTypedArray (prop, bean, method, values, t,
                                          propertyEditorClass);
                    }
                } else {
                    if(value == null || (param != null && value.equals(""))) return;
                    Object oval = convert(prop, value, type, propertyEditorClass);
                    if ( oval != null )
                        method.invoke(bean, new Object[] { oval });
                }
            }
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
        if (!ignoreMethodNF && (method == null)) {
            if (type == null) {
                throw new JasperException(
                    Localizer.getMessage("jsp.error.beans.noproperty",
                                         prop,
                                         bean.getClass().getName()));
            } else {
                throw new JasperException(
                    Localizer.getMessage("jsp.error.beans.nomethod.setproperty",
                                         prop,
                                         type.getName(),
                                         bean.getClass().getName()));
            }
        }
    }

    //-------------------------------------------------------------------
    // functions to convert builtin Java data types to string.
    //-------------------------------------------------------------------
    public static String toString(Object o) {
        return String.valueOf(o);
    }

    public static String toString(byte b) {
        return Byte.toString(b);
    }

    public static String toString(boolean b) {
        return Boolean.toString(b);
    }

    public static String toString(short s) {
        return Short.toString(s);
    }

    public static String toString(int i) {
        return Integer.toString(i);
    }

    public static String toString(float f) {
        return Float.toString(f);
    }

    public static String toString(long l) {
        return Long.toString(l);
    }

    public static String toString(double d) {
        return Double.toString(d);
    }

    public static String toString(char c) {
        return Character.toString(c);
    }


    /**
     * 创建类型数组.
     * 这是一个特殊的情况，通过请求和属性传递的参数被索引.
     * 
     * @param propertyName The property name
     * @param bean The bean
     * @param method The method
     * @param values Array values
     * @param t The class
     * @param propertyEditorClass The editor for the property
     * 
     * @throws JasperException An error occurred
     */
    public static void createTypedArray(String propertyName,
                                        Object bean,
                                        Method method,
                                        String[] values,
                                        Class<?> t,
                                        Class<?> propertyEditorClass)
                throws JasperException {

        try {
            if (propertyEditorClass != null) {
                Object[] tmpval = new Integer[values.length];
                for (int i=0; i<values.length; i++) {
                    tmpval[i] = getValueFromBeanInfoPropertyEditor(
                            t, propertyName, values[i], propertyEditorClass);
                }
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(Integer.class)) {
                Integer []tmpval = new Integer[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] =  Integer.valueOf(values[i]);
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(Byte.class)) {
                Byte[] tmpval = new Byte[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = Byte.valueOf(values[i]);
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(Boolean.class)) {
                Boolean[] tmpval = new Boolean[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = Boolean.valueOf(values[i]);
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(Short.class)) {
                Short[] tmpval = new Short[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = Short.valueOf(values[i]);
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(Long.class)) {
                Long[] tmpval = new Long[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = Long.valueOf(values[i]);
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(Double.class)) {
                Double[] tmpval = new Double[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = Double.valueOf(values[i]);
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(Float.class)) {
                Float[] tmpval = new Float[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = Float.valueOf(values[i]);
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(Character.class)) {
                Character[] tmpval = new Character[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = Character.valueOf(values[i].charAt(0));
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(int.class)) {
                int []tmpval = new int[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = Integer.parseInt (values[i]);
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(byte.class)) {
                byte[] tmpval = new byte[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = Byte.parseByte (values[i]);
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(boolean.class)) {
                boolean[] tmpval = new boolean[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = Boolean.parseBoolean(values[i]);
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(short.class)) {
                short[] tmpval = new short[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = Short.parseShort (values[i]);
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(long.class)) {
                long[] tmpval = new long[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = Long.parseLong (values[i]);
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(double.class)) {
                double[] tmpval = new double[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = Double.parseDouble(values[i]);
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(float.class)) {
                float[] tmpval = new float[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = Float.parseFloat(values[i]);
                method.invoke (bean, new Object[] {tmpval});
            } else if (t.equals(char.class)) {
                char[] tmpval = new char[values.length];
                for (int i = 0 ; i < values.length; i++)
                    tmpval[i] = values[i].charAt(0);
                method.invoke (bean, new Object[] {tmpval});
            } else {
                Object[] tmpval = new Integer[values.length];
                for (int i=0; i<values.length; i++) {
                    tmpval[i] =
                        getValueFromPropertyEditorManager(
                                            t, propertyName, values[i]);
                }
                method.invoke (bean, new Object[] {tmpval});
            }
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException ("error in invoking method", ex);
        }
    }

    /**
     * 转义特殊的shell 字符.
     * @param unescString 要转义的shell字符串
     * @return The escaped shell string.
     */
    public static String escapeQueryString(String unescString) {
        if (unescString == null) {
            return null;
        }

        StringBuilder escStringBuilder = new StringBuilder();
        String shellSpChars = "&;`'\"|*?~<>^()[]{}$\\\n";

        for (int index = 0; index < unescString.length(); index++) {
            char nextChar = unescString.charAt(index);

            if (shellSpChars.indexOf(nextChar) != -1) {
                escStringBuilder.append('\\');
            }

            escStringBuilder.append(nextChar);
        }
        return escStringBuilder.toString();
    }

    public static Object handleGetProperty(Object o, String prop)
    throws JasperException {
        if (o == null) {
            throw new JasperException(
                    Localizer.getMessage("jsp.error.beans.nullbean"));
        }
        Object value = null;
        try {
            Method method = getReadMethod(o.getClass(), prop);
            value = method.invoke(o, (Object[]) null);
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException (ex);
        }
        return value;
    }

    // handles <jsp:setProperty> with EL expression for 'value' attribute
    public static void handleSetPropertyExpression(Object bean,
        String prop, String expression, PageContext pageContext,
        ProtectedFunctionMapper functionMapper )
        throws JasperException
    {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, new Object[] {
                PageContextImpl.proprietaryEvaluate(
                    expression,
                    method.getParameterTypes()[0],
                    pageContext,
                    functionMapper)
            });
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop,
                                         Object value)
        throws JasperException
    {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, new Object[] { value });
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop,
                                         int value)
        throws JasperException
    {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, new Object[] { Integer.valueOf(value) });
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop,
                                         short value)
        throws JasperException
    {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, new Object[] { Short.valueOf(value) });
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop,
                                         long value)
        throws JasperException
    {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, new Object[] { Long.valueOf(value) });
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop,
                                         double value)
        throws JasperException
    {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, new Object[] { Double.valueOf(value) });
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop,
                                         float value)
        throws JasperException
    {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, new Object[] { Float.valueOf(value) });
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop,
                                         char value)
        throws JasperException
    {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, new Object[] { Character.valueOf(value) });
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop,
                                         byte value)
        throws JasperException
    {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, new Object[] { Byte.valueOf(value) });
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop,
                                         boolean value)
        throws JasperException
    {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, new Object[] { Boolean.valueOf(value) });
        } catch (Exception ex) {
            Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
            ExceptionUtils.handleThrowable(thr);
            throw new JasperException(ex);
        }
    }

    public static Method getWriteMethod(Class<?> beanClass, String prop)
    throws JasperException {
        Method method = null;
        Class<?> type = null;
        try {
            java.beans.BeanInfo info
                = java.beans.Introspector.getBeanInfo(beanClass);
            if ( info != null ) {
                java.beans.PropertyDescriptor pd[]
                    = info.getPropertyDescriptors();
                for (int i = 0 ; i < pd.length ; i++) {
                    if ( pd[i].getName().equals(prop) ) {
                        method = pd[i].getWriteMethod();
                        type   = pd[i].getPropertyType();
                        break;
                    }
                }
            } else {
                // just in case introspection silently fails.
                throw new JasperException(
                    Localizer.getMessage("jsp.error.beans.nobeaninfo",
                                         beanClass.getName()));
            }
        } catch (Exception ex) {
            throw new JasperException (ex);
        }
        if (method == null) {
            if (type == null) {
                throw new JasperException(
                        Localizer.getMessage("jsp.error.beans.noproperty",
                                             prop,
                                             beanClass.getName()));
            } else {
                throw new JasperException(
                    Localizer.getMessage("jsp.error.beans.nomethod.setproperty",
                                         prop,
                                         type.getName(),
                                         beanClass.getName()));
            }
        }
        return method;
    }

    public static Method getReadMethod(Class<?> beanClass, String prop)
            throws JasperException {

        Method method = null;
        Class<?> type = null;
        try {
            java.beans.BeanInfo info
                = java.beans.Introspector.getBeanInfo(beanClass);
            if ( info != null ) {
                java.beans.PropertyDescriptor pd[]
                    = info.getPropertyDescriptors();
                for (int i = 0 ; i < pd.length ; i++) {
                    if ( pd[i].getName().equals(prop) ) {
                        method = pd[i].getReadMethod();
                        type   = pd[i].getPropertyType();
                        break;
                    }
                }
            } else {
                // just in case introspection silently fails.
                throw new JasperException(
                    Localizer.getMessage("jsp.error.beans.nobeaninfo",
                                         beanClass.getName()));
            }
        } catch (Exception ex) {
            throw new JasperException (ex);
        }
        if (method == null) {
            if (type == null) {
                throw new JasperException(
                    Localizer.getMessage("jsp.error.beans.noproperty", prop,
                                         beanClass.getName()));
            } else {
                throw new JasperException(
                    Localizer.getMessage("jsp.error.beans.nomethod", prop,
                                         beanClass.getName()));
            }
        }

        return method;
    }

    //*********************************************************************
    // PropertyEditor Support

    public static Object getValueFromBeanInfoPropertyEditor(
                           Class<?> attrClass, String attrName, String attrValue,
                           Class<?> propertyEditorClass)
        throws JasperException
    {
        try {
            PropertyEditor pe = (PropertyEditor)propertyEditorClass.getConstructor().newInstance();
            pe.setAsText(attrValue);
            return pe.getValue();
        } catch (Exception ex) {
            throw new JasperException(
                Localizer.getMessage("jsp.error.beans.property.conversion",
                                     attrValue, attrClass.getName(), attrName,
                                     ex.getMessage()));
        }
    }

    public static Object getValueFromPropertyEditorManager(
                     Class<?> attrClass, String attrName, String attrValue)
        throws JasperException
    {
        try {
            PropertyEditor propEditor =
                PropertyEditorManager.findEditor(attrClass);
            if (propEditor != null) {
                propEditor.setAsText(attrValue);
                return propEditor.getValue();
            } else {
                throw new IllegalArgumentException(
                    Localizer.getMessage("jsp.error.beans.propertyeditor.notregistered"));
            }
        } catch (IllegalArgumentException ex) {
            throw new JasperException(
                Localizer.getMessage("jsp.error.beans.property.conversion",
                                     attrValue, attrClass.getName(), attrName,
                                     ex.getMessage()));
        }
    }


    // ************************************************************************
    // General Purpose Runtime Methods
    // ************************************************************************


    /**
     * 将可能的相对资源路径转换为相对上下文的资源路径, 以'/'开头.
     *
     * @param request 要处理的servlet 请求
     * @param relativePath 可能相对于资源路径
     * @return an absolute path
     */
    public static String getContextRelativePath(ServletRequest request,
                                                String relativePath) {

        if (relativePath.startsWith("/"))
            return (relativePath);
        if (!(request instanceof HttpServletRequest))
            return (relativePath);
        HttpServletRequest hrequest = (HttpServletRequest) request;
        String uri = (String) request.getAttribute(
                RequestDispatcher.INCLUDE_SERVLET_PATH);
        if (uri != null) {
            String pathInfo = (String)
                request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
            if (pathInfo == null) {
                if (uri.lastIndexOf('/') >= 0)
                    uri = uri.substring(0, uri.lastIndexOf('/'));
            }
        }
        else {
            uri = hrequest.getServletPath();
            if (uri.lastIndexOf('/') >= 0)
                uri = uri.substring(0, uri.lastIndexOf('/'));
        }
        return uri + '/' + relativePath;

    }


    /**
     * 执行一个 RequestDispatcher.include() 操作, 预先选择响应刷新.
     *
     * @param request 要处理的servlet 请求
     * @param response 要处理的servlet响应
     * @param relativePath 包含的资源的相对路径
     * @param out 要写入的Writer
     * @param flush 是否在包含处理之前刷新?
     *
     * @exception IOException 如果包含的servlet抛出
     * @exception ServletException 如果包含的servlet抛出
     */
    public static void include(ServletRequest request,
                               ServletResponse response,
                               String relativePath,
                               JspWriter out,
                               boolean flush)
        throws IOException, ServletException {

        if (flush && !(out instanceof BodyContent))
            out.flush();

        // FIXME - 尝试使用request.getRequestDispatcher() 直接解析相对路径, 但是Catalina 目前不考虑调用者是否是在一个RequestDispatcher中.
        // Catalina 是否应该考虑*是目前正在审查的规范问题吗. 与此同时, 复制Jasper之前的行为

        String resourcePath = getContextRelativePath(request, relativePath);
        RequestDispatcher rd = request.getRequestDispatcher(resourcePath);

        rd.include(request, new ServletResponseWrapperInclude(response, out));
    }

    /**
     * URL 编码, 基于所提供的字符编码.
     * 执行和J2SDK1.4中的java.next.URLEncode.encode相同的函数, 如果唯一支持的平台是 1.4 或更高版本, 应该删除.
     * 
     * @param s 要URL 编码的字符串.
     * @param enc 字符编码
     * @return The URL encoded String
     */
    public static String URLEncode(String s, String enc) {

        if (s == null) {
            return "null";
        }

        if (enc == null) {
            enc = "ISO-8859-1";        // The default request encoding
        }

        StringBuilder out = new StringBuilder(s.length());
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(buf, enc);
        } catch (java.io.UnsupportedEncodingException ex) {
            // Use the default encoding?
            writer = new OutputStreamWriter(buf);
        }

        for (int i = 0; i < s.length(); i++) {
            int c = s.charAt(i);
            if (c == ' ') {
                out.append('+');
            } else if (isSafeChar(c)) {
                out.append((char)c);
            } else {
                // 十六进制转换前转换为外部编码
                try {
                    writer.write(c);
                    writer.flush();
                } catch(IOException e) {
                    buf.reset();
                    continue;
                }
                byte[] ba = buf.toByteArray();
                for (int j = 0; j < ba.length; j++) {
                    out.append('%');
                    // 在每个字节缓冲区转换
                    out.append(Character.forDigit((ba[j]>>4) & 0xf, 16));
                    out.append(Character.forDigit(ba[j] & 0xf, 16));
                }
                buf.reset();
            }
        }
        return out.toString();
    }

    private static boolean isSafeChar(int c) {
        if (c >= 'a' && c <= 'z') {
            return true;
        }
        if (c >= 'A' && c <= 'Z') {
            return true;
        }
        if (c >= '0' && c <= '9') {
            return true;
        }
        if (c == '-' || c == '_' || c == '.' || c == '!' ||
            c == '~' || c == '*' || c == '\'' || c == '(' || c == ')') {
            return true;
        }
        return false;
    }


    public static JspWriter startBufferedBody(PageContext pageContext, BodyTag tag)
            throws JspException {
        BodyContent out = pageContext.pushBody();
        tag.setBodyContent(out);
        tag.doInitBody();
        return out;
    }


    public static void releaseTag(Tag tag, InstanceManager instanceManager, boolean reused) {
        // 调用者确保池不是null，如果 reuse 是 true
        if (!reused) {
            releaseTag(tag, instanceManager);
        }
    }


    protected static void releaseTag(Tag tag, InstanceManager instanceManager) {
        try {
            tag.release();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.warn("Error processing release on tag instance of "
                    + tag.getClass().getName(), t);
        }
        try {
            instanceManager.destroyInstance(tag);
        } catch (Exception e) {
            Throwable t = ExceptionUtils.unwrapInvocationTargetException(e);
            ExceptionUtils.handleThrowable(t);
            log.warn("Error processing preDestroy on tag instance of "
                    + tag.getClass().getName(), t);
        }

    }
}
