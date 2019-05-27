package org.apache.catalina.valves;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.Store;
import org.apache.catalina.StoreManager;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

/**
 * 实现每个请求会话持久性的Valve. 它的目的是与non-sticky负载平衡器一起使用.
 * <p>
 * <b>USAGE CONSTRAINT</b>: 正确工作需要一个PersistentManager.
 * <p>
 * <b>USAGE CONSTRAINT</b>: 若要正确工作，它假定在任何时候每个会话只存在一个请求.
 */
public class PersistentValve extends ValveBase {

    // 在每个请求上保存一对调用 getClassLoader(). 在高负荷下，这些调用花了很长时间才成为热点(虽然很小)在分析器中.
    private static final ClassLoader MY_CLASSLOADER = PersistentValve.class.getClassLoader();

    private volatile boolean clBindRequired;


    //------------------------------------------------------ Constructor

    public PersistentValve() {
        super(true);
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public void setContainer(Container container) {
        super.setContainer(container);
        if (container instanceof Engine || container instanceof Host) {
            clBindRequired = true;
        } else {
            clBindRequired = false;
        }
    }


    /**
     * 选择适当的子上下文来处理此请求, 根据指定的请求 URI. 如果找不到匹配的 Context, 返回适当的 HTTP 错误.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     *
     * @exception IOException if an input/output error occurred
     * @exception ServletException if a servlet error occurred
     */
    @Override
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        // 选择要用于此请求的上下文
        Context context = request.getContext();
        if (context == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    sm.getString("standardHost.noContext"));
            return;
        }

        // 更新会话最后访问时间
        String sessionId = request.getRequestedSessionId();
        Manager manager = context.getManager();
        if (sessionId != null && manager instanceof StoreManager) {
            Store store = ((StoreManager) manager).getStore();
            if (store != null) {
                Session session = null;
                try {
                    session = store.load(sessionId);
                } catch (Exception e) {
                    container.getLogger().error("deserializeError");
                }
                if (session != null) {
                    if (!session.isValid() ||
                        isSessionStale(session, System.currentTimeMillis())) {
                        if (container.getLogger().isDebugEnabled()) {
                            container.getLogger().debug("session swapped in is invalid or expired");
                        }
                        session.expire();
                        store.remove(sessionId);
                    } else {
                        session.setManager(manager);
                        // session.setId(sessionId); Only if new ???
                        manager.add(session);
                        // ((StandardSession)session).activate();
                        session.access();
                        session.endAccess();
                    }
                }
            }
        }
        if (container.getLogger().isDebugEnabled()) {
            container.getLogger().debug("sessionId: " + sessionId);
        }

        // Ask the next valve to process the request.
        getNext().invoke(request, response);

        // 如果仍然异步处理, 不要保存这个会话
        if (!request.isAsync()) {
            // Read the sessionid after the response.
            // HttpSession hsess = hreq.getSession(false);
            Session hsess;
            try {
                hsess = request.getSessionInternal(false);
            } catch (Exception ex) {
                hsess = null;
            }
            String newsessionId = null;
            if (hsess!=null) {
                newsessionId = hsess.getIdInternal();
            }

            if (container.getLogger().isDebugEnabled()) {
                container.getLogger().debug("newsessionId: " + newsessionId);
            }
            if (newsessionId!=null) {
                try {
                    bind(context);

                    /* store the session and remove it from the manager */
                    if (manager instanceof StoreManager) {
                        Session session = manager.findSession(newsessionId);
                        Store store = ((StoreManager) manager).getStore();
                        if (store != null && session != null && session.isValid() &&
                                !isSessionStale(session, System.currentTimeMillis())) {
                            store.save(session);
                            ((StoreManager) manager).removeSuper(session);
                            session.recycle();
                        } else {
                            if (container.getLogger().isDebugEnabled()) {
                                container.getLogger().debug("newsessionId store: " +
                                        store + " session: " + session +
                                        " valid: " +
                                        (session == null ? "N/A" : Boolean.toString(
                                                session.isValid())) +
                                        " stale: " + isSessionStale(session,
                                                System.currentTimeMillis()));
                            }

                        }
                    } else {
                        if (container.getLogger().isDebugEnabled()) {
                            container.getLogger().debug("newsessionId Manager: " +
                                    manager);
                        }
                    }
                } finally {
                    unbind(context);
                }
            }
        }
    }


    /**
     * 指示会话空闲时间是否超过其到期日期，与所提供的时间无关.
     *
     * FIXME: Probably belongs in the Session class.
     * @param session The session to check
     * @param timeNow The current time to check for
     * 
     * @return <code>true</code>如果会话已过期
     */
    protected boolean isSessionStale(Session session, long timeNow) {

        if (session != null) {
            int maxInactiveInterval = session.getMaxInactiveInterval();
            if (maxInactiveInterval >= 0) {
                int timeIdle = // Truncate, do not round up
                    (int) ((timeNow - session.getThisAccessedTime()) / 1000L);
                if (timeIdle >= maxInactiveInterval) {
                    return true;
                }
            }
        }

        return false;
    }


    private void bind(Context context) {
        if (clBindRequired) {
            context.bind(Globals.IS_SECURITY_ENABLED, MY_CLASSLOADER);
        }
    }


    private void unbind(Context context) {
        if (clBindRequired) {
            context.unbind(Globals.IS_SECURITY_ENABLED, MY_CLASSLOADER);
        }
    }
}
