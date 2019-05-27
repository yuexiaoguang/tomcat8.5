package org.apache.jasper.compiler.tagplugin;


/**
 * 这个接口允许插件作者对当前标签的属性进行查询, 并使用Jasper 资源在标签处理程序调用的地方直接生成 java代码.
 */
public interface TagPluginContext {
    /**
     * @return true 如果标签的主体是脚本.
     */
    boolean isScriptless();

    /**
     * @param attribute 属性名
     * @return true 如果属性在标签中指定
     */
    boolean isAttributeSpecified(String attribute);

    /**
     * @return 插件可以使用的惟一的临时变量名.
     */
    String getTemporaryVariableName();

    /**
     * 生成引入语句
     * @param s 引入类的名称, 允许'*'.
     */
    void generateImport(String s);

    /**
     * 在生成的类中生成一个声明. 这可以用来声明一个内部类, 一个方法, 或类变量.
     * @param id 标识该声明的唯一标识. 它不是声明的一部分, 并用于确保声明只出现一次.
     * 		如果在翻译单元中调用同一ID不止一次的话, 只有第一个声明将被采纳.
     * @param text 声明的文本.
     */
    void generateDeclaration(String id, String text);

    /**
     * 生成java源代码
     * 
     * @param s the scriptlet (raw Java source)
     */
    void generateJavaSource(String s);

    /**
     * @param attribute 属性名
     * @return true 如果指定了属性，并且它的值是一个翻译时常量..
     */
    boolean isConstantAttribute(String attribute);

    /**
     * @param attribute 属性名
     * @return 一个字符串，它是一个常量属性的值. 未定义的, 如果属性不是一个(翻译时)常量.
     *         null 如果属性未指定.
     */
    String getConstantAttribute(String attribute);

    /**
     * 生成自定义标签中属性的值. 代码是Java 表达式.
     * NOTE: 当前无法处理分段属性.
     * @param attribute 指定的属性
     */
    void generateAttribute(String attribute);

    /**
     * 为自定义标签的主体生成代码
     */
    void generateBody();

    /**
     * 放弃此标签处理程序的优化, 并通知Jasper 生成标签处理程序调用, 像往常一样.
     * 如果检测到错误，则应调用, 当标签体被过于复杂的优化.
     */
    void dontUseTagPlugin();

    /**
     * 获取这个自定义标签父级的PluginContext.
     * NOTE: 操作对于PluginContext是可用的, 因此获取是被限制来getPluginAttribute 和 setPluginAttribute, 和查询
     * (即isScriptless(). 不应该调用generate*().
     * @return 父节点的pluginContext.
     *         null 如果父级不是一个自定义标签, 或者如果 pluginConxt不可用(因为useTagPlugin 是 false).
     */
    TagPluginContext getParentContext();

    /**
     * 当前tagplugin上下文中的值关联的属性.
     * 插件属性可用于必须作为一个组一起工作的标签之间的通信. 查看<c:when>作为一个例子.
     * 
     * @param attr 属性名
     * @param value 属性值
     */
    void setPluginAttribute(String attr, Object value);

    /**
     * 获取当前tagplugin上下文中的属性的值.
     * 
     * @param attr The attribute name
     * @return the attribute value
     */
    Object getPluginAttribute(String attr);

    /**
     * 标签是否在标签文件中使用?
     * 
     * @return <code>true</code> if inside a tag file
     */
    boolean isTagFile();
}

