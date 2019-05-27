package org.apache.tomcat.util.descriptor.web;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.res.StringManager;

/**
 * 需要跟踪源XML中使用的编码的那些元素的基类.
 */
public abstract class XmlEncodingBase {

    private static final Log log = LogFactory.getLog(XmlEncodingBase.class);
    private static final StringManager sm = StringManager.getManager(XmlEncodingBase.class);
    private Charset charset = StandardCharsets.UTF_8;


    /**
     * @param encoding 用于填充此对象的XML源的编码.
     * @deprecated This method will be removed in Tomcat 9
     */
    @Deprecated
    public void setEncoding(String encoding) {
        try {
            charset = B2CConverter.getCharset(encoding);
        } catch (UnsupportedEncodingException e) {
            log.warn(sm.getString("xmlEncodingBase.encodingInvalid", encoding, charset.name()), e);
        }
    }


    /**
     * 获取用于填充此对象的XML源的编码.
     *
     * @return 如果无法确定编码，则关联XML源的编码或<code>UTF-8</code>
     * @deprecated This method will be removed in Tomcat 9
     */
    @Deprecated
    public String getEncoding() {
        return charset.name();
    }


    public void setCharset(Charset charset) {
        this.charset = charset;
    }


    /**
     * 获取用于填充此对象的XML源的字符编码.
     *
     * @return 如果无法确定编码，则关联XML源的字符编码或<code>UTF-8</code>
     */
    public Charset getCharset() {
        return charset;
    }
}
