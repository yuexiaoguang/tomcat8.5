package org.apache.jasper.compiler;

import org.apache.jasper.JspCompilationContext;

/**
 * 定义表达式语言解释器的接口. 这允许用户提供自定义EL解释器实现，可以优化应用程序的EL处理 , 例如, 为简单表达式执行代码生成.
 */
public interface ELInterpreter {

    /**
     * 返回表示将插入为JSP生成的servlet的代码的字符串.
     * 默认实现创建一个调用
     * {@link org.apache.jasper.runtime.PageContextImpl#proprietaryEvaluate(
     * String, Class, javax.servlet.jsp.PageContext, org.apache.jasper.runtime.ProtectedFunctionMapper)},
     * 但其他实现可能会产生更优化的代码.
     * 
     * @param context 编译上下文
     * @param isTagFile <code>true</code>如果在标签文件而不是JSP中
     * @param expression 包含零个或多个 "${}" 表达式的字符串
     * @param expectedType 解释结果的预期类型
     * @param fnmapvar 指向函数映射的变量.
     * 
     * @return 表示对EL解释器的调用的String.
     */
    public String interpreterCall(JspCompilationContext context,
            boolean isTagFile, String expression,
            Class<?> expectedType, String fnmapvar);
}
