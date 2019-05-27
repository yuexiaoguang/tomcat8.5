package javax.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * 提供了一个方便的ServletResponse接口，可以由开发人员子类化，以适应不同的响应.
 * 这个类实现Wrapper或 Decorator模式. 方法默认调用已包装的响应对象.
 */
public class ServletResponseWrapper implements ServletResponse {
    private ServletResponse response;

    /**
     * @param response 要包装的响应
     *
     * @throws java.lang.IllegalArgumentException 如果响应是null.
     */
    public ServletResponseWrapper(ServletResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Response cannot be null");
        }
        this.response = response;
    }

    public ServletResponse getResponse() {
        return this.response;
    }

    public void setResponse(ServletResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Response cannot be null");
        }
        this.response = response;
    }

    @Override
    public void setCharacterEncoding(String charset) {
        this.response.setCharacterEncoding(charset);
    }

    @Override
    public String getCharacterEncoding() {
        return this.response.getCharacterEncoding();
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return this.response.getOutputStream();
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return this.response.getWriter();
    }

    @Override
    public void setContentLength(int len) {
        this.response.setContentLength(len);
    }

    @Override
    public void setContentLengthLong(long length) {
        this.response.setContentLengthLong(length);
    }

    @Override
    public void setContentType(String type) {
        this.response.setContentType(type);
    }

    @Override
    public String getContentType() {
        return this.response.getContentType();
    }

    @Override
    public void setBufferSize(int size) {
        this.response.setBufferSize(size);
    }

    @Override
    public int getBufferSize() {
        return this.response.getBufferSize();
    }

    @Override
    public void flushBuffer() throws IOException {
        this.response.flushBuffer();
    }

    @Override
    public boolean isCommitted() {
        return this.response.isCommitted();
    }
    @Override
    public void reset() {
        this.response.reset();
    }

    @Override
    public void resetBuffer() {
        this.response.resetBuffer();
    }

    @Override
    public void setLocale(Locale loc) {
        this.response.setLocale(loc);
    }

    @Override
    public Locale getLocale() {
        return this.response.getLocale();
    }

    /**
     * TODO SERVLET3 - 添加注释
     * @param wrapped 与包装响应进行比较的响应
     * @return <code>true</code>如果响应对象是这个包装器包装的响应(或者是一个已经包装的响应), 否则<code>false</code>
     */
    public boolean isWrapperFor(ServletResponse wrapped) {
        if (response == wrapped) {
            return true;
        }
        if (response instanceof ServletResponseWrapper) {
            return ((ServletResponseWrapper) response).isWrapperFor(wrapped);
        }
        return false;
    }

    /**
     * TODO SERVLET3 - 添加注释
     * @param wrappedType 要与包装响应类进行比较的类
     * @return <code>true</code>如果响应对象是这个包装器包装的响应(或者是一个已经包装的响应), 否则<code>false</code>
     */
    public boolean isWrapperFor(Class<?> wrappedType) {
    	//isAssignableFrom判定此 Class 对象所表示的类或接口与指定的 Class 参数所表示的类或接口是否相同，或是否是其超类或超接口
        if (wrappedType.isAssignableFrom(response.getClass())) {
            return true;
        }
        if (response instanceof ServletResponseWrapper) {
            return ((ServletResponseWrapper) response).isWrapperFor(wrappedType);
        }
        return false;
    }
}
