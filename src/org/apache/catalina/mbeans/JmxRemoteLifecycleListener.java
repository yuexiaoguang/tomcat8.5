package org.apache.catalina.mbeans;

import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.management.remote.rmi.RMIJRMPServerImpl;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * 这个监听器修复了JMX/RMI服务器使用的端口，如果需要将jconsole或类似的工具连接到防火墙后面运行的远程Tomcat实例，那么这样事情就简单多了.
 * 配置的其余部分是通过配置JMX的标准系统属性来实现的.
 */
public class JmxRemoteLifecycleListener implements LifecycleListener {

    private static final Log log = LogFactory.getLog(JmxRemoteLifecycleListener.class);

    protected static final StringManager sm =
            StringManager.getManager(JmxRemoteLifecycleListener.class);

    protected String rmiBindAddress = null;
    protected int rmiRegistryPortPlatform = -1;
    protected int rmiServerPortPlatform = -1;
    protected boolean rmiRegistrySSL = true;
    protected boolean rmiServerSSL = true;
    protected String ciphers[] = null;
    protected String protocols[] = null;
    protected boolean clientAuth = true;
    protected boolean authenticate = true;
    protected String passwordFile = null;
    protected String loginModuleName = null;
    protected String accessFile = null;
    protected boolean useLocalPorts = false;

    protected JMXConnectorServer csPlatform = null;

    /**
     * 获取平台RMI服务器导出的inet地址.
     */
    public String getRmiBindAddress() {
        return rmiBindAddress;
    }

    /**
     * 设置平台RMI服务器导出的inet地址.
     * 
     * @param theRmiBindAddress inet地址
     */
    public void setRmiBindAddress(String theRmiBindAddress) {
        rmiBindAddress = theRmiBindAddress;
    }

    /**
     * 获取平台RMI服务器导出的端口. 这是通常由RMI栈选择的端口.
     * 
     * @return 端口
     */
    public int getRmiServerPortPlatform() {
        return rmiServerPortPlatform;
    }

    /**
     * 设置平台RMI服务器导出的端口. 这是通常由RMI栈选择的端口.
     * 
     * @param theRmiServerPortPlatform 端口
     */
    public void setRmiServerPortPlatform(int theRmiServerPortPlatform) {
        rmiServerPortPlatform = theRmiServerPortPlatform;
    }

    /**
     * 获取平台RMI注册表导出的端口.
     * 
     * @return 端口
     */
    public int getRmiRegistryPortPlatform() {
        return rmiRegistryPortPlatform;
    }

    /**
     * 设置平台RMI注册表导出的端口.
     * 
     * @param theRmiRegistryPortPlatform 端口
     */
    public void setRmiRegistryPortPlatform(int theRmiRegistryPortPlatform) {
        rmiRegistryPortPlatform = theRmiRegistryPortPlatform;
    }

    /**
     * 是否所有端口都应该使用本地端口. 如果使用SSH隧道，或类似，则应将其设置为true，以确保RMI客户端使用隧道.
     * 
     * @return <code>true</code>如果需要使用本地端口
     */
    public boolean getUseLocalPorts() {
        return useLocalPorts;
    }

    /**
     * 是否所有端口都应该使用本地端口. 如果使用SSH隧道，或类似，则应将其设置为true，以确保RMI客户端使用隧道.
     * 
     * @param useLocalPorts <code>true</code>如果需要使用本地端口
     */
    public void setUseLocalPorts(boolean useLocalPorts) {
        this.useLocalPorts = useLocalPorts;
    }

