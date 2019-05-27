package org.apache.catalina.ssi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.apache.catalina.util.Strftime;
import org.apache.catalina.util.URLEncoder;
import org.apache.tomcat.util.security.Escape;

/**
 * 允许不同的SSICommand实现数据共享/交流
 */
public class SSIMediator {
    protected static final String DEFAULT_CONFIG_ERR_MSG = "[an error occurred while processing this directive]";
    protected static final String DEFAULT_CONFIG_TIME_FMT = "%A, %d-%b-%Y %T %Z";
    protected static final String DEFAULT_CONFIG_SIZE_FMT = "abbrev";
    protected String configErrMsg = DEFAULT_CONFIG_ERR_MSG;
    protected String configTimeFmt = DEFAULT_CONFIG_TIME_FMT;
    protected String configSizeFmt = DEFAULT_CONFIG_SIZE_FMT;
    protected final String className = getClass().getName();
    protected final SSIExternalResolver ssiExternalResolver;
    protected final long lastModifiedDate;
    protected Strftime strftime;
    protected final SSIConditionalState conditionalState = new SSIConditionalState();


    public SSIMediator(SSIExternalResolver ssiExternalResolver,
            long lastModifiedDate) {
        this.ssiExternalResolver = ssiExternalResolver;
        this.lastModifiedDate = lastModifiedDate;
        setConfigTimeFmt(DEFAULT_CONFIG_TIME_FMT, true);
    }


    public void setConfigErrMsg(String configErrMsg) {
        this.configErrMsg = configErrMsg;
    }


    public void setConfigTimeFmt(String configTimeFmt) {
        setConfigTimeFmt(configTimeFmt, false);
    }


    public void setConfigTimeFmt(String configTimeFmt, boolean fromConstructor) {
        this.configTimeFmt = configTimeFmt;
        this.strftime = new Strftime(configTimeFmt, Locale.US);
        //变量类似 DATE_LOCAL, DATE_GMT, and LAST_MODIFIED 需要更新，当timefmt改变时. 这就是Apache SSI所做的.
        setDateVariables(fromConstructor);
    }


    public void setConfigSizeFmt(String configSizeFmt) {
        this.configSizeFmt = configSizeFmt;
    }


    public String getConfigErrMsg() {
        return configErrMsg;
    }


    public String getConfigTimeFmt() {
        return configTimeFmt;
    }


    public String getConfigSizeFmt() {
        return configSizeFmt;
    }


    public SSIConditionalState getConditionalState() {
        return conditionalState;
    }


    public Collection<String> getVariableNames() {
        Set<String> variableNames = new HashSet<>();
        //这些内置变量由中介提供(如果用户没有重写)并始终存在
        variableNames.add("DATE_GMT");
        variableNames.add("DATE_LOCAL");
        variableNames.add("LAST_MODIFIED");
        ssiExternalResolver.addVariableNames(variableNames);
        //删除该类保留的任何变量
        Iterator<String> iter = variableNames.iterator();
        while (iter.hasNext()) {
            String name = iter.next();
            if (isNameReserved(name)) {
                iter.remove();
            }
        }
        return variableNames;
    }


    public long getFileSize(String path, boolean virtual) throws IOException {
        return ssiExternalResolver.getFileSize(path, virtual);
    }


    public long getFileLastModified(String path, boolean virtual)
            throws IOException {
        return ssiExternalResolver.getFileLastModified(path, virtual);
    }


    public String getFileText(String path, boolean virtual) throws IOException {
        return ssiExternalResolver.getFileText(path, virtual);
    }


    protected boolean isNameReserved(String name) {
        return name.startsWith(className + ".");
    }


    public String getVariableValue(String variableName) {
        return getVariableValue(variableName, "none");
    }


    public void setVariableValue(String variableName, String variableValue) {
        if (!isNameReserved(variableName)) {
            ssiExternalResolver.setVariableValue(variableName, variableValue);
        }
    }


    public String getVariableValue(String variableName, String encoding) {
        String lowerCaseVariableName = variableName.toLowerCase(Locale.ENGLISH);
        String variableValue = null;
        if (!isNameReserved(lowerCaseVariableName)) {
            //试着先从外部获取它, 如果失败, 尝试获取'built-in' 值
            variableValue = ssiExternalResolver.getVariableValue(variableName);
            if (variableValue == null) {
                variableName = variableName.toUpperCase(Locale.ENGLISH);
                variableValue = ssiExternalResolver
                        .getVariableValue(className + "." + variableName);
            }
            if (variableValue != null) {
                variableValue = encode(variableValue, encoding);
            }
        }
        return variableValue;
    }


