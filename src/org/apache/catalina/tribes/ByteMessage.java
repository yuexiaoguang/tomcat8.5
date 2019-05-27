package org.apache.catalina.tribes;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * 字节消息不是由channel序列化和反序列化的，而是以byte数组的形式发送的<br>
 * 默认情况下, Tribes 使用java序列化， 当它接收到要通过导线发送的对象时.
 * Java序列化不是序列化数据最有效的方法, 而且Tribes 甚至可能无法访问正确的类加载器来适当地反序列化对象.
 * <br>
 * ByteMessage 类是一个不会序列化接收到的消息的类, 而只是流化<code>getMessage()</code>字节.<br>
 * 如果你在Tribe之上使用多个应用程序, 应该添加一些有序的 header, 因此可以使用<code>ChannelListener.accept()</code>决定是否接收消息.
 */
public class ByteMessage implements Externalizable {
    /**
     * 要发送的消息的存储
     */
    private byte[] message;


    public ByteMessage() {
    }

    /**
     * @param data byte[] - 消息内容
     */
    public ByteMessage(byte[] data) {
        message = data;
    }

    /**
     * 返回此字节消息的消息内容
     * @return byte[] - 消息内容, 可以是 null
     */
    public byte[] getMessage() {
        return message;
    }

    /**
     * 设置这个字节消息的消息内容
     * @param message byte[]
     */
    public void setMessage(byte[] message) {
        this.message = message;
    }

    /**
     * @param in ObjectInput
     * @throws IOException 发生的IO 错误
     */
    @Override
    public void readExternal(ObjectInput in ) throws IOException {
        int length = in.readInt();
        message = new byte[length];
        in.readFully(message);
    }

    /**
     * @param out ObjectOutput
     * @throws IOException 发生的IO 错误
     */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(message!=null?message.length:0);
        if ( message!=null ) out.write(message,0,message.length);
    }
}
