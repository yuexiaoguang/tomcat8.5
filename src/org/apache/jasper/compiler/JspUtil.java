package org.apache.jasper.compiler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Vector;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.tomcat.Jar;
import org.apache.tomcat.util.security.Escape;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;

/**
 * 理想情况下，应该将所有bean容器移到这里.
 */
public class JspUtil {

    private static final String WEB_INF_TAGS = "/WEB-INF/tags/";
    private static final String META_INF_TAGS = "/META-INF/tags/";

    // 请求时表达式的分隔符 (JSP 和 XML 语法)
    private static final String OPEN_EXPR = "<%=";
    private static final String CLOSE_EXPR = "%>";

    private static final String javaKeywords[] = { "abstract", "assert",
            "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long",
            "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while" };

    public static final int CHUNKSIZE = 1024;

    /**
     * 获取潜在的表达式并将其转换为XML格式.
     * @param expression 要转换的表达式
     * @return XML view
     */
    public static String getExprInXml(String expression) {
        String returnString;
        int length = expression.length();

        if (expression.startsWith(OPEN_EXPR) &&
                expression.endsWith(CLOSE_EXPR)) {
            returnString = expression.substring(1, length - 1);
        } else {
            returnString = expression;
        }

        return Escape.xml(returnString);
    }

    /**
     * 检查给定范围是否有效.
     *
     * @param scope 要检查的范围
     * @param n 要检查其值的包含'scope'属性的 Node
     * @param err 错误分派器
     *
     * @throws JasperException 如果范围不是 null, 并和
     * 		&quot;page&quot;, &quot;request&quot;, &quot;session&quot;, &quot;application&quot; 不同
     */
    public static void checkScope(String scope, Node n, ErrorDispatcher err)
            throws JasperException {
        if (scope != null && !scope.equals("page") && !scope.equals("request")
                && !scope.equals("session") && !scope.equals("application")) {
            err.jspError(n, "jsp.error.invalid.scope", scope);
        }
    }

