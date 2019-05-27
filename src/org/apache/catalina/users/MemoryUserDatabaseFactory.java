package org.apache.catalina.users;


import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;


/**
 * <p><code>MemoryUserDatabase</code>实例的JNDI对象创建工厂.
 * 在这个Catalina 实例关联的全局JNDI资源配置用户数据库更加方便,
 * 然后链接到管理用户数据库内容的Web应用程序的资源.</p>
 *
 * <p>基于下面的参数值配置<code>MemoryUserDatabase</code>实例:</p>
 * <ul>
 * <li><strong>pathname</strong> - 绝对或相对(通过<code>catalina.base</code>系统属性配置目录路径)
 *     路径到加载用户信息的XML文件, 并存储它.  [conf/tomcat-users.xml]</li>
 * </ul>
 */
public class MemoryUserDatabaseFactory implements ObjectFactory {


    // --------------------------------------------------------- Public Methods


    /**
     * <p>创建并返回一个新的<code>MemoryUserDatabase</code>实例， 根据<code>Reference</code>属性配置.
     * 如果该实例已经创建, 返回<code>null</code></p>
     *
     * @param obj 可能为空的对象，其中包含用于创建对象的位置或引用信息
     * @param name 此对象相对于<code>nameCtx</code>的名称
     * @param nameCtx 与指定的<code>name</code>参数相对应的上下文, 或者<code>null</code>如果<code>name</code>
     *  与默认初始上下文相关
     * @param environment 用于创建此对象的可能的null环境
     */
    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                    Hashtable<?,?> environment)
        throws Exception {

        // 我们只知道如何处理指定了"org.apache.catalina.UserDatabase"类名的 <code>javax.naming.Reference</code>
        if ((obj == null) || !(obj instanceof Reference)) {
            return (null);
        }
        Reference ref = (Reference) obj;
        if (!"org.apache.catalina.UserDatabase".equals(ref.getClassName())) {
            return (null);
        }

        // 根据这个引用关联的RefAddr值，创建和配置 MemoryUserDatabase实例
        MemoryUserDatabase database = new MemoryUserDatabase(name.toString());
        RefAddr ra = null;

        ra = ref.get("pathname");
        if (ra != null) {
            database.setPathname(ra.getContent().toString());
        }

        ra = ref.get("readonly");
        if (ra != null) {
            database.setReadonly(Boolean.parseBoolean(ra.getContent().toString()));
        }

        // 返回配置的数据库实例
        database.open();
        // Don't try something we know won't work
        if (!database.getReadonly())
            database.save();
        return (database);
    }
}
