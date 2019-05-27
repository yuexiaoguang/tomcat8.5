package org.apache.catalina.ha.session;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.Cluster;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterValve;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.PersistentManager;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * 节点崩溃后, 后续请求转到其他集群节点.
 * 那会导致性能下降. 当在备份节点上启用此Valve并看到请求时, 这是为另一个（失败的）节点而设计的,
 * 它将重写Cookie jsessionid信息，使用这个备份集群节点的路由, 响应请求.
 * 在向客户端交付响应后, 所有后续客户端请求将直接转到备份节点. sessionid的更改也被发送到所有其他集群节点.
 * 毕竟, 会话粘性将直接作用于备份节点，并且在重新启动之后，流量不会返回到故障节点!
 *
 * <p>
 * 添加这个Valve到conf/server.xml中定义的主机.
 *
 * 自从5.5.10作为直接集群阀门:<br>
 *
 * <pre>
 *  &lt;Cluster&gt;
 *  &lt;Valve className=&quot;org.apache.catalina.ha.session.JvmRouteBinderValve&quot; /&gt;
 *  &lt;/Cluster&gt;
 * </pre>
 *
 * <br>
 * 在5.5.10作为 Host元素之前:<br>
 *
 * <pre>
 *  &lt;Host&gt;
 *  &lt;Valve className=&quot;org.apache.catalina.ha.session.JvmRouteBinderValve&quot; /&gt;
 *  &lt;/Host&gt;
 * </pre>
 *
 * <em>A Trick:</em><br>
 * 可以通过 JMX启用这个mod_jk turnover模块，在将节点丢弃到所有备份节点之前!
 * 在所有JvmRouteBinderValve备份上设置启用 true, 在mod_jk上禁用worker, 然后删除节点并重新启动它!
 * 然后启用 mod_jk worker 并再次禁用JvmRouteBinderValves. 此用例意味着只迁移请求的会话.
 */
public class JvmRouteBinderValve extends ValveBase implements ClusterValve {

    /*--Static Variables----------------------------------------*/
    public static final Log log = LogFactory.getLog(JvmRouteBinderValve.class);

    //------------------------------------------------------ Constructor
    public JvmRouteBinderValve() {
        super(true);
    }

    /*--Instance Variables--------------------------------------*/

    protected CatalinaCluster cluster;

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(JvmRouteBinderValve.class);

    /**
     * 是否启用
     */
    protected boolean enabled = true;

    /**
     * 在此Tomcat实例中没有的会话数
     */
    protected long numberOfSessions = 0;

    protected String sessionIdAttribute = "org.apache.catalina.ha.session.JvmRouteOrignalSessionID";


    /*--Logic---------------------------------------------------*/

    /**
     * 将会话ID属性设置为请求失败的节点.
     */
    public String getSessionIdAttribute() {
        return sessionIdAttribute;
    }

    /**
     * 获取失败请求会话属性的名称
     *
     * @param sessionIdAttribute 要设置的sessionIdAttribute.
     */
    public void setSessionIdAttribute(String sessionIdAttribute) {
        this.sessionIdAttribute = sessionIdAttribute;
    }

