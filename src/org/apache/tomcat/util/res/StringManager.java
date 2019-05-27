package org.apache.tomcat.util.res;

import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 一个国际化/本地化助手类，它减少了处理ResourceBundles的麻烦，并处理消息格式化的常见情况，否则需要创建对象数组等.
 *
 * <p>StringManager以包为基础进行操作. 可以通过getManager方法调用来创建和访问每个包的一个StringManager.
 *
 * <p>StringManager将查找一个ResourceBundle，由给定的包名加上 "LocalStrings"的后缀命名.
 * 在实践中, 这意味着本地化信息将包含在类路径的包目录中的LocalStrings.properties文件中.
 *
 * <p>请参阅java.util.ResourceBundle的文档以获取更多信息.
 */
public class StringManager {

    private static int LOCALE_CACHE_SIZE = 10;

    /**
     * 这个StringManager的 ResourceBundle.
     */
    private final ResourceBundle bundle;
    private final Locale locale;


    /**
     * 为给定包创建新的StringManager.
     * 这是一个私有方法，对它的所有访问都由静态getManager方法调用仲裁，以便每个包只创建一个StringManager.
     *
     * @param packageName 用于创建StringManager的包名称.
     */
    private StringManager(String packageName, Locale locale) {
        String bundleName = packageName + ".LocalStrings";
        ResourceBundle bnd = null;
        try {
            bnd = ResourceBundle.getBundle(bundleName, locale);
        } catch (MissingResourceException ex) {
            // 从当前加载器尝试 (这是可信应用程序的情况)
            // 只需使用TC5风格的类加载器结构即可
            // common != shared != server
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) {
                try {
                    bnd = ResourceBundle.getBundle(bundleName, locale, cl);
                } catch (MissingResourceException ex2) {
                    // Ignore
                }
            }
        }
        bundle = bnd;
        // 获取实际区域, 可能与请求的不同
        if (bundle != null) {
            Locale bundleLocale = bundle.getLocale();
            if (bundleLocale.equals(Locale.ROOT)) {
                this.locale = Locale.ENGLISH;
            } else {
                this.locale = bundleLocale;
            }
        } else {
            this.locale = null;
        }
    }


    /**
     * 如果没有找到字符串，则从基础资源包中获取字符串或返回null.
     *
     * @param key 所需资源字符串的键
     *
     * @return 从底层包匹配<i>key</i>的资源字符串, 或null 如果未找到.
     *
     * @throws IllegalArgumentException 如果<i>key</i>是 null
     */
    public String getString(String key) {
        if (key == null){
            String msg = "key may not have a null value";
            throw new IllegalArgumentException(msg);
        }

        String str = null;

        try {
            // 如果包为null，则避免NPE并将其视为MRE
            if (bundle != null) {
                str = bundle.getString(key);
            }
        } catch (MissingResourceException mre) {
            //bad: 不应该用下面的方式掩盖异常:
            //   str = "[cannot find message associated with key '" + key +
            //         "' due to " + mre + "]";
            //     因为它隐藏了从调用代码中丢失字符串的事实.
            //good: 可以抛出异常 (或者把它包装在另一个中), 但这可能会对现有代码造成严重破坏.
            //better: 与容器模式一致，只返回null.  然后调用代码可以进行null校验.
            str = null;
        }

        return str;
    }


    /**
     * 从基础资源包中获取字符串，并将其与给定的参数集格式化.
     *
     * @param key  所需消息的密钥
     * @param args 插入消息的值
     *
     * @return 如果未找到密钥，则使用提供的参数或密钥格式化请求字符串.
     */
    public String getString(final String key, final Object... args) {
        String value = getString(key);
        if (value == null) {
            value = key;
        }

        MessageFormat mf = new MessageFormat(value);
        mf.setLocale(locale);
        return mf.format(args, new StringBuffer(), null).toString();
    }


    /**
     * 标识此StringManager关联的Locale.
     *
     * @return 与StringManager关联的Locale
     */
    public Locale getLocale() {
        return locale;
    }


    // --------------------------------------------------------------
    // STATIC SUPPORT METHODS
    // --------------------------------------------------------------

    private static final Map<String, Map<Locale,StringManager>> managers =
            new Hashtable<>();


    /**
     * 为给定的类获取StringManager.
     * StringManager将返回该类所在的包. 如果那个包的管理者已经存在, 它将被重复使用, 否则将创建并返回新的StringManager.
     *
     * @param clazz 要检索StringManager的类
     *
     * @return 与提供类的包关联的实例
     */
    public static final StringManager getManager(Class<?> clazz) {
        return getManager(clazz.getPackage().getName());
    }


    /**
     * 获取特定软件包的StringManager.
     * 如果一个包的管理者已经存在, 它将被重复使用, 否则将创建并返回新的StringManager.
     *
     * @param packageName 包名
     *
     * @return 与给定包和默认Locale相关联的实例
     */
    public static final StringManager getManager(String packageName) {
        return getManager(packageName, Locale.getDefault());
    }


    /**
     * 获取特定包和Locale的管理器.
     * 如果已经存在包/Locale的管理器, 它将被重复使用, 否则将创建并返回新的StringManager.
     *
     * @param packageName 包名
     * @param locale      Locale
     *
     * @return 与给定包和Locale相关联的实例
     */
    public static final synchronized StringManager getManager(
            String packageName, Locale locale) {

        Map<Locale,StringManager> map = managers.get(packageName);
        if (map == null) {
            /*
             * 不希望将HashMap扩展到LOCALE_CACHE_SIZE之外.
             * 当size()超过容量时发生扩展. 因此保持在大小以下或容量以下.
             * removeEldestEntry() 在插入后执行, 因此用于删除的测试需要使用小于最大期望大小的一个
             */
            map = new LinkedHashMap<Locale,StringManager>(LOCALE_CACHE_SIZE, 1, true) {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<Locale,StringManager> eldest) {
                    if (size() > (LOCALE_CACHE_SIZE - 1)) {
                        return true;
                    }
                    return false;
                }
            };
            managers.put(packageName, map);
        }

        StringManager mgr = map.get(locale);
        if (mgr == null) {
            mgr = new StringManager(packageName, locale);
            map.put(locale, mgr);
        }
        return mgr;
    }


    /**
     * 为Locale列表检索StringManager. 将返回找到的第一个 StringManager.
     *
     * @param packageName      请求的StringManager的包
     * @param requestedLocales Locale列表
     *
     * @return 找到的StringManager或默认的 StringManager
     */
    public static StringManager getManager(String packageName,
            Enumeration<Locale> requestedLocales) {
        while (requestedLocales.hasMoreElements()) {
            Locale locale = requestedLocales.nextElement();
            StringManager result = getManager(packageName, locale);
            if (result.getLocale().equals(locale)) {
                return result;
            }
        }
        // 返回默认的
        return getManager(packageName);
    }
}
