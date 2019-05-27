package org.apache.tomcat.jni;

/** 打开SSL BIO回调接口
 */
public interface BIOCallback {

    /**
     * 写入数据
     * 
     * @param buf 要写入的字节.
     * @return 写入的字符数.
     */
    public int write(byte [] buf);

    /**
     * 读取数据
     * 
     * @param buf 来存储读取的字节的缓冲区.
     * @return 读取的字节数.
     */
    public int read(byte [] buf);

    /**
     * 放入字符串
     * 
     * @param data 要写入的字符串
     * @return 写入的字符数
     */
    public int puts(String data);

    /**
     * 读取字符串到len或CLRLF
     * 
     * @param len 要读取的最大字符数
     * @return 读取最多len个字节的字符串
     */
    public String gets(int len);

}
