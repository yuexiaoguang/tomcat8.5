package org.apache.tomcat.util.buf;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * 此类用于表示HTTP消息中的字节子数组.
 * 它代表所有请求/响应元素. 字节/字符转换被延迟和缓存. 一切都是可回收的.
 *
 * 对象可以表示 byte[], char[], (sub) String. 所有操作都可以在区分大小写模式下进行.
 */
public final class MessageBytes implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    // 主要类型 ( 无论设置为原始值 )
    private int type = T_NULL;

    public static final int T_NULL = 0;
    /** getType() 是 T_STR, 如果用于创建MessageBytes的对象是String */
    public static final int T_STR  = 1;
    /** getType() 是 T_BYTES, 如果用于创建MessageBytes的对象是byte[] */
    public static final int T_BYTES = 2;
    /** getType() 是 T_CHARS, 如果用于创建MessageBytes的对象是char[] */
    public static final int T_CHARS = 3;

    private int hashCode=0;
    // 是否计算了哈希码 ?
    private boolean hasHashCode=false;

    // 内部对象表示数组+偏移量，以及特定方法
    private final ByteChunk byteC=new ByteChunk();
    private final CharChunk charC=new CharChunk();

    // String
    private String strValue;
    // true 如果计算了String值. 可能不需要, strValue!=null 也是一样的
    private boolean hasStrValue=false;

    /**
     * 使用静态 newInstance() 以允许将来挂钩.
     */
    private MessageBytes() {
    }

    public static MessageBytes newInstance() {
        return factory.newInstance();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public boolean isNull() {
        return byteC.isNull() && charC.isNull() && ! hasStrValue;
    }

    /**
     * 将消息字节重置为未初始化（NULL）状态.
     */
    public void recycle() {
        type=T_NULL;
        byteC.recycle();
        charC.recycle();

        strValue=null;

        hasStrValue=false;
        hasHashCode=false;
        hasLongValue=false;
    }


    /**
     * 将内容设置为指定的字节子数组.
     *
     * @param b 字节
     * @param off 字节的起始偏移量
     * @param len 字节的长度
     */
    public void setBytes(byte[] b, int off, int len) {
        byteC.setBytes( b, off, len );
        type=T_BYTES;
        hasStrValue=false;
        hasHashCode=false;
        hasLongValue=false;
    }

    /**
     * 将内容设置为char[]
     *
     * @param c the chars
     * @param off 字符的起始偏移量
     * @param len 字符的长度
     */
    public void setChars( char[] c, int off, int len ) {
        charC.setChars( c, off, len );
        type=T_CHARS;
        hasStrValue=false;
        hasHashCode=false;
        hasLongValue=false;
    }

    /**
     * 将内容设置为字符串
     * 
     * @param s
     */
    public void setString( String s ) {
        strValue=s;
        hasHashCode=false;
        hasLongValue=false;
        if (s == null) {
            hasStrValue=false;
            type=T_NULL;
        } else {
            hasStrValue=true;
            type=T_STR;
        }
    }

    // -------------------- Conversion and getters --------------------

    /**
     * 计算字符串值.
     */
    @Override
    public String toString() {
        if( hasStrValue ) {
            return strValue;
        }

        switch (type) {
        case T_CHARS:
            strValue=charC.toString();
            hasStrValue=true;
            return strValue;
        case T_BYTES:
            strValue=byteC.toString();
            hasStrValue=true;
            return strValue;
        }
        return null;
    }

    //----------------------------------------
    /**
     * 返回原始内容的类型. 可能是 T_STR, T_BYTES, T_CHARS, T_NULL
     */
    public int getType() {
        return type;
    }

    /**
     * 返回字节块, 表示byte[]和 offset/length.
     * 仅在T_BYTES或转换后有效.
     */
    public ByteChunk getByteChunk() {
        return byteC;
    }

    /**
     * 返回char块, 表示char[]和 offset/length.
     * 仅在T_CHARS或转换后有效.
     */
    public CharChunk getCharChunk() {
        return charC;
    }

    /**
     * 返回字符串值.
     * 仅在T_STR或转换后有效.
     */
    public String getString() {
        return strValue;
    }

    /**
     * @return 用于字符串向字节转换的Charset.
     */
    public Charset getCharset() {
        return byteC.getCharset();
    }

    /**
     * 用于字符串向字节转换的Charset.
     * 
     * @param charset
     */
    public void setCharset(Charset charset) {
        byteC.setCharset(charset);
    }

    /**
     * 字符向字节转换.
     */
    public void toBytes() {
        if (!byteC.isNull()) {
            type=T_BYTES;
            return;
        }
        toString();
        type=T_BYTES;
        Charset charset = byteC.getCharset();
        ByteBuffer result = charset.encode(strValue);
        byteC.setBytes(result.array(), result.arrayOffset(), result.limit());
    }

    /**
     * 转换为char[]并填充CharChunk.
     * XXX 没有优化 - 它首先转换为String.
     */
    public void toChars() {
        if( ! charC.isNull() ) {
            type=T_CHARS;
            return;
        }
        // inefficient
        toString();
        type=T_CHARS;
        char cc[]=strValue.toCharArray();
        charC.setChars(cc, 0, cc.length);
    }


    /**
     * 返回原始缓冲区的长度.
     * 请注意，字节长度可能与字符长度不同.
     */
    public int getLength() {
        if(type==T_BYTES) {
            return byteC.getLength();
        }
        if(type==T_CHARS) {
            return charC.getLength();
        }
        if(type==T_STR) {
            return strValue.length();
        }
        toString();
        if( strValue==null ) {
            return 0;
        }
        return strValue.length();
    }

    // -------------------- equals --------------------

    /**
     * 将消息字节与指定的String对象进行比较.
     * 
     * @param s 要比较的字符串
     * 
     * @return <code>true</code> 如果比较成功, 否则<code>false</code>
     */
    public boolean equals(String s) {
        switch (type) {
        case T_STR:
            if (strValue == null) {
                return s == null;
            }
            return strValue.equals( s );
        case T_CHARS:
            return charC.equals( s );
        case T_BYTES:
            return byteC.equals( s );
        default:
            return false;
        }
    }

    /**
     * 将消息字节与指定的String对象进行比较.
     * 
     * @param s 要比较的字符串
     * 
     * @return <code>true</code> 如果比较成功, 否则<code>false</code>
     */
    public boolean equalsIgnoreCase(String s) {
        switch (type) {
        case T_STR:
            if (strValue == null) {
                return s == null;
            }
            return strValue.equalsIgnoreCase( s );
        case T_CHARS:
            return charC.equalsIgnoreCase( s );
        case T_BYTES:
            return byteC.equalsIgnoreCase( s );
        default:
            return false;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MessageBytes) {
            return equals((MessageBytes) obj);
        }
        return false;
    }

    public boolean equals(MessageBytes mb) {
        switch (type) {
        case T_STR:
            return mb.equals( strValue );
        }

        if( mb.type != T_CHARS &&
            mb.type!= T_BYTES ) {
            // it's a string or int/date string value
            return equals( mb.toString() );
        }

        // mb 可能是 CHARS 或 BYTES.
        // 这是CHARS或BYTES
        // Deal with the 4 cases ( in fact 3, one is symmetric)

        if( mb.type == T_CHARS && type==T_CHARS ) {
            return charC.equals( mb.charC );
        }
        if( mb.type==T_BYTES && type== T_BYTES ) {
            return byteC.equals( mb.byteC );
        }
        if( mb.type== T_CHARS && type== T_BYTES ) {
            return byteC.equals( mb.charC );
        }
        if( mb.type== T_BYTES && type== T_CHARS ) {
            return mb.byteC.equals( charC );
        }
        // can't happen
        return true;
    }


    /**
     * @param s
     * @param pos 开始位置
     * 
     * @return <code>true</code> 如果消息字节以指定的字符串开头.
     */
    public boolean startsWithIgnoreCase(String s, int pos) {
        switch (type) {
        case T_STR:
            if( strValue==null ) {
                return false;
            }
            if( strValue.length() < pos + s.length() ) {
                return false;
            }

            for( int i=0; i<s.length(); i++ ) {
                if( Ascii.toLower( s.charAt( i ) ) !=
                    Ascii.toLower( strValue.charAt( pos + i ))) {
                    return false;
                }
            }
            return true;
        case T_CHARS:
            return charC.startsWithIgnoreCase( s, pos );
        case T_BYTES:
            return byteC.startsWithIgnoreCase( s, pos );
        default:
            return false;
        }
    }


    // -------------------- Hash code  --------------------
    @Override
    public  int hashCode() {
        if( hasHashCode ) {
            return hashCode;
        }
        int code = 0;

        code=hash();
        hashCode=code;
        hasHashCode=true;
        return code;
    }

    // normal hash.
    private int hash() {
        int code=0;
        switch (type) {
        case T_STR:
            // 需要使用相同的哈希函数
            for (int i = 0; i < strValue.length(); i++) {
                code = code * 37 + strValue.charAt( i );
            }
            return code;
        case T_CHARS:
            return charC.hash();
        case T_BYTES:
            return byteC.hash();
        default:
            return 0;
        }
    }

    // 最初的实现效率低下. 将在下一轮调整中被取代
    public int indexOf(String s, int starting) {
        toString();
        return strValue.indexOf( s, starting );
    }

    // 最初的实现效率低下. 将在下一轮调整中被取代
    public int indexOf(String s) {
        return indexOf( s, 0 );
    }

    public int indexOfIgnoreCase(String s, int starting) {
        toString();
        String upper=strValue.toUpperCase(Locale.ENGLISH);
        String sU=s.toUpperCase(Locale.ENGLISH);
        return upper.indexOf( sU, starting );
    }

    /**
     * 将src复制到此MessageBytes中, 如果需要, 分配更多空间.
     * 
     * @param src
     * 
     * @throws IOException 将溢出数据写入输出通道失败
     */
    public void duplicate( MessageBytes src ) throws IOException
    {
        switch( src.getType() ) {
        case MessageBytes.T_BYTES:
            type=T_BYTES;
            ByteChunk bc=src.getByteChunk();
            byteC.allocate( 2 * bc.getLength(), -1 );
            byteC.append( bc );
            break;
        case MessageBytes.T_CHARS:
            type=T_CHARS;
            CharChunk cc=src.getCharChunk();
            charC.allocate( 2 * cc.getLength(), -1 );
            charC.append( cc );
            break;
        case MessageBytes.T_STR:
            type=T_STR;
            String sc=src.getString();
            this.setString( sc );
            break;
        }
        setCharset(src.getCharset());
    }

    // -------------------- Deprecated code --------------------
    // efficient long
    // XXX 仅用于 header - 不应该存储在这里.
    private long longValue;
    private boolean hasLongValue=false;

    /**
     * 将缓冲区设置为long的表示形式.
     * 
     * @param l
     */
    public void setLong(long l) {
        byteC.allocate(32, 64);
        long current = l;
        byte[] buf = byteC.getBuffer();
        int start = 0;
        int end = 0;
        if (l == 0) {
            buf[end++] = (byte) '0';
        }
        if (l < 0) {
            current = -l;
            buf[end++] = (byte) '-';
        }
        while (current > 0) {
            int digit = (int) (current % 10);
            current = current / 10;
            buf[end++] = HexUtils.getHex(digit);
        }
        byteC.setOffset(0);
        byteC.setEnd(end);
        // Inverting buffer
        end--;
        if (l < 0) {
            start++;
        }
        while (end > start) {
            byte temp = buf[start];
            buf[start] = buf[end];
            buf[end] = temp;
            start++;
            end--;
        }
        longValue=l;
        hasStrValue=false;
        hasHashCode=false;
        hasLongValue=true;
        type=T_BYTES;
    }

    // Used for headers conversion
    /**
     * 将缓冲区转换为long, 缓存值.
     */
    public long getLong() {
        if( hasLongValue ) {
            return longValue;
        }

        switch (type) {
        case T_BYTES:
            longValue=byteC.getLong();
            break;
        default:
            longValue=Long.parseLong(toString());
        }

        hasLongValue=true;
        return longValue;

     }

    // -------------------- Future may be different --------------------

    private static final MessageBytesFactory factory=new MessageBytesFactory();

    private static class MessageBytesFactory {
        protected MessageBytesFactory() {
        }
        public MessageBytes newInstance() {
            return new MessageBytes();
        }
    }
}
