package org.apache.catalina.ha.deploy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * 这个工厂用于读取文件和写入文件，将它们拆分成较小的消息. 因此，整个文件不必被读取到内存中.
 * <BR>
 * 工厂可以作为reader或writer使用，但不能同时使用.
 * 当读取或写入时，工厂将关闭输入或输出流，并将工厂标记为关闭. 之后再使用它是不可能的. <BR>
 * 强制清理, 从调用对象调用 cleanup(). <BR>
 * 这个类不是线程安全的.
 */
public class FileMessageFactory {
    /*--Static Variables----------------------------------------*/
    private static final Log log = LogFactory.getLog(FileMessageFactory.class);
    private static final StringManager sm = StringManager.getManager(FileMessageFactory.class);

    /**
     * 从文件中读取的字节数
     */
    public static final int READ_SIZE = 1024 * 10; //10kb

    /**
     * 正在读/写的文件
     */
    protected final File file;

    /**
     * True 意味着正在和这个工厂一起写入. False 意味着正在和这个工厂一起读取
     */
    protected final boolean openForWrite;

    /**
     * 一旦工厂被使用, 不能再用.
     */
    protected boolean closed = false;

    /**
     * 当 openForWrite=false 时, 输入流由该变量保存
     */
    protected FileInputStream in;

    /**
     * 当 openForWrite=true时, 输出流由该变量保存
     */
    protected FileOutputStream out;

    /**
     * 写入的消息的数量
     */
    protected int nrOfMessagesProcessed = 0;

    /**
     * 文件的总大小
     */
    protected long size = 0;

    /**
     * 将文件分割成的数据包数量
     */
    protected long totalNrOfMessages = 0;

    /**
     * 处理的最后一个消息的数量. 消息ID从 1 开始.
     */
    protected AtomicLong lastMessageProcessed = new AtomicLong(0);

    /**
     * 接收到的无序信息被保存在缓冲区中.
     * 如果一切顺利, 消息将在缓冲区中花费很少的时间.
     */
    protected final Map<Long, FileMessage> msgBuffer = new ConcurrentHashMap<>();

    /**
     * 保存数据的字节, 非线程安全.
     */
    protected byte[] data = new byte[READ_SIZE];

    /**
     * 指示线程是否正在向磁盘写入消息. 访问此标志必须同步.
     */
    protected boolean isWriting = false;

    /**
     * 创建该实例的时间. (毫秒)
     */
    protected long creationTime = 0;

    /**
     * 最大有效时间(秒).
     */
    protected int maxValidTime = -1;

    /**
     * 实例化一个工厂来读或写. <BR>
     * openForWrite==true, 那么文件, f, 将创建一个输出流并写入它. <BR>
     * openForWrite==false, 打开输入流，文件必须存在.
     *
     * @param f File - 要读/写的文件
     * @param openForWrite - true 表示写入文件, false 表示读取文件
     * @throws FileNotFoundException - 如果要读取的文件不存在
     * @throws IOException - 如果系统未能打开文件的输入/输出流，或者无法创建要写入的文件.
     */
    private FileMessageFactory(File f, boolean openForWrite)
            throws FileNotFoundException, IOException {
        this.file = f;
        this.openForWrite = openForWrite;
        if (log.isDebugEnabled())
            log.debug("open file " + f + " write " + openForWrite);
        if (openForWrite) {
            if (!file.exists())
                if (!file.createNewFile()) {
                    throw new IOException(sm.getString("fileNewFail", file));
                }
            out = new FileOutputStream(f);
        } else {
            size = file.length();
            totalNrOfMessages = (size / READ_SIZE) + 1;
            in = new FileInputStream(f);
        }//end if
        creationTime = System.currentTimeMillis();
    }

    /**
     * 读取可以调用 readMessage, 写入可以调用writeMessage.
     *
     * @param f - 要被读取或写入的文件
     * @param openForWrite - true 表示正在写文件, false 表示正在读取它
     * @throws FileNotFoundException - 如果要读取的文件不存在
     * @throws IOException - 如果未能创建要写入的文件
     */
    public static FileMessageFactory getInstance(File f, boolean openForWrite)
            throws FileNotFoundException, IOException {
        return new FileMessageFactory(f, openForWrite);
    }

    /**
     * 将文件数据读入文件消息并设置大小, totalLength, totalNrOfMsg, 消息数<BR>
     * 如果到达EOF, 工厂返回 null, 并关闭它自己, 否则返回的消息和传入的相同. 这确保了不再使用更多的内存.
     * 记住, 文件消息或工厂都不是线程安全的. 不要将消息转交给一个线程，并用另一个线程读取.
     *
     * @param f - 要填充文件数据的消息
     * @throws IllegalArgumentException - 如果工厂是为了写入或关闭的
     * @throws IOException - 如果文件读取异常发生
     * 
     * @return FileMessage - 返回传入的参数的相同消息, 或 null
     */
    public FileMessage readMessage(FileMessage f) throws IllegalArgumentException, IOException {
        checkState(false);
        int length = in.read(data);
        if (length == -1) {
            cleanup();
            return null;
        } else {
            f.setData(data, length);
            f.setTotalNrOfMsgs(totalNrOfMessages);
            f.setMessageNumber(++nrOfMessagesProcessed);
            return f;
        }
    }

