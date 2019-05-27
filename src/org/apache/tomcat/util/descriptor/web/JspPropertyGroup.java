package org.apache.tomcat.util.descriptor.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.tomcat.util.buf.UDecoder;

/**
 * 在web.xml中表示jsp-property-group元素.
 */
public class JspPropertyGroup extends XmlEncodingBase {

    private Boolean deferredSyntax = null;
    public void setDeferredSyntax(String deferredSyntax) {
        this.deferredSyntax = Boolean.valueOf(deferredSyntax);
    }
    public Boolean getDeferredSyntax() { return deferredSyntax; }

    private Boolean elIgnored = null;
    public void setElIgnored(String elIgnored) {
        this.elIgnored = Boolean.valueOf(elIgnored);
    }
    public Boolean getElIgnored() { return elIgnored; }

    private final Collection<String> includeCodas = new ArrayList<>();
    public void addIncludeCoda(String includeCoda) {
        includeCodas.add(includeCoda);
    }
    public Collection<String> getIncludeCodas() { return includeCodas; }

    private final Collection<String> includePreludes = new ArrayList<>();
    public void addIncludePrelude(String includePrelude) {
        includePreludes.add(includePrelude);
    }
    public Collection<String> getIncludePreludes() { return includePreludes; }

    private Boolean isXml = null;
    public void setIsXml(String isXml) {
        this.isXml = Boolean.valueOf(isXml);
    }
    public Boolean getIsXml() { return isXml; }

    private String pageEncoding = null;
    public void setPageEncoding(String pageEncoding) {
        this.pageEncoding = pageEncoding;
    }
    public String getPageEncoding() { return this.pageEncoding; }

    private Boolean scriptingInvalid = null;
    public void setScriptingInvalid(String scriptingInvalid) {
        this.scriptingInvalid = Boolean.valueOf(scriptingInvalid);
    }
    public Boolean getScriptingInvalid() { return scriptingInvalid; }

    private Boolean trimWhitespace = null;
    public void setTrimWhitespace(String trimWhitespace) {
        this.trimWhitespace = Boolean.valueOf(trimWhitespace);
    }
    public Boolean getTrimWhitespace() { return trimWhitespace; }

    private LinkedHashSet<String> urlPattern = new LinkedHashSet<>();
    public void addUrlPattern(String urlPattern) {
        addUrlPatternDecoded(UDecoder.URLDecode(urlPattern, getCharset()));
    }
    public void addUrlPatternDecoded(String urlPattern) {
        this.urlPattern.add(urlPattern);
    }
    public Set<String> getUrlPatterns() { return this.urlPattern; }

    private String defaultContentType = null;
    public void setDefaultContentType(String defaultContentType) {
        this.defaultContentType = defaultContentType;
    }
    public String getDefaultContentType() { return this.defaultContentType; }

    private String buffer = null;
    public void setBuffer(String buffer) {
        this.buffer = buffer;
    }
    public String getBuffer() { return this.buffer; }

    private Boolean errorOnUndeclaredNamespace = null;
    public void setErrorOnUndeclaredNamespace(
            String errorOnUndeclaredNamespace) {
        this.errorOnUndeclaredNamespace =
            Boolean.valueOf(errorOnUndeclaredNamespace);
    }
    public Boolean getErrorOnUndeclaredNamespace() {
        return this.errorOnUndeclaredNamespace;
    }
}
