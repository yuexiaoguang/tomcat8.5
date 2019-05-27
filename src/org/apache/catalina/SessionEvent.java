package org.apache.catalina;


import java.util.EventObject;


/**
 * 一般事件，通知监听器Session中有重大修改.
 */
public final class SessionEvent extends EventObject {

    private static final long serialVersionUID = 1L;


    private final Object data;


    private final Session session;


    private final String type;


    /**
     * @param session 发生事件的Session
     * @param type Event type
     * @param data Event data
     */
    public SessionEvent(Session session, String type, Object data) {
        super(session);
        this.session = session;
        this.type = type;
        this.data = data;
    }


    public Object getData() {
        return (this.data);
    }


    public Session getSession() {
        return (this.session);
    }


    public String getType() {
        return (this.type);
    }


    @Override
    public String toString() {
        return ("SessionEvent['" + getSession() + "','" +
                getType() + "']");
    }
}
