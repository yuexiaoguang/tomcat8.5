package org.apache.catalina.valves;

import org.apache.catalina.Contained;
import org.apache.catalina.Container;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.res.StringManager;

/**
 * <b>Valve</b>接口实现类的基类.
 * 子类必须实现<code>invoke()</code>方法提供所需功能, 也可以实现<code>Lifecycle</code>接口提供配置管理和生命周期支持
 */
public abstract class ValveBase extends LifecycleMBeanBase implements Contained, Valve {

    protected static final StringManager sm = StringManager.getManager(ValveBase.class);


    //------------------------------------------------------ Constructor

    public ValveBase() {
        this(false);
    }


    public ValveBase(boolean asyncSupported) {
        this.asyncSupported = asyncSupported;
    }


    //------------------------------------------------------ Instance Variables

    /**
     * 是否支持Servlet 3+ 异步请求?
     */
    protected boolean asyncSupported;


    /**
     * The Container whose pipeline this Valve is a component of.
     */
    protected Container container = null;


    /**
     * Container log
     */
    protected Log containerLog = null;


    /**
     * 管道中下一个Valve.
     */
    protected Valve next = null;


    //-------------------------------------------------------------- Properties

    /**
     * 返回关联的Container.
     */
    @Override
    public Container getContainer() {
        return container;
    }


    /**
     * 设置关联的Container.
     *
     * @param container The new associated container
     */
    @Override
    public void setContainer(Container container) {
        this.container = container;
    }


    @Override
    public boolean isAsyncSupported() {
        return asyncSupported;
    }


    public void setAsyncSupported(boolean asyncSupported) {
        this.asyncSupported = asyncSupported;
    }


    /**
     * 返回这个管道中下一个 Valve, 如果这个Valve已经是最后的一个返回<code>null</code>.
     */
    @Override
    public Valve getNext() {
        return next;
    }


    /**
     * 设置管道中下一个Valve.
     *
     * @param valve The new next valve
     */
    @Override
    public void setNext(Valve valve) {
        this.next = valve;
    }


    //---------------------------------------------------------- Public Methods

    /**
     * 执行周期任务, 例如重新加载, etc. 该方法将在该容器的类加载上下文中被调用.
     * 异常将被捕获和记录.
     */
    @Override
    public void backgroundProcess() {
        // NOOP by default
    }


    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        containerLog = getContainer().getLogger();
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {
        setState(LifecycleState.STARTING);
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append('[');
        if (container == null) {
            sb.append("Container is null");
        } else {
            sb.append(container.getName());
        }
        sb.append(']');
        return sb.toString();
    }


    // -------------------- JMX and Registration  --------------------

    @Override
    public String getObjectNameKeyProperties() {
        StringBuilder name = new StringBuilder("type=Valve");

        Container container = getContainer();

        name.append(container.getMBeanKeyProperties());

        int seq = 0;

        // Pipeline 可能不存在于单元测试中
        Pipeline p = container.getPipeline();
        if (p != null) {
            for (Valve valve : p.getValves()) {
                // Skip null valves
                if (valve == null) {
                    continue;
                }
                // 在管道中比较阀门，直到找到这个阀门为止
                if (valve == this) {
                    break;
                }
                if (valve.getClass() == this.getClass()) {
                    // Duplicate valve earlier in pipeline
                    // increment sequence number
                    seq ++;
                }
            }
        }

        if (seq > 0) {
            name.append(",seq=");
            name.append(seq);
        }

        String className = this.getClass().getName();
        int period = className.lastIndexOf('.');
        if (period >= 0) {
            className = className.substring(period + 1);
        }
        name.append(",name=");
        name.append(className);

        return name.toString();
    }


    @Override
    public String getDomainInternal() {
        Container c = getContainer();
        if (c == null) {
            return null;
        } else {
            return c.getDomain();
        }
    }
}
