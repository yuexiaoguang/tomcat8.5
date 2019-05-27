package org.apache.tomcat.util.http.fileupload;

/**
 * {@link ProgressListener}可用于显示进度条或执行类似的操作.
 */
public interface ProgressListener {

    /**
     * 更新监听器状态信息.
     *
     * @param pBytesRead 到目前为止已读取的总字节数.
     * @param pContentLength 正在读取的总字节数. 可能是 -1, 如果数字未知.
     * @param pItems 当前正在读取的字段数. (0 = 到目前为止没有项目, 1 = 第一项正在读取中, ...)
     */
    void update(long pBytesRead, long pContentLength, int pItems);

}
