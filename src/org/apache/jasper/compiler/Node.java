package org.apache.jasper.compiler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ExpressionFactory;
import javax.servlet.jsp.tagext.BodyTag;
import javax.servlet.jsp.tagext.DynamicAttributes;
import javax.servlet.jsp.tagext.IterationTag;
import javax.servlet.jsp.tagext.JspIdConsumer;
import javax.servlet.jsp.tagext.SimpleTag;
import javax.servlet.jsp.tagext.TagAttributeInfo;
import javax.servlet.jsp.tagext.TagData;
import javax.servlet.jsp.tagext.TagFileInfo;
import javax.servlet.jsp.tagext.TagInfo;
import javax.servlet.jsp.tagext.TagVariableInfo;
import javax.servlet.jsp.tagext.TryCatchFinally;
import javax.servlet.jsp.tagext.VariableInfo;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;
import org.xml.sax.Attributes;

/**
 * 一个JSP页面或JSP文档(XML)这类的内部数据表示.
 * 这里还包括一种穿越节点的访问者类.
 */
abstract class Node implements TagConstants {

    private static final VariableInfo[] ZERO_VARIABLE_INFO = {};

    protected Attributes attrs;

    // xmlns 属性表示标签库(只在XML语法中)
    protected Attributes taglibAttrs;

    /*
     * xmlns 属性不表示标签库(只在XML语法中)
     */
    protected Attributes nonTaglibXmlnsAttrs;

    protected Nodes body;

    protected String text;

    protected Mark startMark;

    protected int beginJavaLine;

    protected int endJavaLine;

    protected Node parent;

    protected Nodes namedAttributeNodes; // cached for performance

    protected String qName;

    protected String localName;

    /*
     * 生成此节点及其正文的代码的内部类的名称.
     * 例如, 对于foo.jsp中的<jsp:body>, 它是"foo_jspHelper". 这主要是用于传达这样的信息从生成器到SMAP生成器.
     */
    protected String innerClassName;


    public Node() {
    }

    /**
     * @param start JSP页面的位置
     * @param parent 外围节点
     */
    public Node(Mark start, Node parent) {
        this.startMark = start;
        addToParent(parent);
    }

    /**
     * @param qName 动作的限定名
     * @param localName 动作的本地名
     * @param attrs 此节点的属性
     * @param start JSP页面的位置
     * @param parent 外围节点
     */
    public Node(String qName, String localName, Attributes attrs, Mark start,
            Node parent) {
        this.qName = qName;
        this.localName = localName;
        this.attrs = attrs;
        this.startMark = start;
        addToParent(parent);
    }

    /**
     * @param qName 动作的限定名
     * @param localName 动作的本地名
     * @param attrs 名称不是以xmlns开头的动作的属性
     * @param nonTaglibXmlnsAttrs 不代表标签库的动作的xmlns属性
     * @param taglibAttrs 代表标签库的动作的xmlns属性
     * @param start JSP页面的位置
     * @param parent 外围节点
     */
    public Node(String qName, String localName, Attributes attrs,
            Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs, Mark start,
            Node parent) {
        this.qName = qName;
        this.localName = localName;
        this.attrs = attrs;
        this.nonTaglibXmlnsAttrs = nonTaglibXmlnsAttrs;
        this.taglibAttrs = taglibAttrs;
        this.startMark = start;
        addToParent(parent);
    }

    /*
     * @param qName 动作的限定名
     * @param localName 动作的本地名
     * @param text 与此节点关联的文本
     * @param start JSP页面的位置
     * @param parent 外围节点
     */
    public Node(String qName, String localName, String text, Mark start,
            Node parent) {
        this.qName = qName;
        this.localName = localName;
        this.text = text;
        this.startMark = start;
        addToParent(parent);
    }

    public String getQName() {
        return this.qName;
    }

    public String getLocalName() {
        return this.localName;
    }

    /*
     * 获取这个 Node的属性.
     *
     * 从标准语法解析的节点的情况下, 此方法返回所有节点的属性.
     *
     * 从XML语法解析的节点的情况下, 此方法只返回那些名称不是以xmlns开头的属性.
     */
    public Attributes getAttributes() {
        return this.attrs;
    }

    /*
     * 获取这个节点表示标签库的xmlns属性(只对从XML语法解析的节点有意义)
     */
    public Attributes getTaglibAttributes() {
        return this.taglibAttrs;
    }

    /*
     * 获取这个节点不表示标签库的xmlns属性(只对从XML语法解析的节点有意义)
     */
    public Attributes getNonTaglibXmlnsAttributes() {
        return this.nonTaglibXmlnsAttrs;
    }

    public void setAttributes(Attributes attrs) {
        this.attrs = attrs;
    }

    public String getAttributeValue(String name) {
        return (attrs == null) ? null : attrs.getValue(name);
    }

    /**
     * 获取非请求时间表达式的属性, 或者从节点的属性中, 或者从一个 jsp:attrbute 
     *
     * @param name 属性名
     *
     * @return The attribute value
     */
    public String getTextAttribute(String name) {

        String attr = getAttributeValue(name);
        if (attr != null) {
            return attr;
        }

        NamedAttribute namedAttribute = getNamedAttributeNode(name);
        if (namedAttribute == null) {
            return null;
        }

        return namedAttribute.getText();
    }

    /**
     * 搜索此节点的所有的子节点，为给定名称的jsp:attribute标准动作的名字, 并返回匹配的命名属性的NamedAttribute节点, 或者null.
     * <p>
     * 这应该总是被调用，并且只对接受动态运行时属性表达式的节点调用.
     *
     * @param name 属性名
     * @return 匹配的命名属性的 NamedAttribute 节点, 或 null
     */
    public NamedAttribute getNamedAttributeNode(String name) {
        NamedAttribute result = null;

        // 查找NamedAttribute子节点中的属性
        Nodes nodes = getNamedAttributeNodes();
        int numChildNodes = nodes.size();
        for (int i = 0; i < numChildNodes; i++) {
            NamedAttribute na = (NamedAttribute) nodes.getNode(i);
            boolean found = false;
            int index = name.indexOf(':');
            if (index != -1) {
                // qualified name
                found = na.getName().equals(name);
            } else {
                found = na.getLocalName().equals(name);
            }
            if (found) {
                result = na;
                break;
            }
        }

        return result;
    }

