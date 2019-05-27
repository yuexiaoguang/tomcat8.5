package org.apache.jasper.runtime;

import java.util.Map;

/**
 * 跟踪源文件依赖关系的接口, 为了编译过时的页面. 用于
 * 1) 由页面指令包含的文件
 * 2) jsp:config中的include-prelude和include-coda包含的文件
 * 3) 标签文件和引用文件
 * 4) TLD引用
 */
public interface JspSourceDependent {

   /**
    * 返回当前页面具有源依赖项的文件名列表.
    * 
    * @return the map of dependent resources
    */
    public Map<String,Long> getDependants();

}
