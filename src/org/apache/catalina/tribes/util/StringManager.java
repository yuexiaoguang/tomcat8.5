package org.apache.catalina.tribes.util;

import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;


/**
 * 一个国际化/本地化辅助类，从而减少处理ResourceBundles的麻烦和照顾消息格式化的常见情况，否则需要创建对象数组等等.
 *
 * <p>StringManager在包的基础上运行. 每个包都有一个StringManager可以创建并通过getmanager方法调用访问.
 *
 * <p>StringManager将查找包名加上"LocalStrings"后缀命名的ResourceBundle.
 * 在实践中, 这意味着将包含本地化信息，在位于类路径中的包目录的LocalStrings.properties文件.
 */
public class StringManager {

    private static int LOCALE_CACHE_SIZE = 10;

    /**
     * The ResourceBundle for this StringManager.
     */
    private final ResourceBundle bundle;
    private final Locale locale;


    /**
     * 所有的访问是由调用静态getManager方法，因此每个包只有一个StringManager被创建.
     *
     * @param packageName Name of package to create StringManager for.
     */
    private StringManager(String packageName, Locale locale) {
        String bundleName = packageName + ".LocalStrings";
        ResourceBundle bnd = null;
        try {
            bnd = ResourceBundle.getBundle(bundleName, locale);
        } catch (MissingResourceException ex) {
            // 从当前加载器尝试(相信应用的情况下)
            // 只有当使用TC5风格的类加载器结构时使用
            // where common != shared != server
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
        // 获取真实的地址, 可能与请求的不同
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
     * 从底层资源包获取一个字符串, 或者null.
     *
     * @param key to desired resource String
     *
     * @return resource String matching <i>key</i> from underlying bundle or
     *         null if not found.
     *
     * @throws IllegalArgumentException if <i>key</i> is null
     */
    public String getString(String key) {
        if (key == null){
            String msg = "key may not have a null value";
            throw new IllegalArgumentException(msg);
        }

        String str = null;

        try {
            // Avoid NPE if bundle is null and treat it like an MRE
            if (bundle != null) {
                str = bundle.getString(key);
            }
        } catch (MissingResourceException mre) {
            //bad: shouldn't mask an exception the following way:
            //   str = "[cannot find message associated with key '" + key +
            //         "' due to " + mre + "]";
            //     because it hides the fact that the String was missing
            //     from the calling code.
            //good: could just throw the exception (or wrap it in another)
            //      but that would probably cause much havoc on existing
            //      code.
            //better: consistent with container pattern to
            //      simply return null.  Calling code can then do
            //      a null check.
            str = null;
        }

        return str;
    }


    /**
     * 从底层资源包获取一个字符串, 并用给定的参数集格式化它.
     *
     * @param key  所需信息的key
     * @param args 插入到信息的值
     *
     * @return 对应的字符串或key本身.
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
     * 标识这个StringManager关联的 Locale
     *
     * @return 这个实例关联的Locale
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
     * 获取一个特定的StringManager. 如果管理器已经存在, 它将被重用, 否则一个新的StringManager将创建并返回.
     *
     * @param clazz 要检索的类
     *
     * @return 指定类的StringManager.
     */
    public static final StringManager getManager(Class<?> clazz) {
        return getManager(clazz.getPackage().getName());
    }


    /**
     * 如果包的管理器已经存在, 它将被重用, 否则一个新的StringManager将创建并返回.
     *
     * @param packageName 包名
     *
     * @return 指定包的StringManager.
     */
    public static final StringManager getManager(String packageName) {
        return getManager(packageName, Locale.getDefault());
    }


    /**
     * 如果package/Locale组合的管理器已经存在, 它将被重用, 否则一个新的StringManager将创建并返回.
     *
     * @param packageName 包名
     * @param locale      The Locale
     *
     * @return 指定包和区域的 StringManager
     */
    public static final synchronized StringManager getManager(
            String packageName, Locale locale) {

        Map<Locale,StringManager> map = managers.get(packageName);
        if (map == null) {
            /*
             * 不要扩展 HashMap 超过 LOCALE_CACHE_SIZE.
             * 膨胀发生, 当 size() 超过容量. 因此保持大小或在容量以下.
             * 插入之后执行 removeEldestEntry() , 因此用于删除的测试需要使用小于最大期望大小的一个
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
     * 检索区域列表的 StringManager. 将返回找到的第一个 StringManager.
     *
     * @param packageName 需要 StringManager 的包名
     * @param requestedLocales Locale列表
     *
     * @return 找到的 StringManager 或默认的 StringManager
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
        // Return the default
        return getManager(packageName);
    }
}
