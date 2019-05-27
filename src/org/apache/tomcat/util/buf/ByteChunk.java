package org.apache.tomcat.util.buf;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


/**
 * 此类用于表示一块字节, 以及用于操作byte[].
 *
 * 可以修改缓冲区并将其用于输入和输出.
 *
 * 有2种模式: 块可以与接收器相关联 - ByteInputChannel 或 ByteOutputChannel, 当缓冲区为空（输入时）或填充（输出时）时将使用.
 * 对于输出, 它也可以增长. 通过调用 setLimit() 或limit != -1的allocate(initial, limit) 选择此操作模式.
 *
 * 定义了各种搜索和追加方法 - 与String和StringBuffer类似, 但是按字节操作.
 *
 * 这很重要, 因为它允许直接在接收的字节上处理http标头, 没有转换为字符和字符串, 直到需要字符串.此外，稍后将根据标头或用户代码确定字符集.
 */
public final class ByteChunk implements Cloneable, Serializable {

    private static final long serialVersionUID = 1L;

    /** 输入接口, 当缓冲区为空时使用
     *
     * Same as java.nio.channel.ReadableByteChannel
     */
    public static interface ByteInputChannel {
        /**
         * 读取新字节.
         *
         * @return 读取的字节数
         *
         * @throws IOException 如果在读取字节时发生I/O
         */
        public int realReadBytes() throws IOException;
    }

    /** Same as java.nio.channel.WritableByteChannel.
     */
    public static interface ByteOutputChannel {
        /**
         * 发送字节 ( 通常是内部转换缓冲区 ).
         * 如果缓冲区已满, 则预计输出8k.
         *
         * @param cbuf 将写入的字节
         * @param off 字节数组中的偏移量
         * @param len 将写入的长度
         * @throws IOException 如果在写入字节时发生I/O
         */
        public void realWriteBytes(byte cbuf[], int off, int len)
            throws IOException;

        /**
         * 发送字节 ( 通常是内部转换缓冲区 ).
         * 如果缓冲区已满, 则预计输出8k.
         *
         * @param from 将写入的字节
         * @throws IOException 如果在写入字节时发生I/O
         */
        public void realWriteBytes(ByteBuffer from) throws IOException;
    }

    // --------------------

    /** 用于转换为字符串的默认编码. 它应该是UTF8, 因为大多数标准似乎都在趋同, 但servlet API需要8859_1, 这个对象主要用于servlet.
    */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.ISO_8859_1;

    private int hashCode=0;
    // 是否计算了哈希码 ?
    private boolean hasHashCode = false;

    // byte[]
    private byte[] buff;

    private int start=0;
    private int end;

    private Charset charset;

    private boolean isSet=false; // XXX

    // 添加数据时，它可以增长多少
    private int limit=-1;

    private ByteInputChannel in = null;
    private ByteOutputChannel out = null;

    public ByteChunk() {
        // NO-OP
    }

