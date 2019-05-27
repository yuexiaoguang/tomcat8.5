package org.apache.catalina.tribes.membership;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.transport.SenderState;
import org.apache.catalina.tribes.util.StringManager;

/**
 * 使用多播.
 * 这是多播成员的表示. 携带这个或其它集群节点的主机和端口.
 */
public class MemberImpl implements Member, java.io.Externalizable {

    /**
     * 是否调用 getName 或 getHostName, 查找 DNS?
     * 默认是 false
     */
    public static final boolean DO_DNS_LOOKUPS = Boolean.parseBoolean(System.getProperty("org.apache.catalina.tribes.dns_lookups","false"));

    public static final transient byte[] TRIBES_MBR_BEGIN = new byte[] {84, 82, 73, 66, 69, 83, 45, 66, 1, 0};
    public static final transient byte[] TRIBES_MBR_END   = new byte[] {84, 82, 73, 66, 69, 83, 45, 69, 1, 0};
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * 这个成员的监听主机
     */
    protected volatile byte[] host = new byte[0];
    protected transient volatile String hostname;
    /**
     * 这个成员的TCP监听端口
     */
    protected volatile int port;
    /**
     * 这个成员的udp监听端口
     */
    protected volatile int udpPort = -1;

    /**
     * 这个成员的 tcp/SSL 监听端口
     */
    protected volatile int securePort = -1;

    /**
     * 计数器, 从这个成员已发送多少广播消息
     */
    protected AtomicInteger msgCount = new AtomicInteger(0);

    /**
     * 自创建该成员以来的毫秒数, 保持使用开始时间的轨迹
     */
    protected volatile long memberAliveTime = 0;

    /**
     * 只用于本地成员
     */
    protected transient long serviceStartTime;

    /**
     * 避免重复序列化, 一旦本地 dataPkg 已经被设置, 使用它来传输数据
     */
    protected transient byte[] dataPkg = null;

    /**
     * 此成员的唯一会话Id
     */
    protected volatile byte[] uniqueId = new byte[16];

    /**
     * 应用程序框架可以广播的自定义有效载荷. 也用于传输停止命令.
     */
    protected volatile byte[] payload = new byte[0];

    /**
     * 命令, 因此，不必使用自定义有效载荷.
     * 这是为内部使用的, 例如 SHUTDOWN_COMMAND
     */
    protected volatile byte[] command = new byte[0];

    /**
     * 域名, 如果基于域名过滤.
     */
    protected volatile byte[] domain = new byte[0];

    /**
     * 指示该成员是本地成员的标志.
     */
    protected volatile boolean local = false;

    /**
     * 用于序列化
     */
    public MemberImpl() {

    }

    /**
     * @param host - tcp 监听主机
     * @param port - tcp 监听端口
     * @param aliveTime - 自创建该成员以来的毫秒数
     *
     * @throws IOException 如果将主机名转换为IP地址存在错误
     */
    public MemberImpl(String host,
                      int port,
                      long aliveTime) throws IOException {
        setHostname(host);
        this.port = port;
        this.memberAliveTime=aliveTime;
    }

    public MemberImpl(String host,
                      int port,
                      long aliveTime,
                      byte[] payload) throws IOException {
        this(host,port,aliveTime);
        setPayload(payload);
    }

    @Override
    public boolean isReady() {
        return SenderState.getSenderState(this).isReady();
    }
    @Override
    public boolean isSuspect() {
        return SenderState.getSenderState(this).isSuspect();
    }
    @Override
    public boolean isFailing() {
        return SenderState.getSenderState(this).isFailing();
    }

    /**
     * 增加消息计数.
     */
    protected void inc() {
        msgCount.incrementAndGet();
    }

    /**
     * 创建一个数据包通过代表该成员的线发送. 快于序列化.
     * @return - 此成员反序列化的字节数
     */
    public byte[] getData()  {
        return getData(true);
    }


    @Override
    public byte[] getData(boolean getalive)  {
        return getData(getalive,false);
    }


    @Override
    public synchronized int getDataLength() {
        return TRIBES_MBR_BEGIN.length+ //start pkg
               4+ //data length
               8+ //alive time
               4+ //port
               4+ //secure port
               4+ //udp port
               1+ //host length
               host.length+ //host
               4+ //command length
               command.length+ //command
               4+ //domain length
               domain.length+ //domain
               16+ //unique id
               4+ //payload length
               payload.length+ //payload
               TRIBES_MBR_END.length; //end pkg
    }


