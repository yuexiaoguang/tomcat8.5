package org.apache.catalina.session;

/**
 * <b>Manager</b>接口实现类，使用Store将活动会话交换到磁盘. 
 * 它可以配置成实现几个不同的目标:
 *
 * <li>在容器重新启动期间持久会话</li>
 * <li>容错, 在磁盘上备份会话以允许在未计划重启的情况下恢复.</li>
 * <li>通过将不活跃的会话交换到磁盘，限制内存中活跃会话的数量.</li>
 */
public final class PersistentManager extends PersistentManagerBase {

    // ----------------------------------------------------- Instance Variables

    /**
     * 描述信息, 用于记录日志.
     */
    private static final String name = "PersistentManager";


    // ------------------------------------------------------------- Properties

    @Override
    public String getName() {
        return name;
    }
 }