    public ByteChunk( int initial ) {
        allocate( initial, -1 );
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public boolean isNull() {
        return ! isSet; // buff==null;
    }

    /**
     * 将消息buff重置为未初始化状态.
     */
    public void recycle() {
        charset=null;
        start=0;
        end=0;
        isSet=false;
        hasHashCode = false;
    }

    // -------------------- Setup --------------------

    public void allocate( int initial, int limit  ) {
        if( buff==null || buff.length < initial ) {
            buff=new byte[initial];
        }
        this.limit=limit;
        start=0;
        end=0;
        isSet=true;
        hasHashCode = false;
    }

    /**
     * 将消息字节设置为指定的字节子数组.
     *
     * @param b ascii字节
     * @param off 字节的起始偏移量
     * @param len 字节的长度
     */
    public void setBytes(byte[] b, int off, int len) {
        buff = b;
        start = off;
        end = start+ len;
        isSet=true;
        hasHashCode = false;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    public Charset getCharset() {
        if (charset == null) {
            charset = DEFAULT_CHARSET;
        }
        return charset;
    }

    /**
     * @return 消息字节.
     */
    public byte[] getBytes() {
        return getBuffer();
    }

    /**
     * @return 消息字节.
     */
    public byte[] getBuffer() {
        return buff;
    }

    /**
     * @return 字节的起始偏移量. 对于输出，这是缓冲区的结尾.
     */
    public int getStart() {
        return start;
    }

    public int getOffset() {
        return start;
    }

    public void setOffset(int off) {
        if (end < off ) {
            end=off;
        }
        start=off;
    }

    /**
     * @return 字节的长度.
     */
    public int getLength() {
        return end-start;
    }

    /**
     * 此缓冲区中的最大数据量.
     * 如果为-1或未设置, 缓冲区将无限增长. 可以小于当前缓冲区大小 ( 不会缩小 ).
     * 达到限制值时, 缓冲区将被刷新( 如果设置了 out ), 或者抛出异常.
     * 
     * @param limit 限制值
     */
    public void setLimit(int limit) {
        this.limit=limit;
    }

    public int getLimit() {
        return limit;
    }

    /**
     * 当缓冲区为空时, 从输入通道读取数据.
     * 
     * @param in 输入通道
     */
    public void setByteInputChannel(ByteInputChannel in) {
        this.in = in;
    }

    /**
     * 当缓冲区满时, 将数据写入输出通道.
     * 当附加大量数据时也使用. 如果没有设置, 缓冲区将增长到极限.
     * 
     * @param out 输出通道
     */
    public void setByteOutputChannel(ByteOutputChannel out) {
        this.out=out;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd( int i ) {
        end=i;
    }

    // -------------------- Adding data to the buffer --------------------
    public void append( byte b )
        throws IOException
    {
        makeSpace( 1 );

        // 无法腾出空间
        if( limit >0 && end >= limit ) {
            flushBuffer();
        }
        buff[end++]=b;
    }

    public void append( ByteChunk src )
        throws IOException
    {
        append( src.getBytes(), src.getStart(), src.getLength());
    }

    /**
     * 将数据添加到缓冲区.
     * 
     * @param src 字节数组
     * @param off Offset
     * @param len Length
     * 
     * @throws IOException 将溢出数据写入输出通道失败
     */
    public void append(byte src[], int off, int len) throws IOException {
        // 会增长，达到极限
        makeSpace( len );

        // 如果我们没有限制: makeSpace 可以无限增长
        if( limit < 0 ) {
            // assert: makeSpace 有足够空间
            System.arraycopy( src, off, buff, end, len );
            end+=len;
            return;
        }

        // 优化常见案例.
        // 如果缓冲区为空并且源将填满缓冲区中的所有空间, 也可以直接写入输出, 并避免额外的副本
        if ( len == limit && end == start && out != null ) {
            out.realWriteBytes( src, off, len );
            return;
        }
        // 如果有限制
        if( len <= limit - end ) {
            // makeSpace 将缓冲区增加到极限, 所以我们有空间
            System.arraycopy( src, off, buff, end, len );
            end+=len;
            return;
        }

        // 需要更多的空间，需要刷新缓冲区

        // 缓冲区已经处于（或大于）限制

        // 我们将数据块化为适合缓冲区限制的切片, 虽然如果数据不适合直接写入

        int avail=limit-end;
        System.arraycopy(src, off, buff, end, avail);
        end += avail;

        flushBuffer();

        int remain = len - avail;

        while (remain > (limit - end)) {
            out.realWriteBytes( src, (off + len) - remain, limit - end );
            remain = remain - (limit - end);
        }

        System.arraycopy(src, (off + len) - remain, buff, end, remain);
        end += remain;

    }


    /**
     * 将数据添加到缓冲区.
     *
     * @param from 带有数据的ByteBuffer
     * @throws IOException 将溢出数据写入输出通道失败
     */
    public void append(ByteBuffer from) throws IOException {
        int len = from.remaining();

        // 会增长，达到极限
        makeSpace(len);

        // 如果我们没有限制: makeSpace 可以无限增长
        if (limit < 0) {
            // assert: makeSpace 有足够空间
            from.get(buff, end, len);
            end += len;
            return;
        }

        // 优化常见案例.
        // 如果缓冲区为空并且源将填满缓冲区中的所有空间, 也可以直接写入输出, 并避免额外的副本
        if (len == limit && end == start && out != null) {
            out.realWriteBytes(from);
            from.position(from.limit());
            return;
        }
        // 如果有限制
        if (len <= limit - end) {
            // makeSpace 将缓冲区增加到极限, 所以我们有空间
            from.get(buff, end, len);
            end += len;
            return;
        }

        // 需要更多的空间，需要刷新缓冲区

        // 缓冲区已经处于（或大于）限制

        // 我们将数据块化为适合缓冲区限制的切片, 虽然如果数据不适合直接写入

        int avail = limit - end;
        from.get(buff, end, avail);
        end += avail;

        flushBuffer();

        int fromLimit = from.limit();
        int remain = len - avail;
        avail = limit - end;
        while (remain >= avail) {
            from.limit(from.position() + avail);
            out.realWriteBytes(from);
            from.position(from.limit());
            remain = remain - avail;
        }

        from.limit(fromLimit);
        from.get(buff, end, remain);
        end += remain;
    }


    // -------------------- Removing data from the buffer --------------------

    public int substract() throws IOException {
        if (checkEof()) {
            return -1;
        }
        return buff[start++] & 0xFF;
    }


    public byte substractB() throws IOException {
        if (checkEof()) {
            return -1;
        }
        return buff[start++];
    }


    public int substract(byte dest[], int off, int len ) throws IOException {
        if (checkEof()) {
            return -1;
        }
        int n = len;
        if (len > getLength()) {
            n = getLength();
        }
        System.arraycopy(buff, start, dest, off, n);
        start += n;
        return n;
    }


    /**
     * 将字节从缓冲区传输到指定的ByteBuffer.
     * 在操作之后, ByteBuffer的位置将返回到操作之前的位置, 限制将是传输的字节数增加的位置.
     *
     * @param to 要写入字节的ByteBuffer.
     * 
     * @return 一个整数，指定读取的实际字节数; 或 -1 如果到达流的末尾
     * @throws IOException 如果发生输入或输出异常
     */
    public int substract(ByteBuffer to) throws IOException {
        if (checkEof()) {
            return -1;
        }
        int n = Math.min(to.remaining(), getLength());
        to.put(buff, start, n);
        to.limit(to.position());
        to.position(to.position() - n);
        start += n;
        return n;
    }


    private boolean checkEof() throws IOException {
        if ((end - start) == 0) {
            if (in == null) {
                return true;
            }
            int n = in.realReadBytes();
            if (n < 0) {
                return true;
            }
        }
        return false;
    }


    /**
     * 将缓冲区发送到接收器.
     * 达到限制时由append()调用. 您也可以显式调用它来强制写入数据.
     *
     * @throws IOException 将溢出数据写入输出通道失败
     */
    public void flushBuffer()
        throws IOException
    {
        //assert out!=null
        if( out==null ) {
            throw new IOException( "Buffer overflow, no sink " + limit + " " +
                                   buff.length  );
        }
        out.realWriteBytes( buff, start, end-start );
        end=start;
    }

    /**
     * 为len个字节腾出空间.
     * 如果len很小, 也分配一个预留空间. 永远不要超过限制.
     * 
     * @param count The size
     */
    public void makeSpace(int count) {
        byte[] tmp = null;

        int newSize;
        int desiredSize=end + count;

        // 增长不能超过限制
        if( limit > 0 &&
            desiredSize > limit) {
            desiredSize=limit;
        }

        if( buff==null ) {
            if( desiredSize < 256 )
             {
                desiredSize=256; // take a minimum
            }
            buff=new byte[desiredSize];
        }

        // limit < buf.length (缓冲区已经很大了)
        // 或者已经有空间 XXX
        if( desiredSize <= buff.length ) {
            return;
        }
        // 在更大的块中增长
        if( desiredSize < 2 * buff.length ) {
            newSize= buff.length * 2;
        } else {
            newSize= buff.length * 2 + count ;
        }

        if (limit > 0 && newSize > limit) {
            newSize = limit;
        }
        tmp = new byte[newSize];

        System.arraycopy(buff, start, tmp, 0, end-start);
        buff = tmp;
        tmp = null;
        end=end-start;
        start=0;
    }

    // -------------------- Conversion and getters --------------------

    @Override
    public String toString() {
        if (null == buff) {
            return null;
        } else if (end-start == 0) {
            return "";
        }
        return StringCache.toString(this);
    }

    public String toStringInternal() {
        if (charset == null) {
            charset = DEFAULT_CHARSET;
        }
        // new String(byte[], int, int, Charset) 采用整个字节数组的副本.
        // 如果只使用一小部分字节，这是很昂贵的. 以下代码来自Apache Harmony.
        CharBuffer cb = charset.decode(ByteBuffer.wrap(buff, start, end-start));
        return new String(cb.array(), cb.arrayOffset(), cb.length());
    }

    public long getLong() {
        return Ascii.parseLong(buff, start,end-start);
    }


    // -------------------- equals --------------------

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ByteChunk) {
            return equals((ByteChunk) obj);
        }
        return false;
    }

    /**
     * 将消息字节与指定的String对象进行比较.
     * 
     * @param s 要比较的字符串
     * 
     * @return true 如果比较成功, 否则false
     */
    public boolean equals(String s) {
        // XXX ENCODING - 这仅在编码为UTF8-compat时有效 ( ok for tomcat, where we compare ascii - header names, etc )!!!

        byte[] b = buff;
        int blen = end-start;
        if (b == null || blen != s.length()) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            if (b[boff++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将消息字节与指定的String对象进行比较.
     * 
     * @param s 要比较的字符串
     * 
     * @return true 如果比较成功, 否则false
     */
    public boolean equalsIgnoreCase(String s) {
        byte[] b = buff;
        int blen = end-start;
        if (b == null || blen != s.length()) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            if (Ascii.toLower(b[boff++]) != Ascii.toLower(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean equals( ByteChunk bb ) {
        return equals( bb.getBytes(), bb.getStart(), bb.getLength());
    }

    public boolean equals( byte b2[], int off2, int len2) {
        byte b1[]=buff;
        if( b1==null && b2==null ) {
            return true;
        }

        int len=end-start;
        if ( len2 != len || b1==null || b2==null ) {
            return false;
        }

        int off1 = start;

        while ( len-- > 0) {
            if (b1[off1++] != b2[off2++]) {
                return false;
            }
        }
        return true;
    }

    public boolean equals( CharChunk cc ) {
        return equals( cc.getChars(), cc.getStart(), cc.getLength());
    }

    public boolean equals( char c2[], int off2, int len2) {
        // XXX works only for enc compatible with ASCII/UTF !!!
        byte b1[]=buff;
        if( c2==null && b1==null ) {
            return true;
        }

        if (b1== null || c2==null || end-start != len2 ) {
            return false;
        }
        int off1 = start;
        int len=end-start;

        while ( len-- > 0) {
            if ( (char)b1[off1++] != c2[off2++]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 如果消息字节以指定的字符串开头，则返回true.
     * 
     * @param s 字符串
     * @param pos 位置
     * 
     * @return <code>true</code>如果开头匹配
     */
    public boolean startsWithIgnoreCase(String s, int pos) {
        byte[] b = buff;
        int len = s.length();
        if (b == null || len+pos > end-start) {
            return false;
        }
        int off = start+pos;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower( b[off++] ) != Ascii.toLower( s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public int indexOf( String src, int srcOff, int srcLen, int myOff ) {
        char first=src.charAt( srcOff );

        // 寻找第一个字符
        int srcEnd = srcOff + srcLen;

        mainLoop:
        for( int i=myOff+start; i <= (end - srcLen); i++ ) {
            if( buff[i] != first ) {
                continue;
            }
            // 寻找第一个字符, 现在寻找匹配
            int myPos=i+1;
            for( int srcPos=srcOff + 1; srcPos< srcEnd;) {
                if( buff[myPos++] != src.charAt( srcPos++ )) {
                    continue mainLoop;
                }
            }
            return i-start; // found it
        }
        return -1;
    }

    // -------------------- Hash code  --------------------

    @Override
    public int hashCode() {
        if (hasHashCode) {
            return hashCode;
        }
        int code = 0;

        code = hash();
        hashCode = code;
        hasHashCode = true;
        return code;
    }

    // normal hash.
    public int hash() {
        return hashBytes( buff, start, end-start);
    }

    private static int hashBytes( byte buff[], int start, int bytesLen ) {
        int max=start+bytesLen;
        byte bb[]=buff;
        int code=0;
        for (int i = start; i < max ; i++) {
            code = code * 37 + bb[i];
        }
        return code;
    }

    /**
     * 从指定的字节开始, 返回此ByteChunk中给定字符的第一个实例. 如果找不到该字符, 返回 -1.
     * <br>
     * NOTE: 这仅适用于0-127范围内的字符.
     *
     * @param c         字符
     * @param starting  起始位置
     * 
     * @return          字符的第一个实例的位置; 如果找不到字符，则返回-1.
     */
    public int indexOf(char c, int starting) {
        int ret = indexOf(buff, start + starting, end, c);
        return (ret >= start) ? ret - start : -1;
    }

    /**
     * 返回给定字节数组中指定的start和end之间给定字符的第一个实例.
     * <br>
     * NOTE: 这仅适用于0-127范围内的字符.
     *
     * @param bytes 要搜索的字节数组
     * @param start 在字节数组中开始搜索的点
     * @param end   在字节数组中停止搜索的点
     * @param c     要搜索的字符
     * 
     * @return      字符的第一个实例的位置; 如果找不到字符，则返回-1.
     */
    public static int indexOf(byte bytes[], int start, int end, char c) {
        int offset = start;

        while (offset < end) {
            byte b=bytes[offset];
            if (b == c) {
                return offset;
            }
            offset++;
        }
        return -1;
    }

    /**
     * 返回字节数组中指定的start和end之间的给定字节的第一个实例.
     *
     * @param bytes 要搜索的字节数组
     * @param start 在字节数组中开始搜索的点
     * @param end   在字节数组中停止搜索的点
     * @param b     要搜索的字节
     * 
     * @return      字节的第一个实例的位置; 如果找不到字节，则返回-1..
     */
    public static int findByte(byte bytes[], int start, int end, byte b) {
        int offset = start;
        while (offset < end) {
            if (bytes[offset] == b) {
                return offset;
            }
            offset++;
        }
        return -1;
    }

    /**
     * 返回字节数组中指定的start和end之间的任何给定字节的第一个实例.
     *
     * @param bytes 要搜索的字节数组
     * @param start 在字节数组中开始搜索的点
     * @param end   在字节数组中停止搜索的点
     * @param b     要搜索的字节数组
     * 
     * @return      字节的第一个实例的位置; 如果找不到字节，则返回-1..
     */
    public static int findBytes(byte bytes[], int start, int end, byte b[]) {
        int blen = b.length;
        int offset = start;
        while (offset < end) {
            for (int i = 0;  i < blen; i++) {
                if (bytes[offset] == b[i]) {
                    return offset;
                }
            }
            offset++;
        }
        return -1;
    }

    /**
     * 将指定的String转换为字节数组. 这只适用于ascii, UTF字符将被截断.
     *
     * @param value 要转换为字节数组的字符串
     * 
     * @return 字节数组
     */
    public static final byte[] convertToBytes(String value) {
        byte[] result = new byte[value.length()];
        for (int i = 0; i < value.length(); i++) {
            result[i] = (byte) value.charAt(i);
        }
        return result;
    }
}
