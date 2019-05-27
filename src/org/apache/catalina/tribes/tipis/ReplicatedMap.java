package org.apache.catalina.tribes.tipis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelException.FaultyMember;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.RemoteProcessException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 哈希map实现的全复制. 集群中的每个节点将携带相同的map副本.<br><br>
 * 这个 map 实现类没有后台线程运行以复制更改. 如果确实修改了, 但是没有调用 put/remove, 那么你需要调用以下方法其中之一:
 * <ul>
 * <li><code>replicate(Object,boolean)</code> - 只复制属于key的对象</li>
 * <li><code>replicate(boolean)</code> - 扫描整个map以进行更改并复制数据</li>
 *  </ul>
 * <code>replicate</code>方法中的 <code>boolean</code> 值用于确定是否只复制实现了<code>ReplicatedMapEntry</code>接口的对象,
 * 或复制所有的对象. 如果对象没有实现<code>ReplicatedMapEntry</code>接口, 每次复制对象时, 整个对象都会被序列化, 因此调用<code>replicate(true)</code>
 * 将复制此map 中使用此节点作为主要的所有对象.
 *
 * <br><br><b>REMEMBER TO CALL <code>breakdown()</code> 或 <code>finalize()</code> 当完成 map 以避免内存泄漏时.</b><br><br>
 * TODO 实现周期同步/传输线程<br>
 * TODO memberDisappeared, 除了更改MAP成员之外，不应做任何事情，它可以重新定位主对象
 *
 * @param <K> The type of Key
 * @param <V> The type of Value
 */
public class ReplicatedMap<K,V> extends AbstractReplicatedMap<K,V> {

    private static final long serialVersionUID = 1L;

    private final Log log = LogFactory.getLog(ReplicatedMap.class);

    //--------------------------------------------------------------------------
    //              CONSTRUCTORS / DESTRUCTORS
    //--------------------------------------------------------------------------
    /**
     * @param owner map拥有者
     * @param channel 用于通信的channel
     * @param timeout long - RPC 消息的超时时间
     * @param mapContextName String - 这个map的唯一名称, 允许每个channel有多个 map
     * @param initialCapacity int - 这个 map的大小, see HashMap
     * @param loadFactor float - 负载因子, see HashMap
     * @param cls Class loaders
     */
    public ReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, int initialCapacity,float loadFactor, ClassLoader[] cls) {
        super(owner,channel, timeout, mapContextName, initialCapacity, loadFactor, Channel.SEND_OPTIONS_DEFAULT, cls, true);
    }

    /**
     * @param owner map拥有者
     * @param channel 用于通信的channel
     * @param timeout long - RPC 消息的超时时间
     * @param mapContextName String - 这个map的唯一名称, 允许每个channel有多个 map
     * @param initialCapacity int - 这个 map的大小, see HashMap
     * @param cls Class loaders
     */
    public ReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, int initialCapacity, ClassLoader[] cls) {
        super(owner,channel, timeout, mapContextName, initialCapacity, AbstractReplicatedMap.DEFAULT_LOAD_FACTOR,Channel.SEND_OPTIONS_DEFAULT, cls, true);
    }

    /**
     * @param owner map拥有者
     * @param channel 用于通信的channel
     * @param timeout long - RPC 消息的超时时间
     * @param mapContextName String - 这个map的唯一名称, 允许每个channel有多个 map
     * @param cls Class loaders
     */
    public ReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, ClassLoader[] cls) {
        super(owner, channel, timeout, mapContextName,AbstractReplicatedMap.DEFAULT_INITIAL_CAPACITY, AbstractReplicatedMap.DEFAULT_LOAD_FACTOR, Channel.SEND_OPTIONS_DEFAULT, cls, true);
    }

    /**
     * @param owner map拥有者
     * @param channel 用于通信的channel
     * @param timeout long - RPC 消息的超时时间
     * @param mapContextName String - 这个map的唯一名称, 允许每个channel有多个 map
     * @param cls Class loaders
     * @param terminate boolean - 是否终止未能启动的地图.
     */
    public ReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, ClassLoader[] cls, boolean terminate) {
        super(owner, channel, timeout, mapContextName,AbstractReplicatedMap.DEFAULT_INITIAL_CAPACITY,
                AbstractReplicatedMap.DEFAULT_LOAD_FACTOR, Channel.SEND_OPTIONS_DEFAULT, cls, terminate);
    }

