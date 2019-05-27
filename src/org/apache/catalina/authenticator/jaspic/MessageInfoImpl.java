package org.apache.catalina.authenticator.jaspic;

import java.util.HashMap;
import java.util.Map;

import javax.security.auth.message.MessageInfo;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.res.StringManager;

public class MessageInfoImpl implements MessageInfo {
    protected static final StringManager sm = StringManager.getManager(MessageInfoImpl.class);

    public static final String IS_MANDATORY = "javax.security.auth.message.MessagePolicy.isMandatory";

    private final Map<String, Object> map = new HashMap<>();
    private HttpServletRequest request;
    private HttpServletResponse response;

    public MessageInfoImpl() {
    }

    public MessageInfoImpl(HttpServletRequest request, HttpServletResponse response, boolean authMandatory) {
        this.request = request;
        this.response = response;
        map.put(IS_MANDATORY, Boolean.toString(authMandatory));
    }

    @Override
    @SuppressWarnings("rawtypes")
    // JASPIC uses raw types
    public Map getMap() {
        return map;
    }

    @Override
    public Object getRequestMessage() {
        return request;
    }

    @Override
    public Object getResponseMessage() {
        return response;
    }

    @Override
    public void setRequestMessage(Object request) {
        if (!(request instanceof HttpServletRequest)) {
            throw new IllegalArgumentException(sm.getString("authenticator.jaspic.badRequestType",
                    request.getClass().getName()));
        }
        this.request = (HttpServletRequest) request;
    }

    @Override
    public void setResponseMessage(Object response) {
        if (!(response instanceof HttpServletResponse)) {
            throw new IllegalArgumentException(sm.getString("authenticator.jaspic.badResponseType",
                    response.getClass().getName()));
        }
        this.response = (HttpServletResponse) response;
    }
}