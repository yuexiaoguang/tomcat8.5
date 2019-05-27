package org.apache.coyote.ajp;

import java.nio.ByteBuffer;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.res.StringManager;

/**
 * Web服务器与容器之间通信的单个数据包. 设计多次重复使用，不产生垃圾. 了解这些数据包的数据类型格式.
 * 对于传入和传出的数据包都可以使用（有点令人困惑）.
 */
public class AjpMessage {


    private static final Log log = LogFactory.getLog(AjpMessage.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(AjpMessage.class);


    // ------------------------------------------------------------ Constructor


    public AjpMessage(int packetSize) {
        buf = new byte[packetSize];
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 固定大小的缓冲区.
     */
    protected final byte buf[];


    /**
     * 缓冲区中当前的读写位置.
     */
    protected int pos;


    /**
     * 这实际上意味着不同的事情，这取决于数据包是读还是写.
     * 对于读, 这是有效载荷的长度(不包括 header).
     * 对于写, 这是整个包的长度 (包括 header).
     */
    protected int len;


    // --------------------------------------------------------- Public Methods


    /**
     * 准备这个包，以便从容器向Web服务器累积消息. 设置写入位置在 header 之后 (但留下未写入的长度, 因为它还不知道).
     */
    public void reset() {
        len = 4;
        pos = 4;
    }


    /**
     * 对于要发送到Web服务器的数据包, 完成数据累加的过程，并将数据有效载荷的长度写入报头.
     */
    public void end() {
        len = pos;
        int dLen = len - 4;

        buf[0] = (byte) 0x41;
        buf[1] = (byte) 0x42;
        buf[2] = (byte) ((dLen>>>8) & 0xFF);
        buf[3] = (byte) (dLen & 0xFF);
    }


    /**
     * 返回底层字节缓冲区.
     *
     * @return The buffer
     */
    public byte[] getBuffer() {
        return buf;
    }


    /**
     * 返回当前消息长度.
     *
     * @return 对于读, 这是有效载荷的长度 (不包括 header).
     * 对于写, 这是整个包的长度 (包括 header).
     */
    public int getLen() {
        return len;
    }


    /**
     * 添加短整数(2 bytes) 到消息.
     *
     * @param val 要追加的整数
     */
    public void appendInt(int val) {
        buf[pos++] = (byte) ((val >>> 8) & 0xFF);
        buf[pos++] = (byte) (val & 0xFF);
    }


    /**
     * 追加一个字节 (1 byte) 到消息.
     *
     * @param val 要追加的字节值
     */
    public void appendByte(int val) {
        buf[pos++] = (byte) val;
    }


    /**
     * 写入一个 MessageBytes 到当前写入位置. 一个 null MessageBytes 被编码为一个字符串, 长度为 0.
     *
     * @param mb 要写入的数据
     */
    public void appendBytes(MessageBytes mb) {
        if (mb == null) {
            log.error(sm.getString("ajpmessage.null"),
                    new NullPointerException());
            appendInt(0);
            appendByte(0);
            return;
        }
        if (mb.getType() != MessageBytes.T_BYTES) {
            mb.toBytes();
            ByteChunk bc = mb.getByteChunk();
            // 需要过滤掉不包括 TAB 的CTL. ISO-8859-1 和 UTF-8值是可以的. 使用其他编码的字符串可能会损坏.
            byte[] buffer = bc.getBuffer();
            for (int i = bc.getOffset(); i < bc.getLength(); i++) {
                // byte 值被标记 i.e. -128 到 127
                // 使用未签名的值. 0 到 31 是 CTL, 因此被过滤(TAB 位置是 9). 127 是一个控件 (DEL).
                // 128 到 255的值都是可以的. 转换这些到给定的签名的 -128 到 -1.
                if ((buffer[i] > -1 && buffer[i] <= 31 && buffer[i] != 9) ||
                        buffer[i] == 127) {
                    buffer[i] = ' ';
                }
            }
        }
        appendByteChunk(mb.getByteChunk());
    }


    /**
     * 写入一个 ByteChunk 到当前写入位置. 一个 null ByteChunk 被编码为0长度的字符串.
     *
     * @param bc 要写入的数据
     */
    public void appendByteChunk(ByteChunk bc) {
        if (bc == null) {
            log.error(sm.getString("ajpmessage.null"),
                    new NullPointerException());
            appendInt(0);
            appendByte(0);
            return;
        }
        appendBytes(bc.getBytes(), bc.getStart(), bc.getLength());
    }


    /**
     * 将一块字节复制到数据包中, 从当前写入位置开始. 字节块用两个字节的长度进行编码, 然后是数据本身, 结尾是 \0 (结尾字节不包括在编码的长度).
     *
     * @param b 从中复制字节的数组.
     * @param off 开始复制的数组中的偏移量
     * @param numBytes 要复制的字节数.
     */
    public void appendBytes(byte[] b, int off, int numBytes) {
        if (checkOverflow(numBytes)) {
            return;
        }
        appendInt(numBytes);
        System.arraycopy(b, off, buf, pos, numBytes);
        pos += numBytes;
        appendByte(0);
    }


    /**
     * 将一大块字节复制到数据包中, 从当前写入位置开始. 字节块用两个字节的长度进行编码, 然后是数据本身, 结尾是 \0 (结尾字节不包括在编码的长度).
     *
     * @param b 包含要复制的字节的ByteBuffer.
     */
    public void appendBytes(ByteBuffer b) {
        int numBytes = b.remaining();
        if (checkOverflow(numBytes)) {
            return;
        }
        appendInt(numBytes);
        b.get(buf, pos, numBytes);
        pos += numBytes;
        appendByte(0);
    }


    private boolean checkOverflow(int numBytes) {
        if (pos + numBytes + 3 > buf.length) {
            log.error(sm.getString("ajpmessage.overflow", "" + numBytes, "" + pos),
                    new ArrayIndexOutOfBoundsException());
            if (log.isDebugEnabled()) {
                dump("Overflow/coBytes");
            }
            return true;
        }
        return false;
    }


    /**
     * 从包中读取整数, 并将读取位置提前.
     *
     * @return 从消息中读取的整数值
     */
    public int getInt() {
        int b1 = buf[pos++] & 0xFF;
        int b2 = buf[pos++] & 0xFF;
        validatePos(pos);
        return (b1<<8) + b2;
    }


    public int peekInt() {
        validatePos(pos + 2);
        int b1 = buf[pos] & 0xFF;
        int b2 = buf[pos+1] & 0xFF;
        return (b1<<8) + b2;
    }


    public byte getByte() {
        byte res = buf[pos++];
        validatePos(pos);
        return res;
    }


    public void getBytes(MessageBytes mb) {
        doGetBytes(mb, true);
    }

    public void getBodyBytes(MessageBytes mb) {
        doGetBytes(mb, false);
    }

    private void doGetBytes(MessageBytes mb, boolean terminated) {
        int length = getInt();
        if ((length == 0xFFFF) || (length == -1)) {
            mb.recycle();
            return;
        }
        if (terminated) {
            validatePos(pos + length + 1);
        } else {
            validatePos(pos + length);
        }
        mb.setBytes(buf, pos, length);
        mb.getCharChunk().recycle(); // not valid anymore
        pos += length;
        if (terminated) {
            pos++; // 跳过结尾的 \0
        }
    }


    /**
     * 从包中读取32位整数, 并将读取位置提前.
     *
     * @return 从消息中读取的 long 值
     */
    public int getLongInt() {
        int b1 = buf[pos++] & 0xFF; // No swap, Java order
        b1 <<= 8;
        b1 |= (buf[pos++] & 0xFF);
        b1 <<= 8;
        b1 |= (buf[pos++] & 0xFF);
        b1 <<=8;
        b1 |= (buf[pos++] & 0xFF);
        validatePos(pos);
        return  b1;
    }


    public int processHeader(boolean toContainer) {
        pos = 0;
        int mark = getInt();
        len = getInt();
        // 验证消息签名
        if ((toContainer && mark != 0x1234) ||
                (!toContainer && mark != 0x4142)) {
            log.error(sm.getString("ajpmessage.invalid", "" + mark));
            if (log.isDebugEnabled()) {
                dump("In");
            }
            return -1;
        }
        if (log.isDebugEnabled())  {
            log.debug("Received " + len + " " + buf[0]);
        }
        return len;
    }


    private void dump(String prefix) {
        if (log.isDebugEnabled()) {
            log.debug(prefix + ": " + HexUtils.toHexString(buf) + " " + pos +"/" + (len + 4));
        }
        int max = pos;
        if (len + 4 > pos)
            max = len+4;
        if (max > 1000)
            max = 1000;
        if (log.isDebugEnabled()) {
            for (int j = 0; j < max; j += 16) {
                log.debug(hexLine(buf, j, len));
            }
        }
    }


    private void validatePos(int posToTest) {
        if (posToTest > len + 4) {
            // 尝试读取超出AJP消息结尾的数据
            throw new ArrayIndexOutOfBoundsException(sm.getString(
                    "ajpMessage.invalidPos", Integer.valueOf(posToTest)));
        }
    }
    // ------------------------------------------------------ Protected Methods


    protected static String hexLine(byte buf[], int start, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + 16 ; i++) {
            if (i < len + 4) {
                sb.append(hex(buf[i]) + " ");
            } else {
                sb.append("   ");
            }
        }
        sb.append(" | ");
        for (int i = start; i < start + 16 && i < len + 4; i++) {
            if (!Character.isISOControl((char) buf[i])) {
                sb.append(Character.valueOf((char) buf[i]));
            } else {
                sb.append(".");
            }
        }
        return sb.toString();
    }


    protected static String hex(int x) {
        String h = Integer.toHexString(x);
        if (h.length() == 1) {
            h = "0" + h;
        }
        return h.substring(h.length() - 2);
    }


}
