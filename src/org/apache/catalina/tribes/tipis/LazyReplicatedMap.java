package org.apache.catalina.tribes.tipis;

import java.io.Serializable;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.UniqueId;
import org.apache.catalina.tribes.util.Arrays;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 有状态的复制的map的智能实现. 使用主/次备份策略.
 * 一个节点总是主节点, 一个节点总是备份. 这个 map 在集群上同步, 只有一个备份成员.<br>
 * 这个 map 的完美用法是集群环境中会话管理器的会话 map.<br>
 * 编辑这个列表的唯一方式是使用 <code>put, putAll, remove</code> 方法.
 * entrySet, entrySetFull, keySet, keySetFull, 返回所有不可修改集合.<br><br>
 * 如果map中的对象 (values)的修改, 不是调用<code>put()</code> 或 <code>remove()</code>, 数据可以使用两种不同的方法进行分发:<br>
 * <code>replicate(boolean)</code> and <code>replicate(Object, boolean)</code><br>
 * 
 * 这两种方法是非常重要的两种理解. map 可以使用两个集合的值对象工作:<br>
 * 1. Serializable - 每次复制对象时, 整个对象都会被序列化<br>
 * 2. ReplicatedMapEntry - 这个接口允许 isDirty() 标志并且可以复制差异.<br>
 * 
 * 实现<code>ReplicatedMapEntry</code>接口允许您决定复制哪些对象以及每次复制多少数据.<br>
 * 如果实现了智能AOP机制来检测底层对象中的更改, 只可以复制实现了ReplicatedMapEntry接口的修改的对象, 并返回 true, 当调用 isDiffable()时.<br><br>
 *
 * 这个 map 实现没有后台线程运行以复制更改. 如果确实修改了, 但是没有调用 put/remove, 那么你需要调用以下方法其中之一:
 * <ul>
 * <li><code>replicate(Object,boolean)</code> - 只复制属于key的对象</li>
 * <li><code>replicate(boolean)</code> - 扫描整个map以进行更改并复制数据</li>
 *  </ul>
 * <code>replicate</code>方法中的 <code>boolean</code> 值用于确定是否只复制实现了<code>ReplicatedMapEntry</code>接口的对象,
 * 或复制所有的对象. 如果对象没有实现<code>ReplicatedMapEntry</code>接口, 每次复制对象时, 整个对象都会被序列化, 因此调用<code>replicate(true)</code>
 * 将复制此map 中使用此节点作为主要的所有对象.
 *
 * <br><br><b>REMEMBER TO CALL</b> <code>breakdown()</code> 或 <code>finalize()</code> 当完成 map 以避免内存泄漏时.<br><br>
 * TODO 实现周期同步/传输线程
 *
 * @param <K> The type of Key
 * @param <V> The type of Value
 */
public class LazyReplicatedMap<K,V> extends AbstractReplicatedMap<K,V> {
    private static final long serialVersionUID = 1L;
    private final Log log = LogFactory.getLog(LazyReplicatedMap.class);


//------------------------------------------------------------------------------
//              CONSTRUCTORS / DESTRUCTORS
//------------------------------------------------------------------------------
    /**
     * @param owner map拥有者
     * @param channel 用于通信的channel
     * @param timeout long - RPC 消息的超时时间
     * @param mapContextName String - 这个map的唯一名称, 允许每个channel有多个 map
     * @param initialCapacity int - 这个 map的大小, see HashMap
     * @param loadFactor float - 负载因子, see HashMap
     * @param cls Class loaders
     */
    public LazyReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, int initialCapacity, float loadFactor, ClassLoader[] cls) {
        super(owner,channel,timeout,mapContextName,initialCapacity,loadFactor, Channel.SEND_OPTIONS_DEFAULT,cls, true);
    }

    /**
     * @param owner map拥有者
     * @param channel 用于通信的channel
     * @param timeout long - RPC 消息的超时时间
     * @param mapContextName String - 这个map的唯一名称, 允许每个channel有多个 map
     * @param initialCapacity int - 这个 map的大小, see HashMap
     * @param cls Class loaders
     */
    public LazyReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, int initialCapacity, ClassLoader[] cls) {
        super(owner, channel,timeout,mapContextName,initialCapacity, AbstractReplicatedMap.DEFAULT_LOAD_FACTOR, Channel.SEND_OPTIONS_DEFAULT, cls, true);
    }

    /**
     * @param owner map拥有者
     * @param channel 用于通信的channel
     * @param timeout long - RPC 消息的超时时间
     * @param mapContextName String - 这个map的唯一名称, 允许每个channel有多个 map
     * @param cls Class loaders
     */
    public LazyReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, ClassLoader[] cls) {
        super(owner, channel,timeout,mapContextName, AbstractReplicatedMap.DEFAULT_INITIAL_CAPACITY,AbstractReplicatedMap.DEFAULT_LOAD_FACTOR,Channel.SEND_OPTIONS_DEFAULT, cls, true);
    }

    /**
     * @param owner map拥有者
     * @param channel 用于通信的channel
     * @param timeout long - RPC 消息的超时时间
     * @param mapContextName String - 这个map的唯一名称, 允许每个channel有多个 map
     * @param cls Class loaders
     * @param terminate boolean - 是否终止未能启动的地图.
     */
    public LazyReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, ClassLoader[] cls, boolean terminate) {
        super(owner, channel,timeout,mapContextName, AbstractReplicatedMap.DEFAULT_INITIAL_CAPACITY,
                AbstractReplicatedMap.DEFAULT_LOAD_FACTOR,Channel.SEND_OPTIONS_DEFAULT, cls, terminate);
    }


