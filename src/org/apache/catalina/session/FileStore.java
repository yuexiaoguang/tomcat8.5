package org.apache.catalina.session;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import javax.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Session;
import org.apache.juli.logging.Log;

/**
 * <b>Store</b>接口实现类，在已配置的目录中使用每个保存会话的文件. 
 * 保存的静止的会话仍将过期.
 */
public final class FileStore extends StoreBase {

    // ----------------------------------------------------- Constants

    /**
     * 序列化会话文件名的后缀名.
     */
    private static final String FILE_EXT = ".session";


    // ----------------------------------------------------- Instance Variables

    /**
     * 存储在会话目录的路径.
     * 这可能是一个绝对路径名, 或相对于此应用程序的临时工作目录解析的相对路径.
     */
    private String directory = ".";


    /**
     * 存储会话的目录.
     */
    private File directoryFile = null;


    /**
     * 注册此存储的名称，用于日志记录.
     */
    private static final String storeName = "fileStore";


    /**
     * 注册后台线程的名称.
     */
    private static final String threadName = "FileStore";


    // ------------------------------------------------------------- Properties

    /**
     * @return 目录路径.
     */
    public String getDirectory() {
        return directory;
    }


    /**
     * 设置目录路径.
     *
     * @param path The new directory path
     */
    public void setDirectory(String path) {
        String oldDirectory = this.directory;
        this.directory = path;
        this.directoryFile = null;
        support.firePropertyChange("directory", oldDirectory, this.directory);
    }


    /**
     * @return 后台线程名称.
     */
    public String getThreadName() {
        return threadName;
    }


    /**
     * 返回名称，用于记录日志.
     */
    @Override
    public String getStoreName() {
        return storeName;
    }


    /**
     * 返回当前会话的数目.
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public int getSize() throws IOException {
        // 获取存储目录中的文件列表
        File file = directory();
        if (file == null) {
            return (0);
        }
        String files[] = file.list();

        // 计算哪些文件是会话
        int keycount = 0;
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                if (files[i].endsWith(FILE_EXT)) {
                    keycount++;
                }
            }
        }
        return keycount;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 删除所有会话.
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void clear() throws IOException {
        String[] keys = keys();
        for (int i = 0; i < keys.length; i++) {
            remove(keys[i]);
        }
    }


    /**
     * 返回一个数组包含当前保存的所有会话的会话标识符. 
     * 如果没有, 返回一个零长度数组.
     *
     * @exception IOException if an input/output error occurred
     */
    @Override
    public String[] keys() throws IOException {
        // 获取存储目录中的文件列表
        File file = directory();
        if (file == null) {
            return (new String[0]);
        }

        String files[] = file.list();

        // Bugzilla 32130
        if((files == null) || (files.length < 1)) {
            return (new String[0]);
        }

        // Build and return the list of session identifiers
        ArrayList<String> list = new ArrayList<>();
        int n = FILE_EXT.length();
        for (int i = 0; i < files.length; i++) {
            if (files[i].endsWith(FILE_EXT)) {
                list.add(files[i].substring(0, files[i].length() - n));
            }
        }
        return list.toArray(new String[list.size()]);
    }


    /**
     * 加载并返回与指定会话标识符关联的会话, 不删除它. 
     * 如果没有, 返回<code>null</code>.
     *
     * @param id Session identifier of the session to load
     *
     * @exception ClassNotFoundException if a deserialization error occurs
     * @exception IOException if an input/output error occurs
     */
    @Override
    public Session load(String id) throws ClassNotFoundException, IOException {
        // 打开一个输入流到指定的路径
        File file = file(id);
        if (file == null) {
            return null;
        }

        if (!file.exists()) {
            return null;
        }

        Context context = getManager().getContext();
        Log contextLog = context.getLogger();

        if (contextLog.isDebugEnabled()) {
            contextLog.debug(sm.getString(getStoreName()+".loading", id, file.getAbsolutePath()));
        }

        ClassLoader oldThreadContextCL = context.bind(Globals.IS_SECURITY_ENABLED, null);

        try (FileInputStream fis = new FileInputStream(file.getAbsolutePath());
                ObjectInputStream ois = getObjectInputStream(fis)) {

            StandardSession session = (StandardSession) manager.createEmptySession();
            session.readObjectData(ois);
            session.setManager(manager);
            return session;
        } catch (FileNotFoundException e) {
            if (contextLog.isDebugEnabled()) {
                contextLog.debug("No persisted data file found");
            }
            return null;
        } finally {
            context.unbind(Globals.IS_SECURITY_ENABLED, oldThreadContextCL);
        }
    }


    /**
     * 删除指定的会话标识符的会话 . 如果没有, 什么都不做.
     *
     * @param id Session identifier of the Session to be removed
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void remove(String id) throws IOException {
        File file = file(id);
        if (file == null) {
            return;
        }
        if (manager.getContext().getLogger().isDebugEnabled()) {
            manager.getContext().getLogger().debug(sm.getString(getStoreName() + ".removing",
                             id, file.getAbsolutePath()));
        }
        file.delete();
    }


    /**
     * 保存指定的 Session. 替换先前保存的关联会话标识符的信息.
     *
     * @param session Session to be saved
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void save(Session session) throws IOException {
        // Open an output stream to the specified pathname, if any
        File file = file(session.getIdInternal());
        if (file == null) {
            return;
        }
        if (manager.getContext().getLogger().isDebugEnabled()) {
            manager.getContext().getLogger().debug(sm.getString(getStoreName() + ".saving",
                             session.getIdInternal(), file.getAbsolutePath()));
        }

        try (FileOutputStream fos = new FileOutputStream(file.getAbsolutePath());
                ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(fos))) {
            ((StandardSession)session).writeObjectData(oos);
        }
    }


    // -------------------------------------------------------- Private Methods

    /**
     * 会话持久化目录的路径信息. 如果目录不存在，则将创建该目录.
     */
    private File directory() throws IOException {
        if (this.directory == null) {
            return null;
        }
        if (this.directoryFile != null) {
            // NOTE:  Race condition is harmless, so do not synchronize
            return this.directoryFile;
        }
        File file = new File(this.directory);
        if (!file.isAbsolute()) {
            Context context = manager.getContext();
            ServletContext servletContext = context.getServletContext();
            File work = (File) servletContext.getAttribute(ServletContext.TEMPDIR);
            file = new File(work, this.directory);
        }
        if (!file.exists() || !file.isDirectory()) {
            if (!file.delete() && file.exists()) {
                throw new IOException(sm.getString("fileStore.deleteFailed", file));
            }
            if (!file.mkdirs() && !file.isDirectory()) {
                throw new IOException(sm.getString("fileStore.createFailed", file));
            }
        }
        this.directoryFile = file;
        return file;
    }


    /**
     * 会话持久化文件的路径信息.
     *
     * @param id 要检索的会话的id. 这在文件命名中使用.
     */
    private File file(String id) throws IOException {
        if (this.directory == null) {
            return null;
        }
        String filename = id + FILE_EXT;
        File file = new File(directory(), filename);
        return file;
    }
}
