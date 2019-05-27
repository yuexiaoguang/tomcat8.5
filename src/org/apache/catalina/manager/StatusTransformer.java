package org.apache.catalina.manager;

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.security.Escape;

/**
 * 这是Servlet的重构，将输出化为一个简单类. 虽然可以使用XSLT, 不必要的复杂化.
 */
public class StatusTransformer {


    // --------------------------------------------------------- Public Methods


    public static void setContentType(HttpServletResponse response,
                                      int mode) {
        if (mode == 0){
            response.setContentType("text/html;charset="+Constants.CHARSET);
        } else if (mode == 1){
            response.setContentType("text/xml;charset="+Constants.CHARSET);
        }
    }


    /**
     * 写入一个HTML 或 XML header.
     *
     * @param writer 要使用的PrintWriter
     * @param args URL的路径前缀
     * @param mode - 0 = HTML header, 1 = XML declaration
     *
     */
    public static void writeHeader(PrintWriter writer, Object[] args, int mode) {
        if (mode == 0){
            // HTML Header Section
            writer.print(Constants.HTML_HEADER_SECTION);
        } else if (mode == 1){
            writer.write(Constants.XML_DECLARATION);
            writer.print(MessageFormat.format
                     (Constants.XML_STYLE, args));
            writer.write("<status>");
        }
    }


    /**
     * 写入header 主体.
     *
     * @param writer 输出 writer
     * @param args 要写入的参数
     * @param mode 0 意味着写入
     */
    public static void writeBody(PrintWriter writer, Object[] args, int mode) {
        if (mode == 0){
            writer.print(MessageFormat.format
                         (Constants.BODY_HEADER_SECTION, args));
        }
    }


    /**
     * 写入管理器 webapp信息.
     *
     * @param writer 输出writer
     * @param args 要写入的参数
     * @param mode 0 意味着写入
     */
    public static void writeManager(PrintWriter writer, Object[] args,
                                    int mode) {
        if (mode == 0){
            writer.print(MessageFormat.format(Constants.MANAGER_SECTION, args));
        }
    }


    public static void writePageHeading(PrintWriter writer, Object[] args,
                                        int mode) {
        if (mode == 0){
            writer.print(MessageFormat.format
                         (Constants.SERVER_HEADER_SECTION, args));
        }
    }


    public static void writeServerInfo(PrintWriter writer, Object[] args,
                                       int mode){
        if (mode == 0){
            writer.print(MessageFormat.format(Constants.SERVER_ROW_SECTION, args));
        }
    }


    public static void writeFooter(PrintWriter writer, int mode) {
        if (mode == 0){
            // HTML Tail Section
            writer.print(Constants.HTML_TAIL_SECTION);
        } else if (mode == 1){
            writer.write("</status>");
        }
    }


    /**
     * 写入 OS 状态.
     *
     * @param writer 输出writer
     * @param mode <code>0</code>将生成 HTML.
     *   <code>1</code>将生成 XML.
     */
    public static void writeOSState(PrintWriter writer, int mode) {
        long[] result = new long[16];
        boolean ok = false;
        try {
            String methodName = "info";
            Class<?> paramTypes[] = new Class[1];
            paramTypes[0] = result.getClass();
            Object paramValues[] = new Object[1];
            paramValues[0] = result;
            Method method = Class.forName("org.apache.tomcat.jni.OS")
                .getMethod(methodName, paramTypes);
            method.invoke(null, paramValues);
            ok = true;
        } catch (Throwable t) {
            t = ExceptionUtils.unwrapInvocationTargetException(t);
            ExceptionUtils.handleThrowable(t);
        }

        if (ok) {
            if (mode == 0){
                writer.print("<h1>OS</h1>");

                writer.print("<p>");
                writer.print(" Physical memory: ");
                writer.print(formatSize(Long.valueOf(result[0]), true));
                writer.print(" Available memory: ");
                writer.print(formatSize(Long.valueOf(result[1]), true));
                writer.print(" Total page file: ");
                writer.print(formatSize(Long.valueOf(result[2]), true));
                writer.print(" Free page file: ");
                writer.print(formatSize(Long.valueOf(result[3]), true));
                writer.print(" Memory load: ");
                writer.print(Long.valueOf(result[6]));
                writer.print("<br>");
                writer.print(" Process kernel time: ");
                writer.print(formatTime(Long.valueOf(result[11] / 1000), true));
                writer.print(" Process user time: ");
                writer.print(formatTime(Long.valueOf(result[12] / 1000), true));
                writer.print("</p>");
            } else if (mode == 1){
                // NO-OP
            }
        }

    }


