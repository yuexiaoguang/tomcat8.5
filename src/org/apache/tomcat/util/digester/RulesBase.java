package org.apache.tomcat.util.digester;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;


/**
 * <p><code>Rules</code>接口的默认实现, 支持标准规则匹配行为.  此类还可以用作专门的<code>Rules</code>实现的基类.</p>
 *
 * <p>此类实现的匹配策略支持两种不同类型的模式匹配规则:</p>
 * <ul>
 * <li><em>Exact Match</em> - 模式 "a/b/c" 精确匹配<code>&lt;c&gt;</code>元素, 其嵌套在一个 <code>&lt;b&gt;</code>元素中,
 * 		b嵌套在一个<code>&lt;a&gt;</code>元素中.</li>
 * <li><em>Tail Match</em> - 模式 "&#42;/a/b" 匹配<code>&lt;b&gt;</code>元素, 其嵌套在一个 <code>&lt;a&gt;</code>元素中,
 *      无论这对被嵌套多深.</li>
 * </ul>
 */
public class RulesBase implements Rules {


    // ----------------------------------------------------- Instance Variables


    /**
     * 已注册的Rule实例集, 使用匹配模式作为Key.
     * 每个值都是一个List，其中包含该模式的规则，按照它们最初注册的顺序.
     */
    protected HashMap<String,List<Rule>> cache = new HashMap<>();


    /**
     * 与此Rules实例关联的Digester实例.
     */
    protected Digester digester = null;


    /**
     * 与随后添加的<code>Rule</code>对象相关的命名空间URI, 或<code>null</code>匹配独立的命名空间.
     *
     * @deprecated Unused. Will be removed in Tomcat 9.0.x
     */
    @Deprecated
    protected String namespaceURI = null;


    /**
     * 已注册的Rule实例集, 按照他们最初注册的顺序.
     */
    protected ArrayList<Rule> rules = new ArrayList<>();


    // ------------------------------------------------------------- Properties


    /**
     * 返回与此Rules实例关联的Digester实例.
     */
    @Override
    public Digester getDigester() {
        return (this.digester);
    }


    /**
     * 设置与此Rules实例关联的Digester实例.
     *
     * @param digester 关联的Digester实例
     */
    @Override
    public void setDigester(Digester digester) {

        this.digester = digester;
        Iterator<Rule> items = rules.iterator();
        while (items.hasNext()) {
            Rule item = items.next();
            item.setDigester(digester);
        }
    }


    /**
     * 返回将应用于所有后续添加的<code>Rule</code>对象的命名空间URI.
     */
    @Override
    public String getNamespaceURI() {
        return (this.namespaceURI);
    }


    /**
     * 设置将应用于所有后续添加的<code>Rule</code>对象的命名空间URI.
     *
     * @param namespaceURI 命名空间URI，必须与所有后续添加的规则匹配; 或<code>null</code>匹配所有的命名空间URI
     */
    @Override
    public void setNamespaceURI(String namespaceURI) {

        this.namespaceURI = namespaceURI;

    }


    // --------------------------------------------------------- Public Methods


    /**
     * 注册与指定模式匹配的新Rule实例.
     *
     * @param pattern 要为此 Rule 匹配的嵌套模式
     * @param rule 要注册的 Rule 实例
     */
    @Override
    public void add(String pattern, Rule rule) {
        // 帮助那些意外添加'/'到模式结尾的用户
        int patternLength = pattern.length();
        if (patternLength>1 && pattern.endsWith("/")) {
            pattern = pattern.substring(0, patternLength-1);
        }


        List<Rule> list = cache.get(pattern);
        if (list == null) {
            list = new ArrayList<>();
            cache.put(pattern, list);
        }
        list.add(rule);
        rules.add(rule);
        if (this.digester != null) {
            rule.setDigester(this.digester);
        }
        if (this.namespaceURI != null) {
            rule.setNamespaceURI(this.namespaceURI);
        }

    }


    /**
     * 清除所有注册的规则实例.
     */
    @Override
    public void clear() {
        cache.clear();
        rules.clear();
    }


    /**
     * 返回与指定嵌套模式匹配的所有已注册Rule实例的List, 如果没有匹配，则为零长度List.
     * 如果多个Rule实例匹配, 它们必须按照最初通过<code>add()</code>方法注册的顺序返回.
     *
     * @param namespaceURI 要为其选择匹配规则的命名空间URI, 或<code>null</code>匹配所有的命名空间URI
     * @param pattern 要匹配的嵌套模式
     */
    @Override
    public List<Rule> match(String namespaceURI, String pattern) {

        // List rulesList = (List) this.cache.get(pattern);
        List<Rule> rulesList = lookup(namespaceURI, pattern);
        if ((rulesList == null) || (rulesList.size() < 1)) {
            // Find the longest key, ie more discriminant
            String longKey = "";
            Iterator<String> keys = this.cache.keySet().iterator();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.startsWith("*/")) {
                    if (pattern.equals(key.substring(2)) ||
                        pattern.endsWith(key.substring(1))) {
                        if (key.length() > longKey.length()) {
                            // rulesList = (List) this.cache.get(key);
                            rulesList = lookup(namespaceURI, key);
                            longKey = key;
                        }
                    }
                }
            }
        }
        if (rulesList == null) {
            rulesList = new ArrayList<>();
        }
        return (rulesList);

    }


    /**
     * 返回所有已注册Rule实例的列表, 如果没有已注册的Rule实例，则为零长度List.
     * 如果已注册多个Rule实例, 它们必须按照最初通过<code>add()</code>方法注册的顺序返回.
     */
    @Override
    public List<Rule> rules() {
        return (this.rules);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 返回指定模式的规则实例列表，该规则实例也与指定的命名空间URI匹配.  如果没有这样的规则, 返回<code>null</code>.
     *
     * @param namespaceURI 要匹配的命名空间URI, 或<code>null</code>匹配所有的命名空间URI
     * @param pattern 要匹配的模式
     * 
     * @return 规则列表
     */
    protected List<Rule> lookup(String namespaceURI, String pattern) {

        // 在没有指定名称空间URI时进行优化
        List<Rule> list = this.cache.get(pattern);
        if (list == null) {
            return (null);
        }
        if ((namespaceURI == null) || (namespaceURI.length() == 0)) {
            return (list);
        }

        // 仅选择与指定的命名空间URI匹配的规则
        ArrayList<Rule> results = new ArrayList<>();
        Iterator<Rule> items = list.iterator();
        while (items.hasNext()) {
            Rule item = items.next();
            if ((namespaceURI.equals(item.getNamespaceURI())) ||
                    (item.getNamespaceURI() == null)) {
                results.add(item);
            }
        }
        return (results);
    }
}