    /**
     * 检查所有强制属性是否存在，以及所有当前属性是否有有效名称. 检查指定为XML样式的属性以及使用jsp:attribute标准行为指定的属性.
     * 
     * @param typeOfTag 标签类型
     * @param n 对应的节点
     * @param validAttributes 具有有效属性的数组
     * @param err 错误分派器
     * 
     * @throws JasperException An error occurred
     */
    public static void checkAttributes(String typeOfTag, Node n,
            ValidAttribute[] validAttributes, ErrorDispatcher err)
            throws JasperException {
        Attributes attrs = n.getAttributes();
        Mark start = n.getStart();
        boolean valid = true;

        // AttributesImpl.removeAttribute is broken, so we do this...
        int tempLength = (attrs == null) ? 0 : attrs.getLength();
        Vector<String> temp = new Vector<>(tempLength, 1);
        for (int i = 0; i < tempLength; i++) {
            @SuppressWarnings("null")  // If attrs==null, tempLength == 0
            String qName = attrs.getQName(i);
            if ((!qName.equals("xmlns")) && (!qName.startsWith("xmlns:"))) {
                temp.addElement(qName);
            }
        }

        // 使用jsp:attribute指定的属性的名称
        Node.Nodes tagBody = n.getBody();
        if (tagBody != null) {
            int numSubElements = tagBody.size();
            for (int i = 0; i < numSubElements; i++) {
                Node node = tagBody.getNode(i);
                if (node instanceof Node.NamedAttribute) {
                    String attrName = node.getAttributeValue("name");
                    temp.addElement(attrName);
                    // Check if this value appear in the attribute of the node
                    if (n.getAttributeValue(attrName) != null) {
                        err.jspError(n,
                                "jsp.error.duplicate.name.jspattribute",
                                attrName);
                    }
                } else {
                    // 没有什么可以在 jsp:attribute之前, 只有jsp:body可以在它之后.
                    break;
                }
            }
        }

        /*
         * 首先检查所有强制属性是否存在.
		 * 如果是这样，那么继续查看其他属性是否对特定标记有效.
         */
        String missingAttribute = null;

        for (int i = 0; i < validAttributes.length; i++) {
            int attrPos;
            if (validAttributes[i].mandatory) {
                attrPos = temp.indexOf(validAttributes[i].name);
                if (attrPos != -1) {
                    temp.remove(attrPos);
                    valid = true;
                } else {
                    valid = false;
                    missingAttribute = validAttributes[i].name;
                    break;
                }
            }
        }

        // 如果缺少强制属性，则抛出异常
        if (!valid) {
            err.jspError(start, "jsp.error.mandatory.attribute", typeOfTag,
                    missingAttribute);
        }

        // 检查是否有指定标记的其他属性.
        int attrLeftLength = temp.size();
        if (attrLeftLength == 0) {
            return;
        }

        // 现在检查其他属性是否有效.
        String attribute = null;

        for (int j = 0; j < attrLeftLength; j++) {
            valid = false;
            attribute = temp.elementAt(j);
            for (int i = 0; i < validAttributes.length; i++) {
                if (attribute.equals(validAttributes[i].name)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                err.jspError(start, "jsp.error.invalid.attribute", typeOfTag,
                        attribute);
            }
        }
        // XXX *could* move EL-syntax validation here... (sb)
    }

    /**
     * 转义从XML定义的5个实体.
     * 
     * @param s 要转义的字符串
     * 
     * @return XML escaped string
     * @deprecated This method will be removed in Tomcat 9
     */
    @Deprecated
    public static String escapeXml(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '\'') {
                sb.append("&apos;");
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static class ValidAttribute {

        private final String name;
        private final boolean mandatory;

        public ValidAttribute(String name, boolean mandatory) {
            this.name = name;
            this.mandatory = mandatory;
        }

        public ValidAttribute(String name) {
            this(name, false);
        }
    }

    /**
     * 将String值转换为 'boolean'.
     * 除了标准转换Boolean.valueOf(s).booleanValue(), "yes"值被转换为'true'. 
     * 如果 's'是 null, 返回'false'.
     *
     * @param s 要转换的字符串
     * 
     * @return 字符串 s关联的boolean值
     */
    public static boolean booleanValue(String s) {
        boolean b = false;
        if (s != null) {
            if (s.equalsIgnoreCase("yes")) {
                b = true;
            } else {
                b = Boolean.parseBoolean(s);
            }
        }
        return b;
    }

    /**
     * 返回给定字符串名称的类或接口关联的<tt>Class</tt>对象.
     *
     * <p><tt>Class</tt>对象通过传递给定字符串名称到<tt>Class.forName()</tt>方法确定, 除非给定的字符串名称表示一个原始类型,
     * 原始类型获取<tt>Class</tt>对象是通过".class"来获取(e.g., "int.class").
     * 
     * @param type 类名, 数组或基元类型
     * @param loader 类加载器
     * 
     * @return 加载的类
     * @throws ClassNotFoundException Loading class failed
     */
    public static Class<?> toClass(String type, ClassLoader loader)
            throws ClassNotFoundException {

        Class<?> c = null;
        int i0 = type.indexOf('[');
        int dims = 0;
        if (i0 > 0) {
            // This is an array. Count the dimensions
            for (int i = 0; i < type.length(); i++) {
                if (type.charAt(i) == '[') {
                    dims++;
                }
            }
            type = type.substring(0, i0);
        }

        if ("boolean".equals(type)) {
            c = boolean.class;
        } else if ("char".equals(type)) {
            c = char.class;
        } else if ("byte".equals(type)) {
            c = byte.class;
        } else if ("short".equals(type)) {
            c = short.class;
        } else if ("int".equals(type)) {
            c = int.class;
        } else if ("long".equals(type)) {
            c = long.class;
        } else if ("float".equals(type)) {
            c = float.class;
        } else if ("double".equals(type)) {
            c = double.class;
        } else if ("void".equals(type)) {
            c = void.class;
        } else if (type.indexOf('[') < 0) {
            c = loader.loadClass(type);
        }

        if (dims == 0) {
            return c;
        }

        if (dims == 1) {
            return java.lang.reflect.Array.newInstance(c, 1).getClass();
        }

        // Array of more than i dimension
        return java.lang.reflect.Array.newInstance(c, new int[dims]).getClass();
    }

    /**
     * 生成一个字符串，表示对EL解释器的调用.
     *
     * @param isTagFile <code>true</code> 如果文件是标签文件而不是JSP
     * @param expression 包括零个或多个 "${}" 表达式字符串
     * @param expectedType 解释结果的预期类型
     * @param fnmapvar 指向函数映射的变量.
     * 
     * @return 表示对EL解释器的调用的字符串.
     */
    public static String interpreterCall(boolean isTagFile, String expression,
            Class<?> expectedType, String fnmapvar) {
        /*
         * 确定要使用的上下文对象.
         */
        String jspCtxt = null;
        if (isTagFile) {
            jspCtxt = "this.getJspContext()";
        } else {
            jspCtxt = "_jspx_page_context";
        }

        /*
         * 确定是否使用预期类型的文本名称, 或如果它是原始的, 对应装箱类型的名称.
         */
        String returnType = expectedType.getCanonicalName();
        String targetType = returnType;
        String primitiveConverterMethod = null;
        if (expectedType.isPrimitive()) {
            if (expectedType.equals(Boolean.TYPE)) {
                returnType = Boolean.class.getName();
                primitiveConverterMethod = "booleanValue";
            } else if (expectedType.equals(Byte.TYPE)) {
                returnType = Byte.class.getName();
                primitiveConverterMethod = "byteValue";
            } else if (expectedType.equals(Character.TYPE)) {
                returnType = Character.class.getName();
                primitiveConverterMethod = "charValue";
            } else if (expectedType.equals(Short.TYPE)) {
                returnType = Short.class.getName();
                primitiveConverterMethod = "shortValue";
            } else if (expectedType.equals(Integer.TYPE)) {
                returnType = Integer.class.getName();
                primitiveConverterMethod = "intValue";
            } else if (expectedType.equals(Long.TYPE)) {
                returnType = Long.class.getName();
                primitiveConverterMethod = "longValue";
            } else if (expectedType.equals(Float.TYPE)) {
                returnType = Float.class.getName();
                primitiveConverterMethod = "floatValue";
            } else if (expectedType.equals(Double.TYPE)) {
                returnType = Double.class.getName();
                primitiveConverterMethod = "doubleValue";
            }
        }

        /*
         * 建立对解释器的基本调用.
         */
        // XXX - 由于目前的标准机器效率不高，需要大量的包装器和适配器，所以我们现在使用专有的调用解释器. 这都要清理一次EL解释器从JSTL移动到自己的项目中.
        // 未来, 这应该被替换通过ExpressionEvaluator.parseExpression() 然后缓存生成的表达式对象. interpreterCall 只需选择预先缓存的表达式中的一个并对其进行评估.
        // 注意： PageContextImpl 实现VariableResolver 并生成 Servlet/SimpleTag 实现 FunctionMapper, 这样机器就已经就位了(mroth).
        targetType = toJavaSourceType(targetType);
        StringBuilder call = new StringBuilder(
                "("
                        + returnType
                        + ") "
                        + "org.apache.jasper.runtime.PageContextImpl.proprietaryEvaluate"
                        + "(" + Generator.quote(expression) + ", " + targetType
                        + ".class, " + "(javax.servlet.jsp.PageContext)" + jspCtxt + ", "
                        + fnmapvar + ")");

        /*
         * 如果需要，添加原始转换器方法.
         */
        if (primitiveConverterMethod != null) {
            call.insert(0, "(");
            call.append(")." + primitiveConverterMethod + "()");
        }

        return call.toString();
    }

    public static String coerceToPrimitiveBoolean(String s,
            boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToBoolean("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "false";
            } else {
                return Boolean.valueOf(s).toString();
            }
        }
    }

    public static String coerceToBoolean(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Boolean) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", java.lang.Boolean.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "new java.lang.Boolean(false)";
            } else {
                // 在翻译时检测格式错误
                return "new java.lang.Boolean(" + Boolean.valueOf(s).toString() + ")";
            }
        }
    }

    public static String coerceToPrimitiveByte(String s,
            boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToByte("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "(byte) 0";
            } else {
                return "((byte)" + Byte.valueOf(s).toString() + ")";
            }
        }
    }

    public static String coerceToByte(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Byte) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", java.lang.Byte.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "new java.lang.Byte((byte) 0)";
            } else {
                // 在翻译时检测格式错误
                return "new java.lang.Byte((byte)" + Byte.valueOf(s).toString() + ")";
            }
        }
    }

    public static String coerceToChar(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToChar("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "(char) 0";
            } else {
                char ch = s.charAt(0);
                // 这个技巧避免了转义问题
                return "((char) " + (int) ch + ")";
            }
        }
    }

    public static String coerceToCharacter(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Character) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", java.lang.Character.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "new java.lang.Character((char) 0)";
            } else {
                char ch = s.charAt(0);
                // 这个技巧避免了转义问题
                return "new java.lang.Character((char) " + (int) ch + ")";
            }
        }
    }

    public static String coerceToPrimitiveDouble(String s,
            boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToDouble("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "(double) 0";
            } else {
                return Double.valueOf(s).toString();
            }
        }
    }

    public static String coerceToDouble(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Double) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", Double.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "new java.lang.Double(0)";
            } else {
                // 在翻译时检测格式错误
                return "new java.lang.Double(" + Double.valueOf(s).toString() + ")";
            }
        }
    }

    public static String coerceToPrimitiveFloat(String s,
            boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToFloat("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "(float) 0";
            } else {
                return Float.valueOf(s).toString() + "f";
            }
        }
    }

    public static String coerceToFloat(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Float) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", java.lang.Float.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "new java.lang.Float(0)";
            } else {
                // 在翻译时检测格式错误
                return "new java.lang.Float(" + Float.valueOf(s).toString() + "f)";
            }
        }
    }

    public static String coerceToInt(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToInt("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "0";
            } else {
                return Integer.valueOf(s).toString();
            }
        }
    }

    public static String coerceToInteger(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Integer) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", java.lang.Integer.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "new java.lang.Integer(0)";
            } else {
                // 在翻译时检测格式错误
                return "new java.lang.Integer(" + Integer.valueOf(s).toString() + ")";
            }
        }
    }

    public static String coerceToPrimitiveShort(String s,
            boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToShort("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "(short) 0";
            } else {
                return "((short) " + Short.valueOf(s).toString() + ")";
            }
        }
    }

    public static String coerceToShort(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Short) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", java.lang.Short.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "new java.lang.Short((short) 0)";
            } else {
                // 在翻译时检测格式错误
                return "new java.lang.Short(\"" + Short.valueOf(s).toString() + "\")";
            }
        }
    }

    public static String coerceToPrimitiveLong(String s,
            boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToLong("
                    + s + ")";
        } else {
            if (s == null || s.length() == 0) {
                return "(long) 0";
            } else {
                return Long.valueOf(s).toString() + "l";
            }
        }
    }

    public static String coerceToLong(String s, boolean isNamedAttribute) {
        if (isNamedAttribute) {
            return "(java.lang.Long) org.apache.jasper.runtime.JspRuntimeLibrary.coerce("
                    + s + ", java.lang.Long.class)";
        } else {
            if (s == null || s.length() == 0) {
                return "new java.lang.Long(0)";
            } else {
                // 在翻译时检测格式错误
                return "new java.lang.Long(" + Long.valueOf(s).toString() + "l)";
            }
        }
    }

    public static InputStream getInputStream(String fname, Jar jar,
            JspCompilationContext ctxt) throws IOException {

        InputStream in = null;

        if (jar != null) {
            String jarEntryName = fname.substring(1, fname.length());
            in = jar.getInputStream(jarEntryName);
        } else {
            in = ctxt.getResourceAsStream(fname);
        }

        if (in == null) {
            throw new FileNotFoundException(Localizer.getMessage(
                    "jsp.error.file.not.found", fname));
        }

        return in;
    }

    public static InputSource getInputSource(String fname, Jar jar, JspCompilationContext ctxt)
        throws IOException {
        InputSource source;
        if (jar != null) {
            String jarEntryName = fname.substring(1, fname.length());
            source = new InputSource(jar.getInputStream(jarEntryName));
            source.setSystemId(jar.getURL(jarEntryName));
        } else {
            source = new InputSource(ctxt.getResourceAsStream(fname));
            source.setSystemId(ctxt.getResource(fname).toExternalForm());
        }
        return source;
    }

    /**
     * 获取与给定标签文件路径相对应的标签处理程序的完全限定类名.
     *
     * @param path 标签文件路径
     * @param urn 标签标识符
     * @param err 错误分派器
     *
     * @return 与给定标签文件路径相对应的标签处理程序的完全限定类名
     * 
     * @throws JasperException 未能为标签生成类名
     */
    public static String getTagHandlerClassName(String path, String urn,
            ErrorDispatcher err) throws JasperException {


        String className = null;
        int begin = 0;
        int index;

        index = path.lastIndexOf(".tag");
        if (index == -1) {
            err.jspError("jsp.error.tagfile.badSuffix", path);
        }

        // 如果删除".tag"后缀, 此标签的完全限定类名可能与其他标签的包名冲突.
        // 对于实例, 标签文件
        //    /WEB-INF/tags/foo.tag
        // 将具有完全限定类名
        //    org.apache.jsp.tag.web.foo
        // 这将与标记文件的包名冲突
        //    /WEB-INF/tags/foo/bar.tag

        index = path.indexOf(WEB_INF_TAGS);
        if (index != -1) {
            className = Constants.TAG_FILE_PACKAGE_NAME + ".web.";
            begin = index + WEB_INF_TAGS.length();
        } else {
            index = path.indexOf(META_INF_TAGS);
            if (index != -1) {
                className = getClassNameBase(urn);
                begin = index + META_INF_TAGS.length();
            } else {
                err.jspError("jsp.error.tagfile.illegalPath", path);
            }
        }

        className += makeJavaPackage(path.substring(begin));

        return className;
    }

    private static String getClassNameBase(String urn) {
        StringBuilder base =
                new StringBuilder(Constants.TAG_FILE_PACKAGE_NAME + ".meta.");
        if (urn != null) {
            base.append(makeJavaPackage(urn));
            base.append('.');
        }
        return base.toString();
    }

    /**
     * 转换给定的到一个java包或完全限定类名的路径
     *
     * @param path 要转换的路径
     *
     * @return java包对应于给定的路径
     */
    public static final String makeJavaPackage(String path) {
        String classNameComponents[] = split(path, "/");
        StringBuilder legalClassNames = new StringBuilder();
        for (int i = 0; i < classNameComponents.length; i++) {
            legalClassNames.append(makeJavaIdentifier(classNameComponents[i]));
            if (i < classNameComponents.length - 1) {
                legalClassNames.append('.');
            }
        }
        return legalClassNames.toString();
    }

    /**
     * 把一个字符串拆分成它的组件.
     * @param path 分割的字符串
     * @param pat 分割模式
     * @return 路径的组件
     */
    private static final String[] split(String path, String pat) {
        Vector<String> comps = new Vector<>();
        int pos = path.indexOf(pat);
        int start = 0;
        while (pos >= 0) {
            if (pos > start) {
                String comp = path.substring(start, pos);
                comps.add(comp);
            }
            start = pos + pat.length();
            pos = path.indexOf(pat, start);
        }
        if (start < path.length()) {
            comps.add(path.substring(start));
        }
        String[] result = new String[comps.size()];
        for (int i = 0; i < comps.size(); i++) {
            result[i] = comps.elementAt(i);
        }
        return result;
    }

    /**
     * 转换给定的标识符为一个合法的java标识符
     *
     * @param identifier 要转化的标识符
     *
     * @return 对应于给定标识符的合法的Java标识符
     */
    public static final String makeJavaIdentifier(String identifier) {
        return makeJavaIdentifier(identifier, true);
    }

    /**
     * 将给定的标识符转换为, 用于JSP Tag文件属性名的合法 Java 标识符.
     *
     * @param identifier 转换标识符
     *
     * @return 对应于给定标识符的合法 Java标识符
     */
    public static final String makeJavaIdentifierForAttribute(String identifier) {
        return makeJavaIdentifier(identifier, false);
    }

    /**
     * 转换给定的标识符为一个合法的java标识符
     *
     * @param identifier 要转化的标识符
     *
     * @return 对应于给定标识符的合法的Java标识符
     */
    private static final String makeJavaIdentifier(String identifier,
            boolean periodToUnderscore) {
        StringBuilder modifiedIdentifier = new StringBuilder(identifier.length());
        if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
            modifiedIdentifier.append('_');
        }
        for (int i = 0; i < identifier.length(); i++) {
            char ch = identifier.charAt(i);
            if (Character.isJavaIdentifierPart(ch) &&
                    (ch != '_' || !periodToUnderscore)) {
                modifiedIdentifier.append(ch);
            } else if (ch == '.' && periodToUnderscore) {
                modifiedIdentifier.append('_');
            } else {
                modifiedIdentifier.append(mangleChar(ch));
            }
        }
        if (isJavaKeyword(modifiedIdentifier.toString())) {
            modifiedIdentifier.append('_');
        }
        return modifiedIdentifier.toString();
    }

    /**
     * 用指定的字符来创建一个合法的java类的名称.
     * 
     * @param ch The character
     * @return 字符串替换字符
     */
    public static final String mangleChar(char ch) {
        char[] result = new char[5];
        result[0] = '_';
        result[1] = Character.forDigit((ch >> 12) & 0xf, 16);
        result[2] = Character.forDigit((ch >> 8) & 0xf, 16);
        result[3] = Character.forDigit((ch >> 4) & 0xf, 16);
        result[4] = Character.forDigit(ch & 0xf, 16);
        return new String(result);
    }

    /**
     * 测试参数是否是一个java关键字.
     * 
     * @param key The name
     * @return <code>true</code>如果是一个java标识符
     */
    public static boolean isJavaKeyword(String key) {
        int i = 0;
        int j = javaKeywords.length;
        while (i < j) {
            int k = (i + j) / 2;
            int result = javaKeywords[k].compareTo(key);
            if (result == 0) {
                return true;
            }
            if (result < 0) {
                i = k + 1;
            } else {
                j = k;
            }
        }
        return false;
    }

    static InputStreamReader getReader(String fname, String encoding,
            Jar jar, JspCompilationContext ctxt, ErrorDispatcher err)
            throws JasperException, IOException {

        return getReader(fname, encoding, jar, ctxt, err, 0);
    }

    static InputStreamReader getReader(String fname, String encoding,
            Jar jar, JspCompilationContext ctxt, ErrorDispatcher err, int skip)
            throws JasperException, IOException {

        InputStreamReader reader = null;
        InputStream in = getInputStream(fname, jar, ctxt);
        for (int i = 0; i < skip; i++) {
            in.read();
        }
        try {
            reader = new InputStreamReader(in, encoding);
        } catch (UnsupportedEncodingException ex) {
            err.jspError("jsp.error.unsupported.encoding", encoding);
        }

        return reader;
    }

    /**
     * 处理来自TLD的输入
     * 'java.lang.Object' -&gt;
     * 'java.lang.Object.class' 'int' -&gt;
     * 'int.class' 'void' -&gt;
     * 'Void.TYPE' 'int[]' -&gt;
     * 'int[].class'
     *
     * @param type 来自TLD的类型
     * @return the Java type
     */
    public static String toJavaSourceTypeFromTld(String type) {
        if (type == null || "void".equals(type)) {
            return "java.lang.Void.TYPE";
        }
        return type + ".class";
    }

    /**
     * Class.getName() 返回"[[[<et>"形式的数组, 其中ET, 元素类型可以是 ZBCDFIJS 或 L<classname>中的一个;
     * 它转换成可以被javac理解的形式.
     * 
     * @param type 要转换的类型
     * @return Java源的等效类型
     */
    public static String toJavaSourceType(String type) {

        if (type.charAt(0) != '[') {
            return type;
        }

        int dims = 1;
        String t = null;
        for (int i = 1; i < type.length(); i++) {
            if (type.charAt(i) == '[') {
                dims++;
            } else {
                switch (type.charAt(i)) {
                case 'Z': t = "boolean"; break;
                case 'B': t = "byte"; break;
                case 'C': t = "char"; break;
                case 'D': t = "double"; break;
                case 'F': t = "float"; break;
                case 'I': t = "int"; break;
                case 'J': t = "long"; break;
                case 'S': t = "short"; break;
                case 'L': t = type.substring(i+1, type.indexOf(';')); break;
                }
                break;
            }
        }

        if (t == null) {
            // Should never happen
            throw new IllegalArgumentException("Unable to extract type from [" +
                    type + "]");
        }

        StringBuilder resultType = new StringBuilder(t);
        for (; dims > 0; dims--) {
            resultType.append("[]");
        }
        return resultType.toString();
    }
}
