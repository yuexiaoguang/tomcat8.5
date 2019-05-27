package org.apache.catalina.ssi;


import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
/**
 * 实现了服务器端 #fsize 命令
 */
public final class SSIFsize implements SSICommand {
    static final int ONE_KILOBYTE = 1024;
    static final int ONE_MEGABYTE = 1024 * 1024;


    @Override
    public long process(SSIMediator ssiMediator, String commandName,
            String[] paramNames, String[] paramValues, PrintWriter writer) {
        long lastModified = 0;
        String configErrMsg = ssiMediator.getConfigErrMsg();
        for (int i = 0; i < paramNames.length; i++) {
            String paramName = paramNames[i];
            String paramValue = paramValues[i];
            String substitutedValue = ssiMediator
                    .substituteVariables(paramValue);
            try {
                if (paramName.equalsIgnoreCase("file")
                        || paramName.equalsIgnoreCase("virtual")) {
                    boolean virtual = paramName.equalsIgnoreCase("virtual");
                    lastModified = ssiMediator.getFileLastModified(
                            substitutedValue, virtual);
                    long size = ssiMediator.getFileSize(substitutedValue,
                            virtual);
                    String configSizeFmt = ssiMediator.getConfigSizeFmt();
                    writer.write(formatSize(size, configSizeFmt));
                } else {
                    ssiMediator.log("#fsize--Invalid attribute: " + paramName);
                    writer.write(configErrMsg);
                }
            } catch (IOException e) {
                ssiMediator.log("#fsize--Couldn't get size for file: "
                        + substitutedValue, e);
                writer.write(configErrMsg);
            }
        }
        return lastModified;
    }


    public String repeat(char aChar, int numChars) {
        if (numChars < 0) {
            throw new IllegalArgumentException("Num chars can't be negative");
        }
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < numChars; i++) {
            buf.append(aChar);
        }
        return buf.toString();
    }


    public String padLeft(String str, int maxChars) {
        String result = str;
        int charsToAdd = maxChars - str.length();
        if (charsToAdd > 0) {
            result = repeat(' ', charsToAdd) + str;
        }
        return result;
    }


    //试图模拟Apache, 就像其他地方一样
    //All the 'magic' numbers are from the util_script.c Apache source file.
    protected String formatSize(long size, String format) {
        String retString = "";
        if (format.equalsIgnoreCase("bytes")) {
            DecimalFormat decimalFormat = new DecimalFormat("#,##0");
            retString = decimalFormat.format(size);
        } else {
            if (size == 0) {
                retString = "0k";
            } else if (size < ONE_KILOBYTE) {
                retString = "1k";
            } else if (size < ONE_MEGABYTE) {
                retString = Long.toString((size + 512) / ONE_KILOBYTE);
                retString += "k";
            } else if (size < 99 * ONE_MEGABYTE) {
                DecimalFormat decimalFormat = new DecimalFormat("0.0M");
                retString = decimalFormat.format(size / (double)ONE_MEGABYTE);
            } else {
                retString = Long.toString((size + (529 * ONE_KILOBYTE))
                        / ONE_MEGABYTE);
                retString += "M";
            }
            retString = padLeft(retString, 5);
        }
        return retString;
    }
}