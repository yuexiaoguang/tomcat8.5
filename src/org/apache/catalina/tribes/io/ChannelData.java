package org.apache.catalina.tribes.io;

import java.sql.Timestamp;
import java.util.Arrays;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.util.UUIDGenerator;

/**
 * 用于通过channel拦截器堆栈传递消息并最终输出到另一节点. 当消息由不同的拦截器处理时, 当每个拦截器看起来合适时, 可以对消息数据进行操作.
 */
public class ChannelData implements ChannelMessage {
    private static final long serialVersionUID = 1L;

    public static final ChannelData[] EMPTY_DATA_ARRAY = new ChannelData[0];

    public static volatile boolean USE_SECURE_RANDOM_FOR_UUID = false;

    /**
     * 发送此消息的选项
     */
    private int options = 0 ;
    /**
     * 消息数据, 存储在动态缓冲区中
     */
    private XByteBuffer message ;
    /**
     * 与此消息一起的时间戳
     */
    private long timestamp ;
    /**
     * 唯一的消息ID
     */
    private byte[] uniqueId ;
    
    /**
     * 此消息的源或答复地址
     */
    private Member address;

    public ChannelData() {
        this(true);
    }

    /**
     * @param generateUUID boolean - 如果是 true, 将生成一个惟一的 Id
     */
    public ChannelData(boolean generateUUID) {
        if ( generateUUID ) generateUUID();
    }


    /**
     * @param uniqueId - 消息惟一的 id
     * @param message - 消息数据
     * @param timestamp - 消息时间戳
     */
    public ChannelData(byte[] uniqueId, XByteBuffer message, long timestamp) {
        this.uniqueId = uniqueId;
        this.message = message;
        this.timestamp = timestamp;
    }

    /**
     * @return 消息字节缓冲器
     */
    @Override
    public XByteBuffer getMessage() {
        return message;
    }
    /**
     * @param message 要发送的消息.
     */
    @Override
    public void setMessage(XByteBuffer message) {
        this.message = message;
    }
    /**
     * @return 时间戳.
     */
    @Override
    public long getTimestamp() {
        return timestamp;
    }
    /**
     * @param timestamp 要发送的时间戳
     */
    @Override
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    /**
     * @return 唯一的消息ID.
     */
    @Override
    public byte[] getUniqueId() {
        return uniqueId;
    }
    /**
     * @param uniqueId 要发送的uniqueId.
     */
    public void setUniqueId(byte[] uniqueId) {
        this.uniqueId = uniqueId;
    }
    /**
     * @return 消息选项
     */
    @Override
    public int getOptions() {
        return options;
    }
    /**
     * 设置消息选项.
     *
     * @param options 消息选项
     */
    @Override
    public void setOptions(int options) {
        this.options = options;
    }

    /**
     * 返回源或答复地址
     * @return Member
     */
    @Override
    public Member getAddress() {
        return address;
    }

    /**
     * 设置源或答复地址
     * @param address Member
     */
    @Override
    public void setAddress(Member address) {
        this.address = address;
    }

    /**
     * 生成一个 UUID并调用 setUniqueId
     */
    public void generateUUID() {
        byte[] data = new byte[16];
        UUIDGenerator.randomUUID(USE_SECURE_RANDOM_FOR_UUID,data,0);
        setUniqueId(data);
    }

    public int getDataPackageLength() {
        int length =
            4 + //options
            8 + //timestamp  off=4
            4 + //unique id length off=12
            uniqueId.length+ //id data off=12+uniqueId.length
            4 + //addr length off=12+uniqueId.length+4
            address.getDataLength()+ //member data off=12+uniqueId.length+4+add.length
            4 + //message length off=12+uniqueId.length+4+add.length+4
            message.getLength();
        return length;

    }

    /**
     * 序列化 ChannelData 对象为一个 byte[] 数组
     * @return byte[]
     */
    public byte[] getDataPackage()  {
        int length = getDataPackageLength();
        byte[] data = new byte[length];
        int offset = 0;
        return getDataPackage(data,offset);
    }

    public byte[] getDataPackage(byte[] data, int offset)  {
        byte[] addr = address.getData(false);
        XByteBuffer.toBytes(options,data,offset);
        offset += 4; //options
        XByteBuffer.toBytes(timestamp,data,offset);
        offset += 8; //timestamp
        XByteBuffer.toBytes(uniqueId.length,data,offset);
        offset += 4; //uniqueId.length
        System.arraycopy(uniqueId,0,data,offset,uniqueId.length);
        offset += uniqueId.length; //uniqueId data
        XByteBuffer.toBytes(addr.length,data,offset);
        offset += 4; //addr.length
        System.arraycopy(addr,0,data,offset,addr.length);
        offset += addr.length; //addr data
        XByteBuffer.toBytes(message.getLength(),data,offset);
        offset += 4; //message.length
        System.arraycopy(message.getBytesDirect(),0,data,offset,message.getLength());
        offset += message.getLength(); //message data
        return data;
    }

