package org.apache.juli;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

/**
 * <b>Handler</b>的实现类追加日志消息到配置的目录中名称为 {prefix}{date}{suffix} 的文件中.
 *
 * <p>可以使用以下配置属性:</p>
 *
 * <ul>
 *   <li><code>directory</code> - 创建日志文件的目录.
 *    如果路径不是绝对的, 它相对于应用程序的当前工作目录. Apache Tomcat配置文件通常指定此属性的绝对路径, <code>${catalina.base}/logs</code>
 *    默认值: <code>logs</code></li>
 *   <li><code>rotatable</code> - 如果是<code>true</code>, 日志文件将在午夜过后的第一次写入时旋转, 文件名将会是 <code>{prefix}{date}{suffix}</code>, 日期是 yyyy-MM-dd.
 *    如果是<code>false</code>, 文件不会旋转, 文件名将会是 <code>{prefix}{suffix}</code>.
 *    默认值: <code>true</code></li>
 *   <li><code>prefix</code> - 日志文件名的前缀部分.
 *    默认值: <code>juli.</code></li>
 *   <li><code>suffix</code> - 日志文件名的后缀.
 *    默认值: <code>.log</code></li>
 *   <li><code>bufferSize</code> - 配置缓冲区. <code>0</code>值使用系统默认缓冲区 (通常使用8K缓冲区).
 *    <code>&lt;0</code>值在每次日志写入时强制 writer 刷新. <code>&gt;0</code>值使用一个 BufferedOutputStream, 但系统默认缓冲区也将应用.
 *    默认值: <code>-1</code></li>
 *   <li><code>encoding</code> - 日志文件使用的字符集.
 *    默认值: 空字符串, 意味着使用系统默认字符集.</li>
 *   <li><code>level</code> - 此Handler的级别阈值. 可能的级别查看 <code>java.util.logging.Level</code>.
 *    默认值: <code>ALL</code></li>
 *   <li><code>filter</code> - 这个Handler的<code>java.util.logging.Filter</code>实现类名.
 *    默认值: unset</li>
 *   <li><code>formatter</code> - 这个Handler的<code>java.util.logging.Formatter</code>实现类名.
 *    默认值: <code>java.util.logging.SimpleFormatter</code></li>
 *   <li><code>maxDays</code> - 保留日志文件的最大天数.
 *    如果指定的值<code>&lt;=0</code>, 那么日志文件将永远保存在文件系统中, 否则他们将保持指定的最长天数.
 *    默认值: <code>-1</code>.</li>
 * </ul>
 */
public class FileHandler extends Handler {
    public static final int DEFAULT_MAX_DAYS = -1;

    private static final ExecutorService DELETE_FILES_SERVICE =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                private final boolean isSecurityEnabled;
                private final ThreadGroup group;
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                private final String namePrefix = "FileHandlerLogFilesCleaner-";

                {
                    SecurityManager s = System.getSecurityManager();
                    if (s == null) {
                        this.isSecurityEnabled = false;
                        this.group = Thread.currentThread().getThreadGroup();
                    } else {
                        this.isSecurityEnabled = true;
                        this.group = s.getThreadGroup();
                    }
                }