    /**
     * @return 返回迁移的会话数.
     */
    public long getNumberOfSessions() {
        return numberOfSessions;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 检测集群备份节点上的JVMRoute是否可以更改.
     *
     * @param request 正在处理的Tomcat请求
     * @param response 正在处理Tomcat响应
     * 
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    @Override
    public void invoke(Request request, Response response) throws IOException,
            ServletException {

         if (getEnabled() &&
                 request.getContext() != null &&
                 request.getContext().getDistributable() &&
                 !request.isAsyncDispatching()) {
             // 阀门集群可以访问管理器 - other cluster handle turnover at host level - hopefully!
             Manager manager = request.getContext().getManager();

             if (manager != null && (
                     (manager instanceof ClusterManager
                       && getCluster() != null
                       && getCluster().getManager(((ClusterManager)manager).getName()) != null)
                     ||
                     (manager instanceof PersistentManager))) {
                handlePossibleTurnover(request);
            }
        }
        // 将此请求传递到管道中的下一个阀门
        getNext().invoke(request, response);
    }

    /**
     * 处理可能的会话翻转.
     *
     * @param request 当前请求
     */
    protected void handlePossibleTurnover(Request request) {
        String sessionID = request.getRequestedSessionId() ;
        if (sessionID != null) {
            long t1 = System.currentTimeMillis();
            String jvmRoute = getLocalJvmRoute(request);
            if (jvmRoute == null) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("jvmRoute.missingJvmRouteAttribute"));
                }
                return;
            }
            handleJvmRoute( request, sessionID, jvmRoute);
            if (log.isDebugEnabled()) {
                long t2 = System.currentTimeMillis();
                long time = t2 - t1;
                log.debug(sm.getString("jvmRoute.turnoverInfo", Long.valueOf(time)));
            }
        }
    }

    /**
     * 从引擎获取jvmroute
     *
     * @param request 当前请求
     * @return return jvmRoute from ManagerBase or null
     */
    protected String getLocalJvmRoute(Request request) {
        Manager manager = getManager(request);
        if(manager instanceof ManagerBase) {
            return ((ManagerBase) manager).getJvmRoute();
        }
        return null ;
    }

    /**
     * 获取ClusterManager
     *
     * @param request 当前请求
     * @return manager or null
     */
    protected Manager getManager(Request request) {
        Manager manager = request.getContext().getManager();
        if (log.isDebugEnabled()) {
            if(manager != null) {
                log.debug(sm.getString("jvmRoute.foundManager", manager,  request.getContext().getName()));
            } else {
                log.debug(sm.getString("jvmRoute.notFoundManager", request.getContext().getName()));
            }
        }
        return manager;
    }

    @Override
    public CatalinaCluster getCluster() {
        return cluster;
    }

    @Override
    public void setCluster(CatalinaCluster cluster) {
        this.cluster = cluster;
    }

    /**
     * Tomcat实例失败后处理jvmRoute 粘性.
     * 修正后，新Cookie用新的jvmRoute发送给客户端，SessionID更改传播到其他集群节点.
     *
     * @param request 当前请求
     * @param sessionId 来自Cookie的请求SessionID
     * @param localJvmRoute 本地jvmRoute
     */
    protected void handleJvmRoute(
            Request request, String sessionId, String localJvmRoute) {
        // get requested jvmRoute.
        String requestJvmRoute = null;
        int index = sessionId.indexOf('.');
        if (index > 0) {
            requestJvmRoute = sessionId
                    .substring(index + 1, sessionId.length());
        }
        if (requestJvmRoute != null && !requestJvmRoute.equals(localJvmRoute)) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("jvmRoute.failover", requestJvmRoute,
                        localJvmRoute, sessionId));
            }
            Session catalinaSession = null;
            try {
                catalinaSession = getManager(request).findSession(sessionId);
            } catch (IOException e) {
                // Hups!
            }
            String id = sessionId.substring(0, index);
            String newSessionID = id + "." + localJvmRoute;
            // OK - 移交会话并通知其他集群节点
            if (catalinaSession != null) {
                changeSessionID(request, sessionId, newSessionID,
                        catalinaSession);
                numberOfSessions++;
            } else {
                try {
                    catalinaSession = getManager(request).findSession(newSessionID);
                } catch (IOException e) {
                    // Hups!
                }
                if (catalinaSession != null) {
                    // session is rewrite at other request, rewrite this also
                    changeRequestSessionID(request, sessionId, newSessionID);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("jvmRoute.cannotFindSession",sessionId));
                    }
                }
            }
        }
    }

    /**
     * 更改会话ID并发送到所有集群节点
     *
     * @param request 当前请求
     * @param sessionId 原始的会话ID
     * @param newSessionID 节点迁移的新会话ID
     * @param catalinaSession 具有原始会话ID的当前会话
     */
    protected void changeSessionID(Request request, String sessionId,
            String newSessionID, Session catalinaSession) {
        fireLifecycleEvent("Before session migration", catalinaSession);
        catalinaSession.getManager().changeSessionId(catalinaSession, newSessionID);
        changeRequestSessionID(request, sessionId, newSessionID);
        fireLifecycleEvent("After session migration", catalinaSession);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("jvmRoute.changeSession", sessionId,
                    newSessionID));
        }
    }

    /**
     * 更改请求会话ID
     * 
     * @param request 当前请求
     * @param sessionId 原始的会话ID
     * @param newSessionID 节点迁移的新会话ID
     */
    protected void changeRequestSessionID(Request request, String sessionId, String newSessionID) {
        request.changeSessionId(newSessionID);

        // 在请求上设置原始 sessionid, 允许应用程序检测更改
        if (sessionIdAttribute != null && !"".equals(sessionIdAttribute)) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("jvmRoute.set.orignalsessionid",sessionIdAttribute,sessionId));
            }
            request.setAttribute(sessionIdAttribute, sessionId);
        }
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        if (cluster == null) {
            Cluster containerCluster = getContainer().getCluster();
            if (containerCluster instanceof CatalinaCluster) {
                setCluster((CatalinaCluster)containerCluster);
            }
        }

        if (log.isInfoEnabled()) {
            log.info(sm.getString("jvmRoute.valve.started"));
            if (cluster == null) {
                log.info(sm.getString("jvmRoute.noCluster"));
            }
        }
        super.startInternal();
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        super.stopInternal();

        cluster = null;
        numberOfSessions = 0;
        if (log.isInfoEnabled()) {
            log.info(sm.getString("jvmRoute.valve.stopped"));
        }
    }
}
