package org.apache.catalina.valves;


import java.io.BufferedWriter;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.catalina.LifecycleException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.B2CConverter;


/**
 * 这是一个{@link AbstractAccessLogValve}的具体的实现, 将访问日志输出到文件. 该实现的特点包括:
 * <ul>
 * <li>日志文件的自动日期翻转</li>
 * <li>可选的日志文件旋转</li>
 * </ul>
 * <p>
 * 对于UNIX 用户, 另一个字段<code>checkExists</code>也是可用的. 如果设置为 true, 日志文件是否存在将在每次日志记录之前检查.
 * 这样，外部日志旋转器可以在某个地方移动文件，而Tomcat将从一个新文件开始.
 * </p>
 *
 * <p>
 * 对于JMX junkies, <code>rotate</code>方法已经允许告诉这个实例将现有的日志文件移到其他地方，并开始编写新的日志文件.
 * </p>
 */
public class AccessLogValve extends AbstractAccessLogValve {

    private static final Log log = LogFactory.getLog(AccessLogValve.class);

    //------------------------------------------------------ Constructor
    public AccessLogValve() {
        super();
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * 当前打开日志文件的日期, 或零长度字符串，如果没有打开的日志文件.
     */
    private volatile String dateStamp = "";


    /**
     * 创建日志文件的目录.
     */
    private String directory = "logs";

    /**
     * 添加到日志文件的文件名的前缀.
     */
    protected String prefix = "access_log";


    /**
     * 应该轮转日志文件吗? 默认是true (例如旧行为)
     */
    protected boolean rotatable = true;

    protected boolean renameOnRotate = false;


    /**
     * 缓冲日志.
     */
    private boolean buffered = true;


    /**
     * 添加到日志文件的文件名的后缀.
     */
    protected String suffix = "";


    /**
     * 当前记录使用的PrintWriter.
     */
    protected PrintWriter writer = null;


    protected SimpleDateFormat fileDateFormatter = null;


    /**
     * 正在写入的当前日志文件. 当 checkExists 是true时有用.
     */
    protected File currentLogFile = null;

    /**
     * 日志每天轮转最后检查的时刻.
     */
    private volatile long rotationLastChecked = 0L;

    /**
     * 检查日志文件是否存在? 如果外部代理重命名日志文件，那么可以自动重新创建它.
     */
    private boolean checkExists = false;

    /**
     * 放置在日志文件名中的日期格式.
     */
    protected String fileDateFormat = ".yyyy-MM-dd";

    /**
     * 日志文件使用的字符集. 如果是<code>null</code>, 系统默认字符集将被使用.
     * 空字符串视为<code>null</code>, 当分配该属性时.
     */
    protected String encoding = null;

    // ------------------------------------------------------------- Properties


    /**
     * @return 创建日志文件的目录.
     */
    public String getDirectory() {
        return (directory);
    }


    /**
     * 设置创建日志文件的目录
     *
     * @param directory 日志文件目录
     */
    public void setDirectory(String directory) {
        this.directory = directory;
    }

    /**
     * 在日志记录之前检查文件是否存在.
     * 
     * @return <code>true</code>首先检查文件存在
     */
    public boolean isCheckExists() {
        return checkExists;
    }


    /**
     * 设置是否在日志记录之前, 检查日志文件是否存在.
     *
     * @param checkExists true 检查文件存在的意义.
     */
    public void setCheckExists(boolean checkExists) {
        this.checkExists = checkExists;
    }


    /**
     * @return 日志文件前缀
     */
    public String getPrefix() {
        return (prefix);
    }


    /**
     * 设置日志文件前缀
     *
     * @param prefix 日志文件前缀
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }


    /**
     * 是否轮转日志.
     *
     * @return <code>true</code>如果访问日志应该被旋转
     */
    public boolean isRotatable() {
        return rotatable;
    }


    /**
     * 是否轮转日志.
     *
     * @param rotatable true如果访问日志应该被旋转
     */
    public void setRotatable(boolean rotatable) {
        this.rotatable = rotatable;
    }


    /**
     * @return <code>true</code>如果日志文件名是时间戳，只有当它们轮转时
     */
    public boolean isRenameOnRotate() {
        return renameOnRotate;
    }


    /**
     * @param renameOnRotate true if defer inclusion of date stamp
     */
    public void setRenameOnRotate(boolean renameOnRotate) {
        this.renameOnRotate = renameOnRotate;
    }


    /**
     * 日志是否缓冲. 通常缓冲可以提高性能.
     * 
     * @return <code>true</code>如果日志记录使用缓冲区
     */
    public boolean isBuffered() {
        return buffered;
    }


    /**
     * 日志是否缓冲.
     *
     * @param buffered <code>true</code>如果缓冲.
     */
    public void setBuffered(boolean buffered) {
        this.buffered = buffered;
    }


    /**
     * @return 日志文件的文件名的后缀..
     */
    public String getSuffix() {
        return (suffix);
    }


    /**
     * 设置日志文件的文件名的后缀..
     *
     * @param suffix 日志文件的文件名的后缀.
     */
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public String getFileDateFormat() {
        return fileDateFormat;
    }


    /**
     * @param fileDateFormat 文件时间戳的格式
     */
    public void setFileDateFormat(String fileDateFormat) {
        String newFormat;
        if (fileDateFormat == null) {
            newFormat = "";
        } else {
            newFormat = fileDateFormat;
        }
        this.fileDateFormat = newFormat;

        synchronized (this) {
            fileDateFormatter = new SimpleDateFormat(newFormat, Locale.US);
            fileDateFormatter.setTimeZone(TimeZone.getDefault());
        }
    }

    /**
     * 返回用于写入日志文件的字符集名称.
     *
     * @return 字符集名称, 或<code>null</code>如果使用系统默认字符集.
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * 设置用于写入日志文件的字符集.
     *
     * @param encoding 字符集的名称.
     */
    public void setEncoding(String encoding) {
        if (encoding != null && encoding.length() > 0) {
            this.encoding = encoding;
        } else {
            this.encoding = null;
        }
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 执行周期性任务, 例如重新加载, etc.
     * 此方法将在容器的类加载上下文中调用. 将捕获和记录异常.
     */
    @Override
    public synchronized void backgroundProcess() {
        if (getState().isAvailable() && getEnabled() && writer != null &&
                buffered) {
            writer.flush();
        }
    }

    /**
     * 轮转日志文件.
     */
    public void rotate() {
        if (rotatable) {
            // 一秒检查一次.
            long systime = System.currentTimeMillis();
            if ((systime - rotationLastChecked) > 1000) {
                synchronized(this) {
                    if ((systime - rotationLastChecked) > 1000) {
                        rotationLastChecked = systime;

                        String tsDate;
                        // 检查日期的修改
                        tsDate = fileDateFormatter.format(new Date(systime));

                        // 如果日期已经修改, 切换日志文件
                        if (!dateStamp.equals(tsDate)) {
                            close(true);
                            dateStamp = tsDate;
                            open();
                        }
                    }
                }
            }
        }
    }

    /**
     * 将现有日志文件重命名为其他文件. 然后再次打开旧日志文件名. 打算由JMX代理调用.
     *
     * @param newFileName 文件名
     * @return true 如果文件轮转没有错误
     */
    public synchronized boolean rotate(String newFileName) {

        if (currentLogFile != null) {
            File holder = currentLogFile;
            close(false);
            try {
                holder.renameTo(new File(newFileName));
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                log.error(sm.getString("accessLogValve.rotateFail"), e);
            }

            /* Make sure date is correct */
            dateStamp = fileDateFormatter.format(
                    new Date(System.currentTimeMillis()));

            open();
            return true;
        } else {
            return false;
        }

    }

    // -------------------------------------------------------- Private Methods


    /**
     * 创建基于当前日志文件名的File对象.
     * 根据需要创建目录，但没有创建或打开基础文件.
     *
     * @param useDateStamp 是否在文件名中包含时间戳.
     * @return the log file object
     */
    private File getLogFile(boolean useDateStamp) {

        // Create the directory if necessary
        File dir = new File(directory);
        if (!dir.isAbsolute()) {
            dir = new File(getContainer().getCatalinaBase(), directory);
        }
        if (!dir.mkdirs() && !dir.isDirectory()) {
            log.error(sm.getString("accessLogValve.openDirFail", dir));
        }

        // 计算当前日志文件名
        File pathname;
        if (useDateStamp) {
            pathname = new File(dir.getAbsoluteFile(), prefix + dateStamp
                    + suffix);
        } else {
            pathname = new File(dir.getAbsoluteFile(), prefix + suffix);
        }
        File parent = pathname.getParentFile();
        if (!parent.mkdirs() && !parent.isDirectory()) {
            log.error(sm.getString("accessLogValve.openDirFail", parent));
        }
        return pathname;
    }

    /**
     * 将轮转的日志文件移回未轮转的日志文件.
     */
    private void restore() {
        File newLogFile = getLogFile(false);
        File rotatedLogFile = getLogFile(true);
        if (rotatedLogFile.exists() && !newLogFile.exists() &&
            !rotatedLogFile.equals(newLogFile)) {
            try {
                if (!rotatedLogFile.renameTo(newLogFile)) {
                    log.error(sm.getString("accessLogValve.renameFail", rotatedLogFile, newLogFile));
                }
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                log.error(sm.getString("accessLogValve.renameFail", rotatedLogFile, newLogFile), e);
            }
        }
    }


    /**
     * 关闭当前打开的日志文件
     *
     * @param rename 关闭文件后重命名为最终名称
     */
    private synchronized void close(boolean rename) {
        if (writer == null) {
            return;
        }
        writer.flush();
        writer.close();
        if (rename && renameOnRotate) {
            File newLogFile = getLogFile(true);
            if (!newLogFile.exists()) {
                try {
                    if (!currentLogFile.renameTo(newLogFile)) {
                        log.error(sm.getString("accessLogValve.renameFail", currentLogFile, newLogFile));
                    }
                } catch (Throwable e) {
                    ExceptionUtils.handleThrowable(e);
                    log.error(sm.getString("accessLogValve.renameFail", currentLogFile, newLogFile), e);
                }
            } else {
                log.error(sm.getString("accessLogValve.alreadyExists", currentLogFile, newLogFile));
            }
        }
        writer = null;
        dateStamp = "";
        currentLogFile = null;
    }


    /**
     * 将指定的消息记录到日志文件中, 如果日期从上一次日志调用更改后切换文件.
     *
     * @param message Message to be logged
     */
    @Override
    public void log(CharArrayWriter message) {

        rotate();

        /* In case something external rotated the file instead */
        if (checkExists) {
            synchronized (this) {
                if (currentLogFile != null && !currentLogFile.exists()) {
                    try {
                        close(false);
                    } catch (Throwable e) {
                        ExceptionUtils.handleThrowable(e);
                        log.info(sm.getString("accessLogValve.closeFail"), e);
                    }

                    /* Make sure date is correct */
                    dateStamp = fileDateFormatter.format(
                            new Date(System.currentTimeMillis()));

                    open();
                }
            }
        }

        // Log this message
        try {
            synchronized(this) {
                if (writer != null) {
                    message.writeTo(writer);
                    writer.println("");
                    if (!buffered) {
                        writer.flush();
                    }
                }
            }
        } catch (IOException ioe) {
            log.warn(sm.getString(
                    "accessLogValve.writeFail", message.toString()), ioe);
        }
    }


    /**
     * 打开<code>dateStamp</code>指定的日期的新日志文件.
     */
    protected synchronized void open() {
        // 打开当前日志文件
        // If no rotate - no need for dateStamp in fileName
        File pathname = getLogFile(rotatable && !renameOnRotate);

        Charset charset = null;
        if (encoding != null) {
            try {
                charset = B2CConverter.getCharset(encoding);
            } catch (UnsupportedEncodingException ex) {
                log.error(sm.getString(
                        "accessLogValve.unsupportedEncoding", encoding), ex);
            }
        }
        if (charset == null) {
            charset = StandardCharsets.ISO_8859_1;
        }

        try {
            writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(pathname, true), charset), 128000),
                    false);

            currentLogFile = pathname;
        } catch (IOException e) {
            writer = null;
            currentLogFile = null;
            log.error(sm.getString("accessLogValve.openFail", pathname), e);
        }
    }

    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        // Initialize the Date formatters
        String format = getFileDateFormat();
        fileDateFormatter = new SimpleDateFormat(format, Locale.US);
        fileDateFormatter.setTimeZone(TimeZone.getDefault());
        dateStamp = fileDateFormatter.format(new Date(System.currentTimeMillis()));
        if (rotatable && renameOnRotate) {
            restore();
        }
        open();

        super.startInternal();
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        super.stopInternal();
        close(false);
    }
}
