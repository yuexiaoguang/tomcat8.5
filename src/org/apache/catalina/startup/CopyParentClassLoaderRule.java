package org.apache.catalina.startup;


import java.lang.reflect.Method;

import org.apache.catalina.Container;
import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;


/**
 * <p>规则：复制<code>parentClassLoader</code>属性 property 从堆栈的下一个到最上面的项目(必须是一个 <code>Container</code>)
 * 到栈顶的项目(必须是一个 <code>Container</code>).</p>
 */
public class CopyParentClassLoaderRule extends Rule {


    // ----------------------------------------------------------- Constructors


    public CopyParentClassLoaderRule() {
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 处理xml元素的开头
     *
     * @param attributes 这个元素的属性
     *
     * @exception Exception if a processing error occurs
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {

        if (digester.getLogger().isDebugEnabled())
            digester.getLogger().debug("Copying parent class loader");
        Container child = (Container) digester.peek(0);
        Object parent = digester.peek(1);
        Method method =
            parent.getClass().getMethod("getParentClassLoader", new Class[0]);
        ClassLoader classLoader =
            (ClassLoader) method.invoke(parent, new Object[0]);
        child.setParentClassLoader(classLoader);
    }
}
