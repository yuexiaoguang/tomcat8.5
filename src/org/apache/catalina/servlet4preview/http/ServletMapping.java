package org.apache.catalina.servlet4preview.http;

/**
 * 请求怎样映射到关联的 servlet.
 */
public interface ServletMapping {

    /**
     * @return 匹配的值或空字符串.
     */
    String getMatchValue();

    /**
     * @return 这个请求匹配的{@code url-pattern}或空字符串.
     */
    String getPattern();

    /**
     * @return 匹配的类型 ({@link MappingMatch#UNKNOWN})
     */
    MappingMatch getMappingMatch();

    /**
     * @return 请求映射的servlet的名称 (在web.xml中指定,
     *         {@link javax.servlet.annotation.WebServlet#name()},
     *         {@link javax.servlet.ServletContext#addServlet(String, Class)}
     *         或其它<code>addServlet()</code>方法之一).
     */
    String getServletName();
}