//------------------------------------------------------------------------------
//              METHODS TO OVERRIDE
//------------------------------------------------------------------------------
    @Override
    protected int getStateMessageType() {
        return AbstractReplicatedMap.MapMessage.MSG_STATE_COPY;
    }

    @Override
    protected int getReplicateMessageType() {
        return AbstractReplicatedMap.MapMessage.MSG_COPY;
    }

    /**
     * 发布关于一个map 对(key/value)的信息到集群中的其它节点
     * @param key Object
     * @param value Object
     * @return Member - the backup node
     * @throws ChannelException Cluster error
     */
    @Override
    protected Member[] publishEntryInfo(Object key, Object value) throws ChannelException {
        if  (! (key instanceof Serializable && value instanceof Serializable)  ) return new Member[0];
        //选择备份节点
        Member[] backup = getMapMembers();

        if (backup == null || backup.length == 0) return null;

        try {
            // 发布数据到所有节点
            MapMessage msg = new MapMessage(getMapContextName(), MapMessage.MSG_COPY, false,
                    (Serializable) key, (Serializable) value, null,channel.getLocalMember(false), backup);

            getChannel().send(backup, msg, getChannelSendOptions());
        } catch (ChannelException e) {
            FaultyMember[] faultyMembers = e.getFaultyMembers();
            if (faultyMembers.length == 0) throw e;
            ArrayList<Member> faulty = new ArrayList<>();
            for (FaultyMember faultyMember : faultyMembers) {
                if (!(faultyMember.getCause() instanceof RemoteProcessException)) {
                    faulty.add(faultyMember.getMember());
                }
            }
            Member[] realFaultyMembers = faulty.toArray(new Member[faulty.size()]);
            if (realFaultyMembers.length != 0) {
                backup = excludeFromSet(realFaultyMembers, backup);
                if (backup.length == 0) {
                    throw e;
                } else {
                    if (log.isWarnEnabled()) {
                        log.warn(sm.getString("replicatedMap.unableReplicate.completely", key,
                                Arrays.toString(backup), Arrays.toString(realFaultyMembers)), e);
                    }
                }
            }
        }
        return backup;
    }

    @Override
    public void memberDisappeared(Member member) {
        boolean removed = false;
        synchronized (mapMembers) {
            removed = (mapMembers.remove(member) != null );
            if (!removed) {
                if (log.isDebugEnabled()) log.debug("Member["+member+"] disappeared, but was not present in the map.");
                return; // 成员不属于这个 map.
            }
        }
        if (log.isInfoEnabled())
            log.info(sm.getString("replicatedMap.member.disappeared", member));
        long start = System.currentTimeMillis();
        Iterator<Map.Entry<K,MapEntry<K,V>>> i = innerMap.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<K,MapEntry<K,V>> e = i.next();
            MapEntry<K,V> entry = innerMap.get(e.getKey());
            if (entry==null) continue;
            if (entry.isPrimary()) {
                try {
                    Member[] backup = getMapMembers();
                    if (backup.length > 0) {
                        MapMessage msg = new MapMessage(getMapContextName(), MapMessage.MSG_NOTIFY_MAPMEMBER,false,
                                (Serializable)entry.getKey(),null,null,channel.getLocalMember(false),backup);
                        getChannel().send(backup, msg, getChannelSendOptions());
                    }
                    entry.setBackupNodes(backup);
                    entry.setPrimary(channel.getLocalMember(false));
                } catch (ChannelException x) {
                    log.error(sm.getString("replicatedMap.unable.relocate", entry.getKey()), x);
                }
            } else if (member.equals(entry.getPrimary())) {
                entry.setPrimary(null);
            }

            if ( entry.getPrimary() == null &&
                        entry.isCopy() &&
                        entry.getBackupNodes()!=null &&
                        entry.getBackupNodes().length > 0 &&
                        entry.getBackupNodes()[0].equals(channel.getLocalMember(false)) ) {
                try {
                    entry.setPrimary(channel.getLocalMember(false));
                    entry.setBackup(false);
                    entry.setProxy(false);
                    entry.setCopy(false);
                    Member[] backup = getMapMembers();
                    if (backup.length > 0) {
                        MapMessage msg = new MapMessage(getMapContextName(), MapMessage.MSG_NOTIFY_MAPMEMBER,false,
                                (Serializable)entry.getKey(),null,null,channel.getLocalMember(false),backup);
                        getChannel().send(backup, msg, getChannelSendOptions());
                    }
                    entry.setBackupNodes(backup);
                    if ( mapOwner!=null ) mapOwner.objectMadePrimary(entry.getKey(),entry.getValue());

                } catch (ChannelException x) {
                    log.error(sm.getString("replicatedMap.unable.relocate", entry.getKey()), x);
                }
            }

        }
        long complete = System.currentTimeMillis() - start;
        if (log.isInfoEnabled()) log.info(sm.getString("replicatedMap.relocate.complete",
                Long.toString(complete)));
    }

    @Override
    public void mapMemberAdded(Member member) {
        if ( member.equals(getChannel().getLocalMember(false)) ) return;
        boolean memberAdded = false;
        synchronized (mapMembers) {
            if (!mapMembers.containsKey(member) ) {
                mapMembers.put(member, Long.valueOf(System.currentTimeMillis()));
                memberAdded = true;
            }
        }
        if ( memberAdded ) {
            synchronized (stateMutex) {
                Member[] backup = getMapMembers();
                Iterator<Map.Entry<K,MapEntry<K,V>>> i = innerMap.entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry<K,MapEntry<K,V>> e = i.next();
                    MapEntry<K,V> entry = innerMap.get(e.getKey());
                    if ( entry == null ) continue;
                    if (entry.isPrimary() && !inSet(member,entry.getBackupNodes())) {
                        entry.setBackupNodes(backup);
                    }
                }
            }
        }
    }
}