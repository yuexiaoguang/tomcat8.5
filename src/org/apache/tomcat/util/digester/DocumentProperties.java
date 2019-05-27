package org.apache.tomcat.util.digester;

/**
 * 一个接口集合，每个属性一个，使 digester 填充的对象能够向 digester 发出信号，表明它支持给定的属性，并且 digester 应填充该属性（如果可用）.
 */
public interface DocumentProperties {

    /**
     * 源XML文档使用的编码.
     *
     * @deprecated This method will be removed in Tomcat 9
     */
    @Deprecated
    public interface Encoding {
        public void setEncoding(String encoding);
    }

    /**
     * 源XML文档使用的字符编码.
     */
    public interface Charset {
        public void setCharset(java.nio.charset.Charset charset);
    }
}
