package org.apache.tomcat.util.buf;

import java.io.IOException;
import java.io.Serializable;

/**
 * 用于操作char块.
 * String是操纵字符最简单的方法, 众所周知, 它不是最有效的解决方案 - 字符串被设计为不可变和安全的对象.
 */
public final class CharChunk implements Cloneable, Serializable, CharSequence {

    private static final long serialVersionUID = 1L;

    // 输入接口, 清空缓冲区时使用.
    public static interface CharInputChannel {
        /**
         * 读取新字符.
         *
         * @return 读取的字符数
         *
         * @throws IOException 如果读取字符时发生I/O错误
         */
        public int realReadChars() throws IOException;
    }
    /**
     *  当需要更多空间时, 将增加缓冲区（达到极限）或将其发送到通道.
     */
    public static interface CharOutputChannel {
        /**
         * 发送字节 ( 通常是内部转换缓冲区 ).
         * 如果缓冲区已满, 则预计输出8k.
         *
         * @param cbuf 将要写入的字符
         * @param off 字符数组中的偏移量
         * @param len 将写入的长度
         * 
         * @throws IOException 如果在写入字符时发生 I/O
         */
        public void realWriteChars(char cbuf[], int off, int len)
            throws IOException;
    }

    // --------------------

    private int hashCode = 0;
    // 是否计算了哈希码 ?
    private boolean hasHashCode = false;

    // char[]
    private char buff[];

    private int start;
    private int end;

    private boolean isSet=false;  // XXX

    // -1: 无限增长
    // 最大缓存量
    private int limit=-1;

    private CharInputChannel in = null;
    private CharOutputChannel out = null;

    private boolean optimizedWrite=true;

    public CharChunk() {
    }

    public CharChunk(int size) {
        allocate( size, -1 );
    }

    // --------------------

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public boolean isNull() {
        if( end > 0 ) {
            return false;
        }
        return !isSet; //XXX
    }

    /**
     * 将消息字节重置为未初始化状态.
     */
    public void recycle() {
        //        buff=null;
        isSet=false; // XXX
        hasHashCode = false;
        start=0;
        end=0;
    }

    // -------------------- Setup --------------------

    public void allocate( int initial, int limit  ) {
        if( buff==null || buff.length < initial ) {
            buff=new char[initial];
        }
        this.limit=limit;
        start=0;
        end=0;
        isSet=true;
        hasHashCode = false;
    }


    public void setOptimizedWrite(boolean optimizedWrite) {
        this.optimizedWrite = optimizedWrite;
    }

    public void setChars( char[] c, int off, int len ) {
        buff=c;
        start=off;
        end=start + len;
        isSet=true;
        hasHashCode = false;
    }

    /**
     * 此缓冲区中的最大数据量.
     * 如果为-1或未设置, 缓冲区将无限增长.
     * 可以小于当前缓冲区大小 ( 这不会缩小 ).
     * 达到限制时, 缓冲区将被刷新 ( 如果设置了 out ), 或抛出异常.
     * 
     * @param limit 限制
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
    public void setCharInputChannel(CharInputChannel in) {
        this.in = in;
    }

    /**
     * 当缓冲区满时, 将数据写入输出通道.
     * 当附加大量数据时也使用. 如果没有设置, 缓冲区将增长到极限.
     * 
     * @param out 输出通道
     */
    public void setCharOutputChannel(CharOutputChannel out) {
        this.out=out;
    }

    // compat
    public char[] getChars()
    {
        return getBuffer();
    }

    public char[] getBuffer()
    {
        return buff;
    }

    /**
     * @return 字符的起始偏移量. 对于输出, 这是缓冲区的结尾.
     */
    public int getStart() {
        return start;
    }

    public int getOffset() {
        return start;
    }

    public void setOffset(int off) {
        start=off;
    }

    /**
     * @return 字节的长度.
     */
    public int getLength() {
        return end-start;
    }


    public int getEnd() {
        return end;
    }

    public void setEnd( int i ) {
        end=i;
    }

    // -------------------- Adding data --------------------

    public void append( char b )
        throws IOException
    {
        makeSpace( 1 );

        // 没有空间了
        if( limit >0 && end >= limit ) {
            flushBuffer();
        }
        buff[end++]=b;
    }

