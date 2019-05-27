package org.apache.catalina.valves;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.servlet.ServletException;

import org.apache.catalina.Globals;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 使用 mod_proxy_http时, 客户端 SSL 信息不会包含在协议中 (不像 mod_jk 和 mod_proxy_ajp一样).
 * 让客户端 SSL信息对Tomcat可用, 需要一些额外的配置. 在 httpd中, mod_headers 用于添加 SSL 信息到 HTTP header中.
 * 在Tomcat中, 这个 valve用于从HTTP header读取信息, 并将其插入到请求中.<p>
 *
 * <b>Note: 确保所有请求的 header都是通过 httpd 设置的, 通过发送假 header 来防止客户端欺骗SSL信息. </b><p>
 *
 * 在 httpd.conf 中添加 以下信息:
 * <pre>
 * &lt;IfModule ssl_module&gt;
 *   RequestHeader set SSL_CLIENT_CERT "%{SSL_CLIENT_CERT}s"
 *   RequestHeader set SSL_CIPHER "%{SSL_CIPHER}s"
 *   RequestHeader set SSL_SESSION_ID "%{SSL_SESSION_ID}s"
 *   RequestHeader set SSL_CIPHER_USEKEYSIZE "%{SSL_CIPHER_USEKEYSIZE}s"
 * &lt;/IfModule&gt;
 * </pre>
 *
 * 在 server.xml 中, 配置这个 valve 在 Engine 元素的下面:
 * <pre>
 * &lt;Engine ...&gt;
 *   &lt;Valve className="org.apache.catalina.valves.SSLValve" /&gt;
 *   &lt;Host ... /&gt;
 * &lt;/Engine&gt;
 * </pre>
 */
public class SSLValve extends ValveBase {

    private static final Log log = LogFactory.getLog(SSLValve.class);

    private String sslClientCertHeader = "ssl_client_cert";
    private String sslCipherHeader = "ssl_cipher";
    private String sslSessionIdHeader = "ssl_session_id";
    private String sslCipherUserKeySizeHeader = "ssl_cipher_usekeysize";

    //------------------------------------------------------ Constructor
    public SSLValve() {
        super(true);
    }


    public String getSslClientCertHeader() {
        return sslClientCertHeader;
    }

    public void setSslClientCertHeader(String sslClientCertHeader) {
        this.sslClientCertHeader = sslClientCertHeader;
    }

    public String getSslCipherHeader() {
        return sslCipherHeader;
    }

    public void setSslCipherHeader(String sslCipherHeader) {
        this.sslCipherHeader = sslCipherHeader;
    }

    public String getSslSessionIdHeader() {
        return sslSessionIdHeader;
    }

    public void setSslSessionIdHeader(String sslSessionIdHeader) {
        this.sslSessionIdHeader = sslSessionIdHeader;
    }

    public String getSslCipherUserKeySizeHeader() {
        return sslCipherUserKeySizeHeader;
    }

    public void setSslCipherUserKeySizeHeader(String sslCipherUserKeySizeHeader) {
        this.sslCipherUserKeySizeHeader = sslCipherUserKeySizeHeader;
    }


    public String mygetHeader(Request request, String header) {
        String strcert0 = request.getHeader(header);
        if (strcert0 == null) {
            return null;
        }
        /* mod_header 写入 "(null)", 当SSL变量没有填充时 */
        if ("(null)".equals(strcert0)) {
            return null;
        }
        return strcert0;
    }


    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        /*
         * 通过以下方式处理反向代理的已知行为:
         * - mod_header 转换 '\n' 为 ' '
         * - nginx 转换 '\n' 为多个 ' '
         *
         * 代码假设去除首尾空格的 header 值以 '-----BEGIN CERTIFICATE-----' 开始, 并且以 '-----END CERTIFICATE-----' 结尾.
         *
         * Note: 对于 Java 7, BEGIN 和 END 标记必须是独立的行, 必须是每个原始内容行.
         *       CertificateFactory 容忍任何额外的空格, 例如首尾空格和新行, 只要它们不会出现在原始内容行的中间.
         */
        String headerValue = mygetHeader(request, sslClientCertHeader);
        if (headerValue != null) {
            headerValue = headerValue.trim();
            if (headerValue.length() > 27) {
                String body = headerValue.substring(27, headerValue .length() - 25);
                body = body.replace(' ', '\n');
                body = body.replace('\t', '\n');
                String header = "-----BEGIN CERTIFICATE-----\n";
                String footer = "\n-----END CERTIFICATE-----\n";
                String strcerts = header.concat(body).concat(footer);
                ByteArrayInputStream bais = new ByteArrayInputStream(
                        strcerts.getBytes(StandardCharsets.ISO_8859_1));
                X509Certificate jsseCerts[] = null;
                String providerName = (String) request.getConnector().getProperty(
                        "clientCertProvider");
                try {
                    CertificateFactory cf;
                    if (providerName == null) {
                        cf = CertificateFactory.getInstance("X.509");
                    } else {
                        cf = CertificateFactory.getInstance("X.509", providerName);
                    }
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(bais);
                    jsseCerts = new X509Certificate[1];
                    jsseCerts[0] = cert;
                } catch (java.security.cert.CertificateException e) {
                    log.warn(sm.getString("sslValve.certError", strcerts), e);
                } catch (NoSuchProviderException e) {
                    log.error(sm.getString(
                            "sslValve.invalidProvider", providerName), e);
                }
                request.setAttribute(Globals.CERTIFICATES_ATTR, jsseCerts);
            }
        }
        headerValue = mygetHeader(request, sslCipherHeader);
        if (headerValue != null) {
            request.setAttribute(Globals.CIPHER_SUITE_ATTR, headerValue);
        }
        headerValue = mygetHeader(request, sslSessionIdHeader);
        if (headerValue != null) {
            request.setAttribute(Globals.SSL_SESSION_ID_ATTR, headerValue);
        }
        headerValue = mygetHeader(request, sslCipherUserKeySizeHeader);
        if (headerValue != null) {
            request.setAttribute(Globals.KEY_SIZE_ATTR, Integer.valueOf(headerValue));
        }
        getNext().invoke(request, response);
    }
}
