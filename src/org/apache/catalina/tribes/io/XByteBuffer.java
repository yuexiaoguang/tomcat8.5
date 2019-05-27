package org.apache.catalina.tribes.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * XByteBuffer提供了一个双重功能.
 * One, 它保存消息字节并自动继承字节缓冲区.<BR>
 * Two, 它可以编码和解码包, 这样它们就可以在socket上被定义和识别出来.
 * <br>
 * <b>非线程安全</B><BR>
 * <br>
 * Transfer package:
 * <ul>
 * <li><b>START_DATA</b>- 7 bytes - <i>FLT2002</i></li>
 * <li><b>SIZE</b>      - 4 bytes - 数据包的大小</li>
 * <li><b>DATA</b>      - 应该和之前的SIZE一样大小</li>
 * <li><b>END_DATA</b>  - 7 bytes - <i>TLF2003</i></li>
 * </ul>
 */
public class XByteBuffer {

    private static final Log log = LogFactory.getLog(XByteBuffer.class);
    protected static final StringManager sm = StringManager.getManager(XByteBuffer.class);

    /**
     * 这是一个包 header, 7 bytes (FLT2002)
     */
    private static final byte[] START_DATA = {70,76,84,50,48,48,50};

    /**
     * 这是一个包 footer, 7 bytes (TLF2003)
     */
    private static final byte[] END_DATA = {84,76,70,50,48,48,51};

    /**
     * 保存数据
     */
    protected byte[] buf = null;

    /**
     * 缓冲区中数据的当前大小
     */
    protected int bufSize = 0;

    /**
     * 用于丢弃无效包的标志.
     * 如果这个标记被设置为 true, 并调用 append(byte[],...),添加的数据将被检查,
     * 如果它不是使用<code>START_DATA</code>启动, 它会被扔掉.
     */
    protected boolean discard = true;

    /**
     * TODO use a pool of byte[] for performance
     * @param size 字节缓冲区的初始大小
     * @param discard 用于丢弃无效包的标志
     */
    public XByteBuffer(int size, boolean discard) {
        buf = new byte[size];
        this.discard = discard;
    }

    public XByteBuffer(byte[] data,boolean discard) {
        this(data,data.length+128,discard);
    }

    public XByteBuffer(byte[] data, int size,boolean discard) {
        int length = Math.max(data.length,size);
        buf = new byte[length];
        System.arraycopy(data,0,buf,0,data.length);
        bufSize = data.length;
        this.discard = discard;
    }

    public int getLength() {
        return bufSize;
    }

    public void setLength(int size) {
        if ( size > buf.length ) throw new ArrayIndexOutOfBoundsException(sm.getString("xByteBuffer.size.larger.buffer"));
        bufSize = size;
    }

    public void trim(int length) {
        if ( (bufSize - length) < 0 )
            throw new ArrayIndexOutOfBoundsException(sm.getString("xByteBuffer.unableTrim",
                    Integer.toString(bufSize), Integer.toString(length)));
        bufSize -= length;
    }

    public void reset() {
        bufSize = 0;
    }

    public byte[] getBytesDirect() {
        return this.buf;
    }

    /**
     * @return 缓冲区中的字节, 在它精确的长度
     */
    public byte[] getBytes() {
        byte[] b = new byte[bufSize];
        System.arraycopy(buf,0,b,0,bufSize);
        return b;
    }

    /**
     * 重置缓冲区
     */
    public void clear() {
        bufSize = 0;
    }

    /**
     * 追加数据到缓冲区. 如果数据格式不正确, 将返回false , 而且数据将被丢弃.
     * @param b - 要追加的字节
     * @param len - 要追加的字节数.
     * @return true 如果数据被正确地追加. 返回 false, 如果包不正确, 即丢失 header 或其它一些东西, 或数据长度为 0
     */
    public boolean append(ByteBuffer b, int len) {
        int newcount = bufSize + len;
        if (newcount > buf.length) {
            expand(newcount);
        }
        b.get(buf,bufSize,len);

        bufSize = newcount;

        if ( discard ) {
            if (bufSize > START_DATA.length && (firstIndexOf(buf, 0, START_DATA) == -1)) {
                bufSize = 0;
                log.error(sm.getString("xByteBuffer.discarded.invalidHeader"));
                return false;
            }
        }
        return true;

    }

    public boolean append(byte i) {
        int newcount = bufSize + 1;
        if (newcount > buf.length) {
            expand(newcount);
        }
        buf[bufSize] = i;
        bufSize = newcount;
        return true;
    }


