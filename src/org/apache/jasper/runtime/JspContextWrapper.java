package org.apache.jasper.runtime;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.EvaluationListener;
import javax.el.FunctionMapper;
import javax.el.ImportHandler;
import javax.el.VariableMapper;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.JspApplicationContext;
import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspFactory;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.BodyContent;
import javax.servlet.jsp.tagext.JspTag;
import javax.servlet.jsp.tagext.VariableInfo;

import org.apache.jasper.compiler.Localizer;

/**
 * JSP Context Wrapper的实现.
 *
 * JSP Context Wrapper是一个创建的 JspContext, 并由标签处理程序实现来维护.
 * 它包装了JSP Context的调用, JspContext 实例传递给标签处理程序通过调用 setJspContext().
 */
@SuppressWarnings("deprecation") // Have to support old JSP EL API
public class JspContextWrapper extends PageContext {

    private final JspTag jspTag;

    // 调用JSP上下文
    private final PageContext invokingJspCtxt;

    private final transient HashMap<String, Object> pageAttributes;

    // NESTED 脚本变量
    private final ArrayList<String> nestedVars;

    // AT_BEGIN 脚本变量
    private final ArrayList<String> atBeginVars;

    // AT_END 脚本变量
    private final ArrayList<String> atEndVars;

    private final Map<String,String> aliases;

    private final HashMap<String, Object> originalNestedVars;

    private ServletContext servletContext = null;

    private ELContext elContext = null;

    private final PageContext rootJspCtxt;

    public JspContextWrapper(JspTag jspTag, JspContext jspContext,
            ArrayList<String> nestedVars, ArrayList<String> atBeginVars,
            ArrayList<String> atEndVars, Map<String,String> aliases) {
        this.jspTag = jspTag;
        this.invokingJspCtxt = (PageContext) jspContext;
        if (jspContext instanceof JspContextWrapper) {
            rootJspCtxt = ((JspContextWrapper)jspContext).rootJspCtxt;
        }
        else {
            rootJspCtxt = invokingJspCtxt;
        }
        this.nestedVars = nestedVars;
        this.atBeginVars = atBeginVars;
        this.atEndVars = atEndVars;
        this.pageAttributes = new HashMap<>(16);
        this.aliases = aliases;

        if (nestedVars != null) {
            this.originalNestedVars = new HashMap<>(nestedVars.size());
        } else {
            this.originalNestedVars = null;
        }
        syncBeginTagFile();
    }

    @Override
    public void initialize(Servlet servlet, ServletRequest request,
            ServletResponse response, String errorPageURL,
            boolean needsSession, int bufferSize, boolean autoFlush)
            throws IOException, IllegalStateException, IllegalArgumentException {
    }

    @Override
    public Object getAttribute(String name) {

        if (name == null) {
            throw new NullPointerException(Localizer
                    .getMessage("jsp.error.attribute.null_name"));
        }

        return pageAttributes.get(name);
    }

    @Override
    public Object getAttribute(String name, int scope) {

        if (name == null) {
            throw new NullPointerException(Localizer
                    .getMessage("jsp.error.attribute.null_name"));
        }

        if (scope == PAGE_SCOPE) {
            return pageAttributes.get(name);
        }

        return rootJspCtxt.getAttribute(name, scope);
    }

    @Override
    public void setAttribute(String name, Object value) {

        if (name == null) {
            throw new NullPointerException(Localizer
                    .getMessage("jsp.error.attribute.null_name"));
        }

        if (value != null) {
            pageAttributes.put(name, value);
        } else {
            removeAttribute(name, PAGE_SCOPE);
        }
    }

    @Override
    public void setAttribute(String name, Object value, int scope) {

        if (name == null) {
            throw new NullPointerException(Localizer
                    .getMessage("jsp.error.attribute.null_name"));
        }

        if (scope == PAGE_SCOPE) {
            if (value != null) {
                pageAttributes.put(name, value);
            } else {
                removeAttribute(name, PAGE_SCOPE);
            }
        } else {
            rootJspCtxt.setAttribute(name, value, scope);
        }
    }