                @Override
                public Thread newThread(Runnable r) {
                    final ClassLoader loader = Thread.currentThread().getContextClassLoader();
                    try {
                        // 不应该由webapp类加载器创建线程
                        if (isSecurityEnabled) {
                            AccessController.doPrivileged(new PrivilegedAction<Void>() {

                                @Override
                                public Void run() {
                                    Thread.currentThread()
                                            .setContextClassLoader(getClass().getClassLoader());
                                    return null;
                                }
                            });
                        } else {
                            Thread.currentThread()
                                    .setContextClassLoader(getClass().getClassLoader());
                        }
                        Thread t = new Thread(group, r,
                                namePrefix + threadNumber.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    } finally {
                        if (isSecurityEnabled) {
                            AccessController.doPrivileged(new PrivilegedAction<Void>() {

                                @Override
                                public Void run() {
                                    Thread.currentThread().setContextClassLoader(loader);
                                    return null;
                                }
                            });
                        } else {
                            Thread.currentThread().setContextClassLoader(loader);
                        }
                    }
                }
            });

    // ------------------------------------------------------------ Constructor


    public FileHandler() {
        this(null, null, null, DEFAULT_MAX_DAYS);
    }


    public FileHandler(String directory, String prefix, String suffix) {
        this(directory, prefix, suffix, DEFAULT_MAX_DAYS);
    }

    public FileHandler(String directory, String prefix, String suffix, int maxDays) {
        this.directory = directory;
        this.prefix = prefix;
        this.suffix = suffix;
        this.maxDays = maxDays;
        configure();
        openWriter();
        clean();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 当前打开的日志文件的截止日期, 如果没有打开的日志文件，则为零长度字符串.
     */
    private volatile String date = "";


    /**
     * 创建日志文件的目录.
     */
    private String directory = null;


    /**
     * 添加到日志文件文件名的前缀.
     */
    private String prefix = null;


    /**
     * 添加到日志文件文件名的后缀.
     */
    private String suffix = null;


    /**
     * 确定日志文件是否可以旋转
     */
    private boolean rotatable = true;


    /**
     * 保留日志文件的最大天数
     */
    private int maxDays = DEFAULT_MAX_DAYS;


    /**
     * 当前记录的 PrintWriter.
     */
    private volatile PrintWriter writer = null;


    /**
     * 用于控制访问writer的锁.
     */
    protected final ReadWriteLock writerLock = new ReentrantReadWriteLock();


    /**
     * 日志缓冲区大小.
     */
    private int bufferSize = -1;


    /**
     * 表示{prefix}{date}{suffix}类型的文件名模式. 日期是 YYYY-MM-DD
     */
    private Pattern pattern;


    // --------------------------------------------------------- Public Methods


    /**
     * 格式化并发布一个 <tt>LogRecord</tt>.
     *
     * @param  record  日志事件的描述
     */
    @Override
    public void publish(LogRecord record) {

        if (!isLoggable(record)) {
            return;
        }

        // 构造将使用的时间戳
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        String tsDate = ts.toString().substring(0, 10);

        writerLock.readLock().lock();
        try {
            // 如果日期已更改, 转换日志文件
            if (rotatable && !date.equals(tsDate)) {
                // 转换之前更新到 writeLock
                writerLock.readLock().unlock();
                writerLock.writeLock().lock();
                try {
                    // 确保另一个线程尚未完成此操作
                    if (!date.equals(tsDate)) {
                        closeWriter();
                        date = tsDate;
                        openWriter();
                        clean();
                    }
                } finally {
                    // Downgrade to read-lock. 确保 writer 保持有效, 直到写入日志消息
                    writerLock.readLock().lock();
                    writerLock.writeLock().unlock();
                }
            }

            String result = null;
            try {
                result = getFormatter().format(record);
            } catch (Exception e) {
                reportError(null, e, ErrorManager.FORMAT_FAILURE);
                return;
            }

            try {
                if (writer != null) {
                    writer.write(result);
                    if (bufferSize < 0) {
                        writer.flush();
                    }
                } else {
                    reportError("FileHandler is closed or not yet initialized, unable to log ["
                            + result + "]", null, ErrorManager.WRITE_FAILURE);
                }
            } catch (Exception e) {
                reportError(null, e, ErrorManager.WRITE_FAILURE);
                return;
            }
        } finally {
            writerLock.readLock().unlock();
        }
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 关闭当前打开的日志文件.
     */
    @Override
    public void close() {
        closeWriter();
    }

    protected void closeWriter() {

        writerLock.writeLock().lock();
        try {
            if (writer == null) {
                return;
            }
            writer.write(getFormatter().getTail(this));
            writer.flush();
            writer.close();
            writer = null;
            date = "";
        } catch (Exception e) {
            reportError(null, e, ErrorManager.CLOSE_FAILURE);
        } finally {
            writerLock.writeLock().unlock();
        }
    }


    /**
     * 刷新 writer.
     */
    @Override
    public void flush() {

        writerLock.readLock().lock();
        try {
            if (writer == null) {
                return;
            }
            writer.flush();
        } catch (Exception e) {
            reportError(null, e, ErrorManager.FLUSH_FAILURE);
        } finally {
            writerLock.readLock().unlock();
        }

    }


    /**
     * 从<code>LogManager</code> 属性配置.
     */
    private void configure() {

        Timestamp ts = new Timestamp(System.currentTimeMillis());
        String tsString = ts.toString().substring(0, 19);
        date = tsString.substring(0, 10);

        String className = this.getClass().getName(); //allow classes to override

        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        // 检索日志文件名的配置
        rotatable = Boolean.parseBoolean(getProperty(className + ".rotatable", "true"));
        if (directory == null) {
            directory = getProperty(className + ".directory", "logs");
        }
        if (prefix == null) {
            prefix = getProperty(className + ".prefix", "juli.");
        }
        if (suffix == null) {
            suffix = getProperty(className + ".suffix", ".log");
        }

        // https://bz.apache.org/bugzilla/show_bug.cgi?id=61232
        boolean shouldCheckForRedundantSeparator = !rotatable && !prefix.isEmpty()
                && !suffix.isEmpty();
        // 假设separator只是一个char, 如果有更多的用例, 可能会引入分隔符的概念
        if (shouldCheckForRedundantSeparator
                && (prefix.charAt(prefix.length() - 1) == suffix.charAt(0))) {
            suffix = suffix.substring(1);
        }

        pattern = Pattern.compile("^(" + Pattern.quote(prefix) + ")\\d{4}-\\d{1,2}-\\d{1,2}("
                + Pattern.quote(suffix) + ")$");
        String sMaxDays = getProperty(className + ".maxDays", String.valueOf(DEFAULT_MAX_DAYS));
        if (maxDays <= 0) {
            try {
                maxDays = Integer.parseInt(sMaxDays);
            } catch (NumberFormatException ignore) {
                // no-op
            }
        }
        String sBufferSize = getProperty(className + ".bufferSize", String.valueOf(bufferSize));
        try {
            bufferSize = Integer.parseInt(sBufferSize);
        } catch (NumberFormatException ignore) {
            //no op
        }
        // 获取日志文件的编码
        String encoding = getProperty(className + ".encoding", null);
        if (encoding != null && encoding.length() > 0) {
            try {
                setEncoding(encoding);
            } catch (UnsupportedEncodingException ex) {
                // Ignore
            }
        }

        // 获取处理程序的日志记录级别
        setLevel(Level.parse(getProperty(className + ".level", "" + Level.ALL)));

        // 获取过滤器配置
        String filterName = getProperty(className + ".filter", null);
        if (filterName != null) {
            try {
                setFilter((Filter) cl.loadClass(filterName).getConstructor().newInstance());
            } catch (Exception e) {
                // Ignore
            }
        }

        // Set formatter
        String formatterName = getProperty(className + ".formatter", null);
        if (formatterName != null) {
            try {
                setFormatter((Formatter) cl.loadClass(
                        formatterName).getConstructor().newInstance());
            } catch (Exception e) {
                // 忽略并回退到默认值
                setFormatter(new OneLineFormatter());
            }
        } else {
            setFormatter(new OneLineFormatter());
        }

        // 设置错误管理器
        setErrorManager(new ErrorManager());

    }


    private String getProperty(String name, String defaultValue) {
        String value = LogManager.getLogManager().getProperty(name);
        if (value == null) {
            value = defaultValue;
        } else {
            value = value.trim();
        }
        return value;
    }


    /**
     * 打开<code>date</code>指定的日期的新日志文件.
     */
    protected void open() {
        openWriter();
    }

    protected void openWriter() {

        // 创建目录
        File dir = new File(directory);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            reportError("Unable to create [" + dir + "]", null, ErrorManager.OPEN_FAILURE);
            writer = null;
            return;
        }

        // 打开当前日志文件
        writerLock.writeLock().lock();
        FileOutputStream fos = null;
        OutputStream os = null;
        try {
            File pathname = new File(dir.getAbsoluteFile(), prefix
                    + (rotatable ? date : "") + suffix);
            File parent = pathname.getParentFile();
            if (!parent.mkdirs() && !parent.isDirectory()) {
                reportError("Unable to create [" + parent + "]", null, ErrorManager.OPEN_FAILURE);
                writer = null;
                return;
            }
            String encoding = getEncoding();
            fos = new FileOutputStream(pathname, true);
            os = bufferSize > 0 ? new BufferedOutputStream(fos, bufferSize) : fos;
            writer = new PrintWriter(
                    (encoding != null) ? new OutputStreamWriter(os, encoding)
                                       : new OutputStreamWriter(os), false);
            writer.write(getFormatter().getHead(this));
        } catch (Exception e) {
            reportError(null, e, ErrorManager.OPEN_FAILURE);
            writer = null;
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        } finally {
            writerLock.writeLock().unlock();
        }
    }

    private void clean() {
        if (maxDays <= 0) {
            return;
        }
        DELETE_FILES_SERVICE.submit(new Runnable() {

            @Override
            public void run() {
                try (DirectoryStream<Path> files = streamFilesForDelete()) {
                    for (Path file : files) {
                        Files.delete(file);
                    }
                } catch (IOException e) {
                    reportError("Unable to delete log files older than [" + maxDays + "] days",
                            null, ErrorManager.GENERIC_FAILURE);
                }
            }
        });
    }

    private DirectoryStream<Path> streamFilesForDelete() throws IOException {
        final Date maxDaysOffset = getMaxDaysOffset();
        final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        return Files.newDirectoryStream(new File(directory).toPath(),
                new DirectoryStream.Filter<Path>() {

                    @Override
                    public boolean accept(Path path) throws IOException {
                        boolean result = false;
                        String date = obtainDateFromPath(path);
                        if (date != null) {
                            try {
                                Date dateFromFile = formatter.parse(date);
                                result = dateFromFile.before(maxDaysOffset);
                            } catch (ParseException e) {
                                // no-op
                            }
                        }
                        return result;
                    }
                });
    }

    private String obtainDateFromPath(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return null;
        }
        String date = fileName.toString();
        if (pattern.matcher(date).matches()) {
            date = date.substring(prefix.length());
            return date.substring(0, date.length() - suffix.length());
        } else {
            return null;
        }
    }

    private Date getMaxDaysOffset() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DATE, -maxDays);
        return cal.getTime();
    }
}