    @Override
    public synchronized byte[] getData(boolean getalive, boolean reset)  {
        if (reset) {
            dataPkg = null;
        }
        // 首先查看缓存
        if (dataPkg != null) {
            if (getalive) {
                // System.currentTimeMillis 显示在分析器上
                long alive = System.currentTimeMillis() - getServiceStartTime();
                byte[] result = dataPkg.clone();
                XByteBuffer.toBytes(alive, result, TRIBES_MBR_BEGIN.length + 4);
                dataPkg = result;
            }
            return dataPkg;
        }

        //package looks like
        //start package TRIBES_MBR_BEGIN.length
        //package length - 4 bytes
        //alive - 8 bytes
        //port - 4 bytes
        //secure port - 4 bytes
        //udp port - 4 bytes
        //host length - 1 byte
        //host - hl bytes
        //clen - 4 bytes
        //command - clen bytes
        //dlen - 4 bytes
        //domain - dlen bytes
        //uniqueId - 16 bytes
        //payload length - 4 bytes
        //payload plen bytes
        //end package TRIBES_MBR_END.length
        long alive=System.currentTimeMillis()-getServiceStartTime();
        byte[] data = new byte[getDataLength()];

        int bodylength = (getDataLength() - TRIBES_MBR_BEGIN.length - TRIBES_MBR_END.length - 4);

        int pos = 0;

        //TRIBES_MBR_BEGIN
        System.arraycopy(TRIBES_MBR_BEGIN,0,data,pos,TRIBES_MBR_BEGIN.length);
        pos += TRIBES_MBR_BEGIN.length;

        //body length
        XByteBuffer.toBytes(bodylength,data,pos);
        pos += 4;

        //alive data
        XByteBuffer.toBytes(alive,data,pos);
        pos += 8;
        //port
        XByteBuffer.toBytes(port,data,pos);
        pos += 4;
        //secure port
        XByteBuffer.toBytes(securePort,data,pos);
        pos += 4;
        //udp port
        XByteBuffer.toBytes(udpPort,data,pos);
        pos += 4;
        //host length
        data[pos++] = (byte) host.length;
        //host
        System.arraycopy(host,0,data,pos,host.length);
        pos+=host.length;
        //clen - 4 bytes
        XByteBuffer.toBytes(command.length,data,pos);
        pos+=4;
        //command - clen bytes
        System.arraycopy(command,0,data,pos,command.length);
        pos+=command.length;
        //dlen - 4 bytes
        XByteBuffer.toBytes(domain.length,data,pos);
        pos+=4;
        //domain - dlen bytes
        System.arraycopy(domain,0,data,pos,domain.length);
        pos+=domain.length;
        //unique Id
        System.arraycopy(uniqueId,0,data,pos,uniqueId.length);
        pos+=uniqueId.length;
        //payload
        XByteBuffer.toBytes(payload.length,data,pos);
        pos+=4;
        System.arraycopy(payload,0,data,pos,payload.length);
        pos+=payload.length;

        //TRIBES_MBR_END
        System.arraycopy(TRIBES_MBR_END,0,data,pos,TRIBES_MBR_END.length);
        pos += TRIBES_MBR_END.length;

        //create local data
        dataPkg = data;
        return data;
    }
    /**
     * 从通过导线发送的数据反序列化成员.
     *
     * @param data   接受的字节
     * @param member 要填充的成员对象
     *
     * @return 填充的成员对象.
     */
    public static Member getMember(byte[] data, MemberImpl member) {
        return getMember(data,0,data.length,member);
    }

