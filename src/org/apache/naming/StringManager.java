package org.apache.naming;

import java.text.MessageFormat;
import java.util.Hashtable;
import java.util.Locale;
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

    /**
     * The ResourceBundle for this StringManager.
     */
    private final ResourceBundle bundle;
    private final Locale locale;

    /**
     * 所有的访问是由调用静态getManager方法，因此每个包只有一个StringManager被创建.
     *
     * @param packageName 要为其创建StringManager的包的名称.
     */
    private StringManager(String packageName) {
        String bundleName = packageName + ".LocalStrings";
        ResourceBundle tempBundle = null;
        try {
            tempBundle = ResourceBundle.getBundle(bundleName, Locale.getDefault());
        } catch( MissingResourceException ex ) {
            // 从当前加载器尝试(相信应用的情况下)
            // 只有当使用TC5风格的类加载器结构时使用
            // where common != shared != server
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if( cl != null ) {
                try {
                    tempBundle = ResourceBundle.getBundle(
                            bundleName, Locale.getDefault(), cl);
                } catch(MissingResourceException ex2) {
                    // Ignore
                }
            }
        }
        // 获取真实的地址, 可能与请求的不同
        if (tempBundle != null) {
            locale = tempBundle.getLocale();
        } else {
            locale = null;
        }
        bundle = tempBundle;
    }

    /**
     * 从底层资源包获取一个字符串, 或者null.
     * 
     * @param key 键
     * @return 匹配<i>key</i>的资源或 null.
     * 
     * @throws IllegalArgumentException if <i>key</i> is null.
     */
    public String getString(String key) {
        if(key == null){
            String msg = "key may not have a null value";

            throw new IllegalArgumentException(msg);
        }

        String str = null;

        try {
            str = bundle.getString(key);
        } catch(MissingResourceException mre) {
            //bad: shouldn't mask an exception the following way:
            //   str = "[cannot find message associated with key '" + key + "' due to " + mre + "]";
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

    // --------------------------------------------------------------
    // STATIC SUPPORT METHODS
    // --------------------------------------------------------------

    private static final Hashtable<String, StringManager> managers =
            new Hashtable<>();

    /**
     * 获取一个特定的StringManager. 如果包的管理器已经存在, 它将被重用, 否则一个新的StringManager将创建并返回.
     *
     * @param packageName 包名
     *
     * @return 给定包关联的实例
     */
    public static final synchronized StringManager getManager(String packageName) {
        StringManager mgr = managers.get(packageName);
        if (mgr == null) {
            mgr = new StringManager(packageName);
            managers.put(packageName, mgr);
        }
        return mgr;
    }


    public static final StringManager getManager(Class<?> clazz) {
        return getManager(clazz.getPackage().getName());
    }
}