    @Override
    public Object findAttribute(String name) {

        if (name == null) {
            throw new NullPointerException(Localizer
                    .getMessage("jsp.error.attribute.null_name"));
        }

        Object o = pageAttributes.get(name);
        if (o == null) {
            o = rootJspCtxt.getAttribute(name, REQUEST_SCOPE);
            if (o == null) {
                if (getSession() != null) {
                    o = rootJspCtxt.getAttribute(name, SESSION_SCOPE);
                }
                if (o == null) {
                    o = rootJspCtxt.getAttribute(name, APPLICATION_SCOPE);
                }
            }
        }

        return o;
    }

    @Override
    public void removeAttribute(String name) {

        if (name == null) {
            throw new NullPointerException(Localizer
                    .getMessage("jsp.error.attribute.null_name"));
        }

        pageAttributes.remove(name);
        rootJspCtxt.removeAttribute(name, REQUEST_SCOPE);
        if (getSession() != null) {
            rootJspCtxt.removeAttribute(name, SESSION_SCOPE);
        }
        rootJspCtxt.removeAttribute(name, APPLICATION_SCOPE);
    }

    @Override
    public void removeAttribute(String name, int scope) {

        if (name == null) {
            throw new NullPointerException(Localizer
                    .getMessage("jsp.error.attribute.null_name"));
        }

        if (scope == PAGE_SCOPE) {
            pageAttributes.remove(name);
        } else {
            rootJspCtxt.removeAttribute(name, scope);
        }
    }

    @Override
    public int getAttributesScope(String name) {

        if (name == null) {
            throw new NullPointerException(Localizer
                    .getMessage("jsp.error.attribute.null_name"));
        }

        if (pageAttributes.get(name) != null) {
            return PAGE_SCOPE;
        } else {
            return rootJspCtxt.getAttributesScope(name);
        }
    }

    @Override
    public Enumeration<String> getAttributeNamesInScope(int scope) {
        if (scope == PAGE_SCOPE) {
            return Collections.enumeration(pageAttributes.keySet());
        }

        return rootJspCtxt.getAttributeNamesInScope(scope);
    }

    @Override
    public void release() {
        invokingJspCtxt.release();
    }

    @Override
    public JspWriter getOut() {
        return rootJspCtxt.getOut();
    }

    @Override
    public HttpSession getSession() {
        return rootJspCtxt.getSession();
    }

    @Override
    public Object getPage() {
        return invokingJspCtxt.getPage();
    }

    @Override
    public ServletRequest getRequest() {
        return invokingJspCtxt.getRequest();
    }

    @Override
    public ServletResponse getResponse() {
        return rootJspCtxt.getResponse();
    }

    @Override
    public Exception getException() {
        return invokingJspCtxt.getException();
    }

    @Override
    public ServletConfig getServletConfig() {
        return invokingJspCtxt.getServletConfig();
    }

    @Override
    public ServletContext getServletContext() {
        if (servletContext == null) {
            servletContext = rootJspCtxt.getServletContext();
        }
        return servletContext;
    }

    @Override
    public void forward(String relativeUrlPath) throws ServletException,
            IOException {
        invokingJspCtxt.forward(relativeUrlPath);
    }

    @Override
    public void include(String relativeUrlPath) throws ServletException,
            IOException {
        invokingJspCtxt.include(relativeUrlPath);
    }

    @Override
    public void include(String relativeUrlPath, boolean flush)
            throws ServletException, IOException {
        invokingJspCtxt.include(relativeUrlPath, false);
    }

    @Override
    public BodyContent pushBody() {
        return invokingJspCtxt.pushBody();
    }

