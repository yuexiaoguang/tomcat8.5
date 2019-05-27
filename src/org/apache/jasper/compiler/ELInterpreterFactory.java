package org.apache.jasper.compiler;

import javax.servlet.ServletContext;

import org.apache.jasper.JspCompilationContext;

/**
 * 为JSP编译提供一个 {@link ELInterpreter} 实例.
 *
 * 按以下顺序搜索:
 * <ol>
 * <li>ELInterpreter 实例或实现类类名, 作为一个 ServletContext 属性提供</li>
 * <li>ServletContext 初始化参数中的实现类类名</li>
 * <li>默认实现类</li>
 * </ol>
 */
public class ELInterpreterFactory {

    public static final String EL_INTERPRETER_CLASS_NAME =
            ELInterpreter.class.getName();

    private static final ELInterpreter DEFAULT_INSTANCE =
            new DefaultELInterpreter();


    /**
     * 获取给定Web应用程序的正确的EL解释器.
     * 
     * @param context Servlet 上下文
     * @return EL 解释器
     * @throws Exception 如果创建解释器时发生错误
     */
    public static ELInterpreter getELInterpreter(ServletContext context)
            throws Exception {

        ELInterpreter result = null;

        // 搜索一个实现
        // 1. ServletContext 属性 (由应用程序设置或由先前调用此方法缓存).
        Object attribute = context.getAttribute(EL_INTERPRETER_CLASS_NAME);
        if (attribute instanceof ELInterpreter) {
            return (ELInterpreter) attribute;
        } else if (attribute instanceof String) {
            result = createInstance(context, (String) attribute);
        }

        // 2. ServletContext 初始化参数
        if (result == null) {
            String className =
                    context.getInitParameter(EL_INTERPRETER_CLASS_NAME);
            if (className != null) {
                result = createInstance(context, className);
            }
        }

        // 3. Default
        if (result == null) {
            result = DEFAULT_INSTANCE;
        }

        // 缓存
        context.setAttribute(EL_INTERPRETER_CLASS_NAME, result);
        return result;
    }


    private static ELInterpreter createInstance(ServletContext context,
            String className) throws Exception {
        return (ELInterpreter) context.getClassLoader().loadClass(
                    className).getConstructor().newInstance();
    }


    private ELInterpreterFactory() {
        // Utility class. Hide default constructor.
    }


    public static class DefaultELInterpreter implements ELInterpreter {

        @Override
        public String interpreterCall(JspCompilationContext context,
                boolean isTagFile, String expression,
                Class<?> expectedType, String fnmapvar) {
            return JspUtil.interpreterCall(isTagFile, expression, expectedType,
                    fnmapvar);
        }
    }
}