    /**
     * 写入 VM 状态.
     * 
     * @param writer 输出writer
     * @param mode <code>0</code>将生成 HTML.
     *   			<code>1</code>将生成 XML.
     * @throws Exception Propagated JMX error
     */
    public static void writeVMState(PrintWriter writer, int mode)
        throws Exception {

        SortedMap<String, MemoryPoolMXBean> memoryPoolMBeans = new TreeMap<>();
        for (MemoryPoolMXBean mbean: ManagementFactory.getMemoryPoolMXBeans()) {
            String sortKey = mbean.getType() + ":" + mbean.getName();
            memoryPoolMBeans.put(sortKey, mbean);
        }

        if (mode == 0){
            writer.print("<h1>JVM</h1>");

            writer.print("<p>");
            writer.print(" Free memory: ");
            writer.print(formatSize(
                    Long.valueOf(Runtime.getRuntime().freeMemory()), true));
            writer.print(" Total memory: ");
            writer.print(formatSize(
                    Long.valueOf(Runtime.getRuntime().totalMemory()), true));
            writer.print(" Max memory: ");
            writer.print(formatSize(
                    Long.valueOf(Runtime.getRuntime().maxMemory()), true));
            writer.print("</p>");

            writer.write("<table border=\"0\"><thead><tr><th>Memory Pool</th><th>Type</th><th>Initial</th><th>Total</th><th>Maximum</th><th>Used</th></tr></thead><tbody>");
            for (MemoryPoolMXBean memoryPoolMBean : memoryPoolMBeans.values()) {
                MemoryUsage usage = memoryPoolMBean.getUsage();
                writer.write("<tr><td>");
                writer.print(memoryPoolMBean.getName());
                writer.write("</td><td>");
                writer.print(memoryPoolMBean.getType());
                writer.write("</td><td>");
                writer.print(formatSize(Long.valueOf(usage.getInit()), true));
                writer.write("</td><td>");
                writer.print(formatSize(Long.valueOf(usage.getCommitted()), true));
                writer.write("</td><td>");
                writer.print(formatSize(Long.valueOf(usage.getMax()), true));
                writer.write("</td><td>");
                writer.print(formatSize(Long.valueOf(usage.getUsed()), true));
                if (usage.getMax() > 0) {
                    writer.write(" ("
                            + (usage.getUsed() * 100 / usage.getMax()) + "%)");
                }
                writer.write("</td></tr>");
            }
            writer.write("</tbody></table>");
        } else if (mode == 1){
            writer.write("<jvm>");

            writer.write("<memory");
            writer.write(" free='" + Runtime.getRuntime().freeMemory() + "'");
            writer.write(" total='" + Runtime.getRuntime().totalMemory() + "'");
            writer.write(" max='" + Runtime.getRuntime().maxMemory() + "'/>");

            for (MemoryPoolMXBean memoryPoolMBean : memoryPoolMBeans.values()) {
                MemoryUsage usage = memoryPoolMBean.getUsage();
                writer.write("<memorypool");
                writer.write(" name='" + Escape.xml("", memoryPoolMBean.getName()) + "'");
                writer.write(" type='" + memoryPoolMBean.getType() + "'");
                writer.write(" usageInit='" + usage.getInit() + "'");
                writer.write(" usageCommitted='" + usage.getCommitted() + "'");
                writer.write(" usageMax='" + usage.getMax() + "'");
                writer.write(" usageUsed='" + usage.getUsed() + "'/>");
            }

            writer.write("</jvm>");
        }

    }