    public boolean append(boolean i) {
        int newcount = bufSize + 1;
        if (newcount > buf.length) {
            expand(newcount);
        }
        XByteBuffer.toBytes(i,buf,bufSize);
        bufSize = newcount;
        return true;
    }

    public boolean append(long i) {
        int newcount = bufSize + 8;
        if (newcount > buf.length) {
            expand(newcount);
        }
        XByteBuffer.toBytes(i,buf,bufSize);
        bufSize = newcount;
        return true;
    }

    public boolean append(int i) {
        int newcount = bufSize + 4;
        if (newcount > buf.length) {
            expand(newcount);
        }
        XByteBuffer.toBytes(i,buf,bufSize);
        bufSize = newcount;
        return true;
    }

    public boolean append(byte[] b, int off, int len) {
        if ((off < 0) || (off > b.length) || (len < 0) ||
            ((off + len) > b.length) || ((off + len) < 0))  {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return false;
        }

        int newcount = bufSize + len;
        if (newcount > buf.length) {
            expand(newcount);
        }
        System.arraycopy(b, off, buf, bufSize, len);
        bufSize = newcount;

        if ( discard ) {
            if (bufSize > START_DATA.length && (firstIndexOf(buf, 0, START_DATA) == -1)) {
                bufSize = 0;
                log.error(sm.getString("xByteBuffer.discarded.invalidHeader"));
                return false;
            }
        }
        return true;
    }

    public void expand(int newcount) {
        // 不要改变分配策略
        byte newbuf[] = new byte[Math.max(buf.length << 1, newcount)];
        System.arraycopy(buf, 0, newbuf, 0, bufSize);
        buf = newbuf;
    }

    public int getCapacity() {
        return buf.length;
    }


    /**
     * 检查缓冲区内是否存在完整包的内部机制
     * @return - true 如果是完整的包, 缓冲区中存在(header,compress,size,data,footer)
     */
    public int countPackages() {
        return countPackages(false);
    }

    public int countPackages(boolean first)
    {
        int cnt = 0;
        int pos = START_DATA.length;
        int start = 0;

        while ( start < bufSize ) {
            //首先检查启动 header
            int index = XByteBuffer.firstIndexOf(buf,start,START_DATA);
            //如果 header (START_DATA) 不是第一个或缓冲区不是 14 个字节
            if ( index != start || ((bufSize-start)<14) ) break;
            //后面 4 个字节是压缩标志在计数包时不需要, 那么获取 4 个字节大小
            int size = toInt(buf, pos);
            //现在所有的缓冲区已经足够长来保存
            //START_DATA.length+4+size+END_DATA.length
            pos = start + START_DATA.length + 4 + size;
            if ( (pos + END_DATA.length) > bufSize) break;
            // 最后检查END_DATA包的 footer
            int newpos = firstIndexOf(buf, pos, END_DATA);
            //不匹配, 没有包
            if (newpos != pos) break;
            //增加包计数
            cnt++;
            //重置值
            start = pos + END_DATA.length;
            pos = start + START_DATA.length;
            //只想验证我们至少有一个包
            if ( first ) break;
        }
        return cnt;
    }

    /**
     * 检查此字节缓冲区中是否存在包.
     * @return - true 如果完整的包 (header,options,size,data,footer)已经存在于缓冲区中
     */
    public boolean doesPackageExist()  {
        return (countPackages(true)>0);
    }

    /**
     * 从包中提取消息字节.
     * 如果包不存在, 将抛出 IllegalStateException.
     * @param clearFromBuffer - 如果是 true, 包将从字节缓冲区中删除
     * @return - 返回实际消息字节(header, compress,size and footer not included).
     */
    public XByteBuffer extractDataPackage(boolean clearFromBuffer) {
        int psize = countPackages(true);
        if (psize == 0) {
            throw new java.lang.IllegalStateException(sm.getString("xByteBuffer.no.package"));
        }
        int size = toInt(buf, START_DATA.length);
        XByteBuffer xbuf = BufferPool.getBufferPool().getBuffer(size,false);
        xbuf.setLength(size);
        System.arraycopy(buf, START_DATA.length + 4, xbuf.getBytesDirect(), 0, size);
        if (clearFromBuffer) {
            int totalsize = START_DATA.length + 4 + size + END_DATA.length;
            bufSize = bufSize - totalsize;
            System.arraycopy(buf, totalsize, buf, 0, bufSize);
        }
        return xbuf;

    }

