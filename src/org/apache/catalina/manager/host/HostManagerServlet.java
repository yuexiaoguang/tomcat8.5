package org.apache.catalina.manager.host;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringTokenizer;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.HostConfig;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * 启用服务器上安装的虚拟主机的远程管理的Servlet.
 * 通常, 此功能将受到Web应用程序部署描述符中的安全约束的保护. 但是, 在测试过程中可以放宽这一要求.
 * <p>
 * 这个servlet 检查<code>getPathInfo()</code>返回的值，并和相关的查询参数来确定正在请求什么操作.
 * 支持下列动作和参数(从servlet路径开始) :
 * <ul>
 * <li><b>/add?name={host-name}&amp;aliases={host-aliases}&amp;manager={manager}</b> - 创建并添加新的虚拟主机.
 * 		<code>host-name</code>属性表示新主机的名称. <code>host-aliases</code>属性是主机别名的逗号分隔列表.
 *     <code>manager</code>属性是一个 boolean值指示WebApp管理器是否将安装在新创建的主机中(可选, 默认false).</li>
 * <li><b>/remove?name={host-name}</b> - 删除虚拟主机. <code>host-name</code>属性表示主机名.</li>
 * <li><b>/list</b> - 列出服务器上安装的虚拟主机. 每个主机将以以下格式<code>host-name#host-aliases</code>列出.</li>
 * <li><b>/start?name={host-name}</b> - 启动虚拟主机.</li>
 * <li><b>/stop?name={host-name}</b> - 停止虚拟主机.</li>
 * </ul>
 * <p>
 * <b>NOTE</b> - 试图停止或删除包含此servlet自身的主机将无法成功. 因此, 这个servlet 一般部署在单独的虚拟主机中.
 * <p>
 * 以下servlet初始化参数被识别:
 * <ul>
 * <li><b>debug</b> - 调试详细信息级别，控制由该servlet记录的信息量.  默认为零.
 * </ul>
 */
public class HostManagerServlet extends HttpServlet implements ContainerServlet {

    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------- Instance Variables


    /**
     * Web应用管理的Context容器.
     */
    protected transient Context context = null;


    /**
     * 调试等级.
     */
    protected int debug = 1;


    /**
     * 关联的主机.
     */
    protected transient Host installedHost = null;


