package org.apache.naming.factory;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimePartDataSource;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

/**
 * 工厂类, 创建一个JNDI命名JavaMail MimePartDataSource对象, 可使用SMTP发送邮件.
 * <p>
 * 可以在server.xml配置文件中在 DefaultContext 或 Context范围内配置.
 * <p>
 * Example:
 * <pre>
 * &lt;Resource name="mail/send" auth="CONTAINER"
 *           type="javax.mail.internet.MimePartDataSource"/&gt;
 * &lt;ResourceParams name="mail/send"&gt;
 *   &lt;parameter&gt;&lt;name&gt;factory&lt;/name&gt;
 *     &lt;value&gt;org.apache.naming.factory.SendMailFactory&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;&lt;name&gt;mail.smtp.host&lt;/name&gt;
 *     &lt;value&gt;your.smtp.host&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;&lt;name&gt;mail.smtp.user&lt;/name&gt;
 *     &lt;value&gt;someuser&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;&lt;name&gt;mail.from&lt;/name&gt;
 *     &lt;value&gt;someuser@some.host&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;&lt;name&gt;mail.smtp.sendpartial&lt;/name&gt;
 *     &lt;value&gt;true&lt;/value&gt;
 *   &lt;/parameter&gt;
 *  &lt;parameter&gt;&lt;name&gt;mail.smtp.dsn.notify&lt;/name&gt;
 *     &lt;value&gt;FAILURE&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;&lt;name&gt;mail.smtp.dsn.ret&lt;/name&gt;
 *     &lt;value&gt;FULL&lt;/value&gt;
 *   &lt;/parameter&gt;
 * &lt;/ResourceParams&gt;
 * </pre>
 */
public class SendMailFactory implements ObjectFactory
{
    // The class name for the javamail MimeMessageDataSource
    protected static final String DataSourceClassName =
        "javax.mail.internet.MimePartDataSource";

    @Override
    public Object getObjectInstance(Object refObj, Name name, Context ctx,
            Hashtable<?,?> env) throws Exception {
        final Reference ref = (Reference)refObj;

        // DataSource的创建是包装进一个 doPrivileged中的, 因此javamail 可以读取默认属性, 而不抛出 Security Exceptions
        if (ref.getClassName().equals(DataSourceClassName)) {
            return AccessController.doPrivileged(
                    new PrivilegedAction<MimePartDataSource>()
            {
                @Override
                public MimePartDataSource run() {
                    // set up the smtp session that will send the message
                    Properties props = new Properties();
                    // enumeration of all refaddr
                    Enumeration<RefAddr> list = ref.getAll();
                    // current refaddr to be set
                    RefAddr refaddr;
                    // set transport to smtp
                    props.put("mail.transport.protocol", "smtp");

                    while (list.hasMoreElements()) {
                        refaddr = list.nextElement();

                        // set property
                        props.put(refaddr.getType(), refaddr.getContent());
                    }
                    MimeMessage message = new MimeMessage(
                        Session.getInstance(props));
                    try {
                        RefAddr fromAddr = ref.get("mail.from");
                        String from = null;
                        if (fromAddr != null) {
                            from = (String)ref.get("mail.from").getContent();
                        }
                        if (from != null) {
                            message.setFrom(new InternetAddress(from));
                        }
                        message.setSubject("");
                    } catch (Exception e) {/*Ignore*/}
                    MimePartDataSource mds = new MimePartDataSource(message);
                    return mds;
                }
            } );
        }
        else { // 不能创建一个DataSource实例
            return null;
        }
    }
}
