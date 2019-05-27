package javax.servlet;

import java.io.CharConversionException;
import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * 提供向客户端发送二进制数据的输出流.
 * <code>ServletOutputStream</code>对象通常通过{@link ServletResponse#getOutputStream}方法获取.
 * <p>
 * 这是servlet容器实现的抽象类. 这个类的子类必须实现<code>java.io.OutputStream.write(int)</code>方法.
 */
public abstract class ServletOutputStream extends OutputStream {

    private static final String LSTRING_FILE = "javax.servlet.LocalStrings";
    private static final ResourceBundle lStrings = ResourceBundle.getBundle(LSTRING_FILE);

    protected ServletOutputStream() {
        // NOOP
    }

    /**
     * 写入一个<code>String</code>到客户端, 没有一个回车换行（回车换行）在结束字符.
     *
     * @param s 发送到客户端的<code>String</code>
     * @exception IOException 如果出现输入或输出异常
     */
    public void print(String s) throws IOException {
        if (s == null)
            s = "null";
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);

            // XXX NOTE: 对于许多字符串，这显然是不正确的, 但是在当前servlet框架中是唯一一致的方法. 它必须足够，直到servlet输出流正确地编码它们的输出为止.
            if ((c & 0xff00) != 0) { // 高阶字节必须为零
                String errMsg = lStrings.getString("err.not_iso8859_1");
                Object[] errArgs = new Object[1];
                errArgs[0] = Character.valueOf(c);
                errMsg = MessageFormat.format(errMsg, errArgs);
                throw new CharConversionException(errMsg);
            }
            write(c);
        }
    }

    /**
     * 写入一个<code>boolean</code>值到客户端, 没有一个回车换行（回车换行）在结束字符.
     *
     * @param b 发送到客户端的<code>boolean</code>值
     * @exception IOException 如果出现输入或输出异常
     */
    public void print(boolean b) throws IOException {
        String msg;
        if (b) {
            msg = lStrings.getString("value.true");
        } else {
            msg = lStrings.getString("value.false");
        }
        print(msg);
    }

    /**
     * 写入一个字符到客户端, 没有一个回车换行（回车换行）在结束字符.
     *
     * @param c 发送到客户端的字符
     * @exception IOException 如果出现输入或输出异常
     */
    public void print(char c) throws IOException {
        print(String.valueOf(c));
    }

    /**
     * 写入一个int到客户端, 没有一个回车换行（回车换行）在结束字符.
     *
     * @param i 发送到客户端的int
     * @exception IOException 如果出现输入或输出异常
     */
    public void print(int i) throws IOException {
        print(String.valueOf(i));
    }

    /**
     * 写入一个<code>long</code>到客户端, 没有一个回车换行（回车换行）在结束字符.
     *
     * @param l 发送到客户端的<code>long</code>
     * @exception IOException 如果出现输入或输出异常
     */
    public void print(long l) throws IOException {
        print(String.valueOf(l));
    }

    /**
     * 写入一个<code>float</code>到客户端, 没有一个回车换行（回车换行）在结束字符.
     *
     * @param f 发送到客户端的<code>float</code>
     * @exception IOException 如果出现输入或输出异常
     */
    public void print(float f) throws IOException {
        print(String.valueOf(f));
    }

    /**
     * 写入一个<code>double</code>到客户端, 没有一个回车换行（回车换行）在结束字符.
     *
     * @param d 发送到客户端的<code>double</code>
     * @exception IOException 如果出现输入或输出异常
     */
    public void print(double d) throws IOException {
        print(String.valueOf(d));
    }

    /**
     * 写入一个回车换行（回车换行）到客户端.
     *
     * @exception IOException 如果出现输入或输出异常
     */
    public void println() throws IOException {
        print("\r\n");
    }

    /**
     * 写入一个<code>String</code>到客户端, 没有一个回车换行在结束字符.
     *
     * @param s 发送到客户端的<code>String</code>
     * @exception IOException 如果出现输入或输出异常
     */
    public void println(String s) throws IOException {
        print(s);
        println();
    }

    /**
     * 写入一个<code>boolean</code>值到客户端, 然后是一个回车换行.
     *
     * @param b 写入到客户端的<code>boolean</code>值
     * @exception IOException 如果出现输入或输出异常
     */
    public void println(boolean b) throws IOException {
        print(b);
        println();
    }

    /**
     * 写入一个字符到客户端, 然后是一个回车换行(CRLF).
     *
     * @param c 写入到客户端的字符
     * @exception IOException 如果出现输入或输出异常
     */
    public void println(char c) throws IOException {
        print(c);
        println();
    }

    /**
     * 写入一个int到客户端, 然后是一个回车换行(CRLF).
     *
     * @param i 写入到客户端的int
     * @exception IOException 如果出现输入或输出异常
     */
    public void println(int i) throws IOException {
        print(i);
        println();
    }

    /**
     * 写入一个<code>long</code>到客户端, 然后是一个回车换行(CRLF).
     *
     * @param l 写入到客户端的<code>long</code>
     * @exception IOException 如果出现输入或输出异常
     */
    public void println(long l) throws IOException {
        print(l);
        println();
    }

    /**
     * 写入一个<code>float</code>到客户端, 然后是一个回车换行(CRLF).
     *
     * @param f 写入到客户端的<code>float</code>
     * @exception IOException 如果出现输入或输出异常
     */
    public void println(float f) throws IOException {
        print(f);
        println();
    }

    /**
     * 写入一个<code>double</code>到客户端, 然后是一个回车换行(CRLF).
     *
     * @param d 写入到客户端的<code>double</code>
     * @exception IOException 如果出现输入或输出异常
     */
    public void println(double d) throws IOException {
        print(d);
        println();
    }

    /**
     * 检查非阻塞写入是否成功.
     * 如果返回<code>false</code>, 它将回调{@link WriteListener#onWritePossible()}，当缓冲区变空了.
     * 如果返回<code>false</code>，没有进一步的数据必须写，直到调用{@link WriteListener#onWritePossible()}.
     *
     * @return <code>true</code>如果可以写入数据, 否则<code>false</code>
     */
    public abstract boolean isReady();

    /**
     * 设置这个{@link ServletOutputStream}的{@link WriteListener}，从而切换到非阻塞IO.
     * 这是唯一有效的切换到非阻塞IO异步处理或HTTP升级处理.
     *
     * @param listener  非阻塞IO写入监听器
     *
     * @throws IllegalStateException    如果没有异步或HTTP升级正在进行，就调用这个方法；或者{@link WriteListener}已经被设置
     * @throws NullPointerException     如果监听器是null
     */
    public abstract void setWriteListener(javax.servlet.WriteListener listener);
}
