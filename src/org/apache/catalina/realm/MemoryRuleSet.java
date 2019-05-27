package org.apache.catalina.realm;


import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.digester.RuleSetBase;
import org.xml.sax.Attributes;


/**
 * <p>识别<code>MemoryRealm</code>处理的XML文件中定义的用户的<strong>RuleSet</strong>.</p>
 */
@SuppressWarnings("deprecation")
public class MemoryRuleSet extends RuleSetBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * 用于识别元素的匹配模式前缀..
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor


    public MemoryRuleSet() {
        this("tomcat-users/");
    }


    /**
     * @param prefix 匹配模式规则的前缀 (包括尾部斜杠字符)
     */
    public MemoryRuleSet(String prefix) {
        super();
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>添加这个RuleSet定义的Rule实例集合到指定的<code>Digester</code>实例, 将它们与命名空间URI关联起来.
     * 此方法只应由Digester实例调用.</p>
     *
     * @param digester Digester instance to which the new Rule instances
     *  should be added.
     */
    @Override
    public void addRuleInstances(Digester digester) {
        digester.addRule(prefix + "user", new MemoryUserRule());
    }
}


/**
 * 当解析XML数据库文件的时候，使用的私有类.
 */
final class MemoryUserRule extends Rule {


    public MemoryUserRule() {
        // No initialisation required
    }


    /**
     * 处理XML数据库文件的<code>&lt;user&gt;</code>元素.
     *
     * @param attributes 此元素的属性列表
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {

        String username = attributes.getValue("username");
        if (username == null) {
            username = attributes.getValue("name");
        }
        String password = attributes.getValue("password");
        String roles = attributes.getValue("roles");

        MemoryRealm realm =
            (MemoryRealm) digester.peek(digester.getCount() - 1);
        realm.addUser(username, password, roles);
    }
}
