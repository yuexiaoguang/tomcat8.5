package org.apache.tomcat.websocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.websocket.Extension;

public class Constants {

    // OP Codes
    public static final byte OPCODE_CONTINUATION = 0x00;
    public static final byte OPCODE_TEXT = 0x01;
    public static final byte OPCODE_BINARY = 0x02;
    public static final byte OPCODE_CLOSE = 0x08;
    public static final byte OPCODE_PING = 0x09;
    public static final byte OPCODE_PONG = 0x0A;

    // Internal OP Codes
    // RFC 6455将OP码限制为4位，因此它们不应该发生冲突
    // 总是设置位4，因此这些将被视为控制代码
    static final byte INTERNAL_OPCODE_FLUSH = 0x18;

    // Buffers
    static final int DEFAULT_BUFFER_SIZE = Integer.getInteger(
            "org.apache.tomcat.websocket.DEFAULT_BUFFER_SIZE", 8 * 1024)
            .intValue();

    // Client connection
    /**
     * 传递到{@link javax.net.ssl.SSLEngine#setEnabledProtocols(String[])}的属性名称. 该值应该是逗号分隔的字符串.
     */
    public static final String SSL_PROTOCOLS_PROPERTY =
            "org.apache.tomcat.websocket.SSL_PROTOCOLS";
    public static final String SSL_TRUSTSTORE_PROPERTY =
            "org.apache.tomcat.websocket.SSL_TRUSTSTORE";
    public static final String SSL_TRUSTSTORE_PWD_PROPERTY =
            "org.apache.tomcat.websocket.SSL_TRUSTSTORE_PWD";
    public static final String SSL_TRUSTSTORE_PWD_DEFAULT = "changeit";
    /**
     * 设置用于配置使用的SSLContext的属性名称. 值应该是一个SSLContext实例. 如果存在此属性, 将忽略 SSL_TRUSTSTORE*属性.
     */
    public static final String SSL_CONTEXT_PROPERTY =
            "org.apache.tomcat.websocket.SSL_CONTEXT";
    /**
     * 建立到服务器的WebSocket连接时, 配置超时的属性名 (以毫秒为单位). 默认是{@link #IO_TIMEOUT_MS_DEFAULT}.
     */
    public static final String IO_TIMEOUT_MS_PROPERTY =
            "org.apache.tomcat.websocket.IO_TIMEOUT_MS";
    public static final long IO_TIMEOUT_MS_DEFAULT = 5000;

    // RFC 2068推荐5的限制
    // 大多数浏览器的默认限制为20
    public static final String MAX_REDIRECTIONS_PROPERTY =
            "org.apache.tomcat.websocket.MAX_REDIRECTIONS";
    public static final int MAX_REDIRECTIONS_DEFAULT = 20;

    // HTTP升级header名称和值
    public static final String HOST_HEADER_NAME = "Host";
    public static final String UPGRADE_HEADER_NAME = "Upgrade";
    public static final String UPGRADE_HEADER_VALUE = "websocket";
    public static final String ORIGIN_HEADER_NAME = "Origin";
    public static final String CONNECTION_HEADER_NAME = "Connection";
    public static final String CONNECTION_HEADER_VALUE = "upgrade";
    public static final String LOCATION_HEADER_NAME = "Location";
    public static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    public static final String WWW_AUTHENTICATE_HEADER_NAME = "WWW-Authenticate";
    public static final String WS_VERSION_HEADER_NAME = "Sec-WebSocket-Version";
    public static final String WS_VERSION_HEADER_VALUE = "13";
    public static final String WS_KEY_HEADER_NAME = "Sec-WebSocket-Key";
    public static final String WS_PROTOCOL_HEADER_NAME = "Sec-WebSocket-Protocol";
    public static final String WS_EXTENSIONS_HEADER_NAME = "Sec-WebSocket-Extensions";

    /// HTTP 重定向状态代码
    public static final int MULTIPLE_CHOICES = 300;
    public static final int MOVED_PERMANENTLY = 301;
    public static final int FOUND = 302;
    public static final int SEE_OTHER = 303;
    public static final int USE_PROXY = 305;
    public static final int TEMPORARY_REDIRECT = 307;

    // 客户端Origin header的配置
    static final String DEFAULT_ORIGIN_HEADER_VALUE =
            System.getProperty("org.apache.tomcat.websocket.DEFAULT_ORIGIN_HEADER_VALUE");

    // 阻塞发送的配置
    public static final String BLOCKING_SEND_TIMEOUT_PROPERTY =
            "org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT";
    // 毫秒
    public static final long DEFAULT_BLOCKING_SEND_TIMEOUT = 20 * 1000;

    // 后台进程检查间隔的配置
    static final int DEFAULT_PROCESS_PERIOD = Integer.getInteger(
            "org.apache.tomcat.websocket.DEFAULT_PROCESS_PERIOD", 10)
            .intValue();

    public static final String WS_AUTHENTICATION_USER_NAME = "org.apache.tomcat.websocket.WS_AUTHENTICATION_USER_NAME";
    public static final String WS_AUTHENTICATION_PASSWORD = "org.apache.tomcat.websocket.WS_AUTHENTICATION_PASSWORD";

    /* 扩展配置
     * Note: 这些选项主要用于使此实现通过符合性测试. 一旦WebSocket API包含用于添加自定义扩展和禁用内置扩展的机制，就应该删除它们.
     */
    static final boolean DISABLE_BUILTIN_EXTENSIONS =
            Boolean.getBoolean("org.apache.tomcat.websocket.DISABLE_BUILTIN_EXTENSIONS");
    static final boolean ALLOW_UNSUPPORTED_EXTENSIONS =
            Boolean.getBoolean("org.apache.tomcat.websocket.ALLOW_UNSUPPORTED_EXTENSIONS");

    // 流的行为的配置
    static final boolean STREAMS_DROP_EMPTY_MESSAGES =
            Boolean.getBoolean("org.apache.tomcat.websocket.STREAMS_DROP_EMPTY_MESSAGES");

    public static final boolean STRICT_SPEC_COMPLIANCE =
            Boolean.getBoolean("org.apache.tomcat.websocket.STRICT_SPEC_COMPLIANCE");

    public static final List<Extension> INSTALLED_EXTENSIONS;

    static {
        if (DISABLE_BUILTIN_EXTENSIONS) {
            INSTALLED_EXTENSIONS = Collections.unmodifiableList(new ArrayList<Extension>());
        } else {
            List<Extension> installed = new ArrayList<>(1);
            installed.add(new WsExtension("permessage-deflate"));
            INSTALLED_EXTENSIONS = Collections.unmodifiableList(installed);
        }
    }

    private Constants() {
        // Hide default constructor
    }
}