    /**
     * 写入连接器状态.
     * 
     * @param writer 输出writer
     * @param tpName 线程池的MBean名称
     * @param name 连接器名称
     * @param mBeanServer MBean服务器
     * @param globalRequestProcessors 全局请求处理器的MBean名称
     * @param requestProcessors 请求处理器的MBean名称
     * @param mode <code>0</code>将生成 HTML.
     *   			<code>1</code>将生成 XML.
     *   
     * @throws Exception Propagated JMX error
     */
    public static void writeConnectorState(PrintWriter writer,
            ObjectName tpName, String name, MBeanServer mBeanServer,
            Vector<ObjectName> globalRequestProcessors,
            Vector<ObjectName> requestProcessors, int mode) throws Exception {

        if (mode == 0) {
            writer.print("<h1>");
            writer.print(name);
            writer.print("</h1>");

            writer.print("<p>");
            writer.print(" Max threads: ");
            writer.print(mBeanServer.getAttribute(tpName, "maxThreads"));
            writer.print(" Current thread count: ");
            writer.print(mBeanServer.getAttribute(tpName, "currentThreadCount"));
            writer.print(" Current thread busy: ");
            writer.print(mBeanServer.getAttribute(tpName, "currentThreadsBusy"));
            try {
                Object value = mBeanServer.getAttribute(tpName, "keepAliveCount");
                writer.print(" Keep alive sockets count: ");
                writer.print(value);
            } catch (Exception e) {
                // Ignore
            }

            writer.print("<br>");

            ObjectName grpName = null;

            Enumeration<ObjectName> enumeration =
                globalRequestProcessors.elements();
            while (enumeration.hasMoreElements()) {
                ObjectName objectName = enumeration.nextElement();
                if (name.equals(objectName.getKeyProperty("name"))) {
                    grpName = objectName;
                }
            }

            if (grpName == null) {
                return;
            }

            writer.print(" Max processing time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (grpName, "maxTime"), false));
            writer.print(" Processing time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (grpName, "processingTime"), true));
            writer.print(" Request count: ");
            writer.print(mBeanServer.getAttribute(grpName, "requestCount"));
            writer.print(" Error count: ");
            writer.print(mBeanServer.getAttribute(grpName, "errorCount"));
            writer.print(" Bytes received: ");
            writer.print(formatSize(mBeanServer.getAttribute
                                    (grpName, "bytesReceived"), true));
            writer.print(" Bytes sent: ");
            writer.print(formatSize(mBeanServer.getAttribute
                                    (grpName, "bytesSent"), true));
            writer.print("</p>");

            writer.print("<table border=\"0\"><tr><th>Stage</th><th>Time</th><th>B Sent</th><th>B Recv</th><th>Client (Forwarded)</th><th>Client (Actual)</th><th>VHost</th><th>Request</th></tr>");

            enumeration = requestProcessors.elements();
            while (enumeration.hasMoreElements()) {
                ObjectName objectName = enumeration.nextElement();
                if (name.equals(objectName.getKeyProperty("worker"))) {
                    writer.print("<tr>");
                    writeProcessorState(writer, objectName, mBeanServer, mode);
                    writer.print("</tr>");
                }
            }

            writer.print("</table>");

            writer.print("<p>");
            writer.print("P: Parse and prepare request S: Service F: Finishing R: Ready K: Keepalive");
            writer.print("</p>");
        } else if (mode == 1){
            writer.write("<connector name='" + name + "'>");

            writer.write("<threadInfo ");
            writer.write(" maxThreads=\"" + mBeanServer.getAttribute(tpName, "maxThreads") + "\"");
            writer.write(" currentThreadCount=\"" + mBeanServer.getAttribute(tpName, "currentThreadCount") + "\"");
            writer.write(" currentThreadsBusy=\"" + mBeanServer.getAttribute(tpName, "currentThreadsBusy") + "\"");
            writer.write(" />");

            ObjectName grpName = null;

            Enumeration<ObjectName> enumeration =
                globalRequestProcessors.elements();
            while (enumeration.hasMoreElements()) {
                ObjectName objectName = enumeration.nextElement();
                if (name.equals(objectName.getKeyProperty("name"))) {
                    grpName = objectName;
                }
            }

            if (grpName != null) {

                writer.write("<requestInfo ");
                writer.write(" maxTime=\"" + mBeanServer.getAttribute(grpName, "maxTime") + "\"");
                writer.write(" processingTime=\"" + mBeanServer.getAttribute(grpName, "processingTime") + "\"");
                writer.write(" requestCount=\"" + mBeanServer.getAttribute(grpName, "requestCount") + "\"");
                writer.write(" errorCount=\"" + mBeanServer.getAttribute(grpName, "errorCount") + "\"");
                writer.write(" bytesReceived=\"" + mBeanServer.getAttribute(grpName, "bytesReceived") + "\"");
                writer.write(" bytesSent=\"" + mBeanServer.getAttribute(grpName, "bytesSent") + "\"");
                writer.write(" />");

                writer.write("<workers>");
                enumeration = requestProcessors.elements();
                while (enumeration.hasMoreElements()) {
                    ObjectName objectName = enumeration.nextElement();
                    if (name.equals(objectName.getKeyProperty("worker"))) {
                        writeProcessorState(writer, objectName, mBeanServer, mode);
                    }
                }
                writer.write("</workers>");
            }

            writer.write("</connector>");
        }

    }


    /**
     * 写入处理器状态.
     * 
     * @param writer 输出writer
     * @param pName 处理器的MBean名称
     * @param  mBeanServer MBean server
     * @param mode <code>0</code>将生成 HTML.
     *   			<code>1</code>将生成 XML.
     * @throws Exception Propagated JMX error
     */
    protected static void writeProcessorState(PrintWriter writer,
                                              ObjectName pName,
                                              MBeanServer mBeanServer,
                                              int mode)
        throws Exception {

        Integer stageValue =
            (Integer) mBeanServer.getAttribute(pName, "stage");
        int stage = stageValue.intValue();
        boolean fullStatus = true;
        boolean showRequest = true;
        String stageStr = null;

        switch (stage) {

        case (1/*org.apache.coyote.Constants.STAGE_PARSE*/):
            stageStr = "P";
            fullStatus = false;
            break;
        case (2/*org.apache.coyote.Constants.STAGE_PREPARE*/):
            stageStr = "P";
            fullStatus = false;
            break;
        case (3/*org.apache.coyote.Constants.STAGE_SERVICE*/):
            stageStr = "S";
            break;
        case (4/*org.apache.coyote.Constants.STAGE_ENDINPUT*/):
            stageStr = "F";
            break;
        case (5/*org.apache.coyote.Constants.STAGE_ENDOUTPUT*/):
            stageStr = "F";
            break;
        case (7/*org.apache.coyote.Constants.STAGE_ENDED*/):
            stageStr = "R";
            fullStatus = false;
            break;
        case (6/*org.apache.coyote.Constants.STAGE_KEEPALIVE*/):
            stageStr = "K";
            fullStatus = true;
            showRequest = false;
            break;
        case (0/*org.apache.coyote.Constants.STAGE_NEW*/):
            stageStr = "R";
            fullStatus = false;
            break;
        default:
            // Unknown stage
            stageStr = "?";
            fullStatus = false;

        }

        if (mode == 0) {
            writer.write("<td><strong>");
            writer.write(stageStr);
            writer.write("</strong></td>");

            if (fullStatus) {
                writer.write("<td>");
                writer.print(formatTime(mBeanServer.getAttribute
                                        (pName, "requestProcessingTime"), false));
                writer.write("</td>");
                writer.write("<td>");
                if (showRequest) {
                    writer.print(formatSize(mBeanServer.getAttribute
                                            (pName, "requestBytesSent"), false));
                } else {
                    writer.write("?");
                }
                writer.write("</td>");
                writer.write("<td>");
                if (showRequest) {
                    writer.print(formatSize(mBeanServer.getAttribute
                                            (pName, "requestBytesReceived"),
                                            false));
                } else {
                    writer.write("?");
                }
                writer.write("</td>");
                writer.write("<td>");
                writer.print(Escape.htmlElementContext(mBeanServer.getAttribute
                                    (pName, "remoteAddrForwarded")));
                writer.write("</td>");
                writer.write("<td>");
                writer.print(Escape.htmlElementContext(mBeanServer.getAttribute
                                    (pName, "remoteAddr")));
                writer.write("</td>");
                writer.write("<td nowrap>");
                writer.write(Escape.htmlElementContext(mBeanServer.getAttribute
                                    (pName, "virtualHost")));
                writer.write("</td>");
                writer.write("<td nowrap class=\"row-left\">");
                if (showRequest) {
                    writer.write(Escape.htmlElementContext(mBeanServer.getAttribute
                                        (pName, "method")));
                    writer.write(" ");
                    writer.write(Escape.htmlElementContext(mBeanServer.getAttribute
                                        (pName, "currentUri")));
                    String queryString = (String) mBeanServer.getAttribute
                        (pName, "currentQueryString");
                    if ((queryString != null) && (!queryString.equals(""))) {
                        writer.write("?");
                        writer.print(Escape.htmlElementContent(queryString));
                    }
                    writer.write(" ");
                    writer.write(Escape.htmlElementContext(mBeanServer.getAttribute
                                        (pName, "protocol")));
                } else {
                    writer.write("?");
                }
                writer.write("</td>");
            } else {
                writer.write("<td>?</td><td>?</td><td>?</td><td>?</td><td>?</td><td>?</td>");
            }
        } else if (mode == 1){
            writer.write("<worker ");
            writer.write(" stage=\"" + stageStr + "\"");

            if (fullStatus) {
                writer.write(" requestProcessingTime=\""
                             + mBeanServer.getAttribute
                             (pName, "requestProcessingTime") + "\"");
                writer.write(" requestBytesSent=\"");
                if (showRequest) {
                    writer.write("" + mBeanServer.getAttribute
                                 (pName, "requestBytesSent"));
                } else {
                    writer.write("0");
                }
                writer.write("\"");
                writer.write(" requestBytesReceived=\"");
                if (showRequest) {
                    writer.write("" + mBeanServer.getAttribute
                                 (pName, "requestBytesReceived"));
                } else {
                    writer.write("0");
                }
                writer.write("\"");
                writer.write(" remoteAddr=\""
                             + Escape.htmlElementContext(mBeanServer.getAttribute
                                      (pName, "remoteAddr")) + "\"");
                writer.write(" virtualHost=\""
                             + Escape.htmlElementContext(mBeanServer.getAttribute
                                      (pName, "virtualHost")) + "\"");

                if (showRequest) {
                    writer.write(" method=\""
                                 + Escape.htmlElementContext(mBeanServer.getAttribute
                                          (pName, "method")) + "\"");
                    writer.write(" currentUri=\""
                                 + Escape.htmlElementContext(mBeanServer.getAttribute
                                          (pName, "currentUri")) + "\"");

                    String queryString = (String) mBeanServer.getAttribute
                        (pName, "currentQueryString");
                    if ((queryString != null) && (!queryString.equals(""))) {
                        writer.write(" currentQueryString=\""
                                     + Escape.htmlElementContent(queryString) + "\"");
                    } else {
                        writer.write(" currentQueryString=\"&#63;\"");
                    }
                    writer.write(" protocol=\""
                                 + Escape.htmlElementContext(mBeanServer.getAttribute
                                          (pName, "protocol")) + "\"");
                } else {
                    writer.write(" method=\"&#63;\"");
                    writer.write(" currentUri=\"&#63;\"");
                    writer.write(" currentQueryString=\"&#63;\"");
                    writer.write(" protocol=\"&#63;\"");
                }
            } else {
                writer.write(" requestProcessingTime=\"0\"");
                writer.write(" requestBytesSent=\"0\"");
                writer.write(" requestBytesReceived=\"0\"");
                writer.write(" remoteAddr=\"&#63;\"");
                writer.write(" virtualHost=\"&#63;\"");
                writer.write(" method=\"&#63;\"");
                writer.write(" currentUri=\"&#63;\"");
                writer.write(" currentQueryString=\"&#63;\"");
                writer.write(" protocol=\"&#63;\"");
            }
            writer.write(" />");
        }
    }


    /**
     * 写入应用状态.
     * 
     * @param writer 输出writer
     * @param mBeanServer MBean服务器
     * @param mode <code>0</code>将生成 HTML.
     *   			<code>1</code>将生成 XML.
     * @throws Exception Propagated JMX error
     */
    public static void writeDetailedState(PrintWriter writer,
                                          MBeanServer mBeanServer, int mode)
        throws Exception {

        if (mode == 0){
            ObjectName queryHosts = new ObjectName("*:j2eeType=WebModule,*");
            Set<ObjectName> hostsON = mBeanServer.queryNames(queryHosts, null);

            // Navigation menu
            writer.print("<h1>");
            writer.print("Application list");
            writer.print("</h1>");

            writer.print("<p>");
            int count = 0;
            Iterator<ObjectName> iterator = hostsON.iterator();
            while (iterator.hasNext()) {
                ObjectName contextON = iterator.next();
                String webModuleName = contextON.getKeyProperty("name");
                if (webModuleName.startsWith("//")) {
                    webModuleName = webModuleName.substring(2);
                }
                int slash = webModuleName.indexOf('/');
                if (slash == -1) {
                    count++;
                    continue;
                }

                writer.print("<a href=\"#" + (count++) + ".0\">");
                writer.print(Escape.htmlElementContext(webModuleName));
                writer.print("</a>");
                if (iterator.hasNext()) {
                    writer.print("<br>");
                }

            }
            writer.print("</p>");

            // Webapp list
            count = 0;
            iterator = hostsON.iterator();
            while (iterator.hasNext()) {
                ObjectName contextON = iterator.next();
                writer.print("<a class=\"A.name\" name=\""
                             + (count++) + ".0\">");
                writeContext(writer, contextON, mBeanServer, mode);
            }

        } else if (mode == 1){
            // 目前还没有在XML中写出详细的状态
        }
    }


    /**
     * 写入上下文状态.
     * 
     * @param writer 输出writer
     * @param objectName 上下文MBean 名称
     * @param mBeanServer MBean 服务器
     * @param mode <code>0</code>将生成 HTML.
     *   			<code>1</code>将生成 XML.
     * @throws Exception Propagated JMX error
     */
    protected static void writeContext(PrintWriter writer,
                                       ObjectName objectName,
                                       MBeanServer mBeanServer, int mode)
        throws Exception {

        if (mode == 0){
            String webModuleName = objectName.getKeyProperty("name");
            String name = webModuleName;
            if (name == null) {
                return;
            }

            String hostName = null;
            String contextName = null;
            if (name.startsWith("//")) {
                name = name.substring(2);
            }
            int slash = name.indexOf('/');
            if (slash != -1) {
                hostName = name.substring(0, slash);
                contextName = name.substring(slash);
            } else {
                return;
            }

            ObjectName queryManager = new ObjectName
                (objectName.getDomain() + ":type=Manager,context=" + contextName
                 + ",host=" + hostName + ",*");
            Set<ObjectName> managersON =
                mBeanServer.queryNames(queryManager, null);
            ObjectName managerON = null;
            Iterator<ObjectName> iterator2 = managersON.iterator();
            while (iterator2.hasNext()) {
                managerON = iterator2.next();
            }

            ObjectName queryJspMonitor = new ObjectName
                (objectName.getDomain() + ":type=JspMonitor,WebModule=" +
                 webModuleName + ",*");
            Set<ObjectName> jspMonitorONs =
                mBeanServer.queryNames(queryJspMonitor, null);

            // Special case for the root context
            if (contextName.equals("/")) {
                contextName = "";
            }

            writer.print("<h1>");
            writer.print(Escape.htmlElementContext(name));
            writer.print("</h1>");
            writer.print("</a>");

            writer.print("<p>");
            Object startTime = mBeanServer.getAttribute(objectName,
                                                        "startTime");
            writer.print(" Start time: " +
                         new Date(((Long) startTime).longValue()));
            writer.print(" Startup time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (objectName, "startupTime"), false));
            writer.print(" TLD scan time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (objectName, "tldScanTime"), false));
            if (managerON != null) {
                writeManager(writer, managerON, mBeanServer, mode);
            }
            if (jspMonitorONs != null) {
                writeJspMonitor(writer, jspMonitorONs, mBeanServer, mode);
            }
            writer.print("</p>");

            String onStr = objectName.getDomain()
                + ":j2eeType=Servlet,WebModule=" + webModuleName + ",*";
            ObjectName servletObjectName = new ObjectName(onStr);
            Set<ObjectInstance> set =
                mBeanServer.queryMBeans(servletObjectName, null);
            Iterator<ObjectInstance> iterator = set.iterator();
            while (iterator.hasNext()) {
                ObjectInstance oi = iterator.next();
                writeWrapper(writer, oi.getObjectName(), mBeanServer, mode);
            }

        } else if (mode == 1){
            // 目前还没有用XML写出上下文
        }
    }


    /**
     * 写入关于管理器的详细信息.
     * 
     * @param writer 输出writer
     * @param objectName 管理器 MBean名称
     * @param mBeanServer MBean服务器
     * @param mode <code>0</code>将生成 HTML.
     *   			<code>1</code>将生成 XML.
     * @throws Exception Propagated JMX error
     */
    public static void writeManager(PrintWriter writer, ObjectName objectName,
                                    MBeanServer mBeanServer, int mode)
        throws Exception {

        if (mode == 0) {
            writer.print("<br>");
            writer.print(" Active sessions: ");
            writer.print(mBeanServer.getAttribute
                         (objectName, "activeSessions"));
            writer.print(" Session count: ");
            writer.print(mBeanServer.getAttribute
                         (objectName, "sessionCounter"));
            writer.print(" Max active sessions: ");
            writer.print(mBeanServer.getAttribute(objectName, "maxActive"));
            writer.print(" Rejected session creations: ");
            writer.print(mBeanServer.getAttribute
                         (objectName, "rejectedSessions"));
            writer.print(" Expired sessions: ");
            writer.print(mBeanServer.getAttribute
                         (objectName, "expiredSessions"));
            writer.print(" Longest session alive time: ");
            writer.print(formatSeconds(mBeanServer.getAttribute(
                                                    objectName,
                                                    "sessionMaxAliveTime")));
            writer.print(" Average session alive time: ");
            writer.print(formatSeconds(mBeanServer.getAttribute(
                                                    objectName,
                                                    "sessionAverageAliveTime")));
            writer.print(" Processing time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (objectName, "processingTime"), false));
        } else if (mode == 1) {
            // for now we don't write out the wrapper details
        }
    }


    /**
     * 写入JSP 监视信息.
     * 
     * @param writer 输出writer
     * @param jspMonitorONs JSP MBean名称
     * @param mBeanServer MBean服务器
     * @param mode <code>0</code>将生成 HTML.
     *   			<code>1</code>将生成 XML.
     * @throws Exception Propagated JMX error
     */
    public static void writeJspMonitor(PrintWriter writer,
                                       Set<ObjectName> jspMonitorONs,
                                       MBeanServer mBeanServer,
                                       int mode)
            throws Exception {

        int jspCount = 0;
        int jspReloadCount = 0;

        Iterator<ObjectName> iter = jspMonitorONs.iterator();
        while (iter.hasNext()) {
            ObjectName jspMonitorON = iter.next();
            Object obj = mBeanServer.getAttribute(jspMonitorON, "jspCount");
            jspCount += ((Integer) obj).intValue();
            obj = mBeanServer.getAttribute(jspMonitorON, "jspReloadCount");
            jspReloadCount += ((Integer) obj).intValue();
        }

        if (mode == 0) {
            writer.print("<br>");
            writer.print(" JSPs loaded: ");
            writer.print(jspCount);
            writer.print(" JSPs reloaded: ");
            writer.print(jspReloadCount);
        } else if (mode == 1) {
            // for now we don't write out anything
        }
    }


    /**
     * 写入包装器的详细信息.
     * 
     * @param writer 输出writer
     * @param objectName 包装器MBean名称
     * @param mBeanServer MBean服务器
     * @param mode Mode <code>0</code>将生成 HTML.
     *   Mode <code>1</code>将生成 XML.
     * @throws Exception Propagated JMX error
     */
    public static void writeWrapper(PrintWriter writer, ObjectName objectName,
                                    MBeanServer mBeanServer, int mode)
        throws Exception {

        if (mode == 0) {
            String servletName = objectName.getKeyProperty("name");

            String[] mappings = (String[])
                mBeanServer.invoke(objectName, "findMappings", null, null);

            writer.print("<h2>");
            writer.print(Escape.htmlElementContext(servletName));
            if ((mappings != null) && (mappings.length > 0)) {
                writer.print(" [ ");
                for (int i = 0; i < mappings.length; i++) {
                    writer.print(Escape.htmlElementContext(mappings[i]));
                    if (i < mappings.length - 1) {
                        writer.print(" , ");
                    }
                }
                writer.print(" ] ");
            }
            writer.print("</h2>");

            writer.print("<p>");
            writer.print(" Processing time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (objectName, "processingTime"), true));
            writer.print(" Max time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (objectName, "maxTime"), false));
            writer.print(" Request count: ");
            writer.print(mBeanServer.getAttribute(objectName, "requestCount"));
            writer.print(" Error count: ");
            writer.print(mBeanServer.getAttribute(objectName, "errorCount"));
            writer.print(" Load time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (objectName, "loadTime"), false));
            writer.print(" Classloading time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (objectName, "classLoadTime"), false));
            writer.print("</p>");
        } else if (mode == 1){
            // for now we don't write out the wrapper details
        }

    }


    /**
     * 过滤HTML中敏感的字符. 避免经常报告错误信息的请求URL中包含的JavaScript代码导致的潜在的攻击.
     *
     * @param obj 要过滤的消息字符串
     * @return filtered HTML内容
     *
     * @deprecated This method will be removed in Tomcat 9
     */
    @Deprecated
    public static String filter(Object obj) {

        if (obj == null)
            return ("?");
        String message = obj.toString();

        char content[] = new char[message.length()];
        message.getChars(0, message.length(), content, 0);
        StringBuilder result = new StringBuilder(content.length + 50);
        for (int i = 0; i < content.length; i++) {
            switch (content[i]) {
            case '<':
                result.append("&lt;");
                break;
            case '>':
                result.append("&gt;");
                break;
            case '&':
                result.append("&amp;");
                break;
            case '"':
                result.append("&quot;");
                break;
            default:
                result.append(content[i]);
            }
        }
        return (result.toString());
    }


    /**
     * 转义XML定义的5个实体.
     * 
     * @param s 要过滤的消息字符串
     * @return 过滤的XML 内容
     *
     * @deprecated This method will be removed in Tomcat 9
     */
    @Deprecated
    public static String filterXml(String s) {
        if (s == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '\'') {
                sb.append("&apos;");
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }


    /**
     * 以字节显示给定的大小, KB 或 MB.
     *
     * @param obj 要格式化的对象
     * @param mb true显示MB, 否则false 显示 KB
     * @return 格式化的大小
     */
    public static String formatSize(Object obj, boolean mb) {

        long bytes = -1L;

        if (obj instanceof Long) {
            bytes = ((Long) obj).longValue();
        } else if (obj instanceof Integer) {
            bytes = ((Integer) obj).intValue();
        }

        if (mb) {
            StringBuilder buff = new StringBuilder();
            if (bytes < 0) {
                buff.append('-');
                bytes = -bytes;
            }
            long mbytes = bytes / (1024 * 1024);
            long rest =
                ((bytes - (mbytes * (1024 * 1024))) * 100) / (1024 * 1024);
            buff.append(mbytes).append('.');
            if (rest < 10) {
                buff.append('0');
            }
            buff.append(rest).append(" MB");
            return buff.toString();
        } else {
            return ((bytes / 1024) + " KB");
        }
    }


    /**
     * 以毫秒显示给定的时间, ms 或 s.
     *
     * @param obj 要格式化的对象
     * @param seconds true显示秒数, false显示毫秒
     * @return 格式化的时间
     */
    public static String formatTime(Object obj, boolean seconds) {

        long time = -1L;

        if (obj instanceof Long) {
            time = ((Long) obj).longValue();
        } else if (obj instanceof Integer) {
            time = ((Integer) obj).intValue();
        }

        if (seconds) {
            return ((((float) time ) / 1000) + " s");
        } else {
            return (time + " ms");
        }
    }


    /**
     * 格式化给定的时间 (秒).
     *
     * @param obj 将对象格式化为字符串的时间对象
     * @return 格式化的时间
     */
    public static String formatSeconds(Object obj) {

        long time = -1L;

        if (obj instanceof Long) {
            time = ((Long) obj).longValue();
        } else if (obj instanceof Integer) {
            time = ((Integer) obj).intValue();
        }
        return (time + " s");
    }
}
