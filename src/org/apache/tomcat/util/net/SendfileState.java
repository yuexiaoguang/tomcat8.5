package org.apache.tomcat.util.net;

public enum SendfileState {

    /**
     * 文件的发送已经开始但尚未完成. Sendfile仍在使用套接字.
     */
    PENDING,

    /**
     * 该文件已完全发送. Sendfile不再使用套接字.
     */
    DONE,

    /**
     * 出了些问题. 该文件可能已发送，也可能未发送. 套接字处于未知状态.
     */
    ERROR
}
