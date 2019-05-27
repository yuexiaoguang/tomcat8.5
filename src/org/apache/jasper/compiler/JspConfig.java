package org.apache.jasper.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

import javax.servlet.ServletContext;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.descriptor.JspPropertyGroupDescriptor;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 处理WEB_INF/web.xml中的JSP配置元素. 用于在JSP页面上指定JSP配置信息
 */
public class JspConfig {

    // Logger
    private final Log log = LogFactory.getLog(JspConfig.class);

    private Vector<JspPropertyGroup> jspProperties = null;
    private final ServletContext ctxt;
    private volatile boolean initialized = false;

    private static final String defaultIsXml = null;    // unspecified
    private String defaultIsELIgnored = null;           // unspecified
    private static final String defaultIsScriptingInvalid = null;
    private String defaultDeferedSyntaxAllowedAsLiteral = null;
    private static final String defaultTrimDirectiveWhitespaces = null;
    private static final String defaultDefaultContentType = null;
    private static final String defaultBuffer = null;
    private static final String defaultErrorOnUndeclaredNamespace = "false";
    private JspProperty defaultJspProperty;

    public JspConfig(ServletContext ctxt) {
        this.ctxt = ctxt;
    }

    private void processWebDotXml() {

        // Very, very unlikely but just in case...
        if (ctxt.getEffectiveMajorVersion() < 2) {
            defaultIsELIgnored = "true";
            defaultDeferedSyntaxAllowedAsLiteral = "true";
            return;
        }
        if (ctxt.getEffectiveMajorVersion() == 2) {
            if (ctxt.getEffectiveMinorVersion() < 5) {
                defaultDeferedSyntaxAllowedAsLiteral = "true";
            }
            if (ctxt.getEffectiveMinorVersion() < 4) {
                defaultIsELIgnored = "true";
                return;
            }
        }

        JspConfigDescriptor jspConfig = ctxt.getJspConfigDescriptor();

        if (jspConfig == null) {
            return;
        }

        jspProperties = new Vector<>();
        Collection<JspPropertyGroupDescriptor> jspPropertyGroups =
                jspConfig.getJspPropertyGroups();

        for (JspPropertyGroupDescriptor jspPropertyGroup : jspPropertyGroups) {

            Collection<String> urlPatterns = jspPropertyGroup.getUrlPatterns();

            if (urlPatterns.size() == 0) {
                continue;
            }

            JspProperty property = new JspProperty(jspPropertyGroup.getIsXml(),
                    jspPropertyGroup.getElIgnored(),
                    jspPropertyGroup.getScriptingInvalid(),
                    jspPropertyGroup.getPageEncoding(),
                    jspPropertyGroup.getIncludePreludes(),
                    jspPropertyGroup.getIncludeCodas(),
                    jspPropertyGroup.getDeferredSyntaxAllowedAsLiteral(),
                    jspPropertyGroup.getTrimDirectiveWhitespaces(),
                    jspPropertyGroup.getDefaultContentType(),
                    jspPropertyGroup.getBuffer(),
                    jspPropertyGroup.getErrorOnUndeclaredNamespace());

            // 为每个URL模式添加一个JspPropertyGroup. 这使得匹配逻辑更容易.
            for (String urlPattern : urlPatterns) {
                String path = null;
                String extension = null;

                if (urlPattern.indexOf('*') < 0) {
                    // Exact match
                    path = urlPattern;
                } else {
                    int i = urlPattern.lastIndexOf('/');
                    String file;
                    if (i >= 0) {
                        path = urlPattern.substring(0,i+1);
                        file = urlPattern.substring(i+1);
                    } else {
                        file = urlPattern;
                    }

                    // pattern must be "*", or of the form "*.jsp"
                    if (file.equals("*")) {
                        extension = "*";
                    } else if (file.startsWith("*.")) {
                        extension = file.substring(file.indexOf('.')+1);
                    }

                    // URL模式重构为以下:
                    // path != null, extension == null:  / or /foo/bar.ext
                    // path == null, extension != null:  *.ext
                    // path != null, extension == "*":   /foo/*
                    boolean isStar = "*".equals(extension);
                    if ((path == null && (extension == null || isStar))
                            || (path != null && !isStar)) {
                        if (log.isWarnEnabled()) {
                            log.warn(Localizer.getMessage(
                                    "jsp.warning.bad.urlpattern.propertygroup",
                                    urlPattern));
                        }
                        continue;
                    }
                }

                JspPropertyGroup propertyGroup =
                    new JspPropertyGroup(path, extension, property);

                jspProperties.addElement(propertyGroup);
            }
        }
    }

