package org.apache.tomcat.util.digester;

import java.util.List;

/**
 * 定义Rule实例集合（以及相应的匹配模式），以及匹配策略的实现，该策略选择与解析期间发现的嵌套元素的特定模式匹配的规则.
 */
public interface Rules {


    // ------------------------------------------------------------- Properties


    /**
     * @return 与此Rules实例关联的Digester实例.
     */
    public Digester getDigester();


    /**
     * 设置与此Rules实例关联的Digester实例.
     *
     * @param digester 新关联的Digester实例
     */
    public void setDigester(Digester digester);


    /**
     * @return 将应用于所有后续添加的<code>Rule</code>对象的命名空间URI.
     *
     * @deprecated Unused. Will be removed in Tomcat 9
     */
    @Deprecated
    public String getNamespaceURI();


    /**
     * 设置将应用于所有后续添加的<code>Rule</code>对象的命名空间URI.
     *
     * @param namespaceURI 命名空间URI，必须与所有后续添加的规则匹配; 或<code>null</code>，无论当前的命名空间URI如何，都可以进行匹配
     *
     * @deprecated Unused. Will be removed in Tomcat 9
     */
    @Deprecated
    public void setNamespaceURI(String namespaceURI);


    // --------------------------------------------------------- Public Methods


    /**
     * 注册与指定模式匹配的新Rule实例.
     *
     * @param pattern 要为此规则匹配的嵌套模式
     * @param rule 要注册的规则实例
     */
    public void add(String pattern, Rule rule);


    /**
     * 清除所有现有规则实例注册.
     */
    public void clear();


    /**
     * 返回与指定嵌套模式匹配的所有已注册Rule实例的List, 如果没有匹配, 则为零长度List.
     * 如果多个Rule实例匹配, 它们必须按照最初通过<code>add()</code>方法注册的顺序返回.
     *
     * @param namespaceURI 要为其选择匹配规则的命名空间URI, 或<code>null</code>匹配所有的命名空间URI
     * @param pattern 要匹配的嵌套模式
     * 
     * @return 规则列表
     */
    public List<Rule> match(String namespaceURI, String pattern);


    /**
     * 返回所有已注册Rule实例的列表, 如果没有已注册的Rule实例，则为零长度List.
     * 如果已注册多个Rule实例, 它们必须按照最初通过<code>add()</code>方法注册的顺序返回.
     * 
     * @return 规则列表
     */
    public List<Rule> rules();


}
