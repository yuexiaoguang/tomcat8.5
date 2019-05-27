package org.apache.coyote.http11;

import java.io.IOException;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * 输入过滤器接口.
 */
public interface InputFilter extends InputBuffer {

    /**
     * 一些过滤器需要来自请求的额外的参数.
     *
     * @param request 与此过滤器关联的请求
     */
    public void setRequest(Request request);


    /**
     * 准备好处理下一个请求.
     */
    public void recycle();


    /**
     * 获取由该过滤器处理的编码的名称.
     *
     * @return 将编码名称作为字节块，以便于与HTTP header读取的值进行比较，该HTTP header也将是ByteChunk
     */
    public ByteChunk getEncodingName();


    /**
     * 在过滤器管道中设置下一个缓冲区.
     *
     * @param buffer 下一个缓冲区
     */
    public void setBuffer(InputBuffer buffer);


    /**
     * 结束当前请求.
     *
     * @return 应该返回 0. 正值指示读取的字节太多. 此方法允许使用buffer.doRead来消耗额外的字节.
     * 		结果不能是负的 (如果发生错误, 应该抛出 IOException).
     *
     * @throws IOException 如果发生错误
     */
    public long end() throws IOException;


    /**
     * 缓冲区中仍然可用的字节数量.
     *
     * @return 缓冲区中的字节数量
     */
    public int available();


    /**
     * 请求主体已经被完全读取?
     *
     * @return {@code true} 如果请求主体已被完全读取, 否则{@code false}
     */
    public boolean isFinished();
}
