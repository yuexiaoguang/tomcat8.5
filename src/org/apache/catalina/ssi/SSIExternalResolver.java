package org.apache.catalina.ssi;


import java.io.IOException;
import java.util.Collection;
import java.util.Date;
/**
 * SSIMediator使用的接口, 和外部交流 (通常是servlet )
 */
public interface SSIExternalResolver {
    /**
     * 添加任何外部变量到variableNames 集合
     *
     * @param variableNames
     *            the collection to add to
     */
    public void addVariableNames(Collection<String> variableNames);


    public String getVariableValue(String name);


    /**
     * 设置变量的值
     * 如果值是null, 然后变量将被删除 ( ie. 调用getVariableValue 将返回 null )
     *
     * @param name
     *            of the variable
     * @param value
     *            of the variable
     */
    public void setVariableValue(String name, String value);


    /**
     * 这有助于把SSI的东西放进回归测试中. 因为您可以使当前日期成为常量, 它使测试更容易，因为输出不会改变.
     *
     * @return the data
     */
    public Date getCurrentDate();


    public long getFileSize(String path, boolean virtual) throws IOException;


    public long getFileLastModified(String path, boolean virtual)
            throws IOException;


    public String getFileText(String path, boolean virtual) throws IOException;


    public void log(String message, Throwable throwable);
}