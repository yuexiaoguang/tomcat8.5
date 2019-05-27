package org.apache.jasper.compiler;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.jasper.runtime.ExceptionUtils;

/**
 * 负责将错误代码转换为相应的本地化错误消息.
 */
public class Localizer {

    private static ResourceBundle bundle;

    static {
        try {
            bundle = ResourceBundle.getBundle(
                    "org.apache.jasper.resources.LocalStrings");
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            t.printStackTrace();
        }
    }

    /*
     * 返回与给定错误代码相对应的本地化错误消息.
     *
     * 如果给定的错误代码在资源包中没有为本地化错误消息定义, 它用作错误消息.
     *
     * @param errCode 要本地化错误代码
     * 
     * @return 本地化错误信息
     */
    public static String getMessage(String errCode) {
        String errMsg = errCode;
        try {
            errMsg = bundle.getString(errCode);
        } catch (MissingResourceException e) {
        }
        return errMsg;
    }

    /*
     * 返回与给定错误代码相对应的本地化错误消息.
     *
     * 如果给定的错误代码在资源包中没有为本地化错误消息定义, 它用作错误消息.
     *
     * @param errCode 要本地化错误代码
     * @param arg 参数替换的参数
     *
     * @return 本地化的错误信息
     */
    public static String getMessage(String errCode, String arg) {
        return getMessage(errCode, new Object[] {arg});
    }

    /*
     * 返回与给定错误代码相对应的本地化错误消息.
     *
     * 如果给定的错误代码在资源包中没有为本地化错误消息定义, 它用作错误消息.
     *
     * @param errCode 要本地化的错误代码
     * @param arg1 参数替换的第一个参数
     * @param arg2 参数替换的第二个参数
     *
     * @return 本地化的错误信息
     */
    public static String getMessage(String errCode, String arg1, String arg2) {
        return getMessage(errCode, new Object[] {arg1, arg2});
    }

    /*
     * 返回与给定错误代码相对应的本地化错误消息.
     *
     * 如果给定的错误代码在资源包中没有为本地化错误消息定义, 它用作错误消息.
     *
     * @param errCode 要本地化的错误代码
     * @param arg1 参数替换的第一个参数
     * @param arg2 参数替换的第二个参数
     * @param arg3 参数替换的第三个参数
     *
     * @return 本地化的错误信息
     */
    public static String getMessage(String errCode, String arg1, String arg2,
                                    String arg3) {
        return getMessage(errCode, new Object[] {arg1, arg2, arg3});
    }

    /*
     * 返回与给定错误代码相对应的本地化错误消息.
     *
     * 如果给定的错误代码在资源包中没有为本地化错误消息定义, 它用作错误消息.
     *
     * @param errCode 要本地化的错误代码
     * @param arg1 参数替换的第一个参数
     * @param arg2 参数替换的第二个参数
     * @param arg3 参数替换的第三个参数
     * @param arg4 参数替换的第四个参数
     *
     * @return 本地化的错误信息
     */
    public static String getMessage(String errCode, String arg1, String arg2,
                                    String arg3, String arg4) {
        return getMessage(errCode, new Object[] {arg1, arg2, arg3, arg4});
    }

    /*
     * 返回与给定错误代码相对应的本地化错误消息.
     *
     * 如果给定的错误代码在资源包中没有为本地化错误消息定义, 它用作错误消息.
     *
     * @param errCode 要本地化的错误代码
     * @param args 参数替换的参数
     *
     * @return 本地化的错误信息
     */
    public static String getMessage(String errCode, Object[] args) {
        String errMsg = errCode;
        try {
            errMsg = bundle.getString(errCode);
            if (args != null && args.length > 0) {
                MessageFormat formatter = new MessageFormat(errMsg);
                errMsg = formatter.format(args);
            }
        } catch (MissingResourceException e) {
        }

        return errMsg;
    }
}
