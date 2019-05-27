package org.apache.tomcat.util.buf;

import java.io.ByteArrayOutputStream;
import java.io.CharConversionException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 *  所有URL解码都在此处进行. 这样我们就可以重用，审查和优化，而不会增加缓冲区的复杂性.
 *
 *  转换将修改原始缓冲区.
 */
public final class UDecoder {

    private static final StringManager sm = StringManager.getManager(UDecoder.class);

    private static final Log log = LogFactory.getLog(UDecoder.class);

    public static final boolean ALLOW_ENCODED_SLASH =
        Boolean.parseBoolean(System.getProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "false"));

    private static class DecodeException extends CharConversionException {
        private static final long serialVersionUID = 1L;
        public DecodeException(String s) {
            super(s);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            // 此类不提供堆栈跟踪
            return this;
        }
    }

    /** 意外的数据结束. */
    private static final IOException EXCEPTION_EOF = new DecodeException("EOF");

    /** %xx 没有十六进制数字 */
    private static final IOException EXCEPTION_NOT_HEX_DIGIT = new DecodeException(
            "isHexDigit");

    /** 资源路径中禁止使用编码%-斜杠 */
    private static final IOException EXCEPTION_SLASH = new DecodeException(
            "noSlash");

    public UDecoder()
    {
    }

    /**
     * URLDecode，将修改源.
     * 
     * @param mb URL编码的字节
     * @param query <code>true</code> 如果这是一个查询字符串
     * @throws IOException 无效的 %xx URL 编码
     */
    public void convert( ByteChunk mb, boolean query )
        throws IOException
    {
        int start=mb.getOffset();
        byte buff[]=mb.getBytes();
        int end=mb.getEnd();

        int idx= ByteChunk.findByte( buff, start, end, (byte) '%' );
        int idx2=-1;
        if( query ) {
            idx2= ByteChunk.findByte( buff, start, (idx >= 0 ? idx : end), (byte) '+' );
        }
        if( idx<0 && idx2<0 ) {
            return;
        }

        // idx 将是最小的正索引 ( first % or + )
        if( (idx2 >= 0 && idx2 < idx) || idx < 0 ) {
            idx=idx2;
        }

        final boolean noSlash = !(ALLOW_ENCODED_SLASH || query);

        for( int j=idx; j<end; j++, idx++ ) {
            if( buff[ j ] == '+' && query) {
                buff[idx]= (byte)' ' ;
            } else if( buff[ j ] != '%' ) {
                buff[idx]= buff[j];
            } else {
                // 读取后2位数字
                if( j+2 >= end ) {
                    throw EXCEPTION_EOF;
                }
                byte b1= buff[j+1];
                byte b2=buff[j+2];
                if( !isHexDigit( b1 ) || ! isHexDigit(b2 )) {
                    throw EXCEPTION_NOT_HEX_DIGIT;
                }

                j+=2;
                int res=x2c( b1, b2 );
                if (noSlash && (res == '/')) {
                    throw EXCEPTION_SLASH;
                }
                buff[idx]=(byte)res;
            }
        }

        mb.setEnd( idx );

        return;
    }

    // -------------------- Additional methods --------------------
    // XXX What do we do about charset ????

    /**
     * 缓冲区内处理 - 缓冲区将被修改.
     * 
     * @param mb URL编码的字符
     * @param query <code>true</code> 如果这是一个查询字符串
     * 
     * @throws IOException 无效的 %xx URL 编码
     */
    public void convert( CharChunk mb, boolean query )
        throws IOException
    {
        //        log( "Converting a char chunk ");
        int start=mb.getOffset();
        char buff[]=mb.getBuffer();
        int cend=mb.getEnd();

        int idx= CharChunk.indexOf( buff, start, cend, '%' );
        int idx2=-1;
        if( query ) {
            idx2= CharChunk.indexOf( buff, start, (idx >= 0 ? idx : cend), '+' );
        }
        if( idx<0 && idx2<0 ) {
            return;
        }

        // idx 将是最小的正数索引 ( first % or + )
        if( (idx2 >= 0 && idx2 < idx) || idx < 0 ) {
            idx=idx2;
        }

        final boolean noSlash = !(ALLOW_ENCODED_SLASH || query);

        for( int j=idx; j<cend; j++, idx++ ) {
            if( buff[ j ] == '+' && query ) {
                buff[idx]=( ' ' );
            } else if( buff[ j ] != '%' ) {
                buff[idx]=buff[j];
            } else {
                // read next 2 digits
                if( j+2 >= cend ) {
                    // invalid
                    throw EXCEPTION_EOF;
                }
                char b1= buff[j+1];
                char b2=buff[j+2];
                if( !isHexDigit( b1 ) || ! isHexDigit(b2 )) {
                    throw EXCEPTION_NOT_HEX_DIGIT;
                }

                j+=2;
                int res=x2c( b1, b2 );
                if (noSlash && (res == '/')) {
                    throw EXCEPTION_SLASH;
                }
                buff[idx]=(char)res;
            }
        }
        mb.setEnd( idx );
    }

    /**
     * URLDecode, 将修改源
     * 
     * @param mb URL编码的字符串, 字节或字符
     * @param query <code>true</code>如果这是一个查询字符串
     * 
     * @throws IOException 无效的 %xx URL 编码
     */
    public void convert(MessageBytes mb, boolean query)
        throws IOException
    {

        switch (mb.getType()) {
        case MessageBytes.T_STR:
            String strValue=mb.toString();
            if( strValue==null ) {
                return;
            }
            try {
                mb.setString( convert( strValue, query ));
            } catch (RuntimeException ex) {
                throw new DecodeException(ex.getMessage());
            }
            break;
        case MessageBytes.T_CHARS:
            CharChunk charC=mb.getCharChunk();
            convert( charC, query );
            break;
        case MessageBytes.T_BYTES:
            ByteChunk bytesC=mb.getByteChunk();
            convert( bytesC, query );
            break;
        }
    }

    /**
     * 字符串的 %xx 解码. FIXME: 这是低效的.
     * 
     * @param str URL编码的字符串
     * @param query <code>true</code> 如果这是一个查询字符串
     * 
     * @return 解码后的字符串
     */
    public final String convert(String str, boolean query)
    {
        if (str == null) {
            return  null;
        }

        if( (!query || str.indexOf( '+' ) < 0) && str.indexOf( '%' ) < 0 ) {
            return str;
        }

        final boolean noSlash = !(ALLOW_ENCODED_SLASH || query);

        StringBuilder dec = new StringBuilder();    // decoded string output
        int strPos = 0;
        int strLen = str.length();

        dec.ensureCapacity(str.length());
        while (strPos < strLen) {
            int laPos;        // lookahead position

            // 展望下一个URLencoded元字符
            for (laPos = strPos; laPos < strLen; laPos++) {
                char laChar = str.charAt(laPos);
                if ((laChar == '+' && query) || (laChar == '%')) {
                    break;
                }
            }

            // 如果没有元字符, 将它们全部复制为块
            if (laPos > strPos) {
                dec.append(str.substring(strPos,laPos));
                strPos = laPos;
            }

            // 如果在字符串的末尾，则离开此处
            if (strPos >= strLen) {
                break;
            }

            // 处理下一个元字符
            char metaChar = str.charAt(strPos);
            if (metaChar == '+') {
                dec.append(' ');
                strPos++;
                continue;
            } else if (metaChar == '%') {
                // 抛出原始异常 - 超类将处理它
                //                try {
                char res = (char) Integer.parseInt(
                        str.substring(strPos + 1, strPos + 3), 16);
                if (noSlash && (res == '/')) {
                    throw new IllegalArgumentException("noSlash");
                }
                dec.append(res);
                strPos += 3;
            }
        }

        return dec.toString();
    }


    /**
     * 解码并返回指定的URL编码的String.
     * 当字节数组转换为字符串时, 使用ISO-885901. 这可能与其他一些服务器不同. 假设字符串不是查询字符串.
     *
     * @param str url编码的字符串
     * @return 解码后的字符串
     * @exception IllegalArgumentException 如果'%'字符后面没有有效的2位十六进制数字
     */
    public static String URLDecode(String str) {
        return URLDecode(str, StandardCharsets.ISO_8859_1);
    }


    /**
     * 解码并返回指定的URL编码的String. 假设字符串不是查询字符串.
     *
     * @param str url编码的字符串
     * @param enc 要使用的编码; 如果是 null, 使用 ISO-885901. 如果指定了不受支持的编码，则返回null
     * @return 解码后的字符串
     * @exception IllegalArgumentException 如果'%'字符后面没有有效的2位十六进制数字
     *
     * @deprecated This method will be removed in Tomcat 9
     */
    @Deprecated
    public static String URLDecode(String str, String enc) {
        return URLDecode(str, enc, false);
    }


    /**
     * 解码并返回指定的URL编码的String. 假设字符串不是查询字符串.
     *
     * @param str url编码的字符串
     * @param charset 要使用的字符编码; 如果是 null, 使用 ISO-885901.
     * 
     * @return 解码后的字符串
     * @exception IllegalArgumentException 如果'%'字符后面没有有效的2位十六进制数字
     */
    public static String URLDecode(String str, Charset charset) {
        return URLDecode(str, charset, false);
    }


    /**
     * 解码并返回指定的URL编码的String.
     *
     * @param str url编码的字符串
     * @param enc 要使用的编码; 如果是 null, 使用ISO-8859-1. 如果指定了不受支持的编码，则返回null
     * @param isQuery 是否正在处理的查询字符串
     * 
     * @return 解码后的字符串
     * @exception IllegalArgumentException 如果'%'字符后面没有有效的2位十六进制数字
     *
     * @deprecated This method will be removed in Tomcat 9
     */
    @Deprecated
    public static String URLDecode(String str, String enc, boolean isQuery) {
        Charset charset = null;

        if (enc != null) {
            try {
                charset = B2CConverter.getCharset(enc);
            } catch (UnsupportedEncodingException uee) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("uDecoder.urlDecode.uee", enc), uee);
                }
            }
        }

        return URLDecode(str, charset, isQuery);
    }


    /**
     * 解码并返回指定的URL编码字节数组.
     *
     * @param bytes url编码的字节数组
     * @param enc 要使用的编码; 如果是 null, 使用ISO-8859-1. 如果指定了不受支持的编码，则返回null
     * @param isQuery 是否正在处理的查询字符串
     * 
     * @return the 解码后的字符串
     * @exception IllegalArgumentException 如果'%'字符后面没有有效的2位十六进制数字
     *
     * @deprecated This method will be removed in Tomcat 9
     */
    @Deprecated
    public static String URLDecode(byte[] bytes, String enc, boolean isQuery) {
        throw new IllegalArgumentException(sm.getString("udecoder.urlDecode.iae"));
    }


    private static String URLDecode(String str, Charset charset, boolean isQuery) {

        if (str == null) {
            return null;
        }

        if (str.indexOf('%') == -1) {
            // 没有％nn序列, 所以返回字符串不变
            return str;
        }

        if (charset == null) {
            charset = StandardCharsets.ISO_8859_1;
        }

        /*
         * 需要解码.
         *
         * 潜在的组合:
         * - 源String可以被部分解码，因此假设源String是ASCII是无效的.
         * - 必须作为字符处理，因为不能保证'%'的字节序列在所有字符集中都是相同的.
         * - 我们不知道单个字符需要多少'％nn'序列. 它在字符集之间变化, 有些使用可变长度.
         */

        // 这并不完美，但它是数组需要的大小的合理猜测
        ByteArrayOutputStream baos = new ByteArrayOutputStream(str.length() * 2);

        OutputStreamWriter osw = new OutputStreamWriter(baos, charset);

        char[] sourceChars = str.toCharArray();
        int len = sourceChars.length;
        int ix = 0;

        try {
            while (ix < len) {
                char c = sourceChars[ix++];
                if (c == '%') {
                    osw.flush();
                    if (ix + 2 > len) {
                        throw new IllegalArgumentException(
                                sm.getString("uDecoder.urlDecode.missingDigit", str));
                    }
                    char c1 = sourceChars[ix++];
                    char c2 = sourceChars[ix++];
                    if (isHexDigit(c1) && isHexDigit(c2)) {
                        baos.write(x2c(c1, c2));
                    } else {
                        throw new IllegalArgumentException(
                                sm.getString("uDecoder.urlDecode.missingDigit", str));
                    }
                } else if (c == '+' && isQuery) {
                    osw.append(' ');
                } else {
                    osw.append(c);
                }
            }
            osw.flush();

            return baos.toString(charset.name());
        } catch (IOException ioe) {
            throw new IllegalArgumentException(
                    sm.getString("uDecoder.urlDecode.conversionError", str, charset.name()), ioe);
        }
    }


    private static boolean isHexDigit( int c ) {
        return ( ( c>='0' && c<='9' ) ||
                 ( c>='a' && c<='f' ) ||
                 ( c>='A' && c<='F' ));
    }


    private static int x2c( byte b1, byte b2 ) {
        int digit= (b1>='A') ? ( (b1 & 0xDF)-'A') + 10 :
            (b1 -'0');
        digit*=16;
        digit +=(b2>='A') ? ( (b2 & 0xDF)-'A') + 10 :
            (b2 -'0');
        return digit;
    }


    private static int x2c( char b1, char b2 ) {
        int digit= (b1>='A') ? ( (b1 & 0xDF)-'A') + 10 :
            (b1 -'0');
        digit*=16;
        digit +=(b2>='A') ? ( (b2 & 0xDF)-'A') + 10 :
            (b2 -'0');
        return digit;
    }
}
