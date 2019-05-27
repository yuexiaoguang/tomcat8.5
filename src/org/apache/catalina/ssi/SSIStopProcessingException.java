package org.apache.catalina.ssi;


/**
 * 用来告诉SSIProcessor应停止处理SSI指令的异常.
 * 这是用来模拟在#set属性无效的Apache行为.
 */
public class SSIStopProcessingException extends Exception {

    private static final long serialVersionUID = 1L;
    // No specific functionality for this class
}