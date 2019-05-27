package org.apache.catalina.ha.tcp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.ServletException;

import org.apache.catalina.Cluster;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.ha.ClusterSession;
import org.apache.catalina.ha.ClusterValve;
import org.apache.catalina.ha.session.DeltaManager;
import org.apache.catalina.ha.session.DeltaSession;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>Valve实现类从指定的请求中记录需要的的内容(处理之前)和对应的响应(处理之后).
 * 它特别适用于调试与header和cookie有关的问题.</p>
 *
 * <p>这个Valve将被附加到任何Container, 根据希望执行的日志记录的粒度.</p>
 *
 * <p>primaryIndicator=true, 那么请求属性<i>org.apache.catalina.ha.tcp.isPrimarySession.</i>
 * 被设置为true, 当请求处理处于会话主节点时.
 * </p>
 */
public class ReplicationValve extends ValveBase implements ClusterValve {

    private static final Log log = LogFactory.getLog(ReplicationValve.class);

    // ----------------------------------------------------- Instance Variables

    /**
     * The StringManager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    private CatalinaCluster cluster = null ;

    /**
     * 过滤表达式
     */
    protected Pattern filter = null;

    /**
     * crossContext会话容器
     */
    protected final ThreadLocal<ArrayList<DeltaSession>> crossContextSessions =
        new ThreadLocal<>() ;

    /**
     * doProcessingStats (default = off)
     */
    protected boolean doProcessingStats = false;

    protected long totalRequestTime = 0;
    protected long totalSendTime = 0;
    protected long nrOfRequests = 0;
    protected long lastSendTime = 0;
    protected long nrOfFilterRequests = 0;
    protected long nrOfSendRequests = 0;
    protected long nrOfCrossContextSendRequests = 0;

    /**
     * 必须一次改变指示器组
     */
    protected boolean primaryIndicator = false ;

    /**
     * 作为请求属性的主更改指示符的名称
     */
    protected String primaryIndicatorName = "org.apache.catalina.ha.tcp.isPrimarySession";

    // ------------------------------------------------------------- Properties

    public ReplicationValve() {
        super(true);
    }

    @Override
    public CatalinaCluster getCluster() {
        return cluster;
    }

    @Override
    public void setCluster(CatalinaCluster cluster) {
        this.cluster = cluster;
    }

    public String getFilter() {
       if (filter == null) {
           return null;
       }
       return filter.toString();
    }