//------------------------------------------------------------------------------
//              METHODS TO OVERRIDE
//------------------------------------------------------------------------------
    @Override
    protected int getStateMessageType() {
        return AbstractReplicatedMap.MapMessage.MSG_STATE;
    }

    @Override
    protected int getReplicateMessageType() {
        return AbstractReplicatedMap.MapMessage.MSG_BACKUP;
    }

    /**
     * 发布关于一个map 对(key/value)的信息到集群中的其它节点
     * @param key Object
     * @param value Object
     * @return Member - 备份节点
     * @throws ChannelException 集群错误
     */
    @Override
    protected Member[] publishEntryInfo(Object key, Object value) throws ChannelException {
        if  (! (key instanceof Serializable && value instanceof Serializable)  ) return new Member[0];
        Member[] members = getMapMembers();
        int firstIdx = getNextBackupIndex();
        int nextIdx = firstIdx;
        Member[] backup = new Member[0];

        // 没有备份
        if ( members.length == 0 || firstIdx == -1 ) return backup;

        boolean success = false;
        do {
            // 选择备份节点
            Member next = members[nextIdx];

            // 下一轮备份选择的增量
            nextIdx = nextIdx + 1;
            if ( nextIdx >= members.length ) nextIdx = 0;

            if (next == null) {
                continue;
            }
            MapMessage msg = null;
            try {
                Member[] tmpBackup = wrap(next);
                // 发布备份数据到一个节点
                msg = new MapMessage(getMapContextName(), MapMessage.MSG_BACKUP, false,
                                     (Serializable) key, (Serializable) value, null, channel.getLocalMember(false), tmpBackup);
                if ( log.isTraceEnabled() )
                    log.trace("Publishing backup data:"+msg+" to: "+next.getName());
                UniqueId id = getChannel().send(tmpBackup, msg, getChannelSendOptions());
                if ( log.isTraceEnabled() )
                    log.trace("Data published:"+msg+" msg Id:"+id);
                // 发布一个备份, 标记测试成功
                success = true;
                backup = tmpBackup;
            }catch ( ChannelException x ) {
                log.error(sm.getString("lazyReplicatedMap.unableReplicate.backup", key, next, x.getMessage()), x);
                continue;
            }
            try {
                // 发布数据到所有节点
                Member[] proxies = excludeFromSet(backup, getMapMembers());
                if (success && proxies.length > 0 ) {
                    msg = new MapMessage(getMapContextName(), MapMessage.MSG_PROXY, false,
                                         (Serializable) key, null, null, channel.getLocalMember(false),backup);
                    if ( log.isTraceEnabled() )
                        log.trace("Publishing proxy data:"+msg+" to: "+Arrays.toNameString(proxies));
                    getChannel().send(proxies, msg, getChannelSendOptions());
                }
            }catch  ( ChannelException x ) {
                //记录错误, 不处理, 只有节点下线时才会发生这种情况,
                //如果节点下线, 那么它无法再接收消息, 其他节点仍然应该得到它.
                log.error(sm.getString("lazyReplicatedMap.unableReplicate.proxy", key, next, x.getMessage()), x);
            }
        } while ( !success && (firstIdx!=nextIdx));
        return backup;
    }
}