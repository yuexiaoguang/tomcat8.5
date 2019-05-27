package org.apache.tomcat.util.http.fileupload;

import java.util.Iterator;

/**
 * <p>此类提供对访问<code>multipart/form-data</code> POST请求中接收的文件或表单项的 Header 的支持.</p>
 */
public interface FileItemHeaders {

    /**
     * 以<code>String</code>的形式返回指定Header的值.
     *
     * 如果不包含指定名称的Header, 这个方法返回 <code>null</code>.
     * 如果有多个具有相同名称的Header,此方法返回项目中的第一个Header. Header名称不区分大小写.
     *
     * @param name header名称
     * @return 包含所请求header值的<code>String</code>, 或<code>null</code>如果该项没有该名称的Header
     */
    String getHeader(String name);

    /**
     * <p>
     * 返回指定Header的所有值，作为<code>String</code>对象的<code>Iterator</code>.
     * </p>
     * <p>
     * 如果该项目不包含指定名称的任何Header, 这个方法返回一个空的<code>Iterator</code>. Header名称不区分大小写.
     * </p>
     *
     * @param name header名称
     * @return 包含所请求Header值的<code>Iterator</code>. 如果该项目没有该名称的任何Header, 返回空<code>Iterator</code>
     */
    Iterator<String> getHeaders(String name);

    /**
     * <p>
     * 返回所有 Header名称的<code>Iterator</code>.
     * </p>
     *
     * @return 包含此文件项提供的所有 Header 名称的<code>Iterator</code>. 如果该项没有任何Header，则返回空的<code>Iterator</code>
     */
    Iterator<String> getHeaderNames();

}