    public void append( CharChunk src )
        throws IOException
    {
        append( src.getBuffer(), src.getOffset(), src.getLength());
    }

    /**
     * 将数据添加到缓冲区.
     * 
     * @param src
     * @param off Offset
     * @param len Length
     * 
     * @throws IOException 将溢出数据写入输出通道失败
     */
    public void append( char src[], int off, int len )
        throws IOException
    {
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
        if ( optimizedWrite && len == limit && end == start && out != null ) {
            out.realWriteChars( src, off, len );
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

        // 优化:
        // 如果 len-avail < length ( 也就是说，在尽可能地填充缓冲区之后，剩下的将适合缓冲区 )
        // 将只复制第一部分, 刷新, 然后复制第二部分 - 1 写入, 并且仍然有一些空间
        // 仍然会有2次写入, 但在第一次上写得更多.

        if( len + end < 2 * limit ) {
            /* 如果请求长度超过输出缓冲区的大小, 刷新输出缓冲区然后直接写入数据.
             * 无法避免2次写作, 但可以在第二次上写得更多
             */
            int avail=limit-end;
            System.arraycopy(src, off, buff, end, avail);
            end += avail;

            flushBuffer();

            System.arraycopy(src, off+avail, buff, end, len - avail);
            end+= len - avail;

        } else {        // len > buf.length + avail
            // 长写入 - 刷新缓冲区并直接从源写入其余内容
            flushBuffer();

            out.realWriteChars( src, off, len );
        }
    }


    /**
     * 将字符串附加到缓冲区.
     * 
     * @param s
     * 
     * @throws IOException 将溢出数据写入输出通道失败
     */
    public void append(String s) throws IOException {
        append(s, 0, s.length());
    }

    /**
     * 将字符串附加到缓冲区.
     * 
     * @param s
     * @param off Offset
     * @param len Length
     * 
     * @throws IOException 将溢出数据写入输出通道失败
     */
    public void append(String s, int off, int len) throws IOException {
        if (s==null) {
            return;
        }

        // 会增长，达到极限
        makeSpace( len );

        // 如果没有限制: makeSpace 可以无限增长
        if( limit < 0 ) {
            // assert: makeSpace 有足够空间
            s.getChars(off, off+len, buff, end );
            end+=len;
            return;
        }

        int sOff = off;
        int sEnd = off + len;
        while (sOff < sEnd) {
            int d = min(limit - end, sEnd - sOff);
            s.getChars( sOff, sOff+d, buff, end);
            sOff += d;
            end += d;
            if (end >= limit) {
                flushBuffer();
            }
        }
    }

    // -------------------- Removing data from the buffer --------------------

    public int substract() throws IOException {
        if ((end - start) == 0) {
            if (in == null) {
                return -1;
            }
            int n = in.realReadChars();
            if (n < 0) {
                return -1;
            }
        }
        return (buff[start++]);
    }

    public int substract(char dest[], int off, int len) throws IOException {
        if ((end - start) == 0) {
            if (in == null) {
                return -1;
            }
            int n = in.realReadChars();
            if (n < 0) {
                return -1;
            }
        }

        int n = len;
        if (len > getLength()) {
            n = getLength();
        }
        System.arraycopy(buff, start, dest, off, n);
        start += n;
        return n;
    }


    public void flushBuffer() throws IOException {
        //assert out!=null
        if( out==null ) {
            throw new IOException( "Buffer overflow, no sink " + limit + " " +
                                   buff.length  );
        }
        out.realWriteChars( buff, start, end - start );
        end=start;
    }

    /**
     * 为len个字节腾出空间.
     * 如果len很小, 也分配一个预留空间. 永远不要超过限制.
     * 
     * @param count The size
     */
    public void makeSpace(int count)
    {
        char[] tmp = null;

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
            buff=new char[desiredSize];
        }

        // limit < buf.length (缓冲区已经很大了)
        // 或者已经有空间  XXX
        if( desiredSize <= buff.length) {
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
        tmp = new char[newSize];

        System.arraycopy(buff, 0, tmp, 0, end);
        buff = tmp;
        tmp = null;
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
        return new String(buff, start, end-start);
    }

    // -------------------- equals --------------------

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CharChunk) {
            return equals((CharChunk) obj);
        }
        return false;
    }

    /**
     * 将消息字节与指定的String对象进行比较.
     * 
     * @param s 要比较的字符串
     * 
     * @return <code>true</code> 如果比较成功, 否则<code>false</code>
     */
    public boolean equals(String s) {
        char[] c = buff;
        int len = end-start;
        if (c == null || len != s.length()) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (c[off++] != s.charAt(i)) {
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
     * @return <code>true</code> 如果比较成功, 否则<code>false</code>
     */
    public boolean equalsIgnoreCase(String s) {
        char[] c = buff;
        int len = end-start;
        if (c == null || len != s.length()) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower( c[off++] ) != Ascii.toLower( s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean equals(CharChunk cc) {
        return equals( cc.getChars(), cc.getOffset(), cc.getLength());
    }

    public boolean equals(char b2[], int off2, int len2) {
        char b1[]=buff;
        if( b1==null && b2==null ) {
            return true;
        }

        if (b1== null || b2==null || end-start != len2) {
            return false;
        }
        int off1 = start;
        int len=end-start;
        while ( len-- > 0) {
            if (b1[off1++] != b2[off2++]) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param s
     * 
     * @return <code>true</code>如果消息字节以指定的字符串开头.
     */
    public boolean startsWith(String s) {
        char[] c = buff;
        int len = s.length();
        if (c == null || len > end-start) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (c[off++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param s
     * @param pos 比较开始的位置
     * 
     * @return <code>true</code>如果消息字节以指定的字符串开头.
     */
    public boolean startsWithIgnoreCase(String s, int pos) {
        char[] c = buff;
        int len = s.length();
        if (c == null || len+pos > end-start) {
            return false;
        }
        int off = start+pos;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower( c[off++] ) != Ascii.toLower( s.charAt(i))) {
                return false;
            }
        }
        return true;
    }


    /**
     * @param s
     * 
     * @return <code>true</code>如果消息字节以指定的字符串结尾.
     */
    public boolean endsWith(String s) {
        char[] c = buff;
        int len = s.length();
        if (c == null || len > end-start) {
            return false;
        }
        int off = end - len;
        for (int i = 0; i < len; i++) {
            if (c[off++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
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
        int code=0;
        for (int i = start; i < start + end-start; i++) {
            code = code * 37 + buff[i];
        }
        return code;
    }

    public int indexOf(char c) {
        return indexOf( c, start);
    }

    /**
     * @param c
     * @param starting 开始位置
     * 
     * @return <code>true</code>如果消息字节以指定的字符串开头.
     */
    public int indexOf(char c, int starting) {
        int ret = indexOf( buff, start+starting, end, c );
        return (ret >= start) ? ret - start : -1;
    }

    public static int indexOf( char chars[], int off, int cend, char qq )
    {
        while( off < cend ) {
            char b=chars[off];
            if( b==qq ) {
                return off;
            }
            off++;
        }
        return -1;
    }


    public int indexOf( String src, int srcOff, int srcLen, int myOff ) {
        char first=src.charAt( srcOff );

        // 寻找第一个字符
        int srcEnd = srcOff + srcLen;

        for( int i=myOff+start; i <= (end - srcLen); i++ ) {
            if( buff[i] != first ) {
                continue;
            }
            // 找到第一个字符, 现在开始匹配
            int myPos=i+1;
            for( int srcPos=srcOff + 1; srcPos< srcEnd;) {
                if( buff[myPos++] != src.charAt( srcPos++ )) {
                    break;
                }
                if( srcPos==srcEnd )
                 {
                    return i-start; // found it
                }
            }
        }
        return -1;
    }

    // -------------------- utils
    private int min(int a, int b) {
        if (a < b) {
            return a;
        }
        return b;
    }

    // Char sequence impl

    @Override
    public char charAt(int index) {
        return buff[index + start];
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        try {
            CharChunk result = (CharChunk) this.clone();
            result.setOffset(this.start + start);
            result.setEnd(this.start + end);
            return result;
        } catch (CloneNotSupportedException e) {
            // Cannot happen
            return null;
        }
    }

    @Override
    public int length() {
        return end - start;
    }

}
