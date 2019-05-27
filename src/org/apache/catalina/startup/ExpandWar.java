package org.apache.catalina.startup;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

import org.apache.catalina.Host;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * 扩展Host的appBase的 WAR.
 */
public class ExpandWar {

    private static final Log log = LogFactory.getLog(ExpandWar.class);

    /**
     * The string resources for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * 将指定URL上找到的WAR文件扩展到解压的目录结构中, 返回到扩展目录的绝对路径名.
     *
     * @param host 正在安装的Host
     * @param war 要扩展的Web应用程序归档文件的URL(必须以 "jar:"开头)
     * @param pathname Web应用的上下文路径名称
     *
     * @exception IllegalArgumentException 如果不是 "jar:" URL或WAR文件无效
     * @exception IOException 如果在扩展过程中遇到输入/输出错误
     *
     * @return The absolute path to the expanded directory foe the given WAR
     */
    public static String expand(Host host, URL war, String pathname)
        throws IOException {

        /* 获得打开InputStream的最后更新时间, 没有明确的封闭方法. 需要获取并关闭InputStream避免文件泄漏和关联的锁定文件.
         */
        JarURLConnection juc = (JarURLConnection) war.openConnection();
        juc.setUseCaches(false);
        URL jarFileUrl = juc.getJarFileURL();
        URLConnection jfuc = jarFileUrl.openConnection();

        boolean success = false;
        File docBase = new File(host.getAppBaseFile(), pathname);
        File warTracker = new File(host.getAppBaseFile(), pathname + Constants.WarTracker);
        long warLastModified = -1;

        try (InputStream is = jfuc.getInputStream()) {
            // 获取WAR的最后更新时间
            warLastModified = jfuc.getLastModified();
        }

        // 检查一下WAR是否已经扩展
        if (docBase.exists()) {
            // 扩展的WAR. Tomcat将设置warTracker的最后更新时间到WAR的最后更新时间，因此 当检测到Tomcat停止时修改WAR
            if (!warTracker.exists() || warTracker.lastModified() == warLastModified) {
                // No (detectable) changes to the WAR
                success = true;
                return (docBase.getAbsolutePath());
            }

            // WAR必须已经修改. 删除扩展的目录.
            log.info(sm.getString("expandWar.deleteOld", docBase));
            if (!delete(docBase)) {
                throw new IOException(sm.getString("expandWar.deleteFailed", docBase));
            }
        }

        // 创建新的文档基目录
        if(!docBase.mkdir() && !docBase.isDirectory()) {
            throw new IOException(sm.getString("expandWar.createFailed", docBase));
        }

        // 扩展WAR为新文档基目录
        String canonicalDocBasePrefix = docBase.getCanonicalPath();
        if (!canonicalDocBasePrefix.endsWith(File.separator)) {
            canonicalDocBasePrefix += File.separator;
        }

        // 创建war tracker parent (normally META-INF)
        File warTrackerParent = warTracker.getParentFile();
        if (!warTrackerParent.isDirectory() && !warTrackerParent.mkdirs()) {
            throw new IOException(sm.getString("expandWar.createFailed", warTrackerParent.getAbsolutePath()));
        }

        try (JarFile jarFile = juc.getJarFile()) {

            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                String name = jarEntry.getName();
                File expandedFile = new File(docBase, name);
                if (!expandedFile.getCanonicalPath().startsWith(
                        canonicalDocBasePrefix)) {
                    // Trying to expand outside the docBase
                    // Throw an exception to stop the deployment
                    throw new IllegalArgumentException(
                            sm.getString("expandWar.illegalPath",war, name,
                                    expandedFile.getCanonicalPath(),
                                    canonicalDocBasePrefix));
                }
                int last = name.lastIndexOf('/');
                if (last >= 0) {
                    File parent = new File(docBase,
                                           name.substring(0, last));
                    if (!parent.mkdirs() && !parent.isDirectory()) {
                        throw new IOException(
                                sm.getString("expandWar.createFailed", parent));
                    }
                }
                if (name.endsWith("/")) {
                    continue;
                }

                try (InputStream input = jarFile.getInputStream(jarEntry)) {
                    if (null == input) {
                        throw new ZipException(sm.getString("expandWar.missingJarEntry",
                                jarEntry.getName()));
                    }

                    // Bugzilla 33636
                    expand(input, expandedFile);
                    long lastModified = jarEntry.getTime();
                    if ((lastModified != -1) && (lastModified != 0)) {
                        expandedFile.setLastModified(lastModified);
                    }
                }
            }

            // 创建warTracker文件并将WAR的上次修改时间与上次修改时间对齐
            warTracker.createNewFile();
            warTracker.setLastModified(warLastModified);

            success = true;
        } catch (IOException e) {
            throw e;
        } finally {
            if (!success) {
                // 如果出了什么问题, 删除扩展的 dir
                deleteDir(docBase);
            }
        }

