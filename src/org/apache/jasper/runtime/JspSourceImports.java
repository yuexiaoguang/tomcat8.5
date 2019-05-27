package org.apache.jasper.runtime;

import java.util.Set;

/**
 * EL 引擎需要访问JSP页面中使用的 import来配置 ELContext.
 * import在编译时可用，但ELContext是每页延迟创建的. 此接口在运行时公开import，以便在创建ELContext时将它们添加到ELContext中.
 */
public interface JspSourceImports {
    Set<String> getPackageImports();
    Set<String> getClassImports();
}
