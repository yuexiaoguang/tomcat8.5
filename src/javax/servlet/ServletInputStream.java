package javax.servlet;

import java.io.IOException;
import java.io.InputStream;

/**
 * 提供从客户端请求读取的二进制数据的输入流, 其中一个有效的<code>readLine</code>一次读取一行数据的方法.
 * 一些协议中, 例如HTTP POST 和 PUT, 一个<code>ServletInputStream</code>对象可用于读取从客户端发送的数据.
 * <p>
 * 一个<code>ServletInputStream</code>对象通常是通过{@link ServletRequest#getInputStream}方法获取的.
 * <p>
 * 这是servlet容器实现的抽象类. 这个类的子类必须实现<code>java.io.InputStream.read()</code>方法.
 */
public abstract class ServletInputStream extends InputStream {

    protected ServletInputStream() {
    }

    /**
     * 读取输入流，每次一行. 从偏移开始, 读入字节数组, 直到它读取一定数量的字节或达到一个换行符, 它也读入数组.
     * <p>
     * 这个方法返回-1， 如果在读取最大字节数之前到达输入流的结尾.
     *
     * @param b 读取数据的字节数组
     * @param off 指定该方法开始读取的字符的整数
     * @param len 指定要读取的最大字节数的整数
     * @return 指定读取的实际字节数的整数, 或者-1 如果到达流的结尾
     * @exception IOException 如果出现了输入或输出异常
     */
    public int readLine(byte[] b, int off, int len) throws IOException {

        if (len <= 0) {
            return 0;
        }
        int count = 0, c;

        while ((c = read()) != -1) {
            b[off++] = (byte) c;
            count++;
            if (c == '\n' || count == len) {
                break;
            }
        }
        return count > 0 ? count : -1;
    }

    /**
     * 这个InputStream是否到达结尾?
     *
     * @return <code>true</code>如果所有数据都从流中读取, 否则<code>false</code>
     */
    public abstract boolean isFinished();

    /**
     * 数据是否会从这无阻塞读取InputStream?
     * 如果调用此方法并返回false，则返回, 容器将调用{@link ReadListener#onDataAvailable()}，当数据可用的时候.
     *
     * @return <code>true</code>如果可以不阻塞地读取数据, 否则<code>false</code>
     */
    public abstract boolean isReady();

    /**
     * 设置这个{@link ServletInputStream}的{@link ReadListener}，从而切换到非阻塞IO。这是唯一有效的切换到非阻塞IO异步处理或HTTP升级处理.
     *
     * @param listener  非阻塞IO读取监听器
     *
     * @throws IllegalStateException    如果调用此方法，既不是异步也不是HTTP升级处理，或者如果{@link ReadListener}已经被设置
     * @throws NullPointerException     如果监听器是null
     */
    public abstract void setReadListener(ReadListener listener);
}