        // 将绝对路径返回到新的文档基目录
        return docBase.getAbsolutePath();
    }


    /**
     * 验证指定的URL中找到的 WAR文件.
     *
     * @param host 正在安装的Host
     * @param war 要扩展的Web应用程序归档文件的URL(必须以 "jar:"开头)
     * @param pathname Web应用的上下文路径名称
     *
     * @exception IllegalArgumentException 如果不是 "jar:" URL或WAR文件无效
     * @exception IOException 如果在扩展过程中遇到输入/输出错误
     */
    public static void validate(Host host, URL war, String pathname) throws IOException {

        File docBase = new File(host.getAppBaseFile(), pathname);

        // Calculate the document base directory
        String canonicalDocBasePrefix = docBase.getCanonicalPath();
        if (!canonicalDocBasePrefix.endsWith(File.separator)) {
            canonicalDocBasePrefix += File.separator;
        }
        JarURLConnection juc = (JarURLConnection) war.openConnection();
        juc.setUseCaches(false);
        try (JarFile jarFile = juc.getJarFile()) {
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                String name = jarEntry.getName();
                File expandedFile = new File(docBase, name);
                if (!expandedFile.getCanonicalPath().startsWith(
                        canonicalDocBasePrefix)) {
                    // Entry located outside the docBase
                    // Throw an exception to stop the deployment
                    throw new IllegalArgumentException(
                            sm.getString("expandWar.illegalPath",war, name,
                                    expandedFile.getCanonicalPath(),
                                    canonicalDocBasePrefix));
                }
            }
        } catch (IOException e) {
            throw e;
        }
    }


    /**
     * 将指定的文件或目录复制到目的地.
     *
     * @param src 源文件
     * @param dest 目标文件
     * @return <code>true</code>如果成功复制
     */
    public static boolean copy(File src, File dest) {

        boolean result = true;

        String files[] = null;
        if (src.isDirectory()) {
            files = src.list();
            result = dest.mkdir();
        } else {
            files = new String[1];
            files[0] = "";
        }
        if (files == null) {
            files = new String[0];
        }
        for (int i = 0; (i < files.length) && result; i++) {
            File fileSrc = new File(src, files[i]);
            File fileDest = new File(dest, files[i]);
            if (fileSrc.isDirectory()) {
                result = copy(fileSrc, fileDest);
            } else {
                try (FileChannel ic = (new FileInputStream(fileSrc)).getChannel();
                        FileChannel oc = (new FileOutputStream(fileDest)).getChannel()) {
                    ic.transferTo(0, ic.size(), oc);
                } catch (IOException e) {
                    log.error(sm.getString("expandWar.copy", fileSrc, fileDest), e);
                    result = false;
                }
            }
        }
        return result;
    }


    /**
     * 删除指定的目录，包括其所有内容和递归子目录.
     *
     * @param dir 要删除的File
     * @return <code>true</code>如果删除成功
     */
    public static boolean delete(File dir) {
        // Log failure by default
        return delete(dir, true);
    }


    /**
     * 删除指定的目录，包括其所有内容和递归子目录.
     *
     * @param dir 要删除的File
     * @param logFailure <code>true</code>删除失败后是否记录
     * 
     * @return <code>true</code>如果删除成功
     */
    public static boolean delete(File dir, boolean logFailure) {
        boolean result;
        if (dir.isDirectory()) {
            result = deleteDir(dir, logFailure);
        } else {
            if (dir.exists()) {
                result = dir.delete();
            } else {
                result = true;
            }
        }
        if (logFailure && !result) {
            log.error(sm.getString(
                    "expandWar.deleteFailed", dir.getAbsolutePath()));
        }
        return result;
    }


    /**
     * 删除指定的目录，包括其所有内容和递归子目录. 所有失败将被记录.
     *
     * @param dir 要删除的File
     * @return <code>true</code>如果删除成功
     */
    public static boolean deleteDir(File dir) {
        return deleteDir(dir, true);
    }


    /**
     * 删除指定的目录，包括其所有内容和递归子目录.
     *
     * @param dir 要删除的File
     * @param logFailure <code>true</code>删除失败后是否记录
     * 
     * @return <code>true</code>如果删除成功
     */
    public static boolean deleteDir(File dir, boolean logFailure) {

        String files[] = dir.list();
        if (files == null) {
            files = new String[0];
        }
        for (int i = 0; i < files.length; i++) {
            File file = new File(dir, files[i]);
            if (file.isDirectory()) {
                deleteDir(file, logFailure);
            } else {
                file.delete();
            }
        }

        boolean result;
        if (dir.exists()) {
            result = dir.delete();
        } else {
            result = true;
        }

        if (logFailure && !result) {
            log.error(sm.getString(
                    "expandWar.deleteFailed", dir.getAbsolutePath()));
        }

        return result;
    }


    /**
     * 将指定的输入流扩展到指定的文件中.
     *
     * @param input 要复制的InputStream
     * @param file 要创建的文件
     *
     * @exception IOException if an input/output error occurs
     */
    private static void expand(InputStream input, File file) throws IOException {
        try (BufferedOutputStream output =
                new BufferedOutputStream(new FileOutputStream(file))) {
            byte buffer[] = new byte[2048];
            while (true) {
                int n = input.read(buffer);
                if (n <= 0)
                    break;
                output.write(buffer, 0, n);
            }
        }
    }
}
