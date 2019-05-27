package org.apache.coyote.http11;

import java.io.IOException;

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;

/**
 * 输出过滤器.
 */
public interface OutputFilter extends OutputBuffer {


    /**
     * 一些过滤器需要来自响应的额外的参数. 所有必要的读取都可以在该方法中进行, 由于响应header处理完成后调用此方法.
     *
     * @param response 这个OutputFilter管理的响应
     */
    public void setResponse(Response response);


    /**
     * 准备好处理下一个请求.
     */
    public void recycle();


    /**
     * 在过滤器管道中设置下一个缓冲区.
     *
     * @param buffer 下一个缓冲区实例
     */
    public void setBuffer(OutputBuffer buffer);


    /**
     * 结束当前请求. 使用缓冲区.doWrite写入额外的字节是可以接受的, 在该方法的执行过程中.
     *
     * @return 应该返回 0, 除非过滤器做一些内容长度界定, 在这种情况下是额外的字节或丢失的字节的数量, 表示一个错误.
     * Note: 建议额外的字节被过滤器忽略.
     *
     * @throws IOException 如果在写入客户端时发生I/O错误
     */
    public long end() throws IOException;
}
