package org.apache.naming.factory;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

/**
 * <p>工厂类创建一个JNDI命名JavaMail会话工厂, 它可以通过JavaMail API用于管理入站和出站电子邮件.
 * 所有在JavaMail规范描述的通讯环境特性可以传递给会话工厂; 但是下列属性是最常用的:</p>
 * <ul>
 * <li>
 * <li><strong>mail.smtp.host</strong> - 对外交流连接的主机名. 默认是<code>localhost</code>.</li>
 * </ul>
 *
 * <p>这个工厂可以在<code>conf/server.xml</code>配置文件使用<code>&lt;DefaultContext&gt;</code>
 * 或<code>&lt;Context&gt;</code> 配置. 工厂配置的一个例子是:</p>
 * <pre>
 * &lt;Resource name="mail/smtp" auth="CONTAINER"
 *           type="javax.mail.Session"/&gt;
 * &lt;ResourceParams name="mail/smtp"&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;factory&lt;/name&gt;
 *     &lt;value&gt;org.apache.naming.factory.MailSessionFactory&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;mail.smtp.host&lt;/name&gt;
 *     &lt;value&gt;mail.mycompany.com&lt;/value&gt;
 *   &lt;/parameter&gt;
 * &lt;/ResourceParams&gt;
 * </pre>
 */
public class MailSessionFactory implements ObjectFactory {


    /**
     * 这个工厂知道如何创建对象的Java 类
     */
    protected static final String factoryType = "javax.mail.Session";


    /**
     * 基于指定的特性创建和返回一个对象实例
     *
     * @param refObj 包含参数的引用信息, 或null
     * @param name 此对象的名称, 相对于上下文, 或null
     * @param context 名称是相对的上下文, 如果名称与默认初始上下文相关，则为null
     * @param env 环境变量, 或null
     *
     * @exception Exception if an error occurs during object creation
     */
    @Override
    public Object getObjectInstance(Object refObj, Name name, Context context,
            Hashtable<?,?> env) throws Exception {

        // 如果不能创建请求类型的对象，则返回null
        final Reference ref = (Reference) refObj;
        if (!ref.getClassName().equals(factoryType))
            return (null);

        // 在doPrivileged 块中创建一个新会话, 因此 JavaMail可以在不抛出安全异常的情况下读取其默认属性.
        //
        // Bugzilla 31288, 33077: add support for authentication.
        return AccessController.doPrivileged(new PrivilegedAction<Session>() {
                @Override
                public Session run() {

                    // 创建使用的JavaMail 属性
                    Properties props = new Properties();
                    props.put("mail.transport.protocol", "smtp");
                    props.put("mail.smtp.host", "localhost");

                    String password = null;

                    Enumeration<RefAddr> attrs = ref.getAll();
                    while (attrs.hasMoreElements()) {
                        RefAddr attr = attrs.nextElement();
                        if ("factory".equals(attr.getType())) {
                            continue;
                        }

                        if ("password".equals(attr.getType())) {
                            password = (String) attr.getContent();
                            continue;
                        }

                        props.put(attr.getType(), attr.getContent());
                    }

                    Authenticator auth = null;
                    if (password != null) {
                        String user = props.getProperty("mail.smtp.user");
                        if(user == null) {
                            user = props.getProperty("mail.user");
                        }

                        if(user != null) {
                            final PasswordAuthentication pa = new PasswordAuthentication(user, password);
                            auth = new Authenticator() {
                                    @Override
                                    protected PasswordAuthentication getPasswordAuthentication() {
                                        return pa;
                                    }
                                };
                        }
                    }
                    // 创建并返回新会话对象
                    Session session = Session.getInstance(props, auth);
                    return (session);
                }
        } );
    }
}