    /**
     * 将过滤器字符串编译为正则表达式
     * 
     * @param filter 要设置的过滤器.
     */
    public void setFilter(String filter) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("ReplicationValve.filter.loading", filter));
        }

        if (filter == null || filter.length() == 0) {
            this.filter = null;
        } else {
            try {
                this.filter = Pattern.compile(filter);
            } catch (PatternSyntaxException pse) {
                log.error(sm.getString("ReplicationValve.filter.failure",
                        filter), pse);
            }
        }
    }

    public boolean isPrimaryIndicator() {
        return primaryIndicator;
    }

    public void setPrimaryIndicator(boolean primaryIndicator) {
        this.primaryIndicator = primaryIndicator;
    }

    public String getPrimaryIndicatorName() {
        return primaryIndicatorName;
    }

    public void setPrimaryIndicatorName(String primaryIndicatorName) {
        this.primaryIndicatorName = primaryIndicatorName;
    }

    /**
     * 是否统计
     * 
     * @return <code>true</code>如果启用统计信息
     */
    public boolean doStatistics() {
        return doProcessingStats;
    }

    /**
     * <code>true</code>如果启用统计信息
     *
     * @param doProcessingStats New flag value
     */
    public void setStatistics(boolean doProcessingStats) {
        this.doProcessingStats = doProcessingStats;
    }

    public long getLastSendTime() {
        return lastSendTime;
    }

    public long getNrOfRequests() {
        return nrOfRequests;
    }

    public long getNrOfFilterRequests() {
        return nrOfFilterRequests;
    }

    public long getNrOfCrossContextSendRequests() {
        return nrOfCrossContextSendRequests;
    }

    public long getNrOfSendRequests() {
        return nrOfSendRequests;
    }

    public long getTotalRequestTime() {
        return totalRequestTime;
    }

    public long getTotalSendTime() {
        return totalSendTime;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 在endAccess中注册所有跨上下文会话.
     * 使用包含检查的列表, Portlet API可以包括许多相同或不同应用程序的随会话变化的片段.
     *
     * @param session 跨上下文会话
     */
    public void registerReplicationSession(DeltaSession session) {
        List<DeltaSession> sessions = crossContextSessions.get();
        if(sessions != null) {
            if(!sessions.contains(session)) {
                if(log.isDebugEnabled()) {
                    log.debug(sm.getString("ReplicationValve.crossContext.registerSession",
                        session.getIdInternal(),
                        session.getManager().getContext().getName()));
                }
                sessions.add(session);
            }
        }
    }

    /**
     * 记录相关的请求参数, 调用序列中下一个 Valve, 并记录相关响应参数.
     *
     * @param request 要处理的servlet请求
     * @param response 要创建的servlet响应
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    @Override
    public void invoke(Request request, Response response)
        throws IOException, ServletException
    {
        long totalstart = 0;

        // 这种情况发生在请求之前
        if(doStatistics()) {
            totalstart = System.currentTimeMillis();
        }
        if (primaryIndicator) {
            createPrimaryIndicator(request) ;
        }
        Context context = request.getContext();
        boolean isCrossContext = context != null
                && context instanceof StandardContext
                && ((StandardContext) context).getCrossContext();
        try {
            if(isCrossContext) {
                if(log.isDebugEnabled()) {
                    log.debug(sm.getString("ReplicationValve.crossContext.add"));
                }
                //FIXME add Pool of Arraylists
                crossContextSessions.set(new ArrayList<DeltaSession>());
            }
            getNext().invoke(request, response);
            if(context != null && cluster != null
                    && context.getManager() instanceof ClusterManager) {
                ClusterManager clusterManager = (ClusterManager) context.getManager();

                // 阀门集群可以访问管理器 - 主机级别的其他集群处理复制 - hopefully!
                if(cluster.getManager(clusterManager.getName()) == null) {
                    return ;
                }
                if(cluster.hasMembers()) {
                    sendReplicationMessage(request, totalstart, isCrossContext, clusterManager);
                } else {
                    resetReplicationRequest(request,isCrossContext);
                }
            }
        } finally {
            // 必须删除Array: 当前主请求在重用中发送endAccess.
            // 不要再次注册这个请求会话!
            if(isCrossContext) {
                if(log.isDebugEnabled()) {
                    log.debug(sm.getString("ReplicationValve.crossContext.remove"));
                }
                // crossContextSessions.remove()只存在于 Java 5
                // register ArrayList at a pool
                crossContextSessions.set(null);
            }
        }
    }


    /**
     * 重置活动统计
     */
    public void resetStatistics() {
        totalRequestTime = 0 ;
        totalSendTime = 0 ;
        lastSendTime = 0 ;
        nrOfFilterRequests = 0 ;
        nrOfRequests = 0 ;
        nrOfSendRequests = 0;
        nrOfCrossContextSendRequests = 0;
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
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(sm.getString("ReplicationValve.nocluster"));
                }
            }
        }
        super.startInternal();
    }


    // --------------------------------------------------------- Protected Methods

    protected void sendReplicationMessage(Request request, long totalstart, boolean isCrossContext, ClusterManager clusterManager) {
        // 请求之后发生
        long start = 0;
        if(doStatistics()) {
            start = System.currentTimeMillis();
        }
        try {
            // 发送无效的会话
            // DeltaManager returns String[0]
            if (!(clusterManager instanceof DeltaManager)) {
                sendInvalidSessions(clusterManager);
            }
            // 发送复制
            sendSessionReplicationMessage(request, clusterManager);
            if(isCrossContext) {
                sendCrossContextSession();
            }
        } catch (Exception x) {
            // FIXME we have a lot of sends, but the trouble with one node stops the correct replication to other nodes!
            log.error(sm.getString("ReplicationValve.send.failure"), x);
        } finally {
            // FIXME this stats update are not cheap!!
            if(doStatistics()) {
                updateStats(totalstart,start);
            }
        }
    }

    /**
     * 将所有更改的跨上下文会话发送到备份
     */
    protected void sendCrossContextSession() {
        List<DeltaSession> sessions = crossContextSessions.get();
        if(sessions != null && sessions.size() >0) {
            for(Iterator<DeltaSession> iter = sessions.iterator(); iter.hasNext() ;) {
                Session session = iter.next();
                if(log.isDebugEnabled()) {
                    log.debug(sm.getString("ReplicationValve.crossContext.sendDelta",
                            session.getManager().getContext().getName() ));
                }
                sendMessage(session,(ClusterManager)session.getManager());
                if(doStatistics()) {
                    nrOfCrossContextSendRequests++;
                }
            }
        }
    }

    /**
     * 修复长会话的内存泄漏, 当没有备份成员存在时!
     * 
     * @param request 生成响应后的当前请求
     * @param isCrossContext 是否检查交叉上下文threadlocal
     */
    protected void resetReplicationRequest(Request request, boolean isCrossContext) {
        Session contextSession = request.getSessionInternal(false);
        if(contextSession instanceof DeltaSession){
            resetDeltaRequest(contextSession);
            ((DeltaSession)contextSession).setPrimarySession(true);
        }
        if(isCrossContext) {
            List<DeltaSession> sessions = crossContextSessions.get();
            if(sessions != null && sessions.size() >0) {
                Iterator<DeltaSession> iter = sessions.iterator();
                for(; iter.hasNext() ;) {
                    Session session = iter.next();
                    resetDeltaRequest(session);
                    if(session instanceof DeltaSession) {
                        ((DeltaSession)contextSession).setPrimarySession(true);
                    }

                }
            }
        }
    }

    /**
     * 从会话重置DeltaRequest
     * 
     * @param session 从当前请求或跨上下文会话的HttpSession
     */
    protected void resetDeltaRequest(Session session) {
        if(log.isDebugEnabled()) {
            log.debug(sm.getString("ReplicationValve.resetDeltaRequest" ,
                session.getManager().getContext().getName() ));
        }
        ((DeltaSession)session).resetDeltaRequest();
    }

    /**
     * 发送集群复制请求
     * 
     * @param request 当前请求
     * @param manager 会话管理器
     */
    protected void sendSessionReplicationMessage(Request request,
            ClusterManager manager) {
        Session session = request.getSessionInternal(false);
        if (session != null) {
            String uri = request.getDecodedRequestURI();
            // request without session change
            if (!isRequestWithoutSessionChange(uri)) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("ReplicationValve.invoke.uri", uri));
                }
                sendMessage(session,manager);
            } else
                if(doStatistics()) {
                    nrOfFilterRequests++;
                }
        }

    }

   /**
    * 从请求会话发送消息
    * 
    * @param session 当前会话
    * @param manager 会话管理器
    */
    protected void sendMessage(Session session,
             ClusterManager manager) {
        String id = session.getIdInternal();
        if (id != null) {
            send(manager, id);
        }
    }

    /**
     * 发送管理器requestCompleted 信息到集群
     * 
     * @param manager SessionManager
     * @param sessionId 管理器的sessionid
     */
    protected void send(ClusterManager manager, String sessionId) {
        ClusterMessage msg = manager.requestCompleted(sessionId);
        if (msg != null && cluster != null) {
            cluster.send(msg);
            if(doStatistics()) {
                nrOfSendRequests++;
            }
        }
    }

    /**
     * 检查会话无效
     * 
     * @param manager 关联的管理器
     */
    protected void sendInvalidSessions(ClusterManager manager) {
        String[] invalidIds=manager.getInvalidatedSessions();
        if ( invalidIds.length > 0 ) {
            for ( int i=0;i<invalidIds.length; i++ ) {
                try {
                    send(manager,invalidIds[i]);
                } catch ( Exception x ) {
                    log.error(sm.getString("ReplicationValve.send.invalid.failure",invalidIds[i]),x);
                }
            }
        }
    }

    /**
     * 是请求而不可能会话更改
     * 
     * @param uri 请求URI
     * @return True 如果没有会话修改
     */
    protected boolean isRequestWithoutSessionChange(String uri) {
        Pattern f = filter;
        return f != null && f.matcher(uri).matches();
    }

    /**
     * 协议集群复制统计
     * 
     * @param requestTime 请求时间
     * @param clusterTime 集群时间
     */
    protected  void updateStats(long requestTime, long clusterTime) {
        // TODO: Async requests may trigger multiple replication requests. How,
        //       if at all, should the stats handle this?
        synchronized(this) {
            lastSendTime=System.currentTimeMillis();
            totalSendTime+=lastSendTime - clusterTime;
            totalRequestTime+=lastSendTime - requestTime;
            nrOfRequests++;
        }
        if(log.isInfoEnabled()) {
            if ( (nrOfRequests % 100) == 0 ) {
                 log.info(sm.getString("ReplicationValve.stats",
                     new Object[]{
                         Long.valueOf(totalRequestTime/nrOfRequests),
                         Long.valueOf(totalSendTime/nrOfRequests),
                         Long.valueOf(nrOfRequests),
                         Long.valueOf(nrOfSendRequests),
                         Long.valueOf(nrOfCrossContextSendRequests),
                         Long.valueOf(nrOfFilterRequests),
                         Long.valueOf(totalRequestTime),
                         Long.valueOf(totalSendTime)}));
             }
        }
    }


    /**
     * 在主节点使用primaryIndicatorName属性处理的标记请求
     *
     * @param request Servlet请求
     * @throws IOException 查找会话的IO错误
     */
    protected void createPrimaryIndicator(Request request) throws IOException {
        String id = request.getRequestedSessionId();
        if ((id != null) && (id.length() > 0)) {
            Manager manager = request.getContext().getManager();
            Session session = manager.findSession(id);
            if (session instanceof ClusterSession) {
                ClusterSession cses = (ClusterSession) session;
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString(
                            "ReplicationValve.session.indicator", request.getContext().getName(),id,
                            primaryIndicatorName,
                            Boolean.valueOf(cses.isPrimarySession())));
                }
                request.setAttribute(primaryIndicatorName, cses.isPrimarySession()?Boolean.TRUE:Boolean.FALSE);
            } else {
                if (log.isDebugEnabled()) {
                    if (session != null) {
                        log.debug(sm.getString(
                                "ReplicationValve.session.found", request.getContext().getName(),id));
                    } else {
                        log.debug(sm.getString(
                                "ReplicationValve.session.invalid", request.getContext().getName(),id));
                    }
                }
            }
        }
    }
}
