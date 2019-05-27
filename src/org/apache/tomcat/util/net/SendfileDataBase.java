package org.apache.tomcat.util.net;

public abstract class SendfileDataBase {

    /**
     * 是否在keep-alive连接上处理当前请求?
     * 这将确定一旦发送文件完成后套接字是否关闭，或者是否继续处理连接上的下一个请求或等待下一个请求到达.
     */
    public SendfileKeepAliveState keepAliveState = SendfileKeepAliveState.NONE;

    /**
     * 包含要写入套接字的数据的文件的完整路径.
     */
    public final String fileName;

    /**
     * 要写入套接字的文件中下一个字节的位置.
     * 这将初始化为起始点，然后在写入文件时更新.
     */
    public long pos;

    /**
     * 要从文件写入的剩余字节数 (从当前 {@link #pos}.
     * 这是初始化到终点 - 起始点，然后在写入文件时更新.
     */
    public long length;

    public SendfileDataBase(String filename, long pos, long length) {
        this.fileName = filename;
        this.pos = pos;
        this.length = length;
    }
}
