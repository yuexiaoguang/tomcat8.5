package org.apache.catalina.core;

import java.util.Locale;

import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;


/**
 * 封装一个<code>javax.servlet.ServletResponse</code>
 * 转换应用程序响应对象(这可能是传递给servlet的原始消息, 或者可能基于 2.3
 * <code>javax.servlet.ServletResponseWrapper</code>)
 * 回到内部的 <code>org.apache.catalina.Response</code>.
 * <p>
 * <strong>WARNING</strong>:
 * 由于java不支持多重继承, <code>ApplicationResponse</code>中的所有逻辑 在<code>ApplicationHttpResponse</code>中是重复的. 
 * 确保在进行更改时保持这两个类同步!
 */
class ApplicationResponse extends ServletResponseWrapper {

    // ----------------------------------------------------------- Constructors

    /**
     * @param response 要包装的servlet响应
     * @param included <code>true</code>如果该响应正在由<code>RequestDispatcher.include()</code>调用
     */
    public ApplicationResponse(ServletResponse response, boolean included) {

        super(response);
        setIncluded(included);

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Is this wrapped response the subject of an <code>include()</code>
     * call?
     */
    protected boolean included = false;


    // ------------------------------------------------ ServletResponse Methods


    /**
     * 不允许调用included响应的<code>reset()</code>.
     *
     * @exception IllegalStateException 如果响应已经提交
     */
    @Override
    public void reset() {

        // If already committed, the wrapped response will throw ISE
        if (!included || getResponse().isCommitted())
            getResponse().reset();

    }


    /**
     * 不允许调用included响应的<code>setContentLength(int)</code>.
     *
     * @param len The new content length
     */
    @Override
    public void setContentLength(int len) {

        if (!included)
            getResponse().setContentLength(len);

    }


    /**
     * 不允许调用included响应的<code>setContentLengthLong(long)</code>.
     *
     * @param len The new content length
     */
    @Override
    public void setContentLengthLong(long len) {

        if (!included)
            getResponse().setContentLengthLong(len);

    }


    /**
     * 不允许调用included响应的<code>setContentType()</code>.
     *
     * @param type The new content type
     */
    @Override
    public void setContentType(String type) {

        if (!included)
            getResponse().setContentType(type);

    }


    /**
     * 不允许调用included响应的<code>setLocale()</code>.
     *
     * @param loc The new locale
     */
    @Override
    public void setLocale(Locale loc) {
        if (!included)
            getResponse().setLocale(loc);
    }


    /**
     * 不允许调用included响应的<code>setBufferSize()</code>.
     *
     * @param size 缓冲区大小
     */
    @Override
    public void setBufferSize(int size) {
        if (!included)
            getResponse().setBufferSize(size);
    }


    // ----------------------------------------- ServletResponseWrapper Methods


    /**
     * 设置包装的响应.
     *
     * @param response The new wrapped response
     */
    @Override
    public void setResponse(ServletResponse response) {
        super.setResponse(response);
    }


    // -------------------------------------------------------- Package Methods

    /**
     * @param included
     */
    void setIncluded(boolean included) {
        this.included = included;
    }
}