    private void init() {

        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    processWebDotXml();
                    defaultJspProperty = new JspProperty(defaultIsXml,
                            defaultIsELIgnored,
                            defaultIsScriptingInvalid,
                            null, null, null,
                            defaultDeferedSyntaxAllowedAsLiteral,
                            defaultTrimDirectiveWhitespaces,
                            defaultDefaultContentType,
                            defaultBuffer,
                            defaultErrorOnUndeclaredNamespace);
                    initialized = true;
                }
            }
        }
    }

    /**
     * 选择具有更严格的URL模式的属性组.
     * 如果有多个, 选择第一个.
     */
    @SuppressWarnings("null") // NPE not possible
    private JspPropertyGroup selectProperty(JspPropertyGroup prev,
            JspPropertyGroup curr) {
        if (prev == null) {
            return curr;
        }
        if (prev.getExtension() == null) {
            // exact match
            return prev;
        }
        if (curr.getExtension() == null) {
            // exact match
            return curr;
        }
        String prevPath = prev.getPath();
        String currPath = curr.getPath();
        if (prevPath == null && currPath == null) {
            // Both specifies a *.ext, keep the first one
            return prev;
        }
        if (prevPath == null && currPath != null) {
            return curr;
        }
        if (prevPath != null && currPath == null) {
            return prev;
        }
        if (prevPath.length() >= currPath.length()) {
            return prev;
        }
        return curr;
    }


    /**
     * 找到与所提供资源最匹配的属性.
     * @param uri 提供的资源.
     * @return 表示最佳匹配的JspProperty, 或一些默认的.
     */
    public JspProperty findJspProperty(String uri) {

        init();

        // JSP 配置设置不适用于标签文件
        if (jspProperties == null || uri.endsWith(".tag")
                || uri.endsWith(".tagx")) {
            return defaultJspProperty;
        }

        String uriPath = null;
        int index = uri.lastIndexOf('/');
        if (index >=0 ) {
            uriPath = uri.substring(0, index+1);
        }
        String uriExtension = null;
        index = uri.lastIndexOf('.');
        if (index >=0) {
            uriExtension = uri.substring(index+1);
        }

        Collection<String> includePreludes = new ArrayList<>();
        Collection<String> includeCodas = new ArrayList<>();

        JspPropertyGroup isXmlMatch = null;
        JspPropertyGroup elIgnoredMatch = null;
        JspPropertyGroup scriptingInvalidMatch = null;
        JspPropertyGroup pageEncodingMatch = null;
        JspPropertyGroup deferedSyntaxAllowedAsLiteralMatch = null;
        JspPropertyGroup trimDirectiveWhitespacesMatch = null;
        JspPropertyGroup defaultContentTypeMatch = null;
        JspPropertyGroup bufferMatch = null;
        JspPropertyGroup errorOnUndeclaredNamespaceMatch = null;

        Iterator<JspPropertyGroup> iter = jspProperties.iterator();
        while (iter.hasNext()) {

            JspPropertyGroup jpg = iter.next();
            JspProperty jp = jpg.getJspProperty();

            // (数组长度必须相同)
            String extension = jpg.getExtension();
            String path = jpg.getPath();

            if (extension == null) {
                // 精确匹配模式: /a/foo.jsp
                if (!uri.equals(path)) {
                    // not matched;
                    continue;
                }
            } else {
                // 匹配模式 *.ext 或 /p/*
                if (path != null && uriPath != null &&
                        ! uriPath.startsWith(path)) {
                    // not matched
                    continue;
                }
                if (!extension.equals("*") &&
                        !extension.equals(uriExtension)) {
                    // not matched
                    continue;
                }
            }
            // 有一个匹配
            // 添加include-preludes 和 include-codas
            if (jp.getIncludePrelude() != null) {
                includePreludes.addAll(jp.getIncludePrelude());
            }
            if (jp.getIncludeCoda() != null) {
                includeCodas.addAll(jp.getIncludeCoda());
            }

            // 如果有相同属性的前一个匹配项, 记住那个更严格的.
            if (jp.isXml() != null) {
                isXmlMatch = selectProperty(isXmlMatch, jpg);
            }
            if (jp.isELIgnored() != null) {
                elIgnoredMatch = selectProperty(elIgnoredMatch, jpg);
            }
            if (jp.isScriptingInvalid() != null) {
                scriptingInvalidMatch =
                    selectProperty(scriptingInvalidMatch, jpg);
            }
            if (jp.getPageEncoding() != null) {
                pageEncodingMatch = selectProperty(pageEncodingMatch, jpg);
            }
            if (jp.isDeferedSyntaxAllowedAsLiteral() != null) {
                deferedSyntaxAllowedAsLiteralMatch =
                    selectProperty(deferedSyntaxAllowedAsLiteralMatch, jpg);
            }
            if (jp.isTrimDirectiveWhitespaces() != null) {
                trimDirectiveWhitespacesMatch =
                    selectProperty(trimDirectiveWhitespacesMatch, jpg);
            }
            if (jp.getDefaultContentType() != null) {
                defaultContentTypeMatch =
                    selectProperty(defaultContentTypeMatch, jpg);
            }
            if (jp.getBuffer() != null) {
                bufferMatch = selectProperty(bufferMatch, jpg);
            }
            if (jp.isErrorOnUndeclaredNamespace() != null) {
                errorOnUndeclaredNamespaceMatch =
                    selectProperty(errorOnUndeclaredNamespaceMatch, jpg);
            }
        }


        String isXml = defaultIsXml;
        String isELIgnored = defaultIsELIgnored;
        String isScriptingInvalid = defaultIsScriptingInvalid;
        String pageEncoding = null;
        String isDeferedSyntaxAllowedAsLiteral =
            defaultDeferedSyntaxAllowedAsLiteral;
        String isTrimDirectiveWhitespaces = defaultTrimDirectiveWhitespaces;
        String defaultContentType = defaultDefaultContentType;
        String buffer = defaultBuffer;
        String errorOnUndeclaredNamespace = defaultErrorOnUndeclaredNamespace;

        if (isXmlMatch != null) {
            isXml = isXmlMatch.getJspProperty().isXml();
        }
        if (elIgnoredMatch != null) {
            isELIgnored = elIgnoredMatch.getJspProperty().isELIgnored();
        }
        if (scriptingInvalidMatch != null) {
            isScriptingInvalid =
                scriptingInvalidMatch.getJspProperty().isScriptingInvalid();
        }
        if (pageEncodingMatch != null) {
            pageEncoding = pageEncodingMatch.getJspProperty().getPageEncoding();
        }
        if (deferedSyntaxAllowedAsLiteralMatch != null) {
            isDeferedSyntaxAllowedAsLiteral =
                deferedSyntaxAllowedAsLiteralMatch.getJspProperty().isDeferedSyntaxAllowedAsLiteral();
        }
        if (trimDirectiveWhitespacesMatch != null) {
            isTrimDirectiveWhitespaces =
                trimDirectiveWhitespacesMatch.getJspProperty().isTrimDirectiveWhitespaces();
        }
        if (defaultContentTypeMatch != null) {
            defaultContentType =
                defaultContentTypeMatch.getJspProperty().getDefaultContentType();
        }
        if (bufferMatch != null) {
            buffer = bufferMatch.getJspProperty().getBuffer();
        }
        if (errorOnUndeclaredNamespaceMatch != null) {
            errorOnUndeclaredNamespace =
                errorOnUndeclaredNamespaceMatch.getJspProperty().isErrorOnUndeclaredNamespace();
        }

        return new JspProperty(isXml, isELIgnored, isScriptingInvalid,
                pageEncoding, includePreludes, includeCodas,
                isDeferedSyntaxAllowedAsLiteral, isTrimDirectiveWhitespaces,
                defaultContentType, buffer, errorOnUndeclaredNamespace);
    }

    /**
     * 要了解URI是否与JSP配置中的URL模式匹配. 如果匹配, 随后uri 是一个 JSP 页面. 这是主要用于jspc.
     * 
     * @param uri 要检查的路径
     * @return <code>true</code>如果路径表示一个JSP页面
     */
    public boolean isJspPage(String uri) {

        init();
        if (jspProperties == null) {
            return false;
        }

        String uriPath = null;
        int index = uri.lastIndexOf('/');
        if (index >=0 ) {
            uriPath = uri.substring(0, index+1);
        }
        String uriExtension = null;
        index = uri.lastIndexOf('.');
        if (index >=0) {
            uriExtension = uri.substring(index+1);
        }

        Iterator<JspPropertyGroup> iter = jspProperties.iterator();
        while (iter.hasNext()) {

            JspPropertyGroup jpg = iter.next();

            String extension = jpg.getExtension();
            String path = jpg.getPath();

            if (extension == null) {
                if (uri.equals(path)) {
                    // There is an exact match
                    return true;
                }
            } else {
                if ((path == null || path.equals(uriPath)) &&
                        (extension.equals("*") || extension.equals(uriExtension))) {
                    // Matches *, *.ext, /p/*, or /p/*.ext
                    return true;
                }
            }
        }
        return false;
    }

    public static class JspPropertyGroup {
        private final String path;
        private final String extension;
        private final JspProperty jspProperty;

        JspPropertyGroup(String path, String extension,
                JspProperty jspProperty) {
            this.path = path;
            this.extension = extension;
            this.jspProperty = jspProperty;
        }

        public String getPath() {
            return path;
        }

        public String getExtension() {
            return extension;
        }

        public JspProperty getJspProperty() {
            return jspProperty;
        }
    }

    public static class JspProperty {

        private final String isXml;
        private final String elIgnored;
        private final String scriptingInvalid;
        private final String pageEncoding;
        private final Collection<String> includePrelude;
        private final Collection<String> includeCoda;
        private final String deferedSyntaxAllowedAsLiteral;
        private final String trimDirectiveWhitespaces;
        private final String defaultContentType;
        private final String buffer;
        private final String errorOnUndeclaredNamespace;

        public JspProperty(String isXml, String elIgnored,
                String scriptingInvalid, String pageEncoding,
                Collection<String> includePrelude, Collection<String> includeCoda,
                String deferedSyntaxAllowedAsLiteral,
                String trimDirectiveWhitespaces,
                String defaultContentType,
                String buffer,
                String errorOnUndeclaredNamespace) {

            this.isXml = isXml;
            this.elIgnored = elIgnored;
            this.scriptingInvalid = scriptingInvalid;
            this.pageEncoding = pageEncoding;
            this.includePrelude = includePrelude;
            this.includeCoda = includeCoda;
            this.deferedSyntaxAllowedAsLiteral = deferedSyntaxAllowedAsLiteral;
            this.trimDirectiveWhitespaces = trimDirectiveWhitespaces;
            this.defaultContentType = defaultContentType;
            this.buffer = buffer;
            this.errorOnUndeclaredNamespace = errorOnUndeclaredNamespace;
        }

        public String isXml() {
            return isXml;
        }

        public String isELIgnored() {
            return elIgnored;
        }

        public String isScriptingInvalid() {
            return scriptingInvalid;
        }

        public String getPageEncoding() {
            return pageEncoding;
        }

        public Collection<String> getIncludePrelude() {
            return includePrelude;
        }

        public Collection<String> getIncludeCoda() {
            return includeCoda;
        }

        public String isDeferedSyntaxAllowedAsLiteral() {
            return deferedSyntaxAllowedAsLiteral;
        }

        public String isTrimDirectiveWhitespaces() {
            return trimDirectiveWhitespaces;
        }

        public String getDefaultContentType() {
            return defaultContentType;
        }

        public String getBuffer() {
            return buffer;
        }

        public String isErrorOnUndeclaredNamespace() {
            return errorOnUndeclaredNamespace;
        }
    }
}