    /**
     * 写入消息到文件. 如果(msg.getMessageNumber() == msg.getTotalNrOfMsgs())， 输出流将在写入后关闭.
     *
     * @param msg - 包含要写入的数据的消息
     * @throws IllegalArgumentException - 如果工厂为读取的或关闭的
     * @throws IOException - 如果文件写入错误发生
     * @return returns true 如果文件已完成, 而且outputstream已经关闭; 否则false.
     */
    public boolean writeMessage(FileMessage msg)
            throws IllegalArgumentException, IOException {
        if (!openForWrite)
            throw new IllegalArgumentException(
                    "Can't write message, this factory is reading.");
        if (log.isDebugEnabled())
            log.debug("Message " + msg + " data " + HexUtils.toHexString(msg.getData())
                    + " data length " + msg.getDataLength() + " out " + out);

        if (msg.getMessageNumber() <= lastMessageProcessed.get()) {
            // 已处理的消息副本
            log.warn("Receive Message again -- Sender ActTimeout too short [ name: "
                    + msg.getContextName()
                    + " war: "
                    + msg.getFileName()
                    + " data: "
                    + HexUtils.toHexString(msg.getData())
                    + " data length: " + msg.getDataLength() + " ]");
            return false;
        }

        FileMessage previous =
            msgBuffer.put(Long.valueOf(msg.getMessageNumber()), msg);
        if (previous !=null) {
            // 尚未处理的消息副本
            log.warn("Receive Message again -- Sender ActTimeout too short [ name: "
                    + msg.getContextName()
                    + " war: "
                    + msg.getFileName()
                    + " data: "
                    + HexUtils.toHexString(msg.getData())
                    + " data length: " + msg.getDataLength() + " ]");
            return false;
        }

        FileMessage next = null;
        synchronized (this) {
            if (!isWriting) {
                next = msgBuffer.get(Long.valueOf(lastMessageProcessed.get() + 1));
                if (next != null) {
                    isWriting = true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        while (next != null) {
            out.write(next.getData(), 0, next.getDataLength());
            lastMessageProcessed.incrementAndGet();
            out.flush();
            if (next.getMessageNumber() == next.getTotalNrOfMsgs()) {
                out.close();
                cleanup();
                return true;
            }
            synchronized(this) {
                next =
                    msgBuffer.get(Long.valueOf(lastMessageProcessed.get() + 1));
                if (next == null) {
                    isWriting = false;
                }
            }
        }
        return false;
    }

    /**
     * 关闭工厂和流, 并设置它的所有应用为 null
     */
    public void cleanup() {
        if (in != null)
            try {
                in.close();
            } catch (IOException ignore) {
            }
        if (out != null)
            try {
                out.close();
            } catch (IOException ignore) {
            }
        in = null;
        out = null;
        size = 0;
        closed = true;
        data = null;
        nrOfMessagesProcessed = 0;
        totalNrOfMessages = 0;
        msgBuffer.clear();
        lastMessageProcessed = null;
    }

    /**
     * 检查以确保工厂能够执行被要求做的功能. 由readMessage/writeMessage调用，在这些方法继续之前.
     *
     * @param openForWrite 要检查的值
     * @throws IllegalArgumentException 如果状态不是预期的
     */
    protected void checkState(boolean openForWrite)
            throws IllegalArgumentException {
        if (this.openForWrite != openForWrite) {
            cleanup();
            if (openForWrite)
                throw new IllegalArgumentException(
                        "Can't write message, this factory is reading.");
            else
                throw new IllegalArgumentException(
                        "Can't read message, this factory is writing.");
        }
        if (this.closed) {
            cleanup();
            throw new IllegalArgumentException("Factory has been closed.");
        }
    }

    /**
     * 示例用法.
     *
     * @param args
     *            String[], args[0] - 从文件名读取, args[1] 写入文件名
     * @throws Exception An error occurred
     */
    public static void main(String[] args) throws Exception {

        System.out
                .println("Usage: FileMessageFactory fileToBeRead fileToBeWritten");
        System.out
                .println("Usage: This will make a copy of the file on the local file system");
        FileMessageFactory read = getInstance(new File(args[0]), false);
        FileMessageFactory write = getInstance(new File(args[1]), true);
        FileMessage msg = new FileMessage(null, args[0], args[0]);
        msg = read.readMessage(msg);
        if (msg == null) {
            System.out.println("Empty input file : " + args[0]);
            return;
        }
        System.out.println("Expecting to write " + msg.getTotalNrOfMsgs()
                + " messages.");
        int cnt = 0;
        while (msg != null) {
            write.writeMessage(msg);
            cnt++;
            msg = read.readMessage(msg);
        }
        System.out.println("Actually wrote " + cnt + " messages.");
    }

    public File getFile() {
        return file;
    }

    public boolean isValid() {
        if (maxValidTime > 0) {
            long timeNow = System.currentTimeMillis();
            int timeIdle = (int) ((timeNow - creationTime) / 1000L);
            if (timeIdle > maxValidTime) {
                cleanup();
                if (file.exists()) file.delete();
                return false;
            }
        }
        return true;
    }

    public int getMaxValidTime() {
        return maxValidTime;
    }

    public void setMaxValidTime(int maxValidTime) {
        this.maxValidTime = maxValidTime;
    }

}
