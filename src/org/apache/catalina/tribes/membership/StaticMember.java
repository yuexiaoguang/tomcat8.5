package org.apache.catalina.tribes.membership;

import java.io.IOException;

import org.apache.catalina.tribes.util.Arrays;

public class StaticMember extends MemberImpl {
    public StaticMember() {
        super();
    }

    public StaticMember(String host, int port, long aliveTime) throws IOException {
        super(host, port, aliveTime);
    }

    public StaticMember(String host, int port, long aliveTime, byte[] payload) throws IOException {
        super(host, port, aliveTime, payload);
    }

    /**
     * @param host 字节数组字符串格式, 例如 {214,116,1,3}
     * 或作为常规主机名, 127.0.0.1 或 tomcat01.mydomain.com
     */
    public void setHost(String host) {
        if ( host == null ) return;
        if ( host.startsWith("{") ) setHost(Arrays.fromString(host));
        else try { setHostname(host); }catch (IOException x) { throw new RuntimeException(x);}

    }

    /**
     * @param domain 字节数组字符串格式, 例如 {214,116,1,3}; 或作为常规字符串, 例如 'mydomain'. 稍后将使用 ISO-8859-1 编码
     */
    public void setDomain(String domain) {
        if ( domain == null ) return;
        if ( domain.startsWith("{") ) setDomain(Arrays.fromString(domain));
        else setDomain(Arrays.convert(domain));
    }

    /**
     * @param id 必须是字节数组字符串格式, 例如 {214,116,1,3} , 并且只能是 16 个字节长
     */
    public void setUniqueId(String id) {
        byte[] uuid = Arrays.fromString(id);
        if ( uuid==null || uuid.length != 16 ) throw new RuntimeException(sm.getString("staticMember.invalid.uuidLength", id));
        setUniqueId(uuid);
    }
}