    @Override
    public JspWriter pushBody(Writer writer) {
        return invokingJspCtxt.pushBody(writer);
    }

    @Override
    public JspWriter popBody() {
        return invokingJspCtxt.popBody();
    }

    @Override
    public void handlePageException(Exception ex) throws IOException,
            ServletException {
        // Should never be called since handleException() called with a
        // Throwable in the generated servlet.
        handlePageException((Throwable) ex);
    }

    @Override
    public void handlePageException(Throwable t) throws IOException,
            ServletException {
        invokingJspCtxt.handlePageException(t);
    }

    /**
     * 在标签文件开始处同步变量
     */
    public void syncBeginTagFile() {
        saveNestedVariables();
    }

    /**
     * 在分段调用之前同步变量
     */
    public void syncBeforeInvoke() {
        copyTagToPageScope(VariableInfo.NESTED);
        copyTagToPageScope(VariableInfo.AT_BEGIN);
    }

    /**
     * 在标签文件结尾同步变量
     */
    public void syncEndTagFile() {
        copyTagToPageScope(VariableInfo.AT_BEGIN);
        copyTagToPageScope(VariableInfo.AT_END);
        restoreNestedVariables();
    }

    /**
     * 将给定范围的变量从这个JSP上下文包装器的虚拟页面范围复制到调用JSP上下文的页面范围.
     *
     * @param scope 变量范围(NESTED, AT_BEGIN, AT_END)
     */
    private void copyTagToPageScope(int scope) {
        Iterator<String> iter = null;

        switch (scope) {
        case VariableInfo.NESTED:
            if (nestedVars != null) {
                iter = nestedVars.iterator();
            }
            break;
        case VariableInfo.AT_BEGIN:
            if (atBeginVars != null) {
                iter = atBeginVars.iterator();
            }
            break;
        case VariableInfo.AT_END:
            if (atEndVars != null) {
                iter = atEndVars.iterator();
            }
            break;
        }

        while ((iter != null) && iter.hasNext()) {
            String varName = iter.next();
            Object obj = getAttribute(varName);
            varName = findAlias(varName);
            if (obj != null) {
                invokingJspCtxt.setAttribute(varName, obj);
            } else {
                invokingJspCtxt.removeAttribute(varName, PAGE_SCOPE);
            }
        }
    }

    /**
     * 保存存在于调用的JSP上下文中的 NESTED 变量的值, 因此它们之后可以被恢复.
     */
    private void saveNestedVariables() {
        if (nestedVars != null) {
            Iterator<String> iter = nestedVars.iterator();
            while (iter.hasNext()) {
                String varName = iter.next();
                varName = findAlias(varName);
                Object obj = invokingJspCtxt.getAttribute(varName);
                if (obj != null) {
                    originalNestedVars.put(varName, obj);
                }
            }
        }
    }

    /**
     * 恢复存在于调用的JSP上下文中的 NESTED 变量的值.
     */
    private void restoreNestedVariables() {
        if (nestedVars != null) {
            Iterator<String> iter = nestedVars.iterator();
            while (iter.hasNext()) {
                String varName = iter.next();
                varName = findAlias(varName);
                Object obj = originalNestedVars.get(varName);
                if (obj != null) {
                    invokingJspCtxt.setAttribute(varName, obj);
                } else {
                    invokingJspCtxt.removeAttribute(varName, PAGE_SCOPE);
                }
            }
        }
    }

    /**
     * 检查给定变量名是否用作别名, 如果是的话, 返回用作别名的变量名.
     *
     * @param varName 要检查的变量名
     * @return 返回varName或别名
     */
    private String findAlias(String varName) {

        if (aliases == null)
            return varName;

        String alias = aliases.get(varName);
        if (alias == null) {
            return varName;
        }
        return alias;
    }

