package org.apache.jasper.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.PermissionCollection;

import org.apache.jasper.Constants;

/**
 * 加载servlet类文件的类加载器(对应于 JSP 文件) 和标签处理程序类文件(对应于标签文件).
 */
public class JasperLoader extends URLClassLoader {

    private final PermissionCollection permissionCollection;
    private final SecurityManager securityManager;

    public JasperLoader(URL[] urls, ClassLoader parent,
                        PermissionCollection permissionCollection) {
        super(urls, parent);
        this.permissionCollection = permissionCollection;
        this.securityManager = System.getSecurityManager();
    }

    /**
     * 加载指定名称的类.
     *
     * @param name 要加载的类的名称
     *
     * @exception ClassNotFoundException if the class was not found
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {

        return (loadClass(name, false));
    }

    /**
     * 加载指定名称的类, 使用以下算法搜索, 直到找到并返回类为止. 如果找不到类, 返回<code>ClassNotFoundException</code>.
     * <ul>
     * <li>调用<code>findLoadedClass(String)</code>检查类是否已经加载. 如果已经加载, 返回相同的<code>Class</code>对象.</li>
     * <li>如果<code>delegate</code>属性被设置为<code>true</code>, 调用父类加载器的<code>loadClass()</code>方法.</li>            
     * <li>调用<code>findClass()</code>在本地定义的存储库中找到这个类.</li>      
     * <li>调用父类加载器的<code>loadClass()</code>.</li>      
     * </ul>
     * 如果使用上述步骤找到类, 并且<code>resolve</code>标志是<code>true</code>, 这个方法随后调用结果Class对象的<code>resolveClass(Class)</code>方法.
     *
     * @param name 要加载的类的名称
     * @param resolve 如果是<code>true</code> 然后解析这个类
     *
     * @exception ClassNotFoundException if the class was not found
     */
    @Override
    public synchronized Class<?> loadClass(final String name, boolean resolve)
        throws ClassNotFoundException {

        Class<?> clazz = null;

        // (0) 检查以前加载的类缓存
        clazz = findLoadedClass(name);
        if (clazz != null) {
            if (resolve)
                resolveClass(clazz);
            return (clazz);
        }

        // (.5) 访问这个类的权限, 当使用一个SecurityManager的时候
        if (securityManager != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                try {
                    // 不要在默认情况下调用安全管理器, 同意那个包.
                    if (!"org.apache.jasper.runtime".equalsIgnoreCase(name.substring(0,dot))){
                        securityManager.checkPackageAccess(name.substring(0,dot));
                    }
                } catch (SecurityException se) {
                    String error = "Security Violation, attempt to use " +
                        "Restricted Class: " + name;
                    se.printStackTrace();
                    throw new ClassNotFoundException(error);
                }
            }
        }

        if( !name.startsWith(Constants.JSP_PACKAGE_NAME + '.') ) {
            //  Class 不是在 org.apache.jsp中, 因此, 让父级加载它
            clazz = getParent().loadClass(name);
            if( resolve )
                resolveClass(clazz);
            return clazz;
        }

        return findClass(name);
    }


    /**
     * 委托给父级
     */
    @Override
    public InputStream getResourceAsStream(String name) {
        InputStream is = getParent().getResourceAsStream(name);
        if (is == null) {
            URL url = findResource(name);
            if (url != null) {
                try {
                    is = url.openStream();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        return is;
    }


    /**
     * 获取一个 CodeSource的权限.
     *
     * 因为这个 ClassLoader 仅用于Web应用程序上下文中的JSP页面, 只返回预设的
     * PermissionCollection 对于Web应用程序上下文.
     *
     * @param codeSource 从中加载代码的代码源
     * @return PermissionCollection for CodeSource
     */
    @Override
    public final PermissionCollection getPermissions(CodeSource codeSource) {
        return permissionCollection;
    }
}
