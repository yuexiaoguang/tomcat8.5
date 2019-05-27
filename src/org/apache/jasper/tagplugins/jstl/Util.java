package org.apache.jasper.tagplugins.jstl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.PageContext;

import org.apache.jasper.Constants;

/**
 * 包含有一些经常使用的常量, 支持JSTL标签插件静态方法和嵌入类.
 */
public class Util {

    private static final String VALID_SCHEME_CHAR =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+.-";

    public static final String DEFAULT_ENCODING =
        "ISO-8859-1";

    private static final int HIGHEST_SPECIAL = '>';

    private static final char[][] specialCharactersRepresentation =
            new char[HIGHEST_SPECIAL + 1][];

    static {
        specialCharactersRepresentation['&'] = "&amp;".toCharArray();
        specialCharactersRepresentation['<'] = "&lt;".toCharArray();
        specialCharactersRepresentation['>'] = "&gt;".toCharArray();
        specialCharactersRepresentation['"'] = "&#034;".toCharArray();
        specialCharactersRepresentation['\''] = "&#039;".toCharArray();
    }

    /**
     * 将范围的给定字符串描述转换为相应的PageContext常量字符串描述.
     *
     * 在给定的范围内的有效性已经被相应的TLV检查.
     *
     * @param scope 范围的字符串描述
     *
     * @return PageContext 与给定范围描述相对应的常量
     *
     * taken from org.apache.taglibs.standard.tag.common.core.Util
     */
    public static int getScope(String scope){
        int ret = PageContext.PAGE_SCOPE;

        if("request".equalsIgnoreCase(scope)){
            ret = PageContext.REQUEST_SCOPE;
        }else if("session".equalsIgnoreCase(scope)){
            ret = PageContext.SESSION_SCOPE;
        }else if("application".equalsIgnoreCase(scope)){
            ret = PageContext.APPLICATION_SCOPE;
        }

        return ret;
    }

    /**
     * 返回<tt>true</tt>如果当期URL 是绝对的, 否则<tt>false</tt>.
     * taken from org.apache.taglibs.standard.tag.common.core.ImportSupport
     * @param url The URL
     * @return <tt>true</tt> if the URL is absolute
     */
    public static boolean isAbsoluteUrl(String url){
        if(url == null){
            return false;
        }

        int colonPos = url.indexOf(':');
        if(colonPos == -1){
            return false;
        }

        for(int i=0;i<colonPos;i++){
            if(VALID_SCHEME_CHAR.indexOf(url.charAt(i)) == -1){
                return false;
            }
        }

        return true;
    }

    /**
     * 获取与内容类型属性关联的值.
     * taken from org.apache.taglibs.standard.tag.common.core.Util
     * @param input The attribute string
     * @param name The attribute name
     * @return the attribute value
     */
    public static String getContentTypeAttribute(String input, String name) {
        int begin;
        int end;
        int index = input.toUpperCase(Locale.ENGLISH).indexOf(name.toUpperCase(Locale.ENGLISH));
        if (index == -1) return null;
        index = index + name.length(); // 定位在属性名称之后
        index = input.indexOf('=', index); // 位于 '='
        if (index == -1) return null;
        index += 1; // 定位 '='
        input = input.substring(index).trim();

        if (input.charAt(0) == '"') {
            // 属性值是引号字符串
            begin = 1;
            end = input.indexOf('"', begin);
            if (end == -1) return null;
        } else {
            begin = 0;
            end = input.indexOf(';');
            if (end == -1) end = input.indexOf(' ');
            if (end == -1) end = input.length();
        }
        return input.substring(begin, end).trim();
    }

    /**
     * 将servlet会话ID加入到<tt>url</tt>.
     * 会话ID被编码为 URL "路径参数", 以"jsessionid="开头.
     * 因此删除";jsessionid=" (包括)和其他 EOS 或随后的';' (不包括)之间的所有字符.
     *
     * taken from org.apache.taglibs.standard.tag.common.core.ImportSupport
     * @param url The URL
     * @return the URL without a user submitted session id parameter
     */
    public static String stripSession(String url) {
        StringBuilder u = new StringBuilder(url);
        int sessionStart;
        while ((sessionStart = u.toString().indexOf(";" + Constants.SESSION_PARAMETER_NAME + "=")) != -1) {
            int sessionEnd = u.toString().indexOf(';', sessionStart + 1);
            if (sessionEnd == -1)
                sessionEnd = u.toString().indexOf('?', sessionStart + 1);
            if (sessionEnd == -1) // still
                sessionEnd = u.length();
            u.delete(sessionStart, sessionEnd);
        }
        return u.toString();
    }


