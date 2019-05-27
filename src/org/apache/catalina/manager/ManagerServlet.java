package org.apache.catalina.manager;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.naming.Binding;
import javax.naming.NamingEnumeration;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Manager;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.Session;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.ExpandWar;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.ServerInfo;
import org.apache.tomcat.util.Diagnostics;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;

/**
 * 它支持在同一虚拟主机中安装的Web应用程序的远程管理.
 * 通常，此功能将受到Web应用程序部署描述符中的安全约束的保护. 但是，在测试过程中可以放宽这一要求.
 * <p>
 * 此servlet检查<code>getPathInfo()</code>返回的值， 以及相关的查询参数，以确定正在请求什么操作.
 * 支持以下动作和参数(从servlet路径开始) :
 * <ul>
 * <li><b>/deploy?config={config-url}</b> - 安装并启动一个新的Web应用程序, 基于在指定URL中找到的上下文配置文件的内容.
 * 		上下文配置文件的<code>docBase</code>属性用于定位包含应用程序的实际WAR 或目录.</li>
 * <li><b>/deploy?config={config-url}&amp;war={war-url}/</b> - 安装并启动一个新的Web应用程序, 
 * 		基于在<code>{config-url}</code>中找到的上下文配置的内容, 
 * 		根据在<code>{war-url}</code>找到的web应用归档文件的内容覆盖<code>docBase</code>属性 .</li>
 * <li><b>/deploy?path=/xxx&amp;war={war-url}</b> - 安装并启动一个新的<code>/xxx</code>上下文路径关联的Web应用程序,
 * 		基于在指定URL中找到的Web应用程序存档的内容.</li>
 * <li><b>/list</b> - 列出此虚拟主机当前所有已安装Web应用程序的上下文路径. 将以<code>path:status:sessions</code>格式列出每个上下文.
 *     其中路径是上下文路径. 状态或正在运行或停止. Sessions是活动会话的个数.</li>
 * <li><b>/reload?path=/xxx</b> - 重新加载指定路径上的应用的 Java类和资源.</li>
 * <li><b>/resources?type=xxxx</b> - 枚举可用的全局JNDI资源, 可选地限制指定类型的那些 (java类的完全限定名称).</li>
 * <li><b>/serverinfo</b> - 显示系统操作系统和JVM属性.
 * <li><b>/sessions</b> - 弃用. 使用 expire.
 * <li><b>/expire?path=/xxx</b> - 列出附加到这个虚拟主机上下文路径<code>/xxx</code>的Web应用程序的会话空闲时间信息.</li>
 * <li><b>/expire?path=/xxx&amp;idle=mm</b> - 让空闲至少mm分钟的上下文路径为<code>/xxx</code>的会话过期.</li>
 * <li><b>/sslConnectorCiphers</b> - 在当前为每个连接器配置的SSL/TLS密码上显示诊断信息.
 * <li><b>/start?path=/xxx</b> - 启动这个虚拟主机上关联到上下文路径<code>/xxx</code>的web应用.</li>
 * <li><b>/stop?path=/xxx</b> - 关闭这个虚拟主机上关联到上下文路径<code>/xxx</code>的web应用.</li>
 * <li><b>/threaddump</b> - 写入JVM线程转储.</li>
 * <li><b>/undeploy?path=/xxx</b> - 关闭并删除这个虚拟主机上关联到上下文路径<code>/xxx</code>的web应用, 并删除底层WAR文件或文档基目录.
 *     (<em>NOTE</em> - 只有在WAR文件或文档基目录保存在这个主机的<code>appBase</code>目录的情况下,
 *     通常是由于被放置在那里通过<code>/deploy</code>命令.</li>
 * <li><b>/vminfo</b> - 写入一些VM 信息.</li>
 * <li><b>/save</b> - 保存当前服务器配置到 server.xml</li>
 * <li><b>/save?path=/xxx</b> - 保存部署在路径<code>/xxx</code>的Web应用程序的上下文配置到一个<code>xmlBase</code>中适当的 context.xml 文件, 为关联的Host.</li>
 * </ul>
 * <p>使用<code>path=/</code>表示ROOT上下文.</p>
 * <p>Web应用程序归档文件的URL的语法必须符合以下模式之一，以便成功部署:</p>
 * <ul>
 * <li><b>file:/absolute/path/to/a/directory</b> - 可以指定包含Web应用程序解压版本的目录的绝对路径. 此目录将附加到您指定的上下文路径而无需更改.</li>
 * </ul>
 * <p>
 * <b>NOTE</b> - 试图重新加载或删除包含此servlet本身的应用程序将不会成功. 因此，这个servlet应该作为一个单独的Web应用程序部署到虚拟主机中进行管理.
 * <p>
 * 识别以下servlet初始化参数:
 * <ul>
 * <li><b>debug</b> - 控制此servlet记录的信息量的调试详细级别. 默认是零.
 * </ul>
 */
public class ManagerServlet extends HttpServlet implements ContainerServlet {

    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------- Instance Variables


    /**
     * 应部署上下文描述符的路径.
     */
    protected File configBase = null;


    /**
     * Web应用关联的Context容器.
     */
    protected transient Context context = null;


    /**
     * 调试等级.
     */
    protected int debug = 1;


    /**
     * 用于存储Web应用程序修订的路径.
     */
    protected File versioned = null;


    /**
     * 关联的主机.
     */
    protected transient Host host = null;


    /**
     * MBean 服务器.
     */
    protected transient MBeanServer mBeanServer = null;


    /**
     * 关联的部署程序ObjectName.
     */
    protected ObjectName oname = null;


    /**
     * 这个服务器的全局JNDI <code>NamingContext</code>.
     */
    protected transient javax.naming.Context global = null;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 这个servlet关联的 Wrapper容器.
     */
    protected transient Wrapper wrapper = null;


