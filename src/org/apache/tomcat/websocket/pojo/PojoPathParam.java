package org.apache.tomcat.websocket.pojo;

/**
 * 存储需要传递到{@link javax.websocket.Endpoint}的onXxx方法的参数的参数类型和名称.
 * 该名称仅用于用{@link javax.websocket.server.PathParam}注解的参数.
 * 对于{@link javax.websocket.Session} 和 {@link java.lang.Throwable} 参数, {@link #getName()} 将总是返回<code>null</code>.
 */
public class PojoPathParam {

    private final Class<?> type;
    private final String name;


    public PojoPathParam(Class<?> type, String name) {
        this.type = type;
        this.name = name;
    }


    public Class<?> getType() {
        return type;
    }


    public String getName() {
        return name;
    }
}