    /**
     * 关联的引擎.
     */
    protected transient Engine engine = null;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 这个servlet关联的Wrapper容器.
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
            installedHost = null;
            engine = null;
        } else {
            context = (Context) wrapper.getParent();
            installedHost = (Host) context.getParent();
            engine = (Engine) installedHost.getParent();
        }
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

        // 识别需要的请求参数
        String command = request.getPathInfo();
        if (command == null)
            command = request.getServletPath();
        String name = request.getParameter("name");

        // Prepare our output writer to generate the response message
        response.setContentType("text/plain; charset=" + Constants.CHARSET);
        PrintWriter writer = response.getWriter();

        // Process the requested command
        if (command == null) {
            writer.println(sm.getString("hostManagerServlet.noCommand"));
        } else if (command.equals("/add")) {
            add(request, writer, name, false, smClient);
        } else if (command.equals("/remove")) {
            remove(writer, name, smClient);
        } else if (command.equals("/list")) {
            list(writer, smClient);
        } else if (command.equals("/start")) {
            start(writer, name, smClient);
        } else if (command.equals("/stop")) {
            stop(writer, name, smClient);
        } else if (command.equals("/persist")) {
            persist(writer, smClient);
        } else {
            writer.println(sm.getString("hostManagerServlet.unknownCommand",
                                        command));
        }

        // Finish up the response
        writer.flush();
        writer.close();

    }

    /**
     * 用给定的参数添加主机.
     *
     * @param request 请求
     * @param writer 输出writer
     * @param name 主机名
     * @param htmlMode Flag value
     * @param smClient 客户端区域的StringManager
    */
    protected void add(HttpServletRequest request, PrintWriter writer,
            String name, boolean htmlMode, StringManager smClient) {
        String aliases = request.getParameter("aliases");
        String appBase = request.getParameter("appBase");
        boolean manager = booleanParameter(request, "manager", false, htmlMode);
        boolean autoDeploy = booleanParameter(request, "autoDeploy", true, htmlMode);
        boolean deployOnStartup = booleanParameter(request, "deployOnStartup", true, htmlMode);
        boolean deployXML = booleanParameter(request, "deployXML", true, htmlMode);
        boolean unpackWARs = booleanParameter(request, "unpackWARs", true, htmlMode);
        boolean copyXML = booleanParameter(request, "copyXML", false, htmlMode);
        add(writer, name, aliases, appBase, manager,
            autoDeploy,
            deployOnStartup,
            deployXML,
            unpackWARs,
            copyXML,
            smClient);
    }


    /**
     * @param request Servlet请求
     * @param parameter 参数名称
     * @param theDefault 默认值
     * @param htmlMode Flag value
     * 
     * @return the boolean value for the parameter
     */
    protected boolean booleanParameter(HttpServletRequest request,
            String parameter, boolean theDefault, boolean htmlMode) {
        String value = request.getParameter(parameter);
        boolean booleanValue = theDefault;
        if (value != null) {
            if (htmlMode) {
                if (value.equals("on")) {
                    booleanValue = true;
                }
            } else if (theDefault) {
                if (value.equals("false")) {
                    booleanValue = false;
                }
            } else if (value.equals("true")) {
                booleanValue = true;
            }
        } else if (htmlMode)
            booleanValue = false;
        return booleanValue;
    }


    @Override
    public void init() throws ServletException {

        // 确保已经设置ContainerServlet属性
        if ((wrapper == null) || (context == null))
            throw new UnavailableException
                (sm.getString("hostManagerServlet.noWrapper"));

        // 从初始化参数设置属性
        String value = null;
        try {
            value = getServletConfig().getInitParameter("debug");
            debug = Integer.parseInt(value);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
        }

    }



    // -------------------------------------------------------- Private Methods


    /**
     * 使用指定的参数添加主机.
     *
     * @param writer Writer to render results to
     * @param name 主机名
     * @param aliases 逗号分隔的别名列表
     * @param appBase application base for the host
     * @param manager 是否应该将管理器WebApp部署到新主机上?
     * @param autoDeploy Flag value
     * @param deployOnStartup Flag value
     * @param deployXML Flag value
     * @param unpackWARs Flag value
     * @param copyXML Flag value
     * @param smClient 客户端的区域的StringManager
     */
    protected synchronized void add(PrintWriter writer, String name, String aliases, String appBase,
         boolean manager,
         boolean autoDeploy,
         boolean deployOnStartup,
         boolean deployXML,
         boolean unpackWARs,
         boolean copyXML,
         StringManager smClient) {
        if (debug >= 1) {
            log(sm.getString("hostManagerServlet.add", name));
        }

        // 验证请求的主机名
        if ((name == null) || name.length() == 0) {
            writer.println(smClient.getString(
                    "hostManagerServlet.invalidHostName", name));
            return;
        }

        // 检查主机是否已经存在
        if (engine.findChild(name) != null) {
            writer.println(smClient.getString(
                    "hostManagerServlet.alreadyHost", name));
            return;
        }

        // 验证并创建 appBase
        File appBaseFile = null;
        File file = null;
        String applicationBase = appBase;
        if (applicationBase == null || applicationBase.length() == 0) {
            applicationBase = name;
        }
        file = new File(applicationBase);
        if (!file.isAbsolute())
            file = new File(engine.getCatalinaBase(), file.getPath());
        try {
            appBaseFile = file.getCanonicalFile();
        } catch (IOException e) {
            appBaseFile = file;
        }
        if (!appBaseFile.mkdirs() && !appBaseFile.isDirectory()) {
            writer.println(smClient.getString(
                    "hostManagerServlet.appBaseCreateFail",
                    appBaseFile.toString(), name));
            return;
        }

        // 为配置文件创建基础
        File configBaseFile = getConfigBase(name);

        // 复制 manager.xml
        if (manager) {
            if (configBaseFile == null) {
                writer.println(smClient.getString(
                        "hostManagerServlet.configBaseCreateFail", name));
                return;
            }
            try (InputStream is = getServletContext().getResourceAsStream("/manager.xml")) {
                Path dest = (new File(configBaseFile, "manager.xml")).toPath();
                Files.copy(is, dest);
            } catch (IOException e) {
                writer.println(smClient.getString("hostManagerServlet.managerXml"));
                return;
            }
        }

        StandardHost host = new StandardHost();
        host.setAppBase(applicationBase);
        host.setName(name);

        host.addLifecycleListener(new HostConfig());

        // Add host aliases
        if ((aliases != null) && !("".equals(aliases))) {
            StringTokenizer tok = new StringTokenizer(aliases, ", ");
            while (tok.hasMoreTokens()) {
                host.addAlias(tok.nextToken());
            }
        }
        host.setAutoDeploy(autoDeploy);
        host.setDeployOnStartup(deployOnStartup);
        host.setDeployXML(deployXML);
        host.setUnpackWARs(unpackWARs);
        host.setCopyXML(copyXML);

        // Add new host
        try {
            engine.addChild(host);
        } catch (Exception e) {
            writer.println(smClient.getString("hostManagerServlet.exception",
                    e.toString()));
            return;
        }

        host = (StandardHost) engine.findChild(name);
        if (host != null) {
            writer.println(smClient.getString("hostManagerServlet.add", name));
        } else {
            // Something failed
            writer.println(smClient.getString(
                    "hostManagerServlet.addFailed", name));
        }

    }


    /**
     * 删除指定主机.
     *
     * @param writer Writer to render results to
     * @param name 主机名
     * @param smClient 客户端区域的StringManager
     */
    protected synchronized void remove(PrintWriter writer, String name,
            StringManager smClient) {

        if (debug >= 1) {
            log(sm.getString("hostManagerServlet.remove", name));
        }

        // 验证请求的主机名
        if ((name == null) || name.length() == 0) {
            writer.println(smClient.getString(
                    "hostManagerServlet.invalidHostName", name));
            return;
        }

        // 检查主机是否存在
        if (engine.findChild(name) == null) {
            writer.println(smClient.getString(
                    "hostManagerServlet.noHost", name));
            return;
        }

        // 防止删除拥有的主机
        if (engine.findChild(name) == installedHost) {
            writer.println(smClient.getString(
                    "hostManagerServlet.cannotRemoveOwnHost", name));
            return;
        }

        // Remove host
        // 注意，主机将不会被物理移除
        try {
            Container child = engine.findChild(name);
            engine.removeChild(child);
            if ( child instanceof ContainerBase ) ((ContainerBase)child).destroy();
        } catch (Exception e) {
            writer.println(smClient.getString("hostManagerServlet.exception",
                    e.toString()));
            return;
        }

        Host host = (StandardHost) engine.findChild(name);
        if (host == null) {
            writer.println(smClient.getString(
                    "hostManagerServlet.remove", name));
        } else {
            // Something failed
            writer.println(smClient.getString(
                    "hostManagerServlet.removeFailed", name));
        }

    }


    /**
     * 在虚拟主机中呈现当前活动上下文的列表.
     *
     * @param writer Writer to render to
     * @param smClient StringManager for the client's locale
     */
    protected void list(PrintWriter writer, StringManager smClient) {

        if (debug >= 1) {
            log(sm.getString("hostManagerServlet.list", engine.getName()));
        }

        writer.println(smClient.getString("hostManagerServlet.listed",
                engine.getName()));
        Container[] hosts = engine.findChildren();
        for (int i = 0; i < hosts.length; i++) {
            Host host = (Host) hosts[i];
            String name = host.getName();
            String[] aliases = host.findAliases();
            writer.println(smClient.getString("hostManagerServlet.listitem",
                    name, StringUtils.join(aliases)));
        }
    }


    /**
     * 启动指定名称的主机.
     *
     * @param writer Writer to render to
     * @param name 主机名
     * @param smClient StringManager for the client's locale
     */
    protected void start(PrintWriter writer, String name,
            StringManager smClient) {

        if (debug >= 1) {
            log(sm.getString("hostManagerServlet.start", name));
        }

        // 验证请求的主机名
        if ((name == null) || name.length() == 0) {
            writer.println(smClient.getString(
                    "hostManagerServlet.invalidHostName", name));
            return;
        }

        Container host = engine.findChild(name);

        // 检查主机是否存在
        if (host == null) {
            writer.println(smClient.getString(
                    "hostManagerServlet.noHost", name));
            return;
        }

        // 防止启动我们自己的主机
        if (host == installedHost) {
            writer.println(smClient.getString(
                    "hostManagerServlet.cannotStartOwnHost", name));
            return;
        }

        // 如果已经启动，不要启动主机
        if (host.getState().isAvailable()) {
            writer.println(smClient.getString(
                    "hostManagerServlet.alreadyStarted", name));
            return;
        }

        // Start host
        try {
            host.start();
            writer.println(smClient.getString(
                    "hostManagerServlet.started", name));
        } catch (Exception e) {
            getServletContext().log
                (sm.getString("hostManagerServlet.startFailed", name), e);
            writer.println(smClient.getString(
                    "hostManagerServlet.startFailed", name));
            writer.println(smClient.getString(
                    "hostManagerServlet.exception", e.toString()));
            return;
        }

    }


    /**
     * 停止指定名称的主机.
     *
     * @param writer Writer to render to
     * @param name 主机名
     * @param smClient StringManager for the client's locale
     */
    protected void stop(PrintWriter writer, String name,
            StringManager smClient) {

        if (debug >= 1) {
            log(sm.getString("hostManagerServlet.stop", name));
        }

        // 验证请求的主机名
        if ((name == null) || name.length() == 0) {
            writer.println(smClient.getString(
                    "hostManagerServlet.invalidHostName", name));
            return;
        }

        Container host = engine.findChild(name);

        // 检查主机是否存在
        if (host == null) {
            writer.println(smClient.getString("hostManagerServlet.noHost",
                    name));
            return;
        }

        // 防止停止拥有的主机
        if (host == installedHost) {
            writer.println(smClient.getString(
                    "hostManagerServlet.cannotStopOwnHost", name));
            return;
        }

        // Don't stop host if already stopped
        if (!host.getState().isAvailable()) {
            writer.println(smClient.getString(
                    "hostManagerServlet.alreadyStopped", name));
            return;
        }

        // Stop host
        try {
            host.stop();
            writer.println(smClient.getString("hostManagerServlet.stopped",
                    name));
        } catch (Exception e) {
            getServletContext().log(sm.getString(
                    "hostManagerServlet.stopFailed", name), e);
            writer.println(smClient.getString("hostManagerServlet.stopFailed",
                    name));
            writer.println(smClient.getString("hostManagerServlet.exception",
                    e.toString()));
            return;
        }

    }


    /**
     * 持久化当前配置到 server.xml.
     *
     * @param writer Writer to render to
     * @param smClient i18n resources localized for the client
     */
    protected void persist(PrintWriter writer, StringManager smClient) {

        if (debug >= 1) {
            log(sm.getString("hostManagerServlet.persist"));
        }

        try {
            MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName oname = new ObjectName(engine.getDomain() + ":type=StoreConfig");
            platformMBeanServer.invoke(oname, "storeConfig", null, null);
            writer.println(smClient.getString("hostManagerServlet.persisted"));
        } catch (Exception e) {
            getServletContext().log(sm.getString("hostManagerServlet.persistFailed"), e);
            writer.println(smClient.getString("hostManagerServlet.persistFailed"));
            // catch InstanceNotFoundException when StoreConfig is not enabled instead of printing
            // the failure message
            if (e instanceof InstanceNotFoundException) {
                writer.println("Please enable StoreConfig to use this feature.");
            } else {
                writer.println(smClient.getString("hostManagerServlet.exception", e.toString()));
            }
            return;
        }
    }


    // -------------------------------------------------------- Support Methods

    /**
     * 获取配置 base.
     * 
     * @param hostName 主机名
     * @return the config base for the host
     */
    protected File getConfigBase(String hostName) {
        File configBase = new File(context.getCatalinaBase(), "conf");
        if (!configBase.exists()) {
            return null;
        }
        if (engine != null) {
            configBase = new File(configBase, engine.getName());
        }
        if (installedHost != null) {
            configBase = new File(configBase, hostName);
        }
        if (!configBase.mkdirs() && !configBase.isDirectory()) {
            return null;
        }
        return configBase;
    }
}