    // ----------------------------------------------- ContainerServlet Methods


    /**
     * 返回关联的Wrapper.
     */
    @Override
    public Wrapper getWrapper() {
        return (this.wrapper);
    }


    /**
     * 设置关联的Wrapper.
     *
     * @param wrapper The new wrapper
     */
    @Override
    public void setWrapper(Wrapper wrapper) {

        this.wrapper = wrapper;
        if (wrapper == null) {
            context = null;
            host = null;
            oname = null;
        } else {
            context = (Context) wrapper.getParent();
            host = (Host) context.getParent();
            Engine engine = (Engine) host.getParent();
            String name = engine.getName() + ":type=Deployer,host=" +
                    host.getName();
            try {
                oname = new ObjectName(name);
            } catch (Exception e) {
                log(sm.getString("managerServlet.objectNameFail", name), e);
            }
        }

        // 检索 MBean 服务器
        mBeanServer = Registry.getRegistry(null, null).getMBeanServer();
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 完成 servlet.
     */
    @Override
    public void destroy() {
        // No actions necessary
    }


    /**
     * 处理指定资源的 GET请求.
     *
     * @param request 正在处理的servlet请求
     * @param response 正在创建的servlet响应
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果servlet指定的错误发生
     */
    @Override
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws IOException, ServletException {

        StringManager smClient = StringManager.getManager(
                Constants.Package, request.getLocales());

        // 标识需要的请求参数
        String command = request.getPathInfo();
        if (command == null)
            command = request.getServletPath();
        String config = request.getParameter("config");
        String path = request.getParameter("path");
        ContextName cn = null;
        if (path != null) {
            cn = new ContextName(path, request.getParameter("version"));
        }
        String type = request.getParameter("type");
        String war = request.getParameter("war");
        String tag = request.getParameter("tag");
        boolean update = false;
        if ((request.getParameter("update") != null)
            && (request.getParameter("update").equals("true"))) {
            update = true;
        }

        boolean statusLine = false;
        if ("true".equals(request.getParameter("statusLine"))) {
            statusLine = true;
        }

        // 准备输出writer 以生成响应消息
        response.setContentType("text/plain; charset=" + Constants.CHARSET);
        PrintWriter writer = response.getWriter();

        // 处理请求的命令
        if (command == null) {
            writer.println(smClient.getString("managerServlet.noCommand"));
        } else if (command.equals("/deploy")) {
            if (war != null || config != null) {
                deploy(writer, config, cn, war, update, smClient);
            } else if (tag != null) {
                deploy(writer, cn, tag, smClient);
            } else {
                writer.println(smClient.getString(
                        "managerServlet.invalidCommand", command));
            }
        } else if (command.equals("/list")) {
            list(writer, smClient);
        } else if (command.equals("/reload")) {
            reload(writer, cn, smClient);
        } else if (command.equals("/resources")) {
            resources(writer, type, smClient);
        } else if (command.equals("/save")) {
            save(writer, path, smClient);
        } else if (command.equals("/serverinfo")) {
            serverinfo(writer, smClient);
        } else if (command.equals("/sessions")) {
            expireSessions(writer, cn, request, smClient);
        } else if (command.equals("/expire")) {
            expireSessions(writer, cn, request, smClient);
        } else if (command.equals("/start")) {
            start(writer, cn, smClient);
        } else if (command.equals("/stop")) {
            stop(writer, cn, smClient);
        } else if (command.equals("/undeploy")) {
            undeploy(writer, cn, smClient);
        } else if (command.equals("/findleaks")) {
            findleaks(statusLine, writer, smClient);
        } else if (command.equals("/vminfo")) {
            vmInfo(writer, smClient, request.getLocales());
        } else if (command.equals("/threaddump")) {
            threadDump(writer, smClient, request.getLocales());
        } else if (command.equals("/sslConnectorCiphers")) {
            sslConnectorCiphers(writer, smClient);
        } else {
            writer.println(smClient.getString("managerServlet.unknownCommand",
                    command));
        }

        // 完成响应
        writer.flush();
        writer.close();
    }


    /**
     * 处理指定资源的PUT请求.
     *
     * @param request 正在处理的servlet请求
     * @param response 正在创建的servlet响应
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果servlet指定的错误发生
     */
    @Override
    public void doPut(HttpServletRequest request,
                      HttpServletResponse response)
        throws IOException, ServletException {

        StringManager smClient = StringManager.getManager(
                Constants.Package, request.getLocales());

        // 标识需要的请求参数
        String command = request.getPathInfo();
        if (command == null)
            command = request.getServletPath();
        String path = request.getParameter("path");
        ContextName cn = null;
        if (path != null) {
            cn = new ContextName(path, request.getParameter("version"));
        }
        String tag = request.getParameter("tag");
        boolean update = false;
        if ((request.getParameter("update") != null)
            && (request.getParameter("update").equals("true"))) {
            update = true;
        }

        // 准备输出writer以生成响应消息
        response.setContentType("text/plain;charset="+Constants.CHARSET);
        PrintWriter writer = response.getWriter();

        // 处理请求的命令
        if (command == null) {
            writer.println(smClient.getString("managerServlet.noCommand"));
        } else if (command.equals("/deploy")) {
            deploy(writer, cn, tag, update, request, smClient);
        } else {
            writer.println(smClient.getString("managerServlet.unknownCommand",
                    command));
        }

        // 完成响应
        writer.flush();
        writer.close();
    }


    /**
     * 初始化这个servlet.
     */
    @Override
    public void init() throws ServletException {

        // 确保已经设置 ContainerServlet属性
        if ((wrapper == null) || (context == null))
            throw new UnavailableException(
                    sm.getString("managerServlet.noWrapper"));

        // 从初始化参数设置属性
        String value = null;
        try {
            value = getServletConfig().getInitParameter("debug");
            debug = Integer.parseInt(value);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
        }

        // 获取全局JNDI资源
        Server server = ((Engine)host.getParent()).getService().getServer();
        if (server != null) {
            global = server.getGlobalNamingContext();
        }

        // 计算将部署应用程序的目录
        versioned = (File) getServletContext().getAttribute
            (ServletContext.TEMPDIR);

        configBase = new File(context.getCatalinaBase(), "conf");
        Container container = context;
        Container host = null;
        Container engine = null;
        while (container != null) {
            if (container instanceof Host)
                host = container;
            if (container instanceof Engine)
                engine = container;
            container = container.getParent();
        }
        if (engine != null) {
            configBase = new File(configBase, engine.getName());
        }
        if (host != null) {
            configBase = new File(configBase, host.getName());
        }
        // Note: 目录必须存在.

        // Log debugging messages as necessary
        if (debug >= 1) {
            log("init: Associated with Deployer '" +
                oname + "'");
            if (global != null) {
                log("init: Global resources are available");
            }
        }

    }



    // -------------------------------------------------------- Private Methods


    /**
     * 查找Web应用程序重载导致的潜在内存泄漏.
     *
     * @param statusLine 打印状态行
     * @param writer 输出writer
     * @param smClient 客户端区域的StringManager
     */
    protected void findleaks(boolean statusLine, PrintWriter writer,
            StringManager smClient) {

        if (!(host instanceof StandardHost)) {
            writer.println(smClient.getString("managerServlet.findleaksFail"));
            return;
        }

        String[] results =
            ((StandardHost) host).findReloadedContextMemoryLeaks();

        if (results.length > 0) {
            if (statusLine) {
                writer.println(
                        smClient.getString("managerServlet.findleaksList"));
            }
            for (String result : results) {
                if ("".equals(result)) {
                    result = "/";
                }
                writer.println(result);
            }
        } else if (statusLine) {
            writer.println(smClient.getString("managerServlet.findleaksNone"));
        }
    }


    /**
     * 写入一些 VM 信息.
     *
     * @param writer 输出 writer
     * @param smClient 客户端区域的StringManager
     * @param requestedLocales 客户端的区域
     */
    protected void vmInfo(PrintWriter writer, StringManager smClient,
            Enumeration<Locale> requestedLocales) {
        writer.println(smClient.getString("managerServlet.vminfo"));
        writer.print(Diagnostics.getVMInfo(requestedLocales));
    }

    /**
     * 写入JVM 线程转储.
     *
     * @param writer 输出writer
     * @param smClient 客户端区域的StringManager
     * @param requestedLocales 客户端区域
     */
    protected void threadDump(PrintWriter writer, StringManager smClient,
            Enumeration<Locale> requestedLocales) {
        writer.println(smClient.getString("managerServlet.threaddump"));
        writer.print(Diagnostics.getThreadDump(requestedLocales));
    }

    protected void sslConnectorCiphers(PrintWriter writer,
            StringManager smClient) {
        writer.println(smClient.getString(
                "managerServlet.sslConnectorCiphers"));
        Map<String,List<String>> connectorCiphers = getConnectorCiphers();
        for (Map.Entry<String,List<String>> entry : connectorCiphers.entrySet()) {
            writer.println(entry.getKey());
            for (String cipher : entry.getValue()) {
                writer.print("  ");
                writer.println(cipher);
            }
        }
    }


    /**
     * 存储服务器配置.
     *
     * @param writer   此操作期间任何用户消息的目的地
     * @param path     保存可选上下文路径
     * @param smClient 当前客户端的区域的i18n 支持
     */
    protected synchronized void save(PrintWriter writer, String path, StringManager smClient) {

        ObjectName storeConfigOname;
        try {
            // Note: Hard-coded domain used since this object is per Server/JVM
            storeConfigOname = new ObjectName("Catalina:type=StoreConfig");
        } catch (MalformedObjectNameException e) {
            // Should never happen. The name above is valid.
            log(sm.getString("managerServlet.exception"), e);
            writer.println(smClient.getString("managerServlet.exception", e.toString()));
            return;
        }

        if (!mBeanServer.isRegistered(storeConfigOname)) {
            writer.println(smClient.getString(
                    "managerServlet.storeConfig.noMBean", storeConfigOname));
            return;
        }

        if ((path == null) || path.length() == 0 || !path.startsWith("/")) {
            try {
                mBeanServer.invoke(storeConfigOname, "storeConfig", null, null);
                writer.println(smClient.getString("managerServlet.saved"));
            } catch (Exception e) {
                log("managerServlet.storeConfig", e);
                writer.println(smClient.getString("managerServlet.exception",
                        e.toString()));
                return;
            }
        } else {
            String contextPath = path;
            if (path.equals("/")) {
                contextPath = "";
            }
            Context context = (Context) host.findChild(contextPath);
            if (context == null) {
                writer.println(smClient.getString("managerServlet.noContext",
                        path));
                return;
            }
            try {
                mBeanServer.invoke(storeConfigOname, "store",
                        new Object[] {context},
                        new String [] { "java.lang.String"});
                writer.println(smClient.getString("managerServlet.savedContext",
                        path));
            } catch (Exception e) {
                log("managerServlet.save[" + path + "]", e);
                writer.println(smClient.getString("managerServlet.exception",
                        e.toString()));
                return;
            }
        }
    }


    /**
     * 在指定的上下文路径上部署Web应用程序归档文件（包括在当前请求中）.
     *
     * @param writer   Writer to render results to
     * @param cn       要安装的应用程序的名称
     * @param tag      要与webapp关联的标签
     * @param update   任何现有的应用程序是否都应该被替换
     * @param request  正在处理的Servlet请求
     * @param smClient 当前客户端的区域的i18n 支持
     */
    protected synchronized void deploy(PrintWriter writer, ContextName cn,
         String tag, boolean update, HttpServletRequest request,
         StringManager smClient) {

        if (debug >= 1) {
            log("deploy: Deploying web application '" + cn + "'");
        }

        // 验证请求的上下文路径
        if (!validateContextName(cn, writer, smClient)) {
            return;
        }
        String name = cn.getName();
        String baseName = cn.getBaseName();
        String displayPath = cn.getDisplayName();

        // 如果应用程序存在，部署只能继续进行, 如果 update 是 true
        // Note: 现有 WAR 将被删除，然后替换
        Context context = (Context) host.findChild(name);
        if (context != null && !update) {
            writer.println(smClient.getString("managerServlet.alreadyContext",
                    displayPath));
            return;
        }

        File deployedWar = new File(host.getAppBaseFile(), baseName + ".war");

        // 确定上传的WAR的完整路径
        File uploadedWar;
        if (tag == null) {
            if (update) {
                // 追加 ".tmp" 到文件名, 因此如果启用自动部署，它将无法部署. 这也意味着如果上传失败，旧war不会被删除
                uploadedWar = new File(deployedWar.getAbsolutePath() + ".tmp");
                if (uploadedWar.exists() && !uploadedWar.delete()) {
                    writer.println(smClient.getString("managerServlet.deleteFail",
                            uploadedWar));
                }
            } else {
                uploadedWar = deployedWar;
            }
        } else {
            File uploadPath = new File(versioned, tag);
            if (!uploadPath.mkdirs() && !uploadPath.isDirectory()) {
                writer.println(smClient.getString("managerServlet.mkdirFail",
                        uploadPath));
                return;
            }
            uploadedWar = new File(uploadPath, baseName + ".war");
        }
        if (debug >= 2) {
            log("Uploading WAR file to " + uploadedWar);
        }

        try {
            if (isServiced(name)) {
                writer.println(smClient.getString("managerServlet.inService", displayPath));
            } else {
                addServiced(name);
                try {
                    // Upload WAR
                    uploadWar(writer, request, uploadedWar, smClient);
                    if (update && tag == null) {
                        if (deployedWar.exists() && !deployedWar.delete()) {
                            writer.println(smClient.getString("managerServlet.deleteFail",
                                    deployedWar));
                            return;
                        }
                        // 重命名上传的 WAR 文件
                        uploadedWar.renameTo(deployedWar);
                    }
                    if (tag != null) {
                        // Copy WAR to the host's appBase
                        copy(uploadedWar, deployedWar);
                    }
                    // 执行新部署
                    check(name);
                } finally {
                    removeServiced(name);
                }
            }
        } catch (Exception e) {
            log("managerServlet.check[" + displayPath + "]", e);
            writer.println(smClient.getString("managerServlet.exception",
                    e.toString()));
            return;
        }

        writeDeployResult(writer, smClient, name, displayPath);
    }


    /**
     * 从指定的Web应用程序存档中安装指定路径的应用程序.
     *
     * @param writer    Writer to render results to
     * @param tag       从中部署的修订标记
     * @param cn        要安装的应用程序的名称
     * @param smClient  当前客户端的区域的i18n 支持
     */
    protected void deploy(PrintWriter writer, ContextName cn, String tag,
            StringManager smClient) {

        // NOTE: 假设update在该方法中是 true.

        // 验证请求的上下文路径
        if (!validateContextName(cn, writer, smClient)) {
            return;
        }

        String baseName = cn.getBaseName();
        String name = cn.getName();
        String displayPath = cn.getDisplayName();

        // Find the local WAR file
        File localWar = new File(new File(versioned, tag), baseName + ".war");

        File deployedWar = new File(host.getAppBaseFile(), baseName + ".war");

        // Copy WAR to appBase
        try {
            if (isServiced(name)) {
                writer.println(smClient.getString("managerServlet.inService", displayPath));
            } else {
                addServiced(name);
                try {
                    if (!deployedWar.delete()) {
                        writer.println(smClient.getString("managerServlet.deleteFail",
                                deployedWar));
                        return;
                    }
                    copy(localWar, deployedWar);
                    // Perform new deployment
                    check(name);
                } finally {
                    removeServiced(name);
                }
            }
        } catch (Exception e) {
            log("managerServlet.check[" + displayPath + "]", e);
            writer.println(smClient.getString("managerServlet.exception",
                    e.toString()));
            return;
        }

        writeDeployResult(writer, smClient, name, displayPath);
    }


    /**
     * 从指定的Web应用程序存档中安装指定路径的应用程序.
     *
     * @param writer    Writer to render results to
     * @param config    要安装的上下文配置文件的URL
     * @param cn        要安装的应用程序的名称
     * @param war       要安装的Web应用程序归档的URL
     * @param update    true 在路径上重写任何现有的webapp
     * @param smClient  当前客户端的区域的i18n 支持
     */
    protected void deploy(PrintWriter writer, String config, ContextName cn,
            String war, boolean update, StringManager smClient) {

        if (config != null && config.length() == 0) {
            config = null;
        }
        if (war != null && war.length() == 0) {
            war = null;
        }

        if (debug >= 1) {
            if (config != null && config.length() > 0) {
                if (war != null) {
                    log("install: Installing context configuration at '" +
                            config + "' from '" + war + "'");
                } else {
                    log("install: Installing context configuration at '" +
                            config + "'");
                }
            } else {
                if (cn != null) {
                    log("install: Installing web application '" + cn +
                            "' from '" + war + "'");
                } else {
                    log("install: Installing web application from '" + war + "'");
                }
            }
        }

        if (!validateContextName(cn, writer, smClient)) {
            return;
        }
        @SuppressWarnings("null") // checked in call above
        String name = cn.getName();
        String baseName = cn.getBaseName();
        String displayPath = cn.getDisplayName();

        // If app exists deployment can only proceed if update is true
        // Note existing files will be deleted and then replaced
        Context context = (Context) host.findChild(name);
        if (context != null && !update) {
            writer.println(smClient.getString("managerServlet.alreadyContext",
                    displayPath));
            return;
        }

        if (config != null && (config.startsWith("file:"))) {
            config = config.substring("file:".length());
        }
        if (war != null && (war.startsWith("file:"))) {
            war = war.substring("file:".length());
        }

        try {
            if (isServiced(name)) {
                writer.println(smClient.getString("managerServlet.inService", displayPath));
            } else {
                addServiced(name);
                try {
                    if (config != null) {
                        if (!configBase.mkdirs() && !configBase.isDirectory()) {
                            writer.println(smClient.getString(
                                    "managerServlet.mkdirFail",configBase));
                            return;
                        }
                        File localConfig = new File(configBase, baseName + ".xml");
                        if (localConfig.isFile() && !localConfig.delete()) {
                            writer.println(smClient.getString(
                                    "managerServlet.deleteFail", localConfig));
                            return;
                        }
                        copy(new File(config), localConfig);
                    }
                    if (war != null) {
                        File localWar;
                        if (war.endsWith(".war")) {
                            localWar = new File(host.getAppBaseFile(), baseName + ".war");
                        } else {
                            localWar = new File(host.getAppBaseFile(), baseName);
                        }
                        if (localWar.exists() && !ExpandWar.delete(localWar)) {
                            writer.println(smClient.getString(
                                    "managerServlet.deleteFail", localWar));
                            return;
                        }
                        copy(new File(war), localWar);
                    }
                    // Perform new deployment
                    check(name);
                } finally {
                    removeServiced(name);
                }
            }
            writeDeployResult(writer, smClient, name, displayPath);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log("ManagerServlet.install[" + displayPath + "]", t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }

    }


    private void writeDeployResult(PrintWriter writer, StringManager smClient,
            String name, String displayPath) {
        Context deployed = (Context) host.findChild(name);
        if (deployed != null && deployed.getConfigured() &&
                deployed.getState().isAvailable()) {
            writer.println(smClient.getString(
                    "managerServlet.deployed", displayPath));
        } else if (deployed!=null && !deployed.getState().isAvailable()) {
            writer.println(smClient.getString(
                    "managerServlet.deployedButNotStarted", displayPath));
        } else {
            // Something failed
            writer.println(smClient.getString("managerServlet.deployFailed", displayPath));
        }
    }


    /**
     * 在虚拟主机中呈现当前活动Context的列表.
     *
     * @param writer Writer to render to
     * @param smClient 当前客户端的区域的i18n 支持
     */
    protected void list(PrintWriter writer, StringManager smClient) {

        if (debug >= 1)
            log("list: Listing contexts for virtual host '" + host.getName() + "'");

        writer.println(smClient.getString("managerServlet.listed",
                                    host.getName()));
        Container[] contexts = host.findChildren();
        for (int i = 0; i < contexts.length; i++) {
            Context context = (Context) contexts[i];
            if (context != null ) {
                String displayPath = context.getPath();
                if( displayPath.equals("") )
                    displayPath = "/";
                if (context.getState().isAvailable()) {
                    writer.println(smClient.getString("managerServlet.listitem",
                            displayPath,
                            "running",
                            "" + context.getManager().findSessions().length,
                            context.getDocBase()));
                } else {
                    writer.println(smClient.getString("managerServlet.listitem",
                            displayPath,
                            "stopped",
                            "0",
                            context.getDocBase()));
                }
            }
        }
    }


    /**
     * 在指定的上下文路径上重新加载Web应用程序.
     *
     * @param writer Writer to render to
     * @param cn 要重新启动的应用程序的名称
     * @param smClient 当前客户端的区域的i18n 支持
     */
    protected void reload(PrintWriter writer, ContextName cn,
            StringManager smClient) {

        if (debug >= 1)
            log("restart: Reloading web application '" + cn + "'");

        if (!validateContextName(cn, writer, smClient)) {
            return;
        }

        try {
            Context context = (Context) host.findChild(cn.getName());
            if (context == null) {
                writer.println(smClient.getString("managerServlet.noContext",
                        Escape.htmlElementContent(cn.getDisplayName())));
                return;
            }
            // It isn't possible for the manager to reload itself
            if (context.getName().equals(this.context.getName())) {
                writer.println(smClient.getString("managerServlet.noSelf"));
                return;
            }
            context.reload();
            writer.println(smClient.getString("managerServlet.reloaded",
                    cn.getDisplayName()));
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log("ManagerServlet.reload[" + cn.getDisplayName() + "]", t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }
    }


    /**
     * 呈现可用的全局JNDI资源列表.
     *
     * @param writer Writer to render to
     * @param type 资源类型的完全限定类名, 或<code>null</code>列出所有类型的资源
     * @param smClient 当前客户端的区域的i18n 支持
     */
    protected void resources(PrintWriter writer, String type,
            StringManager smClient) {

        if (debug >= 1) {
            if (type != null) {
                log("resources:  Listing resources of type " + type);
            } else {
                log("resources:  Listing resources of all types");
            }
        }

        // Is the global JNDI resources context available?
        if (global == null) {
            writer.println(smClient.getString("managerServlet.noGlobal"));
            return;
        }

        // Enumerate the global JNDI resources of the requested type
        if (type != null) {
            writer.println(smClient.getString("managerServlet.resourcesType",
                    type));
        } else {
            writer.println(smClient.getString("managerServlet.resourcesAll"));
        }

        Class<?> clazz = null;
        try {
            if (type != null) {
                clazz = Class.forName(type);
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log("ManagerServlet.resources[" + type + "]", t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
            return;
        }

        printResources(writer, "", global, type, clazz, smClient);

    }


    /**
     * 列出给定上下文的资源.
     * 
     * @param writer Writer to render to
     * @param prefix 递归路径
     * @param namingContext 查找的命名上下文
     * @param type 资源类型的完全限定类名, 或<code>null</code>列出所有类型的资源
     * @param clazz 资源类或 <code>null</code>列出所有类型的资源
     * @param smClient 当前客户端的区域的i18n 支持
     */
    protected void printResources(PrintWriter writer, String prefix,
                                  javax.naming.Context namingContext,
                                  String type, Class<?> clazz,
                                  StringManager smClient) {

        try {
            NamingEnumeration<Binding> items = namingContext.listBindings("");
            while (items.hasMore()) {
                Binding item = items.next();
                if (item.getObject() instanceof javax.naming.Context) {
                    printResources
                        (writer, prefix + item.getName() + "/",
                         (javax.naming.Context) item.getObject(), type, clazz,
                         smClient);
                } else {
                    if ((clazz != null) &&
                        (!(clazz.isInstance(item.getObject())))) {
                        continue;
                    }
                    writer.print(prefix + item.getName());
                    writer.print(':');
                    writer.print(item.getClassName());
                    // Do we want a description if available?
                    writer.println();
                }
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log("ManagerServlet.resources[" + type + "]", t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }

    }


    /**
     * 写入系统操作系统和JVM属性.
     * 
     * @param writer Writer to render to
     * @param smClient 当前客户端的区域的i18n 支持
    */
    protected void serverinfo(PrintWriter writer,  StringManager smClient) {
        if (debug >= 1)
            log("serverinfo");
        try {
            StringBuilder props = new StringBuilder();
            props.append("OK - Server info");
            props.append("\nTomcat Version: ");
            props.append(ServerInfo.getServerInfo());
            props.append("\nOS Name: ");
            props.append(System.getProperty("os.name"));
            props.append("\nOS Version: ");
            props.append(System.getProperty("os.version"));
            props.append("\nOS Architecture: ");
            props.append(System.getProperty("os.arch"));
            props.append("\nJVM Version: ");
            props.append(System.getProperty("java.runtime.version"));
            props.append("\nJVM Vendor: ");
            props.append(System.getProperty("java.vm.vendor"));
            writer.println(props.toString());
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            getServletContext().log("ManagerServlet.serverinfo",t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }
    }

    /**
     * Web应用程序在指定上下文路径中的会话信息.
     * Displays a profile of session thisAccessedTime listing number
     * of sessions for each 10 minute interval up to 10 hours.
     *
     * @param writer Writer to render to
     * @param cn 列出会话信息的应用程序的名称
     * @param idle 以空闲时间过期所有会话
     * @param smClient 当前客户端的区域的i18n 支持
     */
    protected void sessions(PrintWriter writer, ContextName cn, int idle, StringManager smClient) {

        if (debug >= 1) {
            log("sessions: Session information for web application '" + cn + "'");
            if (idle >= 0)
                log("sessions: Session expiration for " + idle + " minutes '" + cn + "'");
        }

        if (!validateContextName(cn, writer, smClient)) {
            return;
        }

        String displayPath = cn.getDisplayName();

        try {
            Context context = (Context) host.findChild(cn.getName());
            if (context == null) {
                writer.println(smClient.getString("managerServlet.noContext",
                        Escape.htmlElementContent(displayPath)));
                return;
            }
            Manager manager = context.getManager() ;
            if(manager == null) {
                writer.println(smClient.getString("managerServlet.noManager",
                        Escape.htmlElementContent(displayPath)));
                return;
            }
            int maxCount = 60;
            int histoInterval = 1;
            int maxInactiveInterval = context.getSessionTimeout();
            if (maxInactiveInterval > 0) {
                histoInterval = maxInactiveInterval / maxCount;
                if (histoInterval * maxCount < maxInactiveInterval)
                    histoInterval++;
                if (0 == histoInterval)
                    histoInterval = 1;
                maxCount = maxInactiveInterval / histoInterval;
                if (histoInterval * maxCount < maxInactiveInterval)
                    maxCount++;
            }

            writer.println(smClient.getString("managerServlet.sessions",
                    displayPath));
            writer.println(smClient.getString(
                    "managerServlet.sessiondefaultmax",
                    "" + maxInactiveInterval));
            Session [] sessions = manager.findSessions();
            int[] timeout = new int[maxCount + 1];
            int notimeout = 0;
            int expired = 0;
            for (int i = 0; i < sessions.length; i++) {
                int time = (int) (sessions[i].getIdleTimeInternal() / 1000L);
                if (idle >= 0 && time >= idle*60) {
                    sessions[i].expire();
                    expired++;
                }
                time=time/60/histoInterval;
                if (time < 0)
                    notimeout++;
                else if (time >= maxCount)
                    timeout[maxCount]++;
                else
                    timeout[time]++;
            }
            if (timeout[0] > 0)
                writer.println(smClient.getString(
                        "managerServlet.sessiontimeout",
                        "<" + histoInterval, "" + timeout[0]));
            for (int i = 1; i < maxCount; i++) {
                if (timeout[i] > 0)
                    writer.println(smClient.getString(
                            "managerServlet.sessiontimeout",
                            "" + (i)*histoInterval + " - <" + (i+1)*histoInterval,
                            "" + timeout[i]));
            }
            if (timeout[maxCount] > 0) {
                writer.println(smClient.getString(
                        "managerServlet.sessiontimeout",
                        ">=" + maxCount*histoInterval,
                        "" + timeout[maxCount]));
            }
            if (notimeout > 0)
                writer.println(smClient.getString(
                        "managerServlet.sessiontimeout.unlimited",
                        "" + notimeout));
            if (idle >= 0)
                writer.println(smClient.getString(
                        "managerServlet.sessiontimeout.expired",
                        ">" + idle,"" + expired));
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log("ManagerServlet.sessions[" + displayPath + "]", t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }
    }


    /**
     * 提取到期请求参数
     *
     * @param writer Writer to render to
     * @param cn 列出会话信息的应用程序的名称
     * @param req Servlet请求
     * @param smClient 当前客户端的区域的i18n 支持
     */
    protected void expireSessions(PrintWriter writer, ContextName cn,
            HttpServletRequest req, StringManager smClient) {
        int idle = -1;
        String idleParam = req.getParameter("idle");
        if (idleParam != null) {
            try {
                idle = Integer.parseInt(idleParam);
            } catch (NumberFormatException e) {
                log("Could not parse idle parameter to an int: " + idleParam);
            }
        }
        sessions(writer, cn, idle, smClient);
    }

    /**
     * 在指定的上下文路径启动Web应用程序.
     *
     * @param writer Writer to render to
     * @param cn 要启动的应用程序的名称
     * @param smClient 当前客户端的区域的i18n 支持
     */
    protected void start(PrintWriter writer, ContextName cn,
            StringManager smClient) {

        if (debug >= 1)
            log("start: Starting web application '" + cn + "'");

        if (!validateContextName(cn, writer, smClient)) {
            return;
        }

        String displayPath = cn.getDisplayName();

        try {
            Context context = (Context) host.findChild(cn.getName());
            if (context == null) {
                writer.println(smClient.getString("managerServlet.noContext",
                        Escape.htmlElementContent(displayPath)));
                return;
            }
            context.start();
            if (context.getState().isAvailable())
                writer.println(smClient.getString("managerServlet.started",
                        displayPath));
            else
                writer.println(smClient.getString("managerServlet.startFailed",
                        displayPath));
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            getServletContext().log(sm.getString("managerServlet.startFailed",
                    displayPath), t);
            writer.println(smClient.getString("managerServlet.startFailed",
                    displayPath));
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }
    }


    /**
     * 在指定的上下文路径上停止Web应用程序.
     *
     * @param writer Writer to render to
     * @param cn 要停止的应用程序的名称
     * @param smClient 当前客户端的区域的i18n 支持
     */
    protected void stop(PrintWriter writer, ContextName cn,
            StringManager smClient) {

        if (debug >= 1)
            log("stop: Stopping web application '" + cn + "'");

        if (!validateContextName(cn, writer, smClient)) {
            return;
        }

        String displayPath = cn.getDisplayName();

        try {
            Context context = (Context) host.findChild(cn.getName());
            if (context == null) {
                writer.println(smClient.getString("managerServlet.noContext",
                        Escape.htmlElementContent(displayPath)));
                return;
            }
            // It isn't possible for the manager to stop itself
            if (context.getName().equals(this.context.getName())) {
                writer.println(smClient.getString("managerServlet.noSelf"));
                return;
            }
            context.stop();
            writer.println(smClient.getString(
                    "managerServlet.stopped", displayPath));
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log("ManagerServlet.stop[" + displayPath + "]", t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }

    }


    /**
     * 在指定的上下文路径中取消部署Web应用程序.
     *
     * @param writer Writer to render to
     * @param cn 要删除的应用程序的名称
     * @param smClient 当前客户端的区域的i18n 支持
     */
    protected void undeploy(PrintWriter writer, ContextName cn,
            StringManager smClient) {

        if (debug >= 1)
            log("undeploy: Undeploying web application at '" + cn + "'");

        if (!validateContextName(cn, writer, smClient)) {
            return;
        }

        String name = cn.getName();
        String baseName = cn.getBaseName();
        String displayPath = cn.getDisplayName();

        try {

            // Validate the Context of the specified application
            Context context = (Context) host.findChild(name);
            if (context == null) {
                writer.println(smClient.getString("managerServlet.noContext",
                        Escape.htmlElementContent(displayPath)));
                return;
            }

            if (!isDeployed(name)) {
                writer.println(smClient.getString("managerServlet.notDeployed",
                        Escape.htmlElementContent(displayPath)));
                return;
            }

            if (isServiced(name)) {
                writer.println(smClient.getString("managerServlet.inService", displayPath));
            } else {
                addServiced(name);
                try {
                    // Try to stop the context first to be nicer
                    context.stop();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                }
                try {
                    File war = new File(host.getAppBaseFile(), baseName + ".war");
                    File dir = new File(host.getAppBaseFile(), baseName);
                    File xml = new File(configBase, baseName + ".xml");
                    if (war.exists() && !war.delete()) {
                        writer.println(smClient.getString(
                                "managerServlet.deleteFail", war));
                        return;
                    } else if (dir.exists() && !undeployDir(dir)) {
                        writer.println(smClient.getString(
                                "managerServlet.deleteFail", dir));
                        return;
                    } else if (xml.exists() && !xml.delete()) {
                        writer.println(smClient.getString(
                                "managerServlet.deleteFail", xml));
                        return;
                    }
                    // Perform new deployment
                    check(name);
                } finally {
                    removeServiced(name);
                }
            }
            writer.println(smClient.getString("managerServlet.undeployed",
                    displayPath));
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log("ManagerServlet.undeploy[" + displayPath + "]", t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }
    }


    // -------------------------------------------------------- Support Methods


    /**
     * 在部署程序上执行 isDeployed方法.
     *
     * @param name The webapp name
     * @return <code>true</code>如果部署了具有该名称的webapp
     * @throws Exception Propagate JMX invocation error
     */
    protected boolean isDeployed(String name)
        throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        Boolean result =
            (Boolean) mBeanServer.invoke(oname, "isDeployed", params, signature);
        return result.booleanValue();
    }


    /**
     * 在部署程序上执行 check方法.
     *
     * @param name The webapp name
     * @throws Exception Propagate JMX invocation error
     */
    protected void check(String name)
        throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "check", params, signature);
    }


    /**
     * 在部署程序上执行 isServiced方法.
     *
     * @param name The webapp name
     * @return <code>true</code> 如果有这个名称的webapp正在被服务
     * @throws Exception Propagate JMX invocation error
     */
    protected boolean isServiced(String name)
        throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        Boolean result =
            (Boolean) mBeanServer.invoke(oname, "isServiced", params, signature);
        return result.booleanValue();
    }


    /**
     * 在部署程序上执行 addServiced方法.
     *
     * @param name The webapp name
     * @throws Exception Propagate JMX invocation error
     */
    protected void addServiced(String name)
        throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "addServiced", params, signature);
    }


    /**
     * 在部署程序上执行 removeServiced方法.
     *
     * @param name The webapp name
     * @throws Exception Propagate JMX invocation error
     */
    protected void removeServiced(String name)
        throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "removeServiced", params, signature);
    }


    /**
     * 删除指定的目录, 递归地包含所有的内容和子目录. 假定目录存在.
     *
     * @param dir 要删除的目录.
     * @return <code>true</code>如果成功删除
     */
    protected boolean undeployDir(File dir) {

        String files[] = dir.list();
        if (files == null) {
            files = new String[0];
        }
        for (int i = 0; i < files.length; i++) {
            File file = new File(dir, files[i]);
            if (file.isDirectory()) {
                if (!undeployDir(file)) {
                    return false;
                }
            } else {
                if (!file.delete()) {
                    return false;
                }
            }
        }
        return dir.delete();
    }


    /**
     * 上传这个请求中包含的WAR文件, 并将其存储在指定的文件位置.
     *
     * @param writer    Writer to render to
     * @param request   正在处理的servlet请求
     * @param war       应该存储上传的WAR的文件
     * @param smClient  用于基于客户端的区域的构造i18n消息的StringManager
     *
     * @exception IOException 如果在处理过程中发生I/O错误
     */
    protected void uploadWar(PrintWriter writer, HttpServletRequest request,
            File war, StringManager smClient) throws IOException {

        if (war.exists() && !war.delete()) {
            String msg = smClient.getString("managerServlet.deleteFail", war);
            throw new IOException(msg);
        }

        try (ServletInputStream istream = request.getInputStream();
                BufferedOutputStream ostream =
                        new BufferedOutputStream(new FileOutputStream(war), 1024)) {
            byte buffer[] = new byte[1024];
            while (true) {
                int n = istream.read(buffer);
                if (n < 0) {
                    break;
                }
                ostream.write(buffer, 0, n);
            }
        } catch (IOException e) {
            if (war.exists() && !war.delete()) {
                writer.println(
                        smClient.getString("managerServlet.deleteFail", war));
            }
            throw e;
        }

    }


    protected static boolean validateContextName(ContextName cn,
            PrintWriter writer, StringManager sm) {

        // ContextName should be non-null with a path that is empty or starts
        // with /
        if (cn != null &&
                (cn.getPath().startsWith("/") || cn.getPath().equals(""))) {
            return true;
        }

        String path = null;
        if (cn != null) {
            path = Escape.htmlElementContent(cn.getPath());
        }
        writer.println(sm.getString("managerServlet.invalidPath", path));
        return false;
    }

    /**
     * 将指定的文件或目录复制到目的地.
     *
     * @param src 资源
     * @param dest 目标
     * @return <code>true</code>如果成功复制
     */
    public static boolean copy(File src, File dest) {
        boolean result = false;
        try {
            if( src != null &&
                    !src.getCanonicalPath().equals(dest.getCanonicalPath()) ) {
                result = copyInternal(src, dest, new byte[4096]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }


    /**
     * 将指定的文件或目录复制到目的地.
     *
     * @param src 资源
     * @param dest 目标
     * @param buf 临时字节缓冲器
     * @return <code>true</code>如果成功复制
     */
    public static boolean copyInternal(File src, File dest, byte[] buf) {

        boolean result = true;

        String files[] = null;
        if (src.isDirectory()) {
            files = src.list();
            result = dest.mkdir();
        } else {
            files = new String[1];
            files[0] = "";
        }
        if (files == null) {
            files = new String[0];
        }
        for (int i = 0; (i < files.length) && result; i++) {
            File fileSrc = new File(src, files[i]);
            File fileDest = new File(dest, files[i]);
            if (fileSrc.isDirectory()) {
                result = copyInternal(fileSrc, fileDest, buf);
            } else {
                try (FileInputStream is = new FileInputStream(fileSrc);
                        FileOutputStream os = new FileOutputStream(fileDest)){
                    int len = 0;
                    while (true) {
                        len = is.read(buf);
                        if (len == -1)
                            break;
                        os.write(buf, 0, len);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    result = false;
                }
            }
        }
        return result;
    }


    protected Map<String,List<String>> getConnectorCiphers() {
        Map<String,List<String>> result = new HashMap<>();

        Engine e = (Engine) host.getParent();
        Service s = e.getService();
        Connector connectors[] = s.findConnectors();
        for (Connector connector : connectors) {
            if (Boolean.TRUE.equals(connector.getProperty("SSLEnabled"))) {
                SSLHostConfig[] sslHostConfigs = connector.getProtocolHandler().findSslHostConfigs();
                for (SSLHostConfig sslHostConfig : sslHostConfigs) {
                    String name = connector.toString() + "-" + sslHostConfig.getHostName();
                    /* Add cipher list, keep order but remove duplicates */
                    result.put(name, new ArrayList<>(new LinkedHashSet<>(
                        Arrays.asList(sslHostConfig.getEnabledCiphers()))));
                }
            } else {
                ArrayList<String> cipherList = new ArrayList<>(1);
                cipherList.add(sm.getString("managerServlet.notSslConnector"));
                result.put(connector.toString(), cipherList);
            }
        }
        return result;
    }
}