    /**
     * 为 jsp:attribute标准动作搜索该节点 的所有的子节点, 并返回一组节点为 Node.Nodes 对象.
     *
     * @return 可能是空的 Node.Nodes对象
     */
    public Node.Nodes getNamedAttributeNodes() {

        if (namedAttributeNodes != null) {
            return namedAttributeNodes;
        }

        Node.Nodes result = new Node.Nodes();

        // 查找NamedAttribute 子元素中的属性
        Nodes nodes = getBody();
        if (nodes != null) {
            int numChildNodes = nodes.size();
            for (int i = 0; i < numChildNodes; i++) {
                Node n = nodes.getNode(i);
                if (n instanceof NamedAttribute) {
                    result.add(n);
                } else if (!(n instanceof Comment)) {
                    // Nothing can come before jsp:attribute, and only
                    // jsp:body can come after it.
                    break;
                }
            }
        }

        namedAttributeNodes = result;
        return result;
    }

    public Nodes getBody() {
        return body;
    }

    public void setBody(Nodes body) {
        this.body = body;
    }

    public String getText() {
        return text;
    }

    public Mark getStart() {
        return startMark;
    }

    public Node getParent() {
        return parent;
    }

    public int getBeginJavaLine() {
        return beginJavaLine;
    }

    public void setBeginJavaLine(int begin) {
        beginJavaLine = begin;
    }

    public int getEndJavaLine() {
        return endJavaLine;
    }

    public void setEndJavaLine(int end) {
        endJavaLine = end;
    }

    public Node.Root getRoot() {
        Node n = this;
        while (!(n instanceof Node.Root)) {
            n = n.getParent();
        }
        return (Node.Root) n;
    }

    public String getInnerClassName() {
        return innerClassName;
    }

    public void setInnerClassName(String icn) {
        innerClassName = icn;
    }

    /**
     * 基于节点类型选择和调用访问者类中的方法.
     * @param v 访问者类
     */
    abstract void accept(Visitor v) throws JasperException;

    // *********************************************************************
    // Private utility methods

    /*
     * 将此节点添加到给定父节点的主体.
     */
    private void addToParent(Node parent) {
        if (parent != null) {
            this.parent = parent;
            Nodes parentBody = parent.getBody();
            if (parentBody == null) {
                parentBody = new Nodes();
                parent.setBody(parentBody);
            }
            parentBody.add(this);
        }
    }

    /***************************************************************************
     * Child classes
     */

    /**
     * 表示JSP页面或JSP文档的根
     */
    public static class Root extends Node {

        private final Root parentRoot;

        private final boolean isXmlSyntax;

        // 包含此根的页面的源代码编码
        private String pageEnc;

        // JSP配置元素中指定的页面编码
        private String jspConfigPageEnc;

        /*
         * 是否正在使用默认页面编码(只适用于标准语法).
		 *
		 * True 如果页面不提供页面指令和'contentType'属性(或者'contentType'属性没有CHARSET值),
		 * 页面不会提供页面指令和 'pageEncoding'属性, 并且没有其URL模式与页面匹配的JSP配置元素页编码.
         */
        private boolean isDefaultPageEncoding;

        /*
         * 是否在页面的XML 序言中显式的指定了编码(只用于XML语法中的页面).
		 * 此信息用于决定是否必须对编码冲突报告错误.
         */
        private boolean isEncodingSpecifiedInProlog;

        /*
         * 是否在页面DOM中明确指定了编码.
         */
        private boolean isBomPresent;

        /*
         * 临时变量序列号.
         */
        private int tempSequenceNumber = 0;

        Root(Mark start, Node parent, boolean isXmlSyntax) {
            super(start, parent);
            this.isXmlSyntax = isXmlSyntax;
            this.qName = JSP_ROOT_ACTION;
            this.localName = ROOT_ACTION;

            // 找出并设置父级根
            Node r = parent;
            while ((r != null) && !(r instanceof Node.Root))
                r = r.getParent();
            parentRoot = (Node.Root) r;
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public boolean isXmlSyntax() {
            return isXmlSyntax;
        }

        /*
         * 设置JSP配置元素中指定的编码，其URL模式与包含此根的页面相匹配.
         */
        public void setJspConfigPageEncoding(String enc) {
            jspConfigPageEnc = enc;
        }

        /*
         * 获取JSP配置元素中指定的编码，其URL模式与包含此根的页面相匹配.
         */
        public String getJspConfigPageEncoding() {
            return jspConfigPageEnc;
        }

        public void setPageEncoding(String enc) {
            pageEnc = enc;
        }

        public String getPageEncoding() {
            return pageEnc;
        }

        public void setIsDefaultPageEncoding(boolean isDefault) {
            isDefaultPageEncoding = isDefault;
        }

        public boolean isDefaultPageEncoding() {
            return isDefaultPageEncoding;
        }

        public void setIsEncodingSpecifiedInProlog(boolean isSpecified) {
            isEncodingSpecifiedInProlog = isSpecified;
        }

        public boolean isEncodingSpecifiedInProlog() {
            return isEncodingSpecifiedInProlog;
        }

        public void setIsBomPresent(boolean isBom) {
            isBomPresent = isBom;
        }

        public boolean isBomPresent() {
            return isBomPresent;
        }

        /**
         * 生成一个新的临时变量名.
         *
         * @return 临时变量的名称
         */
        public String nextTemporaryVariableName() {
            if (parentRoot == null) {
                return Constants.TEMP_VARIABLE_NAME_PREFIX + (tempSequenceNumber++);
            } else {
                return parentRoot.nextTemporaryVariableName();
            }

        }
    }

    /**
     * 表示JSP文档的根(XML syntax)
     */
    public static class JspRoot extends Node {