    public static Member getMember(byte[] data, int offset, int length, MemberImpl member) {
        //package looks like
        //start package TRIBES_MBR_BEGIN.length
        //package length - 4 bytes
        //alive - 8 bytes
        //port - 4 bytes
        //secure port - 4 bytes
        //udp port - 4 bytes
        //host length - 1 byte
        //host - hl bytes
        //clen - 4 bytes
        //command - clen bytes
        //dlen - 4 bytes
        //domain - dlen bytes
        //uniqueId - 16 bytes
        //payload length - 4 bytes
        //payload plen bytes
        //end package TRIBES_MBR_END.length

        int pos = offset;

        if (XByteBuffer.firstIndexOf(data,offset,TRIBES_MBR_BEGIN)!=pos) {
            throw new IllegalArgumentException(sm.getString("memberImpl.invalid.package.begin", org.apache.catalina.tribes.util.Arrays.toString(TRIBES_MBR_BEGIN)));
        }

        if ( length < (TRIBES_MBR_BEGIN.length+4) ) {
            throw new ArrayIndexOutOfBoundsException(sm.getString("memberImpl.package.small"));
        }

        pos += TRIBES_MBR_BEGIN.length;

        int bodylength = XByteBuffer.toInt(data,pos);
        pos += 4;

        if ( length < (bodylength+4+TRIBES_MBR_BEGIN.length+TRIBES_MBR_END.length) ) {
            throw new ArrayIndexOutOfBoundsException(sm.getString("memberImpl.notEnough.bytes"));
        }

        int endpos = pos+bodylength;
        if (XByteBuffer.firstIndexOf(data,endpos,TRIBES_MBR_END)!=endpos) {
            throw new IllegalArgumentException(sm.getString("memberImpl.invalid.package.end", org.apache.catalina.tribes.util.Arrays.toString(TRIBES_MBR_END)));
        }


        byte[] alived = new byte[8];
        System.arraycopy(data, pos, alived, 0, 8);
        pos += 8;
        byte[] portd = new byte[4];
        System.arraycopy(data, pos, portd, 0, 4);
        pos += 4;

        byte[] sportd = new byte[4];
        System.arraycopy(data, pos, sportd, 0, 4);
        pos += 4;

        byte[] uportd = new byte[4];
        System.arraycopy(data, pos, uportd, 0, 4);
        pos += 4;


        byte hl = data[pos++];
        byte[] addr = new byte[hl];
        System.arraycopy(data, pos, addr, 0, hl);
        pos += hl;

        int cl = XByteBuffer.toInt(data, pos);
        pos += 4;

        byte[] command = new byte[cl];
        System.arraycopy(data, pos, command, 0, command.length);
        pos += command.length;

        int dl = XByteBuffer.toInt(data, pos);
        pos += 4;

        byte[] domain = new byte[dl];
        System.arraycopy(data, pos, domain, 0, domain.length);
        pos += domain.length;

        byte[] uniqueId = new byte[16];
        System.arraycopy(data, pos, uniqueId, 0, 16);
        pos += 16;

        int pl = XByteBuffer.toInt(data, pos);
        pos += 4;

        byte[] payload = new byte[pl];
        System.arraycopy(data, pos, payload, 0, payload.length);
        pos += payload.length;

        member.setHost(addr);
        member.setPort(XByteBuffer.toInt(portd, 0));
        member.setSecurePort(XByteBuffer.toInt(sportd, 0));
        member.setUdpPort(XByteBuffer.toInt(uportd, 0));
        member.setMemberAliveTime(XByteBuffer.toLong(alived, 0));
        member.setUniqueId(uniqueId);
        member.payload = payload;
        member.domain = domain;
        member.command = command;

        member.dataPkg = new byte[length];
        System.arraycopy(data, offset, member.dataPkg, 0, length);

        return member;
    }

    public static Member getMember(byte[] data) {
       return getMember(data,new MemberImpl());
    }

    public static Member getMember(byte[] data, int offset, int length) {
       return getMember(data,offset,length,new MemberImpl());
    }

    /**
     * 返回此对象的名称
     * @return 集群的唯一名称
     */
    @Override
    public String getName() {
        return "tcp://"+getHostname()+":"+getPort();
    }

    /**
     * 返回该成员的监听端口
     * @return - tcp 监听端口
     */
    @Override
    public int getPort()  {
        return this.port;
    }

    /**
     * 返回这个成员的TCP监听主机
     * @return IP 地址或主机名
     */
    @Override
    public byte[] getHost()  {
        return host;
    }

    public String getHostname() {
        if ( this.hostname != null ) return hostname;
        else {
            try {
                byte[] host = this.host;
                if (DO_DNS_LOOKUPS)
                    this.hostname = java.net.InetAddress.getByAddress(host).getHostName();
                else
                    this.hostname = org.apache.catalina.tribes.util.Arrays.toString(host,0,host.length,true);
                return this.hostname;
            }catch ( IOException x ) {
                throw new RuntimeException(sm.getString("memberImpl.unableParse.hostname"),x);
            }
        }
    }

    public int getMsgCount() {
        return msgCount.get();
    }

    /**
     * 包含有关该成员在线多长时间的信息.
     * 这个成员已经向集群广播成员的毫秒数.
     * @return 这个成员启动的毫秒数.
     */
    @Override
    public long getMemberAliveTime() {
       return memberAliveTime;
    }

    public long getServiceStartTime() {
        return serviceStartTime;
    }

    @Override
    public byte[] getUniqueId() {
        return uniqueId;
    }

    @Override
    public byte[] getPayload() {
        return payload;
    }

