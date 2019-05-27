package org.apache.tomcat;

public interface JarScanFilter {

    /**
     * @param jarScanType   当前正在执行的JAR扫描类型
     * @param jarName       要检查JAR文件的名称（没有任何路径信息）以查看它是否应包含在结果中
     * 
     * @return <code>true</code> 如果应该在结果中返回JAR,
     *             <code>false</code>如果应该排除
     */
    boolean check(JarScanType jarScanType, String jarName);
}
