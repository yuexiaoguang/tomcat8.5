package org.apache.catalina.tribes.group;

import java.io.Serializable;

import org.apache.catalina.tribes.Member;

/**
 * 保存来自响应伙伴的消息.
 */
public class Response {
    private Member source;
    private Serializable message;
    public Response() {
    }

    public Response(Member source, Serializable message) {
        this.source = source;
        this.message = message;
    }

    public void setSource(Member source) {
        this.source = source;
    }

    public void setMessage(Serializable message) {
        this.message = message;
    }

    public Member getSource() {
        return source;
    }

    public Serializable getMessage() {
        return message;
    }
}