    public ChannelData extractPackage(boolean clearFromBuffer) {
        XByteBuffer xbuf = extractDataPackage(clearFromBuffer);
        ChannelData cdata = ChannelData.getDataFromPackage(xbuf);
        return cdata;
    }

    /**
     * 创建完整的数据包
     * @param cdata - 要包含在包中的消息数据
     * @return - 完整的包 (header,size,data,footer)
     */
    public static byte[] createDataPackage(ChannelData cdata) {
//        return createDataPackage(cdata.getDataPackage());
        //避免一个额外的字节数组创建
        int dlength = cdata.getDataPackageLength();
        int length = getDataPackageLength(dlength);
        byte[] data = new byte[length];
        int offset = 0;
        System.arraycopy(START_DATA, 0, data, offset, START_DATA.length);
        offset += START_DATA.length;
        toBytes(dlength,data, START_DATA.length);
        offset += 4;
        cdata.getDataPackage(data,offset);
        offset += dlength;
        System.arraycopy(END_DATA, 0, data, offset, END_DATA.length);
        offset += END_DATA.length;
        return data;
    }

    public static byte[] createDataPackage(byte[] data, int doff, int dlength, byte[] buffer, int bufoff) {
        if ( (buffer.length-bufoff) > getDataPackageLength(dlength) ) {
            throw new ArrayIndexOutOfBoundsException(sm.getString("xByteBuffer.unableCreate"));
        }
        System.arraycopy(START_DATA, 0, buffer, bufoff, START_DATA.length);
        toBytes(data.length,buffer, bufoff+START_DATA.length);
        System.arraycopy(data, doff, buffer, bufoff+START_DATA.length + 4, dlength);
        System.arraycopy(END_DATA, 0, buffer, bufoff+START_DATA.length + 4 + data.length, END_DATA.length);
        return buffer;
    }


    public static int getDataPackageLength(int datalength) {
        int length =
            START_DATA.length + //header length
            4 + //data length indicator
            datalength + //actual data length
            END_DATA.length; //footer length
        return length;

    }

    public static byte[] createDataPackage(byte[] data) {
        int length = getDataPackageLength(data.length);
        byte[] result = new byte[length];
        return createDataPackage(data,0,data.length,result,0);
    }


//    public static void fillDataPackage(byte[] data, int doff, int dlength, XByteBuffer buf) {
//        int pkglen = getDataPackageLength(dlength);
//        if ( buf.getCapacity() <  pkglen ) buf.expand(pkglen);
//        createDataPackage(data,doff,dlength,buf.getBytesDirect(),buf.getLength());
//    }

    /**
     * 将四个字节转换为int
     * @param b - 包含四个字节的字节数组
     * @param off - 偏移量
     * @return the integer value constructed from the four bytes
     */
    public static int toInt(byte[] b,int off){
        return ( ( b[off+3]) & 0xFF) +
            ( ( ( b[off+2]) & 0xFF) << 8) +
            ( ( ( b[off+1]) & 0xFF) << 16) +
            ( ( ( b[off+0]) & 0xFF) << 24);
    }

    /**
     * 将八个字节转换为 long
     * @param b - 包含八个字节的字节数组
     * @param off - 偏移量
     * @return the long value constructed from the eight bytes
     */
    public static long toLong(byte[] b,int off){
        return ( ( (long) b[off+7]) & 0xFF) +
            ( ( ( (long) b[off+6]) & 0xFF) << 8) +
            ( ( ( (long) b[off+5]) & 0xFF) << 16) +
            ( ( ( (long) b[off+4]) & 0xFF) << 24) +
            ( ( ( (long) b[off+3]) & 0xFF) << 32) +
            ( ( ( (long) b[off+2]) & 0xFF) << 40) +
            ( ( ( (long) b[off+1]) & 0xFF) << 48) +
            ( ( ( (long) b[off+0]) & 0xFF) << 56);
    }


    /**
     * 转换一个 boolean 并将其放入字节数组.
     * @param bool the integer
     * @param data 将放置boolean值的字节缓冲区
     * @param offset 字节数组的偏移量
     * @return the byte array
     */
    public static byte[] toBytes(boolean bool, byte[] data, int offset) {
        data[offset] = (byte)(bool?1:0);
        return data;
    }

    /**
     * 将字节数组项转换为boolean.
     * @param b 字节数组
     * @param offset within byte array
     * @return true 如果字节数组非零, 否则false
     */
    public static boolean toBoolean(byte[] b, int offset) {
        return b[offset] != 0;
    }


