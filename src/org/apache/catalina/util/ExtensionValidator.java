package org.apache.catalina.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.catalina.Context;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * 确保所有的扩展依赖对于Web应用是满足的. 这个类建立了一个有效的应用扩展的清单并验证了这些扩展.
 *
 * See http://docs.oracle.com/javase/1.4.2/docs/guide/extensions/spec.html
 * for a detailed explanation of the extension mechanism in Java.
 */
public final class ExtensionValidator {

    private static final Log log = LogFactory.getLog(ExtensionValidator.class);

    /**
     * The string resources for this package.
     */
    private static final StringManager sm =
            StringManager.getManager("org.apache.catalina.util");

    private static volatile ArrayList<Extension> containerAvailableExtensions =
            null;
    private static final ArrayList<ManifestResource> containerManifestResources =
            new ArrayList<>();


    // ----------------------------------------------------- Static Initializer


    /**
     *  这个静态初始化器加载了所有Web应用程序都可用的容器级扩展名. 这种方法通过"java.ext.dirs"系统属性扫描所有可用的扩展目录. 
     *
     *  系统类路径也被扫描为JAR文件，这些文件可能包含可用的扩展名.
     */
    static {

        // 检查容器级可选包
        String systemClasspath = System.getProperty("java.class.path");

        StringTokenizer strTok = new StringTokenizer(systemClasspath,
                                                     File.pathSeparator);

        // 在类路径中建立一个列表的jar文件
        while (strTok.hasMoreTokens()) {
            String classpathItem = strTok.nextToken();
            if (classpathItem.toLowerCase(Locale.ENGLISH).endsWith(".jar")) {
                File item = new File(classpathItem);
                if (item.isFile()) {
                    try {
                        addSystemResource(item);
                    } catch (IOException e) {
                        log.error(sm.getString
                                  ("extensionValidator.failload", item), e);
                    }
                }
            }
        }

        // 将指定的文件夹添加到列表中
        addFolderList("java.ext.dirs");
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 一个Web应用程序的运行时验证.
     *
     * 此方法使用JNDI查找<code>DirContext</code>目录下的资源. 它定位web应用的/META-INF/目录下的 MANIFEST.MF文件，
     * 和WEB-INF/lib目录下的每个JAR文件的MANIFEST.MF 文件, 并创建一个<code>ManifestResorce<code>对象的<code>ArrayList</code>.
     * 这些对象随后传递给validateManifestResources 方法验证.
     *
     * @param resources Web应用程序配置的资源
     * @param context   Logger和应用路径的上下文
     *
     * @return true 如果所有需要的扩展都满足
     * @throws IOException 读取验证所需资源的错误
     */
    public static synchronized boolean validateApplication(
                                           WebResourceRoot resources,
                                           Context context)
                    throws IOException {

        String appName = context.getName();
        ArrayList<ManifestResource> appManifestResources = new ArrayList<>();

        // Web application manifest
        WebResource resource = resources.getResource("/META-INF/MANIFEST.MF");
        if (resource.isFile()) {
            try (InputStream inputStream = resource.getInputStream()) {
                Manifest manifest = new Manifest(inputStream);
                ManifestResource mre = new ManifestResource
                    (sm.getString("extensionValidator.web-application-manifest"),
                    manifest, ManifestResource.WAR);
                appManifestResources.add(mre);
            }
        }

        // Web application library manifests
        WebResource[] manifestResources =
                resources.getClassLoaderResources("/META-INF/MANIFEST.MF");
        for (WebResource manifestResource : manifestResources) {
            if (manifestResource.isFile()) {
                // Primarily used for error reporting
                String jarName = manifestResource.getURL().toExternalForm();
                Manifest jmanifest = manifestResource.getManifest();
                if (jmanifest != null) {
                    ManifestResource mre = new ManifestResource(jarName,
                            jmanifest, ManifestResource.APPLICATION);
                    appManifestResources.add(mre);
                }
            }
        }

        return validateManifestResources(appName, appManifestResources);
    }


    /**
     * 指定的系统JAR 文件是否包含 MANIFEST, 并将其添加到容器的资源清单.
     *
     * @param jarFile 要添加清单的系统JAR
     * @throws IOException Error reading JAR file
     */
    public static void addSystemResource(File jarFile) throws IOException {
        try (InputStream is = new FileInputStream(jarFile)) {
            Manifest manifest = getManifest(is);
            if (manifest != null) {
                ManifestResource mre = new ManifestResource(jarFile.getAbsolutePath(), manifest,
                        ManifestResource.SYSTEM);
                containerManifestResources.add(mre);
            }
        }
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 验证<code>ManifestResource</code>对象的<code>ArrayList</code>. 此方法需要应用程序名(这是应用程序运行时的上下文根).  
     *
     * 如果任何<code>ManifestResource</code>对象代表的扩展依赖不满足, 返回<code>false</false>.
     *
     * 这个方法还应该提供一个Web应用程序的静态验证, 如果提供必要的参数.
     *
     * @param appName 将出现在错误消息中的应用程序的名称
     * @param resources 要验证的<code>ManifestResource</code>对象的列表
     *
     * @return true 如果满足资源文件要求
     */
    private static boolean validateManifestResources(String appName,
            ArrayList<ManifestResource> resources) {
        boolean passes = true;
        int failureCount = 0;
        ArrayList<Extension> availableExtensions = null;

        Iterator<ManifestResource> it = resources.iterator();
        while (it.hasNext()) {
            ManifestResource mre = it.next();
            ArrayList<Extension> requiredList = mre.getRequiredExtensions();
            if (requiredList == null) {
                continue;
            }

            // 建立可用的扩展列表
            if (availableExtensions == null) {
                availableExtensions = buildAvailableExtensionsList(resources);
            }

            // 如果尚未构建容器级资源映射，则加载它
            if (containerAvailableExtensions == null) {
                containerAvailableExtensions
                    = buildAvailableExtensionsList(containerManifestResources);
            }

            // 遍历所需扩展名的列表
            Iterator<Extension> rit = requiredList.iterator();
            while (rit.hasNext()) {
                boolean found = false;
                Extension requiredExt = rit.next();
                // 检查应用本身的扩展
                if (availableExtensions != null) {
                    Iterator<Extension> ait = availableExtensions.iterator();
                    while (ait.hasNext()) {
                        Extension targetExt = ait.next();
                        if (targetExt.isCompatibleWith(requiredExt)) {
                            requiredExt.setFulfilled(true);
                            found = true;
                            break;
                        }
                    }
                }
                // check the container level list for the extension
                if (!found && containerAvailableExtensions != null) {
                    Iterator<Extension> cit =
                        containerAvailableExtensions.iterator();
                    while (cit.hasNext()) {
                        Extension targetExt = cit.next();
                        if (targetExt.isCompatibleWith(requiredExt)) {
                            requiredExt.setFulfilled(true);
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    // Failure
                    log.info(sm.getString(
                        "extensionValidator.extension-not-found-error",
                        appName, mre.getResourceName(),
                        requiredExt.getExtensionName()));
                    passes = false;
                    failureCount++;
                }
            }
        }

        if (!passes) {
            log.info(sm.getString(
                     "extensionValidator.extension-validation-error", appName,
                     failureCount + ""));
        }

        return passes;
    }

   /*
    * 构建这个可用扩展列表，这样我们每次迭代遍历所需扩展列表时就不必重新构建这个列表. 
    * <code>MainfestResource</code>对象的所有可用的扩展将被添加到一个在第一个依赖项列表处理过程中返回的HashMap. 
    *
    * key 是 name + 实现类版本.
    *
    * NOTE: 只有在需要检查依赖项的情况下才能构建列表(性能优化).
    *
    * @param resources <code>ManifestResource</code>对象的列表
    *
    * @return HashMap 可用扩展名的Map
    */
    private static ArrayList<Extension> buildAvailableExtensionsList(
            ArrayList<ManifestResource> resources) {

        ArrayList<Extension> availableList = null;

        Iterator<ManifestResource> it = resources.iterator();
        while (it.hasNext()) {
            ManifestResource mre = it.next();
            ArrayList<Extension> list = mre.getAvailableExtensions();
            if (list != null) {
                Iterator<Extension> values = list.iterator();
                while (values.hasNext()) {
                    Extension ext = values.next();
                    if (availableList == null) {
                        availableList = new ArrayList<>();
                        availableList.add(ext);
                    } else {
                        availableList.add(ext);
                    }
                }
            }
        }

        return availableList;
    }

    /**
     * 从JAR文件或WAR文件返回的清单
     *
     * @param inStream Input stream to a WAR or JAR file
     * @return The WAR's or JAR's manifest
     */
    private static Manifest getManifest(InputStream inStream) throws IOException {
        Manifest manifest = null;
        try (JarInputStream jin = new JarInputStream(inStream)) {
            manifest = jin.getManifest();
        }
        return manifest;
    }


    /**
     * 将指定的JAR添加到扩展列表中.
     */
    private static void addFolderList(String property) {

        // 获取扩展目录中的文件
        String extensionsDir = System.getProperty(property);
        if (extensionsDir != null) {
            StringTokenizer extensionsTok
                = new StringTokenizer(extensionsDir, File.pathSeparator);
            while (extensionsTok.hasMoreTokens()) {
                File targetDir = new File(extensionsTok.nextToken());
                if (!targetDir.isDirectory()) {
                    continue;
                }
                File[] files = targetDir.listFiles();
                if (files == null) {
                    continue;
                }
                for (int i = 0; i < files.length; i++) {
                    if (files[i].getName().toLowerCase(Locale.ENGLISH).endsWith(".jar") &&
                            files[i].isFile()) {
                        try {
                            addSystemResource(files[i]);
                        } catch (IOException e) {
                            log.error
                                (sm.getString
                                 ("extensionValidator.failload", files[i]), e);
                        }
                    }
                }
            }
        }
    }
}
