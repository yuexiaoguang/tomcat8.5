package org.apache.tomcat.util.digester;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Permission;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.PropertyPermission;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.PermissionCheck;
import org.xml.sax.Attributes;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.ext.Locator2;
import org.xml.sax.helpers.AttributesImpl;


/**
 * <p>通过匹配一系列元素嵌套模式来处理XML输入流, 以执行在解析开始之前添加的规则.
 * 这个软件包的灵感来自于作为Tomcat 3.0和3.1一部分的<code> XmlMapper </ code>类, 但组织有所不同.</p>
 *
 * <p><strong>IMPLEMENTATION NOTE</strong> - 单个Digester实例一次只能在单个线程的上下文中使用,
 * 并且必须先完成对<code>parse()</code>的调用, 然后才能从同一个线程启动另一个.</p>
 *
 * <p><strong>IMPLEMENTATION NOTE</strong> - Xerces 2.0.2中的 bug 阻止了XML模式的支持. 您需要Xerces 2.1/2.3及更高版本才能使此类使用XML模式</p>
 */
public class Digester extends DefaultHandler2 {

    // ---------------------------------------------------------- Static Fields

    protected static IntrospectionUtils.PropertySource propertySource;
    private static boolean propertySourceSet = false;

    static {
        String className = System.getProperty("org.apache.tomcat.util.digester.PROPERTY_SOURCE");
        IntrospectionUtils.PropertySource source = null;
        if (className != null) {
            ClassLoader[] cls = new ClassLoader[] { Digester.class.getClassLoader(),
                    Thread.currentThread().getContextClassLoader() };
            for (int i = 0; i < cls.length; i++) {
                try {
                    Class<?> clazz = Class.forName(className, true, cls[i]);
                    source = (IntrospectionUtils.PropertySource)
                            clazz.getConstructor().newInstance();
                    break;
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    LogFactory.getLog("org.apache.tomcat.util.digester.Digester")
                            .error("Unable to load property source[" + className + "].", t);
                }
            }
        }
        if (source != null) {
            propertySource = source;
            propertySourceSet = true;
        }
    }

    public static void setPropertySource(IntrospectionUtils.PropertySource propertySource) {
        if (!propertySourceSet) {
            Digester.propertySource = propertySource;
            propertySourceSet = true;
        }
    }

    // --------------------------------------------------- Instance Variables


    private class SystemPropertySource implements IntrospectionUtils.PropertySource {
        @Override
        public String getProperty(String key) {
            ClassLoader cl = getClassLoader();
            if (cl instanceof PermissionCheck) {
                Permission p = new PropertyPermission(key, "read");
                if (!((PermissionCheck) cl).check(p)) {
                    return null;
                }
            }
            return System.getProperty(key);
        }
    }


    protected IntrospectionUtils.PropertySource source[] = new IntrospectionUtils.PropertySource[] {
            new SystemPropertySource() };


    /**
     * 当前元素的正文主体.
     */
    protected StringBuilder bodyText = new StringBuilder();


    /**
     * 用于周围元素的正文文本字符串缓冲区堆栈.
     */
    protected ArrayStack<StringBuilder> bodyTexts = new ArrayStack<>();


    /**
     * 其元素为List对象的堆栈, 每个对象包含从Rules.getMatch()返回的Rule对象列表.
     * 输入每个xml元素时, 匹配规则将被推送到此堆栈. 到达结束标记后, 匹配再次弹出.
     * 因此，堆栈的深度与输入xml的当前“嵌套”级别完全相同.
     */
    protected ArrayStack<List<Rule>> matches = new ArrayStack<>(10);

    /**
     * 用于实例化应用程序对象的类加载器.
     * 如果未指定，则使用上下文类加载器或用于加载Digester本身的类加载器，基于<code>useContextClassLoader</code>变量的值.
     */
    protected ClassLoader classLoader = null;


    /**
     * 这个Digester是否已经配置好了.
     */
    protected boolean configured = false;


    /**
     * SAX解析器使用的EntityResolver. 默认情况下，它使用此类
     */
    protected EntityResolver entityResolver;

    /**
     * 已注册的entityValidator的URL, 使用对应的公共标识符作为 Key.
     */
    protected HashMap<String, String> entityValidator = new HashMap<>();


    /**
     * 在解析警告，错误或致命错误时, 通知的应用程序提供的错误处理程序.
     */
    protected ErrorHandler errorHandler = null;


    /**
     * 第一次需要时, 创建的SAXParserFactory.
     */
    protected SAXParserFactory factory = null;

    /**
     * 解析器相关联的Locator.
     */
    protected Locator locator = null;


    /**
     * 嵌套元素处理的当前匹配模式.
     */
    protected String match = "";


    /**
     * 是否需要“命名空间感知”解析器.
     */
    protected boolean namespaceAware = false;


    /**
     * 正在处理的已注册的命名空间.
     * Key是文档中声明的名称空间前缀.  该值是此前缀已映射到的命名空间URI的ArrayStack --
     * 顶部的Stack元素是最新的元素.  (此体系结构是必需的, 因为文档可以为不同的命名空间URI声明相同前缀的嵌套使用).
     */
    protected HashMap<String, ArrayStack<String>> namespaces = new HashMap<>();


    /**
     * CallMethodRule和CallParamRule规则使用的参数堆栈.
     */
    protected ArrayStack<Object> params = new ArrayStack<>();

    /**
     * 用来解析输入流的SAXParser.
     */
    protected SAXParser parser = null;


    /**
     * 正在解析的DTD的公共标识符
     */
    protected String publicId = null;


    /**
     * 用于解析 digester 规则的XMLReader.
     */
    protected XMLReader reader = null;


    /**
     * 堆栈的“根”元素 (换句话说, 弹出的最后一个对象).
     */
    protected Object root = null;


    /**
     * <code>Rules</code>实现, 包含<code>Rule</code>实例和相关匹配策略的集合.
     * 如果在添加第一个规则之前未建立, 将提供默认实现.
     */
    protected Rules rules = null;

