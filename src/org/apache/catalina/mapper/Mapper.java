package org.apache.catalina.mapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.Wrapper;
import org.apache.catalina.servlet4preview.http.MappingMatch;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.Ascii;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.res.StringManager;

/**
 * Mapper, 实现了servlet API映射规则 (这是从HTTP规则中派生出来的).
 */
public final class Mapper {


    private static final Log log = LogFactory.getLog(Mapper.class);

    private static final StringManager sm = StringManager.getManager(Mapper.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * 虚拟主机定义.
     */
    volatile MappedHost[] hosts = new MappedHost[0];


    /**
     * 默认主机名.
     */
    private String defaultHostName = null;
    private volatile MappedHost defaultHost = null;


    /**
     * 从Context 对象到Context 版本的映射, 支持RequestDispatcher 映射.
     */
    private final Map<Context, ContextVersion> contextObjectToContextVersionMap =
            new ConcurrentHashMap<>();


    // --------------------------------------------------------- Public Methods

    /**
     * 设置默认主机.
     *
     * @param defaultHostName 默认主机名
     */
    public synchronized void setDefaultHostName(String defaultHostName) {
        this.defaultHostName = renameWildcardHost(defaultHostName);
        if (this.defaultHostName == null) {
            defaultHost = null;
        } else {
            defaultHost = exactFind(hosts, this.defaultHostName);
        }
    }


    /**
     * 向映射器添加新主机.
     *
     * @param name 虚拟主机名
     * @param aliases 虚拟主机别名
     * @param host 主机对象
     */
    public synchronized void addHost(String name, String[] aliases,
                                     Host host) {
        name = renameWildcardHost(name);
        MappedHost[] newHosts = new MappedHost[hosts.length + 1];
        MappedHost newHost = new MappedHost(name, host);
        if (insertMap(hosts, newHosts, newHost)) {
            hosts = newHosts;
            if (newHost.name.equals(defaultHostName)) {
                defaultHost = newHost;
            }
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("mapper.addHost.success", name));
            }
        } else {
            MappedHost duplicate = hosts[find(hosts, name)];
            if (duplicate.object == host) {
                // 主机已在映射器中注册.
                // E.g. it might have been added by addContextVersion()
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("mapper.addHost.sameHost", name));
                }
                newHost = duplicate;
            } else {
                log.error(sm.getString("mapper.duplicateHost", name,
                        duplicate.getRealHostName()));
                // 不要添加别名, 因为 removeHost(hostName) 无法移除它们
                return;
            }
        }
        List<MappedHost> newAliases = new ArrayList<>(aliases.length);
        for (String alias : aliases) {
            alias = renameWildcardHost(alias);
            MappedHost newAlias = new MappedHost(alias, newHost);
            if (addHostAliasImpl(newAlias)) {
                newAliases.add(newAlias);
            }
        }
        newHost.addAliases(newAliases);
    }


    /**
     * 从映射器中删除主机.
     *
     * @param name 虚拟主机名
     */
    public synchronized void removeHost(String name) {
        name = renameWildcardHost(name);
        // Find and remove the old host
        MappedHost host = exactFind(hosts, name);
        if (host == null || host.isAlias()) {
            return;
        }
        MappedHost[] newHosts = hosts.clone();
        // 删除实际主机及其所有别名
        int j = 0;
        for (int i = 0; i < newHosts.length; i++) {
            if (newHosts[i].getRealHost() != host) {
                newHosts[j++] = newHosts[i];
            }
        }
        hosts = Arrays.copyOf(newHosts, j);
    }

    /**
     * 向现有主机添加别名.
     * 
     * @param name  主机名
     * @param alias 要添加的别名
     */
    public synchronized void addHostAlias(String name, String alias) {
        MappedHost realHost = exactFind(hosts, name);
        if (realHost == null) {
            // 不应该为不存在的主机添加别名，只是以防万一...
            return;
        }
        alias = renameWildcardHost(alias);
        MappedHost newAlias = new MappedHost(alias, realHost);
        if (addHostAliasImpl(newAlias)) {
            realHost.addAlias(newAlias);
        }
    }

    private synchronized boolean addHostAliasImpl(MappedHost newAlias) {
        MappedHost[] newHosts = new MappedHost[hosts.length + 1];
        if (insertMap(hosts, newHosts, newAlias)) {
            hosts = newHosts;
            if (newAlias.name.equals(defaultHostName)) {
                defaultHost = newAlias;
            }
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("mapper.addHostAlias.success",
                        newAlias.name, newAlias.getRealHostName()));
            }
            return true;
        } else {
            MappedHost duplicate = hosts[find(hosts, newAlias.name)];
            if (duplicate.getRealHost() == newAlias.getRealHost()) {
                // 同一主机的重复别名.
                // 无害的冗余. E.g.
                // <Host name="localhost"><Alias>localhost</Alias></Host>
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("mapper.addHostAlias.sameHost",
                            newAlias.name, newAlias.getRealHostName()));
                }
                return false;
            }
            log.error(sm.getString("mapper.duplicateHostAlias", newAlias.name,
                    newAlias.getRealHostName(), duplicate.getRealHostName()));
            return false;
        }
    }

    /**
     * 删除主机别名
     * 
     * @param alias 要删除的别名
     */
    public synchronized void removeHostAlias(String alias) {
        alias = renameWildcardHost(alias);
        // Find and remove the alias
        MappedHost hostMapping = exactFind(hosts, alias);
        if (hostMapping == null || !hostMapping.isAlias()) {
            return;
        }
        MappedHost[] newHosts = new MappedHost[hosts.length - 1];
        if (removeMap(hosts, newHosts, alias)) {
            hosts = newHosts;
            hostMapping.getRealHost().removeAlias(hostMapping);
        }

    }

    /**
     * 替换<code>realHost</code>中的 {@link MappedHost#contextList}字段，及其所有的别名.
     */
    private void updateContextList(MappedHost realHost,
            ContextList newContextList) {

        realHost.contextList = newContextList;
        for (MappedHost alias : realHost.getAliases()) {
            alias.contextList = newContextList;
        }
    }

    /**
     * 添加一个 Context到现有的 Host.
     *
     * @param hostName 此上下文所属的虚拟主机名
     * @param host Host object
     * @param path Context path
     * @param version Context version
     * @param context Context object
     * @param welcomeResources 为该上下文定义的欢迎文件
     * @param resources 上下文的静态资源
     * @param wrappers 包装映射的信息
     */
    public void addContextVersion(String hostName, Host host, String path,
            String version, Context context, String[] welcomeResources,
            WebResourceRoot resources, Collection<WrapperMappingInfo> wrappers) {

        hostName = renameWildcardHost(hostName);

        MappedHost mappedHost  = exactFind(hosts, hostName);
        if (mappedHost == null) {
            addHost(hostName, new String[0], host);
            mappedHost = exactFind(hosts, hostName);
            if (mappedHost == null) {
                log.error("No host found: " + hostName);
                return;
            }
        }
        if (mappedHost.isAlias()) {
            log.error("No host found: " + hostName);
            return;
        }
        int slashCount = slashCount(path);
        synchronized (mappedHost) {
            ContextVersion newContextVersion = new ContextVersion(version,
                    path, slashCount, context, resources, welcomeResources);
            if (wrappers != null) {
                addWrappers(newContextVersion, wrappers);
            }

            ContextList contextList = mappedHost.contextList;
            MappedContext mappedContext = exactFind(contextList.contexts, path);
            if (mappedContext == null) {
                mappedContext = new MappedContext(path, newContextVersion);
                ContextList newContextList = contextList.addContext(
                        mappedContext, slashCount);
                if (newContextList != null) {
                    updateContextList(mappedHost, newContextList);
                    contextObjectToContextVersionMap.put(context, newContextVersion);
                }
            } else {
                ContextVersion[] contextVersions = mappedContext.versions;
                ContextVersion[] newContextVersions = new ContextVersion[contextVersions.length + 1];
                if (insertMap(contextVersions, newContextVersions,
                        newContextVersion)) {
                    mappedContext.versions = newContextVersions;
                    contextObjectToContextVersionMap.put(context, newContextVersion);
                } else {
                    // Re-registration after Context.reload()
                    // Replace ContextVersion with the new one
                    int pos = find(contextVersions, version);
                    if (pos >= 0 && contextVersions[pos].name.equals(version)) {
                        contextVersions[pos] = newContextVersion;
                        contextObjectToContextVersionMap.put(context, newContextVersion);
                    }
                }
            }
        }
    }


    /**
     * 从现有主机中删除上下文.
     *
     * @param ctxt      实际的上下文
     * @param hostName  此上下文所属的虚拟主机名
     * @param path      Context path
     * @param version   Context version
     */
    public void removeContextVersion(Context ctxt, String hostName,
            String path, String version) {

        hostName = renameWildcardHost(hostName);
        contextObjectToContextVersionMap.remove(ctxt);

        MappedHost host = exactFind(hosts, hostName);
        if (host == null || host.isAlias()) {
            return;
        }

        synchronized (host) {
            ContextList contextList = host.contextList;
            MappedContext context = exactFind(contextList.contexts, path);
            if (context == null) {
                return;
            }

            ContextVersion[] contextVersions = context.versions;
            ContextVersion[] newContextVersions =
                new ContextVersion[contextVersions.length - 1];
            if (removeMap(contextVersions, newContextVersions, version)) {
                if (newContextVersions.length == 0) {
                    // Remove the context
                    ContextList newContextList = contextList.removeContext(path);
                    if (newContextList != null) {
                        updateContextList(host, newContextList);
                    }
                } else {
                    context.versions = newContextVersions;
                }
            }
        }
    }


    /**
     * 标记上下文被重新加载.
     * 执行此状态的反转, 通过调用<code>addContextVersion(...)</code>, 当上下文启动时.
     *
     * @param ctxt      实际的上下文
     * @param hostName  此上下文所属的虚拟主机名
     * @param contextPath Context path
     * @param version   Context version
     */
    public void pauseContextVersion(Context ctxt, String hostName,
            String contextPath, String version) {
        hostName = renameWildcardHost(hostName);
        ContextVersion contextVersion = findContextVersion(hostName,
                contextPath, version, true);
        if (contextVersion == null || !ctxt.equals(contextVersion.object)) {
            return;
        }
        contextVersion.markPaused();
    }


    private ContextVersion findContextVersion(String hostName,
            String contextPath, String version, boolean silent) {
        MappedHost host = exactFind(hosts, hostName);
        if (host == null || host.isAlias()) {
            if (!silent) {
                log.error("No host found: " + hostName);
            }
            return null;
        }
        MappedContext context = exactFind(host.contextList.contexts,
                contextPath);
        if (context == null) {
            if (!silent) {
                log.error("No context found: " + contextPath);
            }
            return null;
        }
        ContextVersion contextVersion = exactFind(context.versions, version);
        if (contextVersion == null) {
            if (!silent) {
                log.error("No context version found: " + contextPath + " "
                        + version);
            }
            return null;
        }
        return contextVersion;
    }


    public void addWrapper(String hostName, String contextPath, String version,
                           String path, Wrapper wrapper, boolean jspWildCard,
                           boolean resourceOnly) {
        hostName = renameWildcardHost(hostName);
        ContextVersion contextVersion = findContextVersion(hostName,
                contextPath, version, false);
        if (contextVersion == null) {
            return;
        }
        addWrapper(contextVersion, path, wrapper, jspWildCard, resourceOnly);
    }

    public void addWrappers(String hostName, String contextPath,
            String version, Collection<WrapperMappingInfo> wrappers) {
        hostName = renameWildcardHost(hostName);
        ContextVersion contextVersion = findContextVersion(hostName,
                contextPath, version, false);
        if (contextVersion == null) {
            return;
        }
        addWrappers(contextVersion, wrappers);
    }

    /**
     * 添加wrapper到指定的上下文.
     *
     * @param contextVersion 添加wrapper的上下文
     * @param wrappers Information on wrapper mappings
     */
    private void addWrappers(ContextVersion contextVersion,
            Collection<WrapperMappingInfo> wrappers) {
        for (WrapperMappingInfo wrapper : wrappers) {
            addWrapper(contextVersion, wrapper.getMapping(),
                    wrapper.getWrapper(), wrapper.isJspWildCard(),
                    wrapper.isResourceOnly());
        }
    }

    /**
     * Adds a wrapper to the given context.
     *
     * @param context The context to which to add the wrapper
     * @param path Wrapper mapping
     * @param wrapper The Wrapper object
     * @param jspWildCard true: 如果包装器对应于JspServlet，映射路径包含通配符; 否则false
     * @param resourceOnly true: 如果这个包装器总是希望物理资源存在(例如一个 JSP)
     */
    protected void addWrapper(ContextVersion context, String path,
            Wrapper wrapper, boolean jspWildCard, boolean resourceOnly) {

        synchronized (context) {
            if (path.endsWith("/*")) {
                // Wildcard wrapper
                String name = path.substring(0, path.length() - 2);
                MappedWrapper newWrapper = new MappedWrapper(name, wrapper,
                        jspWildCard, resourceOnly);
                MappedWrapper[] oldWrappers = context.wildcardWrappers;
                MappedWrapper[] newWrappers = new MappedWrapper[oldWrappers.length + 1];
                if (insertMap(oldWrappers, newWrappers, newWrapper)) {
                    context.wildcardWrappers = newWrappers;
                    int slashCount = slashCount(newWrapper.name);
                    if (slashCount > context.nesting) {
                        context.nesting = slashCount;
                    }
                }
            } else if (path.startsWith("*.")) {
                // Extension wrapper
                String name = path.substring(2);
                MappedWrapper newWrapper = new MappedWrapper(name, wrapper,
                        jspWildCard, resourceOnly);
                MappedWrapper[] oldWrappers = context.extensionWrappers;
                MappedWrapper[] newWrappers =
                    new MappedWrapper[oldWrappers.length + 1];
                if (insertMap(oldWrappers, newWrappers, newWrapper)) {
                    context.extensionWrappers = newWrappers;
                }
            } else if (path.equals("/")) {
                // Default wrapper
                MappedWrapper newWrapper = new MappedWrapper("", wrapper,
                        jspWildCard, resourceOnly);
                context.defaultWrapper = newWrapper;
            } else {
                // Exact wrapper
                final String name;
                if (path.length() == 0) {
                    // Context Root映射的特殊情况，被视为精确匹配
                    name = "/";
                } else {
                    name = path;
                }
                MappedWrapper newWrapper = new MappedWrapper(name, wrapper,
                        jspWildCard, resourceOnly);
                MappedWrapper[] oldWrappers = context.exactWrappers;
                MappedWrapper[] newWrappers = new MappedWrapper[oldWrappers.length + 1];
                if (insertMap(oldWrappers, newWrappers, newWrapper)) {
                    context.exactWrappers = newWrappers;
                }
            }
        }
    }


    /**
     * 从现有上下文中移除包装器.
     *
     * @param hostName    Virtual host name this wrapper belongs to
     * @param contextPath Context path this wrapper belongs to
     * @param version     Context version this wrapper belongs to
     * @param path        Wrapper mapping
     */
    public void removeWrapper(String hostName, String contextPath,
            String version, String path) {
        hostName = renameWildcardHost(hostName);
        ContextVersion contextVersion = findContextVersion(hostName,
                contextPath, version, true);
        if (contextVersion == null || contextVersion.isPaused()) {
            return;
        }
        removeWrapper(contextVersion, path);
    }

    protected void removeWrapper(ContextVersion context, String path) {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("mapper.removeWrapper", context.name, path));
        }

        synchronized (context) {
            if (path.endsWith("/*")) {
                // Wildcard wrapper
                String name = path.substring(0, path.length() - 2);
                MappedWrapper[] oldWrappers = context.wildcardWrappers;
                if (oldWrappers.length == 0) {
                    return;
                }
                MappedWrapper[] newWrappers =
                    new MappedWrapper[oldWrappers.length - 1];
                if (removeMap(oldWrappers, newWrappers, name)) {
                    // Recalculate nesting
                    context.nesting = 0;
                    for (int i = 0; i < newWrappers.length; i++) {
                        int slashCount = slashCount(newWrappers[i].name);
                        if (slashCount > context.nesting) {
                            context.nesting = slashCount;
                        }
                    }
                    context.wildcardWrappers = newWrappers;
                }
            } else if (path.startsWith("*.")) {
                // Extension wrapper
                String name = path.substring(2);
                MappedWrapper[] oldWrappers = context.extensionWrappers;
                if (oldWrappers.length == 0) {
                    return;
                }
                MappedWrapper[] newWrappers =
                    new MappedWrapper[oldWrappers.length - 1];
                if (removeMap(oldWrappers, newWrappers, name)) {
                    context.extensionWrappers = newWrappers;
                }
            } else if (path.equals("/")) {
                // Default wrapper
                context.defaultWrapper = null;
            } else {
                // Exact wrapper
                String name;
                if (path.length() == 0) {
                    // Context Root映射的特殊情况，被视为精确匹配
                    name = "/";
                } else {
                    name = path;
                }
                MappedWrapper[] oldWrappers = context.exactWrappers;
                if (oldWrappers.length == 0) {
                    return;
                }
                MappedWrapper[] newWrappers =
                    new MappedWrapper[oldWrappers.length - 1];
                if (removeMap(oldWrappers, newWrappers, name)) {
                    context.exactWrappers = newWrappers;
                }
            }
        }
    }


    /**
     * 在给定上下文中添加欢迎文件.
     *
     * @param hostName    可以找到给定上下文的主机
     * @param contextPath 给定上下文的路径
     * @param version     给定上下文的版本
     * @param welcomeFile 添加欢迎文件
     */
    public void addWelcomeFile(String hostName, String contextPath, String version,
            String welcomeFile) {
        hostName = renameWildcardHost(hostName);
        ContextVersion contextVersion = findContextVersion(hostName, contextPath, version, false);
        if (contextVersion == null) {
            return;
        }
        int len = contextVersion.welcomeResources.length + 1;
        String[] newWelcomeResources = new String[len];
        System.arraycopy(contextVersion.welcomeResources, 0, newWelcomeResources, 0, len - 1);
        newWelcomeResources[len - 1] = welcomeFile;
        contextVersion.welcomeResources = newWelcomeResources;
    }


    /**
     * 从给定上下文中删除欢迎文件.
     *
     * @param hostName    可以找到给定上下文的主机
     * @param contextPath 给定上下文的路径
     * @param version     给定上下文的版本
     * @param welcomeFile 要删除的欢迎文件
     */
    public void removeWelcomeFile(String hostName, String contextPath,
            String version, String welcomeFile) {
        hostName = renameWildcardHost(hostName);
        ContextVersion contextVersion = findContextVersion(hostName, contextPath, version, false);
        if (contextVersion == null || contextVersion.isPaused()) {
            return;
        }
        int match = -1;
        for (int i = 0; i < contextVersion.welcomeResources.length; i++) {
            if (welcomeFile.equals(contextVersion.welcomeResources[i])) {
                match = i;
                break;
            }
        }
        if (match > -1) {
            int len = contextVersion.welcomeResources.length - 1;
            String[] newWelcomeResources = new String[len];
            System.arraycopy(contextVersion.welcomeResources, 0, newWelcomeResources, 0, match);
            if (match < len) {
                System.arraycopy(contextVersion.welcomeResources, match + 1,
                        newWelcomeResources, match, len - match);
            }
            contextVersion.welcomeResources = newWelcomeResources;
        }
    }


    /**
     * 清除给定上下文的欢迎文件.
     *
     * @param hostName    可以找到的要清除的上下文的主机
     * @param contextPath 要清除的上下文的路径
     * @param version     要清除的上下文的版本
     */
    public void clearWelcomeFiles(String hostName, String contextPath, String version) {
        hostName = renameWildcardHost(hostName);
        ContextVersion contextVersion = findContextVersion(hostName, contextPath, version, false);
        if (contextVersion == null) {
            return;
        }
        contextVersion.welcomeResources = new String[0];
    }


    /**
     * 映射指定的主机名和URI, 转化给定映射数据.
     *
     * @param host 虚拟主机名
     * @param uri URI
     * @param version 版本号, 包含在要映射的请求中
     * @param mappingData 此结构将包含映射操作的结果
     * 
     * @throws IOException 如果缓冲区太小，无法保存映射结果.
     */
    public void map(MessageBytes host, MessageBytes uri, String version,
                    MappingData mappingData) throws IOException {

        if (host.isNull()) {
            host.getCharChunk().append(defaultHostName);
        }
        host.toChars();
        uri.toChars();
        internalMap(host.getCharChunk(), uri.getCharChunk(), version,
                mappingData);
    }


    /**
     * 映射指定的URI 相对于上下文, 转化给定映射数据.
     *
     * @param context 实际的上下文
     * @param uri URI
     * @param mappingData 此结构将包含映射操作的结果
     * 
     * @throws IOException 如果缓冲区太小，无法保存映射结果.
     */
    public void map(Context context, MessageBytes uri, MappingData mappingData) throws IOException {

        ContextVersion contextVersion =
                contextObjectToContextVersionMap.get(context);
        uri.toChars();
        CharChunk uricc = uri.getCharChunk();
        uricc.setLimit(-1);
        internalMapWrapper(contextVersion, uricc, mappingData);
    }


    // -------------------------------------------------------- Private Methods

    /**
     * 映射指定的URI.
     */
    private final void internalMap(CharChunk host, CharChunk uri,
            String version, MappingData mappingData) throws IOException {

        if (mappingData.host != null) {
            // 遗留代码 (dating down at least to Tomcat 4.1) 在这种情况下，跳过所有映射工作.
            // 这种行为有可能返回不一致的结果. 没有看到一个有效的用例.
            throw new AssertionError();
        }

        uri.setLimit(-1);

        // 虚拟主机映射
        MappedHost[] hosts = this.hosts;
        MappedHost mappedHost = exactFindIgnoreCase(hosts, host);
        if (mappedHost == null) {
            // Note: 内部的, Mapper在通配符主机上不能使用 * 开头. This is to allow this shortcut.
            int firstDot = host.indexOf('.');
            if (firstDot > -1) {
                int offset = host.getOffset();
                try {
                    host.setOffset(firstDot + offset);
                    mappedHost = exactFindIgnoreCase(hosts, host);
                } finally {
                    // Make absolutely sure this gets reset
                    host.setOffset(offset);
                }
            }
            if (mappedHost == null) {
                mappedHost = defaultHost;
                if (mappedHost == null) {
                    return;
                }
            }
        }
        mappingData.host = mappedHost.object;

        // Context mapping
        ContextList contextList = mappedHost.contextList;
        MappedContext[] contexts = contextList.contexts;
        int pos = find(contexts, uri);
        if (pos == -1) {
            return;
        }

        int lastSlash = -1;
        int uriEnd = uri.getEnd();
        int length = -1;
        boolean found = false;
        MappedContext context = null;
        while (pos >= 0) {
            context = contexts[pos];
            if (uri.startsWith(context.name)) {
                length = context.name.length();
                if (uri.getLength() == length) {
                    found = true;
                    break;
                } else if (uri.startsWithIgnoreCase("/", length)) {
                    found = true;
                    break;
                }
            }
            if (lastSlash == -1) {
                lastSlash = nthSlash(uri, contextList.nesting + 1);
            } else {
                lastSlash = lastSlash(uri);
            }
            uri.setEnd(lastSlash);
            pos = find(contexts, uri);
        }
        uri.setEnd(uriEnd);

        if (!found) {
            if (contexts[0].name.equals("")) {
                context = contexts[0];
            } else {
                context = null;
            }
        }
        if (context == null) {
            return;
        }

        mappingData.contextPath.setString(context.name);

        ContextVersion contextVersion = null;
        ContextVersion[] contextVersions = context.versions;
        final int versionCount = contextVersions.length;
        if (versionCount > 1) {
            Context[] contextObjects = new Context[contextVersions.length];
            for (int i = 0; i < contextObjects.length; i++) {
                contextObjects[i] = contextVersions[i].object;
            }
            mappingData.contexts = contextObjects;
            if (version != null) {
                contextVersion = exactFind(contextVersions, version);
            }
        }
        if (contextVersion == null) {
            // 返回最新版本
            // 已知版本数组至少包含一个元素
            contextVersion = contextVersions[versionCount - 1];
        }
        mappingData.context = contextVersion.object;
        mappingData.contextSlashCount = contextVersion.slashCount;

        // Wrapper mapping
        if (!contextVersion.isPaused()) {
            internalMapWrapper(contextVersion, uri, mappingData);
        }

    }


    /**
     * @throws IOException 如果缓冲区太小，无法保存映射结果.
     */
    private final void internalMapWrapper(ContextVersion contextVersion,
                                          CharChunk path,
                                          MappingData mappingData) throws IOException {

        int pathOffset = path.getOffset();
        int pathEnd = path.getEnd();
        boolean noServletPath = false;

        int length = contextVersion.path.length();
        if (length == (pathEnd - pathOffset)) {
            noServletPath = true;
        }
        int servletPath = pathOffset + length;
        path.setOffset(servletPath);

        // Rule 1 -- Exact Match
        MappedWrapper[] exactWrappers = contextVersion.exactWrappers;
        internalMapExactWrapper(exactWrappers, path, mappingData);

        // Rule 2 -- Prefix Match
        boolean checkJspWelcomeFiles = false;
        MappedWrapper[] wildcardWrappers = contextVersion.wildcardWrappers;
        if (mappingData.wrapper == null) {
            internalMapWildcardWrapper(wildcardWrappers, contextVersion.nesting,
                                       path, mappingData);
            if (mappingData.wrapper != null && mappingData.jspWildCard) {
                char[] buf = path.getBuffer();
                if (buf[pathEnd - 1] == '/') {
                    /*
                     * 以'/'结尾的路径基于通配符匹配映射到JSP Servlet(即, 在jsp-property-group的url-pattern中指定的.
                     * 强制上下文的欢迎文件，这些文件被解释为JSP文件(因为它们匹配url-pattern), 被考虑. See Bugzilla 27664.
                     */
                    mappingData.wrapper = null;
                    checkJspWelcomeFiles = true;
                } else {
                    // See Bugzilla 27704
                    mappingData.wrapperPath.setChars(buf, path.getStart(),
                                                     path.getLength());
                    mappingData.pathInfo.recycle();
                }
            }
        }

        if(mappingData.wrapper == null && noServletPath &&
                contextVersion.object.getMapperContextRootRedirectEnabled()) {
            // The path is empty, redirect to "/"
            path.append('/');
            pathEnd = path.getEnd();
            mappingData.redirectPath.setChars
                (path.getBuffer(), pathOffset, pathEnd - pathOffset);
            path.setEnd(pathEnd - 1);
            return;
        }

        // Rule 3 -- Extension Match
        MappedWrapper[] extensionWrappers = contextVersion.extensionWrappers;
        if (mappingData.wrapper == null && !checkJspWelcomeFiles) {
            internalMapExtensionWrapper(extensionWrappers, path, mappingData,
                    true);
        }

        // Rule 4 -- 欢迎使用Servlet的资源处理
        if (mappingData.wrapper == null) {
            boolean checkWelcomeFiles = checkJspWelcomeFiles;
            if (!checkWelcomeFiles) {
                char[] buf = path.getBuffer();
                checkWelcomeFiles = (buf[pathEnd - 1] == '/');
            }
            if (checkWelcomeFiles) {
                for (int i = 0; (i < contextVersion.welcomeResources.length)
                         && (mappingData.wrapper == null); i++) {
                    path.setOffset(pathOffset);
                    path.setEnd(pathEnd);
                    path.append(contextVersion.welcomeResources[i], 0,
                            contextVersion.welcomeResources[i].length());
                    path.setOffset(servletPath);

                    // Rule 4a -- Welcome resources processing for exact macth
                    internalMapExactWrapper(exactWrappers, path, mappingData);

                    // Rule 4b -- Welcome resources processing for prefix match
                    if (mappingData.wrapper == null) {
                        internalMapWildcardWrapper
                            (wildcardWrappers, contextVersion.nesting,
                             path, mappingData);
                    }

                    // Rule 4c -- Welcome resources processing for physical folder
                    if (mappingData.wrapper == null
                        && contextVersion.resources != null) {
                        String pathStr = path.toString();
                        WebResource file =
                                contextVersion.resources.getResource(pathStr);
                        if (file != null && file.isFile()) {
                            internalMapExtensionWrapper(extensionWrappers, path,
                                                        mappingData, true);
                            if (mappingData.wrapper == null
                                && contextVersion.defaultWrapper != null) {
                                mappingData.wrapper =
                                    contextVersion.defaultWrapper.object;
                                mappingData.requestPath.setChars
                                    (path.getBuffer(), path.getStart(),
                                     path.getLength());
                                mappingData.wrapperPath.setChars
                                    (path.getBuffer(), path.getStart(),
                                     path.getLength());
                                mappingData.requestPath.setString(pathStr);
                                mappingData.wrapperPath.setString(pathStr);
                            }
                        }
                    }
                }

                path.setOffset(servletPath);
                path.setEnd(pathEnd);
            }

        }

        /* 欢迎文件处理 - take 2
         * 现在已经找到了物理支持的欢迎文件, 现在寻找一个扩展映射，但可能没有物理支持. This is for the case of index.jsf, index.do, etc.
         * A watered down version of rule 4
         */
        if (mappingData.wrapper == null) {
            boolean checkWelcomeFiles = checkJspWelcomeFiles;
            if (!checkWelcomeFiles) {
                char[] buf = path.getBuffer();
                checkWelcomeFiles = (buf[pathEnd - 1] == '/');
            }
            if (checkWelcomeFiles) {
                for (int i = 0; (i < contextVersion.welcomeResources.length)
                         && (mappingData.wrapper == null); i++) {
                    path.setOffset(pathOffset);
                    path.setEnd(pathEnd);
                    path.append(contextVersion.welcomeResources[i], 0,
                                contextVersion.welcomeResources[i].length());
                    path.setOffset(servletPath);
                    internalMapExtensionWrapper(extensionWrappers, path,
                                                mappingData, false);
                }

                path.setOffset(servletPath);
                path.setEnd(pathEnd);
            }
        }


        // Rule 7 -- Default servlet
        if (mappingData.wrapper == null && !checkJspWelcomeFiles) {
            if (contextVersion.defaultWrapper != null) {
                mappingData.wrapper = contextVersion.defaultWrapper.object;
                mappingData.requestPath.setChars
                    (path.getBuffer(), path.getStart(), path.getLength());
                mappingData.wrapperPath.setChars
                    (path.getBuffer(), path.getStart(), path.getLength());
                mappingData.matchType = MappingMatch.DEFAULT;
            }
            // 重定向到文件夹
            char[] buf = path.getBuffer();
            if (contextVersion.resources != null && buf[pathEnd -1 ] != '/') {
                String pathStr = path.toString();
                WebResource file;
                // Handle context root
                if (pathStr.length() == 0) {
                    file = contextVersion.resources.getResource("/");
                } else {
                    file = contextVersion.resources.getResource(pathStr);
                }
                if (file != null && file.isDirectory() &&
                        contextVersion.object.getMapperDirectoryRedirectEnabled()) {
                    // Note: 转换路径: 此后不做任何处理(自从设置了 redirectPath, 不应该有任何)
                    path.setOffset(pathOffset);
                    path.append('/');
                    mappingData.redirectPath.setChars
                        (path.getBuffer(), path.getStart(), path.getLength());
                } else {
                    mappingData.requestPath.setString(pathStr);
                    mappingData.wrapperPath.setString(pathStr);
                }
            }
        }

        path.setOffset(pathOffset);
        path.setEnd(pathEnd);
    }


    /**
     * 精确映射.
     */
    private final void internalMapExactWrapper
        (MappedWrapper[] wrappers, CharChunk path, MappingData mappingData) {
        MappedWrapper wrapper = exactFind(wrappers, path);
        if (wrapper != null) {
            mappingData.requestPath.setString(wrapper.name);
            mappingData.wrapper = wrapper.object;
            if (path.equals("/")) {
                // Context Root 映射 servlet的特殊处理
                mappingData.pathInfo.setString("/");
                mappingData.wrapperPath.setString("");
                // 似乎是错误的，但这是规范所说的...
                mappingData.contextPath.setString("");
                mappingData.matchType = MappingMatch.CONTEXT_ROOT;
            } else {
                mappingData.wrapperPath.setString(wrapper.name);
                mappingData.matchType = MappingMatch.EXACT;
            }
        }
    }


    /**
     * 通配符映射.
     */
    private final void internalMapWildcardWrapper
        (MappedWrapper[] wrappers, int nesting, CharChunk path,
         MappingData mappingData) {

        int pathEnd = path.getEnd();

        int lastSlash = -1;
        int length = -1;
        int pos = find(wrappers, path);
        if (pos != -1) {
            boolean found = false;
            while (pos >= 0) {
                if (path.startsWith(wrappers[pos].name)) {
                    length = wrappers[pos].name.length();
                    if (path.getLength() == length) {
                        found = true;
                        break;
                    } else if (path.startsWithIgnoreCase("/", length)) {
                        found = true;
                        break;
                    }
                }
                if (lastSlash == -1) {
                    lastSlash = nthSlash(path, nesting + 1);
                } else {
                    lastSlash = lastSlash(path);
                }
                path.setEnd(lastSlash);
                pos = find(wrappers, path);
            }
            path.setEnd(pathEnd);
            if (found) {
                mappingData.wrapperPath.setString(wrappers[pos].name);
                if (path.getLength() > length) {
                    mappingData.pathInfo.setChars
                        (path.getBuffer(),
                         path.getOffset() + length,
                         path.getLength() - length);
                }
                mappingData.requestPath.setChars
                    (path.getBuffer(), path.getOffset(), path.getLength());
                mappingData.wrapper = wrappers[pos].object;
                mappingData.jspWildCard = wrappers[pos].jspWildCard;
                mappingData.matchType = MappingMatch.PATH;
            }
        }
    }


    /**
     * 扩展名映射.
     *
     * @param wrappers          用于检查匹配的包装器集合
     * @param path              要映射的路径
     * @param mappingData       结果的映射数据
     * @param resourceExpected  这个映射是否希望找到资源
     */
    private final void internalMapExtensionWrapper(MappedWrapper[] wrappers,
            CharChunk path, MappingData mappingData, boolean resourceExpected) {
        char[] buf = path.getBuffer();
        int pathEnd = path.getEnd();
        int servletPath = path.getOffset();
        int slash = -1;
        for (int i = pathEnd - 1; i >= servletPath; i--) {
            if (buf[i] == '/') {
                slash = i;
                break;
            }
        }
        if (slash >= 0) {
            int period = -1;
            for (int i = pathEnd - 1; i > slash; i--) {
                if (buf[i] == '.') {
                    period = i;
                    break;
                }
            }
            if (period >= 0) {
                path.setOffset(period + 1);
                path.setEnd(pathEnd);
                MappedWrapper wrapper = exactFind(wrappers, path);
                if (wrapper != null
                        && (resourceExpected || !wrapper.resourceOnly)) {
                    mappingData.wrapperPath.setChars(buf, servletPath, pathEnd
                            - servletPath);
                    mappingData.requestPath.setChars(buf, servletPath, pathEnd
                            - servletPath);
                    mappingData.wrapper = wrapper.object;
                    mappingData.matchType = MappingMatch.EXTENSION;
                }
                path.setOffset(servletPath);
                path.setEnd(pathEnd);
            }
        }
    }


    /**
     * 在一个 map 元素排序的数组元素中给出它的名称, 查找一个 map 元素.
     * 这将返回给定数组中最接近的或相等的项的索引.
     */
    private static final <T> int find(MapElement<T>[] map, CharChunk name) {
        return find(map, name, name.getStart(), name.getEnd());
    }


    /**
     * 在一个 map 元素排序的数组元素中给出它的名称, 查找一个 map 元素.
     * 这将返回给定数组中最接近的或相等的项的索引.
     */
    private static final <T> int find(MapElement<T>[] map, CharChunk name,
                                  int start, int end) {

        int a = 0;
        int b = map.length - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }

        if (compare(name, start, end, map[0].name) < 0 ) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) / 2;
            int result = compare(name, start, end, map[i].name);
            if (result == 1) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = compare(name, start, end, map[b].name);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }
    }

    /**
     * 在一个 map 元素排序的数组元素中给出它的名称, 查找一个 map 元素.
     * 这将返回给定数组中最接近的或相等的项的索引.
     */
    private static final <T> int findIgnoreCase(MapElement<T>[] map, CharChunk name) {
        return findIgnoreCase(map, name, name.getStart(), name.getEnd());
    }


    /**
     * 在一个 map 元素排序的数组元素中给出它的名称, 查找一个 map 元素.
     * 这将返回给定数组中最接近的或相等的项的索引.
     */
    private static final <T> int findIgnoreCase(MapElement<T>[] map, CharChunk name,
                                  int start, int end) {

        int a = 0;
        int b = map.length - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }
        if (compareIgnoreCase(name, start, end, map[0].name) < 0 ) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) / 2;
            int result = compareIgnoreCase(name, start, end, map[i].name);
            if (result == 1) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = compareIgnoreCase(name, start, end, map[b].name);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }
    }


    /**
     * 在一个 map 元素排序的数组元素中给出它的名称, 查找一个 map 元素.
     * 这将返回给定数组中最接近的或相等的项的索引.
     */
    private static final <T> int find(MapElement<T>[] map, String name) {

        int a = 0;
        int b = map.length - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }

        if (name.compareTo(map[0].name) < 0) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) / 2;
            int result = name.compareTo(map[i].name);
            if (result > 0) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = name.compareTo(map[b].name);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }

    }


    /**
     * 在一个 map 元素排序的数组元素中给出它的名称, 查找一个 map 元素.
     * 这将返回正在搜索的元素. 否则返回<code>null</code>.
     */
    private static final <T, E extends MapElement<T>> E exactFind(E[] map,
            String name) {
        int pos = find(map, name);
        if (pos >= 0) {
            E result = map[pos];
            if (name.equals(result.name)) {
                return result;
            }
        }
        return null;
    }

    /**
     * 在一个 map 元素排序的数组元素中给出它的名称, 查找一个 map 元素.
     * 这将返回正在搜索的元素. 否则返回<code>null</code>.
     */
    private static final <T, E extends MapElement<T>> E exactFind(E[] map,
            CharChunk name) {
        int pos = find(map, name);
        if (pos >= 0) {
            E result = map[pos];
            if (name.equals(result.name)) {
                return result;
            }
        }
        return null;
    }

    /**
     * 在一个 map 元素排序的数组元素中给出它的名称, 查找一个 map 元素.
     * 这将返回正在搜索的元素. 否则返回<code>null</code>.
     */
    private static final <T, E extends MapElement<T>> E exactFindIgnoreCase(
            E[] map, CharChunk name) {
        int pos = findIgnoreCase(map, name);
        if (pos >= 0) {
            E result = map[pos];
            if (name.equalsIgnoreCase(result.name)) {
                return result;
            }
        }
        return null;
    }


    /**
     * 给定字符块与字符串的比较.
     * 返回 -1, 0 或 +1 if inferior, equal, or superior to the String.
     */
    private static final int compare(CharChunk name, int start, int end,
                                     String compareTo) {
        int result = 0;
        char[] c = name.getBuffer();
        int len = compareTo.length();
        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (c[i + start] > compareTo.charAt(i)) {
                result = 1;
            } else if (c[i + start] < compareTo.charAt(i)) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length() > (end - start)) {
                result = -1;
            } else if (compareTo.length() < (end - start)) {
                result = 1;
            }
        }
        return result;
    }


    /**
     * 将给定字符块与字符串忽略大小写进行比较.
     * Return -1, 0 or +1 if inferior, equal, or superior to the String.
     */
    private static final int compareIgnoreCase(CharChunk name, int start, int end,
                                     String compareTo) {
        int result = 0;
        char[] c = name.getBuffer();
        int len = compareTo.length();
        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (Ascii.toLower(c[i + start]) > Ascii.toLower(compareTo.charAt(i))) {
                result = 1;
            } else if (Ascii.toLower(c[i + start]) < Ascii.toLower(compareTo.charAt(i))) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length() > (end - start)) {
                result = -1;
            } else if (compareTo.length() < (end - start)) {
                result = 1;
            }
        }
        return result;
    }


    /**
     * 查找给定char块中最后一个斜杠的位置.
     */
    private static final int lastSlash(CharChunk name) {

        char[] c = name.getBuffer();
        int end = name.getEnd();
        int start = name.getStart();
        int pos = end;

        while (pos > start) {
            if (c[--pos] == '/') {
                break;
            }
        }
        return (pos);
    }


    /**
     * 在给定的字符块中找到第n条斜杠的位置.
     */
    private static final int nthSlash(CharChunk name, int n) {

        char[] c = name.getBuffer();
        int end = name.getEnd();
        int start = name.getStart();
        int pos = start;
        int count = 0;

        while (pos < end) {
            if ((c[pos++] == '/') && ((++count) == n)) {
                pos--;
                break;
            }
        }
        return (pos);
    }


    /**
     * 返回给定字符串中的斜杠计数.
     */
    private static final int slashCount(String name) {
        int pos = -1;
        int count = 0;
        while ((pos = name.indexOf('/', pos + 1)) != -1) {
            count++;
        }
        return count;
    }


    /**
     * 在排序MapElement数组中插入正确的位置，并防止重复.
     */
    private static final <T> boolean insertMap
        (MapElement<T>[] oldMap, MapElement<T>[] newMap, MapElement<T> newElement) {
        int pos = find(oldMap, newElement.name);
        if ((pos != -1) && (newElement.name.equals(oldMap[pos].name))) {
            return false;
        }
        System.arraycopy(oldMap, 0, newMap, 0, pos + 1);
        newMap[pos + 1] = newElement;
        System.arraycopy
            (oldMap, pos + 1, newMap, pos + 2, oldMap.length - pos - 1);
        return true;
    }


    /**
     * 在排序MapElement数组中插入正确的位置.
     */
    private static final <T> boolean removeMap
        (MapElement<T>[] oldMap, MapElement<T>[] newMap, String name) {
        int pos = find(oldMap, name);
        if ((pos != -1) && (name.equals(oldMap[pos].name))) {
            System.arraycopy(oldMap, 0, newMap, 0, pos);
            System.arraycopy(oldMap, pos + 1, newMap, pos,
                             oldMap.length - pos - 1);
            return true;
        }
        return false;
    }


    /*
     * 简化映射过程, 通配符主机采用表单 ".apache.org" 而不是 "*.apache.org".
     * 但是, 为了便于使用, 使用 "*.apache.org". 任何传入这个类的主机名都需要通过这个方法, 从外部到内部形式重命名和通配符主机名.
     */
    private static String renameWildcardHost(String hostName) {
        if (hostName.startsWith("*.")) {
            return hostName.substring(1);
        } else {
            return hostName;
        }
    }


    // ------------------------------------------------- MapElement Inner Class


    protected abstract static class MapElement<T> {

        public final String name;
        public final T object;

        public MapElement(String name, T object) {
            this.name = name;
            this.object = object;
        }
    }


    // ------------------------------------------------------- Host Inner Class


    protected static final class MappedHost extends MapElement<Host> {

        public volatile ContextList contextList;

        /**
         * "real" MappedHost的链接, 所有别名共享.
         */
        private final MappedHost realHost;

        /**
         * 链接到所有注册的别名，便于枚举. 字段只有在"real" MappedHost中可用. 在别名中, 这个字段是<code>null</code>.
         */
        private final List<MappedHost> aliases;

        /**
         * @param name 虚拟主机的名称
         * @param host 主机
         */
        public MappedHost(String name, Host host) {
            super(name, host);
            realHost = this;
            contextList = new ContextList();
            aliases = new CopyOnWriteArrayList<>();
        }

        /**
         * @param alias    虚拟主机的别名
         * @param realHost 指向的主机别名
         */
        public MappedHost(String alias, MappedHost realHost) {
            super(alias, realHost.object);
            this.realHost = realHost;
            this.contextList = realHost.contextList;
            this.aliases = null;
        }

        public boolean isAlias() {
            return realHost != this;
        }

        public MappedHost getRealHost() {
            return realHost;
        }

        public String getRealHostName() {
            return realHost.name;
        }

        public Collection<MappedHost> getAliases() {
            return aliases;
        }

        public void addAlias(MappedHost alias) {
            aliases.add(alias);
        }

        public void addAliases(Collection<? extends MappedHost> c) {
            aliases.addAll(c);
        }

        public void removeAlias(MappedHost alias) {
            aliases.remove(alias);
        }
    }


    // ------------------------------------------------ ContextList Inner Class


    protected static final class ContextList {

        public final MappedContext[] contexts;
        public final int nesting;

        public ContextList() {
            this(new MappedContext[0], 0);
        }

        private ContextList(MappedContext[] contexts, int nesting) {
            this.contexts = contexts;
            this.nesting = nesting;
        }

        public ContextList addContext(MappedContext mappedContext,
                int slashCount) {
            MappedContext[] newContexts = new MappedContext[contexts.length + 1];
            if (insertMap(contexts, newContexts, mappedContext)) {
                return new ContextList(newContexts, Math.max(nesting,
                        slashCount));
            }
            return null;
        }

        public ContextList removeContext(String path) {
            MappedContext[] newContexts = new MappedContext[contexts.length - 1];
            if (removeMap(contexts, newContexts, path)) {
                int newNesting = 0;
                for (MappedContext context : newContexts) {
                    newNesting = Math.max(newNesting, slashCount(context.name));
                }
                return new ContextList(newContexts, newNesting);
            }
            return null;
        }
    }


    // ---------------------------------------------------- Context Inner Class


    protected static final class MappedContext extends MapElement<Void> {
        public volatile ContextVersion[] versions;

        public MappedContext(String name, ContextVersion firstVersion) {
            super(name, null);
            this.versions = new ContextVersion[] { firstVersion };
        }
    }

    protected static final class ContextVersion extends MapElement<Context> {
        public final String path;
        public final int slashCount;
        public final WebResourceRoot resources;
        public String[] welcomeResources;
        public MappedWrapper defaultWrapper = null;
        public MappedWrapper[] exactWrappers = new MappedWrapper[0];
        public MappedWrapper[] wildcardWrappers = new MappedWrapper[0];
        public MappedWrapper[] extensionWrappers = new MappedWrapper[0];
        public int nesting = 0;
        private volatile boolean paused;

        public ContextVersion(String version, String path, int slashCount,
                Context context, WebResourceRoot resources,
                String[] welcomeResources) {
            super(version, context);
            this.path = path;
            this.slashCount = slashCount;
            this.resources = resources;
            this.welcomeResources = welcomeResources;
        }

        public boolean isPaused() {
            return paused;
        }

        public void markPaused() {
            paused = true;
        }
    }

    // ---------------------------------------------------- Wrapper Inner Class


    protected static class MappedWrapper extends MapElement<Wrapper> {

        public final boolean jspWildCard;
        public final boolean resourceOnly;

        public MappedWrapper(String name, Wrapper wrapper, boolean jspWildCard,
                boolean resourceOnly) {
            super(name, wrapper);
            this.jspWildCard = jspWildCard;
            this.resourceOnly = resourceOnly;
        }
    }
}