    @Override
    public byte[] getCommand() {
        return command;
    }

    @Override
    public byte[] getDomain() {
        return domain;
    }

    @Override
    public int getSecurePort() {
        return securePort;
    }

    @Override
    public int getUdpPort() {
        return udpPort;
    }

    @Override
    public void setMemberAliveTime(long time) {
       memberAliveTime=time;
    }


    @Override
    public String toString()  {
        StringBuilder buf = new StringBuilder(getClass().getName());
        buf.append("[");
        buf.append(getName()).append(",");
        buf.append(getHostname()).append(",");
        buf.append(port).append(", alive=");
        buf.append(memberAliveTime).append(", ");
        buf.append("securePort=").append(securePort).append(", ");
        buf.append("UDP Port=").append(udpPort).append(", ");
        buf.append("id=").append(bToS(this.uniqueId)).append(", ");
        buf.append("payload=").append(bToS(this.payload,8)).append(", ");
        buf.append("command=").append(bToS(this.command,8)).append(", ");
        buf.append("domain=").append(bToS(this.domain,8)).append(", ");
        buf.append("]");
        return buf.toString();
    }
    public static String bToS(byte[] data) {
        return bToS(data,data.length);
    }
    public static String bToS(byte[] data, int max) {
        StringBuilder buf = new StringBuilder(4*16);
        buf.append("{");
        for (int i=0; data!=null && i<data.length; i++ ) {
            buf.append(String.valueOf(data[i])).append(" ");
            if ( i==max ) {
                buf.append("...("+data.length+")");
                break;
            }
        }
        buf.append("}");
        return buf.toString();
    }

    @Override
    public int hashCode() {
        return getHost()[0]+getHost()[1]+getHost()[2]+getHost()[3];
    }

    /**
     * true: 如果参数 o 是一个有着相同名称的 McastMember
     *
     * @param o
     */
    @Override
    public boolean equals(Object o) {
        if ( o instanceof MemberImpl )    {
            return Arrays.equals(this.getHost(),((MemberImpl)o).getHost()) &&
                   this.getPort() == ((MemberImpl)o).getPort() &&
                   Arrays.equals(this.getUniqueId(),((MemberImpl)o).getUniqueId());
        }
        else
            return false;
    }

    public synchronized void setHost(byte[] host) {
        this.host = host;
    }

    public void setHostname(String host) throws IOException {
        hostname = host;
        synchronized (this) {
            this.host = java.net.InetAddress.getByName(host).getAddress();
        }
    }

    public void setMsgCount(int msgCount) {
        this.msgCount.set(msgCount);
    }

    public synchronized void setPort(int port) {
        this.port = port;
        this.dataPkg = null;
    }

    public void setServiceStartTime(long serviceStartTime) {
        this.serviceStartTime = serviceStartTime;
    }

    public synchronized void setUniqueId(byte[] uniqueId) {
        this.uniqueId = uniqueId!=null?uniqueId:new byte[16];
        getData(true,true);
    }

    @Override
    public synchronized void setPayload(byte[] payload) {
        // 避免任何溢出
        long oldPayloadLength = 0;
        if (this.payload != null) {
            oldPayloadLength = this.payload.length;
        }
        long newPayloadLength = 0;
        if (payload != null) {
            newPayloadLength = payload.length;
        }
        if (newPayloadLength > oldPayloadLength) {
            // 可能超过最大分组大小
            if ((newPayloadLength - oldPayloadLength + getData(false, false).length) >
                    McastServiceImpl.MAX_PACKET_SIZE) {
                throw new IllegalArgumentException(sm.getString("memberImpl.large.payload"));
            }
        }
        this.payload = payload != null ? payload : new byte[0];
        getData(true, true);
    }

    @Override
    public synchronized void setCommand(byte[] command) {
        this.command = command!=null?command:new byte[0];
        getData(true,true);
    }

    public synchronized void setDomain(byte[] domain) {
        this.domain = domain!=null?domain:new byte[0];
        getData(true,true);
    }

    public synchronized void setSecurePort(int securePort) {
        this.securePort = securePort;
        this.dataPkg = null;
    }

    public synchronized void setUdpPort(int port) {
        this.udpPort = port;
        this.dataPkg = null;
    }

    @Override
    public boolean isLocal() {
        return local;
    }

    @Override
    public void setLocal(boolean local) {
        this.local = local;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int length = in.readInt();
        byte[] message = new byte[length];
        in.readFully(message);
        getMember(message,this);

    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        byte[] data = this.getData();
        out.writeInt(data.length);
        out.write(data);
    }
}