    /**
     * 执行以下的子字符串替换(以便于输出到XML/HTML 页面):
     *
     *    & -> &amp;
     *    < -> &lt;
     *    > -> &gt;
     *    " -> &#034;
     *    ' -> &#039;
     *
     * 也查看 OutSupport.writeEscapedXml().
     *
     * taken from org.apache.taglibs.standard.tag.common.core.Util
     * @param buffer Data to escape
     * @return escaped data
     */
    public static String escapeXml(String buffer) {
        String result = escapeXml(buffer.toCharArray(), buffer.length());
        if (result == null) {
            return buffer;
        } else {
            return result;
        }
    }

    @SuppressWarnings("null") // escapedBuffer cannot be null
    public static String escapeXml(char[] arrayBuffer, int length) {
        int start = 0;
        StringBuilder escapedBuffer = null;

        for (int i = 0; i < length; i++) {
            char c = arrayBuffer[i];
            if (c <= HIGHEST_SPECIAL) {
                char[] escaped = specialCharactersRepresentation[c];
                if (escaped != null) {
                    // 创建一个 StringBuffer 保存转义的 xml 字符串
                    if (start == 0) {
                        escapedBuffer = new StringBuilder(length + 5);
                    }
                    // 加上未转义的的部分
                    if (start < i) {
                        escapedBuffer.append(arrayBuffer,start,i-start);
                    }
                    start = i + 1;
                    // 加上转义的xml
                    escapedBuffer.append(escaped);
                }
            }
        }
        // 不需要转义
        if (start == 0) {
            return null;
        }
        // 加上其余的未转义的部分
        if (start < length) {
            escapedBuffer.append(arrayBuffer,start,length-start);
        }
        return escapedBuffer.toString();
    }

    /**
     * Utility methods
     * taken from org.apache.taglibs.standard.tag.common.core.UrlSupport
     * @param url The URL
     * @param context The context
     * @param pageContext The page context
     * @return the absolute URL
     * @throws JspException If the URL doesn't start with '/'
     */
    public static String resolveUrl(
            String url, String context, PageContext pageContext)
    throws JspException {
        // 不能是绝对 URL
        if (isAbsoluteUrl(url))
            return url;

        // 根据上下文根路径规范相对的URL
        HttpServletRequest request =
            (HttpServletRequest) pageContext.getRequest();
        if (context == null) {
            if (url.startsWith("/"))
                return (request.getContextPath() + url);
            else
                return url;
        } else {
            if (!context.startsWith("/") || !url.startsWith("/")) {
                throw new JspTagException(
                "In URL tags, when the \"context\" attribute is specified, values of both \"context\" and \"url\" must start with \"/\".");
            }
            if (context.equals("/")) {
                // 不要产生以 '//'开头的字符串, 许多浏览器将此解释为主机名, 不作为同一主机上的路径.
                return url;
            } else {
                return (context + url);
            }
        }
    }

    /** 包装响应以允许将结果检索为字符串. 
     * mainly taken from org.apache.taglibs.standard.tag.common.core.importSupport
     */
    public static class ImportResponseWrapper extends HttpServletResponseWrapper{

        private final StringWriter sw = new StringWriter();
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        private final ServletOutputStream sos = new ServletOutputStream() {
            @Override
            public void write(int b) throws IOException {
                bos.write(b);
            }

            @Override
            public boolean isReady() {
                // Non-blocking IO not supported
                return false;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
                // Non-blocking IO not supported
                throw new UnsupportedOperationException();
            }
        };
        private boolean isWriterUsed;
        private boolean isStreamUsed;
        private int status = 200;
        private String charEncoding;

        public ImportResponseWrapper(HttpServletResponse arg0) {
            super(arg0);
        }

        @Override
        public PrintWriter getWriter() {
            if (isStreamUsed)
                throw new IllegalStateException("Unexpected internal error during &lt;import&gt: " +
                "Target servlet called getWriter(), then getOutputStream()");
            isWriterUsed = true;
            return new PrintWriter(sw);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (isWriterUsed)
                throw new IllegalStateException("Unexpected internal error during &lt;import&gt: " +
                "Target servlet called getOutputStream(), then getWriter()");
            isStreamUsed = true;
            return sos;
        }

        @Override
        public void setContentType(String x) {
            // ignore
        }

        @Override
        public void setLocale(Locale x) {
            // ignore
        }

        @Override
        public void setStatus(int status) {
            this.status = status;
        }

        @Override
        public int getStatus() {
            return status;
        }

        public String getCharEncoding(){
            return this.charEncoding;
        }

        public void setCharEncoding(String ce){
            this.charEncoding = ce;
        }

        public String getString() throws UnsupportedEncodingException {
            if (isWriterUsed)
                return sw.toString();
            else if (isStreamUsed) {
                if (this.charEncoding != null && !this.charEncoding.equals(""))
                    return bos.toString(charEncoding);
                else
                    return bos.toString("ISO-8859-1");
            } else
                return ""; // target didn't write anything
        }
    }
}
