package org.apache.coyote;

import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.tomcat.util.net.SocketWrapperBase;

public interface UpgradeProtocol {

    /**
     * @param isSSLEnabled 是否用于配置支持TLS的连接器. 一些协议 (e.g. HTTP/2) 只支持 HTTP在非安全连接上升级.
     * 
     * @return 客户端将使用的名称来请求对该协议进行升级, 通过一个 HTTP/1.1 升级请求或 <code>null</code>, 如果不支持通过 HTTP/1.1 升级请求升级.
     */
    public String getHttpUpgradeName(boolean isSSLEnabled);

    /**
     * @return 在该协议的IANA注册表中列出的字节序列, 或 <code>null</code>如果不支持通过 ALPN 升级.
     */
    public byte[] getAlpnIdentifier();

    /**
     * @return IANA注册表中列出的协议的名称, 当且仅当 {@link #getAlpnIdentifier()} 返回这个名称的 UTF-8 编码.
     * 			如果{@link #getAlpnIdentifier()} 返回其他字节序列, 然后此方法返回空字符串.
     *         如果不支持通过 ALPN 升级, 将返回 <code>null</code>.
     */
    /*
     * Implementation note: 如果Tomcat曾经支持ALPN 协议，其中标识符不是UTF-8编码的名称，那么就需要进行一些重构.
     *
     * Implementation note: Tomcat假设这个名称的UTF-8编码不会超过255字节. 如果使用更长的名称，Tomcat的行为是未定义的.
     */
    public String getAlpnName();

    /**
     * @param socketWrapper 需要一个处理器的连接的 socketWrapper
     * @param adapter 提供访问标准Engine/Host/Context/Wrapper处理链的 Adapter实例
     *
     * @return 使用该协议处理连接的处理器实例.
     */
    public Processor getProcessor(SocketWrapperBase<?> socketWrapper, Adapter adapter);


    /**
     * @param adapter 用于配置新升级处理器的 Adapter
     * @param request 升级触发的请求的副本 (可能是不完整的)
     *
     * @return 这个协议的HTTP升级处理器的实例
     */
    public InternalHttpUpgradeHandler getInternalUpgradeHandler(Adapter adapter, Request request);


    /**
     * 允许实现检查请求并根据它所发现的来接受或拒绝请求.
     *
     * @param request 包含此协议的升级header的请求
     *
     * @return <code>true</code>如果接受请求, 否则<code>false</code>
     */
    public boolean accept(Request request);
}
