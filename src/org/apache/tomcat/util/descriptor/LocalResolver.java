package org.apache.tomcat.util.descriptor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;

/**
 * 用于本地缓存的XML资源的解析程序.
 */
public class LocalResolver implements EntityResolver2 {

    private static final StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);

    private static final String[] JAVA_EE_NAMESPACES = {
        XmlIdentifiers.JAVAEE_1_4_NS,
        XmlIdentifiers.JAVAEE_5_NS,
        XmlIdentifiers.JAVAEE_7_NS};


    private final Map<String,String> publicIds;
    private final Map<String,String> systemIds;
    private final boolean blockExternal;

    /**
     * 提供公共和系统标识符到本地资源的映射的构造函数. 每个映射包含从众所周知的标识符到本地资源路径的URL的映射.
     *
     * @param publicIds 将众所周知的公共标识符映射到本地资源
     * @param systemIds 将众所周知的系统标识符映射到本地资源
     * @param blockExternal 是否阻止不是众所周知的外部资源
     */
    public LocalResolver(Map<String,String> publicIds,
            Map<String,String> systemIds, boolean blockExternal) {
        this.publicIds = publicIds;
        this.systemIds = systemIds;
        this.blockExternal = blockExternal;
    }


    @Override
    public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException, IOException {
        return resolveEntity(null, publicId, null, systemId);
    }


    @Override
    public InputSource resolveEntity(String name, String publicId,
            String base, String systemId) throws SAXException, IOException {

        // First try resolving using the publicId
        String resolved = publicIds.get(publicId);
        if (resolved != null) {
            InputSource is = new InputSource(resolved);
            is.setPublicId(publicId);
            return is;
        }

        // If there is no systemId, can't try anything else
        if (systemId == null) {
            throw new FileNotFoundException(sm.getString("localResolver.unresolvedEntity",
                    name, publicId, systemId, base));
        }

        // Try resolving with the supplied systemId
        resolved = systemIds.get(systemId);
        if (resolved != null) {
            InputSource is = new InputSource(resolved);
            is.setPublicId(publicId);
            return is;
        }

        // 解决XML文档的问题，这些文档仅使用该位置的文件名来引用JavaEE架构
        for (String javaEENamespace : JAVA_EE_NAMESPACES) {
            String javaEESystemId = javaEENamespace + '/' + systemId;
            resolved = systemIds.get(javaEESystemId);
            if (resolved != null) {
                InputSource is = new InputSource(resolved);
                is.setPublicId(publicId);
                return is;
            }
        }

        // Resolve the supplied systemId against the base
        URI systemUri;
        try {
            if (base == null) {
                systemUri = new URI(systemId);
            } else {
                // 无法使用 URI.resolve(), 因为 "jar:..." URL不是有效的分层URI, 因此 resolve() 无效.
                // 新的 URL() 表示 jar: 流处理器, 并且管理它.
                URI baseUri = new URI(base);
                systemUri = new URL(baseUri.toURL(), systemId).toURI();
            }
            systemUri = systemUri.normalize();
        } catch (URISyntaxException e) {
            // Windows上的绝对文件 URI 将使用 | , 而不是 : .
            if (blockExternal) {
                // 绝对路径是不允许的，所以阻止它
                throw new MalformedURLException(e.getMessage());
            } else {
                // See if the URLHandler can resolve it
                return new InputSource(systemId);
            }
        }
        if (systemUri.isAbsolute()) {
            // Try the resolved systemId
            resolved = systemIds.get(systemUri.toString());
            if (resolved != null) {
                InputSource is = new InputSource(resolved);
                is.setPublicId(publicId);
                return is;
            }
            if (!blockExternal) {
                InputSource is = new InputSource(systemUri.toString());
                is.setPublicId(publicId);
                return is;
            }
        }

        throw new FileNotFoundException(sm.getString("localResolver.unresolvedEntity",
                name, publicId, systemId, base));
    }


    @Override
    public InputSource getExternalSubset(String name, String baseURI)
            throws SAXException, IOException {
        return null;
    }
}