    /**
     * 将变量替换应用于指定的字符串，并返回新的解析字符串.
     * 
     * @param val The value which should be checked
     * @return the value after variable substitution
     */
    public String substituteVariables(String val) {
        // 如果它没有变量引用，那么就不需要做任何工作
        if (val.indexOf('$') < 0 && val.indexOf('&') < 0) return val;

        // HTML decoding
        val = val.replace("&lt;", "<");
        val = val.replace("&gt;", ">");
        val = val.replace("&quot;", "\"");
        val = val.replace("&amp;", "&");

        StringBuilder sb = new StringBuilder(val);
        int charStart = sb.indexOf("&#");
        while (charStart > -1) {
            int charEnd = sb.indexOf(";", charStart);
            if (charEnd > -1) {
                char c = (char) Integer.parseInt(
                        sb.substring(charStart + 2, charEnd));
                sb.delete(charStart, charEnd + 1);
                sb.insert(charStart, c);
                charStart = sb.indexOf("&#");
            } else {
                break;
            }
        }

        for (int i = 0; i < sb.length();) {
            // Find the next $
            for (; i < sb.length(); i++) {
                if (sb.charAt(i) == '$') {
                    i++;
                    break;
                }
            }
            if (i == sb.length()) break;
            // Check to see if the $ is escaped
            if (i > 1 && sb.charAt(i - 2) == '\\') {
                sb.deleteCharAt(i - 2);
                i--;
                continue;
            }
            int nameStart = i;
            int start = i - 1;
            int end = -1;
            int nameEnd = -1;
            char endChar = ' ';
            // Check for {} wrapped var
            if (sb.charAt(i) == '{') {
                nameStart++;
                endChar = '}';
            }
            // Find the end of the var reference
            for (; i < sb.length(); i++) {
                if (sb.charAt(i) == endChar) break;
            }
            end = i;
            nameEnd = end;
            if (endChar == '}') end++;
            // 现在应该有足够的空间提取var名称了
            String varName = sb.substring(nameStart, nameEnd);
            String value = getVariableValue(varName);
            if (value == null) value = "";
            // Replace the var name with its value
            sb.replace(start, end, value);
            // Start searching for the next $ after the value
            // that was just substituted.
            i = start + value.length();
        }
        return sb.toString();
    }


    protected String formatDate(Date date, TimeZone timeZone) {
        String retVal;
        if (timeZone != null) {
            //暂时改变函数. 由于SSIMediator本身是单线程的, 这不是一个问题
            TimeZone oldTimeZone = strftime.getTimeZone();
            strftime.setTimeZone(timeZone);
            retVal = strftime.format(date);
            strftime.setTimeZone(oldTimeZone);
        } else {
            retVal = strftime.format(date);
        }
        return retVal;
    }


    protected String encode(String value, String encoding) {
        String retVal = null;
        if (encoding.equalsIgnoreCase("url")) {
            retVal = URLEncoder.DEFAULT.encode(value, StandardCharsets.UTF_8);
        } else if (encoding.equalsIgnoreCase("none")) {
            retVal = value;
        } else if (encoding.equalsIgnoreCase("entity")) {
            retVal = Escape.htmlElementContent(value);
        } else {
            //This shouldn't be possible
            throw new IllegalArgumentException("Unknown encoding: " + encoding);
        }
        return retVal;
    }


    public void log(String message) {
        ssiExternalResolver.log(message, null);
    }


    public void log(String message, Throwable throwable) {
        ssiExternalResolver.log(message, throwable);
    }


    protected void setDateVariables(boolean fromConstructor) {
        boolean alreadySet = ssiExternalResolver.getVariableValue(className
                + ".alreadyset") != null;
        //如果我们从构造函数中调用，跳过这个, 这个已经设置好了
        if (!(fromConstructor && alreadySet)) {
            ssiExternalResolver.setVariableValue(className + ".alreadyset",
                    "true");
            Date date = new Date();
            TimeZone timeZone = TimeZone.getTimeZone("GMT");
            String retVal = formatDate(date, timeZone);
            //如果设置日期变量, 希望将它们从用户定义的变量列表中删除, 因为这就是Apache所做的
            setVariableValue("DATE_GMT", null);
            ssiExternalResolver.setVariableValue(className + ".DATE_GMT",
                    retVal);
            retVal = formatDate(date, null);
            setVariableValue("DATE_LOCAL", null);
            ssiExternalResolver.setVariableValue(className + ".DATE_LOCAL",
                    retVal);
            retVal = formatDate(new Date(lastModifiedDate), null);
            setVariableValue("LAST_MODIFIED", null);
            ssiExternalResolver.setVariableValue(className + ".LAST_MODIFIED",
                    retVal);
        }
    }
}