    /**
     * 从byte数组反序列化一个 ChannelData 对象
     * @param xbuf byte[]
     * @return ChannelData
     */
    public static ChannelData getDataFromPackage(XByteBuffer xbuf)  {
        ChannelData data = new ChannelData(false);
        int offset = 0;
        data.setOptions(XByteBuffer.toInt(xbuf.getBytesDirect(),offset));
        offset += 4; //options
        data.setTimestamp(XByteBuffer.toLong(xbuf.getBytesDirect(),offset));
        offset += 8; //timestamp
        data.uniqueId = new byte[XByteBuffer.toInt(xbuf.getBytesDirect(),offset)];
        offset += 4; //uniqueId length
        System.arraycopy(xbuf.getBytesDirect(),offset,data.uniqueId,0,data.uniqueId.length);
        offset += data.uniqueId.length; //uniqueId data
        //byte[] addr = new byte[XByteBuffer.toInt(xbuf.getBytesDirect(),offset)];
        int addrlen = XByteBuffer.toInt(xbuf.getBytesDirect(),offset);
        offset += 4; //addr length
        //System.arraycopy(xbuf.getBytesDirect(),offset,addr,0,addr.length);
        data.setAddress(MemberImpl.getMember(xbuf.getBytesDirect(),offset,addrlen));
        //offset += addr.length; //addr data
        offset += addrlen;
        int xsize = XByteBuffer.toInt(xbuf.getBytesDirect(),offset);
        offset += 4; //xsize length
        System.arraycopy(xbuf.getBytesDirect(),offset,xbuf.getBytesDirect(),0,xsize);
        xbuf.setLength(xsize);
        data.message = xbuf;
        return data;

    }

    public static ChannelData getDataFromPackage(byte[] b)  {
        ChannelData data = new ChannelData(false);
        int offset = 0;
        data.setOptions(XByteBuffer.toInt(b,offset));
        offset += 4; //options
        data.setTimestamp(XByteBuffer.toLong(b,offset));
        offset += 8; //timestamp
        data.uniqueId = new byte[XByteBuffer.toInt(b,offset)];
        offset += 4; //uniqueId length
        System.arraycopy(b,offset,data.uniqueId,0,data.uniqueId.length);
        offset += data.uniqueId.length; //uniqueId data
        byte[] addr = new byte[XByteBuffer.toInt(b,offset)];
        offset += 4; //addr length
        System.arraycopy(b,offset,addr,0,addr.length);
        data.setAddress(MemberImpl.getMember(addr));
        offset += addr.length; //addr data
        int xsize = XByteBuffer.toInt(b,offset);
        //data.message = new XByteBuffer(new byte[xsize],false);
        data.message = BufferPool.getBufferPool().getBuffer(xsize,false);
        offset += 4; //message length
        System.arraycopy(b,offset,data.message.getBytesDirect(),0,xsize);
        data.message.append(b,offset,xsize);
        offset += xsize; //message data
        return data;
    }

    @Override
    public int hashCode() {
        return XByteBuffer.toInt(getUniqueId(),0);
    }

    /**
     * 比较ChannelData 对象, 只比较 getUniqueId().equals(o.getUniqueId())
     * @param o Object
     * @return boolean
     */
    @Override
    public boolean equals(Object o) {
        if ( o instanceof ChannelData ) {
            return Arrays.equals(getUniqueId(),((ChannelData)o).getUniqueId());
        } else return false;
    }

    /**
     * 创建一个浅克隆, 只重新创建数据
     * @return ClusterData
     */
    @Override
    public Object clone() {
//        byte[] d = this.getDataPackage();
//        return ClusterData.getDataFromPackage(d);
        ChannelData clone = new ChannelData(false);
        clone.options = this.options;
        clone.message = new XByteBuffer(this.message.getBytesDirect(),false);
        clone.timestamp = this.timestamp;
        clone.uniqueId = this.uniqueId;
        clone.address = this.address;
        return clone;
    }

    /**
     * 深度克隆
     * @return ClusterData
     */
    @Override
    public Object deepclone() {
        byte[] d = this.getDataPackage();
        return ChannelData.getDataFromPackage(d);
    }

    /**
     * 工具方法, 返回 true, 如果 options 标志表示在消息被接收和处理之后, 发送一个 ack
     * 
     * @param options int - 消息的选项
     * @return boolean
     */
    public static boolean sendAckSync(int options) {
        return ( (Channel.SEND_OPTIONS_USE_ACK & options) == Channel.SEND_OPTIONS_USE_ACK) &&
            ( (Channel.SEND_OPTIONS_SYNCHRONIZED_ACK & options) == Channel.SEND_OPTIONS_SYNCHRONIZED_ACK);
    }


    /**
     * 工具方法, 返回 true, 如果 options 标志表示在消息被接收之后, 但在处理之前, 发送一个 ack
     * @param options int - 消息的选项
     * @return boolean
     */
    public static boolean sendAckAsync(int options) {
        return ( (Channel.SEND_OPTIONS_USE_ACK & options) == Channel.SEND_OPTIONS_USE_ACK) &&
            ( (Channel.SEND_OPTIONS_SYNCHRONIZED_ACK & options) != Channel.SEND_OPTIONS_SYNCHRONIZED_ACK);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("ClusterData[src=");
        buf.append(getAddress()).append("; id=");
        buf.append(bToS(getUniqueId())).append("; sent=");
        buf.append(new Timestamp(this.getTimestamp()).toString()).append("]");
        return buf.toString();
    }

    public static String bToS(byte[] data) {
        StringBuilder buf = new StringBuilder(4*16);
        buf.append("{");
        for (int i=0; data!=null && i<data.length; i++ ) buf.append(String.valueOf(data[i])).append(" ");
        buf.append("}");
        return buf.toString();
    }
}
