package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;

import org.apache.tomcat.util.buf.UDecoder;

/**
 * 表示Web应用程序的错误页面元素, 作为部署描述符中<code>&lt;error-page&gt;</code>元素的表示.
 */
public class ErrorPage implements Serializable {

    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------- Instance Variables


    /**
     * 此错误页面处于活动状态的错误（状态）代码. 请注意，状态代码0用于默认错误页面.
     */
    private int errorCode = 0;


    /**
     * 此错误页面处于活动状态的异常类型.
     */
    private String exceptionType = null;


    /**
     * 处理此错误或异常的上下文相关位置.
     */
    private String location = null;


    // ------------------------------------------------------------- Properties


    public int getErrorCode() {
        return (this.errorCode);
    }


    /**
     * 设置错误码.
     *
     * @param errorCode 错误码
     */
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }


    /**
     * 设置错误码 (hack默认的XmlMapper数据类型).
     *
     * @param errorCode 错误码
     */
    public void setErrorCode(String errorCode) {

        try {
            this.errorCode = Integer.parseInt(errorCode);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(nfe);
        }
    }


    /**
     * @return 异常类型.
     */
    public String getExceptionType() {
        return (this.exceptionType);
    }


    /**
     * 设置异常类型.
     *
     * @param exceptionType 异常类型
     */
    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }


    /**
     * @return 位置.
     */
    public String getLocation() {
        return (this.location);
    }


    /**
     * 设置位置.
     *
     * @param location 位置
     */
    public void setLocation(String location) {

        //        if ((location == null) || !location.startsWith("/"))
        //            throw new IllegalArgumentException
        //                ("Error Page Location must start with a '/'");
        this.location = UDecoder.URLDecode(location);
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ErrorPage[");
        if (exceptionType == null) {
            sb.append("errorCode=");
            sb.append(errorCode);
        } else {
            sb.append("exceptionType=");
            sb.append(exceptionType);
        }
        sb.append(", location=");
        sb.append(location);
        sb.append("]");
        return (sb.toString());
    }

    public String getName() {
        if (exceptionType == null) {
            return Integer.toString(errorCode);
        } else {
            return exceptionType;
        }
    }
}
