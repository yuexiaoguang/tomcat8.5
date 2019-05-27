package org.apache.jasper.compiler;

import java.io.ByteArrayInputStream;
import java.io.CharArrayWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ListIterator;

import javax.servlet.jsp.tagext.PageData;

import org.apache.jasper.JasperException;
import org.apache.tomcat.util.security.Escape;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

/**
 * <tt>javax.servlet.jsp.tagext.PageData</tt>实现类建立给定页面的XML视图.
 *
 * XML视图是在两个通道中构建的:
 *
 * 第一个通道时, FirstPassVisitor 收集顶层jsp:root的属性以及包含的页面的jsp:root属性, 并将它们添加到XML视图的 jsp:root元素.
 * 此外, 任何taglib 指令被转换成 xmlns: 属性并被添加到XML视图的jsp:root元素.
 * 这个传递忽略任何不同于JspRoot和TaglibDirective的节点.
 *
 * 第二个通道时, SecondPassVisitor 产生 XML 视图, 采用组合的 jsp:root 属性在第一个通道和任何剩余的页面节点中确定(忽略所有的JspRoot和 TaglibDirective节点).
 */
class PageDataImpl extends PageData implements TagConstants {

    private static final String JSP_VERSION = "2.0";
    private static final String CDATA_START_SECTION = "<![CDATA[\n";
    private static final String CDATA_END_SECTION = "]]>\n";

    // 用于建立XML 视图
    private final StringBuilder buf;

    /**
     * @param page 生成XML视图的页面节点
     * @param compiler 此页面的编译器
     *
     * @throws JasperException If an error occurs
     */
    public PageDataImpl(Node.Nodes page, Compiler compiler)
                throws JasperException {

        // First pass
        FirstPassVisitor firstPass = new FirstPassVisitor(page.getRoot(),
                                                          compiler.getPageInfo());
        page.visit(firstPass);

        // Second pass
        buf = new StringBuilder();
        SecondPassVisitor secondPass
            = new SecondPassVisitor(page.getRoot(), buf, compiler,
                                    firstPass.getJspIdPrefix());
        page.visit(secondPass);
    }

