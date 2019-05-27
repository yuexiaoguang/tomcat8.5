package org.apache.catalina.tribes;

/**
 * 定义组中的一个成员.
 * 每个成员都可以携带一组属性, 通过实际的实现定义.<BR>
 * 成员使用 host/ip/uniqueId 标识<br>
 * 主机是成员正在监听的接口, 来接收数据<br>
 * 端口是成员正在侦听的端口, 来接收数据<br>
 * uniqueId 是成员的 session id. 这是一个重要的功能, 因为崩溃的成员再次在同样的port/host上启动，不会被视为同样的成员, 所以没有情况转移会被混淆
 */
public interface Member {

    /**
     * 当成员离开集群时, memberDisappeared 成员有效载荷将会是下面的字节. 表示软关闭, 而不是崩溃
     */
    public static final byte[] SHUTDOWN_PAYLOAD = new byte[] {66, 65, 66, 89, 45, 65, 76, 69, 88};

    /**
     * @return 这个节点的名称, 应该在组中唯一.
     */
    public String getName();

    /**
     * 返回ChannelReceiver 实现类监听的主机
     * @return 主机的IPv4 或 IPv6地址
     */
    public byte[] getHost();

    /**
     * 返回ChannelReceiver 实现类监听的端口号
     * @return 成员监听的端口号, -1 如果它没有监听不安全的端口
     */
    public int getPort();

    /**
     * 返回ChannelReceiver 实现类监听的安全接口.
     * @return 成员监听的端口号, -1 如果未监听安全接口.
     */
    public int getSecurePort();

    /**
     * 返回ChannelReceiver 实现类监听的 UDP 端口.
     * @return 成员监听的端口号, -1 如果未监听UDP端口
     */
    public int getUdpPort();


    /**
     * 包含这个成员已经在线多长时间.
     * 结果是添加到这个组的毫秒数.
     * @return 成员启动的毫秒数.
     */
    public long getMemberAliveTime();

    public void setMemberAliveTime(long memberAliveTime);

    /**
     * 成员的当前状态
     * @return boolean - true 如果成员正确运行
     */
    public boolean isReady();
    /**
     * 成员的当前状态
     * @return boolean - true 如果成员是可疑的, 但不确定是否崩溃
     */
    public boolean isSuspect();

    /**
     * @return boolean - true 如果成员已确认故障
     */
    public boolean isFailing();

    /**
     * 返回一个 UUID, 此成员在所有会话中都是唯一的.
     * 如果成员崩溃或重新启动, uniqueId 将会不一样.
     */
    public byte[] getUniqueId();

    /**
     * 返回这个成员关联的有效负荷
     */
    public byte[] getPayload();

    public void setPayload(byte[] payload);

    /**
     * 返回这个成员关联的命令
     */
    public byte[] getCommand();

    public void setCommand(byte[] command);

    /**
     * 这个集群的域名
     */
    public byte[] getDomain();

    /**
     * 将成员序列化为字节数组的高度优化版本. 返回一个缓存的 byte[] 引用, 不要编辑这个数据
     * @param getalive  是否计算成员生存时间
     * @return 数据作为一个字节数组
     */
    public byte[] getData(boolean getalive);

    /**
     * 将成员序列化为字节数组的高度优化版本. 返回一个缓存的 byte[] 引用, 不要编辑这个数据
     * 
     * @param getalive  是否计算成员生存时间
     * @param reset     重置缓存的数据包, 并创建一个新的
     * @return 数据作为一个字节数组
     */
    public byte[] getData(boolean getalive, boolean reset);

    /**
     * 通过{@link #getData(boolean)} 或 {@link #getData(boolean, boolean)}获取的信息长度.
     * @return the data length
     */
    public int getDataLength();

    /**
     * @return boolean - true 如果成员是本地成员
     */
    public boolean isLocal();

    public void setLocal(boolean local);
}
