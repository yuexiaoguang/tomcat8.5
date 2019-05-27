package org.apache.tomcat.websocket.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;
import org.apache.tomcat.websocket.Constants;
import org.apache.tomcat.websocket.Transformation;
import org.apache.tomcat.websocket.TransformationFactory;
import org.apache.tomcat.websocket.Util;
import org.apache.tomcat.websocket.WsHandshakeResponse;
import org.apache.tomcat.websocket.pojo.PojoEndpointServer;

public class UpgradeUtil {

    private static final StringManager sm =
            StringManager.getManager(UpgradeUtil.class.getPackage().getName());
    private static final byte[] WS_ACCEPT =
            "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(
                    StandardCharsets.ISO_8859_1);

    private UpgradeUtil() {
        // Utility class. Hide default constructor.
    }

    /**
     * 检查此是否是HTTP请求，其中包含对Web套接字的有效升级请求.
     * <p>
     * Note: RFC 2616不限制HTTP升级为GET请求, 但是Java WebSocket spec 1.0, section 8.2 意味着这样的限制,
     * 		而且RFC 6455 section 4.1 要求WebSocket Upgrade 使用 GET.
     * 
     * @param request  检查它是否是WebSocket连接的HTTP升级请求的请求
     * @param response 与请求相关联的响应
     * 
     * @return <code>true</code>如果请求包含对WebSocket协议的HTTP升级请求, 否则<code>false</code>
     */
    public static boolean isWebSocketUpgradeRequest(ServletRequest request,
            ServletResponse response) {

        return ((request instanceof HttpServletRequest) &&
                (response instanceof HttpServletResponse) &&
                headerContainsToken((HttpServletRequest) request,
                        Constants.UPGRADE_HEADER_NAME,
                        Constants.UPGRADE_HEADER_VALUE) &&
                "GET".equals(((HttpServletRequest) request).getMethod()));
    }


    public static void doUpgrade(WsServerContainer sc, HttpServletRequest req,
            HttpServletResponse resp, ServerEndpointConfig sec,
            Map<String,String> pathParams)
            throws ServletException, IOException {

        // 验证剩下的header, 如果验证失败, 拒绝请求
        String key;
        String subProtocol = null;
        if (!headerContainsToken(req, Constants.CONNECTION_HEADER_NAME,
                Constants.CONNECTION_HEADER_VALUE)) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!headerContainsToken(req, Constants.WS_VERSION_HEADER_NAME,
                Constants.WS_VERSION_HEADER_VALUE)) {
            resp.setStatus(426);
            resp.setHeader(Constants.WS_VERSION_HEADER_NAME,
                    Constants.WS_VERSION_HEADER_VALUE);
            return;
        }
        key = req.getHeader(Constants.WS_KEY_HEADER_NAME);
        if (key == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }


        // Origin check
        String origin = req.getHeader(Constants.ORIGIN_HEADER_NAME);
        if (!sec.getConfigurator().checkOrigin(origin)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        // Sub-protocols
        List<String> subProtocols = getTokensFromHeader(req,
                Constants.WS_PROTOCOL_HEADER_NAME);
        subProtocol = sec.getConfigurator().getNegotiatedSubprotocol(
                sec.getSubprotocols(), subProtocols);

        // Extensions
        // 通常只为一个header，但处理多个header的情况
        List<Extension> extensionsRequested = new ArrayList<>();
        Enumeration<String> extHeaders = req.getHeaders(Constants.WS_EXTENSIONS_HEADER_NAME);
        while (extHeaders.hasMoreElements()) {
            Util.parseExtensionHeader(extensionsRequested, extHeaders.nextElement());
        }
        // Negotiation phase 1. 默认情况下，这只是过滤掉服务器不支持的扩展，但是应用程序可以使用自定义配置器执行更多操作.
        List<Extension> installedExtensions = null;
        if (sec.getExtensions().size() == 0) {
            installedExtensions = Constants.INSTALLED_EXTENSIONS;
        } else {
            installedExtensions = new ArrayList<>();
            installedExtensions.addAll(sec.getExtensions());
            installedExtensions.addAll(Constants.INSTALLED_EXTENSIONS);
        }
        List<Extension> negotiatedExtensionsPhase1 = sec.getConfigurator().getNegotiatedExtensions(
                installedExtensions, extensionsRequested);

        // Negotiation phase 2. 创建将应用于此连接的转换.
        // 注意，如果客户端请求了服务器无法支持的配置，则此时可能删除扩展.
        List<Transformation> transformations = createTransformations(negotiatedExtensionsPhase1);

        List<Extension> negotiatedExtensionsPhase2;
        if (transformations.isEmpty()) {
            negotiatedExtensionsPhase2 = Collections.emptyList();
        } else {
            negotiatedExtensionsPhase2 = new ArrayList<>(transformations.size());
            for (Transformation t : transformations) {
                negotiatedExtensionsPhase2.add(t.getExtensionResponse());
            }
        }

        // 建立转换管道
        Transformation transformation = null;
        StringBuilder responseHeaderExtensions = new StringBuilder();
        boolean first = true;
        for (Transformation t : transformations) {
            if (first) {
                first = false;
            } else {
                responseHeaderExtensions.append(',');
            }
            append(responseHeaderExtensions, t.getExtensionResponse());
            if (transformation == null) {
                transformation = t;
            } else {
                transformation.setNext(t);
            }
        }

        // 现在有完整的管道, 验证使用的RSV位.
        if (transformation != null && !transformation.validateRsvBits(0)) {
            throw new ServletException(sm.getString("upgradeUtil.incompatibleRsv"));
        }

        // 接受连接.
        resp.setHeader(Constants.UPGRADE_HEADER_NAME,
                Constants.UPGRADE_HEADER_VALUE);
        resp.setHeader(Constants.CONNECTION_HEADER_NAME,
                Constants.CONNECTION_HEADER_VALUE);
        resp.setHeader(HandshakeResponse.SEC_WEBSOCKET_ACCEPT,
                getWebSocketAccept(key));
        if (subProtocol != null && subProtocol.length() > 0) {
            // RFC6455 4.2.2 explicitly states "" is not valid here
            resp.setHeader(Constants.WS_PROTOCOL_HEADER_NAME, subProtocol);
        }
        if (!transformations.isEmpty()) {
            resp.setHeader(Constants.WS_EXTENSIONS_HEADER_NAME, responseHeaderExtensions.toString());
        }

        WsHandshakeRequest wsRequest = new WsHandshakeRequest(req, pathParams);
        WsHandshakeResponse wsResponse = new WsHandshakeResponse();
        WsPerSessionServerEndpointConfig perSessionServerEndpointConfig =
                new WsPerSessionServerEndpointConfig(sec);
        sec.getConfigurator().modifyHandshake(perSessionServerEndpointConfig,
                wsRequest, wsResponse);
        wsRequest.finished();

        // 添加额外的 header
        for (Entry<String,List<String>> entry :
                wsResponse.getHeaders().entrySet()) {
            for (String headerValue: entry.getValue()) {
                resp.addHeader(entry.getKey(), headerValue);
            }
        }

        Endpoint ep;
        try {
            Class<?> clazz = sec.getEndpointClass();
            if (Endpoint.class.isAssignableFrom(clazz)) {
                ep = (Endpoint) sec.getConfigurator().getEndpointInstance(
                        clazz);
            } else {
                ep = new PojoEndpointServer();
                // 需要使路径参数可用于 POJO
                perSessionServerEndpointConfig.getUserProperties().put(
                        org.apache.tomcat.websocket.pojo.Constants.POJO_PATH_PARAM_KEY, pathParams);
            }
        } catch (InstantiationException e) {
            throw new ServletException(e);
        }

        WsHttpUpgradeHandler wsHandler =
                req.upgrade(WsHttpUpgradeHandler.class);
        wsHandler.preInit(ep, perSessionServerEndpointConfig, sc, wsRequest,
                negotiatedExtensionsPhase2, subProtocol, transformation, pathParams,
                req.isSecure());

    }


    private static List<Transformation> createTransformations(
            List<Extension> negotiatedExtensions) {

        TransformationFactory factory = TransformationFactory.getInstance();

        LinkedHashMap<String,List<List<Extension.Parameter>>> extensionPreferences =
                new LinkedHashMap<>();

        // 结果可能会比这个小
        List<Transformation> result = new ArrayList<>(negotiatedExtensions.size());

        for (Extension extension : negotiatedExtensions) {
            List<List<Extension.Parameter>> preferences =
                    extensionPreferences.get(extension.getName());

            if (preferences == null) {
                preferences = new ArrayList<>();
                extensionPreferences.put(extension.getName(), preferences);
            }

            preferences.add(extension.getParameters());
        }

        for (Map.Entry<String,List<List<Extension.Parameter>>> entry :
            extensionPreferences.entrySet()) {
            Transformation transformation = factory.create(entry.getKey(), entry.getValue(), true);
            if (transformation != null) {
                result.add(transformation);
            }
        }
        return result;
    }


    private static void append(StringBuilder sb, Extension extension) {
        if (extension == null || extension.getName() == null || extension.getName().length() == 0) {
            return;
        }

        sb.append(extension.getName());

        for (Extension.Parameter p : extension.getParameters()) {
            sb.append(';');
            sb.append(p.getName());
            if (p.getValue() != null) {
                sb.append('=');
                sb.append(p.getValue());
            }
        }
    }


    /*
     * 这只适用于token. 带引号的字符串需要更复杂的解析.
     */
    private static boolean headerContainsToken(HttpServletRequest req,
            String headerName, String target) {
        Enumeration<String> headers = req.getHeaders(headerName);
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            String[] tokens = header.split(",");
            for (String token : tokens) {
                if (target.equalsIgnoreCase(token.trim())) {
                    return true;
                }
            }
        }
        return false;
    }


    /*
     * 这只适用于 token. 带引号的字符串需要更复杂的解析.
     */
    private static List<String> getTokensFromHeader(HttpServletRequest req,
            String headerName) {
        List<String> result = new ArrayList<>();
        Enumeration<String> headers = req.getHeaders(headerName);
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            String[] tokens = header.split(",");
            for (String token : tokens) {
                result.add(token.trim());
            }
        }
        return result;
    }


    private static String getWebSocketAccept(String key) {
        byte[] digest = ConcurrentMessageDigest.digestSHA1(
                key.getBytes(StandardCharsets.ISO_8859_1), WS_ACCEPT);
        return Base64.encodeBase64String(digest);
    }
}