    /**
     * 返回XML视图的输入流.
     *
     * @return xml视图的输入流
     */
    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(
                buf.toString().getBytes(StandardCharsets.UTF_8));
    }

    /*
     * 第一个通道访问者, 对于JspRoot 节点 (表示jsp:root 元素)和TablibDirective 节点, 忽略其他节点.
     *
     * 这个访问者的目的是收集顶层jsp:root的属性和包含的页面中的jsp:root的属性, 并将它们添加到XML视图的 jsp:root元素.
     * 此外, 任何taglib 指令被转换成 xmlns: 属性并被添加到XML视图的jsp:root元素.
     */
    private static class FirstPassVisitor
                extends Node.Visitor implements TagConstants {

        private final Node.Root root;
        private final AttributesImpl rootAttrs;
        private final PageInfo pageInfo;

        // 'id'属性的前缀
        private String jspIdPrefix;

        public FirstPassVisitor(Node.Root root, PageInfo pageInfo) {
            this.root = root;
            this.pageInfo = pageInfo;
            this.rootAttrs = new AttributesImpl();
            this.rootAttrs.addAttribute("", "", "version", "CDATA",
                                        JSP_VERSION);
            this.jspIdPrefix = "jsp";
        }

        @Override
        public void visit(Node.Root n) throws JasperException {
            visitBody(n);
            if (n == root) {
                /*
                 * 顶级页面.
				 *
				 * 如果没有现在, 只添加xmlns:jsp="http://java.sun.com/JSP/Page"属性.
                 */
                if (!JSP_URI.equals(rootAttrs.getValue("xmlns:jsp"))) {
                    rootAttrs.addAttribute("", "", "xmlns:jsp", "CDATA",
                                           JSP_URI);
                }

                if (pageInfo.isJspPrefixHijacked()) {
                    /*
                     * 'jsp'前缀已经被 hijacked, 即, 绑定到除JSP命名空间以外的命名空间. 意味着添加一个'id'属性到每个元素, 不能使用'jsp'前缀.
				     * 因此, 创建一个'id'属性使用的新的前缀(这在翻译单元是唯一的), 并将其绑定到JSP命名空间
                     */
                    jspIdPrefix += "jsp";
                    while (pageInfo.containsPrefix(jspIdPrefix)) {
                        jspIdPrefix += "jsp";
                    }
                    rootAttrs.addAttribute("", "", "xmlns:" + jspIdPrefix,
                                           "CDATA", JSP_URI);
                }

                root.setAttributes(rootAttrs);
            }
        }

        @Override
        public void visit(Node.JspRoot n) throws JasperException {
            addAttributes(n.getTaglibAttributes());
            addAttributes(n.getNonTaglibXmlnsAttributes());
            addAttributes(n.getAttributes());

            visitBody(n);
        }

        /*
         * taglib伪指令转换成jsp:root元素的 "xmlns:..." 属性.
         */
        @Override
        public void visit(Node.TaglibDirective n) throws JasperException {
            Attributes attrs = n.getAttributes();
            if (attrs != null) {
                String qName = "xmlns:" + attrs.getValue("prefix");
                /*
                 * 根据 org.xml.sax.helpers.AttributesImpl的javadoc,
				 * addAttribute方法不会检查指定的属性是否已经包含在列表中: 这是应用的责任!
                 */
                if (rootAttrs.getIndex(qName) == -1) {
                    String location = attrs.getValue("uri");
                    if (location != null) {
                        if (location.startsWith("/")) {
                            location = URN_JSPTLD + location;
                        }
                        rootAttrs.addAttribute("", "", qName, "CDATA",
                                               location);
                    } else {
                        location = attrs.getValue("tagdir");
                        rootAttrs.addAttribute("", "", qName, "CDATA",
                                               URN_JSPTAGDIR + location);
                    }
                }
            }
        }

        public String getJspIdPrefix() {
            return jspIdPrefix;
        }

        private void addAttributes(Attributes attrs) {
            if (attrs != null) {
                int len = attrs.getLength();

                for (int i=0; i<len; i++) {
                    String qName = attrs.getQName(i);
                    if ("version".equals(qName)) {
                        continue;
                    }

                    // Bugzilla 35252: http://bz.apache.org/bugzilla/show_bug.cgi?id=35252
                    if(rootAttrs.getIndex(qName) == -1) {
                        rootAttrs.addAttribute(attrs.getURI(i),
                                               attrs.getLocalName(i),
                                               qName,
                                               attrs.getType(i),
                                               attrs.getValue(i));
                    }
                }
            }
        }
    }


    /*
     * 第二个通道访问者负责生成XML 视图并为每个元素分配一个唯一 的jsp:id 属性.
     */
    private static class SecondPassVisitor extends Node.Visitor
                implements TagConstants {

        private final Node.Root root;
        private final StringBuilder buf;
        private final Compiler compiler;
        private final String jspIdPrefix;
        private boolean resetDefaultNS = false;

        // jsp:id 属性的当前值
        private int jspId;

        public SecondPassVisitor(Node.Root root, StringBuilder buf,
                                 Compiler compiler, String jspIdPrefix) {
            this.root = root;
            this.buf = buf;
            this.compiler = compiler;
            this.jspIdPrefix = jspIdPrefix;
        }

        /*
         * Visits root node.
         */
        @Override
        public void visit(Node.Root n) throws JasperException {
            if (n == this.root) {
                // top-level page
                appendXmlProlog();
                appendTag(n);
            } else {
                boolean resetDefaultNSSave = resetDefaultNS;
                if (n.isXmlSyntax()) {
                    resetDefaultNS = true;
                }
                visitBody(n);
                resetDefaultNS = resetDefaultNSSave;
            }
        }

        /*
         * 访问xml语法中的jsp页面的jsp:root元素.
		 *
		 * 任何嵌套的jsp:root元素(通过包含指令包含的页面)被忽略.
         */
        @Override
    public void visit(Node.JspRoot n) throws JasperException {
            visitBody(n);
        }

        @Override
    public void visit(Node.PageDirective n) throws JasperException {
            appendPageDirective(n);
        }

        @Override
    public void visit(Node.IncludeDirective n) throws JasperException {
            // expand in place
            visitBody(n);
        }

        @Override
    public void visit(Node.Comment n) throws JasperException {
            // Comments are ignored in XML view
        }

        @Override
    public void visit(Node.Declaration n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.Expression n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.Scriptlet n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.JspElement n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.ELExpression n) throws JasperException {
            if (!n.getRoot().isXmlSyntax()) {
                buf.append("<").append(JSP_TEXT_ACTION);
                buf.append(" ");
                buf.append(jspIdPrefix);
                buf.append(":id=\"");
                buf.append(jspId++).append("\">");
            }
            buf.append("${");
            buf.append(Escape.xml(n.getText()));
            buf.append("}");
            if (!n.getRoot().isXmlSyntax()) {
                buf.append(JSP_TEXT_ACTION_END);
            }
            buf.append("\n");
        }

        @Override
    public void visit(Node.IncludeAction n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.ForwardAction n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.GetProperty n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.SetProperty n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.ParamAction n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.ParamsAction n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.FallBackAction n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.UseBean n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.PlugIn n) throws JasperException {
            appendTag(n);
        }

        @Override
        public void visit(Node.NamedAttribute n) throws JasperException {
            appendTag(n);
        }

        @Override
        public void visit(Node.JspBody n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.CustomTag n) throws JasperException {
            boolean resetDefaultNSSave = resetDefaultNS;
            appendTag(n, resetDefaultNS);
            resetDefaultNS = resetDefaultNSSave;
        }

        @Override
    public void visit(Node.UninterpretedTag n) throws JasperException {
            boolean resetDefaultNSSave = resetDefaultNS;
            appendTag(n, resetDefaultNS);
            resetDefaultNS = resetDefaultNSSave;
        }

        @Override
    public void visit(Node.JspText n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.DoBodyAction n) throws JasperException {
            appendTag(n);
        }

        @Override
        public void visit(Node.InvokeAction n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.TagDirective n) throws JasperException {
            appendTagDirective(n);
        }

        @Override
    public void visit(Node.AttributeDirective n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.VariableDirective n) throws JasperException {
            appendTag(n);
        }

        @Override
    public void visit(Node.TemplateText n) throws JasperException {
            /*
             * 如果模板文本来自JSP语法编写的JSP页面, 为它创建一个jsp:text元素(JSP 5.3.2).
             */
            appendText(n.getText(), !n.getRoot().isXmlSyntax());
        }

        /*
         * 追加指定的标签, 包括它的主体, 到XML 视图.
         */
        private void appendTag(Node n) throws JasperException {
            appendTag(n, false);
        }

        /*
         * 追加指定的标签, 包括它的主体, 到XML 视图, 并可选地将默认命名空间重置为 "", 如果为指定.
         */
        private void appendTag(Node n, boolean addDefaultNS)
                throws JasperException {

            Node.Nodes body = n.getBody();
            String text = n.getText();

            buf.append("<").append(n.getQName());
            buf.append("\n");

            printAttributes(n, addDefaultNS);
            buf.append("  ").append(jspIdPrefix).append(":id").append("=\"");
            buf.append(jspId++).append("\"\n");

            if (ROOT_ACTION.equals(n.getLocalName()) || body != null
                        || text != null) {
                buf.append(">\n");
                if (ROOT_ACTION.equals(n.getLocalName())) {
                    if (compiler.getCompilationContext().isTagFile()) {
                        appendTagDirective();
                    } else {
                        appendPageDirective();
                    }
                }
                if (body != null) {
                    body.visit(this);
                } else {
                    appendText(text, false);
                }
                buf.append("</" + n.getQName() + ">\n");
            } else {
                buf.append("/>\n");
            }
        }

        /*
         * 添加页面指令与给定的属性到XML视图.
		 *
		 * 因为页面指令的导入属性是惟一允许在同一文档中多次出现的页面属性, 由于XML只允许单个值属性,
		 * 多个导入属性的值必须合并为一个, 用逗号分隔.
		 *
		 * 如果给定的页面指令包含 'contentType'和'pageEncoding'属性, 忽略它, 已经附加了只包含这两个属性的页面指令.
         */
        private void appendPageDirective(Node.PageDirective n) {
            boolean append = false;
            Attributes attrs = n.getAttributes();
            int len = (attrs == null) ? 0 : attrs.getLength();
            for (int i=0; i<len; i++) {
                @SuppressWarnings("null")  // If attrs==null, len == 0
                String attrName = attrs.getQName(i);
                if (!"pageEncoding".equals(attrName)
                        && !"contentType".equals(attrName)) {
                    append = true;
                    break;
                }
            }
            if (!append) {
                return;
            }

            buf.append("<").append(n.getQName());
            buf.append("\n");

            // append jsp:id
            buf.append("  ").append(jspIdPrefix).append(":id").append("=\"");
            buf.append(jspId++).append("\"\n");

            // append remaining attributes
            for (int i=0; i<len; i++) {
                @SuppressWarnings("null")  // If attrs==null, len == 0
                String attrName = attrs.getQName(i);
                if ("import".equals(attrName) || "contentType".equals(attrName)
                        || "pageEncoding".equals(attrName)) {
                    /*
                     * 页面指令的'import'属性被认为是进一步下降, 并且它的'pageEncoding' 和 'contentType'属性被忽略,
				     * 因为已经附加了一个新的只包含这两个属性的页面指令
                     */
                    continue;
                }
                String value = attrs.getValue(i);
                buf.append("  ").append(attrName).append("=\"");
                buf.append(JspUtil.getExprInXml(value)).append("\"\n");
            }
            if (n.getImports().size() > 0) {
                // 引入的类/包连接的名字
                boolean first = true;
                ListIterator<String> iter = n.getImports().listIterator();
                while (iter.hasNext()) {
                    if (first) {
                        first = false;
                        buf.append("  import=\"");
                    } else {
                        buf.append(",");
                    }
                    buf.append(JspUtil.getExprInXml(iter.next()));
                }
                buf.append("\"\n");
            }
            buf.append("/>\n");
        }

        /*
         * 使用 'pageEncoding' 和 'contentType'属性追加一个页面指令.
		 *
		 * 'pageEncoding'属性的值被硬编码为UTF-8, 而'contentType'属性的值 attribute, 与容器传递给
		 * ServletResponse.setContentType()的值相同, 来自pageInfo.
         */
        private void appendPageDirective() {
            buf.append("<").append(JSP_PAGE_DIRECTIVE_ACTION);
            buf.append("\n");

            // append jsp:id
            buf.append("  ").append(jspIdPrefix).append(":id").append("=\"");
            buf.append(jspId++).append("\"\n");
            buf.append("  ").append("pageEncoding").append("=\"UTF-8\"\n");
            buf.append("  ").append("contentType").append("=\"");
            buf.append(compiler.getPageInfo().getContentType()).append("\"\n");
            buf.append("/>\n");
        }

        /*
         * 使用指定的属性追加标签指令给XML视图.
		 *
		 * 如果给定的标签指令只包含一个 'pageEncoding'属性, 忽略它, 已经附加了一个包含此属性的标签指令.
         */
        private void appendTagDirective(Node.TagDirective n)
                throws JasperException {

            boolean append = false;
            Attributes attrs = n.getAttributes();
            int len = (attrs == null) ? 0 : attrs.getLength();
            for (int i=0; i<len; i++) {
                @SuppressWarnings("null")  // If attrs==null, len == 0
                String attrName = attrs.getQName(i);
                if (!"pageEncoding".equals(attrName)) {
                    append = true;
                    break;
                }
            }
            if (!append) {
                return;
            }

            appendTag(n);
        }

        /*
         * 追加包含单个'pageEncoding'属性的标签指令, 其值被硬编码为 UTF-8.
         */
        private void appendTagDirective() {
            buf.append("<").append(JSP_TAG_DIRECTIVE_ACTION);
            buf.append("\n");

            // append jsp:id
            buf.append("  ").append(jspIdPrefix).append(":id").append("=\"");
            buf.append(jspId++).append("\"\n");
            buf.append("  ").append("pageEncoding").append("=\"UTF-8\"\n");
            buf.append("/>\n");
        }

        private void appendText(String text, boolean createJspTextElement) {
            if (createJspTextElement) {
                buf.append("<").append(JSP_TEXT_ACTION);
                buf.append("\n");

                // append jsp:id
                buf.append("  ").append(jspIdPrefix).append(":id").append("=\"");
                buf.append(jspId++).append("\"\n");
                buf.append(">\n");

                appendCDATA(text);
                buf.append(JSP_TEXT_ACTION_END);
                buf.append("\n");
            } else {
                appendCDATA(text);
            }
        }

        /*
         * 将给定的文本作为一个 CDATA 部分追加到XML 视图, 除非文本已经被标记为CDATA.
         */
        private void appendCDATA(String text) {
            buf.append(CDATA_START_SECTION);
            buf.append(escapeCDATA(text));
            buf.append(CDATA_END_SECTION);
        }

        /*
         * 转义给定文本中所有 "]]>" (将其替换为 "]]&gt;"), 因此它可以在CDATA 部分中包含.
         */
        private String escapeCDATA(String text) {
            if( text==null ) return "";
            int len = text.length();
            CharArrayWriter result = new CharArrayWriter(len);
            for (int i=0; i<len; i++) {
                if (((i+2) < len)
                        && (text.charAt(i) == ']')
                        && (text.charAt(i+1) == ']')
                        && (text.charAt(i+2) == '>')) {
                    // match found
                    result.write(']');
                    result.write(']');
                    result.write('&');
                    result.write('g');
                    result.write('t');
                    result.write(';');
                    i += 2;
                } else {
                    result.write(text.charAt(i));
                }
            }
            return result.toString();
        }

        /*
         * 追加指定的Node的属性到XML 视图.
         */
        private void printAttributes(Node n, boolean addDefaultNS) {

            /*
             * 追加表示标签库的"xmlns"属性
             */
            Attributes attrs = n.getTaglibAttributes();
            int len = (attrs == null) ? 0 : attrs.getLength();
            for (int i=0; i<len; i++) {
                @SuppressWarnings("null")  // If attrs==null, len == 0
                String name = attrs.getQName(i);
                String value = attrs.getValue(i);
                buf.append("  ").append(name).append("=\"").append(value).append("\"\n");
            }

            /*
             * 追加不表示标签库的"xmlns"属性
             */
            attrs = n.getNonTaglibXmlnsAttributes();
            len = (attrs == null) ? 0 : attrs.getLength();
            boolean defaultNSSeen = false;
            for (int i=0; i<len; i++) {
                @SuppressWarnings("null")  // If attrs==null, len == 0
                String name = attrs.getQName(i);
                String value = attrs.getValue(i);
                buf.append("  ").append(name).append("=\"").append(value).append("\"\n");
                defaultNSSeen |= "xmlns".equals(name);
            }
            if (addDefaultNS && !defaultNSSeen) {
                buf.append("  xmlns=\"\"\n");
            }
            resetDefaultNS = false;

            /*
             * 追加所有的其它属性
             */
            attrs = n.getAttributes();
            len = (attrs == null) ? 0 : attrs.getLength();
            for (int i=0; i<len; i++) {
                @SuppressWarnings("null")  // If attrs==null, len == 0
                String name = attrs.getQName(i);
                String value = attrs.getValue(i);
                buf.append("  ").append(name).append("=\"");
                buf.append(JspUtil.getExprInXml(value)).append("\"\n");
            }
        }

        /*
         * 追加具有编码声明的XML 前言.
         */
        private void appendXmlProlog() {
            buf.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
        }
    }
}