    /**
     * 正在构造的对象堆栈.
     */
    protected ArrayStack<Object> stack = new ArrayStack<>();


    /**
     * 是否希望在加载用于实例化新对象的类时, 使用Context ClassLoader.  默认是<code>false</code>.
     */
    protected boolean useContextClassLoader = false;


    /**
     * 是否想要使用验证解析器.
     */
    protected boolean validating = false;


    /**
     * 警告缺少属性和元素.
     */
    protected boolean rulesValidation = false;


    /**
     * 假的属性 map (属性通常用于创建对象).
     */
    protected Map<Class<?>, List<String>> fakeAttributes = null;


    /**
     * 将进行大多数日志记录调用的Log.
     */
    protected Log log = LogFactory.getLog(Digester.class);
    protected static final StringManager sm = StringManager.getManager(Digester.class);

    /**
     * 将进行所有与SAX事件相关的日志记录调用的Log.
     */
    protected Log saxLog = LogFactory.getLog("org.apache.tomcat.util.digester.Digester.sax");


    public Digester() {
        propertySourceSet = true;
        if (propertySource != null) {
            source = new IntrospectionUtils.PropertySource[] { propertySource, source[0] };
        }
    }


    public static void replaceSystemProperties() {
        Log log = LogFactory.getLog(Digester.class);
        if (propertySource != null) {
            IntrospectionUtils.PropertySource[] propertySources =
                    new IntrospectionUtils.PropertySource[] { propertySource };
            Properties properties = System.getProperties();
            Set<String> names = properties.stringPropertyNames();
            for (String name : names) {
                String value = System.getProperty(name);
                if (value != null) {
                    try {
                        String newValue = IntrospectionUtils.replaceProperties(value, null, propertySources);
                        if (!value.equals(newValue)) {
                            System.setProperty(name, newValue);
                        }
                    } catch (Exception e) {
                        log.warn(sm.getString("digester.failedToUpdateSystemProperty", name, value), e);
                    }
                }
            }
        }
    }


    // ------------------------------------------------------------- Properties

    /**
     * 返回指定前缀当前映射的命名空间URI; 否则返回 <code>null</code>.
     * 这些映射在解析文档时动态地进出.
     *
     * @param prefix 要查找的前缀
     * 
     * @return 命名空间 URI
     */
    public String findNamespaceURI(String prefix) {

        ArrayStack<String> stack = namespaces.get(prefix);
        if (stack == null) {
            return (null);
        }
        try {
            return stack.peek();
        } catch (EmptyStackException e) {
            return (null);
        }
    }


