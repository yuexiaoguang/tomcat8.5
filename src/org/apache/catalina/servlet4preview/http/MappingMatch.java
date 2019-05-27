package org.apache.catalina.servlet4preview.http;

/**
 * 请求可以映射到servlet的方式
 */
public enum MappingMatch {

    CONTEXT_ROOT,
    DEFAULT,
    EXACT,
    EXTENSION,
    PATH,
    UNKNOWN
}