        public JspRoot(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, ROOT_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs,
                    start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示页面指令
     */
    public static class PageDirective extends Node {

        private final Vector<String> imports;

        public PageDirective(Attributes attrs, Mark start, Node parent) {
            this(JSP_PAGE_DIRECTIVE_ACTION, attrs, null, null, start, parent);
        }

        public PageDirective(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, PAGE_DIRECTIVE_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
            imports = new Vector<>();
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * 解析给定属性值中的类或包名称的逗号分隔列表, 并将每个组件添加到这个PageDirective的引入类和包的集合.
		 * @param value 逗号分隔的导入字符串.
         */
        public void addImport(String value) {
            int start = 0;
            int index;
            while ((index = value.indexOf(',', start)) != -1) {
                imports.add(validateImport(value.substring(start, index)));
                start = index + 1;
            }
            if (start == 0) {
                // No comma found
                imports.add(validateImport(value));
            } else {
                imports.add(validateImport(value.substring(start)));
            }
        }

        public List<String> getImports() {
            return imports;
        }

        /**
         * 只是需要足够的验证以确保没有奇怪的事情发生.
         * 编译器将验证这个, 当它试图编译.java文件结果时.
         */
        private String validateImport(String importEntry) {
            // 这应该是一个完全限定的类名或具有通配符的包名
            if (importEntry.indexOf(';') > -1) {
                throw new IllegalArgumentException(
                        Localizer.getMessage("jsp.error.page.invalid.import"));
            }
            return importEntry.trim();
        }
    }

    /**
     * 表示一个包含指令
     */
    public static class IncludeDirective extends Node {

        public IncludeDirective(Attributes attrs, Mark start, Node parent) {
            this(JSP_INCLUDE_DIRECTIVE_ACTION, attrs, null, null, start, parent);
        }

        public IncludeDirective(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, INCLUDE_DIRECTIVE_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示自定义taglib伪指令
     */
    public static class TaglibDirective extends Node {

        public TaglibDirective(Attributes attrs, Mark start, Node parent) {
            super(JSP_TAGLIB_DIRECTIVE_ACTION, TAGLIB_DIRECTIVE_ACTION, attrs,
                    start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示标记指令
     */
    public static class TagDirective extends Node {
        private final Vector<String> imports;

        public TagDirective(Attributes attrs, Mark start, Node parent) {
            this(JSP_TAG_DIRECTIVE_ACTION, attrs, null, null, start, parent);
        }

        public TagDirective(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, TAG_DIRECTIVE_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
            imports = new Vector<>();
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * 解析给定属性值中的类或包名称的逗号分隔列表, 并将每个组件添加到这个PageDirective的引入类和包的集合.
         * @param value 逗号分隔的字符串.
         */
        public void addImport(String value) {
            int start = 0;
            int index;
            while ((index = value.indexOf(',', start)) != -1) {
                imports.add(value.substring(start, index).trim());
                start = index + 1;
            }
            if (start == 0) {
                // No comma found
                imports.add(value.trim());
            } else {
                imports.add(value.substring(start).trim());
            }
        }

        public List<String> getImports() {
            return imports;
        }
    }

    /**
     * 表示属性指令
     */
    public static class AttributeDirective extends Node {

        public AttributeDirective(Attributes attrs, Mark start, Node parent) {
            this(JSP_ATTRIBUTE_DIRECTIVE_ACTION, attrs, null, null, start,
                    parent);
        }

        public AttributeDirective(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, ATTRIBUTE_DIRECTIVE_ACTION, attrs,
                    nonTaglibXmlnsAttrs, taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示变量指令
     */
    public static class VariableDirective extends Node {

        public VariableDirective(Attributes attrs, Mark start, Node parent) {
            this(JSP_VARIABLE_DIRECTIVE_ACTION, attrs, null, null, start,
                    parent);
        }

        public VariableDirective(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, VARIABLE_DIRECTIVE_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示一个<jsp:invoke>标签文件的操作
     */
    public static class InvokeAction extends Node {

        public InvokeAction(Attributes attrs, Mark start, Node parent) {
            this(JSP_INVOKE_ACTION, attrs, null, null, start, parent);
        }

        public InvokeAction(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, INVOKE_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示一个<jsp:doBody>标签文件动作
     */
    public static class DoBodyAction extends Node {

        public DoBodyAction(Attributes attrs, Mark start, Node parent) {
            this(JSP_DOBODY_ACTION, attrs, null, null, start, parent);
        }

        public DoBodyAction(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, DOBODY_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示JSP注释注释保留完整性.
     */
    public static class Comment extends Node {

        public Comment(String text, Mark start, Node parent) {
            super(null, null, text, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示一个表达式, 声明, 或脚本
     */
    public abstract static class ScriptingElement extends Node {

        public ScriptingElement(String qName, String localName, String text,
                Mark start, Node parent) {
            super(qName, localName, text, start, parent);
        }

        public ScriptingElement(String qName, String localName,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, localName, null, nonTaglibXmlnsAttrs, taglibAttrs,
                    start, parent);
        }

        /**
         * 在JSP语法中从JSP页面创建此节点时, 它的文本将以String形式 被保存进"text"字段,
		 * 然而，当这个节点是从JSP文档创建的时候, 它的文本将被保存进一个或多个它的主体的TemplateText节点. 此方法处理任意一种情况.
         *
         * @return The text string
         */
        @Override
        public String getText() {
            String ret = text;
            if (ret == null) {
                if (body != null) {
                    StringBuilder buf = new StringBuilder();
                    for (int i = 0; i < body.size(); i++) {
                        buf.append(body.getNode(i).getText());
                    }
                    ret = buf.toString();
                } else {
                    // Nulls cause NPEs further down the line
                    ret = "";
                }
            }
            return ret;
        }

        /**
         * 出于上述原因, 在包含的TemplateText节点中的源行信息不应该被使用.
         */
        @Override
        public Mark getStart() {
            if (text == null && body != null && body.size() > 0) {
                return body.getNode(0).getStart();
            } else {
                return super.getStart();
            }
        }
    }

    /**
     * 表示一个声明
     */
    public static class Declaration extends ScriptingElement {

        public Declaration(String text, Mark start, Node parent) {
            super(JSP_DECLARATION_ACTION, DECLARATION_ACTION, text, start,
                    parent);
        }

        public Declaration(String qName, Attributes nonTaglibXmlnsAttrs,
                Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, DECLARATION_ACTION, nonTaglibXmlnsAttrs, taglibAttrs,
                    start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示一个表达式. 属性中的表达式嵌入在属性字符串中，而不是这里.
     */
    public static class Expression extends ScriptingElement {

        public Expression(String text, Mark start, Node parent) {
            super(JSP_EXPRESSION_ACTION, EXPRESSION_ACTION, text, start, parent);
        }

        public Expression(String qName, Attributes nonTaglibXmlnsAttrs,
                Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, EXPRESSION_ACTION, nonTaglibXmlnsAttrs, taglibAttrs,
                    start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示一个脚本
     */
    public static class Scriptlet extends ScriptingElement {

        public Scriptlet(String text, Mark start, Node parent) {
            super(JSP_SCRIPTLET_ACTION, SCRIPTLET_ACTION, text, start, parent);
        }

        public Scriptlet(String qName, Attributes nonTaglibXmlnsAttrs,
                Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, SCRIPTLET_ACTION, nonTaglibXmlnsAttrs, taglibAttrs,
                    start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示EL表达式. 属性中的表达式嵌入在属性字符串中，而不是这里.
     */
    public static class ELExpression extends Node {

        private ELNode.Nodes el;

        private final char type;

        public ELExpression(char type, String text, Mark start, Node parent) {
            super(null, null, text, start, parent);
            this.type = type;
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public void setEL(ELNode.Nodes el) {
            this.el = el;
        }

        public ELNode.Nodes getEL() {
            return el;
        }

        public char getType() {
            return this.type;
        }
    }

    /**
     * 表示一个param操作
     */
    public static class ParamAction extends Node {

        private JspAttribute value;

        public ParamAction(Attributes attrs, Mark start, Node parent) {
            this(JSP_PARAM_ACTION, attrs, null, null, start, parent);
        }

        public ParamAction(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, PARAM_ACTION, attrs, nonTaglibXmlnsAttrs, taglibAttrs,
                    start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public void setValue(JspAttribute value) {
            this.value = value;
        }

        public JspAttribute getValue() {
            return value;
        }
    }

    /**
     * 表示一个params操作
     */
    public static class ParamsAction extends Node {

        public ParamsAction(Mark start, Node parent) {
            this(JSP_PARAMS_ACTION, null, null, start, parent);
        }

        public ParamsAction(String qName, Attributes nonTaglibXmlnsAttrs,
                Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, PARAMS_ACTION, null, nonTaglibXmlnsAttrs, taglibAttrs,
                    start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示回退操作
     */
    public static class FallBackAction extends Node {

        public FallBackAction(Mark start, Node parent) {
            this(JSP_FALLBACK_ACTION, null, null, start, parent);
        }

        public FallBackAction(String qName, Attributes nonTaglibXmlnsAttrs,
                Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, FALLBACK_ACTION, null, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示一个include 操作
     */
    public static class IncludeAction extends Node {

        private JspAttribute page;

        public IncludeAction(Attributes attrs, Mark start, Node parent) {
            this(JSP_INCLUDE_ACTION, attrs, null, null, start, parent);
        }

        public IncludeAction(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, INCLUDE_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public void setPage(JspAttribute page) {
            this.page = page;
        }

        public JspAttribute getPage() {
            return page;
        }
    }

    /**
     * 表示一个forward 操作
     */
    public static class ForwardAction extends Node {

        private JspAttribute page;

        public ForwardAction(Attributes attrs, Mark start, Node parent) {
            this(JSP_FORWARD_ACTION, attrs, null, null, start, parent);
        }

        public ForwardAction(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, FORWARD_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public void setPage(JspAttribute page) {
            this.page = page;
        }

        public JspAttribute getPage() {
            return page;
        }
    }

    /**
     * 表示一个getProperty 操作
     */
    public static class GetProperty extends Node {

        public GetProperty(Attributes attrs, Mark start, Node parent) {
            this(JSP_GET_PROPERTY_ACTION, attrs, null, null, start, parent);
        }

        public GetProperty(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, GET_PROPERTY_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示一个setProperty 操作
     */
    public static class SetProperty extends Node {

        private JspAttribute value;

        public SetProperty(Attributes attrs, Mark start, Node parent) {
            this(JSP_SET_PROPERTY_ACTION, attrs, null, null, start, parent);
        }

        public SetProperty(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, SET_PROPERTY_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public void setValue(JspAttribute value) {
            this.value = value;
        }

        public JspAttribute getValue() {
            return value;
        }
    }

    /**
     * 表示一个 useBean 操作
     */
    public static class UseBean extends Node {

        private JspAttribute beanName;

        public UseBean(Attributes attrs, Mark start, Node parent) {
            this(JSP_USE_BEAN_ACTION, attrs, null, null, start, parent);
        }

        public UseBean(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, USE_BEAN_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public void setBeanName(JspAttribute beanName) {
            this.beanName = beanName;
        }

        public JspAttribute getBeanName() {
            return beanName;
        }
    }

    /**
     * 表示一个 plugin 操作
     */
    public static class PlugIn extends Node {

        private JspAttribute width;

        private JspAttribute height;

        public PlugIn(Attributes attrs, Mark start, Node parent) {
            this(JSP_PLUGIN_ACTION, attrs, null, null, start, parent);
        }

        public PlugIn(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, PLUGIN_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public void setHeight(JspAttribute height) {
            this.height = height;
        }

        public void setWidth(JspAttribute width) {
            this.width = width;
        }

        public JspAttribute getHeight() {
            return height;
        }

        public JspAttribute getWidth() {
            return width;
        }
    }

    /**
     * 表示一个无解释的标签, 从一个 Jsp 文档
     */
    public static class UninterpretedTag extends Node {

        private JspAttribute[] jspAttrs;

        public UninterpretedTag(String qName, String localName,
                Attributes attrs, Attributes nonTaglibXmlnsAttrs,
                Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, localName, attrs, nonTaglibXmlnsAttrs, taglibAttrs,
                    start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public void setJspAttributes(JspAttribute[] jspAttrs) {
            this.jspAttrs = jspAttrs;
        }

        public JspAttribute[] getJspAttributes() {
            return jspAttrs;
        }
    }

    /**
     * 表示一个<jsp:element>.
     */
    public static class JspElement extends Node {

        private JspAttribute[] jspAttrs;

        private JspAttribute nameAttr;

        public JspElement(Attributes attrs, Mark start, Node parent) {
            this(JSP_ELEMENT_ACTION, attrs, null, null, start, parent);
        }

        public JspElement(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, ELEMENT_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public void setJspAttributes(JspAttribute[] jspAttrs) {
            this.jspAttrs = jspAttrs;
        }

        public JspAttribute[] getJspAttributes() {
            return jspAttrs;
        }

        /*
         * 设置XML风格的 'name'属性
         */
        public void setNameAttribute(JspAttribute nameAttr) {
            this.nameAttr = nameAttr;
        }

        /*
         * 获取XML风格的 'name'属性
         */
        public JspAttribute getNameAttribute() {
            return this.nameAttr;
        }
    }

    /**
     * 表示一个<jsp:output>.
     */
    public static class JspOutput extends Node {

        public JspOutput(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {
            super(qName, OUTPUT_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 有关子元素的收集信息. 节点所使用的CustomTag, JspBody, NamedAttribute. 信息设置在Collector中.
     */
    public static class ChildInfo {
        private boolean scriptless; // true if the tag and its body

        // contain no scripting elements.
        private boolean hasUseBean;

        private boolean hasIncludeAction;

        private boolean hasParamAction;

        private boolean hasSetProperty;

        private boolean hasScriptingVars;

        public void setScriptless(boolean s) {
            scriptless = s;
        }

        public boolean isScriptless() {
            return scriptless;
        }

        public void setHasUseBean(boolean u) {
            hasUseBean = u;
        }

        public boolean hasUseBean() {
            return hasUseBean;
        }

        public void setHasIncludeAction(boolean i) {
            hasIncludeAction = i;
        }

        public boolean hasIncludeAction() {
            return hasIncludeAction;
        }

        public void setHasParamAction(boolean i) {
            hasParamAction = i;
        }

        public boolean hasParamAction() {
            return hasParamAction;
        }

        public void setHasSetProperty(boolean s) {
            hasSetProperty = s;
        }

        public boolean hasSetProperty() {
            return hasSetProperty;
        }

        public void setHasScriptingVars(boolean s) {
            hasScriptingVars = s;
        }

        public boolean hasScriptingVars() {
            return hasScriptingVars;
        }
    }

    /**
     * 表示一个custom tag
     */
    public static class CustomTag extends Node {

        private final String uri;

        private final String prefix;

        private JspAttribute[] jspAttrs;

        private TagData tagData;

        private String tagHandlerPoolName;

        private final TagInfo tagInfo;

        private final TagFileInfo tagFileInfo;

        private Class<?> tagHandlerClass;

        private VariableInfo[] varInfos;

        private final int customNestingLevel;

        private final ChildInfo childInfo;

        private final boolean implementsIterationTag;

        private final boolean implementsBodyTag;

        private final boolean implementsTryCatchFinally;

        private final boolean implementsJspIdConsumer;

        private final boolean implementsSimpleTag;

        private final boolean implementsDynamicAttributes;

        private List<Object> atBeginScriptingVars;

        private List<Object> atEndScriptingVars;

        private List<Object> nestedScriptingVars;

        private Node.CustomTag customTagParent;

        private Integer numCount;

        private boolean useTagPlugin;

        private TagPluginContext tagPluginContext;

        /**
         * 下面的两个字段用于保存标签插件生成的Java脚本. 只有在useTagPlugin 是 true的时候有意义;
		 * 可以将它们移动到 TagPluginContextImpl, 但我们总是需要将tagPluginContext添加到 TagPluginContextImpl...
         */
        private Nodes atSTag;

        private Nodes atETag;

        public CustomTag(String qName, String prefix, String localName,
                String uri, Attributes attrs, Mark start, Node parent,
                TagInfo tagInfo, Class<?> tagHandlerClass) {
            this(qName, prefix, localName, uri, attrs, null, null, start,
                    parent, tagInfo, tagHandlerClass);
        }

        public CustomTag(String qName, String prefix, String localName,
                String uri, Attributes attrs, Attributes nonTaglibXmlnsAttrs,
                Attributes taglibAttrs, Mark start, Node parent,
                TagInfo tagInfo, Class<?> tagHandlerClass) {
            super(qName, localName, attrs, nonTaglibXmlnsAttrs, taglibAttrs,
                    start, parent);

            this.uri = uri;
            this.prefix = prefix;
            this.tagInfo = tagInfo;
            this.tagFileInfo = null;
            this.tagHandlerClass = tagHandlerClass;
            this.customNestingLevel = makeCustomNestingLevel();
            this.childInfo = new ChildInfo();

            this.implementsIterationTag = IterationTag.class
                    .isAssignableFrom(tagHandlerClass);
            this.implementsBodyTag = BodyTag.class
                    .isAssignableFrom(tagHandlerClass);
            this.implementsTryCatchFinally = TryCatchFinally.class
                    .isAssignableFrom(tagHandlerClass);
            this.implementsSimpleTag = SimpleTag.class
                    .isAssignableFrom(tagHandlerClass);
            this.implementsDynamicAttributes = DynamicAttributes.class
                    .isAssignableFrom(tagHandlerClass);
            this.implementsJspIdConsumer = JspIdConsumer.class
                    .isAssignableFrom(tagHandlerClass);
        }

        public CustomTag(String qName, String prefix, String localName,
                String uri, Attributes attrs, Mark start, Node parent,
                TagFileInfo tagFileInfo) {
            this(qName, prefix, localName, uri, attrs, null, null, start,
                    parent, tagFileInfo);
        }

        public CustomTag(String qName, String prefix, String localName,
                String uri, Attributes attrs, Attributes nonTaglibXmlnsAttrs,
                Attributes taglibAttrs, Mark start, Node parent,
                TagFileInfo tagFileInfo) {

            super(qName, localName, attrs, nonTaglibXmlnsAttrs, taglibAttrs,
                    start, parent);

            this.uri = uri;
            this.prefix = prefix;
            this.tagFileInfo = tagFileInfo;
            this.tagInfo = tagFileInfo.getTagInfo();
            this.customNestingLevel = makeCustomNestingLevel();
            this.childInfo = new ChildInfo();

            this.implementsIterationTag = false;
            this.implementsBodyTag = false;
            this.implementsTryCatchFinally = false;
            this.implementsSimpleTag = true;
            this.implementsJspIdConsumer = false;
            this.implementsDynamicAttributes = tagInfo.hasDynamicAttributes();
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * @return 此自定义操作所属的URI命名空间
         */
        public String getURI() {
            return this.uri;
        }

        /**
         * @return 标签前缀
         */
        public String getPrefix() {
            return prefix;
        }

        public void setJspAttributes(JspAttribute[] jspAttrs) {
            this.jspAttrs = jspAttrs;
        }

        public JspAttribute[] getJspAttributes() {
            return jspAttrs;
        }

        public ChildInfo getChildInfo() {
            return childInfo;
        }

        public void setTagData(TagData tagData) {
            this.tagData = tagData;
            this.varInfos = tagInfo.getVariableInfo(tagData);
            if (this.varInfos == null) {
                this.varInfos = ZERO_VARIABLE_INFO;
            }
        }

        public TagData getTagData() {
            return tagData;
        }

        public void setTagHandlerPoolName(String s) {
            tagHandlerPoolName = s;
        }

        public String getTagHandlerPoolName() {
            return tagHandlerPoolName;
        }

        public TagInfo getTagInfo() {
            return tagInfo;
        }

        public TagFileInfo getTagFileInfo() {
            return tagFileInfo;
        }

        /*
         * @return true 如果该自定义操作由标签文件支持,否则false
         */
        public boolean isTagFile() {
            return tagFileInfo != null;
        }

        public Class<?> getTagHandlerClass() {
            return tagHandlerClass;
        }

        public void setTagHandlerClass(Class<?> hc) {
            tagHandlerClass = hc;
        }

        public boolean implementsIterationTag() {
            return implementsIterationTag;
        }

        public boolean implementsBodyTag() {
            return implementsBodyTag;
        }

        public boolean implementsTryCatchFinally() {
            return implementsTryCatchFinally;
        }

        public boolean implementsJspIdConsumer() {
            return implementsJspIdConsumer;
        }

        public boolean implementsSimpleTag() {
            return implementsSimpleTag;
        }

        public boolean implementsDynamicAttributes() {
            return implementsDynamicAttributes;
        }

        public TagVariableInfo[] getTagVariableInfos() {
            return tagInfo.getTagVariableInfos();
        }

        public VariableInfo[] getVariableInfos() {
            return varInfos;
        }

        public void setCustomTagParent(Node.CustomTag n) {
            this.customTagParent = n;
        }

        public Node.CustomTag getCustomTagParent() {
            return this.customTagParent;
        }

        public void setNumCount(Integer count) {
            this.numCount = count;
        }

        public Integer getNumCount() {
            return this.numCount;
        }

        public void setScriptingVars(List<Object> vec, int scope) {
            switch (scope) {
            case VariableInfo.AT_BEGIN:
                this.atBeginScriptingVars = vec;
                break;
            case VariableInfo.AT_END:
                this.atEndScriptingVars = vec;
                break;
            case VariableInfo.NESTED:
                this.nestedScriptingVars = vec;
                break;
            }
        }

        /*
         * 获取需要声明的给定范围的脚本变量.
         */
        public List<Object> getScriptingVars(int scope) {
            List<Object> vec = null;

            switch (scope) {
            case VariableInfo.AT_BEGIN:
                vec = this.atBeginScriptingVars;
                break;
            case VariableInfo.AT_END:
                vec = this.atEndScriptingVars;
                break;
            case VariableInfo.NESTED:
                vec = this.nestedScriptingVars;
                break;
            }

            return vec;
        }

        /*
         * 获取自定义标签的自定义嵌套级别, 这是自定义标签在内部嵌套的次数.
         */
        public int getCustomNestingLevel() {
            return customNestingLevel;
        }

        /**
         * 检查给定名称的属性是否为JspFragment类型.
         *
         * @param name 要检查的属性
         *
         * @return {@code true} if it is a JspFragment
         */
        public boolean checkIfAttributeIsJspFragment(String name) {
            boolean result = false;

            TagAttributeInfo[] attributes = tagInfo.getAttributes();
            for (int i = 0; i < attributes.length; i++) {
                if (attributes[i].getName().equals(name)
                        && attributes[i].isFragment()) {
                    result = true;
                    break;
                }
            }

            return result;
        }

        public void setUseTagPlugin(boolean use) {
            useTagPlugin = use;
        }

        public boolean useTagPlugin() {
            return useTagPlugin;
        }

        public void setTagPluginContext(TagPluginContext tagPluginContext) {
            this.tagPluginContext = tagPluginContext;
        }

        public TagPluginContext getTagPluginContext() {
            return tagPluginContext;
        }

        public void setAtSTag(Nodes sTag) {
            atSTag = sTag;
        }

        public Nodes getAtSTag() {
            return atSTag;
        }

        public void setAtETag(Nodes eTag) {
            atETag = eTag;
        }

        public Nodes getAtETag() {
            return atETag;
        }

        /*
         * 计算此自定义标签的自定义嵌套级别, 它对应于自定义标签在其内部嵌套的次数.
         *
         * Example:
         *
         * <g:h> <a:b> -- nesting level 0 <c:d> <e:f> <a:b> -- nesting level 1
         * <a:b> -- nesting level 2 </a:b> </a:b> <a:b> -- nesting level 1
         * </a:b> </e:f> </c:d> </a:b> </g:h>
         *
         * @return Custom tag's nesting level
         */
        private int makeCustomNestingLevel() {
            int n = 0;
            Node p = parent;
            while (p != null) {
                if ((p instanceof Node.CustomTag)
                        && qName.equals(((Node.CustomTag) p).qName)) {
                    n++;
                }
                p = p.parent;
            }
            return n;
        }

        /**
         * 如果此自定义操作是空的，则返回true, 否则返回false
         *
         * 如果下列行为成立，则自定义操作被认为是空的:
         * - getBody() 返回null
         * - 所有直接的子级是 jsp:attribute 操作
         * - 操作的 jsp:body 是空的.
         *
         * @return {@code true}如果这个自定义动作有一个空的主体, 否则{@code false}.
         */
        public boolean hasEmptyBody() {
            boolean hasEmptyBody = true;
            Nodes nodes = getBody();
            if (nodes != null) {
                int numChildNodes = nodes.size();
                for (int i = 0; i < numChildNodes; i++) {
                    Node n = nodes.getNode(i);
                    if (!(n instanceof NamedAttribute)) {
                        if (n instanceof JspBody) {
                            hasEmptyBody = (n.getBody() == null);
                        } else {
                            hasEmptyBody = false;
                        }
                        break;
                    }
                }
            }

            return hasEmptyBody;
        }
    }

    /**
     * 用作自定义操作属性的求值代码的占位符(仅由标签插件使用).
     */
    public static class AttributeGenerator extends Node {
        private String name; // 属性名
        private CustomTag tag; // 属性所属的标签

        public AttributeGenerator(Mark start, String name, CustomTag tag) {
            super(start, null);
            this.name = name;
            this.tag = tag;
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public String getName() {
            return name;
        }

        public CustomTag getTag() {
            return tag;
        }
    }

    /**
     * 表示 &lt;jsp:text&gt; 元素的主体
     */
    public static class JspText extends Node {

        public JspText(String qName, Attributes nonTaglibXmlnsAttrs,
                Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, TEXT_ACTION, null, nonTaglibXmlnsAttrs, taglibAttrs,
                    start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }
    }

    /**
     * 表示命名的属性 (&lt;jsp:attribute&gt;)
     */
    public static class NamedAttribute extends Node {

        // 一个适合代码生成的临时变量名
        private String temporaryVariableName;

        // True 如果这个节点需要去掉空格, 否则false
        private boolean trim = true;

        // True 如果该属性应该从输出中省略, 如果使用 <jsp:element>, 否则 false
        private JspAttribute omit;

        private final ChildInfo childInfo;

        private final String name;

        private String localName;

        private String prefix;

        public NamedAttribute(Attributes attrs, Mark start, Node parent) {
            this(JSP_ATTRIBUTE_ACTION, attrs, null, null, start, parent);
        }

        public NamedAttribute(String qName, Attributes attrs,
                Attributes nonTaglibXmlnsAttrs, Attributes taglibAttrs,
                Mark start, Node parent) {

            super(qName, ATTRIBUTE_ACTION, attrs, nonTaglibXmlnsAttrs,
                    taglibAttrs, start, parent);
            if ("false".equals(this.getAttributeValue("trim"))) {
                // (if null or true, leave default of true)
                trim = false;
            }
            childInfo = new ChildInfo();
            name = this.getAttributeValue("name");
            if (name != null) {
                // 强制性的属性 "name" 将在 Validator中检查
                localName = name;
                int index = name.indexOf(':');
                if (index != -1) {
                    prefix = name.substring(0, index);
                    localName = name.substring(index + 1);
                }
            }
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String getLocalName() {
            return this.localName;
        }

        public String getPrefix() {
            return this.prefix;
        }

        public ChildInfo getChildInfo() {
            return this.childInfo;
        }

        public boolean isTrim() {
            return trim;
        }

        public void setOmit(JspAttribute omit) {
            this.omit = omit;
        }

        public JspAttribute getOmit() {
            return omit;
        }

        /**
         * @return 一个惟一的临时变量名称来存储结果. (这可能会发生在别处, 但是这里很方便)
         */
        public String getTemporaryVariableName() {
            if (temporaryVariableName == null) {
                temporaryVariableName = getRoot().nextTemporaryVariableName();
            }
            return temporaryVariableName;
        }

        /*
         * 从这个命名属性获取属性值(<jsp:attribute>).
		 * 由于这种方法只适用于非 rtexpr的属性, 可以假设jsp:attribute主体是模板文本.
         */
        @Override
        public String getText() {

            class AttributeVisitor extends Visitor {
                private String attrValue = null;

                @Override
                public void visit(TemplateText txt) {
                    attrValue = txt.getText();
                }

                public String getAttrValue() {
                    return attrValue;
                }
            }

            // 根据JSP 2.0, 如果<jsp:attribute>操作主体是空的, 它相当于指定 "" 作为属性的值.
            String text = "";
            if (getBody() != null) {
                AttributeVisitor attributeVisitor = new AttributeVisitor();
                try {
                    getBody().visit(attributeVisitor);
                } catch (JasperException e) {
                }
                text = attributeVisitor.getAttrValue();
            }

            return text;
        }
    }

    /**
     * 表示一个JspBody节点(&lt;jsp:body&gt;)
     */
    public static class JspBody extends Node {

        private final ChildInfo childInfo;

        public JspBody(Mark start, Node parent) {
            this(JSP_BODY_ACTION, null, null, start, parent);
        }

        public JspBody(String qName, Attributes nonTaglibXmlnsAttrs,
                Attributes taglibAttrs, Mark start, Node parent) {
            super(qName, BODY_ACTION, null, nonTaglibXmlnsAttrs, taglibAttrs,
                    start, parent);
            this.childInfo = new ChildInfo();
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public ChildInfo getChildInfo() {
            return childInfo;
        }
    }

    /**
     * 表示模板文本字符串
     */
    public static class TemplateText extends Node {

        private ArrayList<Integer> extraSmap = null;

        public TemplateText(String text, Mark start, Node parent) {
            super(null, null, text, start, parent);
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * 删除模板文本左边的所有空格
         */
        public void ltrim() {
            int index = 0;
            while ((index < text.length()) && (text.charAt(index) <= ' ')) {
                index++;
            }
            text = text.substring(index);
        }

        public void setText(String text) {
            this.text = text;
        }

        /**
         * 删除模板文本右边的所有空格
         */
        public void rtrim() {
            int index = text.length();
            while ((index > 0) && (text.charAt(index - 1) <= ' ')) {
                index--;
            }
            text = text.substring(0, index);
        }

        /**
         * @return true 如果这个模板文本只包含空格.
         */
        public boolean isAllSpace() {
            boolean isAllSpace = true;
            for (int i = 0; i < text.length(); i++) {
                if (!Character.isWhitespace(text.charAt(i))) {
                    isAllSpace = false;
                    break;
                }
            }
            return isAllSpace;
        }

        /**
         * 添加资源到Java行映射
         * @param srcLine 资源行的位置, 相对于此节点开始时的行. 对应的java行是连续的.
         */
        public void addSmap(int srcLine) {
            if (extraSmap == null) {
                extraSmap = new ArrayList<>();
            }
            extraSmap.add(Integer.valueOf(srcLine));
        }

        public ArrayList<Integer> getExtraSmap() {
            return extraSmap;
        }
    }

    /***************************************************************************
     * Auxiliary classes used in Node
     */

    /**
     * 表示可以是请求时表达式的属性.
     *
     * 可以是普通属性, 表示请求时间表达式值的属性, 或命名属性 (使用 jsp:attribute标准动作指定).
     */

    public static class JspAttribute {

        private final String qName;

        private final String uri;

        private final String localName;

        private final String value;

        private final boolean expression;

        private final boolean dynamic;

        private final ELNode.Nodes el;

        private final TagAttributeInfo tai;

        // 如果是true, 这个JspAttribute表示一个 <jsp:attribute>
        private final boolean namedAttribute;

        // NamedAttribute解析树的节点
        private final NamedAttribute namedAttributeNode;

        JspAttribute(TagAttributeInfo tai, String qName, String uri,
                String localName, String value, boolean expr, ELNode.Nodes el,
                boolean dyn) {
            this.qName = qName;
            this.uri = uri;
            this.localName = localName;
            this.value = value;
            this.namedAttributeNode = null;
            this.expression = expr;
            this.el = el;
            this.dynamic = dyn;
            this.namedAttribute = false;
            this.tai = tai;
        }

        /**
         * 允许节点验证自身.
         *
         * @param ef 用于计算任何EL的表达式工厂
         * @param ctx 用于计算任何EL的上下文
         *
         * @throws ELException If validation fails
         */
        public void validateEL(ExpressionFactory ef, ELContext ctx)
                throws ELException {
            if (this.el != null) {
                // determine exact type
                ef.createValueExpression(ctx, this.value, String.class);
            }
        }

        /**
         * 如果JspAttribute表示一个命名属性, 必须存储属性的主体的节点.
         */
        JspAttribute(NamedAttribute na, TagAttributeInfo tai, boolean dyn) {
            this.qName = na.getName();
            this.localName = na.getLocalName();
            this.value = null;
            this.namedAttributeNode = na;
            this.expression = false;
            this.el = null;
            this.dynamic = dyn;
            this.namedAttribute = true;
            this.tai = tai;
            this.uri = null;
        }

        /**
         * @return 属性的名称
         */
        public String getName() {
            return qName;
        }

        /**
         * @return 属性的本地名称
         */
        public String getLocalName() {
            return localName;
        }

        /**
         * @return 属性的名称空间,或者 null 如果是默认的命名属性
         */
        public String getURI() {
            return uri;
        }

        public TagAttributeInfo getTagAttributeInfo() {
            return this.tai;
        }

        /**
         *
         * @return true 如果有 TagAttributeInfo 意思是需要分配 ValueExpression
         */
        public boolean isDeferredInput() {
            return (this.tai != null) ? this.tai.isDeferredValue() : false;
        }

        /**
         *
         * @return true 如果有 TagAttributeInfo 意思是需要分配MethodExpression
         */
        public boolean isDeferredMethodInput() {
            return (this.tai != null) ? this.tai.isDeferredMethod() : false;
        }

        public String getExpectedTypeName() {
            if (this.tai != null) {
                if (this.isDeferredInput()) {
                    return this.tai.getExpectedTypeName();
                } else if (this.isDeferredMethodInput()) {
                    String m = this.tai.getMethodSignature();
                    if (m != null) {
                        int rti = m.trim().indexOf(' ');
                        if (rti > 0) {
                            return m.substring(0, rti).trim();
                        }
                    }
                }
            }
            return "java.lang.Object";
        }

        public String[] getParameterTypeNames() {
            if (this.tai != null) {
                if (this.isDeferredMethodInput()) {
                    String m = this.tai.getMethodSignature();
                    if (m != null) {
                        m = m.trim();
                        m = m.substring(m.indexOf('(') + 1);
                        m = m.substring(0, m.length() - 1);
                        if (m.trim().length() > 0) {
                            String[] p = m.split(",");
                            for (int i = 0; i < p.length; i++) {
                                p[i] = p[i].trim();
                            }
                            return p;
                        }
                    }
                }
            }
            return new String[0];
        }

        /**
         * 只有namedAttribute是 false才有意义.
         *
         * @return 属性值, 或表达式字符串(删除 "<%=", "%>", "%=", "%", 但是包含EL表达式的"${" and "}")
         */
        public String getValue() {
            return value;
        }

        /**
         * 只有namedAttribute是true才有意义.
         *
         * @return 对该属性的主体进行评估的节点.
         */
        public NamedAttribute getNamedAttributeNode() {
            return namedAttributeNode;
        }

        /**
         * @return true 如果该值是一个传统的 rtexprvalue
         */
        public boolean isExpression() {
            return expression;
        }

        /**
         * @return true 如果值表示一个 NamedAttribute 值.
         */
        public boolean isNamedAttribute() {
            return namedAttribute;
        }

        /**
         * @return true 如果该值表示一个应该被馈送到表达式解释器的表达式
         * @return false 对字符串或 rtexprvalues不应该被解释或重新评估
         */
        public boolean isELInterpreterInput() {
            return el != null || this.isDeferredInput()
                    || this.isDeferredMethodInput();
        }

        /**
         * @return true 如果值是翻译时已知的字符串文字.
         */
        public boolean isLiteral() {
            return !expression && (el == null) && !namedAttribute;
        }

        /**
         * @return {@code true}如果属性是一个实现了DynamicAttributes接口的自定义标签的动态属性. 即, 标签没有声明随机的额外属性.
         */
        public boolean isDynamic() {
            return dynamic;
        }

        public ELNode.Nodes getEL() {
            return el;
        }
    }

    /**
     * Node的有序列表, 用来表示元素的主体, 或者jsp文档的jsp页面.
     */
    public static class Nodes {

        private final List<Node> list;

        private Node.Root root; // null if this is not a page

        private boolean generatedInBuffer;

        public Nodes() {
            list = new Vector<>();
        }

        public Nodes(Node.Root root) {
            this.root = root;
            list = new Vector<>();
            list.add(root);
        }

        /**
         * 追加一个节点到列表
		 * @param n 要添加的节点
         */
        public void add(Node n) {
            list.add(n);
            root = null;
        }

        /**
         * 从列表中移除指定的节点.
		 * @param n 要移除的节点
         */
        public void remove(Node n) {
            list.remove(n);
        }

        /**
         * 使用提供的访问者访问列表中的节点
         * 
		 * @param v 要使用的访问者
         *
         * @throws JasperException 如果在访问节点时发生错误
         */
        public void visit(Visitor v) throws JasperException {
            Iterator<Node> iter = list.iterator();
            while (iter.hasNext()) {
                Node n = iter.next();
                n.accept(v);
            }
        }

        public int size() {
            return list.size();
        }

        public Node getNode(int index) {
            Node n = null;
            try {
                n = list.get(index);
            } catch (ArrayIndexOutOfBoundsException e) {
            }
            return n;
        }

        public Node.Root getRoot() {
            return root;
        }

        public boolean isGeneratedInBuffer() {
            return generatedInBuffer;
        }

        public void setGeneratedInBuffer(boolean g) {
            generatedInBuffer = g;
        }
    }

    /**
     * 访问节点的访问者类. 该类还为Node的每个子类提供默认操作 (i.e. nop).
     * 实际访问者应该扩展该类, 并为其关心的节点提供访问方法.
     */
    public static class Visitor {

        /**
         * 此方法提供了对所有节点都通用的放置操作的位置. 如果需要，请在子访问类中覆盖此项.
         */
        @SuppressWarnings("unused")
        protected void doVisit(Node n) throws JasperException {
            // NOOP by default
        }

        /**
         * 访问节点的主体, 使用当前访问者
         */
        protected void visitBody(Node n) throws JasperException {
            if (n.getBody() != null) {
                n.getBody().visit(this);
            }
        }

        public void visit(Root n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(JspRoot n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(PageDirective n) throws JasperException {
            doVisit(n);
        }

        public void visit(TagDirective n) throws JasperException {
            doVisit(n);
        }

        public void visit(IncludeDirective n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(TaglibDirective n) throws JasperException {
            doVisit(n);
        }

        public void visit(AttributeDirective n) throws JasperException {
            doVisit(n);
        }

        public void visit(VariableDirective n) throws JasperException {
            doVisit(n);
        }

        public void visit(Comment n) throws JasperException {
            doVisit(n);
        }

        public void visit(Declaration n) throws JasperException {
            doVisit(n);
        }

        public void visit(Expression n) throws JasperException {
            doVisit(n);
        }

        public void visit(Scriptlet n) throws JasperException {
            doVisit(n);
        }

        public void visit(ELExpression n) throws JasperException {
            doVisit(n);
        }

        public void visit(IncludeAction n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(ForwardAction n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(GetProperty n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(SetProperty n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(ParamAction n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(ParamsAction n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(FallBackAction n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(UseBean n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(PlugIn n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(CustomTag n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(UninterpretedTag n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(JspElement n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(JspText n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(NamedAttribute n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(JspBody n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(InvokeAction n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(DoBodyAction n) throws JasperException {
            doVisit(n);
            visitBody(n);
        }

        public void visit(TemplateText n) throws JasperException {
            doVisit(n);
        }

        public void visit(JspOutput n) throws JasperException {
            doVisit(n);
        }

        public void visit(AttributeGenerator n) throws JasperException {
            doVisit(n);
        }
    }
}
