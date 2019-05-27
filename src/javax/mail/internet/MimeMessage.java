package javax.mail.internet;

import javax.mail.Session;

@SuppressWarnings("unused") // 虚拟实现
public class MimeMessage implements MimePart {
    public MimeMessage(Session session) {
        // 虚拟实现
    }
    public void setFrom(InternetAddress from) {
        // 虚拟实现
    }
    public void setSubject(String subject) {
        // 虚拟实现
    }
}