    @Override
    public ELContext getELContext() {
        if (elContext == null) {
            elContext = new ELContextWrapper(rootJspCtxt.getELContext(), jspTag, this);
            JspFactory factory = JspFactory.getDefaultFactory();
            JspApplicationContext jspAppCtxt = factory.getJspApplicationContext(servletContext);
            if (jspAppCtxt instanceof JspApplicationContextImpl) {
                ((JspApplicationContextImpl) jspAppCtxt).fireListeners(elContext);
            }
        }
        return elContext;
    }


    static class ELContextWrapper extends ELContext {

        private final ELContext wrapped;
        private final JspTag jspTag;
        private final PageContext pageContext;
        private ImportHandler importHandler;

        private ELContextWrapper(ELContext wrapped, JspTag jspTag, PageContext pageContext) {
            this.wrapped = wrapped;
            this.jspTag = jspTag;
            this.pageContext = pageContext;
        }

        ELContext getWrappedELContext() {
            return wrapped;
        }

        @Override
        public void setPropertyResolved(boolean resolved) {
            wrapped.setPropertyResolved(resolved);
        }

        @Override
        public void setPropertyResolved(Object base, Object property) {
            wrapped.setPropertyResolved(base, property);
        }

        @Override
        public boolean isPropertyResolved() {
            return wrapped.isPropertyResolved();
        }

        @Override
        public void putContext(@SuppressWarnings("rawtypes") Class key, Object contextObject) {
            wrapped.putContext(key, contextObject);
        }

        @Override
        public Object getContext(@SuppressWarnings("rawtypes") Class key) {
            if (key == JspContext.class) {
                return pageContext;
            }
            return wrapped.getContext(key);
        }

        @Override
        public ImportHandler getImportHandler() {
            if (importHandler == null) {
                importHandler = new ImportHandler();
                if (jspTag instanceof JspSourceImports) {
                    Set<String> packageImports = ((JspSourceImports) jspTag).getPackageImports();
                    if (packageImports != null) {
                        for (String packageImport : packageImports) {
                            importHandler.importPackage(packageImport);
                        }
                    }
                    Set<String> classImports = ((JspSourceImports) jspTag).getClassImports();
                    if (classImports != null) {
                        for (String classImport : classImports) {
                            importHandler.importClass(classImport);
                        }
                    }
                }

            }
            return importHandler;
        }

        @Override
        public Locale getLocale() {
            return wrapped.getLocale();
        }

        @Override
        public void setLocale(Locale locale) {
            wrapped.setLocale(locale);
        }

        @Override
        public void addEvaluationListener(EvaluationListener listener) {
            wrapped.addEvaluationListener(listener);
        }

        @Override
        public List<EvaluationListener> getEvaluationListeners() {
            return wrapped.getEvaluationListeners();
        }

        @Override
        public void notifyBeforeEvaluation(String expression) {
            wrapped.notifyBeforeEvaluation(expression);
        }

        @Override
        public void notifyAfterEvaluation(String expression) {
            wrapped.notifyAfterEvaluation(expression);
        }

        @Override
        public void notifyPropertyResolved(Object base, Object property) {
            wrapped.notifyPropertyResolved(base, property);
        }

        @Override
        public boolean isLambdaArgument(String name) {
            return wrapped.isLambdaArgument(name);
        }

        @Override
        public Object getLambdaArgument(String name) {
            return wrapped.getLambdaArgument(name);
        }

        @Override
        public void enterLambdaScope(Map<String, Object> arguments) {
            wrapped.enterLambdaScope(arguments);
        }

        @Override
        public void exitLambdaScope() {
            wrapped.exitLambdaScope();
        }

        @Override
        public Object convertToType(Object obj, Class<?> type) {
            return wrapped.convertToType(obj, type);
        }

        @Override
        public ELResolver getELResolver() {
            return wrapped.getELResolver();
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return wrapped.getFunctionMapper();
        }

        @Override
        public VariableMapper getVariableMapper() {
            return wrapped.getVariableMapper();
        }
    }
}
