package org.apache.tomcat.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import javax.websocket.Extension;

/**
 * WebSocket扩展在消息上执行的转换的内部表示形式.
 */
public interface Transformation {

    /**
     * 在管道中设置下一个转换.
     * 
     * @param t 下一个转换
     */
    void setNext(Transformation t);

    /**
     * 验证此转换所需的RSV位不被另一扩展使用.
     * 在将使用中的一组位传递到下一个转换之前，预期的实现将设置它所需的任何位.
     *
     * @param i         RSV位，RS1作为MSB，RSv3作为LSB，在0至7的范围内用作int
     *
     * @return <code>true</code>如果流水线中的转换所使用的RSV位的组合不冲突; 否则<code>false</code>
     */
    boolean validateRsvBits(int i);

    /**
     * 获取描述要返回给客户端的信息的扩展名.
     *
     * @return 描述已同意此变换的参数的扩展信息
     */
    Extension getExtensionResponse();

    /**
     * 获取更多的输入数据.
     *
     * @param opCode    当前正在处理的帧的操作码
     * @param fin       这是WebSocket 消息中的最后一帧吗?
     * @param rsv       当前正在处理的帧的保留位
     * @param dest      要写入数据的缓冲区
     *
     * @return 试图从变换中读取更多数据的结果
     *
     * @throws IOException 如果从转换中读取数据时发生I/O错误
     */
    TransformationResult getMoreData(byte opCode, boolean fin, int rsv, ByteBuffer dest) throws IOException;

    /**
     * 验证此扩展的RSV和操作码组合 (假设已从WebSocket 框架中提取).
     * 在将剩余的RSV位传递到流水线中的下一个转换之前，预期实现将取消设置已验证的任何RSV位.
     *
     * @param rsv       RSV位，RS1作为MSB，RSv3作为LSB，在0至7的范围内用作int
     * @param opCode    接收到的 opCode
     *
     * @return <code>true</code>如果 RSV 有效; 否则<code>false</code>
     */
    boolean validateRsv(int rsv, byte opCode);

    /**
     * 获取提供的消息列表, 转换它们, 将转换后的列表传递给下一个转换，然后在应用了所有转换之后返回所得到的消息部分列表.
     *
     * @param messageParts  要转换的消息列表
     *
     * @return  任何后续转换应用之后的消息列表. 返回列表的大小可能大于或小于输入列表的大小
     */
    List<MessagePart> sendMessagePart(List<MessagePart> messageParts);

    /**
     * 清除转换所使用的任何资源.
     */
    void close();
}
