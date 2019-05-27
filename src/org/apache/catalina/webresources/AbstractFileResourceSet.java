package org.apache.catalina.webresources;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.catalina.LifecycleException;
import org.apache.tomcat.util.compat.JrePlatform;
import org.apache.tomcat.util.http.RequestUtil;

public abstract class AbstractFileResourceSet extends AbstractResourceSet {

    protected static final String[] EMPTY_STRING_ARRAY = new String[0];

    private File fileBase;
    private String absoluteBase;
    private String canonicalBase;
    private boolean readOnly = false;

    protected AbstractFileResourceSet(String internalPath) {
        setInternalPath(internalPath);
    }

    protected final File getFileBase() {
        return fileBase;
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    protected final File file(String name, boolean mustExist) {

        if (name.equals("/")) {
            name = "";
        }
        File file = new File(fileBase, name);

        // 如果请求的名称以 '/'结尾, 如果存在，Java File API将返回匹配的文件. 这不是我们想要的，因为它与请求映射的servlet规范规则不一致.
        if (name.endsWith("/") && file.isFile()) {
            return null;
        }

        // 如果 file/dir 必须存在, 但是 file/dir 不能读取, 然后指示资源未被找到
        if (mustExist && !file.canRead()) {
            return null;
        }

        // 如果允许启用链接, 文件不必限制位于 fileBase, 所以所有进一步的检查都被禁用了.
        if (getRoot().getAllowLinking()) {
            return file;
        }

        // 为解决已知的File.getCanonicalPath()问题提供额外的Windows特定检查
        if (JrePlatform.IS_WINDOWS && isInvalidWindowsFilename(name)) {
            return null;
        }

        // 检查此文件是否位于WebResourceSet的基础目录
        String canPath = null;
        try {
            canPath = file.getCanonicalPath();
        } catch (IOException e) {
            // Ignore
        }
        if (canPath == null || !canPath.startsWith(canonicalBase)) {
            return null;
        }

        // 确保文件不在fileBase的外面. 对于标准的请求来说，这是不可能的 (请求在请求处理之前被规范化), 但是可能通过Servlet API进行一些访问 (RequestDispatcher, HTTP/2 push etc.)
        // 因此，这些检查被保留，因为一个额外的安全措施 absoluteBase 已经被规范化, 因此 absPath 也需要被规范化.
        String absPath = normalize(file.getAbsolutePath());
        if (absoluteBase.length() > absPath.length()) {
            return null;
        }

        // 从路径开始的地方删除fileBase位置, 因为这不是请求路径的一部分, 剩下的检查只适用于请求路径
        absPath = absPath.substring(absoluteBase.length());
        canPath = canPath.substring(canonicalBase.length());

        // 大小写检查
        // 规范化请求路径应该是一个精确匹配的等价规范路径. 如果不是，可能的原因包括:
        // - 不区分大小写的文件系统的情况差异
        // - Windows从文件名删除结尾的 ' ' 或 '.'
        //
        // 在所有的情况下, 这里的错误匹配导致资源未被发现
        //
        // absPath 是规范化的, 因此 canPath 也需要规范化
        // 无法更早的规范化 canPath, 因为canonicalBase 不是规范化的
        if (canPath.length() > 0) {
            canPath = normalize(canPath);
        }
        if (!canPath.equals(absPath)) {
            return null;
        }

        return file;
    }


    private boolean isInvalidWindowsFilename(String name) {
        final int len = name.length();
        if (len == 0) {
            return false;
        }
        // 无论输入长度如何，这始终比等效正则表达式快10倍.
        for (int i = 0; i < len; i++) {
            char c = name.charAt(i);
            if (c == '\"' || c == '<' || c == '>') {
                // 这些字符在Windows文件名中是不允许的，对于这些字符的文件名存在已知的问题, 当使用 File#getCanonicalPath()的时候.
                // Note: Windows文件名中不允许有其他字符, 但还不知道使用File#getCanonicalPath()时会引起什么问题.
                return true;
            }
        }
        // Windows不允许文件名以 ' ' 结尾, 除非使用特定的低级别API来创建绕过各种检查的文件.
        // 以 ' ' 结尾的文件名会导致文件, 当使用  File#getCanonicalPath() 时.
        if (name.charAt(len -1) == ' ') {
            return true;
        }
        return false;
    }


    /**
     * 返回上下文相对路径, 以 "/" 开头, 表示指定的去除了".." 和 "."的路径的规范版本.\
     * 如果指定的路径试图超出当前上下文的边界 (i.e. 太多的 ".."), 返回 <code>null</code>.
     *
     * @param path Path to be normalized
     */
    private String normalize(String path) {
        return RequestUtil.normalize(path, File.separatorChar == '\\');
    }

    @Override
    public URL getBaseUrl() {
        try {
            return getFileBase().toURI().toURL();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 默认为基于文件的资源集的NO-OP.
     */
    @Override
    public void gc() {
        // NO-OP
    }


    //-------------------------------------------------------- Lifecycle methods

    @Override
    protected void initInternal() throws LifecycleException {
        fileBase = new File(getBase(), getInternalPath());
        checkType(fileBase);

        this.absoluteBase = normalize(fileBase.getAbsolutePath());

        try {
            this.canonicalBase = fileBase.getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }


    protected abstract void checkType(File file);
}
