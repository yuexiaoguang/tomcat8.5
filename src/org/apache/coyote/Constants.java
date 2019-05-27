package org.apache.coyote;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Constants.
 */
public final class Constants {

    /**
     * @deprecated This will be removed in Tomcat 9.0.x onwards.
     */
    @Deprecated
    public static final String DEFAULT_CHARACTER_ENCODING="ISO-8859-1";

    public static final Charset DEFAULT_URI_CHARSET = StandardCharsets.ISO_8859_1;
    public static final Charset DEFAULT_BODY_CHARSET = StandardCharsets.ISO_8859_1;

    public static final int MAX_NOTES = 32;


    // 请求状态
    public static final int STAGE_NEW = 0;
    public static final int STAGE_PARSE = 1;
    public static final int STAGE_PREPARE = 2;
    public static final int STAGE_SERVICE = 3;
    public static final int STAGE_ENDINPUT = 4;
    public static final int STAGE_ENDOUTPUT = 5;
    public static final int STAGE_KEEPALIVE = 6;
    public static final int STAGE_ENDED = 7;

    // 默认协议设置
    public static final int DEFAULT_CONNECTION_LINGER = -1;
    public static final boolean DEFAULT_TCP_NO_DELAY = true;

    /**
     * 安全已经开启?
     */
    public static final boolean IS_SECURITY_ENABLED = (System.getSecurityManager() != null);


    /**
     * 如果是 true, 将在header中使用自定义 HTTP 状态消息.
     * @deprecated This option will be removed in Tomcat 9. Reason phrase will
     *             not be sent.
     */
    @Deprecated
    public static final boolean USE_CUSTOM_STATUS_MSG_IN_HEADER =
            Boolean.getBoolean("org.apache.coyote.USE_CUSTOM_STATUS_MSG_IN_HEADER");

    /**
     * 处理这个请求的连接器是否支持使用 sendfile.
     */
    public static final String SENDFILE_SUPPORTED_ATTR = "org.apache.tomcat.sendfile.support";


    /**
     * servlet使用它传递给连接器sendfile的文件的名称. 值应该是{@code File.getCanonicalPath()}.
     */
    public static final String SENDFILE_FILENAME_ATTR = "org.apache.tomcat.sendfile.filename";


    /**
     * servlet使用它传递给连接器sendfile的文件的开始偏移量. 值应该是{@code java.lang.Long}.
     * 要获得完整的文件, 值应该是 {@code Long.valueOf(0)}.
     */
    public static final String SENDFILE_FILE_START_ATTR = "org.apache.tomcat.sendfile.start";


    /**
     * servlet使用它传递给连接器sendfile的文件的结束偏移量 (不包括). 值应该是 {@code java.lang.Long}.
     * 要获得完整的文件, 值应该等于文件的长度.
     */
    public static final String SENDFILE_FILE_END_ATTR = "org.apache.tomcat.sendfile.end";


    /**
     * RemoteIpFilter, RemoteIpValve (可以由其他类似组件来设置)设置的请求属性, 标识连接器的与此请求相关联的远程IP地址, 当通过一个或多个代理接收请求时.
     * 它通常通过 X-Forwarded-For HTTP header 提供.
     */
    public static final String REMOTE_ADDR_ATTRIBUTE = "org.apache.tomcat.remoteAddr";
}