    /**
     * 返回类加载器，以便在需要时用于实例化应用程序对象.  基于以下规则确定:
     * <ul>
     * <li><code>setClassLoader()</code>设置的类加载器</li>
     * <li>线程上下文类加载器, 如果它存在, 并且<code>useContextClassLoader</code>属性设置为 true</li>
     * <li>用于加载Digester类本身的类加载器.
     * </ul>
     * 
     * @return 类加载器
     */
    public ClassLoader getClassLoader() {

        if (this.classLoader != null) {
            return (this.classLoader);
        }
        if (this.useContextClassLoader) {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader != null) {
                return (classLoader);
            }
        }
        return (this.getClass().getClassLoader());
    }


    /**
     * 设置类加载器, 以在需要时用于实例化应用程序对象.
     *
     * @param classLoader 要使用的类加载器, 或<code>null</code>使用标准规则
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }


    /**
     * @return 元素堆栈的当前深度.
     */
    public int getCount() {
        return (stack.size());
    }


    /**
     * @return 当前正在处理的XML元素的名称.
     */
    public String getCurrentElementName() {
        String elementName = match;
        int lastSlash = elementName.lastIndexOf('/');
        if (lastSlash >= 0) {
            elementName = elementName.substring(lastSlash + 1);
        }
        return (elementName);
    }


    /**
     * @return 这个Digester的错误处理程序.
     */
    public ErrorHandler getErrorHandler() {
        return (this.errorHandler);
    }


    /**
     * 设置此Digester的错误处理程序.
     *
     * @param errorHandler 错误处理程序
     */
    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }


    /**
     * SAX解析器工厂方法.
     * 
     * @return 将使用的SAXParserFactory, 必要时创建一个.
     * 
     * @throws ParserConfigurationException 创建解析器时出错
     * @throws SAXNotSupportedException 创建解析器时出错
     * @throws SAXNotRecognizedException 创建解析器时出错
     */
    public SAXParserFactory getFactory() throws SAXNotRecognizedException, SAXNotSupportedException,
            ParserConfigurationException {

        if (factory == null) {
            factory = SAXParserFactory.newInstance();

            factory.setNamespaceAware(namespaceAware);
            // 保留xmlns属性
            if (namespaceAware) {
                factory.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
            }

            factory.setValidating(validating);
            if (validating) {
                // 启用DTD验证
                factory.setFeature("http://xml.org/sax/features/validation", true);
                // 启用架构验证
                factory.setFeature("http://apache.org/xml/features/validation/schema", true);
            }
        }
        return (factory);
    }


    /**
     * 设置一个标志, 指示<code>org.xml.sax.XMLReader</code>的底层实现是否支持所请求的功能.
     * 为了有效, 必须在首次调用<code>getParser()</code>方法之前调用此方法, 直接或间接.
     *
     * @param feature 要为其设置状态的功能的名称
     * @param value 此功能的新值
     *
     * @exception ParserConfigurationException 如果发生解析器配置错误
     * @exception SAXNotRecognizedException 如果无法识别属性名称
     * @exception SAXNotSupportedException 如果属性名称被识别但不受支持
     */
    public void setFeature(String feature, boolean value) throws ParserConfigurationException,
            SAXNotRecognizedException, SAXNotSupportedException {

        getFactory().setFeature(feature, value);
    }


    /**
     * @return 与此Digester实例关联的当前Logger
     */
    public Log getLogger() {
        return log;
    }


    /**
     * 设置此Digester的当前记录器.
     * 
     * @param log 将使用的记录器
     */
    public void setLogger(Log log) {
        this.log = log;
    }

    /**
     * 获取用于记录SAX相关信息的记录器.
     * <strong>Note</strong>输出是细粒度的.
     *
     * @return the SAX logger
     */
    public Log getSAXLogger() {
        return saxLog;
    }


    /**
     * 设置用于记录SAX相关信息的记录器.
     * <strong>Note</strong>输出是细粒度的.
     * 
     * @param saxLog Log, 不能是 null
     */
    public void setSAXLogger(Log saxLog) {
        this.saxLog = saxLog;
    }

    /**
     * @return 当前规则匹配路径
     */
    public String getMatch() {
        return match;
    }


    /**
     * @return 创建的解析器的“命名空间感知”标志.
     */
    public boolean getNamespaceAware() {
        return (this.namespaceAware);
    }


    /**
     * 设置创建的解析器的“命名空间感知”标志.
     *
     * @param namespaceAware "命名空间感知"标志
     */
    public void setNamespaceAware(boolean namespaceAware) {
        this.namespaceAware = namespaceAware;
    }


    /**
     * 设置正在解析的当前文件的公共ID.
     * 
     * @param publicId the DTD/Schema public's id.
     */
    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }


    /**
     * @return 正在解析的DTD的公共标识符.
     */
    public String getPublicId() {
        return (this.publicId);
    }


    /**
     * @return 将应用于所有后续添加的<code>Rule</code>对象的名称空间URI.
     *
     * @deprecated Unused. Will be removed in Tomcat 9
     */
    @Deprecated
    public String getRuleNamespaceURI() {
        return (getRules().getNamespaceURI());
    }


    /**
     * 将应用于所有后续添加的<code> Rule </ code>对象的名称空间URI.
     *
     * @param ruleNamespaceURI 名称空间URI, 必须与所有后续添加的规则匹配; 或<code>null</code>, 无论当前的名称空间URI如何, 都可以进行匹配
     *
     * @deprecated Unused. Will be removed in Tomcat 9
     */
    @Deprecated
    public void setRuleNamespaceURI(String ruleNamespaceURI) {
        getRules().setNamespaceURI(ruleNamespaceURI);
    }


    /**
     * @return 用来解析输入流的SAXParser.  如果创建解析器时出现问题, 返回<code>null</code>.
     */
    public SAXParser getParser() {

        // 返回已经创建的解析器
        if (parser != null) {
            return (parser);
        }

        // 创建新解析器
        try {
            parser = getFactory().newSAXParser();
        } catch (Exception e) {
            log.error("Digester.getParser: ", e);
            return (null);
        }
        return (parser);
    }


    /**
     * 返回底层<code>XMLReader</code>实现指定属性的当前值.
     *
     * @param property 要检索的属性名称
     * 
     * @return 属性值
     * 
     * @exception SAXNotRecognizedException 如果无法识别属性名称
     * @exception SAXNotSupportedException 如果属性名称被识别但不受支持
     */
    public Object getProperty(String property)
            throws SAXNotRecognizedException, SAXNotSupportedException {

        return (getParser().getProperty(property));
    }


    /**
     * 返回包含规则集合和相关匹配策略的<code>Rules</code>实现对象.
     * 如果没有建立, 将创建并返回默认实现.
     */
    public Rules getRules() {

        if (this.rules == null) {
            this.rules = new RulesBase();
            this.rules.setDigester(this);
        }
        return (this.rules);
    }


    /**
     * 设置包含规则集合和相关匹配策略的<code>Rules</code>实现对象.
     *
     * @param rules Rules 实现
     */
    public void setRules(Rules rules) {
        this.rules = rules;
        this.rules.setDigester(this);
    }


    /**
     * @return 是否应该使用上下文类加载器.
     */
    public boolean getUseContextClassLoader() {
        return useContextClassLoader;
    }


    /**
     * 是否使用 Context ClassLoader (通过调用 <code>Thread.currentThread().getContextClassLoader()</code>找到的)
     * 解析/加载各种规则中定义的类.
     * 如果不使用Context ClassLoader, 然后类加载默认使用调用类的ClassLoader.
     *
     * @param use 是否使用 Context ClassLoader.
     */
    public void setUseContextClassLoader(boolean use) {
        useContextClassLoader = use;
    }


    /**
     * @return 验证解析器标志.
     */
    public boolean getValidating() {
        return (this.validating);
    }


    /**
     * 验证解析器标志. 必须在第一次调用<code>parse()</code>之前调用它.
     *
     * @param validating
     */
    public void setValidating(boolean validating) {
        this.validating = validating;
    }


    /**
     * @return 规则验证标志.
     */
    public boolean getRulesValidation() {
        return (this.rulesValidation);
    }


    /**
     * 规则验证标志.  必须在第一次调用<code>parse()</code>之前调用它.
     *
     * @param rulesValidation
     */
    public void setRulesValidation(boolean rulesValidation) {
        this.rulesValidation = rulesValidation;
    }


    /**
     * @return 假的属性列表.
     */
    public Map<Class<?>, List<String>> getFakeAttributes() {
        return (this.fakeAttributes);
    }


    /**
     * 确定属性是否为假属性.
     * 
     * @param object 对象
     * @param name 属性名
     * 
     * @return <code>true</code>是一个假属性
     */
    public boolean isFakeAttribute(Object object, String name) {

        if (fakeAttributes == null) {
            return false;
        }
        List<String> result = fakeAttributes.get(object.getClass());
        if (result == null) {
            result = fakeAttributes.get(Object.class);
        }
        if (result == null) {
            return false;
        } else {
            return result.contains(name);
        }
    }


    /**
     * 设置假属性.
     *
     * @param fakeAttributes
     */
    public void setFakeAttributes(Map<Class<?>, List<String>> fakeAttributes) {
        this.fakeAttributes = fakeAttributes;
    }


    /**
     * 返回用于解析输入文档的XMLReader.
     *
     * FIX ME: JAXP/XERCES中存在一个错误，它阻止使用包含带有DTD的模式的解析器.
     * 
     * @return the XML reader
     * @exception SAXException 如果没有XMLReader可以实例化
     */
    public XMLReader getXMLReader() throws SAXException {
        if (reader == null) {
            reader = getParser().getXMLReader();
        }

        reader.setDTDHandler(this);
        reader.setContentHandler(this);

        if (entityResolver == null) {
            reader.setEntityResolver(this);
        } else {
            reader.setEntityResolver(entityResolver);
        }

        reader.setProperty("http://xml.org/sax/properties/lexical-handler", this);

        reader.setErrorHandler(this);
        return reader;
    }

    // ------------------------------------------------- ContentHandler Methods


    /**
     * 处理从XML元素主体接收的字符数据的通知.
     *
     * @param buffer XML文档中的字符
     * @param start 缓冲区的开始偏移量
     * @param length 缓冲区中的字符数
     *
     * @exception SAXException 如果要报告解析错误
     */
    @Override
    public void characters(char buffer[], int start, int length) throws SAXException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("characters(" + new String(buffer, start, length) + ")");
        }

        bodyText.append(buffer, start, length);
    }


    /**
     * 处理到达文档结尾的通知.
     *
     * @exception SAXException 如果解析错误
     */
    @Override
    public void endDocument() throws SAXException {

        if (saxLog.isDebugEnabled()) {
            if (getCount() > 1) {
                saxLog.debug("endDocument():  " + getCount() + " elements left");
            } else {
                saxLog.debug("endDocument()");
            }
        }

        while (getCount() > 1) {
            pop();
        }

        // 对于定义的所有规则触发 "finish" 事件
        Iterator<Rule> rules = getRules().rules().iterator();
        while (rules.hasNext()) {
            Rule rule = rules.next();
            try {
                rule.finish();
            } catch (Exception e) {
                log.error("Finish event threw exception", e);
                throw createSAXException(e);
            } catch (Error e) {
                log.error("Finish event threw error", e);
                throw e;
            }
        }

        // Perform final cleanup
        clear();
    }


    /**
     * 处理到达XML元素结尾的通知.
     *
     * @param namespaceURI - 命名空间URI; 如果元素没有命名空间 URI或者没有执行命名空间处理，则返回空字符串
     * @param localName - 本地名称 (没有前缀); 如果没有执行命名空间处理，则返回空字符串.
     * @param qName - 合格的XML 1.0名称 (带前缀); 如果限定名称不可用, 则为空字符串.
     *   
     * @exception SAXException 如果要报告解析错误
     */
    @Override
    public void endElement(String namespaceURI, String localName, String qName)
            throws SAXException {

        boolean debug = log.isDebugEnabled();

        if (debug) {
            if (saxLog.isDebugEnabled()) {
                saxLog.debug("endElement(" + namespaceURI + "," + localName + "," + qName + ")");
            }
            log.debug("  match='" + match + "'");
            log.debug("  bodyText='" + bodyText + "'");
        }

        // 解析系统属性
        bodyText = updateBodyText(bodyText);

        // 实际的元素名称是localName或qName, 取决于解析器是否可识别命名空间
        String name = localName;
        if ((name == null) || (name.length() < 1)) {
            name = qName;
        }

        // 对于所有相关的规则，触发 "body"事件
        List<Rule> rules = matches.pop();
        if ((rules != null) && (rules.size() > 0)) {
            String bodyText = this.bodyText.toString();
            for (int i = 0; i < rules.size(); i++) {
                try {
                    Rule rule = rules.get(i);
                    if (debug) {
                        log.debug("  Fire body() for " + rule);
                    }
                    rule.body(namespaceURI, name, bodyText);
                } catch (Exception e) {
                    log.error("Body event threw exception", e);
                    throw createSAXException(e);
                } catch (Error e) {
                    log.error("Body event threw error", e);
                    throw e;
                }
            }
        } else {
            if (debug) {
                log.debug("  No rules found matching '" + match + "'.");
            }
            if (rulesValidation) {
                log.warn("  No rules found matching '" + match + "'.");
            }
        }

        // 从周围元素中恢复正文
        bodyText = bodyTexts.pop();

        // 以相反的顺序触发所有相关规则的 "end" 事件
        if (rules != null) {
            for (int i = 0; i < rules.size(); i++) {
                int j = (rules.size() - i) - 1;
                try {
                    Rule rule = rules.get(j);
                    if (debug) {
                        log.debug("  Fire end() for " + rule);
                    }
                    rule.end(namespaceURI, name);
                } catch (Exception e) {
                    log.error("End event threw exception", e);
                    throw createSAXException(e);
                } catch (Error e) {
                    log.error("End event threw error", e);
                    throw e;
                }
            }
        }

        // 恢复上一个匹配表达式
        int slash = match.lastIndexOf('/');
        if (slash >= 0) {
            match = match.substring(0, slash);
        } else {
            match = "";
        }
    }


    /**
     * 处理命名空间前缀超出范围的通知.
     *
     * @param prefix 超出范围的前缀
     *
     * @exception SAXException 如果要报告解析错误
     */
    @Override
    public void endPrefixMapping(String prefix) throws SAXException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("endPrefixMapping(" + prefix + ")");
        }

        // 注销此前缀映射
        ArrayStack<String> stack = namespaces.get(prefix);
        if (stack == null) {
            return;
        }
        try {
            stack.pop();
            if (stack.empty())
                namespaces.remove(prefix);
        } catch (EmptyStackException e) {
            throw createSAXException("endPrefixMapping popped too many times");
        }
    }


    /**
     * 处理从XML元素主体接收的可忽略空格的通知.
     *
     * @param buffer XML文档中的字符
     * @param start 缓冲区中的开始偏移量
     * @param len 缓冲区中的字符数
     *
     * @exception SAXException 如果要报告解析错误
     */
    @Override
    public void ignorableWhitespace(char buffer[], int start, int len) throws SAXException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("ignorableWhitespace(" + new String(buffer, start, len) + ")");
        }

        // No processing required
    }


    /**
     * 处理遇到的处理指令的通知.
     *
     * @param target 处理指令目标
     * @param data 处理指令数据
     *
     * @exception SAXException 如果要报告解析错误
     */
    @Override
    public void processingInstruction(String target, String data) throws SAXException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("processingInstruction('" + target + "','" + data + "')");
        }

        // No processing is required
    }


    /**
     * 获取与解析器关联的文档定位器.
     *
     * @return 文档解析器提供的定位器
     */
    public Locator getDocumentLocator() {
        return locator;
    }

    /**
     * 设置与解析器关联的文档定位器.
     *
     * @param locator 定位器
     */
    @Override
    public void setDocumentLocator(Locator locator) {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("setDocumentLocator(" + locator + ")");
        }

        this.locator = locator;
    }


    /**
     * 处理跳过的实体的通知.
     *
     * @param name 跳过的实体的名称
     *
     * @exception SAXException 如果要报告解析错误
     */
    @Override
    public void skippedEntity(String name) throws SAXException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("skippedEntity(" + name + ")");
        }

        // No processing required
    }


    /**
     * 处理到达文档开头的通知.
     *
     * @exception SAXException 如果要报告解析错误
     */
    @SuppressWarnings("deprecation")
    @Override
    public void startDocument() throws SAXException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("startDocument()");
        }

        if (locator instanceof Locator2) {
            if (root instanceof DocumentProperties.Charset) {
                String enc = ((Locator2) locator).getEncoding();
                if (enc != null) {
                    try {
                        ((DocumentProperties.Charset) root).setCharset(B2CConverter.getCharset(enc));
                    } catch (UnsupportedEncodingException e) {
                        log.warn(sm.getString("disgester.encodingInvalid", enc), e);
                    }
                }
            } else if (root instanceof DocumentProperties.Encoding) {
                ((DocumentProperties.Encoding) root).setEncoding(((Locator2) locator).getEncoding());
            }
        }

        // 确保 digester 配置正确, 因为 digester 可以用作SAX ContentHandler，而不是通过parse()方法.
        configure();
    }


    /**
     * 处理到达XML元素的开始的通知.
     *
     * @param namespaceURI 命名空间URI; 如果元素没有命名空间 URI或者没有执行命名空间处理，则返回空字符串.
     * @param localName 本地名称 (没有前缀); 如果没有执行命名空间处理，则返回空字符串.
     * @param qName 合格的XML 1.0名称 (带前缀); 如果限定名称不可用, 则为空字符串.
     * @param list 附加到元素的属性. 如果没有属性, 它应该是一个空的Attributes对象.
     *   
     * @exception SAXException 如果要报告解析错误
     */
    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes list)
            throws SAXException {
        boolean debug = log.isDebugEnabled();

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("startElement(" + namespaceURI + "," + localName + "," + qName + ")");
        }

        // 解析系统属性
        list = updateAttributes(list);

        // 保存为周围元素累积的正文文本
        bodyTexts.push(bodyText);
        bodyText = new StringBuilder();

        // 实际的元素名称是localName或qName, 取决于解析器是否可识别命名空间
        String name = localName;
        if ((name == null) || (name.length() < 1)) {
            name = qName;
        }

        // 计算当前匹配规则
        StringBuilder sb = new StringBuilder(match);
        if (match.length() > 0) {
            sb.append('/');
        }
        sb.append(name);
        match = sb.toString();
        if (debug) {
            log.debug("  New match='" + match + "'");
        }

        // 对于所有相关的规则，触发 "begin"事件
        List<Rule> rules = getRules().match(namespaceURI, match);
        matches.push(rules);
        if ((rules != null) && (rules.size() > 0)) {
            for (int i = 0; i < rules.size(); i++) {
                try {
                    Rule rule = rules.get(i);
                    if (debug) {
                        log.debug("  Fire begin() for " + rule);
                    }
                    rule.begin(namespaceURI, name, list);
                } catch (Exception e) {
                    log.error("Begin event threw exception", e);
                    throw createSAXException(e);
                } catch (Error e) {
                    log.error("Begin event threw error", e);
                    throw e;
                }
            }
        } else {
            if (debug) {
                log.debug("  No rules found matching '" + match + "'.");
            }
        }
    }


    /**
     * 处理命名空间前缀进入范围的通知.
     *
     * @param prefix 正在声明的前缀
     * @param namespaceURI 相应的名称空间URI
     *
     * @exception SAXException 如果要报告解析错误
     */
    @Override
    public void startPrefixMapping(String prefix, String namespaceURI) throws SAXException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("startPrefixMapping(" + prefix + "," + namespaceURI + ")");
        }

        // 注册此前缀映射
        ArrayStack<String> stack = namespaces.get(prefix);
        if (stack == null) {
            stack = new ArrayStack<>();
            namespaces.put(prefix, stack);
        }
        stack.push(namespaceURI);
    }


    // ----------------------------------------------------- DTDHandler Methods


    /**
     * 接收符号声明事件的通知.
     *
     * @param name 符号名称
     * @param publicId 公共标识符
     * @param systemId 系统标识符
     */
    @Override
    public void notationDecl(String name, String publicId, String systemId) {
        if (saxLog.isDebugEnabled()) {
            saxLog.debug("notationDecl(" + name + "," + publicId + "," + systemId + ")");
        }
    }


    /**
     * 接收未解析的实体声明事件的通知.
     *
     * @param name 未解析的实体名称
     * @param publicId 公共标识符
     * @param systemId 系统标识符
     * @param notation 相关符号的名称
     */
    @Override
    public void unparsedEntityDecl(String name, String publicId, String systemId, String notation) {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug("unparsedEntityDecl(" + name + "," + publicId + "," + systemId + ","
                    + notation + ")");
        }
    }


    // ----------------------------------------------- EntityResolver Methods

    /**
     * 设置解析公共ID和系统ID时, SAX使用的<code>EntityResolver</code>.
     * 必须在第一次调用<code>parse()</code>之前调用它.
     * 
     * @param entityResolver 实现<code>EntityResolver</code> 接口的类.
     */
    public void setEntityResolver(EntityResolver entityResolver) {
        this.entityResolver = entityResolver;
    }


    /**
     * 返回SAX解析器使用的实体解析器.
     */
    public EntityResolver getEntityResolver() {
        return entityResolver;
    }

    @Override
    public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId)
            throws SAXException, IOException {

        if (saxLog.isDebugEnabled()) {
            saxLog.debug(
                    "resolveEntity('" + publicId + "', '" + systemId + "', '" + baseURI + "')");
        }

        // 是否已注册此系统标识符?
        String entityURL = null;
        if (publicId != null) {
            entityURL = entityValidator.get(publicId);
        }

        if (entityURL == null) {
            if (systemId == null) {
                // cannot resolve
                if (log.isDebugEnabled()) {
                    log.debug(" Cannot resolve entity: '" + publicId + "'");
                }
                return (null);

            } else {
                // 尝试使用系统ID解析
                if (log.isDebugEnabled()) {
                    log.debug(" Trying to resolve using system ID '" + systemId + "'");
                }
                entityURL = systemId;
                // 如果不是绝对的，则针对baseURI解析systemId
                if (baseURI != null) {
                    try {
                        URI uri = new URI(systemId);
                        if (!uri.isAbsolute()) {
                            entityURL = new URI(baseURI).resolve(uri).toString();
                        }
                    } catch (URISyntaxException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("Invalid URI '" + baseURI + "' or '" + systemId + "'");
                        }
                    }
                }
            }
        }

        // 返回输入源到备用URL
        if (log.isDebugEnabled()) {
            log.debug(" Resolving to alternate DTD '" + entityURL + "'");
        }

        try {
            return (new InputSource(entityURL));
        } catch (Exception e) {
            throw createSAXException(e);
        }
    }


    // ----------------------------------------------- LexicalHandler Methods

    @Override
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        setPublicId(publicId);
    }


    // ------------------------------------------------- ErrorHandler Methods

    /**
     * 将解析错误的重定向通知转发给应用程序提供的错误处理程序.
     *
     * @param exception 错误信息
     *
     * @exception SAXException 如果发生解析异常
     */
    @Override
    public void error(SAXParseException exception) throws SAXException {

        log.error("Parse Error at line " + exception.getLineNumber() + " column "
                + exception.getColumnNumber() + ": " + exception.getMessage(), exception);
        if (errorHandler != null) {
            errorHandler.error(exception);
        }
    }


    /**
     * 将致命的解析错误的重定向通知转发给应用程序提供的错误处理程序.
     *
     * @param exception 致命的错误信息
     *
     * @exception SAXException 如果发生解析异常
     */
    @Override
    public void fatalError(SAXParseException exception) throws SAXException {

        log.error("Parse Fatal Error at line " + exception.getLineNumber() + " column "
                + exception.getColumnNumber() + ": " + exception.getMessage(), exception);
        if (errorHandler != null) {
            errorHandler.fatalError(exception);
        }
    }


    /**
     * 将解析警告信息的重定向通知转发给应用程序提供的错误处理程序.
     *
     * @param exception 警告信息
     *
     * @exception SAXException 如果发生解析异常
     */
    @Override
    public void warning(SAXParseException exception) throws SAXException {
        if (errorHandler != null) {
            log.warn(
                    "Parse Warning Error at line " + exception.getLineNumber() + " column "
                            + exception.getColumnNumber() + ": " + exception.getMessage(),
                    exception);

            errorHandler.warning(exception);
        }
    }


    // ------------------------------------------------------- Public Methods

    /**
     * 使用此Digester解析指定文件的内容.  返回对象堆栈中的根元素.
     *
     * @param file 包含要解析的XML数据的文件
     * 
     * @return 根对象
     * @exception IOException 如果发生输入/输出错误
     * @exception SAXException 如果发生解析异常
     */
    public Object parse(File file) throws IOException, SAXException {

        configure();
        InputSource input = new InputSource(new FileInputStream(file));
        input.setSystemId("file://" + file.getAbsolutePath());
        getXMLReader().parse(input);
        return (root);
    }


    /**
     * 使用此Digester解析指定输入源的内容. 返回对象堆栈中的根元素.
     *
     * @param input 包含要解析的XML数据的输入源
     * 
     * @return 根对象
     * @exception IOException 如果发生输入/输出错误
     * @exception SAXException 如果发生解析异常
     */
    public Object parse(InputSource input) throws IOException, SAXException {

        configure();
        getXMLReader().parse(input);
        return (root);
    }


    /**
     * 使用此Digester解析指定输入流的内容. 返回对象堆栈中的根元素.
     *
     * @param input 包含要解析的XML数据的输入流
     * 
     * @return 根对象
     * @exception IOException 如果发生输入/输出错误
     * @exception SAXException 如果发生解析异常
     */
    public Object parse(InputStream input) throws IOException, SAXException {

        configure();
        InputSource is = new InputSource(input);
        getXMLReader().parse(is);
        return (root);
    }


    /**
     * <p>为指定的公共标识符注册指定的DTD URL. 必须在第一次调用<code>parse()</code>之前调用它.
     * </p><p>
     * <code>Digester</code>包含一个内部的<code>EntityResolver</code>实现. 映射<code>PUBLICID</code>到 URLs
     * (从中加载资源). 此方法的一个常见用例是为DTD注册本地URL(可能在运行时由类加载器计算).
     * 这允许使用本地版本的性能优势, 而无需确保每个已处理的xml文档上的每个<code>SYSTEM</code> URI都是本地的. 此实现仅提供基本功能.
     * 如果需要更复杂的功能, 使用{@link #setEntityResolver} 设置自定义解析程序.
     * </p><p>
     * <strong>Note:</strong> 设置自定义<code>EntityResolver</code>后, 此方法无效. (设置自定义<code>EntityResolver</code>会覆盖内部实现)
     * </p>
     * @param publicId 要解析的DTD的公共标识符
     * @param entityURL 用于读取此DTD的URL
     */
    public void register(String publicId, String entityURL) {

        if (log.isDebugEnabled()) {
            log.debug("register('" + publicId + "', '" + entityURL + "'");
        }
        entityValidator.put(publicId, entityURL);
    }


    // --------------------------------------------------------- Rule Methods


    /**
     * <p>注册与指定模式匹配的新规则. 此方法在规则上设置<code>Digester</code>属性.</p>
     *
     * @param pattern 元素匹配模式
     * @param rule 要注册的规则
     */
    public void addRule(String pattern, Rule rule) {

        rule.setDigester(this);
        getRules().add(pattern, rule);
    }


    /**
     * 注册RuleSet中定义的一组Rule实例.
     *
     * @param ruleSet 要配置的RuleSet实例
     */
    public void addRuleSet(RuleSet ruleSet) {

        String oldNamespaceURI = getRuleNamespaceURI();
        @SuppressWarnings("deprecation")
        String newNamespaceURI = ruleSet.getNamespaceURI();
        if (log.isDebugEnabled()) {
            if (newNamespaceURI == null) {
                log.debug("addRuleSet() with no namespace URI");
            } else {
                log.debug("addRuleSet() with namespace URI " + newNamespaceURI);
            }
        }
        setRuleNamespaceURI(newNamespaceURI);
        ruleSet.addRuleInstances(this);
        setRuleNamespaceURI(oldNamespaceURI);
    }


    /**
     * 为不接受任何参数的方法添加“调用方法”规则.
     *
     * @param pattern 元素匹配模式
     * @param methodName 要调用的方法名称
     */
    public void addCallMethod(String pattern, String methodName) {
        addRule(pattern, new CallMethodRule(methodName));
    }

    /**
     * 为指定的参数添加“调用方法”规则.
     *
     * @param pattern 元素匹配模式
     * @param methodName 要调用的方法名称
     * @param paramCount 预期的参数数量 (或零, 该元素主体的单个参数)
     */
    public void addCallMethod(String pattern, String methodName, int paramCount) {
        addRule(pattern, new CallMethodRule(methodName, paramCount));
    }


    /**
     * 为指定的参数添加“调用参数”规则.
     *
     * @param pattern 元素匹配模式
     * @param paramIndex 要设置的零相对参数索引 (从这个元素的主体)
     */
    public void addCallParam(String pattern, int paramIndex) {
        addRule(pattern, new CallParamRule(paramIndex));
    }


    /**
     * 为指定的参数添加“工厂创建”规则.
     *
     * @param pattern 元素匹配模式
     * @param creationFactory 以前实例化的ObjectCreationFactory将被使用
     * @param ignoreCreateExceptions 如果为<code>true</code>, 在对象创建期间抛出的任何异常都将被忽略.
     */
    public void addFactoryCreate(String pattern, ObjectCreationFactory creationFactory,
            boolean ignoreCreateExceptions) {

        creationFactory.setDigester(this);
        addRule(pattern, new FactoryCreateRule(creationFactory, ignoreCreateExceptions));
    }

    /**
     * 为指定的参数添加“对象创建”规则.
     *
     * @param pattern 元素匹配模式
     * @param className 要创建的Java类名
     */
    public void addObjectCreate(String pattern, String className) {
        addRule(pattern, new ObjectCreateRule(className));
    }


    /**
     * 为指定的参数添加“对象创建”规则.
     *
     * @param pattern 元素匹配模式
     * @param className 要创建的默认Java类名
     * @param attributeName 属性名称，可选择覆盖要创建的默认Java类名
     */
    public void addObjectCreate(String pattern, String className, String attributeName) {
        addRule(pattern, new ObjectCreateRule(className, attributeName));
    }


    /**
     * 为指定的参数添加“set next”规则.
     *
     * @param pattern 元素匹配模式
     * @param methodName 要在父元素上调用的方法名称
     * @param paramType 预期的参数类型的Java类名
     *  (如果你想使用原始类型, 改为指定相应的Java包装类, 例如<code>java.lang.Boolean</code>对应于<code>boolean</code> 参数)
     */
    public void addSetNext(String pattern, String methodName, String paramType) {
        addRule(pattern, new SetNextRule(methodName, paramType));
    }


    /**
     * 为指定的参数添加“设置属性”规则.
     *
     * @param pattern 元素匹配模式
     */
    public void addSetProperties(String pattern) {
        addRule(pattern, new SetPropertiesRule());
    }


    // --------------------------------------------------- Object Stack Methods


    /**
     * 清除对象堆栈的当前内容.
     * <p>
     * 调用此方法可能允许正确解析另一个相同类型的文档. 但是, 此方法并非用于此目的. 一般来说, 应为要解析的每个文档创建单独的Digester对象.
     */
    public void clear() {

        match = "";
        bodyTexts.clear();
        params.clear();
        publicId = null;
        stack.clear();
        log = null;
        saxLog = null;
        configured = false;
    }


    public void reset() {
        root = null;
        setErrorHandler(null);
        clear();
    }


    /**
     * 返回堆栈顶部对象而不删除它.  如果堆栈中没有对象, 返回 <code>null</code>.
     */
    public Object peek() {
        try {
            return (stack.peek());
        } catch (EmptyStackException e) {
            log.warn("Empty stack (returning null)");
            return (null);
        }
    }


    /**
     * 返回第n个对象，其中0是顶部元素，[getCount() - 1]是底部元素.  如果指定的索引超出范围, 返回<code>null</code>.
     *
     * @param n 所需元素的索引，其中0是堆栈的顶部，1是下一个元素，依此类推.
     * @return 指定的对象
     */
    public Object peek(int n) {
        try {
            return (stack.peek(n));
        } catch (EmptyStackException e) {
            log.warn("Empty stack (returning null)");
            return (null);
        }
    }


    /**
     * 将顶部对象弹出堆栈，然后返回.  如果堆栈中没有对象, 返回 <code>null</code>.
     */
    public Object pop() {
        try {
            return (stack.pop());
        } catch (EmptyStackException e) {
            log.warn("Empty stack (returning null)");
            return (null);
        }
    }


    /**
     * 将新对象推到对象堆栈的顶部.
     *
     * @param object
     */
    public void push(Object object) {

        if (stack.size() == 0) {
            root = object;
        }
        stack.push(object);
    }

    /**
     * 当Digester用作SAXContentHandler时, 此方法允许您访问解析后创建的根对象.
     *
     * @return 解析后创建的根对象; 如果 digester 尚未解析任何XML，则为null.
     */
    public Object getRoot() {
        return root;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * <p>
     * 为此<code>Digester</code>实例的延迟配置提供一个钩子.  默认实现什么都不做, 但是子类可以根据需要覆盖.
     * </p>
     * <p>
     * <strong>Note</strong> 可以多次调用此方法.
     * </p>
     */
    protected void configure() {

        // 不要配置多次
        if (configured) {
            return;
        }

        log = LogFactory.getLog("org.apache.tomcat.util.digester.Digester");
        saxLog = LogFactory.getLog("org.apache.tomcat.util.digester.Digester.sax");

        // 设置配置标志以避免重复
        configured = true;
    }


    /**
     * <p>返回参数堆栈上的顶部对象而不删除它.  如果堆栈中没有对象, 返回<code>null</code>.</p>
     *
     * <p>参数堆栈用于存储 <code>CallMethodRule</code> 参数. See {@link #params}.</p>
     * 
     * @return 参数堆栈上的顶部对象
     */
    public Object peekParams() {

        try {
            return (params.peek());
        } catch (EmptyStackException e) {
            log.warn("Empty stack (returning null)");
            return (null);
        }
    }


    /**
     * <p>弹出参数堆栈中的顶部对象，然后将其返回.  如果堆栈中没有对象, 返回<code>null</code>.</p>
     *
     * <p>参数堆栈用于存储 <code>CallMethodRule</code> 参数. See {@link #params}.</p>
     * 
     * @return 参数堆栈上的顶部对象
     */
    public Object popParams() {
        try {
            if (log.isTraceEnabled()) {
                log.trace("Popping params");
            }
            return (params.pop());
        } catch (EmptyStackException e) {
            log.warn("Empty stack (returning null)");
            return (null);
        }
    }


    /**
     * <p>将新对象推入参数堆栈的顶部.</p>
     *
     * <p>参数堆栈用于存储 <code>CallMethodRule</code> 参数. See {@link #params}.</p>
     *
     * @param object 
     */
    public void pushParams(Object object) {
        if (log.isTraceEnabled()) {
            log.trace("Pushing params");
        }
        params.push(object);
    }

    /**
     * 创建一个SAX异常，该异常还知道发生异常的digester文件中的位置
     * 
     * @param message 错误消息
     * @param e 根异常
     * 
     * @return the new exception
     */
    public SAXException createSAXException(String message, Exception e) {
        if ((e != null) && (e instanceof InvocationTargetException)) {
            Throwable t = e.getCause();
            if (t instanceof ThreadDeath) {
                throw (ThreadDeath) t;
            }
            if (t instanceof VirtualMachineError) {
                throw (VirtualMachineError) t;
            }
            if (t instanceof Exception) {
                e = (Exception) t;
            }
        }
        if (locator != null) {
            String error = "Error at (" + locator.getLineNumber() + ", " + locator.getColumnNumber()
                    + ") : " + message;
            if (e != null) {
                return new SAXParseException(error, locator, e);
            } else {
                return new SAXParseException(error, locator);
            }
        }
        log.error("No Locator!");
        if (e != null) {
            return new SAXException(message, e);
        } else {
            return new SAXException(message);
        }
    }

    /**
     * 建一个SAX异常，该异常还知道发生异常的digester文件中的位置
     * 
     * @param e 根异常
     * 
     * @return the new exception
     */
    public SAXException createSAXException(Exception e) {
        if (e instanceof InvocationTargetException) {
            Throwable t = e.getCause();
            if (t instanceof ThreadDeath) {
                throw (ThreadDeath) t;
            }
            if (t instanceof VirtualMachineError) {
                throw (VirtualMachineError) t;
            }
            if (t instanceof Exception) {
                e = (Exception) t;
            }
        }
        return createSAXException(e.getMessage(), e);
    }

    /**
     * 建一个SAX异常，该异常还知道发生异常的digester文件中的位置
     * 
     * @param message 错误消息
     * 
     * @return the new exception
     */
    public SAXException createSAXException(String message) {
        return createSAXException(message, null);
    }


    // ------------------------------------------------------- Private Methods


   /**
     * 返回一个属性列表，其中包含传入的所有属性，属性值中任何形式为“${xxx}”的文本都替换为系统属性中的相应值.
     */
    private Attributes updateAttributes(Attributes list) {

        if (list.getLength() == 0) {
            return list;
        }

        AttributesImpl newAttrs = new AttributesImpl(list);
        int nAttributes = newAttrs.getLength();
        for (int i = 0; i < nAttributes; ++i) {
            String value = newAttrs.getValue(i);
            try {
                String newValue = IntrospectionUtils.replaceProperties(value, null, source);
                if (value != newValue) {
                    newAttrs.setValue(i, newValue);
                }
            } catch (Exception e) {
                log.warn(sm.getString("digester.failedToUpdateAttributes", newAttrs.getLocalName(i), value), e);
            }
        }

        return newAttrs;
    }


    /**
     * 返回一个包含与输入缓冲区相同内容的StringBuilder，除了表单中${varname}的数据已被系统属性中定义的var的值替换.
     */
    private StringBuilder updateBodyText(StringBuilder bodyText) {
        String in = bodyText.toString();
        String out;
        try {
            out = IntrospectionUtils.replaceProperties(in, null, source);
        } catch (Exception e) {
            return bodyText; // return unchanged data
        }

        if (out == in) {
            // 不需要替换. 不要浪费内存来创建新的缓冲区
            return bodyText;
        } else {
            return new StringBuilder(out);
        }
    }
}