    /**
     * 转换一个 integer 为四个字节.
     * @param n the integer
     * @param b 将放置整数的字节缓冲区
     * @param offset 字节数组中的偏移量
     * @return 四个字节的数组
     */
    public static byte[] toBytes(int n, byte[] b, int offset) {
        b[offset+3] = (byte) (n);
        n >>>= 8;
        b[offset+2] = (byte) (n);
        n >>>= 8;
        b[offset+1] = (byte) (n);
        n >>>= 8;
        b[offset+0] = (byte) (n);
        return b;
    }

    /**
     * 转换一个 long 为八个字节.
     * @param n the long
     * @param b 将放置long的字节缓冲区
     * @param offset 字节数组中的偏移量
     * @return 八个字节的数组
     */
    public static byte[] toBytes(long n, byte[] b, int offset) {
        b[offset+7] = (byte) (n);
        n >>>= 8;
        b[offset+6] = (byte) (n);
        n >>>= 8;
        b[offset+5] = (byte) (n);
        n >>>= 8;
        b[offset+4] = (byte) (n);
        n >>>= 8;
        b[offset+3] = (byte) (n);
        n >>>= 8;
        b[offset+2] = (byte) (n);
        n >>>= 8;
        b[offset+1] = (byte) (n);
        n >>>= 8;
        b[offset+0] = (byte) (n);
        return b;
    }

    /**
     * 类似于 String.IndexOf, 但使用纯字节.
     * @param src - 要搜索的源字节
     * @param srcOff - 源缓冲区上的偏移量
     * @param find - 在src内找到的字符串
     * @return - 第一个匹配字节的索引. -1 如果未找到 find 数组
     */
    public static int firstIndexOf(byte[] src, int srcOff, byte[] find){
        int result = -1;
        if (find.length > src.length) return result;
        if (find.length == 0 || src.length == 0) return result;
        if (srcOff >= src.length ) throw new java.lang.ArrayIndexOutOfBoundsException();
        boolean found = false;
        int srclen = src.length;
        int findlen = find.length;
        byte first = find[0];
        int pos = srcOff;
        while (!found) {
            //find the first byte
            while (pos < srclen){
                if (first == src[pos])
                    break;
                pos++;
            }
            if (pos >= srclen)
                return -1;

            //we found the first character
            //match the rest of the bytes - they have to match
            if ( (srclen - pos) < findlen)
                return -1;
            //assume it does exist
            found = true;
            for (int i = 1; ( (i < findlen) && found); i++) {
                found = (find[i] == src[pos + i]);
            }
            if (found) {
                result = pos;
            } else if ( (srclen - pos) < findlen) {
                return -1; //no more matches possible
            } else {
                pos++;
            }
        }
        return result;
    }


    public static Serializable deserialize(byte[] data)
        throws IOException, ClassNotFoundException, ClassCastException {
        return deserialize(data,0,data.length);
    }

    public static Serializable deserialize(byte[] data, int offset, int length)
        throws IOException, ClassNotFoundException, ClassCastException {
        return deserialize(data,offset,length,null);
    }

    private static final AtomicInteger invokecount = new AtomicInteger(0);

    public static Serializable deserialize(byte[] data, int offset, int length, ClassLoader[] cls)
        throws IOException, ClassNotFoundException, ClassCastException {
        invokecount.addAndGet(1);
        Object message = null;
        if ( cls == null ) cls = new ClassLoader[0];
        if (data != null && length > 0) {
            InputStream  instream = new ByteArrayInputStream(data,offset,length);
            ObjectInputStream stream = null;
            stream = (cls.length>0)? new ReplicationStream(instream,cls):new ObjectInputStream(instream);
            message = stream.readObject();
            instream.close();
            stream.close();
        }
        if ( message == null ) {
            return null;
        } else if (message instanceof Serializable)
            return (Serializable) message;
        else {
            throw new ClassCastException(sm.getString("xByteBuffer.wrong.class", message.getClass().getName()));
        }
    }

    /**
     * 将消息序列化为集群数据
     * @param msg ClusterMessage
     * @return 序列化的内容
     * @throws IOException Serialization error
     */
    public static byte[] serialize(Serializable msg) throws IOException {
        ByteArrayOutputStream outs = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(outs);
        out.writeObject(msg);
        out.flush();
        byte[] data = outs.toByteArray();
        return data;
    }

    public void setDiscard(boolean discard) {
        this.discard = discard;
    }

    public boolean getDiscard() {
        return discard;
    }

}