    private void init() {
        // 获取标准系统属性所需的所有其他参数. 只需要获取影响服务器端口创建的参数.
        String rmiRegistrySSLValue = System.getProperty(
                "com.sun.management.jmxremote.registry.ssl", "false");
        rmiRegistrySSL = Boolean.parseBoolean(rmiRegistrySSLValue);

        String rmiServerSSLValue = System.getProperty(
                "com.sun.management.jmxremote.ssl", "true");
        rmiServerSSL = Boolean.parseBoolean(rmiServerSSLValue);

        String protocolsValue = System.getProperty(
                "com.sun.management.jmxremote.ssl.enabled.protocols");
        if (protocolsValue != null) {
            protocols = protocolsValue.split(",");
        }

        String ciphersValue = System.getProperty(
                "com.sun.management.jmxremote.ssl.enabled.cipher.suites");
        if (ciphersValue != null) {
            ciphers = ciphersValue.split(",");
        }

        String clientAuthValue = System.getProperty(
                "com.sun.management.jmxremote.ssl.need.client.auth", "true");
        clientAuth = Boolean.parseBoolean(clientAuthValue);

        String authenticateValue = System.getProperty(
                "com.sun.management.jmxremote.authenticate", "true");
        authenticate = Boolean.parseBoolean(authenticateValue);

        passwordFile = System.getProperty(
                "com.sun.management.jmxremote.password.file",
                "jmxremote.password");

        accessFile = System.getProperty(
                "com.sun.management.jmxremote.access.file",
                "jmxremote.access");

        loginModuleName = System.getProperty(
                "com.sun.management.jmxremote.login.config");
    }


    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        // 当服务器启动时, 配置 JMX/RMI
        if (Lifecycle.START_EVENT.equals(event.getType())) {
            // 使用标准JMX系统属性配置
            init();

            // 防止攻击者猜测RMI对象ID
            System.setProperty("java.rmi.server.randomIDs", "true");

            // Create the environment
            HashMap<String,Object> env = new HashMap<>();

            RMIClientSocketFactory registryCsf = null;
            RMIServerSocketFactory registrySsf = null;

            RMIClientSocketFactory serverCsf = null;
            RMIServerSocketFactory serverSsf = null;

            // Configure registry socket factories
            if (rmiRegistrySSL) {
                registryCsf = new SslRMIClientSocketFactory();
                if (rmiBindAddress == null) {
                    registrySsf = new SslRMIServerSocketFactory(
                            ciphers, protocols, clientAuth);
                } else {
                    registrySsf = new SslRmiServerBindSocketFactory(
                            ciphers, protocols, clientAuth, rmiBindAddress);
                }
            } else {
                if (rmiBindAddress != null) {
                    registrySsf = new RmiServerBindSocketFactory(rmiBindAddress);
                }
            }

            // Configure server socket factories
            if (rmiServerSSL) {
                serverCsf = new SslRMIClientSocketFactory();
                if (rmiBindAddress == null) {
                    serverSsf = new SslRMIServerSocketFactory(
                            ciphers, protocols, clientAuth);
                } else {
                    serverSsf = new SslRmiServerBindSocketFactory(
                            ciphers, protocols, clientAuth, rmiBindAddress);
                }
            } else {
                if (rmiBindAddress != null) {
                    serverSsf = new RmiServerBindSocketFactory(rmiBindAddress);
                }
            }

            // 默认情况下, 注册表将选择一个地址来监听.
            // 设置此属性将覆盖并确保它在配置的地址上监听.
            if (rmiBindAddress != null) {
                System.setProperty("java.rmi.server.hostname", rmiBindAddress);
            }

            // 如果需要，强制使用本地端口
            if (useLocalPorts) {
                registryCsf = new RmiClientLocalhostSocketFactory(registryCsf);
                serverCsf = new RmiClientLocalhostSocketFactory(serverCsf);
            }

            env.put("jmx.remote.rmi.server.credential.types", new String[] {
                    String[].class.getName(),
                    String.class.getName() });

            // 填充用于创建服务器的 env属性
            if (serverCsf != null) {
                env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE, serverCsf);
                env.put("com.sun.jndi.rmi.factory.socket", registryCsf);
            }
            if (serverSsf != null) {
                env.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE, serverSsf);
            }

            // Configure authentication
            if (authenticate) {
                env.put("jmx.remote.x.password.file", passwordFile);
                env.put("jmx.remote.x.access.file", accessFile);
                env.put("jmx.remote.x.login.config", loginModuleName);
            }

            // Create the Platform server
            csPlatform = createServer("Platform", rmiBindAddress, rmiRegistryPortPlatform,
                    rmiServerPortPlatform, env, registryCsf, registrySsf, serverCsf, serverSsf);

        } else if (Lifecycle.STOP_EVENT.equals(event.getType())) {
            destroyServer("Platform", csPlatform);
        }
    }


    private JMXConnectorServer createServer(String serverName,
            String bindAddress, int theRmiRegistryPort, int theRmiServerPort,
            HashMap<String,Object> theEnv,
            RMIClientSocketFactory registryCsf, RMIServerSocketFactory registrySsf,
            RMIClientSocketFactory serverCsf, RMIServerSocketFactory serverSsf) {

        // Create the RMI registry
        Registry registry;
        try {
            registry = LocateRegistry.createRegistry(
                    theRmiRegistryPort, registryCsf, registrySsf);
        } catch (RemoteException e) {
            log.error(sm.getString(
                    "jmxRemoteLifecycleListener.createRegistryFailed",
                    serverName, Integer.toString(theRmiRegistryPort)), e);
            return null;
        }

        if (bindAddress == null) {
            bindAddress = "localhost";
        }

        String url = "service:jmx:rmi://" + bindAddress;
        JMXServiceURL serviceUrl;
        try {
            serviceUrl = new JMXServiceURL(url);
        } catch (MalformedURLException e) {
            log.error(sm.getString("jmxRemoteLifecycleListener.invalidURL", serverName, url), e);
            return null;
        }

        RMIConnectorServer cs = null;
        try {
            RMIJRMPServerImpl server = new RMIJRMPServerImpl(
                    rmiServerPortPlatform, serverCsf, serverSsf, theEnv);
            cs = new RMIConnectorServer(serviceUrl, theEnv, server,
                    ManagementFactory.getPlatformMBeanServer());
            cs.start();
            registry.bind("jmxrmi", server.toStub());
            log.info(sm.getString("jmxRemoteLifecycleListener.start",
                    Integer.toString(theRmiRegistryPort),
                    Integer.toString(theRmiServerPort), serverName));
        } catch (IOException | AlreadyBoundException e) {
            log.error(sm.getString(
                    "jmxRemoteLifecycleListener.createServerFailed",
                    serverName), e);
        }
        return cs;
    }


    private void destroyServer(String serverName,
            JMXConnectorServer theConnectorServer) {
        if (theConnectorServer != null) {
            try {
                theConnectorServer.stop();
            } catch (IOException e) {
                log.error(sm.getString(
                        "jmxRemoteLifecycleListener.destroyServerFailed",
                        serverName),e);
            }
        }
    }


    public static class RmiClientLocalhostSocketFactory
            implements RMIClientSocketFactory, Serializable {

        private static final long serialVersionUID = 1L;

        private static final String FORCED_HOST = "localhost";

        private final RMIClientSocketFactory factory;

        public RmiClientLocalhostSocketFactory(RMIClientSocketFactory theFactory) {
            factory = theFactory;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            if (factory == null) {
                return new Socket(FORCED_HOST, port);
            } else {
                return factory.createSocket(FORCED_HOST, port);
            }
        }
    }


    public static class RmiServerBindSocketFactory implements RMIServerSocketFactory {

        private final InetAddress bindAddress;

        public RmiServerBindSocketFactory(String address) {
            InetAddress bindAddress = null;
            try {
                bindAddress = InetAddress.getByName(address);
            } catch (UnknownHostException e) {
                log.error(sm.getString(
                        "jmxRemoteLifecycleListener.invalidRmiBindAddress", address), e);
                // 绑定地址将为NULL，这意味着所有本地地址都应该是安全的
            }
            this.bindAddress = bindAddress;
        }

        @Override
        public ServerSocket createServerSocket(int port) throws IOException  {
            return new ServerSocket(port, 0, bindAddress);
        }
    }


    public static class SslRmiServerBindSocketFactory extends SslRMIServerSocketFactory {

        private static final SSLServerSocketFactory sslServerSocketFactory;
        private static final String[] defaultProtocols;

        static {
            SSLContext sslContext;
            try {
                sslContext = SSLContext.getDefault();
            } catch (NoSuchAlgorithmException e) {
                // Can't continue. Force a failure.
                throw new IllegalStateException(e);
            }
            sslServerSocketFactory = sslContext.getServerSocketFactory();
            String[] protocols = sslContext.getDefaultSSLParameters().getProtocols();
            List<String> filteredProtocols = new ArrayList<>(protocols.length);
            for (String protocol : protocols) {
                if (protocol.toUpperCase(Locale.ENGLISH).contains("SSL")) {
                    continue;
                }
                filteredProtocols.add(protocol);
            }
            defaultProtocols = filteredProtocols.toArray(new String[filteredProtocols.size()]);
        }

        private final InetAddress bindAddress;

        public SslRmiServerBindSocketFactory(String[] enabledCipherSuites,
                String[] enabledProtocols, boolean needClientAuth, String address) {
            super(enabledCipherSuites, enabledProtocols, needClientAuth);
            InetAddress bindAddress = null;
            try {
                bindAddress = InetAddress.getByName(address);
            } catch (UnknownHostException e) {
                log.error(sm.getString(
                        "jmxRemoteLifecycleListener.invalidRmiBindAddress", address), e);
                // 绑定地址将为NULL，这意味着所有本地地址都应该是安全的
            }
            this.bindAddress = bindAddress;
        }

        @Override
        public ServerSocket createServerSocket(int port) throws IOException  {
            SSLServerSocket sslServerSocket =
                    (SSLServerSocket) sslServerSocketFactory.createServerSocket(port, 0, bindAddress);
            if (getEnabledCipherSuites() != null) {
                sslServerSocket.setEnabledCipherSuites(getEnabledCipherSuites());
            }
            if (getEnabledProtocols() == null) {
                sslServerSocket.setEnabledProtocols(defaultProtocols);
            } else {
                sslServerSocket.setEnabledProtocols(getEnabledProtocols());
            }
            sslServerSocket.setNeedClientAuth(getNeedClientAuth());
            return sslServerSocket;
        }
    }
}
