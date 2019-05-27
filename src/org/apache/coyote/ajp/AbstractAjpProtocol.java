package org.apache.coyote.ajp;

import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.Processor;
import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.UpgradeToken;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * 这是AJP协议处理程序的基本实现. 实现通常扩展此基类, 而不是实现 {@link org.apache.coyote.ProtocolHandler}.
 * 用Tomcat进行的所有实现都是通过这种方式实现的.
 *
 * @param <S> 实现所使用的socket的类型
 */
public abstract class AbstractAjpProtocol<S> extends AbstractProtocol<S> {

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(AbstractAjpProtocol.class);


    public AbstractAjpProtocol(AbstractEndpoint<S> endpoint) {
        super(endpoint);
        setConnectionTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        // AJP does not use Send File
        getEndpoint().setUseSendfile(false);
        ConnectionHandler<S> cHandler = new ConnectionHandler<>(this);
        setHandler(cHandler);
        getEndpoint().setHandler(cHandler);
    }


    @Override
    protected String getProtocolName() {
        return "Ajp";
    }


    /**
     * {@inheritDoc}
     *
     * 重写以使此包中的其他类可访问 getter.
     */
    @Override
    protected AbstractEndpoint<S> getEndpoint() {
        return super.getEndpoint();
    }


    /**
     * {@inheritDoc}
     *
     * AJP不支持协议协商，所以总是返回 null.
     */
    @Override
    protected UpgradeProtocol getNegotiatedProtocol(String name) {
        return null;
    }


    /**
     * {@inheritDoc}
     *
     * AJP不支持协议升级，所以总是返回 null.
     */
    @Override
    protected UpgradeProtocol getUpgradeProtocol(String name) {
        return null;
    }

    // ------------------------------------------------- AJP specific properties
    // ------------------------------------------ managed in the ProtocolHandler

    /**
     * 刷新时发送AJP刷新包.
     * 刷新包是零字节的 AJP13 SEND_BODY_CHUNK 包. mod_jk 和 mod_proxy_ajp 将这个解释为刷新数据到客户端的请求.
     * AJP 总是在响应时刷新, 所以数据包流到客户端不重要, 不要使用额外的刷新包.
     * 对于兼容性和安全性, 默认情况下启用刷新包.
     */
    protected boolean ajpFlush = true;
    public boolean getAjpFlush() { return ajpFlush; }
    public void setAjpFlush(boolean ajpFlush) {
        this.ajpFlush = ajpFlush;
    }


    /**
     * 应该在本地Web服务器层中进行身份验证吗, 或者在Servlet 容器中 ?
     */
    private boolean tomcatAuthentication = true;
    public boolean getTomcatAuthentication() { return tomcatAuthentication; }
    public void setTomcatAuthentication(boolean tomcatAuthentication) {
        this.tomcatAuthentication = tomcatAuthentication;
    }


    /**
     * 应在本地Web服务器层中进行身份验证，还是在servlet容器中进行?
     */
    private boolean tomcatAuthorization = false;
    public boolean getTomcatAuthorization() { return tomcatAuthorization; }
    public void setTomcatAuthorization(boolean tomcatAuthorization) {
        this.tomcatAuthorization = tomcatAuthorization;
    }


    /**
     * 必需的安全.
     */
    private String requiredSecret = null;
    public void setRequiredSecret(String requiredSecret) {
        this.requiredSecret = requiredSecret;
    }


    /**
     * AJP 包大小.
     */
    private int packetSize = Constants.MAX_PACKET_SIZE;
    public int getPacketSize() { return packetSize; }
    public void setPacketSize(int packetSize) {
        if(packetSize < Constants.MAX_PACKET_SIZE) {
            this.packetSize = Constants.MAX_PACKET_SIZE;
        } else {
            this.packetSize = packetSize;
        }
    }


    // --------------------------------------------- SSL is not supported in AJP

    @Override
    public void addSslHostConfig(SSLHostConfig sslHostConfig) {
        getLog().warn(sm.getString("ajpprotocol.noSSL", sslHostConfig.getHostName()));
    }


    @Override
    public SSLHostConfig[] findSslHostConfigs() {
        return new SSLHostConfig[0];
    }


    @Override
    public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol) {
        getLog().warn(sm.getString("ajpprotocol.noUpgrade", upgradeProtocol.getClass().getName()));
    }


    @Override
    public UpgradeProtocol[] findUpgradeProtocols() {
        return new UpgradeProtocol[0];
    }


    @SuppressWarnings("deprecation")
    @Override
    protected Processor createProcessor() {
        AjpProcessor processor = new AjpProcessor(getPacketSize(), getEndpoint());
        processor.setAdapter(getAdapter());
        processor.setAjpFlush(getAjpFlush());
        processor.setTomcatAuthentication(getTomcatAuthentication());
        processor.setTomcatAuthorization(getTomcatAuthorization());
        processor.setRequiredSecret(requiredSecret);
        processor.setKeepAliveTimeout(getKeepAliveTimeout());
        processor.setClientCertProvider(getClientCertProvider());
        processor.setSendReasonPhrase(getSendReasonPhrase());
        return processor;
    }


    @Override
    protected Processor createUpgradeProcessor(SocketWrapperBase<?> socket,
            UpgradeToken upgradeToken) {
        throw new IllegalStateException(sm.getString("ajpprotocol.noUpgradeHandler",
                upgradeToken.getHttpUpgradeHandler().getClass().getName()));
    }
}
